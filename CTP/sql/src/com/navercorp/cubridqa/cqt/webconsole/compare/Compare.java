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
package com.navercorp.cubridqa.cqt.webconsole.compare;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import name.fraser.neil.plaintext.diff_match_patch;
import name.fraser.neil.plaintext.diff_match_patch.Diff;
import name.fraser.neil.plaintext.diff_match_patch.Operation;

public class Compare {

    String sqlFilename;
    String answerFilename;
    String resultFilename;
    String errorContent;

    TestReader tr0;
    ResultReader rr1;
    ResultReader rr2;

    StringBuffer result;
    int anchorIndex = 1;

    public Compare(String filename0, String filename1, String filename2) throws Exception {

        this.sqlFilename = filename0;
        this.answerFilename = filename1;
        this.resultFilename = filename2;

        tr0 = new TestReader(filename0);
        rr1 = new ResultReader(filename1);
        rr2 = new ResultReader(filename2);
        result = new StringBuffer();
    }

    public static void main(String[] args) throws Exception {
        String filename0 = "data/_01_adhoc_multiple.sql";
        String filename1 = "data/_01_adhoc_multiple.answer";
        String filename2 = "data/_01_adhoc_multiple.result";

        Compare comp = new Compare(filename0, filename1, filename2);
        comp.compare();
    }

    public void compare() throws IOException {
        ArrayList<String> leftList;
        ArrayList<String> rightList;

        out("<html>");
        out("<head><meta http-equiv='Content-Type' content='text/html; charset=utf-8'>");
        out("<title>Test: " + sqlFilename + "</title>");
        out("<script src='js/jquery-1.9.1.min.js'></script>");
        out("<script src='js/page_compare.js'></script>");
        out("<style>");
        out("body {");
        out("   font-family: arial;");
        out("}");
        out(".cls_err {");
        out("   background-color: yellow;");
        out("}");
        out(".cls_diff_delete {");
        out("   background-color: yellow;");
        out("   text-decoration:line-through;");
        out("}");
        out(".cls_diff_insert {");
        out("   background-color: green;");
        out("   color: white;");
        out("}");
        out("td {");
        out("   vertical-align:top;");
        out("}");
        out("</style>");
        out("</head>");
        out("<body>");
        out("<h3>SQL File: " + sqlFilename + "</h3>");
        out("<h5>Answer: " + answerFilename + "</h5>");
        out("<h5>Result: " + resultFilename + "</h5>");
        out("<input type=button id='btn_source' value='View Source'>");
        out("&nbsp;&nbsp;<a href=#FID_N_1>Go to the first error</a>");
        out("<table id='main_table' border=1 cellspacing=0 >");
        out("<tr>");
        out("<td width='35'>NO.</td>");
        out("<td>SQL</td>");
        out("<td>Expect</td>");
        out("<td>Actual</td>");
        out("</tr>");

        String sql;
        int index = 0;
        while ((sql = tr0.nextStatement()) != null) {
            leftList = rr1.getNextBlockText();
            rightList = rr2.getNextBlockText();
            out(++index, sql, leftList, rightList);
        }

        out("</table>");
        if (this.errorContent != null && this.errorContent.length() > 0) {
            out("<br>");
            out("<table width='80%' border=0><tr><td>");
            out("<hr width=100% align=left>");
            out("<a id=FID_N_" + (anchorIndex++) + " href='#FID_N_" + anchorIndex + "'></a>");
            out("<pre>");
            out(this.errorContent.trim());
            out("</pre>");
            out("</table>");
            out("<i>END</i>");
        }
        out("</body>");
        out("</html>");
    }

    private void out(
            int index, String sql, ArrayList<String> leftList, ArrayList<String> rightList) {
        String left, leftDisp, right, rightDisp;

        int leftLen = leftList == null ? 0 : leftList.size();
        int rightLen = rightList == null ? 0 : rightList.size();

        LinkedList<Diff> diffs;

        String link;
        boolean hasError = false;

        if (leftLen > 0) {
            for (int i = 0; i < leftLen; i++) {
                left = leftList.get(i);

                if (i < rightLen) {
                    right = rightList.get(i);
                    if (!left.equals(right)) {
                        diffs = lineCompare(left, right);

                        leftDisp = "";
                        rightDisp = "";
                        for (Diff d : diffs) {
                            if (d.operation == Operation.EQUAL) {
                                leftDisp += d.text;
                                rightDisp += d.text;
                            } else if (d.operation == Operation.DELETE) {
                                if (hasError == false) {
                                    link =
                                            "&nbsp;[<a id=FID_N_"
                                                    + (anchorIndex++)
                                                    + " href='#FID_N_"
                                                    + anchorIndex
                                                    + "'>NEXT</a>, <a id=FID_P_"
                                                    + (anchorIndex - 1)
                                                    + " href='#FID_P_"
                                                    + (anchorIndex - 2)
                                                    + "'>PREV</a>]";
                                    hasError = true;
                                } else {
                                    link = "";
                                }
                                leftDisp +=
                                        "<span class='cls_diff_delete'>"
                                                + d.text
                                                + "</span>"
                                                + link;
                            } else if (d.operation == Operation.INSERT) {
                                if (hasError == false) {
                                    link =
                                            "&nbsp;[<a id=FID_N_"
                                                    + (anchorIndex++)
                                                    + " href='#FID_N_"
                                                    + anchorIndex
                                                    + "'>NEXT</a>, <a id=FID_P_"
                                                    + (anchorIndex - 1)
                                                    + " href='#FID_P_"
                                                    + (anchorIndex - 2)
                                                    + "'>PREV</a>]";
                                    hasError = true;
                                } else {
                                    link = "";
                                }
                                rightDisp +=
                                        "<span class='cls_diff_insert'>"
                                                + d.text
                                                + "</span>"
                                                + link;
                            }
                        }
                        leftList.set(i, leftDisp);
                        rightList.set(i, rightDisp);
                        // leftList.set(i, "<span class='cls_err'>" + left +
                        // "</span>");
                        // rightList.set(i, "<span class='cls_err'>" + right +
                        // "</span>");
                    }
                } else {
                    if (hasError == false) {
                        link =
                                "&nbsp;[<a id=FID_N_"
                                        + (anchorIndex++)
                                        + " href='#FID_N_"
                                        + anchorIndex
                                        + "'>NEXT</a>, <a id=FID_P_"
                                        + (anchorIndex - 1)
                                        + " href='#FID_P_"
                                        + (anchorIndex - 2)
                                        + "'>PREV</a>]";
                        hasError = true;
                    } else {
                        link = "";
                    }
                    leftList.set(i, "<span class='cls_err'>" + left + "</span>" + link);
                }
            }
        }

        if (rightLen > leftLen) {
            for (int i = leftLen; i < rightLen; i++) {
                right = rightList.get(i);
                if (hasError == false) {
                    link =
                            "&nbsp;[<a id=FID_N_"
                                    + (anchorIndex++)
                                    + " href='#FID_N_"
                                    + anchorIndex
                                    + "'>NEXT</a>, <a id=FID_P_"
                                    + (anchorIndex - 1)
                                    + " href='#FID_P_"
                                    + (anchorIndex - 2)
                                    + "'>PREV</a>]";
                    hasError = true;
                } else {
                    link = "";
                }
                rightList.set(i, "<span class='cls_diff_insert'>" + right + "</span>" + link);
            }
        }

        out("<tr>");

        out("<td>");
        out(String.valueOf(index));
        out("</td>");

        out(
                "<td style='width:300px'><pre style='width:300px;word-wrap:break-word; word-break: normal; white-space:pre-wrap; font-size:8pt;'>");
        out(sql);
        out("</pre></td>");

        out("<td><pre>");
        if (leftLen == 0) {
            out("&nbsp;");
        } else {
            out(leftList);
        }
        out("</pre></td>");

        out("<td><pre>");
        if (rightLen == 0) {
            out("&nbsp;");
        } else {
            out(rightList);
        }
        out("</pre></td>");

        out("</tr>");
    }

    private void out(ArrayList<String> list) {
        for (String line : list) {
            out(line);
        }
    }

    private void out(String msg) {
        result.append(msg + "\n");
    }

    public String getResult() {
        return result.toString();
    }

    public LinkedList<Diff> lineCompare(String s1, String s2) {
        diff_match_patch dmp = new diff_match_patch();
        return dmp.diff_main(s1, s2);
    }

    public void setErrorContent(String errorContent) {
        this.errorContent = errorContent;
    }
}
