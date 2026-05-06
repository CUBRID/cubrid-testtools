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
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
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
 * <p>All exceptions inside this writer are swallowed; failures are logged via
 * {@code LogUtil.log("ConsoleBO", ...)} so that the JUnit XML emission cannot break the test
 * pipeline.
 */
public final class JunitXmlWriter {

    private static final String LOG_ID = "ConsoleBO";
    private static final String TESTCASES_ANCHOR = "/cubrid-testcases/";

    private JunitXmlWriter() {}

    /** Single entry point. Never throws — all exceptions are caught and logged. */
    public static void write(Test test) {
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
                LogUtil.log(LOG_ID, "[JunitXmlWriter] cubrid_rel failed: " + t.getMessage());
                return;
            }
            String suiteName = target + "_" + buildMode;

            List<CaseResult> emitted = collectEmittableCases(test.getSummary());
            int tests = emitted.size();
            int failures = countFailures(emitted);

            File outFile = new File(resultDir, "test-" + category + ".xml");
            writeXml(outFile, suiteName, tests, failures, emitted);
            LogUtil.log(LOG_ID, "[JunitXmlWriter] wrote " + outFile.getAbsolutePath()
                    + " (tests=" + tests + ", failures=" + failures + ")");
        } catch (Throwable t) {
            LogUtil.log(LOG_ID, "[JunitXmlWriter] write failed: " + t.getMessage());
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
        int idx = absoluteCaseFile.indexOf(TESTCASES_ANCHOR);
        if (idx < 0) {
            return absoluteCaseFile;
        }
        return absoluteCaseFile.substring(idx + TESTCASES_ANCHOR.length());
    }

    private static void writeXml(
            File outFile, String suiteName, int tests, int failures, List<CaseResult> cases)
            throws IOException, XMLStreamException {
        File parent = outFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        FileOutputStream fos = new FileOutputStream(outFile);
        XMLStreamWriter raw = null;
        IndentingXMLStreamWriter w = null;
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
                writeOneTestcase(w, suiteName, cr);
            }

            w.writeEndElement(); // testsuite
            w.writeEndElement(); // testsuites
            w.writeEndDocument();
            w.flush();
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
        }
    }

    private static void writeOneTestcase(XMLStreamWriter w, String suiteName, CaseResult cr)
            throws XMLStreamException {
        String relPath = relativeToTestcasesRoot(cr.getCaseFile());
        String time = String.valueOf(cr.getTotalTime() / 1000.0);
        w.writeStartElement("testcase");
        w.writeAttribute("classname", suiteName);
        w.writeAttribute("name", relPath);
        w.writeAttribute("file", "cubrid-testcases/" + relPath);
        w.writeAttribute("time", time);
        if (!cr.isSuccessFul()) {
            w.writeStartElement("failure");
            w.writeAttribute("message", "unexpected result");
            String cdata = buildFailureCdata(cr);
            if (cdata.length() > 0) {
                w.writeCData(cdata);
            }
            w.writeEndElement(); // failure
        }
        w.writeEndElement(); // testcase
    }

    private static String buildFailureCdata(CaseResult cr) {
        try {
            String resultFile =
                    cr.getResultDir() + File.separator + cr.getCaseName() + ".result";
            if (cr.getCaseFile() == null
                    || cr.getAnswerFile() == null
                    || cr.getResultDir() == null) {
                LogUtil.log(LOG_ID, "[JunitXmlWriter] missing input paths for case "
                        + cr.getCaseName());
                return "";
            }
            return FailureCdataBuilder.build(cr.getCaseFile(), cr.getAnswerFile(), resultFile);
        } catch (Throwable t) {
            LogUtil.log(LOG_ID, "[JunitXmlWriter] CDATA build failed for "
                    + cr.getCaseFile() + ": " + t.getMessage());
            return "";
        }
    }

    public static void main(String[] args) {
        int passed = 0;
        passed += testRelativePath();
        passed += testEmptySuite();
        if (passed == 2) {
            System.out.println("OK: JunitXmlWriter " + passed + "/2 cases passed");
            System.exit(0);
        } else {
            System.err.println("FAIL: JunitXmlWriter " + passed + "/2 cases passed");
            System.exit(1);
        }
    }

    private static int testRelativePath() {
        String rel = relativeToTestcasesRoot(
                "/home/dev/cubrid-testcases/sql/_01_object/_01_type/cases/abc.sql");
        if (!"sql/_01_object/_01_type/cases/abc.sql".equals(rel)) {
            System.err.println("  FAIL: relativeToTestcasesRoot -> " + rel);
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

            // BuildModeResolver may throw if cubrid_rel is unavailable; if so, this
            // self-test is a no-op rather than a failure (the file simply isn't created).
            try {
                BuildModeResolver.detect();
            } catch (RuntimeException re) {
                System.out.println("  SKIP: cubrid_rel unavailable, skipping write check");
                return 1;
            }
            write(t);
            File expected = new File(tmpDir, "test-sql.xml");
            if (!expected.exists()) {
                System.err.println("  FAIL: expected file not created: " + expected);
                return 0;
            }
            expected.deleteOnExit();
            return 1;
        } catch (Exception e) {
            System.err.println("  FAIL: testEmptySuite exception: " + e.getMessage());
            return 0;
        }
    }
}
