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
package name.fraser.neil.plaintext;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * Produces a {@code diff -ut}-style unified diff between two text blobs by reusing the existing
 * {@link diff_match_patch} library bundled in the same jar.
 *
 * <p>Lives in the {@code name.fraser.neil.plaintext} package to access the {@code protected}
 * line-mode helpers ({@code diff_linesToChars}, {@code diff_charsToLines}) and the {@code
 * protected} fields of the inner {@code LinesToCharsResult} record. The class is built into the
 * same {@code cubridqa-cqt.jar} via the existing build.xml include {@code
 * name/fraser/neil/plaintext/**\/*.class} (no build.xml change needed).
 */
public final class UnifiedDiffUtil extends diff_match_patch {

    private static final int CONTEXT = 3;

    /** A unified diff hunk header + body. */
    private static final class Hunk {
        int oldStart;
        int oldCount;
        int newStart;
        int newCount;
        /** Lines including their leading prefix character (' ', '-', '+') and trailing newline. */
        final List<String> body = new ArrayList<String>();

        String header() {
            int displayOldStart = (oldCount == 0) ? 0 : oldStart;
            int displayNewStart = (newCount == 0) ? 0 : newStart;
            return "@@ -"
                    + displayOldStart
                    + ","
                    + oldCount
                    + " +"
                    + displayNewStart
                    + ","
                    + newCount
                    + " @@";
        }
    }

    private UnifiedDiffUtil() {}

    /** {@code diff -u}-style unified diff of oldText -> newText with the given file labels. */
    public static String unifiedDiff(
            String oldText, String newText, String oldLabel, String newLabel) {
        return render(oldLabel, newLabel, diff(oldText, newText));
    }

    /** Compute a list of hunks describing oldText -> newText (line granularity). */
    private static List<Hunk> diff(String oldText, String newText) {
        UnifiedDiffUtil dmp = new UnifiedDiffUtil();
        LinesToCharsResult lc = dmp.diff_linesToChars(safe(oldText), safe(newText));
        LinkedList<Diff> diffs = dmp.diff_main(lc.chars1, lc.chars2, false);
        dmp.diff_charsToLines(diffs, lc.lineArray);
        dmp.diff_cleanupSemantic(diffs);
        List<Diff> ops = explode(diffs);
        return groupHunks(ops, CONTEXT);
    }

    /** Render hunks as a unified diff string with the given file labels. */
    private static String render(String oldLabel, String newLabel, List<Hunk> hunks) {
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

    /** Split each multi-line Diff into per-line Diffs so hunks can carry exact line counts. */
    private static List<Diff> explode(LinkedList<Diff> diffs) {
        List<Diff> out = new ArrayList<Diff>();
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
                out.add(new Diff(d.operation, line));
            }
        }
        return out;
    }

    /**
     * Group per-line Diffs into unified diff hunks with {@code context} surrounding EQUAL lines.
     */
    private static List<Hunk> groupHunks(List<Diff> ops, int context) {
        List<Hunk> hunks = new ArrayList<Hunk>();
        int oldLine = 1;
        int newLine = 1;
        int i = 0;
        int n = ops.size();
        while (i < n) {
            // Skip leading EQUAL run; advance line counters.
            while (i < n && ops.get(i).operation == Operation.EQUAL) {
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
                Diff eq = ops.get(k);
                h.body.add(" " + eq.text);
                oldCount++;
                newCount++;
            }
            // Walk forward, emitting changes and trailing EQUAL runs of up to 2*context lines
            // before closing the hunk.
            int trailingEq = 0;
            while (i < n) {
                Diff op = ops.get(i);
                if (op.operation == Operation.EQUAL) {
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
                    if (op.operation == Operation.DELETE) {
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
}
