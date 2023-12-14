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
package com.navercorp.cubridqa.cqt.common;

import com.navercorp.cubridqa.cqt.console.bean.Sql;
import com.navercorp.cubridqa.cqt.console.bean.SqlParam;
import com.navercorp.cubridqa.cqt.console.util.CubridUtil;
import com.navercorp.cubridqa.cqt.console.util.TestUtil;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SQLParser {
    /**
     * parse the sql script file .
     *
     * @param sqlFile
     * @return
     */
    public static List<Sql> parseSqlFile(String sqlFile, String codeset, boolean isNeedDebugHint) {
        List<Sql> list = new ArrayList<Sql>();
        BufferedReader reader = null;
        try {
            reader =
                    new BufferedReader(
                            new InputStreamReader(new FileInputStream(new File(sqlFile)), codeset));
            String line = reader.readLine();
            int lineCount = 1;

            StringBuilder ret = new StringBuilder();
            List<SqlParam> paramList = null;
            String connId = "";
            boolean isCall = false;
            boolean isNewStatement = true;
            boolean isQueryplan = false;

            LineScanner lineScanner = new LineScanner();

            while (line != null) {
                line = line.trim();

                if ("".equals(line)) {
                    // empty line. do nothing
                } else if (line.startsWith("--")) {

                    if ("--@queryplan".equals(line.trim())) {
                        isQueryplan = true;
                    } else {
                        String controlCmd = getControlCommand(line);
                        if (controlCmd != null) {
                            // control command : either hodcas or server-message
                            Sql sql = new Sql("", controlCmd, null, false);
                            list.add(sql);
                        }
                    }
                } else if (line.startsWith("$")) {
                    // parameters
                    paramList = getParamList(line, sqlFile);
                } else {
                    // statement

                    if (isNewStatement) {
                        if (line.startsWith("@")) {
                            int pos = line.indexOf(":");
                            if (pos != -1) {
                                connId = line.substring(1, pos);
                                line = line.substring(pos + 1);
                            }
                        }
                    }

                    // set sql hint for debug
                    if (isNeedDebugHint) {
                        String hint_sql = addHintForSQL(line, isNewStatement, lineCount, sqlFile);
                        ret.append(hint_sql + "\n");
                    } else {
                        ret.append(line + "\n");
                    }

                    lineScanner.scan(line);
                    // the following condition should be replaced with is_statement_end()
                    // but it hugely alters the test results.
                    if (line.endsWith(";") && !lineScanner.isInPlcsqlText()) {

                        isCall = isCall(line);

                        // new sql
                        Sql sql = new Sql(connId, ret.toString(), paramList, isCall);
                        sql.setQueryplan(isQueryplan);
                        list.add(sql);

                        // initialize state variables
                        isNewStatement = true;
                        isQueryplan = false;
                        ret.setLength(0);
                        paramList = null;
                        isCall = false;
                        connId = "";
                    } else {
                        isNewStatement = false;
                    }

                    if (lineScanner.isStatementEnd()) {
                        lineScanner.clear();
                    }
                }

                line = reader.readLine();
                lineCount++;
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

    private static String getControlCommand(String line) {
        if (line.startsWith("--+")) {
            String s = line.replaceAll(" ", "").toLowerCase();
            if (s.startsWith("--+holdcas") || s.startsWith("--+server-message")) {
                return line.trim() + System.getProperty("line.separator");
            }
        }

        return null;
    }

    private static boolean isCall(String line) {

        boolean isCall = false;

        int positionCall = line.replaceAll(" ", "").indexOf("call");
        if (positionCall != -1) {
            if (positionCall == 0) {
                int position1 = line.indexOf("(");
                int position2 = line.indexOf(")");
                if (position1 != -1 && position2 != -1) {
                    String params = line.substring(position1, position2);
                    if (params.indexOf("?") != -1) {
                        isCall = true;
                    }
                }
            } else if (line.replaceAll(" ", "").indexOf("?=call") == 0) {
                isCall = true;
            }
        }

        return isCall;
    }

    // add hint for CREATE/ALTER/INSERT/UPDATE/DELETE/DROP/MERGE/PREPARE/REPLACE
    // sql
    private static String addHintForSQL(
            String sql, boolean isNewLine, int lineNum, String sqlFile) {
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

    private static ArrayList<SqlParam> getParamList(String line, String sqlFile) {

        ArrayList<SqlParam> ret = new ArrayList<SqlParam>();

        if (line.endsWith(";")) {
            line = line.substring(0, line.length() - 1);
        }

        String[] parts = line.split(",( )*\\$");
        if (parts.length % 2 != 0) {
            if (line.matches(".+,( )*\\$$")) {
                parts = Arrays.copyOf(parts, parts.length + 1);
                parts[parts.length - 1] = "";
            } else {
                throw new RuntimeException("parameters is invalid in sqlFile:" + sqlFile);
            }
        }
        assert (parts[0].startsWith("$"));
        parts[0] = parts[0].substring(1);

        int index = 1;
        String paramType = null;
        int type = 0;
        for (int i = 0; i < parts.length; i++) {
            String value = parts[i];

            if (i % 2 == 0) { // type
                String typeName = value.trim().toUpperCase();
                int position = typeName.indexOf(":");
                if (position != -1) {
                    paramType = typeName.substring(0, position);
                    typeName = typeName.substring(position + 1);
                } else {
                    paramType = null;
                }
                type = CubridUtil.getSqlType(typeName);
            } else { // value
                Object param = CubridUtil.getObject(type, value);
                ret.add(new SqlParam(paramType, index, param, type));

                index++;
            }
        }

        return ret;
    }

    private static String reConstructSQLForHint(
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

    private static boolean hasHint(String sql) {
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
}
