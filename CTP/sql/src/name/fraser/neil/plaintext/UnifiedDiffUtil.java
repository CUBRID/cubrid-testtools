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
package name.fraser.neil.plaintext;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * Produces a {@code diff -ut}-style unified diff between two text blobs by reusing the existing
 * {@link diff_match_patch} library bundled in the same jar.
 *
 * <p>Lives in the {@code name.fraser.neil.plaintext} package to access the {@code protected}
 * line-mode helpers ({@code diff_linesToChars}, {@code diff_charsToLines}) and the
 * {@code protected} fields of the inner {@code LinesToCharsResult} record. The class is built
 * into the same {@code cubridqa-cqt.jar} via the existing build.xml include
 * {@code name/fraser/neil/plaintext/**\/*.class} (no build.xml change needed).
 */
public final class UnifiedDiffUtil extends diff_match_patch {

    private static final int CONTEXT = 3;

    /** A single op = one input line plus its disposition. */
    private static final class LineOp {
        final Operation op;
        final String text; // includes trailing newline if original had one

        LineOp(Operation op, String text) {
            this.op = op;
            this.text = text;
        }
    }

    /** A unified diff hunk header + body. */
    public static final class Hunk {
        public int oldStart;
        public int oldCount;
        public int newStart;
        public int newCount;
        /** Lines including their leading prefix character (' ', '-', '+') and trailing newline. */
        public final List<String> body = new ArrayList<String>();

        public String header() {
            int displayOldStart = (oldCount == 0) ? 0 : oldStart;
            int displayNewStart = (newCount == 0) ? 0 : newStart;
            return "@@ -" + displayOldStart + "," + oldCount
                    + " +" + displayNewStart + "," + newCount + " @@";
        }
    }

    private UnifiedDiffUtil() {}

    /** Compute a list of hunks describing oldText -> newText (line granularity). */
    public static List<Hunk> diff(String oldText, String newText) {
        UnifiedDiffUtil dmp = new UnifiedDiffUtil();
        LinesToCharsResult lc = dmp.diff_linesToChars(safe(oldText), safe(newText));
        LinkedList<Diff> diffs = dmp.diff_main(lc.chars1, lc.chars2, false);
        dmp.diff_charsToLines(diffs, lc.lineArray);
        dmp.diff_cleanupSemantic(diffs);
        List<LineOp> ops = explode(diffs);
        return groupHunks(ops, CONTEXT);
    }

    /** Render hunks as a unified diff string with the given file labels. */
    public static String render(String oldLabel, String newLabel, List<Hunk> hunks) {
        StringBuilder sb = new StringBuilder();
        sb.append("--- ").append(oldLabel).append('\n');
        sb.append("+++ ").append(newLabel).append('\n');
        for (Hunk h : hunks) {
            sb.append(h.header()).append('\n');
            for (String line : h.body) {
                sb.append(line);
                if (!line.endsWith("\n")) {
                    sb.append('\n');
                }
            }
        }
        return sb.toString();
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    /** Split each multi-line Diff into per-line LineOps so hunks can carry exact line counts. */
    private static List<LineOp> explode(LinkedList<Diff> diffs) {
        List<LineOp> out = new ArrayList<LineOp>();
        for (Diff d : diffs) {
            if (d.text == null || d.text.length() == 0) {
                continue;
            }
            int from = 0;
            int len = d.text.length();
            while (from < len) {
                int nl = d.text.indexOf('\n', from);
                String line;
                if (nl < 0) {
                    line = d.text.substring(from);
                    from = len;
                } else {
                    line = d.text.substring(from, nl + 1);
                    from = nl + 1;
                }
                out.add(new LineOp(d.operation, line));
            }
        }
        return out;
    }

    /** Group LineOps into unified diff hunks with {@code context} surrounding EQUAL lines. */
    private static List<Hunk> groupHunks(List<LineOp> ops, int context) {
        List<Hunk> hunks = new ArrayList<Hunk>();
        int oldLine = 1;
        int newLine = 1;
        int i = 0;
        int n = ops.size();
        while (i < n) {
            // Skip leading EQUAL run; advance line counters.
            while (i < n && ops.get(i).op == Operation.EQUAL) {
                oldLine++;
                newLine++;
                i++;
            }
            if (i >= n) {
                break;
            }
            // Begin a new hunk; include up to `context` EQUAL lines before the change.
            Hunk h = new Hunk();
            int leadStart = Math.max(0, i - context);
            int leadEqualCount = i - leadStart;
            h.oldStart = oldLine - leadEqualCount;
            h.newStart = newLine - leadEqualCount;
            int oldCount = 0;
            int newCount = 0;
            for (int k = leadStart; k < i; k++) {
                LineOp eq = ops.get(k);
                h.body.add(" " + eq.text);
                oldCount++;
                newCount++;
            }
            // Walk forward, emitting changes and trailing EQUAL runs of up to 2*context lines
            // before closing the hunk.
            int trailingEq = 0;
            while (i < n) {
                LineOp op = ops.get(i);
                if (op.op == Operation.EQUAL) {
                    trailingEq++;
                    if (trailingEq > 2 * context) {
                        // This EQUAL line is not appended to the body, so it must not be
                        // counted when trimming trailing context below; otherwise the trim
                        // removes one line too many and the hunk keeps context-1 trailing
                        // context lines instead of context (off-by-one vs `diff -u`).
                        trailingEq--;
                        break;
                    }
                    h.body.add(" " + op.text);
                    oldCount++;
                    newCount++;
                    oldLine++;
                    newLine++;
                } else {
                    trailingEq = 0;
                    if (op.op == Operation.DELETE) {
                        h.body.add("-" + op.text);
                        oldCount++;
                        oldLine++;
                    } else { // INSERT
                        h.body.add("+" + op.text);
                        newCount++;
                        newLine++;
                    }
                }
                i++;
            }
            // Trim any trailing EQUAL lines beyond `context` from the end of the hunk.
            int trim = Math.max(0, trailingEq - context);
            while (trim-- > 0) {
                h.body.remove(h.body.size() - 1);
                oldCount--;
                newCount--;
                oldLine--;
                newLine--;
                // rewind so the trimmed EQUAL lines become leading context for the NEXT hunk
                i--;
            }
            h.oldCount = oldCount;
            h.newCount = newCount;
            hunks.add(h);
        }
        return hunks;
    }

    public static void main(String[] args) {
        int passed = 0;
        passed += check("identical -> empty", diff("a\nb\nc\n", "a\nb\nc\n").isEmpty());

        List<Hunk> add = diff("a\nb\nc\n", "a\nb\nNEW\nc\n");
        passed += check("single insert produces 1 hunk", add.size() == 1);
        String renderedAdd = render("old", "new", add);
        passed += check("insert render contains +NEW", renderedAdd.contains("+NEW\n"));
        passed += check("insert render contains @@ header", renderedAdd.contains("@@ -"));

        List<Hunk> del = diff("a\nb\nc\n", "a\nc\n");
        passed += check("single delete produces 1 hunk", del.size() == 1);
        passed += check("delete render contains -b", render("old", "new", del).contains("-b\n"));

        List<Hunk> empty1 = diff("", "");
        passed += check("both empty -> no hunks", empty1.isEmpty());

        List<Hunk> empty2 = diff("", "x\n");
        passed += check("from empty -> 1 hunk", empty2.size() == 1);
        if (!empty2.isEmpty()) {
            String hdr2 = empty2.get(0).header();
            passed += check("empty old -> header starts @@ -0,0", hdr2.startsWith("@@ -0,0 +1,1 @@"));
            if (!hdr2.startsWith("@@ -0,0 +1,1 @@")) {
                System.err.println("  actual header: " + hdr2);
            }
        } else {
            passed += 0; // skip sub-check if no hunk
            System.err.println("  FAIL: empty old -> no hunk, cannot check header");
        }

        List<Hunk> crlf = diff("a\r\nb\r\n", "a\r\nB\r\n");
        passed += check("CRLF mix produces a hunk without error", !crlf.isEmpty());

        // Fix 5: multi-hunk @@ line-number correctness.
        // 7 EQ + 1 DEL + 7 EQ + 1 DEL + 7 EQ -> expect 2 hunks.
        String twoDelOld = buildLines(7, "eq") + "del1\n" + buildLines(7, "eq") + "del2\n" + buildLines(7, "eq");
        String twoDelNew = buildLines(7, "eq") + buildLines(7, "eq") + buildLines(7, "eq");
        List<Hunk> twoDelHunks = diff(twoDelOld, twoDelNew);
        passed += check("7EQ+DEL+7EQ+DEL+7EQ -> 2 hunks", twoDelHunks.size() == 2);
        if (twoDelHunks.size() == 2) {
            // Hunk 1: del1 is at line 8; context of 3 lines before -> oldStart=5
            Hunk h0 = twoDelHunks.get(0);
            Hunk h1 = twoDelHunks.get(1);
            boolean h0ok = h0.oldStart >= 5 && h0.oldStart <= 8;
            boolean h1ok = h1.oldStart >= 12 && h1.oldStart <= 16;
            passed += check("hunk0 oldStart in [5,8]: " + h0.header(), h0ok);
            passed += check("hunk1 oldStart in [12,16]: " + h1.header(), h1ok);
            if (!h0ok) System.err.println("  actual hunk0: " + h0.header());
            if (!h1ok) System.err.println("  actual hunk1: " + h1.header());
        } else {
            passed += 0; // two sub-checks skipped
            passed += 0;
            System.err.println("  FAIL: expected 2 hunks, got " + twoDelHunks.size());
        }

        // 8 EQ + 1 INS + 8 EQ -> context=3 on each side, distance between hunks would be 16>6 so 2 hunks,
        // but insertion is surrounded by EQ so it should be 1 hunk (within 2*3=6 EQ limit).
        String insOld = buildLines(8, "eq") + buildLines(8, "eq");
        String insNew = buildLines(8, "eq") + "ins\n" + buildLines(8, "eq");
        List<Hunk> insHunks = diff(insOld, insNew);
        passed += check("8EQ+INS+8EQ -> exactly 1 hunk", insHunks.size() == 1);
        if (insHunks.size() == 1) {
            String hdr = insHunks.get(0).header();
            // new count should include 1 inserted line plus context
            passed += check("8EQ+INS+8EQ hunk newCount>oldCount: " + hdr,
                    insHunks.get(0).newCount == insHunks.get(0).oldCount + 1);
            if (insHunks.get(0).newCount != insHunks.get(0).oldCount + 1) {
                System.err.println("  actual header: " + hdr);
            }
        } else {
            passed += 0;
            System.err.println("  FAIL: expected 1 hunk, got " + insHunks.size()
                    + "; headers: " + hunkHeaders(insHunks));
        }

        int expected = 15;
        if (passed == expected) {
            System.out.println("OK: UnifiedDiffUtil " + passed + "/" + expected + " cases passed");
            System.exit(0);
        } else {
            System.err.println("FAIL: UnifiedDiffUtil " + passed + "/" + expected + " cases passed");
            System.exit(1);
        }
    }

    private static String buildLines(int count, String prefix) {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= count; i++) {
            sb.append(prefix).append(i).append('\n');
        }
        return sb.toString();
    }

    private static String hunkHeaders(List<Hunk> hunks) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < hunks.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(hunks.get(i).header());
        }
        sb.append("]");
        return sb.toString();
    }

    private static int check(String label, boolean cond) {
        if (cond) {
            return 1;
        }
        System.err.println("  FAIL: " + label);
        return 0;
    }
}
