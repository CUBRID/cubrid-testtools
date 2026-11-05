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

import com.navercorp.cubridqa.cqt.console.bean.CaseResult;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Writes the JUnit XML of a CCI run ({@code sql/bin/run.sh} with {@code sql_interface_type=cci}).
 * The C runner {@code sql_by_cci/ccqt} leaves only {@code summary.info}; this reads it back into
 * {@link CaseResult}s for {@link JunitXmlWriter}, so the report has the JDBC runner's form.
 *
 * <p>Usage: {@code CciJunitXml <result dir> <scenario> <test type> <test bit> <cubrid build> <log
 * id> <ccqt start ms> <ccqt end ms>}
 */
public final class CciJunitXml {

    // getRelativeCasePath in sql_by_cci/execute.c cuts the case paths of summary.info at the
    // first of these, in this order.
    private static final String[] ANCHORS = {
        "/scenario/", "/cubrid-testcases-private/", "/cubrid-testcases/"
    };

    // The elapsed ms comes from two gettimeofday calls, so it can be negative.
    private static final Pattern LINE = Pattern.compile("^TestCase: (.+)\t:(OK|NOK):(-?\\d+)$");

    private CciJunitXml() {}

    public static void main(String[] args) {
        if (args.length != 8) {
            System.out.println(
                    "usage: CciJunitXml <result dir> <scenario> <test type> <test bit>"
                            + " <cubrid build> <log id> <ccqt start ms> <ccqt end ms>");
            return;
        }
        String logId = args[5];
        try {
            long start = Long.parseLong(args[6]);
            JunitXmlWriter.write(
                    args[0],
                    args[2],
                    args[3],
                    args[4],
                    read(args[0], args[1]),
                    logId,
                    start,
                    Long.parseLong(args[7]) - start);
        } catch (Throwable t) {
            LogUtil.log(logId, "[CciJunitXml] " + t.getClass().getName() + ": " + t.getMessage());
        }
    }

    private static List<CaseResult> read(String resultDir, String scenario) throws IOException {
        String root = repoRoot(scenario);
        if (root == null) {
            throw new IOException("no testcases root in " + scenario);
        }
        List<CaseResult> out = new ArrayList<CaseResult>();
        File summary = new File(resultDir, "summary.info");
        try (BufferedReader r =
                new BufferedReader(new InputStreamReader(new FileInputStream(summary), "UTF-8"))) {
            String line;
            while ((line = r.readLine()) != null) {
                Matcher m = LINE.matcher(line);
                if (m.matches()) {
                    out.add(
                            toCase(
                                    resultDir,
                                    root + m.group(1),
                                    m.group(1),
                                    "OK".equals(m.group(2)),
                                    Long.parseLong(m.group(3))));
                }
            }
        }
        return out;
    }

    private static CaseResult toCase(
            String resultDir, String caseFile, String rel, boolean ok, long ms) {
        int slash = rel.lastIndexOf('/');
        CaseResult cr = new CaseResult();
        cr.setType(CaseResult.TYPE_SQL);
        cr.setShouldRun(true);
        cr.setSuccessFul(ok);
        cr.setTotalTime(Math.max(0, ms));
        cr.setCaseFile(caseFile);
        cr.setCaseName(rel.substring(slash + 1, rel.length() - ".sql".length()));
        // execute.c copies a failed case's .result into the result dir under the case's path.
        cr.setResultDir(resultDir + "/" + rel.substring(0, slash));
        cr.setAnswerFile(answerFile(caseFile));
        return cr;
    }

    // The answer execute.c compared against (getanswerfile): the .answer_cci when there is one.
    private static String answerFile(String caseFile) {
        int i = caseFile.indexOf("/cases/");
        String answer =
                caseFile.substring(0, i)
                        + "/answers/"
                        + caseFile.substring(i + "/cases/".length(), caseFile.length() - 3)
                        + "answer";
        return new File(answer + "_cci").exists() ? answer + "_cci" : answer;
    }

    private static String repoRoot(String scenario) {
        String s = scenario.endsWith("/") ? scenario : scenario + "/";
        for (String a : ANCHORS) {
            int i = s.indexOf(a);
            if (i >= 0) {
                return s.substring(0, i + a.length());
            }
        }
        return null;
    }
}
