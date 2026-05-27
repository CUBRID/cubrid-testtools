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

import com.navercorp.cubridqa.cqt.webconsole.compare.ResultReader;
import com.navercorp.cubridqa.cqt.webconsole.compare.TestReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import name.fraser.neil.plaintext.UnifiedDiffUtil;

/**
 * Builds the {@code <failure>} CDATA payload for a failing SQL/MEDIUM test case.
 *
 * <p>Reuses {@link TestReader} (which delegates to SQLParser/LineScanner; PLCSQL blocks are
 * already grouped into one statement upstream) and {@link ResultReader} (which splits answer
 * and result files by the canonical 51-{@code =} separator emitted at
 * ConsoleBO.executeSqlFile L1267) to align statements with their expected/actual output blocks
 * (see Compare.java L103-110 for the same pattern). For each statement whose answer block
 * differs from its result block, emits a {@code [Query]} + {@code [Diff]} section. Multiple
 * failing statements are separated by a boundary line.
 *
 * <p><b>Coupling risk — 1:1 walk assumption.</b> This class walks {@link TestReader} and
 * {@link ResultReader} in lockstep (one statement → one output block). That assumption holds
 * only when every call to {@link TestReader#nextStatement()} corresponds to exactly one
 * {@code ===} separator written by {@code ConsoleBO.executeSqlFile} (L1270/1273). It can
 * drift when {@code TestReader.nextStatement()} silently concatenates meta lines (lines
 * starting with {@code --}, {@code $}, or {@code autocommit}) onto the next executable
 * statement, causing the statement count to differ from the block count. If drift occurs,
 * the asymmetric-truncation guard fires and the diff is aborted. Compare.java uses the same
 * 1:1 walk pattern and carries the same risk.
 */
public final class FailureCdataBuilder {

    private static final String STATEMENT_BOUNDARY = "--- statement boundary ---";

    private FailureCdataBuilder() {}

    /**
     * Build the CDATA payload describing a failure.
     *
     * @param caseFilePath original .sql case file
     * @param answerFilePath expected output file (.answer)
     * @param resultFilePath actual output file (.result, written by TestUtil.saveResult)
     * @return CDATA-safe string suitable to embed inside {@code <![CDATA[...]]>}
     * @throws IOException if any of the input files cannot be read or parsed
     */
    public static String build(String caseFilePath, String answerFilePath, String resultFilePath)
            throws IOException {
        List<FailingStatement> failing = collectFailingStatements(
                caseFilePath, answerFilePath, resultFilePath);
        if (failing.isEmpty()) {
            return "";
        }
        return cdataSafe(renderLayout(failing));
    }

    private static final class FailingStatement {
        final String sql;
        final List<String> answerLines;
        final List<String> resultLines;

        FailingStatement(String sql, List<String> a, List<String> r) {
            this.sql = sql;
            this.answerLines = a;
            this.resultLines = r;
        }
    }

    private static List<FailingStatement> collectFailingStatements(
            String caseFilePath, String answerFilePath, String resultFilePath) throws IOException {
        List<FailingStatement> out = new ArrayList<FailingStatement>();
        TestReader tr = null;
        ResultReader ar = null;
        ResultReader rr = null;
        try {
            tr = new TestReader(caseFilePath);
            ar = new ResultReader(answerFilePath);
            rr = new ResultReader(resultFilePath);
            String sql;
            while ((sql = tr.nextStatement()) != null) {
                List<String> answerBlock = ar.getNextBlockText();
                List<String> resultBlock = rr.getNextBlockText();
                // Detect asymmetric truncation: one side ran out of blocks, the other didn't.
                if ((answerBlock == null && resultBlock != null)
                        || (answerBlock != null && resultBlock == null)) {
                    String marker = "[Query]\n" + sql + (sql.endsWith("\n") ? "" : "\n")
                            + "\n[Diff]\n"
                            + "<truncation: answer file and result file have different block counts,"
                            + " diff aborted>\n";
                    List<String> markerLines = new ArrayList<String>();
                    markerLines.add(marker);
                    out.add(new FailingStatement(sql,
                            markerLines, new ArrayList<String>()));
                    break;
                }
                if (!blocksEqual(answerBlock, resultBlock)) {
                    out.add(new FailingStatement(sql,
                            answerBlock != null ? answerBlock : new ArrayList<String>(),
                            resultBlock != null ? resultBlock : new ArrayList<String>()));
                }
            }
            return out;
        } catch (Exception e) {
            if (e instanceof IOException) {
                throw (IOException) e;
            }
            // Use toString() so the wrapped cause is identifiable even when getMessage() is null.
            throw new IOException(e.toString(), e);
        } finally {
            if (tr != null) {
                try {
                    tr.closeFile();
                } catch (Exception ignore) {
                }
            }
            closeResultReader(ar);
            closeResultReader(rr);
        }
    }

    private static void closeResultReader(ResultReader rr) {
        if (rr == null) {
            return;
        }
        try {
            rr.close();
        } catch (Exception ignore) {
        }
    }

    private static boolean blocksEqual(List<String> a, List<String> b) {
        if (a == null && b == null) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        return a.equals(b);
    }

    private static String renderLayout(List<FailingStatement> failing) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < failing.size(); i++) {
            if (i > 0) {
                sb.append('\n').append(STATEMENT_BOUNDARY).append('\n');
            }
            FailingStatement f = failing.get(i);
            sb.append("[Query]\n");
            sb.append(f.sql);
            if (!f.sql.endsWith("\n")) {
                sb.append('\n');
            }
            sb.append('\n');
            sb.append("[Diff]\n");
            String answerJoined = joinLines(f.answerLines);
            String resultJoined = joinLines(f.resultLines);
            List<UnifiedDiffUtil.Hunk> hunks = UnifiedDiffUtil.diff(answerJoined, resultJoined);
            sb.append(UnifiedDiffUtil.render("answer", "actual", hunks));
        }
        return sb.toString();
    }

    private static String joinLines(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String l : lines) {
            sb.append(l);
            sb.append('\n');
        }
        return sb.toString();
    }

    /**
     * Make the string safe to embed verbatim inside a CDATA section.
     * <ol>
     *   <li>Strips XML 1.0-illegal C0 control characters (U+0000-U+0008, U+000B, U+000C,
     *       U+000E-U+001F), replacing each with {@code '?'}.</li>
     *   <li>Strips bare CR ({@code \r}) from line endings so they don't round-trip as
     *       {@code &#xD;} in parsed XML.</li>
     *   <li>Splits any {@code "]]>"} sequence so it doesn't terminate the CDATA prematurely.</li>
     * </ol>
     */
    public static String cdataSafe(String s) {
        if (s == null) {
            return "";
        }
        // Replace XML-illegal C0 control chars with '?' and strip \r.
        // XML 1.0 legal chars: #x9 | #xA | #xD | [#x20-#xD7FF] | ...
        // Illegal C0 range: 0x00-0x08, 0x0B, 0x0C, 0x0E-0x1F.
        // \t (0x09) and \n (0x0A) are legal and preserved.
        // \r (0x0D) is legal in XML but we strip it to avoid &#xD; round-trip.
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\r') {
                // strip bare CR
                continue;
            }
            if (c <= '\u001F') {
                // Allow TAB (0x09) and LF (0x0A); replace everything else <= 0x1F
                if (c != '\t' && c != '\n') {
                    sb.append('?');
                    continue;
                }
            }
            sb.append(c);
        }
        return sb.toString().replace("]]>", "]]]]><![CDATA[>");
    }
    public static void main(String[] args) {
        int passed = 0;
        try {
            passed += testCdataEscape();
            passed += testCdataSafeControlChars();
            passed += testSingleStatementSingleFail();
            passed += testMultipleStatementsOneFails();
            passed += testMultipleStatementsMultiFails();
            passed += testMismatchedBlockCounts();
        } catch (Exception e) {
            System.err.println("FAIL: exception in self-test: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
        if (passed == 6) {
            System.out.println("OK: FailureCdataBuilder " + passed + "/6 cases passed");
            System.exit(0);
        } else {
            System.err.println("FAIL: FailureCdataBuilder " + passed + "/6 cases passed");
            System.exit(1);
        }
    }

    private static int testCdataSafeControlChars() {
        // \r\n should become \n (CR stripped)
        String withCrLf = "line1\r\nline2\r\n";
        String result = cdataSafe(withCrLf);
        if (!result.equals("line1\nline2\n")) {
            System.err.println("  FAIL: testCdataSafeControlChars CRLF -> " + repr(result));
            return 0;
        }
        // NUL (0x00) should become '?'
        String withNul = "before\u0000after";
        String result2 = cdataSafe(withNul);
        if (!result2.equals("before?after")) {
            System.err.println("  FAIL: testCdataSafeControlChars NUL -> " + repr(result2));
            return 0;
        }
        // SOH (0x01) should become '?'
        String withSoh = "a\u0001b";
        String result3 = cdataSafe(withSoh);
        if (!result3.equals("a?b")) {
            System.err.println("  FAIL: testCdataSafeControlChars SOH -> " + repr(result3));
            return 0;
        }
        // TAB (0x09) and LF (0x0A) must be preserved
        String withTabLf = "a\tb\nc";
        String result4 = cdataSafe(withTabLf);
        if (!result4.equals("a\tb\nc")) {
            System.err.println("  FAIL: testCdataSafeControlChars TAB/LF -> " + repr(result4));
            return 0;
        }
        return 1;
    }

    private static String repr(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < 0x20 || c > 0x7e) {
                sb.append(String.format("\\u%04x", (int) c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static int testMismatchedBlockCounts() throws IOException {
        File dir = makeTmpDir("fcb-mismatch");
        // Case file has 2 statements; answer has 2 blocks but result has only 1
        File caseF = writeFile(new File(dir, "c.sql"),
                "select 1;\nselect 2;\n");
        File answerF = writeFile(new File(dir, "c.answer"),
                "===================================================\n"
                        + "row1\n"
                        + "===================================================\n"
                        + "row2\n");
        // result is missing the second block (truncated)
        File resultF = writeFile(new File(dir, "c.result"),
                "===================================================\n"
                        + "row1\n");
        String cdata = build(caseF.getAbsolutePath(),
                answerF.getAbsolutePath(),
                resultF.getAbsolutePath());
        boolean ok = cdata.contains("truncation") && cdata.contains("diff aborted");
        if (!ok) {
            System.err.println("  FAIL: testMismatchedBlockCounts; cdata=\n" + cdata);
            return 0;
        }
        return 1;
    }

    private static int testCdataEscape() {
        String esc = cdataSafe("foo ]]> bar ]]> baz");
        if (!esc.equals("foo ]]]]><![CDATA[> bar ]]]]><![CDATA[> baz")) {
            System.err.println("  FAIL: cdataSafe -> " + esc);
            return 0;
        }
        return 1;
    }

    private static int testSingleStatementSingleFail() throws IOException {
        File dir = makeTmpDir("fcb-single");
        File caseF = writeFile(new File(dir, "c.sql"),
                "select 1;\n");
        File answerF = writeFile(new File(dir, "c.answer"),
                "===================================================\n"
                        + "expected\n");
        File resultF = writeFile(new File(dir, "c.result"),
                "===================================================\n"
                        + "actual\n");
        String cdata = build(caseF.getAbsolutePath(),
                answerF.getAbsolutePath(),
                resultF.getAbsolutePath());
        boolean ok = cdata.contains("[Query]")
                && cdata.contains("select 1;")
                && cdata.contains("[Diff]")
                && cdata.contains("--- answer")
                && cdata.contains("+++ actual")
                && cdata.contains("-expected")
                && cdata.contains("+actual");
        if (!ok) {
            System.err.println("  FAIL: testSingleStatementSingleFail; cdata=\n" + cdata);
            return 0;
        }
        return 1;
    }

    private static int testMultipleStatementsOneFails() throws IOException {
        File dir = makeTmpDir("fcb-multi-1fail");
        File caseF = writeFile(new File(dir, "c.sql"),
                "select 1;\nselect 2;\n");
        File answerF = writeFile(new File(dir, "c.answer"),
                "===================================================\n"
                        + "row1\n"
                        + "===================================================\n"
                        + "row2\n");
        // Second statement's output diverges; first matches.
        File resultF = writeFile(new File(dir, "c.result"),
                "===================================================\n"
                        + "row1\n"
                        + "===================================================\n"
                        + "rowTWO\n");
        String cdata = build(caseF.getAbsolutePath(),
                answerF.getAbsolutePath(),
                resultF.getAbsolutePath());
        boolean ok = cdata.contains("select 2;")
                && !cdata.contains("select 1;")  // first statement should NOT appear
                && cdata.contains("-row2")
                && cdata.contains("+rowTWO")
                && !cdata.contains(STATEMENT_BOUNDARY);  // only one failing -> no boundary
        if (!ok) {
            System.err.println("  FAIL: testMultipleStatementsOneFails; cdata=\n" + cdata);
            return 0;
        }
        return 1;
    }

    private static int testMultipleStatementsMultiFails() throws IOException {
        File dir = makeTmpDir("fcb-multi-2fail");
        File caseF = writeFile(new File(dir, "c.sql"),
                "select 1;\nselect 2;\n");
        File answerF = writeFile(new File(dir, "c.answer"),
                "===================================================\n"
                        + "expA\n"
                        + "===================================================\n"
                        + "expB\n");
        File resultF = writeFile(new File(dir, "c.result"),
                "===================================================\n"
                        + "actA\n"
                        + "===================================================\n"
                        + "actB\n");
        String cdata = build(caseF.getAbsolutePath(),
                answerF.getAbsolutePath(),
                resultF.getAbsolutePath());
        boolean ok = cdata.contains("select 1;")
                && cdata.contains("select 2;")
                && cdata.contains(STATEMENT_BOUNDARY)
                && cdata.contains("-expA") && cdata.contains("+actA")
                && cdata.contains("-expB") && cdata.contains("+actB");
        if (!ok) {
            System.err.println("  FAIL: testMultipleStatementsMultiFails; cdata=\n" + cdata);
            return 0;
        }
        return 1;
    }

    private static File makeTmpDir(String prefix) throws IOException {
        File f = File.createTempFile(prefix, ".d");
        f.delete();
        if (!f.mkdir()) {
            throw new IOException("cannot create temp dir: " + f);
        }
        f.deleteOnExit();
        return f;
    }

    private static File writeFile(File f, String content) throws IOException {
        PrintWriter pw = new PrintWriter(new FileWriter(f));
        try {
            pw.print(content);
        } finally {
            pw.close();
        }
        f.deleteOnExit();
        return f;
    }
}
