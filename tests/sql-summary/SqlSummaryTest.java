import com.navercorp.cubridqa.cqt.console.bean.CaseResult;
import com.navercorp.cubridqa.cqt.console.bean.Summary;
import com.navercorp.cubridqa.cqt.console.bean.SummaryInfo;
import com.navercorp.cubridqa.cqt.console.bean.Test;
import com.navercorp.cubridqa.cqt.console.bo.ConsoleBO;
import com.navercorp.cubridqa.cqt.console.util.JunitXmlWriter;
import com.navercorp.cubridqa.cqt.console.util.TestUtil;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class SqlSummaryTest {
    private static int failures;
    private static Path root;

    private static void check(boolean ok, String message) {
        if (!ok) {
            failures++;
            System.err.println("FAIL " + message);
        }
    }

    private static void equal(Object expected, Object actual, String message) {
        check(expected.equals(actual), message + " expected=" + expected + " actual=" + actual);
    }

    private static Map<String, Integer> multiset(List<String> values) {
        Map<String, Integer> result = new TreeMap<String, Integer>();
        for (String value : values) result.put(value, result.containsKey(value) ? result.get(value) + 1 : 1);
        return result;
    }

    private static List<String> names(List<CaseResult> cases) {
        List<String> result = new ArrayList<String>();
        for (CaseResult item : cases) result.add(item.getCaseFile());
        return result;
    }

    private static final class Fixture {
        final Test test = new Test("run-id");
        final Path scenario;
        final Path result;
        final Map<String, String> paths = new HashMap<String, String>();

        Fixture(String name, int mode, String... cases) throws Exception {
            Path base = root.resolve(name);
            scenario = base.resolve("scenario/sql");
            result = base.resolve("sql/result/run-id");
            test.setScenarioRootPath(scenario.toString());
            test.setResult_dir(result.toString());
            test.setTestType("sql");
            test.setTestTypeAlias("sql");
            test.setTestBit("64bit");
            test.setRunMode(mode);
            test.setSiteRunTimes(1);
            test.setDbId("testdb");
            for (String namePart : cases) {
                Path path = scenario.resolve(namePart);
                Files.createDirectories(path.getParent());
                Files.write(path, new byte[0]);
                Path answer = Paths.get(TestUtil.getAnswerFile(path.toString()));
                Files.createDirectories(answer.getParent());
                Files.write(answer, new byte[0]);
                paths.put(namePart, path.toString());
                test.getCaseFileList().add(path.toString());
            }
            Method build = ConsoleBO.class.getDeclaredMethod("build", Test.class);
            build.setAccessible(true);
            build.invoke(new ConsoleBO(), test);
            for (String path : paths.values()) test.getCaseResultFromMap(path).setSuccessFul(true);
        }

        void verdict(String name, boolean run, boolean success, long time) {
            CaseResult item = test.getCaseResultFromMap(paths.get(name));
            item.setShouldRun(run);
            item.setSuccessFul(success);
            item.setTotalTime(time);
        }

        void summarize() throws Exception {
            TestUtil.makeSummary(test, test.getCatMap());
            if (test.getRunMode() == Test.MODE_RESULT) {
                for (String path : paths.values()) Files.createDirectories(Paths.get(TestUtil.getResultDir(test, path)));
                TestUtil.makeSummaryInfo(test, test.getCatMap(), null);
                TestUtil.saveResultSummary(test, test.getCatMap());
            }
            JunitXmlWriter.write(test, "offline-test");
        }

        Summary owner(String name) {
            String path = paths.get(name);
            return test.getSummaryFromMap(TestUtil.getResultDir(test, path));
        }

        void assertRows() throws Exception {
            Map<String, Integer> found = new TreeMap<String, Integer>();
            try (Stream<Path> files = Files.walk(result)) {
                for (Path file : (Iterable<Path>) files.filter(p -> p.getFileName().toString().equals("summary_info"))::iterator) {
                    for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                        for (String path : paths.values()) {
                            if (line.startsWith(path + ":")) {
                                String expectedDir = TestUtil.getResultDir(test, path);
                                equal(Paths.get(expectedDir), file.getParent(), "row directory for " + path);
                                CaseResult cr = test.getCaseResultFromMap(path);
                                String verdict = cr.isShouldRun() ? (cr.isSuccessFul() ? "ok" : "nok") : "";
                                equal(path + ":" + verdict + "    " + cr.getTotalTime() + "ms", line, "row verdict for " + path);
                                found.put(path, found.containsKey(path) ? found.get(path) + 1 : 1);
                            }
                        }
                    }
                }
            }
            equal(multiset(new ArrayList<String>(paths.values())), found, "unique physical summary rows");
        }

        void assertJunit(String... expected) throws Exception {
            Path file = result.resolve("linux_sql_64bit.xml");
            check(Files.exists(file), "JUnit exists " + file);
            if (!Files.exists(file)) return;
            Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file.toFile());
            NodeList nodes = doc.getElementsByTagName("testcase");
            List<String> actual = new ArrayList<String>();
            for (int i = 0; i < nodes.getLength(); i++) actual.add(((Element) nodes.item(i)).getAttribute("name"));
            List<String> wanted = new ArrayList<String>();
            for (String name : expected) wanted.add(paths.get(name));
            equal(multiset(wanted), multiset(actual), "JUnit case identities");
            int expectedFailures = 0;
            for (String name : expected) if (!test.getCaseResultFromMap(paths.get(name)).isSuccessFul()) expectedFailures++;
            equal(expectedFailures, doc.getElementsByTagName("failure").getLength(), "JUnit failures");
            System.out.println("JUnit expected=" + expected.length + " actual=" + nodes.getLength());
        }
    }

    private static void simple(String label, String... cases) throws Exception {
        Fixture f = new Fixture(label, Test.MODE_RESULT, cases);
        f.summarize();
        f.assertRows();
        f.assertJunit(cases);
        equal(cases.length, f.test.getSummary().getTotalCount(), label + " total");
        equal(multiset(new ArrayList<String>(f.paths.values())), multiset(names(f.test.getSummary().getCaseList())), label + " group aggregate cases");
        System.out.println(label + " rows=" + cases.length + " total=" + f.test.getSummary().getTotalCount());
    }

    private static void mixed(String label, String... cases) throws Exception {
        Fixture f = new Fixture(label, Test.MODE_RESULT, cases);
        f.verdict("parent/cases/a.sql", true, true, 7);
        f.verdict("parent/child/cases/b.sql", true, false, 11);
        f.verdict("parent/child/grand/cases/c.sql", false, false, 99);
        f.summarize();
        f.assertRows();
        f.assertJunit("parent/cases/a.sql", "parent/cases/a2.sql", "parent/child/cases/b.sql", "other/cases/d.sql");
        Summary rootSummary = f.test.getSummary();
        equal(5, rootSummary.getTotalCount(), label + " total");
        equal(3, rootSummary.getSuccessCount(), label + " success");
        equal(1, rootSummary.getFailCount(), label + " fail");
        equal(18L, rootSummary.getTotalTime(), label + " time");
        equal(multiset(new ArrayList<String>(f.paths.values())), multiset(names(rootSummary.getCaseList())), label + " group aggregate cases");
        Summary parent = f.owner("parent/cases/a.sql");
        Summary child = f.owner("parent/child/cases/b.sql");
        Summary grand = f.owner("parent/child/grand/cases/c.sql");
        equal(multiset(Arrays.asList(f.paths.get("parent/cases/a.sql"), f.paths.get("parent/cases/a2.sql"))), multiset(names(parent.getCaseList())), label + " parent ownership");
        equal(multiset(Collections.singletonList(f.paths.get("parent/child/cases/b.sql"))), multiset(names(child.getCaseList())), label + " child ownership");
        equal(4, parent.getTotalCount(), label + " parent total");
        equal(2, child.getTotalCount(), label + " child total");
        equal(1, grand.getTotalCount(), label + " grandchild total");
        check(parent.getChildSummaryMap().containsValue(child), label + " parent links child");
        check(child.getChildSummaryMap().containsValue(grand), label + " child links grandchild");
        SummaryInfo parentInfo = f.test.getSummaryInfoFromMap(parent.getResultDir());
        SummaryInfo childInfo = f.test.getSummaryInfoFromMap(child.getResultDir());
        equal(2, parentInfo.getOkList().size(), label + " passing SummaryInfo");
        equal(1, childInfo.getNokList().size(), label + " failing SummaryInfo");
        SummaryInfo info = f.test.getSummaryInfoFromMap(grand.getResultDir());
        equal(1, info.getNotRunList().size(), label + " skipped SummaryInfo");
        String first = new String(Files.readAllBytes(f.result.resolve("linux_sql_64bit.xml")), StandardCharsets.UTF_8);
        f.summarize();
        equal(5, f.test.getSummary().getTotalCount(), label + " repeated total");
        f.assertRows();
        f.assertJunit("parent/cases/a.sql", "parent/cases/a2.sql", "parent/child/cases/b.sql", "other/cases/d.sql");
        equal(first, new String(Files.readAllBytes(f.result.resolve("linux_sql_64bit.xml")), StandardCharsets.UTF_8), label + " repeated JUnit");
    }

    private static void noResult() throws Exception {
        Fixture f = new Fixture("no-result", Test.MODE_NO_RESULT, "parent/cases/a.sql", "parent/child/cases/b.sql");
        f.summarize();
        equal(2, f.test.getSummary().getTotalCount(), "no-result total");
        for (String name : f.paths.keySet()) {
            String category = name.substring(0, name.indexOf("/cases/"));
            Path expected = f.scenario.resolve("result/run-id/sql").resolve(category).normalize();
            equal(expected, Paths.get(f.owner(name).getResultDir()).normalize(), "no-result path " + name);
        }
        check(!Files.exists(f.result.resolve("linux_sql_64bit.xml")), "no-result emits no JUnit");
    }

    public static void main(String[] args) throws Exception {
        root = Paths.get(args[0]);
        for (Class<?> type : new Class<?>[] {ConsoleBO.class, TestUtil.class, Summary.class, SummaryInfo.class, CaseResult.class, Test.class, JunitXmlWriter.class}) {
            String location = type.getProtectionDomain().getCodeSource().getLocation().toString();
            check(Paths.get(type.getProtectionDomain().getCodeSource().getLocation().toURI()).equals(root.resolve("classes")), type.getSimpleName() + " loaded from " + location);
        }
        System.out.println("Production classes: " + TestUtil.class.getProtectionDomain().getCodeSource().getLocation());
        simple("one", "parent/child/cases/b.sql");
        simple("disjoint", "parent/cases/a.sql", "other/cases/b.sql");
        simple("nested", "parent/cases/a.sql", "parent/child/cases/b.sql");
        mixed("mixed", "parent/cases/a.sql", "parent/cases/a2.sql", "parent/child/cases/b.sql", "parent/child/grand/cases/c.sql", "other/cases/d.sql");
        mixed("mixed-reversed", "other/cases/d.sql", "parent/child/grand/cases/c.sql", "parent/child/cases/b.sql", "parent/cases/a2.sql", "parent/cases/a.sql");
        noResult();
        if (failures != 0) throw new AssertionError(failures + " checks failed");
        System.out.println("All SQL summary checks passed");
    }
}
