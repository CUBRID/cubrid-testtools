/**
 * Copyright (c) 2016, Search Solution Corporation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted
 * provided that the following conditions are met:
 *
 *   * Redistributions of source code must retain the above copyright notice, this list of
 *     conditions and the following disclaimer.
 *
 *   * Redistributions in binary form must reproduce the above copyright notice, this list of
 *     conditions and the following disclaimer in the documentation and/or other materials provided
 *     with the distribution.
 *
 *   * Neither the name of the copyright holder nor the names of its contributors may be used to
 *     endorse or promote products derived from this software without specific prior written
 *     permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY
 * WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.navercorp.cubridqa.cqt.console.util;

import com.navercorp.cubridqa.cqt.console.bean.CaseResult;
import com.navercorp.cubridqa.cqt.console.bean.Summary;
import com.navercorp.cubridqa.cqt.console.bean.Test;
import com.sun.xml.txw2.output.IndentingXMLStreamWriter;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

/**
 * Writes a CircleCI-compatible JUnit-style XML at
 * {@code ${test.getResult_dir()}/test-<category>.xml}.
 *
 * <p>Schema mirrors the existing {@code cubridci/docker/ci/docker-entrypoint.sh xsltproc}
 * output (so the entrypoint cleanup PR can simply remove that XSLT step):
 * <pre>
 *   &lt;testsuites&gt;
 *     &lt;testsuite name="$target_$mode" tests="N" failures="M"&gt;
 *       &lt;testcase classname="$target_$mode"
 *                  name="&lt;case rel path&gt;"
 *                  file="cubrid-testcases/&lt;case rel path&gt;"
 *                  time="&lt;sec&gt;"&gt;
 *         [optional] &lt;failure message="unexpected result"&gt;
 *           &lt;![CDATA[ [Query] ... [Diff] ... ]]&gt;
 *         &lt;/failure&gt;
 *       &lt;/testcase&gt;
 *     &lt;/testsuite&gt;
 *   &lt;/testsuites&gt;
 * </pre>
 *
 * <p>Only TYPE_SQL and TYPE_GROOVY cases that actually ran ({@code shouldRun==true}) are
 * emitted, matching the existing {@code TestUtil.saveResult} / {@code TestUtil.copyCaseAnswerFile}
 * gating.
 *
 * <p>The failure CDATA is prefixed with commit-pinned GitHub source links for the case and
 * answer files (same {@code ** Testcase : / ** Expected :} format as the legacy entrypoint
 * {@code .report} header). The links are derived from the testcases repo's own git metadata
 * ({@code remote.origin.url} + {@code rev-parse HEAD}, cached per repo root) and are silently
 * omitted when that metadata is unavailable.
 *
 * <p>All exceptions inside this writer are swallowed; failures are logged via
 * {@code LogUtil.log("ConsoleBO", ...)} so that the JUnit XML emission cannot break the test
 * pipeline.
 */
public final class JunitXmlWriter {

    // Recognized test-case repository directory names, longest first so that
    // "-private-ex" and "-private" win over the bare "cubrid-testcases".
    private static final String[] TESTCASES_ANCHORS = {
        "/cubrid-testcases-private-ex/",
        "/cubrid-testcases-private/",
        "/cubrid-testcases/"
    };

    // Cached "<remote-url>/blob/<HEAD-hash>" per testcases repo root. A null value is cached
    // too: it means git metadata was unavailable and the URL header is skipped for that repo.
    private static final Map<String, String> BASE_URL_CACHE = new HashMap<String, String>();

    private JunitXmlWriter() {}

    /** Single entry point. Never throws — all exceptions are caught and logged. */
    public static void write(Test test, String logId) {
        try {
            if (test == null) {
                return;
            }
            String resultDir = test.getResult_dir();
            if (resultDir == null || resultDir.length() == 0) {
                return;
            }
            String category = test.getTestType();
            if (category == null || category.length() == 0) {
                return;
            }
            String target = pickTarget(test);
            String buildMode;
            try {
                buildMode = BuildModeResolver.detect();
            } catch (Throwable t) {
                LogUtil.log(logId, "[JunitXmlWriter] write failed: "
                        + t.getClass().getName() + ": " + t.getMessage());
                return;
            }
            String suiteName = target + "_" + buildMode;

            List<CaseResult> emitted = collectEmittableCases(test.getSummary());
            int tests = emitted.size();
            int failures = countFailures(emitted);

            File outFile = new File(resultDir, "test-" + category + ".xml");
            writeXml(outFile, suiteName, tests, failures, emitted, logId);
            LogUtil.log(logId, "[JunitXmlWriter] wrote " + outFile.getAbsolutePath()
                    + " (tests=" + tests + ", failures=" + failures + ")");
        } catch (Throwable t) {
            LogUtil.log(logId, "[JunitXmlWriter] write failed: "
                    + t.getClass().getName() + ": " + t.getMessage());
        }
    }

    /** Walk Summary tree, collect TYPE_SQL/TYPE_GROOVY cases that were actually run. */
    private static List<CaseResult> collectEmittableCases(Summary root) {
        List<CaseResult> out = new ArrayList<CaseResult>();
        if (root == null) {
            return out;
        }
        walk(root, out);
        return out;
    }

    private static void walk(Summary s, List<CaseResult> out) {
        if (s == null) {
            return;
        }
        if (s.getType() == Summary.TYPE_BOTTOM) {
            for (CaseResult cr : s.getCaseList()) {
                if (cr == null || !cr.isShouldRun()) {
                    continue;
                }
                int t = cr.getType();
                if (t != CaseResult.TYPE_SQL && t != CaseResult.TYPE_GROOVY) {
                    continue;
                }
                out.add(cr);
            }
        } else {
            Iterator<Summary> it = s.getChildSummaryMap().values().iterator();
            while (it.hasNext()) {
                walk(it.next(), out);
            }
        }
    }

    private static int countFailures(List<CaseResult> cases) {
        int n = 0;
        for (CaseResult cr : cases) {
            if (!cr.isSuccessFul()) {
                n++;
            }
        }
        return n;
    }

    private static String pickTarget(Test test) {
        String alias = test.getTestTypeAlias();
        if (alias != null && alias.length() > 0) {
            return alias;
        }
        return test.getTestType();
    }

    static String relativeToTestcasesRoot(String absoluteCaseFile) {
        if (absoluteCaseFile == null) {
            return "";
        }
        for (int i = 0; i < TESTCASES_ANCHORS.length; i++) {
            String anchor = TESTCASES_ANCHORS[i];
            int idx = absoluteCaseFile.indexOf(anchor);
            if (idx >= 0) {
                return absoluteCaseFile.substring(idx + anchor.length());
            }
        }
        return absoluteCaseFile;
    }

    /**
     * Repository directory name (without surrounding slashes) that the case file lives under,
     * e.g. {@code cubrid-testcases-private}. Used as the {@code file=} attribute prefix so the
     * CI source links resolve to the correct repo. Defaults to {@code cubrid-testcases} when no
     * known anchor matches.
     */
    static String testcasesRepoName(String absoluteCaseFile) {
        if (absoluteCaseFile != null) {
            for (int i = 0; i < TESTCASES_ANCHORS.length; i++) {
                String anchor = TESTCASES_ANCHORS[i];
                if (absoluteCaseFile.indexOf(anchor) >= 0) {
                    return anchor.substring(1, anchor.length() - 1);
                }
            }
        }
        return "cubrid-testcases";
    }

    /**
     * Absolute path of the testcases repository the case file lives in (no trailing slash),
     * or {@code null} when the path contains no known anchor.
     */
    static String testcasesRepoRoot(String absoluteCaseFile) {
        if (absoluteCaseFile == null) {
            return null;
        }
        for (int i = 0; i < TESTCASES_ANCHORS.length; i++) {
            String anchor = TESTCASES_ANCHORS[i];
            int idx = absoluteCaseFile.indexOf(anchor);
            if (idx >= 0) {
                return absoluteCaseFile.substring(0, idx + anchor.length() - 1);
            }
        }
        return null;
    }

    /**
     * Commit-pinned source-link header, mirroring the legacy entrypoint {@code .report} format:
     * <pre>
     * ** Testcase : &lt;rel path&gt; - &lt;remote&gt;/blob/&lt;hash&gt;/&lt;rel path&gt;
     * ** Expected : &lt;rel path&gt; - &lt;remote&gt;/blob/&lt;hash&gt;/&lt;rel path&gt;
     * </pre>
     * Empty when the repo root is unknown or its git metadata is unavailable.
     */
    private static String buildUrlHeader(String caseFile, String answerFile) {
        String root = testcasesRepoRoot(caseFile);
        if (root == null) {
            return "";
        }
        String base = baseUrl(root);
        if (base == null) {
            return "";
        }
        String caseRel = relativeToTestcasesRoot(caseFile);
        String answerRel = relativeToTestcasesRoot(answerFile);
        StringBuilder sb = new StringBuilder();
        sb.append("** Testcase : ").append(caseRel)
                .append(" - ").append(base).append('/').append(caseRel).append('\n');
        sb.append("** Expected : ").append(answerRel)
                .append(" - ").append(base).append('/').append(answerRel).append('\n');
        sb.append('\n');
        return sb.toString();
    }

    /** "&lt;remote-url-without-.git&gt;/blob/&lt;HEAD-hash&gt;" for the repo, or null. Cached per root. */
    private static synchronized String baseUrl(String repoRoot) {
        if (BASE_URL_CACHE.containsKey(repoRoot)) {
            return BASE_URL_CACHE.get(repoRoot);
        }
        String url = composeBaseUrl(
                runGit(repoRoot, "config", "--get", "remote.origin.url"),
                runGit(repoRoot, "rev-parse", "HEAD"));
        BASE_URL_CACHE.put(repoRoot, url);
        return url;
    }

    /** Pure string assembly, separated so the self-test can cover it without invoking git. */
    static String composeBaseUrl(String remoteUrl, String headHash) {
        if (remoteUrl == null || headHash == null) {
            return null;
        }
        String remote = remoteUrl.trim();
        String hash = headHash.trim();
        if (remote.length() == 0 || hash.length() == 0) {
            return null;
        }
        if (remote.endsWith(".git")) {
            remote = remote.substring(0, remote.length() - 4);
        }
        return remote + "/blob/" + hash;
    }

    /** First stdout line of {@code git -C <repoRoot> <args>}, or null on any failure. */
    private static String runGit(String repoRoot, String... args) {
        try {
            List<String> cmd = new ArrayList<String>();
            cmd.add("git");
            cmd.add("-C");
            cmd.add(repoRoot);
            for (int i = 0; i < args.length; i++) {
                cmd.add(args[i]);
            }
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            p.getOutputStream().close();
            BufferedReader r = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), "UTF-8"));
            String first;
            try {
                first = r.readLine();
                while (r.readLine() != null) {
                    // drain remaining output so the process can exit
                }
            } finally {
                r.close();
            }
            int code = p.waitFor();
            return code == 0 ? first : null;
        } catch (IOException e) {
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    private static void writeXml(
            File outFile, String suiteName, int tests, int failures, List<CaseResult> cases,
            String logId)
            throws IOException, XMLStreamException {
        File parent = outFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs() && !parent.exists()) {
            throw new IOException("cannot create parent dir: " + parent);
        }
        // Sweep stale .tmp files left by previous crashed runs (older than 1 minute).
        // Skip the tmp file we are about to create so a concurrent sibling run is not disrupted.
        String currentTmpName = outFile.getName() + ".tmp";
        if (parent != null) {
            File[] stale = parent.listFiles();
            if (stale != null) {
                long now = System.currentTimeMillis();
                for (int i = 0; i < stale.length; i++) {
                    File f = stale[i];
                    String n = f.getName();
                    if (n.equals(currentTmpName)) {
                        continue;
                    }
                    if (n.startsWith("test-") && n.endsWith(".xml.tmp")
                            && now - f.lastModified() > 60000L) {
                        f.delete();
                    }
                }
            }
        }
        File tmpFile = new File(outFile.getAbsolutePath() + ".tmp");
        FileOutputStream fos = new FileOutputStream(tmpFile);
        XMLStreamWriter raw = null;
        IndentingXMLStreamWriter w = null;
        boolean success = false;
        try {
            XMLOutputFactory factory = XMLOutputFactory.newInstance();
            raw = factory.createXMLStreamWriter(fos, "UTF-8");
            w = new IndentingXMLStreamWriter(raw);
            w.writeStartDocument("UTF-8", "1.0");
            w.writeStartElement("testsuites");
            w.writeStartElement("testsuite");
            w.writeAttribute("name", suiteName);
            w.writeAttribute("tests", String.valueOf(tests));
            w.writeAttribute("failures", String.valueOf(failures));

            for (CaseResult cr : cases) {
                writeOneTestcase(w, suiteName, cr, logId);
            }

            w.writeEndElement(); // testsuite
            w.writeEndElement(); // testsuites
            w.writeEndDocument();
            w.flush();
            success = true;
        } finally {
            if (w != null) {
                try {
                    w.close();
                } catch (XMLStreamException ignore) {
                }
            }
            try {
                fos.close();
            } catch (IOException ignore) {
            }
            if (success) {
                if (outFile.exists() && !outFile.delete()) {
                    LogUtil.log(logId, "[JunitXmlWriter] could not delete existing " + outFile);
                }
                boolean renamed = tmpFile.renameTo(outFile);
                if (!renamed) {
                    LogUtil.log(logId, "[JunitXmlWriter] rename failed: tmp=" + tmpFile
                            + " final=" + outFile);
                    tmpFile.delete();
                }
            } else {
                tmpFile.delete();
            }
        }
    }

    private static void writeOneTestcase(
            XMLStreamWriter w, String suiteName, CaseResult cr, String logId)
            throws XMLStreamException {
        String relPath = relativeToTestcasesRoot(cr.getCaseFile());
        String repoName = testcasesRepoName(cr.getCaseFile());
        String time = String.format(Locale.ROOT, "%.3f", cr.getTotalTime() / 1000.0);
        w.writeStartElement("testcase");
        w.writeAttribute("classname", suiteName);
        w.writeAttribute("name", relPath);
        w.writeAttribute("file", repoName + "/" + relPath);
        w.writeAttribute("time", time);
        if (!cr.isSuccessFul()) {
            w.writeStartElement("failure");
            w.writeAttribute("message", "unexpected result");
            String cdata = buildFailureCdata(cr, logId);
            if (cdata.length() > 0) {
                w.writeCData(cdata);
            }
            w.writeEndElement(); // failure
        }
        w.writeEndElement(); // testcase
    }

    private static String buildFailureCdata(CaseResult cr, String logId) {
        try {
            if (cr.getCaseFile() == null
                    || cr.getAnswerFile() == null
                    || cr.getResultDir() == null) {
                LogUtil.log(logId, "[JunitXmlWriter] missing input paths for case "
                        + cr.getCaseName());
                return "";
            }
            // Match the path convention TestUtil.saveResult uses to write the file ("/"),
            // not the platform separator.
            String resultFile = cr.getResultDir() + "/" + cr.getCaseName() + ".result";
            String payload =
                    FailureCdataBuilder.build(cr.getCaseFile(), cr.getAnswerFile(), resultFile);
            if (payload.length() == 0) {
                return "";
            }
            String header = buildUrlHeader(cr.getCaseFile(), cr.getAnswerFile());
            if (header.length() == 0) {
                return payload;
            }
            return FailureCdataBuilder.cdataSafe(header) + payload;
        } catch (Throwable t) {
            LogUtil.log(logId, "[JunitXmlWriter] CDATA build failed for "
                    + cr.getCaseFile() + ": " + t.getClass().getName() + ": " + t.getMessage());
            return "";
        }
    }

    public static void main(String[] args) {
        int passed = 0;
        passed += testRelativePath();
        passed += testTimeFormatting();
        passed += testSourceLinks();
        passed += testEmptySuite();
        if (passed == 4) {
            System.out.println("OK: JunitXmlWriter " + passed + "/4 cases passed");
            System.exit(0);
        } else {
            System.err.println("FAIL: JunitXmlWriter " + passed + "/4 cases passed");
            System.exit(1);
        }
    }

    private static int testSourceLinks() {
        String base = composeBaseUrl("https://github.com/CUBRID/cubrid-testcases.git", "abc123\n");
        if (!"https://github.com/CUBRID/cubrid-testcases/blob/abc123".equals(base)) {
            System.err.println("  FAIL: composeBaseUrl(.git) -> " + base);
            return 0;
        }
        if (!"https://x/y/blob/h".equals(composeBaseUrl("https://x/y", "h"))) {
            System.err.println("  FAIL: composeBaseUrl(no .git)");
            return 0;
        }
        if (composeBaseUrl(null, "h") != null
                || composeBaseUrl("u", null) != null
                || composeBaseUrl(" ", "h") != null) {
            System.err.println("  FAIL: composeBaseUrl null/blank should be null");
            return 0;
        }
        if (!"/home/dev/cubrid-testcases-private".equals(testcasesRepoRoot(
                "/home/dev/cubrid-testcases-private/shell_ext/cases/x.sql"))) {
            System.err.println("  FAIL: testcasesRepoRoot(private)");
            return 0;
        }
        if (testcasesRepoRoot("/some/other/path/abc.sql") != null) {
            System.err.println("  FAIL: testcasesRepoRoot(no anchor) should be null");
            return 0;
        }
        return 1;
    }

    private static int testTimeFormatting() {
        // 20_000_000 ms = 20000 seconds; String.valueOf(20000000/1000.0) -> "2.0E7" (scientific)
        long totalTimeMs = 20000000L;
        String time = String.format(Locale.ROOT, "%.3f", totalTimeMs / 1000.0);
        boolean startsWithDigit = time.length() > 0 && Character.isDigit(time.charAt(0));
        boolean hasDot = time.indexOf('.') >= 0;
        boolean hasExponent = time.indexOf('E') >= 0 || time.indexOf('e') >= 0;
        if (!startsWithDigit || !hasDot || hasExponent) {
            System.err.println("  FAIL: testTimeFormatting: got '" + time + "'");
            return 0;
        }
        return 1;
    }

    private static int testRelativePath() {
        String rel = relativeToTestcasesRoot(
                "/home/dev/cubrid-testcases/sql/_01_object/_01_type/cases/abc.sql");
        if (!"sql/_01_object/_01_type/cases/abc.sql".equals(rel)) {
            System.err.println("  FAIL: relativeToTestcasesRoot -> " + rel);
            return 0;
        }
        if (!"cubrid-testcases".equals(testcasesRepoName(
                "/home/dev/cubrid-testcases/sql/_01_object/_01_type/cases/abc.sql"))) {
            System.err.println("  FAIL: repoName(public)");
            return 0;
        }
        // Private repos must strip their own prefix, not fall through to the absolute path.
        String priv = relativeToTestcasesRoot(
                "/home/dev/cubrid-testcases-private/shell_ext/cases/x.sql");
        if (!"shell_ext/cases/x.sql".equals(priv)) {
            System.err.println("  FAIL: private relativeToTestcasesRoot -> " + priv);
            return 0;
        }
        if (!"cubrid-testcases-private".equals(testcasesRepoName(
                "/home/dev/cubrid-testcases-private/shell_ext/cases/x.sql"))) {
            System.err.println("  FAIL: repoName(private)");
            return 0;
        }
        String privEx = relativeToTestcasesRoot(
                "/home/dev/cubrid-testcases-private-ex/shell/cases/y.sql");
        if (!"shell/cases/y.sql".equals(privEx)) {
            System.err.println("  FAIL: private-ex relativeToTestcasesRoot -> " + privEx);
            return 0;
        }
        if (!"cubrid-testcases-private-ex".equals(testcasesRepoName(
                "/home/dev/cubrid-testcases-private-ex/shell/cases/y.sql"))) {
            System.err.println("  FAIL: repoName(private-ex)");
            return 0;
        }
        String passthru = relativeToTestcasesRoot("/some/other/path/abc.sql");
        if (!"/some/other/path/abc.sql".equals(passthru)) {
            System.err.println("  FAIL: passthru -> " + passthru);
            return 0;
        }
        return 1;
    }

    private static int testEmptySuite() {
        // Build an empty Summary tree and ensure write() doesn't throw and produces a file.
        try {
            File tmpDir = File.createTempFile("jxw", ".d");
            tmpDir.delete();
            if (!tmpDir.mkdir()) {
                System.err.println("  FAIL: cannot create temp dir");
                return 0;
            }
            tmpDir.deleteOnExit();

            Test t = new Test("self-test-id");
            t.setResult_dir(tmpDir.getAbsolutePath());
            t.setTestType("sql");
            t.setTestTypeAlias("sql_self");
            Summary s = new Summary();
            s.setType(Summary.TYPE_BOTTOM);
            t.setSummary(s);

            // BuildModeResolver may throw if cubrid_rel is unavailable.
            // In CI environments, cubrid_rel must be present — treat absence as a failure.
            // Outside CI, skip gracefully.
            boolean inCi = System.getenv("CI") != null || System.getenv("CIRCLECI") != null;
            try {
                BuildModeResolver.detect();
            } catch (RuntimeException re) {
                if (inCi) {
                    System.err.println("  FAIL: cubrid_rel unavailable in CI: " + re.getMessage());
                    return 0;
                }
                System.out.println("  SKIP: cubrid_rel unavailable, skipping write check");
                return 1;
            }
            write(t, "JunitXmlWriter-self-test");
            File expected = new File(tmpDir, "test-sql.xml");
            if (!expected.exists()) {
                System.err.println("  FAIL: expected file not created: " + expected);
                return 0;
            }
            expected.deleteOnExit();
            // Content check: file must contain the empty-suite attributes.
            java.io.InputStream in = new java.io.FileInputStream(expected);
            byte[] buf = new byte[(int) expected.length()];
            try {
                int off = 0;
                int rem = buf.length;
                while (rem > 0) {
                    int r = in.read(buf, off, rem);
                    if (r < 0) break;
                    off += r; rem -= r;
                }
            } finally {
                in.close();
            }
            String content = new String(buf, "UTF-8");
            if (!content.contains("tests=\"0\"") || !content.contains("failures=\"0\"")) {
                System.err.println("  FAIL: testEmptySuite content missing tests/failures attrs: "
                        + content);
                return 0;
            }
            return 1;
        } catch (Exception e) {
            System.err.println("  FAIL: testEmptySuite exception: " + e.getMessage());
            return 0;
        }
    }
}
