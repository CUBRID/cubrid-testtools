/**
 * Copyright (c) 2016; Search Solution Corporation. All rights reserved.
 *
 * <p>Redistribution and use in source and binary forms; with or without modification; are permitted
 * provided that the following conditions are met:
 *
 * <p>* Redistributions of source code must retain the above copyright notice; this list of
 * conditions and the following disclaimer.
 *
 * <p>* Redistributions in binary form must reproduce the above copyright notice; this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided with
 * the distribution.
 *
 * <p>* Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written permission.
 *
 * <p>THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR
 * IMPLIED WARRANTIES; INCLUDING; BUT NOT LIMITED TO; THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT; INDIRECT; INCIDENTAL; SPECIAL; EXEMPLARY; OR CONSEQUENTIAL
 * DAMAGES (INCLUDING; BUT NOT LIMITED TO; PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE;
 * DATA; OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY;
 * WHETHER IN CONTRACT; STRICT LIABILITY; OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY
 * WAY OUT OF THE USE OF THIS SOFTWARE; EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.navercorp.cubridqa.cqt.common;

import com.navercorp.cubridqa.cqt.console.bean.Sql;
import com.navercorp.cubridqa.cqt.console.bean.SqlParam;
import com.navercorp.cubridqa.cqt.console.bean.Test;
import com.navercorp.cubridqa.cqt.console.util.CubridUtil;
import com.navercorp.cubridqa.cqt.console.util.TestUtil;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

public class SQLParser {
    private static final int STATE_GENERAL = 0;
    private static final int STATE_C_COMMENT = 1;
    private static final int STATE_CPP_COMMENT = 2;
    private static final int STATE_SQL_COMMENT = 3;
    private static final int STATE_SINGLE_QUOTE = 4;
    private static final int STATE_MYSQL_QUOTE = 5;
    private static final int STATE_DOUBLE_QUOTE_IDENTIFIER = 6;
    private static final int STATE_BACKTICK_IDENTIFIER = 7;
    private static final int STATE_BRACKET_IDENTIFIER = 8;
    private static final int STATE_STATEMENT_END = 9;

    private static final int STATE_PARAMETER = 10;
    private static final int STATE_SEMICOLON = 11;
    private static final int STATE_CTP_HINT = 12;
    private static final int STATE_C_COMMENT_HINT = 14;
    private static final int STATE_C_COMMENT_HINT_END = 15;
    private static final int STATE_C_COMMENT_END = 16;
    private static final int STATE_SINGLE_LINE_COMMENT_HINT_END = 17;
    private static final int STATE_ESCAPE_END = 18;

    private static final int SUBSTATE_INITIAL = 50;
    private static final int SUBSTATE_SEEN_CREATE = 51;
    private static final int SUBSTATE_SEEN_OR = 52;
    private static final int SUBSTATE_SEEN_REPLACE = 53;
    private static final int SUBSTATE_EXPECTING_IS_OR_AS = 54;
    private static final int SUBSTATE_PL_LANG_SPEC = 55;
    private static final int SUBSTATE_SEEN_LANGUAGE = 56;
    private static final int SUBSTATE_PLCSQL_TEXT = 57;
    private static final int SUBSTATE_SEEN_END = 58;

    public SQLParser() {}

    public List<Sql> parseSqlFile(String sqlfilePath, Test test) {
        List<Sql> list = new ArrayList<Sql>();
        boolean hasFileQueryPlan = TestUtil.isPrintQueryPlan(sqlfilePath);

        BufferedReader reader = null;
        try {
            reader =
                    new BufferedReader(
                            new InputStreamReader(
                                    new FileInputStream(new File(sqlfilePath)), test.getCodeset()));
            EditContents cts = new EditContents();

            int lineCnt = 0;
            int sqlCnt = 0;

            boolean isInBlock = false;
            boolean hasNewStmt = false;
            boolean needNewLine = false;

            int idx = -1;
            String line = null;
            while (true) {
                hasNewStmt = false;

                if (line == null || idx == line.length()) {
                    needNewLine = true;
                }

                if (needNewLine) {
                    idx = 0;
                    lineCnt++;
                    line = reader.readLine();
                    needNewLine = false;
                    cts.isFirstStmt = true;
                }

                if (line == null) {
                    /* if eof is reached, execute all */
                    isInBlock = false;

                    String str = cts.builder.toString();
                    if (str.length() > 0) {
                        Sql sql = createNewSql(test, cts, sqlfilePath, lineCnt);
                        list.add(sql);
                    }

                    break;
                } else {
                    // line = line.trim();
                    // System.out.println("line (" + lineCnt + "): " + line);
                    idx = parseLine(cts, line);
                    if (idx == -1) {
                        needNewLine = true;
                        continue;
                    } else if (idx == line.length()) {
                        needNewLine = true;
                    }

                    // System.out.println (line);

                    /*
                    isInBlock = isStatementInBlock(cts);

                    if (idx > line.length()) {
                        idx = line.length();
                        needNewLine = true;
                    }

                    // System.out.println(line + "("+ line.length() +")");
                    String token = line.substring(0, idx);// .trim();

                    if (cts.state == STATE_SEMICOLON) {
                        if (cts.builder.length() == 0 && token.replaceAll(";", "").isEmpty()) {
                            // do not add empty string
                            //. e.g.) INSERT INTO dba.DCL1(id) VALUES(1);; => INSERT INTO dba.DCL1(id) VALUES(1); + ;
                            idx++;
                            needNewLine = true;
                            cts.state = STATE_GENERAL;
                            continue;
                        } else {
                            needNewLine = true;
                            cts.state = STATE_STATEMENT_END;
                        }
                    }

                    System.out.println (cts.state);
                    System.out.println ("token = " + token);
                    if (cts.state == STATE_GENERAL || cts.state == STATE_STATEMENT_END || cts.state == STATE_C_COMMENT_HINT_END || cts.state == STATE_SINGLE_LINE_COMMENT_HINT_END
                        || cts.state == STATE_SINGLE_QUOTE || cts.state == STATE_DOUBLE_QUOTE_IDENTIFIER || cts.state == STATE_BACKTICK_IDENTIFIER || cts.state == STATE_BRACKET_IDENTIFIER) {
                        cts.builder.append(token);

                        if (needNewLine) {
                            cts.builder.append('\n');
                        } else {
                            cts.builder.append(' ');
                        }
                    }

                    if (idx >= line.length()) {
                        needNewLine = true;
                    }
                    */

                    /*
                    if (needNewLine) {
                        cts.builder.append('\n');
                    } else {
                        cts.builder.append(' ');
                    }
                    */

                    /*
                    if (cts.state == STATE_CPP_COMMENT
                            || cts.state == STATE_SQL_COMMENT || cts.state == STATE_PARAMETER || cts.state == STATE_CTP_HINT) {
                        // ignore comments
                        cts.state = STATE_GENERAL;
                    } else if (cts.state == STATE_C_COMMENT_END || cts.state == STATE_C_COMMENT_HINT_END) {
                        // needNewLine = false;
                        cts.state = STATE_GENERAL;
                        cts.builder.append(' ');
                    } else if (cts.state == STATE_CTP_HINT || cts.state == STATE_SINGLE_LINE_COMMENT_HINT_END) {
                        cts.state = STATE_GENERAL;
                        cts.builder.append('\n');
                    } else if (cts.state == STATE_C_COMMENT) {
                        // do nothing
                    }
                    */

                    if (cts.state == STATE_STATEMENT_END) {
                        // GO: System.out.println ("complete = " + cts.builder.toString() + "(" +
                        // cts.sqlCnt++ + ")");
                        hasNewStmt = true;
                    }
                }

                if (hasNewStmt) {
                    Sql sql = createNewSql(test, cts, sqlfilePath, lineCnt);
                    list.add(sql);
                }

                if (idx < line.length()) {
                    line = splitLine(cts, line, idx);
                    idx = 0;
                    if (line.isEmpty()) {
                        needNewLine = true;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                }
            }
        }

        return list;
    }

    private Sql createNewSql(Test test, EditContents cts, String filePath, int lineCnt) {
        String str = cts.builder.toString();

        // System.out.println ("complete = " + str);

        /*
        if (str.isEmpty()) {
            // do not add empty string e.g.) INSERT INTO dba.DCL1(id) VALUES(1);;
            cts.clear();
            needNewLine = true;
            continue;
        }
        */

        boolean isCall = checkIsCall(str);

        // e.g.) @t1: insert into xoo values(1, 1);
        int pos = str.indexOf(":");
        if (pos != -1 && str.startsWith("@")) {
            cts.connId = str.substring(1, pos);
            str = str.substring(pos + 1);
        }

        // set sql hint for debug
        if (test.isNeedDebugHint()) {
            String tmpSQL = str;
            String hint_sql = addHintForSQL(tmpSQL, true, lineCnt, filePath);
            str += hint_sql + "\n";
        }

        Sql sql = new Sql(cts.connId, str, cts.params, isCall);
        sql.setQueryplan(cts.hasQueryPlan); // || hasFileQueryPlan

        cts.clear();
        return sql;
    }

    private String splitLine(EditContents cts, String line, int idx) {
        cts.isFirstStmt = false;
        return line.substring(idx).trim();
    }

    private int parseLine(EditContents contents, String line) throws Exception {
        int i = -1;
        if (line == null || line.isEmpty()) {
            return i;
        }

        contents.isLastStmtValid = true;
        contents.isIncludeStmt = false;

        if (contents.state == STATE_CPP_COMMENT
                || contents.state == STATE_SQL_COMMENT
                || contents.state == STATE_C_COMMENT_END
                || contents.state == STATE_PARAMETER
                || contents.state == STATE_C_COMMENT_HINT_END) {
            /* these are single line comments and we're parsing a new line */
            contents.state = STATE_GENERAL;
        }

        if (contents.state == STATE_STATEMENT_END) {
            /* reset state in prev statement */
            contents.state = STATE_GENERAL;
            contents.substate = SUBSTATE_INITIAL;
        }

        int prev = i;

        // line = line.trim();
        int lineCnt = line.length();

        boolean doStop = false;
        boolean firstIteration = true;

        for (i = 0; i < lineCnt; i++) {
            switch (contents.state) {
                case STATE_GENERAL:
                    {
                        /*
                        if (firstIteration == true && isBlank(line.charAt(i))) {
                                prev = i; // remove black
                                continue;
                        }
                        */

                        if (contents.isFirstStmt
                                && firstIteration == true
                                && line.charAt(i) == '$') {
                            contents.state = STATE_PARAMETER;
                            contents.isLastStmtValid = true;
                            firstIteration = false;
                            continue;
                        }
                        firstIteration = false;

                        contents.plContinueStatus = PL_CHECK_STOP;
                        do {
                            i = checkPLSubState(contents, line, i);
                            if (contents.state == STATE_STATEMENT_END) {
                                break;
                            }
                        } while (contents.plContinueStatus == PL_CHECK_CONTINUE_WITHOUT_ADVANCE);

                        if (contents.plContinueStatus == PL_CHECK_CONTINUE) {
                            continue;
                        }

                        if (isIdentifierLetter(line.charAt(i))) {
                            contents.isLastStmtValid = true;

                            while (i + 1 < lineCnt) {
                                if (isIdentifierLetter(line.charAt(i + 1))) {
                                    i++;
                                } else {
                                    break;
                                }
                            }
                        }

                        prev = i;
                        i = checkComment(contents, line, i);
                        if (contents.state != STATE_GENERAL) {
                            doStop = true;
                            break;
                        }

                        if (contents.state == STATE_GENERAL && searchSemicolonEnd(line) == true) {
                            if (contents.substate != SUBSTATE_PLCSQL_TEXT) {
                                /*
                                while (i + 1 < lineCnt) {
                                    if (isSemicolon(line.charAt(i + 1))) {
                                        // consume multiple semicolons
                                        i++;
                                    }
                                    break;
                                }
                                */

                                assert (contents.substate != SUBSTATE_SEEN_END);
                                contents.isIncludeStmt = true;
                                contents.isLastStmtValid = false;

                                // initialize the state variables used to identify PL/CSQL text
                                contents.substate = SUBSTATE_INITIAL;
                                contents.plcsqlBeginEndPairs = 0;
                                contents.plcsqlNestedLevel = 0;

                                contents.state = STATE_SEMICOLON;

                                i = line.length();
                                continue;
                            }
                        }
                    }
                    break;

                case STATE_C_COMMENT:
                case STATE_C_COMMENT_HINT:
                    while (true) {
                        while (i < lineCnt && line.charAt(i) != '*') {
                            // find '*' or whole line
                            i++;
                        }

                        if (i == lineCnt) {
                            // find the end of C comment at the next line
                            break;
                        }

                        if ((i + 1 < lineCnt) && line.charAt(i + 1) == '/') {
                            if (contents.state == STATE_C_COMMENT) {
                                contents.state = STATE_C_COMMENT_END;
                            } else {
                                contents.state = STATE_C_COMMENT_HINT_END;
                            }
                            return i + 2;
                        } else {
                            i++;
                        }
                    }
                    break;

                    /* single line comment */
                case STATE_CPP_COMMENT:
                case STATE_SQL_COMMENT:
                    // contents.state = STATE_GENERAL;
                    i = line.length();
                    doStop = true;
                    break;

                case STATE_CTP_HINT:
                    doStop = true;
                    break;
                    /*
                        contents.state = STATE_GENERAL;
                        i = line.length();
                    */

                case STATE_SINGLE_QUOTE:
                    if (line.charAt(i) == '\'') {
                        if ((i + 1 < lineCnt) && line.charAt(i + 1) == '\'') {
                            i++;
                        } else {
                            contents.state = STATE_GENERAL;
                        }
                    }
                    break;
                case STATE_DOUBLE_QUOTE_IDENTIFIER:
                    if (line.charAt(i) == '"') {
                        contents.state = STATE_GENERAL;
                    }
                    break;
                case STATE_BACKTICK_IDENTIFIER:
                    if (line.charAt(i) == '`') {
                        contents.state = STATE_GENERAL;
                    }
                    break;
                case STATE_BRACKET_IDENTIFIER: /* single line? */
                    if (i == lineCnt - 1) {
                        contents.state = STATE_GENERAL;
                    }

                    if (line.charAt(i) == ']') {
                        contents.state = STATE_GENERAL;
                    }
                    break;

                case STATE_PARAMETER:
                    List<SqlParam> params = checkParams(contents, line);
                    if (params == null) {
                        // is not parameter form or invalid params
                        contents.state = STATE_GENERAL; // continue parsing
                    } else {
                        // params are created succesfully
                        contents.params = params;
                        i = line.length();
                        doStop = true;
                    }
                    break;

                default:
                    /* should not be here */
                    break;
            }

            if (doStop) {
                break;
            }
        }

        if (i >= lineCnt) {
            i = lineCnt;
        }

        String token = null;
        if (i < lineCnt) {
            token = line.substring(0, i);
        }

        if (token == null) {
            token = line;
        }

        /*
        if (contents.isIncludeStmt
        && !contents.isLastStmtValid
        && (contents.state == STATE_SQL_COMMENT
                || contents.state == STATE_CPP_COMMENT
                || contents.state == STATE_GENERAL
                || contents.state == STATE_SEMICOLON)) {
            contents.builder.append(token);
            if (i == lineCnt) {
                contents.builder.append('\n');
            } else {
                contents.builder.append(' ');
            }
        } else {
            // System.out.println ("not yet" + line + "\n" + contents.toString());
        }
        */

        // GO:         System.out.println ("state = " + contents.state);
        boolean doAdd = true;
        switch (contents.state) {
            case STATE_SQL_COMMENT:
            case STATE_CPP_COMMENT:
            case STATE_C_COMMENT_END:
            case STATE_PARAMETER:
            case STATE_CTP_HINT:
                contents.state = STATE_GENERAL;
                doAdd = false;
                break;
            case STATE_C_COMMENT:
            case STATE_C_COMMENT_HINT:
                doAdd = false;
                break;
            case STATE_SINGLE_LINE_COMMENT_HINT_END:
            case STATE_C_COMMENT_HINT_END:
                contents.state = STATE_GENERAL;
                break;
        }

        // GO:         System.out.println ("token = " + token);
        if (doAdd) {
            contents.builder.append(token);
            if (i == lineCnt) {
                contents.builder.append('\n');
            } else {
                // contents.builder.append(' ');
            }
        }

        if (contents.state == STATE_SEMICOLON) {
            if (contents.builder.length() == 0 && token.replaceAll(";", "").isEmpty()) {
                // do not add empty string
                // . e.g.) INSERT INTO dba.DCL1(id) VALUES(1);; => INSERT INTO dba.DCL1(id)
                // VALUES(1); + ;
                contents.state = STATE_GENERAL;
            } else {
                contents.state = STATE_SEMICOLON;
            }
        }

        boolean doFinish = false;
        switch (contents.state) {
            case STATE_SEMICOLON:
            case STATE_CTP_HINT:
                doFinish = true;
                break;
        }

        if (doFinish) {
            contents.state = STATE_STATEMENT_END;
        }

        /* when include other stmts and the last smt is non sense stmt. */

        /*
        if (contents.isIncludeStmt
                && !contents.isLastStmtValid
                && (contents.state == STATE_SQL_COMMENT
                        || contents.state == STATE_CPP_COMMENT
                        || contents.state == STATE_GENERAL
                        || contents.state == STATE_SEMICOLON)) {
            contents.state = STATE_STATEMENT_END;
        } else {
            // System.out.println ("not yet" + line + "\n" + contents.toString());
        }
        */

        return i;
    }

    private boolean isBlank(char c) {
        switch (c) {
            case ' ':
            case '\t':
            case '\r':
            case '\n':
                return true;
        }
        return false;
    }

    private boolean isIdentifierLetter(char c) {
        return ((c >= 'a' && c <= 'z')
                || (c >= 'A' && c <= 'Z')
                || (c >= '0' && c <= '9')
                || (c == '_'));
    }

    private boolean isSemicolon(char c) {
        return c == ';';
    }

    private boolean searchSemicolonEnd(String line) {
        /*
        int i = line.length();
        boolean found = false;
        while (i > 0) {
            i--;
            char c = line.charAt(i);
            if (c == ' ') {
                continue;
            } else if (c == ';') {
                found = true;
                break;
            } else {
                // any other character
                break;
            }
        }
        return found;
        */
        return line.trim().endsWith(";");
    }

    private boolean isInComment(int state) {
        switch (state) {
            case STATE_CPP_COMMENT:
            case STATE_C_COMMENT:
            case STATE_SQL_COMMENT:
                return true;
        }

        return false;
    }

    private boolean isStatementInBlock(EditContents contents) {
        int state = contents.state;
        if (state == STATE_C_COMMENT
                || state == STATE_SINGLE_QUOTE
                || state == STATE_MYSQL_QUOTE
                || state == STATE_DOUBLE_QUOTE_IDENTIFIER
                || state == STATE_BACKTICK_IDENTIFIER
                || state == STATE_BRACKET_IDENTIFIER) {
            return true;
        }

        int substate = contents.substate;
        if (state == STATE_GENERAL
                && (substate == SUBSTATE_PLCSQL_TEXT || substate == SUBSTATE_SEEN_END)) {
            return true;
        }

        return false;
    }

    private boolean checkIsCall(String str) {
        boolean isCall = false;
        int pos = str.indexOf(":");
        String strCopy = str;
        if (strCopy.startsWith("@") && pos != -1) {
            strCopy = strCopy.substring(pos);
        }
        String replacedStrCopy = strCopy.replaceAll(" ", "");
        int positionCall = replacedStrCopy.indexOf("call");
        if (positionCall != -1) {
            if (positionCall == 0) {
                int position1 = str.indexOf("(");
                int position2 = str.indexOf(")");
                if (position1 != -1 && position2 != -1) {
                    String params = str.substring(position1, position2);
                    if (params.indexOf("?") != -1) {
                        isCall = true;
                    } else {
                        isCall = false;
                    }
                } else {
                    isCall = false;
                }
            } else if (replacedStrCopy.indexOf("?=call") != -1) {
                /* == 0 */
                isCall = true;
            }
        }

        return isCall;
    }

    private static final String PL_TOKEN_CREATE = "create";
    private static final String PL_TOKEN_OR = "or";
    private static final String PL_TOKEN_PROCEDURE = "procedure";
    private static final String PL_TOKEN_FUNCTION = "function";
    private static final String PL_TOKEN_REPLACE = "replace";

    private static final String PL_TOKEN_IS = "is";
    private static final String PL_TOKEN_AS = "as";

    private static final String PL_TOKEN_LANGUAGE = "language";

    private static final String PL_TOKEN_PLCSQL = "plcsql";
    private static final String PL_TOKEN_JAVA = "java";

    private static final String PL_TOKEN_BEGIN = "begin";
    private static final String PL_TOKEN_END = "end";

    private static final String PL_TOKEN_LOOP = "loop";
    private static final String PL_TOKEN_CASE = "case";
    private static final String PL_TOKEN_IF = "if";

    int PL_CHECK_CONTINUE = 0;
    int PL_CHECK_CONTINUE_WITHOUT_ADVANCE = 1;
    int PL_CHECK_STOP = 2;

    private int checkComment(EditContents contents, String line, int idx) {
        switch (line.charAt(idx)) {
            case '/':
                if (idx + 1 < line.length() && line.charAt(idx + 1) == '/') {
                    if (contents.isFirstStmt == true
                            && idx != 0
                            && idx + 2 < line.length()
                            && line.charAt(idx + 2) == '+') {
                        //  SQL hint.
                        // e.g.)
                        //  select //+ RECOMPILE USE_MERGE
                        //  * from foo right outer join bar on foo.i = bar.i order by 1;
                        contents.isLastStmtValid = false;
                        contents.state = STATE_SINGLE_LINE_COMMENT_HINT_END;
                        return line.length();
                    }

                    if (contents.isFirstStmt == true && idx != 0) {
                        contents.builder.append(line.substring(0, idx));
                    }

                    contents.state = STATE_CPP_COMMENT;
                    return line.length();
                }

                if (idx + 1 < line.length() && line.charAt(idx + 1) == '*') {
                    if (contents.isFirstStmt == true
                            && idx != 0
                            && idx + 2 < line.length()
                            && line.charAt(idx + 2) == '+') {
                        // SQL Hint
                        // e.g.)
                        //  select /*+ RECOMPILE
                        //  USE_MERGE
                        //  */
                        //  * from foo right outer join bar on foo.i = bar.i order by 1;
                        contents.isLastStmtValid = false;
                        contents.state = STATE_C_COMMENT_HINT;
                        return idx + 3;
                    }

                    if (idx != 0) {
                        contents.builder.append(line.substring(0, idx));
                    }

                    idx++;
                    contents.state = STATE_C_COMMENT;
                    break;
                }
                contents.isLastStmtValid = true;
                break;
            case '-':
                if (idx + 1 < line.length() && line.charAt(idx + 1) == '-') {
                    if (contents.isFirstStmt == true && idx == 0) {

                        /* CTP Special Hints */
                        String comment =
                                line.substring(0, line.length()).trim().replaceAll(" ", "");
                        if (comment.startsWith("--@queryplan")) {
                            contents.hasQueryPlan = true;
                            contents.state = STATE_CTP_HINT;
                        } else if (comment.startsWith("--+holdcas")) {
                            contents.hasHoldcas = true;
                            contents.state = STATE_CTP_HINT;
                        } else if (comment.startsWith("--+server-output")) {
                            contents.hasServerOutput = true;
                            contents.state = STATE_CTP_HINT;
                        }

                        if (contents.state == STATE_CTP_HINT) {
                            return line.length();
                        }
                    }

                    if (contents.isFirstStmt == true
                            && idx != 0
                            && idx + 2 < line.length()
                            && line.charAt(idx + 2) == '+') {
                        // SQL Hint
                        contents.isLastStmtValid = false;
                        contents.state = STATE_SINGLE_LINE_COMMENT_HINT_END;
                        return line.length();
                    }

                    if (contents.isFirstStmt == true && idx != 0) {
                        contents.builder.append(line.substring(0, idx));
                    }

                    // idx++;
                    contents.state = STATE_SQL_COMMENT;
                    return line.length();
                    // break;
                }
                contents.isLastStmtValid = true;
                break;

            case '\'':
                /*
                    if (idx != 0) {
                        contents.builder.append(line.substring(0, idx));
                    }
                */
                contents.state = STATE_SINGLE_QUOTE;
                contents.isLastStmtValid = true;
                break;

            case '"':
                /*
                if (idx != 0) {
                    contents.builder.append(line.substring(0, idx));
                }
                            */
                contents.state = STATE_DOUBLE_QUOTE_IDENTIFIER;
                contents.isLastStmtValid = true;
                break;

            case '`':
                /*
                if (idx != 0) {
                    contents.builder.append(line.substring(0, idx));
                }
                */
                contents.state = STATE_BACKTICK_IDENTIFIER;
                contents.isLastStmtValid = true;
                break;

            case '[':
                /*
                if (idx != 0) {
                    contents.builder.append(line.substring(0, idx));
                }
                */
                contents.state = STATE_BRACKET_IDENTIFIER;
                contents.isLastStmtValid = true;
                break;

            case ' ':
            case '\t':
            case '\r':
            case '\n':
                // System.out.println ("unreachable");
                assert (false); // unreachable
                idx++;
                break;
            default:
                if (!contents.isLastStmtValid) {
                    contents.isLastStmtValid = true;
                }
                break;
        }

        return idx;
    }

    private List<SqlParam> checkParams(EditContents contents, String line) throws Exception {
        List<SqlParam> list = new ArrayList<SqlParam>();

        line = line.trim().replaceAll(";", ""); // remove ;
        String[] parts = line.split("\\,( )*\\$");
        if (parts.length % 2 != 0) {
            if (line.endsWith(",$")) {
                String[] temp = new String[parts.length + 1];
                System.arraycopy(parts, 0, temp, 0, parts.length);
                temp[temp.length - 1] = "";
                parts = temp;
            } else {
                // System.out.println(parts);
                // throw new Exception("parameters is invalid in sqlFile");
                return null;
            }
        }

        int index = 1;
        String paramType = null;
        String typeName = null;
        int type = Types.VARCHAR;
        for (int i = 0; i < parts.length; i++) {
            String value = parts[i];
            if (i % 2 == 0) { // type
                typeName = value.trim().toUpperCase();
                if (typeName.startsWith("$")) {
                    typeName = typeName.substring(1);
                }
                int position = typeName.indexOf(":");
                if (position != -1) {
                    paramType = typeName.substring(0, position);
                    typeName = typeName.substring(position + 1);
                } else {
                    paramType = null;
                }
                type = CubridUtil.getSqlType(typeName);
            } else { // value
                if (value.startsWith("$")) {
                    value = value.substring(1);
                }
                Object param = null;
                param = CubridUtil.getObject(type, value);
                SqlParam sqlParam = new SqlParam(paramType, index, param, type);
                list.add(sqlParam);

                index++;
            }
        }

        return list;
    }

    private int checkPLSubState(EditContents contents, String line, int idx) {
        switch (contents.substate) {
            case SUBSTATE_INITIAL:
                if (line.regionMatches(true, idx, PL_TOKEN_CREATE, 0, PL_TOKEN_CREATE.length())) {
                    contents.substate = SUBSTATE_SEEN_CREATE;
                    contents.plContinueStatus = PL_CHECK_CONTINUE;
                    idx += PL_TOKEN_CREATE.length();
                    return idx;
                } else {
                    // keep the contents.substate SUBSTATE_INITIAL
                    // break and proceed to the second switch
                }
                break;

            case SUBSTATE_SEEN_CREATE:
                if (line.regionMatches(true, idx, PL_TOKEN_OR, 0, PL_TOKEN_OR.length())) {
                    contents.substate = SUBSTATE_SEEN_OR;
                    contents.plContinueStatus = PL_CHECK_CONTINUE;
                    idx += PL_TOKEN_OR.length();
                    return idx;
                } else if (line.regionMatches(
                        true, idx, PL_TOKEN_PROCEDURE, 0, PL_TOKEN_PROCEDURE.length())) {
                    contents.substate = SUBSTATE_EXPECTING_IS_OR_AS;
                    contents.plContinueStatus = PL_CHECK_CONTINUE;
                    idx += PL_TOKEN_PROCEDURE.length();
                    return idx;
                } else if (line.regionMatches(
                        true, idx, PL_TOKEN_FUNCTION, 0, PL_TOKEN_FUNCTION.length())) {
                    contents.substate = SUBSTATE_EXPECTING_IS_OR_AS;
                    contents.plContinueStatus = PL_CHECK_CONTINUE;
                    idx += PL_TOKEN_FUNCTION.length();
                    return idx;
                } else {
                    contents.substate = SUBSTATE_INITIAL;
                    // break and proceed to the second switch
                }
                break;

            case SUBSTATE_SEEN_OR:
                if (line.regionMatches(true, idx, PL_TOKEN_REPLACE, 0, PL_TOKEN_REPLACE.length())) {
                    contents.substate = SUBSTATE_SEEN_REPLACE;
                    contents.plContinueStatus = PL_CHECK_CONTINUE;
                    idx += PL_TOKEN_REPLACE.length();
                    return idx;
                } else {
                    contents.substate = SUBSTATE_INITIAL;
                    // break and proceed to the second switch
                }
                break;

            case SUBSTATE_SEEN_REPLACE:
                if (line.regionMatches(
                        true, idx, PL_TOKEN_PROCEDURE, 0, PL_TOKEN_PROCEDURE.length())) {
                    contents.substate = SUBSTATE_EXPECTING_IS_OR_AS;
                    contents.plContinueStatus = PL_CHECK_CONTINUE;
                    idx += PL_TOKEN_PROCEDURE.length();
                    return idx;
                } else if (line.regionMatches(
                        true, idx, PL_TOKEN_FUNCTION, 0, PL_TOKEN_FUNCTION.length())) {
                    contents.substate = SUBSTATE_EXPECTING_IS_OR_AS;
                    contents.plContinueStatus = PL_CHECK_CONTINUE;
                    idx += PL_TOKEN_FUNCTION.length();
                    return idx;
                } else {
                    contents.substate = SUBSTATE_INITIAL;
                    // break and proceed to the second switch
                }
                break;

            case SUBSTATE_EXPECTING_IS_OR_AS:
                if (line.regionMatches(true, idx, PL_TOKEN_IS, 0, PL_TOKEN_IS.length())
                        || line.regionMatches(true, idx, PL_TOKEN_AS, 0, PL_TOKEN_AS.length())) {
                    contents.substate = SUBSTATE_PL_LANG_SPEC;
                    contents.plContinueStatus = PL_CHECK_CONTINUE;
                    idx += 2; // IS OR AS
                    return idx;
                } else {
                    // keep the contents.substate SUBSTATE_EXPECTING_IS_OR_AS
                    // break and proceed to the second switch
                }
                break;

            case SUBSTATE_PL_LANG_SPEC:
                if (line.regionMatches(
                        true, idx, PL_TOKEN_LANGUAGE, 0, PL_TOKEN_LANGUAGE.length())) {
                    contents.substate = SUBSTATE_SEEN_LANGUAGE;
                    contents.plContinueStatus = PL_CHECK_CONTINUE;
                    idx += PL_TOKEN_LANGUAGE.length();
                    return idx;
                } else {
                    // TRANSITION to SUBSTATE_PLCSQL_TEXT!!!
                    contents.substate = SUBSTATE_PLCSQL_TEXT;
                    contents.plcsqlBeginEndPairs = 0;
                    contents.plcsqlNestedLevel = 0;
                    contents.plContinueStatus =
                            PL_CHECK_CONTINUE_WITHOUT_ADVANCE; // use goto to repeat a
                    return idx;
                    // contents.substate transition
                    // without increasing p
                }

            case SUBSTATE_SEEN_LANGUAGE:
                if (line.regionMatches(true, idx, PL_TOKEN_PLCSQL, 0, PL_TOKEN_PLCSQL.length())) {
                    // TRANSITION to SUBSTATE_PLCSQL_TEXT!!!
                    contents.substate = SUBSTATE_PLCSQL_TEXT;
                    contents.plcsqlBeginEndPairs = 0;
                    contents.plcsqlNestedLevel = 0;
                    contents.plContinueStatus = PL_CHECK_CONTINUE;
                    idx += PL_TOKEN_JAVA.length();
                    return idx;
                } else if (line.regionMatches(
                        true, idx, PL_TOKEN_JAVA, 0, PL_TOKEN_JAVA.length())) {
                    contents.substate = SUBSTATE_INITIAL;
                    contents.plContinueStatus = PL_CHECK_CONTINUE;
                    idx += PL_TOKEN_PLCSQL.length();
                    return idx;
                } else {
                    // syntax error
                    contents.substate = SUBSTATE_INITIAL;
                    // break and proceed to the second switch
                }
                break;

            case SUBSTATE_PLCSQL_TEXT:
                if (line.regionMatches(
                        true, idx, PL_TOKEN_PROCEDURE, 0, PL_TOKEN_PROCEDURE.length())) {
                    if (contents.plcsqlBeginEndPairs == 0) {
                        contents.plcsqlNestedLevel++;
                    }
                    contents.plContinueStatus = PL_CHECK_CONTINUE;
                    idx += PL_TOKEN_PROCEDURE.length();
                    return idx;
                } else if (line.regionMatches(
                        true, idx, PL_TOKEN_FUNCTION, 0, PL_TOKEN_FUNCTION.length())) {
                    if (contents.plcsqlBeginEndPairs == 0) {
                        contents.plcsqlNestedLevel++;
                    }
                    contents.plContinueStatus = PL_CHECK_CONTINUE;
                    idx += PL_TOKEN_FUNCTION.length();
                    return idx;
                } else if (line.regionMatches(
                        true, idx, PL_TOKEN_CASE, 0, PL_TOKEN_CASE.length())) {
                    // case can start an expression and can appear in a balance 0 area
                    if (contents.plcsqlBeginEndPairs == 0) {
                        contents.plcsqlNestedLevel++;
                    }
                    contents.plcsqlBeginEndPairs++;
                    contents.plContinueStatus = PL_CHECK_CONTINUE;
                    idx += PL_TOKEN_CASE.length();
                    return idx;
                } else if (line.regionMatches(
                        true, idx, PL_TOKEN_BEGIN, 0, PL_TOKEN_BEGIN.length())) {
                    contents.plcsqlBeginEndPairs++;
                    contents.plContinueStatus = PL_CHECK_CONTINUE;
                    idx += PL_TOKEN_BEGIN.length();
                    return idx;
                } else if (line.regionMatches(true, idx, PL_TOKEN_IF, 0, PL_TOKEN_IF.length())) {
                    contents.plcsqlBeginEndPairs++;
                    contents.plContinueStatus = PL_CHECK_CONTINUE;
                    idx += PL_TOKEN_IF.length();
                    return idx;
                } else if (line.regionMatches(
                        true, idx, PL_TOKEN_LOOP, 0, PL_TOKEN_LOOP.length())) {
                    contents.plcsqlBeginEndPairs++;
                    contents.plContinueStatus = PL_CHECK_CONTINUE;
                    idx += PL_TOKEN_LOOP.length();
                    return idx;
                } else if (line.regionMatches(true, idx, PL_TOKEN_END, 0, PL_TOKEN_END.length())) {
                    contents.substate = SUBSTATE_SEEN_END;
                    contents.plContinueStatus = PL_CHECK_CONTINUE;
                    idx += PL_TOKEN_END.length();
                    return idx;
                } else {
                    // keep the contents.substate SUBSTATE_PLCSQL_TEXT
                    // break and proceed to the second switch
                }
                break;

            case SUBSTATE_SEEN_END:
                contents.plcsqlBeginEndPairs--;
                if (contents.plcsqlBeginEndPairs < 0) {
                    // syntax error
                    contents.plcsqlBeginEndPairs = 0;
                }
                if (contents.plcsqlBeginEndPairs == 0) {
                    contents.plcsqlNestedLevel--;
                    if (contents.plcsqlNestedLevel < 0) {
                        // the last END closing PL/CSQL text was found
                        contents.state = STATE_STATEMENT_END;
                        contents.substate = SUBSTATE_INITIAL;
                        contents.plcsqlBeginEndPairs = 0;
                        contents.plcsqlNestedLevel = 0;
                        contents.plContinueStatus =
                                PL_CHECK_CONTINUE_WITHOUT_ADVANCE; // use goto to repeat a
                        // contents.substate transition
                        // without increasing p
                        return idx;
                    }
                }

                contents.substate = SUBSTATE_PLCSQL_TEXT;

                // match if/case/loop if exists, but just advance p and ignore them
                if (line.regionMatches(true, idx, PL_TOKEN_CASE, 0, PL_TOKEN_CASE.length())) {
                    contents.plContinueStatus = PL_CHECK_CONTINUE;
                    idx += PL_TOKEN_CASE.length();
                    return idx;
                } else if (line.regionMatches(true, idx, PL_TOKEN_IF, 0, PL_TOKEN_IF.length())) {
                    contents.plContinueStatus = PL_CHECK_CONTINUE;
                    idx += PL_TOKEN_IF.length();
                    return idx;
                } else if (line.regionMatches(
                        true, idx, PL_TOKEN_LOOP, 0, PL_TOKEN_LOOP.length())) {
                    contents.plContinueStatus = PL_CHECK_CONTINUE;
                    idx += PL_TOKEN_LOOP.length();
                    return idx;
                } else {
                    contents.plContinueStatus =
                            PL_CHECK_CONTINUE_WITHOUT_ADVANCE; // use goto to repeat a
                    // contents.substate transition
                    // without increasing p
                    return idx;
                }

            default:
                assert (false); // unreachable
        }

        contents.plContinueStatus = PL_CHECK_STOP;
        return idx;
    }

    private class EditContents {
        public int state;
        public int substate;

        public int plContinueStatus;
        public boolean isLastStmtValid;
        public boolean isIncludeStmt;
        public boolean isFirstStmt;

        public int plcsqlBeginEndPairs = 0;
        public int plcsqlNestedLevel = 0;

        public boolean hasHoldcas;
        public boolean hasQueryPlan;
        public boolean hasServerOutput;

        public String connId;
        public List<SqlParam> params;

        public StringBuilder builder;
        public String line;
        public int sqlCnt;
        public int currIdx;

        public EditContents() {
            clear();

            params = null;
            connId = "";
            sqlCnt = 1;
        }

        public void clear() {
            state = STATE_GENERAL;
            substate = SUBSTATE_INITIAL;
            isFirstStmt = true;
            isIncludeStmt = false;
            isLastStmtValid = true;
            plcsqlBeginEndPairs = 0;
            plcsqlNestedLevel = 0;

            hasHoldcas = false;
            hasQueryPlan = false;
            hasServerOutput = false;

            params = null;
            connId = "";
            plContinueStatus = PL_CHECK_STOP;

            builder = new StringBuilder();

            line = null;
            currIdx = 0;
        }

        @Override
        public String toString() {
            return "EditContents [state="
                    + state
                    + ", substate="
                    + substate
                    + ", isLastStmtValid="
                    + isLastStmtValid
                    + ", isIncludeStmt="
                    + isIncludeStmt
                    + ", plcsqlBeginEndPairs="
                    + plcsqlBeginEndPairs
                    + ", plcsqlNestedLevel="
                    + plcsqlNestedLevel
                    + ", hasHoldcas="
                    + hasHoldcas
                    + ", hasQueryPlan="
                    + hasQueryPlan
                    + ", hasServerOutput="
                    + hasServerOutput
                    + ", connId="
                    + connId
                    + ", params="
                    + params
                    + "]";
        }
    }

    // add hint for CREATE/ALTER/INSERT/UPDATE/DELETE/DROP/MERGE/PREPARE/REPLACE
    // sql
    private String addHintForSQL(String sql, boolean isNewLine, int lineNum, String sqlFile) {
        String ret = "";
        String prefix_tmpStr = "";
        String prefix_sql = "";
        String suff_sql = "";
        int prefix_pos = -1;
        boolean hasHint = false;
        String copy_SQL = sql;
        String tmpStr = sql;
        String script = sql.toLowerCase();
        tmpStr = tmpStr.replaceAll(" ", "");
        if (tmpStr.length() >= 4) {
            prefix_tmpStr = tmpStr.substring(0, 4);

        } else {
            return sql;
        }

        if (isNewLine) {
            if ("CREA".equalsIgnoreCase(prefix_tmpStr)) {
                prefix_pos = script.indexOf("create") + 6;
                prefix_sql = copy_SQL.substring(0, prefix_pos);
                suff_sql = copy_SQL.substring(prefix_pos, copy_SQL.length());
                hasHint = hasHint(copy_SQL);
                ret =
                        reConstructSQLForHint(
                                copy_SQL, prefix_sql, suff_sql, sqlFile, lineNum, hasHint);

            } else if ("ALTE".equalsIgnoreCase(prefix_tmpStr)) {
                prefix_pos = script.indexOf("alter") + 5;
                prefix_sql = copy_SQL.substring(0, prefix_pos);
                suff_sql = copy_SQL.substring(prefix_pos, copy_SQL.length());
                hasHint = hasHint(copy_SQL);
                ret =
                        reConstructSQLForHint(
                                copy_SQL, prefix_sql, suff_sql, sqlFile, lineNum, hasHint);

                // }else if("DROP".equalsIgnoreCase(prefix_tmpStr))
                // {
                // prefix_pos = script.indexOf("drop") + 4;
                // prefix_sql = copy_SQL.substring(0, prefix_pos);
                // suff_sql = copy_SQL.substring(prefix_pos, copy_SQL.length());
                // hasHint = hasHint(copy_SQL);
                // ret = reConstructSQLForHint(copy_SQL, prefix_sql, suff_sql,
                // sqlFile, lineNum, hasHint);
                //
            } else if ("UPDA".equalsIgnoreCase(prefix_tmpStr)) {
                prefix_pos = script.indexOf("update") + 6;
                prefix_sql = copy_SQL.substring(0, prefix_pos);
                suff_sql = copy_SQL.substring(prefix_pos, copy_SQL.length());
                hasHint = hasHint(copy_SQL);
                ret =
                        reConstructSQLForHint(
                                copy_SQL, prefix_sql, suff_sql, sqlFile, lineNum, hasHint);

            } else if ("SELE".equalsIgnoreCase(prefix_tmpStr)) {
                prefix_pos = script.indexOf("select") + 6;
                prefix_sql = copy_SQL.substring(0, prefix_pos);
                suff_sql = copy_SQL.substring(prefix_pos, copy_SQL.length());
                hasHint = hasHint(copy_SQL);
                ret =
                        reConstructSQLForHint(
                                copy_SQL, prefix_sql, suff_sql, sqlFile, lineNum, hasHint);

            } else if ("DELE".equalsIgnoreCase(prefix_tmpStr)) {
                prefix_pos = script.indexOf("delete") + 6;
                prefix_sql = copy_SQL.substring(0, prefix_pos);
                suff_sql = copy_SQL.substring(prefix_pos, copy_SQL.length());
                hasHint = hasHint(copy_SQL);
                ret =
                        reConstructSQLForHint(
                                copy_SQL, prefix_sql, suff_sql, sqlFile, lineNum, hasHint);

            } else if ("MERG".equalsIgnoreCase(prefix_tmpStr)) {
                prefix_pos = script.indexOf("merge") + 5;
                prefix_sql = copy_SQL.substring(0, prefix_pos);
                suff_sql = copy_SQL.substring(prefix_pos, copy_SQL.length());
                hasHint = hasHint(copy_SQL);
                ret =
                        reConstructSQLForHint(
                                copy_SQL, prefix_sql, suff_sql, sqlFile, lineNum, hasHint);
            } else if ("INSE".equalsIgnoreCase(prefix_tmpStr)) {
                prefix_pos = script.indexOf("insert") + 6;
                prefix_sql = copy_SQL.substring(0, prefix_pos);
                suff_sql = copy_SQL.substring(prefix_pos, copy_SQL.length());
                hasHint = hasHint(copy_SQL);
                ret =
                        reConstructSQLForHint(
                                copy_SQL, prefix_sql, suff_sql, sqlFile, lineNum, hasHint);
            } else if ("PREP".equalsIgnoreCase(prefix_tmpStr)) {
                prefix_pos = script.indexOf("select") + 6;
                prefix_sql = copy_SQL.substring(0, prefix_pos);
                suff_sql = copy_SQL.substring(prefix_pos, copy_SQL.length());
                hasHint = hasHint(copy_SQL);
                ret =
                        reConstructSQLForHint(
                                copy_SQL, prefix_sql, suff_sql, sqlFile, lineNum, hasHint);
            } else if ("REPL".equalsIgnoreCase(prefix_tmpStr)) {
                prefix_pos = script.indexOf("replace") + 7;
                prefix_sql = copy_SQL.substring(0, prefix_pos);
                suff_sql = copy_SQL.substring(prefix_pos, copy_SQL.length());
                hasHint = hasHint(copy_SQL);
                ret =
                        reConstructSQLForHint(
                                copy_SQL, prefix_sql, suff_sql, sqlFile, lineNum, hasHint);
            } else {
                ret = sql;
            }

        } else {
            ret = sql;
        }

        return ret;
    }

    private boolean hasHint(String sql) {
        boolean ret = false;
        String copySQL = sql;
        int hasCompileKey = -1;
        int hasSlashAndStar = -1;
        if (copySQL != null && copySQL.length() != 0) {
            hasCompileKey = copySQL.toUpperCase().indexOf("RECOMPILE");
            hasSlashAndStar = copySQL.indexOf("/*");
        }

        if (hasCompileKey != -1 && hasSlashAndStar != -1) {
            ret = true;
        }

        return ret;
    }

    private String reConstructSQLForHint(
            String origSQL,
            String prefSQL,
            String suffSQL,
            String sqlFile,
            int lineNum,
            boolean hasHint) {
        String ret = "";

        if (hasHint) {
            int start_pos = origSQL.indexOf("*/");
            String sub_Prefix = origSQL.substring(0, start_pos);
            String sub_Suff = origSQL.substring(start_pos, origSQL.length());
            ret =
                    sub_Prefix
                            + TestUtil.APPEND_HINT_PREFIX
                            + sqlFile
                            + ":"
                            + lineNum
                            + TestUtil.APPEND_HINT_SUFFIX
                            + " "
                            + sub_Suff;
        } else {
            ret =
                    prefSQL
                            + " "
                            + TestUtil.HINT_PREFIX
                            + sqlFile
                            + ":"
                            + lineNum
                            + TestUtil.HINT_SUFFIX
                            + " "
                            + suffSQL;
        }

        return ret;
    }
}
