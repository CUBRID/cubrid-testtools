/**
 * Copyright (c) 2016, Search Solution Corporation. All rights reserved.
 *
 * <p>Redistribution and use in source and binary forms, with or without modification, are permitted
 * provided that the following conditions are met:
 *
 * <p>* Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * <p>* Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided with
 * the distribution.
 *
 * <p>* Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written permission.
 *
 * <p>THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY
 * WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.navercorp.cubridqa.cqt.console.util;

import com.navercorp.cubridqa.common.CommonUtils;
import com.navercorp.cubridqa.common.junit.JunitCase;
import com.navercorp.cubridqa.common.junit.JunitSuite;
import com.navercorp.cubridqa.common.junit.JunitXml;
import com.navercorp.cubridqa.cqt.console.bean.CaseResult;
import com.navercorp.cubridqa.cqt.console.bean.Summary;
import com.navercorp.cubridqa.cqt.console.bean.Test;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import name.fraser.neil.plaintext.UnifiedDiffUtil;

/**
 * Writes a CircleCI-compatible JUnit-style XML at {@code <result dir>/<suite>.xml}, where {@code
 * <suite>} is {@code <os>_<testType>_<testBit>_<build type>} (e.g. {@code
 * linux_sql_64bit_optdebug}), the build type as {@code cubrid_rel} prints it. The build type is in
 * the file name too, so a report read on its own still says which build it judged.
 *
 * <p>A case with no answer file did not run, so it is {@code skipped}.
 *
 * <p>The failure CDATA is prefixed with commit-pinned GitHub source links for the case and answer
 * files (same {@code ** Testcase : / ** Expected :} format as the legacy entrypoint {@code .report}
 * header). The links are derived from the testcases repo's own git metadata ({@code
 * remote.origin.url} + {@code rev-parse HEAD}, cached per repo root) and are silently omitted when
 * that metadata is unavailable.
 *
 * <p>All exceptions inside this writer are swallowed; failures are logged via {@code
 * LogUtil.log("ConsoleBO", ...)} so that the JUnit XML emission cannot break the test pipeline.
 */
public final class JunitXmlWriter {

    // Recognized test-case repository directory names, longest first so that
    // "-private-ex" and "-private" win over the bare "cubrid-testcases".
    private static final String[] TESTCASES_ANCHORS = {
        "/cubrid-testcases-private-ex/", "/cubrid-testcases-private/", "/cubrid-testcases/"
    };

    // Cached "<remote-url>/blob/<HEAD-hash>" per testcases repo root. A null value is cached
    // too: it means git metadata was unavailable and the URL header is skipped for that repo.
    private static final Map<String, String> BASE_URL_CACHE = new HashMap<String, String>();

    // Cached build type from cubrid_rel. Guarded by detectBuildType().
    private static String BUILD_TYPE;

    private JunitXmlWriter() {}

    /**
     * Entry point of the JDBC runner. The suite ran {@code suiteMillis} from {@code suiteStart}
     * (epoch ms). Never throws — all exceptions are caught and logged.
     */
    public static void write(Test test, String logId, long suiteStart, long suiteMillis) {
        try {
            if (test == null) {
                return;
            }
            if (test.getRunMode() != Test.MODE_RESULT) {
                // Only the answer-comparison mode produces pass/fail judgements; emitting a
                // report from MODE_MAKE_ANSWER/MODE_RUN would show every case as passed.
                return;
            }
            List<CaseResult> emitted = new ArrayList<CaseResult>();
            walk(test.getSummary(), emitted);

            String version = test.getDbVersion();
            String build = test.getDbBuild();
            // run.sh splits cubrid_rel's version in two; joined, they equal shell's cubrid_build
            String cubridBuild =
                    !CommonUtils.isEmpty(version) && !CommonUtils.isEmpty(build)
                            ? version + "." + build
                            : null;
            write(
                    test.getResult_dir(),
                    test.getTestType(),
                    test.getTestBit(),
                    cubridBuild,
                    emitted,
                    logId,
                    suiteStart,
                    suiteMillis);
        } catch (Throwable t) {
            LogUtil.log(
                    logId,
                    "[JunitXmlWriter] write failed: "
                            + t.getClass().getName()
                            + ": "
                            + t.getMessage());
        }
    }

    /**
     * Writes {@code <resultDir>/<suite>.xml} for the given cases. The suite ran {@code suiteMillis}
     * from {@code suiteStart} (epoch ms). Never throws.
     */
    public static void write(
            String resultDir,
            String testType,
            String testBit,
            String cubridBuild,
            List<CaseResult> cases,
            String logId,
            long suiteStart,
            long suiteMillis) {
        try {
            if (resultDir == null || resultDir.length() == 0) {
                return;
            }
            if (testType == null || testType.length() == 0) {
                return;
            }
            String buildType = detectBuildType();
            String suiteName = pickTarget(testType, testBit) + "_" + buildType;

            JunitSuite suite = new JunitSuite(suiteName, suiteStart);
            suite.setMillis(suiteMillis);
            suite.addProperty("category", testType);
            suite.addProperty("os", SystemUtil.getOS());
            suite.addProperty("bits", testBit);
            suite.addProperty("build_type", buildType);
            if (!CommonUtils.isEmpty(cubridBuild)) {
                suite.addProperty("cubrid_build", cubridBuild);
            }
            File outFile = new File(resultDir, suiteName + ".xml");
            JunitXml xml = new JunitXml(outFile, suite);
            boolean closed = false;
            try {
                for (CaseResult cr : cases) {
                    xml.add(toCase(cr, logId));
                }
                xml.close();
                closed = true;
            } finally {
                if (!closed) {
                    xml.discard();
                }
            }
            LogUtil.log(
                    logId,
                    "[JunitXmlWriter] wrote "
                            + outFile.getAbsolutePath()
                            + " (tests="
                            + cases.size()
                            + ")");
        } catch (Throwable t) {
            LogUtil.log(
                    logId,
                    "[JunitXmlWriter] write failed: "
                            + t.getClass().getName()
                            + ": "
                            + t.getMessage());
        }
    }

    private static void walk(Summary s, List<CaseResult> out) {
        if (s == null) {
            return;
        }
        if (s.getType() == Summary.TYPE_BOTTOM) {
            for (CaseResult cr : s.getCaseList()) {
                if (cr == null) {
                    continue;
                }
                int t = cr.getType();
                if (t != CaseResult.TYPE_SQL && t != CaseResult.TYPE_GROOVY) {
                    continue;
                }
                out.add(cr);
            }
        } else {
            for (Summary child : s.getChildSummaryMap().values()) {
                walk(child, out);
            }
        }
    }

    private static JunitCase toCase(CaseResult cr, String logId) {
        String relPath = relativeToTestcasesRoot(cr.getCaseFile());
        // Without a known repo anchor relPath is the absolute path; prefixing it with a
        // repo name would yield a bogus "cubrid-testcases//abs/path", so emit it as-is.
        String fileAttr =
                findAnchor(cr.getCaseFile()) == null
                        ? relPath
                        : testcasesRepoName(cr.getCaseFile()) + "/" + relPath;
        if (!cr.isShouldRun()) {
            return JunitCase.skipped(relPath, fileAttr, "no answer file");
        }
        if (cr.isSuccessFul()) {
            return JunitCase.passed(relPath, fileAttr, cr.getTotalTime());
        }
        if (cr.isHasCore()) {
            return new JunitCase(
                    relPath,
                    fileAttr,
                    cr.getTotalTime(),
                    JunitCase.Result.ERROR,
                    "core file found",
                    "CoreDump",
                    buildFailureCdata(cr, logId));
        }
        return new JunitCase(
                relPath,
                fileAttr,
                cr.getTotalTime(),
                JunitCase.Result.FAILURE,
                "unexpected result",
                "UnexpectedResult",
                buildFailureCdata(cr, logId));
    }

    /**
     * Suite label prefix identical to the one the legacy entrypoint derived from the schedule
     * result-directory name: {@code <os>_<testType>_<testBit>} (e.g. {@code linux_sql_64bit}).
     * Built from the same values {@code TestUtil.getTestId} uses to compose that directory name. An
     * unset testBit is skipped (defensive; the CQT agent entry points always set it).
     */
    private static String pickTarget(String testType, String bit) {
        StringBuilder sb = new StringBuilder(SystemUtil.getOS());
        sb.append('_').append(testType);
        if (bit != null && bit.length() > 0) {
            sb.append('_').append(bit);
        }
        return sb.toString();
    }

    /** First matching testcases-repo anchor in {@code path} (longest first), or null. */
    private static String findAnchor(String path) {
        if (path != null) {
            for (int i = 0; i < TESTCASES_ANCHORS.length; i++) {
                if (path.indexOf(TESTCASES_ANCHORS[i]) >= 0) {
                    return TESTCASES_ANCHORS[i];
                }
            }
        }
        return null;
    }

    private static String relativeToTestcasesRoot(String absoluteCaseFile) {
        String anchor = findAnchor(absoluteCaseFile);
        if (anchor == null) {
            return absoluteCaseFile == null ? "" : absoluteCaseFile;
        }
        return absoluteCaseFile.substring(absoluteCaseFile.indexOf(anchor) + anchor.length());
    }

    /**
     * Repository directory name (without surrounding slashes) that the case file lives under, e.g.
     * {@code cubrid-testcases-private}. Used as the {@code file=} attribute prefix so the CI source
     * links resolve to the correct repo. Defaults to {@code cubrid-testcases} when no known anchor
     * matches.
     */
    private static String testcasesRepoName(String absoluteCaseFile) {
        String anchor = findAnchor(absoluteCaseFile);
        return anchor == null ? "cubrid-testcases" : anchor.substring(1, anchor.length() - 1);
    }

    /**
     * Absolute path of the testcases repository the case file lives in (no trailing slash), or
     * {@code null} when the path contains no known anchor.
     */
    private static String testcasesRepoRoot(String absoluteCaseFile) {
        String anchor = findAnchor(absoluteCaseFile);
        if (anchor == null) {
            return null;
        }
        return absoluteCaseFile.substring(
                0, absoluteCaseFile.indexOf(anchor) + anchor.length() - 1);
    }

    /**
     * Commit-pinned source-link header, mirroring the legacy entrypoint {@code .report} format:
     *
     * <pre>
     * ** Testcase : &lt;rel path&gt; - &lt;remote&gt;/blob/&lt;hash&gt;/&lt;rel path&gt;
     * ** Expected : &lt;rel path&gt; - &lt;remote&gt;/blob/&lt;hash&gt;/&lt;rel path&gt;
     * </pre>
     *
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
        return headerLine("Testcase", relativeToTestcasesRoot(caseFile), base)
                + headerLine("Expected", relativeToTestcasesRoot(answerFile), base)
                + "\n";
    }

    /** One commit-pinned source-link line: {@code ** <label> : <rel> - <base>/<rel>\n}. */
    private static String headerLine(String label, String rel, String base) {
        return "** " + label + " : " + rel + " - " + base + '/' + rel + "\n";
    }

    /**
     * "&lt;remote-url-without-.git&gt;/blob/&lt;HEAD-hash&gt;" for the repo, or null. Cached per
     * root; a null result is cached too, so the URL header stays skipped for that repo.
     */
    private static synchronized String baseUrl(String repoRoot) {
        if (BASE_URL_CACHE.containsKey(repoRoot)) {
            return BASE_URL_CACHE.get(repoRoot);
        }
        String remoteUrl =
                runCommand(true, "git", "-C", repoRoot, "config", "--get", "remote.origin.url");
        String headHash = runCommand(true, "git", "-C", repoRoot, "rev-parse", "HEAD");
        String url = null;
        if (remoteUrl != null && headHash != null) {
            String remote = remoteUrl.trim();
            String hash = headHash.trim();
            if (remote.length() > 0 && hash.length() > 0) {
                if (remote.endsWith(".git")) {
                    remote = remote.substring(0, remote.length() - 4);
                }
                url = remote + "/blob/" + hash;
            }
        }
        BASE_URL_CACHE.put(repoRoot, url);
        return url;
    }

    /**
     * Current CUBRID build type from {@code cubrid_rel} output (e.g. "optdebug", "coverage-debug").
     * The exit code is deliberately not checked: cubrid_rel may exit non-zero in some environments
     * while still printing a valid banner, and the content check is what matters.
     *
     * @throws RuntimeException if cubrid_rel cannot be invoked or prints no build type.
     */
    private static synchronized String detectBuildType() {
        if (BUILD_TYPE != null) {
            return BUILD_TYPE;
        }
        String out = runCommand(false, "cubrid_rel");
        if (out == null) {
            throw new RuntimeException("cubrid_rel invocation failed");
        }
        String[] build = CommonUtils.parseCubridRel(out);
        if (build == null) {
            throw new RuntimeException("cubrid_rel output has no build type: " + out);
        }
        BUILD_TYPE = build[1];
        return BUILD_TYPE;
    }

    /** Entire UTF-8 content of the file. Errors propagate to buildFailureCdata's guard. */
    private static String readFile(String path) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r =
                new BufferedReader(new InputStreamReader(new FileInputStream(path), "UTF-8"))) {
            String line;
            while ((line = r.readLine()) != null) {
                sb.append(line).append('\n');
            }
        }
        return sb.toString();
    }

    /**
     * Entire stdout of the command (stderr merged), or null on failure. When {@code
     * requireCleanExit} is true a non-zero exit code also yields null.
     */
    private static String runCommand(boolean requireCleanExit, String... cmd) {
        try {
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            p.getOutputStream().close();
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r =
                    new BufferedReader(new InputStreamReader(p.getInputStream(), "UTF-8"))) {
                String line;
                while ((line = r.readLine()) != null) {
                    sb.append(line).append('\n');
                }
            }
            int code = p.waitFor();
            return (code == 0 || !requireCleanExit) ? sb.toString() : null;
        } catch (IOException e) {
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    private static String buildFailureCdata(CaseResult cr, String logId) {
        try {
            if (cr.getCaseFile() == null
                    || cr.getAnswerFile() == null
                    || cr.getResultDir() == null) {
                LogUtil.log(
                        logId, "[JunitXmlWriter] missing input paths for case " + cr.getCaseName());
                return "";
            }
            // Match the path convention TestUtil.saveResult uses to write the file ("/"),
            // not the platform separator.
            String resultFile = cr.getResultDir() + "/" + cr.getCaseName() + ".result";
            String payload;
            if (cr.getType() == CaseResult.TYPE_SQL) {
                payload =
                        FailureCdataBuilder.build(cr.getCaseFile(), cr.getAnswerFile(), resultFile);
            } else {
                // Groovy cases have no SQL statement/block structure for the lockstep walk;
                // fall back to the whole-file diff the legacy entrypoint produced.
                payload =
                        "[Diff]\n"
                                + UnifiedDiffUtil.unifiedDiff(
                                        readFile(cr.getAnswerFile()),
                                        readFile(resultFile),
                                        "answer",
                                        "actual");
            }
            if (payload.length() == 0) {
                return "";
            }
            String header = buildUrlHeader(cr.getCaseFile(), cr.getAnswerFile());
            if (header.length() == 0) {
                return payload;
            }
            return header + payload;
        } catch (Throwable t) {
            LogUtil.log(
                    logId,
                    "[JunitXmlWriter] CDATA build failed for "
                            + cr.getCaseFile()
                            + ": "
                            + t.getClass().getName()
                            + ": "
                            + t.getMessage());
            return "";
        }
    }
}
