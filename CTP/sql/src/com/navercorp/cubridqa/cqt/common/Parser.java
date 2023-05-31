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
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

public class Parser {
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

    private static final int SUBSTATE_INITIAL = 50;
    private static final int SUBSTATE_SEEN_CREATE = 51;
    private static final int SUBSTATE_SEEN_OR = 52;
    private static final int SUBSTATE_SEEN_REPLACE = 53;
    private static final int SUBSTATE_EXPECTING_IS_OR_AS = 54;
    private static final int SUBSTATE_PL_LANG_SPEC = 55;
    private static final int SUBSTATE_SEEN_LANGUAGE = 56;
    private static final int SUBSTATE_PLCSQL_TEXT = 57;
    private static final int SUBSTATE_SEEN_END = 58;

    public Parser() {}

    public List<Sql> parseSqlFile(String sqlfilePath, Test test) {
        List<Sql> list = new ArrayList<Sql>();

        BufferedReader reader = null;
        try {
            reader =
                    new BufferedReader(
                            new InputStreamReader(
                                    new FileInputStream(new File(sqlfilePath)), test.getCodeset()));
            EditContents cts = new EditContents();

            int lineCnt = 0;
            boolean isInBlock = false;

            boolean hasNewStmt = false;
            boolean needNewLine = true;
            String line = null;

            while (true) {
                lineCnt++;
                hasNewStmt = false;

                if (needNewLine) {
                    line = reader.readLine();
                    needNewLine = false;
                }

                if (line == null) {
                    /* if eof is reached, execute all */
                    isInBlock = false;
                    break;
                } else {
                    line = line.trim();
                    // System.out.println("line (" + lineCnt + "): " + line);
                    int idx = parseLine(cts, line);
                    if (idx == -1) {
                        needNewLine = true;
                        continue;
                    }

                    // System.out.println (line);
                    // System.out.println (idx);

                    isInBlock = isStatementInBlock(cts);

                    if (idx > line.length()) {
                        idx = line.length();
                        System.out.println("hmm?");
                    }

                    // System.out.println("idx:" + idx);
                    // System.out.println(line + "("+ line.length() +")");

                    String token = line.substring(0, idx).trim();
                    // System.out.println ("toAdd = " + toAdd);
                    if (cts.state == STATE_SEMICOLON) {
                        if (cts.builder.length() == 0 && token.replaceAll(";", "").isEmpty()) {
                            idx++;
                            needNewLine = true;
                            cts.state = STATE_GENERAL;
                            continue;
                        }
                        cts.state = STATE_STATEMENT_END;
                    }

                    if (cts.state == STATE_CPP_COMMENT
                            || cts.state == STATE_SQL_COMMENT
                            || cts.state == STATE_PARAMETER) {
                        // cts.builder.append("\n");
                        // ignore comments
                        needNewLine = true;
                        cts.state = STATE_GENERAL;
                        continue;
                    } else if (cts.state == STATE_C_COMMENT) {
                        cts.state = STATE_GENERAL;
                        continue;
                    }

                    cts.builder.append(token);
                    if (needNewLine) {
                        cts.builder.append('\n');
                    } else {
                        cts.builder.append(' ');
                    }
                    /*
                    else {
                        needNewLine = true;
                        continue;
                    }
                    */

                    if (idx == line.length()) {
                        needNewLine = true;
                    } else {
                        // System.out.println("idx:" + idx);
                        line = line.substring(idx);
                        if (line.isEmpty()) {
                            needNewLine = true;
                        }
                        // System.out.println(line + "(" + line.length() + ")");
                    }

                    // System.out.println (cts.state);

                    if (cts.state == STATE_STATEMENT_END) {
                        hasNewStmt = true;
                    } else {
                        continue;
                    }
                }

                if (hasNewStmt) {
                    String str = cts.builder.toString();

                    // System.out.println ("complete = " + str);

                    if (str.isEmpty()) {
                        // do not add empty string e.g.) INSERT INTO dba.DCL1(id) VALUES(1);;
                        cts.clear();
                        needNewLine = true;
                        continue;
                    }

                    // System.out.println("complete = " + str);
                    boolean isCall = checkIsCall(str);

                    // e.g.) @t1: insert into xoo values(1, 1);
                    int pos = str.indexOf(":");
                    if (str.startsWith("@")) {
                        cts.connId = str.substring(1, pos);
                        str = str.substring(pos + 1);
                    }

                    // set sql hint for debug
                    if (test.isNeedDebugHint()) {
                        /*
                         * String tmpSQL = line;
                         * String hint_sql = addHintForSQL(tmpSQL, isNewStatement, lineCount, sqlFile);
                         *
                         * ret.append(hint_sql + "\n");
                         * } else {
                         * ret.append(line + "\n");
                         */
                    }

                    Sql sql = new Sql(cts.connId, str, cts.params, isCall);
                    sql.setQueryplan(cts.hasQueryPlan);
                    list.add(sql);

                    cts.clear();

                    cts.builder = new StringBuilder();
                } else {
                    // System.out.println("incomplete = " + cts.builder.toString());
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

    private int parseLine(EditContents contents, String line) throws Exception {
        int i = -1;
        if (line == null || line.isEmpty()) {
            return i;
        }

        contents.isLastStmtValid = true;
        contents.isIncludeStmt = false;

        if (contents.state == STATE_CPP_COMMENT || contents.state == STATE_SQL_COMMENT) {
            /* these are single line comments and we're parsing a new line */
            contents.state = STATE_GENERAL;
        }

        if (contents.state == STATE_STATEMENT_END) {
            /* reset state in prev statement */
            contents.state = STATE_GENERAL;
            contents.substate = SUBSTATE_INITIAL;
        }

        int prev = i;
        int lineCnt = line.length();

        boolean firstIteration = true;
        for (i = 0; i < lineCnt; i++) {
            switch (contents.state) {
                case STATE_GENERAL:
                    {
                        if (isBlank(line.charAt(i))) {
                            prev = i; // remove black
                            continue;
                        }

                        if (firstIteration == true && line.charAt(i) == '$') {
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
                                return i;
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

                        if (isSemicolon(line.charAt(i))) {
                            if (contents.substate != SUBSTATE_PLCSQL_TEXT) {
                                while (i + 1 < lineCnt) {
                                    if (isSemicolon(line.charAt(i + 1))) {
                                        // consume multiple semicolons
                                        i++;
                                    }
                                    break;
                                }
                                assert (contents.substate != SUBSTATE_SEEN_END);
                                contents.isIncludeStmt = true;
                                contents.isLastStmtValid = false;

                                // initialize the state variables used to identify PL/CSQL text
                                contents.substate = SUBSTATE_INITIAL;
                                contents.plcsqlBeginEndPairs = 0;
                                contents.plcsqlNestedLevel = 0;

                                contents.state = STATE_SEMICOLON;

                                return i + 1;
                            }
                        }

                        prev = i;
                        i = checkComment(contents, line, i);
                        break;
                    }

                case STATE_C_COMMENT:
                    if (line.charAt(i) == '*') {
                        if ((i + 1 < lineCnt) && line.charAt(i + 1) == '/') {
                            contents.state = STATE_C_COMMENT;
                            i++;
                            return i + 1;
                        }
                    }
                    break;

                case STATE_CPP_COMMENT: /* single line comment */
                    // if (line.charAt(i) == '\n' || i == line.length() - 1) {
                    String cppComment =
                            line.substring(prev, line.length()).trim().replaceAll(" ", "");
                    if (cppComment.startsWith("//+")) {
                        contents.state = STATE_GENERAL; /* SQL hint. e.g.)
                                                                    select //+ RECOMPILE USE_MERGE
                                                                    * from foo right outer join bar on foo.i = bar.i order by 1; */
                        break;
                    } else {
                        contents.state = STATE_CPP_COMMENT;
                    }
                    return line.length();
                    // }
                    // break;

                case STATE_SQL_COMMENT: /* single line comment */
                    // if (line.charAt(i) == '\n' || i == line.length() - 1) {
                    String comment = line.substring(prev, line.length()).trim().replaceAll(" ", "");
                    if (comment.startsWith("--@queryplan")) {
                        contents.hasQueryPlan = true;
                        contents.state = STATE_STATEMENT_END;
                    } else if (comment.startsWith("--+holdcas")) {
                        contents.hasHoldcas = true;
                        contents.state = STATE_STATEMENT_END;
                    } else if (comment.startsWith("--+server-output")) {
                        contents.hasServerOutput = true;
                        contents.state = STATE_STATEMENT_END;
                    } else if (comment.startsWith("--+")) {
                        // sql hints
                        contents.state = STATE_GENERAL;
                        break;
                    } else {
                        contents.state = STATE_SQL_COMMENT;
                    }

                    return line.length();
                    // }
                    // break;

                    /*
                    case STATE_SINGLE_QUOTE:
                        if (line.charAt(i) == '\'') {
                            if ((i + 1 < lineCnt) && line.charAt(i + 1) == '\'') {
                                i++;
                            } else {
                                contents.state = STATE_GENERAL;
                            }
                        }
                        break;
                    case STATE_MYSQL_QUOTE:
                         if (line.charAt(i) == '\"') {
                         if ((i + 1 < lineCnt) && line.charAt(i + 1) == '\"') {
                            i++;
                        } else {
                            contents.state = STATE_GENERAL;
                        }
                    }
                        break;
                    */

                    /*
                    case STATE_DOUBLE_QUOTE_IDENTIFIER:
                        if (line.charAt(i) == '"') {
                            contents.state = STATE_GENERAL;
                        }
                        break;
                    */

                    /*
                    case STATE_BACKTICK_IDENTIFIER:
                        if (line.charAt(i) == '`') {
                            contents.state = STATE_GENERAL;
                        }
                        break;

                    case STATE_BRACKET_IDENTIFIER:
                        if (line.charAt(i) == ']') {
                            contents.state = STATE_GENERAL;
                        }
                        break;
                    */

                case STATE_PARAMETER:
                    List<SqlParam> params = checkParams(contents, line);
                    if (params == null) {
                        // is not parameter form or invalid params
                        contents.state = STATE_GENERAL; // continue parsing
                    } else {
                        // params are created succesfully
                        contents.params = params;
                        return line.length();
                    }
                    break;

                    /*
                    case STATE_SEMICOLON:
                        assert (contents.substate != SUBSTATE_SEEN_END);
                        contents.isIncludeStmt = true;
                        contents.isLastStmtValid = false;

                        // initialize the state variables used to identify PL/CSQL text
                        contents.substate = SUBSTATE_INITIAL;
                        contents.plcsqlBeginEndPairs = 0;
                        contents.plcsqlNestedLevel = 0;
                        break;
                    */

                default:
                    /* should not be here */
                    break;
            }
        }

        /* when include other stmts and the last smt is non sense stmt. */
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
                    contents.state = STATE_CPP_COMMENT;
                    idx++;
                    break;
                }
                if (idx + 1 < line.length() && line.charAt(idx + 1) == '*') {
                    contents.state = STATE_C_COMMENT;
                    idx++;
                    break;
                }
                contents.isLastStmtValid = true;
                break;
            case '-':
                if (idx + 1 < line.length() && line.charAt(idx + 1) == '-') {
                    contents.state = STATE_SQL_COMMENT;
                    idx++;
                    break;
                }
                contents.isLastStmtValid = true;
                break;

                /*
                case '\'':
                    contents.state = STATE_SINGLE_QUOTE;
                    contents.isLastStmtValid = true;
                    break;
                case '"':
                */
                /*
                 * TODO:
                 * if (prm_get_bool_value (PRM_ID_ANSI_QUOTES) == false)
                 * {
                 * contents.state = STATE_MYSQL_QUOTE;
                 * }
                 * else
                 *
                 */

                /*
                {
                    contents.state = STATE_DOUBLE_QUOTE_IDENTIFIER;
                }
                contents.isLastStmtValid = true;
                break;
                */
                /*
                case '`':
                    contents.state = STATE_BACKTICK_IDENTIFIER;
                    contents.isLastStmtValid = true;
                    break;
                case '[':
                    contents.state = STATE_BRACKET_IDENTIFIER;
                    contents.isLastStmtValid = true;
                    break;

                case ';':
                    if (contents.substate != SUBSTATE_PLCSQL_TEXT) {
                        assert (contents.substate != SUBSTATE_SEEN_END);
                        contents.state = STATE_SEMICOLON;
                    }
                break;
                */

            case ' ':
            case '\t':
            case '\r':
            case '\n':
                // System.out.println ("unreachable");
                assert (false); // unreachable
                break;
            default:
                if (!contents.isLastStmtValid) {
                    contents.isLastStmtValid = true;
                }
                break;
        }

        if (contents.state == STATE_C_COMMENT) {
            if (idx + 1 < line.length() && line.charAt(idx + 1) == '+') {
                // SQL Hint
                contents.isLastStmtValid = false;
                contents.state = STATE_GENERAL;
                idx++;
            }
        }

        return idx;
    }

    private List<SqlParam> checkParams(EditContents contents, String line) throws Exception {
        List<SqlParam> list = new ArrayList<SqlParam>();

        line = line.replaceAll(";", ""); // remove ;
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

        public int plcsqlBeginEndPairs = 0;
        public int plcsqlNestedLevel = 0;

        public boolean hasHoldcas;
        public boolean hasQueryPlan;
        public boolean hasServerOutput;

        public String connId;
        public List<SqlParam> params;

        public StringBuilder builder;
        public String line;
        public int currIdx;

        public EditContents() {
            clear();

            params = null;
            connId = "";
        }

        public void clear() {
            state = STATE_GENERAL;
            substate = SUBSTATE_INITIAL;
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
}
