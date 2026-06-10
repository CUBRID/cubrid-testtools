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

import com.navercorp.cubridqa.cqt.webconsole.compare.ResultReader;
import com.navercorp.cubridqa.cqt.webconsole.compare.TestReader;
import java.util.ArrayList;
import java.util.List;
import name.fraser.neil.plaintext.UnifiedDiffUtil;

/**
 * Builds the {@code <failure>} CDATA payload for a failing SQL/MEDIUM test case.
 *
 * <p>Reuses {@link TestReader} (which delegates to SQLParser/LineScanner; PLCSQL blocks are already
 * grouped into one statement upstream) and {@link ResultReader} (which splits answer and result
 * files by the canonical 51-{@code =} separator emitted at ConsoleBO.executeSqlFile L1267) to align
 * statements with their expected/actual output blocks (see Compare.java L103-110 for the same
 * pattern). For each statement whose answer block differs from its result block, emits a {@code
 * [Query]} + {@code [Diff]} section. Multiple failing statements are separated by a boundary line.
 *
 * <p><b>Coupling risk — 1:1 walk assumption.</b> This class walks {@link TestReader} and {@link
 * ResultReader} in lockstep (one statement → one output block). That assumption holds only when
 * every call to {@link TestReader#nextStatement()} corresponds to exactly one {@code ===} separator
 * written by {@code ConsoleBO.executeSqlFile} (L1270/1273). It can drift when {@code
 * TestReader.nextStatement()} silently concatenates meta lines (lines starting with {@code --},
 * {@code $}, or {@code autocommit}) onto the next executable statement, causing the statement count
 * to differ from the block count. If drift occurs, the asymmetric-truncation guard fires and the
 * diff is aborted. Compare.java uses the same 1:1 walk pattern and carries the same risk.
 */
public final class FailureCdataBuilder {

    private static final String STATEMENT_BOUNDARY = "--- statement boundary ---";

    private static final String TRUNCATION_NOTICE =
            "<truncation: answer file and result file have different block counts, diff aborted>";

    private FailureCdataBuilder() {}

    /**
     * Build the CDATA payload describing a failure.
     *
     * @param caseFilePath original .sql case file
     * @param answerFilePath expected output file (.answer)
     * @param resultFilePath actual output file (.result, written by TestUtil.saveResult)
     * @return CDATA-safe string suitable to embed inside {@code <![CDATA[...]]>}
     * @throws Exception if any of the input files cannot be read or parsed
     */
    public static String build(String caseFilePath, String answerFilePath, String resultFilePath)
            throws Exception {
        List<FailingStatement> failing =
                collectFailingStatements(caseFilePath, answerFilePath, resultFilePath);
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
            String caseFilePath, String answerFilePath, String resultFilePath) throws Exception {
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
                    // Sentinel: null answerLines = truncation notice; renderLayout branches on it.
                    out.add(new FailingStatement(sql, null, null));
                    break;
                }
                if (answerBlock == null) {
                    // The asymmetric guard above guarantees resultBlock is also null here:
                    // both readers are terminally exhausted (ResultReader.isEOF never resets),
                    // so the remaining statements have no blocks to compare.
                    break;
                }
                if (!answerBlock.equals(resultBlock)) {
                    out.add(new FailingStatement(sql, answerBlock, resultBlock));
                }
            }
            return out;
        } finally {
            if (tr != null) {
                try {
                    // closeFile() rethrows IOException as RuntimeException;
                    // ResultReader.close() below never throws.
                    tr.closeFile();
                } catch (Exception ignore) {
                }
            }
            if (ar != null) {
                ar.close();
            }
            if (rr != null) {
                rr.close();
            }
        }
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
            if (f.answerLines == null) {
                sb.append(TRUNCATION_NOTICE).append('\n');
                continue;
            }
            sb.append(
                    UnifiedDiffUtil.unifiedDiff(
                            joinLines(f.answerLines),
                            joinLines(f.resultLines),
                            "answer",
                            "actual"));
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
     *
     * <ol>
     *   <li>Strips XML 1.0-illegal C0 control characters (U+0000-U+0008, U+000B, U+000C,
     *       U+000E-U+001F), replacing each with {@code '?'}.
     *   <li>Strips bare CR ({@code \r}) from line endings so they don't round-trip as {@code &#xD;}
     *       in parsed XML.
     *   <li>Splits any {@code "]]>"} sequence so it doesn't terminate the CDATA prematurely.
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
}
