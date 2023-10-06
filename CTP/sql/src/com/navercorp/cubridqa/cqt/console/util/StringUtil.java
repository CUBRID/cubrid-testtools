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

import com.navercorp.cubridqa.cqt.console.bean.SystemModel;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.StringTokenizer;

public class StringUtil {

    /**
     * replace null to ""
     *
     * @param src
     * @return
     */
    public static String nullToEmpty(String src) {
        if (src == null) {
            return "";
        } else {
            return src;
        }
    }

    /**
     * replace '\' to '/'
     *
     * @param src
     * @return
     */
    public static String replaceSlash(String src) {
        if (src == null) {
            return null;
        }

        String ret = null;
        StringBuilder sb = new StringBuilder();
        StringTokenizer tokens = new StringTokenizer(src, "\\");
        while (tokens.hasMoreTokens()) {
            sb.append(tokens.nextToken() + "/");
        }
        ret = sb.toString();

        if (!src.endsWith("\\") && ret.length() > 0) {
            ret = ret.substring(0, ret.length() - 1);
        }
        return ret;
    }

    /**
     * replace '/' to '\'
     *
     * @param src
     * @return
     */
    public static String restoreSlash(String src) {
        if (src == null) {
            return null;
        }

        String ret = null;
        StringBuilder sb = new StringBuilder();
        StringTokenizer tokens = new StringTokenizer(src, "/");
        while (tokens.hasMoreTokens()) {
            sb.append(tokens.nextToken() + "\\");
        }
        ret = sb.toString();

        if (!src.endsWith("/") && ret.length() > 0) {
            ret = ret.substring(0, ret.length() - 1);
        }
        return ret;
    }

    /**
     * replace the exception message to ?
     *
     * @param message
     * @return
     */
    public static String replaceExceptionMessage(String message) {
        if (message == null) {
            return null;
        } else {
            message = message.replaceAll("[0-9]+", "?");
            int position1 = message.indexOf("Your transaction (");
            if (position1 != -1) {
                int position2 = message.indexOf(")");
                if (position2 != -1 && position2 > position1) {
                    message =
                            message.substring(0, position1)
                                    + "Your transaction (?"
                                    + message.substring(position2);
                }
                return message;
            } else {
                return message;
            }
        }
    }

    public static String replaceSlashBasedSystem(String src) {
        if (src == null) return null;

        String p;
        p = src.trim().replace('\\', '/');

        return p.replace('/', File.separatorChar);
    }

    /**
     * replace query plan
     *
     * @param queryPlan
     * @return
     */
    public static String replaceQureyPlan(String queryPlan) {
        if (queryPlan == null) {
            return null;
        }
        SystemModel systemModel =
                (SystemModel)
                        XstreamHelper.fromXml(
                                EnvGetter.getenv("CTP_HOME")
                                        + File.separator
                                        + "sql/configuration/System.xml");
        if (!systemModel.isQueryPlan()) {
            queryPlan = queryPlan.replaceAll("[0-9]+", "?");
        }
        StringBuilder ret = new StringBuilder();

        String flag = "msg";
        int stmtCount = 0;
        String separator = System.getProperty("line.separator");
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new StringReader(queryPlan));

            String message = reader.readLine();
            while (message != null) {
                if (message.trim().equals("")) {
                    message = reader.readLine();
                    continue;
                }

                if (message.startsWith("Query plan:")) {
                    flag = "plan";
                    ret.append(message + separator);
                    message = reader.readLine();
                    continue;
                } else if (message.startsWith("Query stmt:")) {
                    flag = "stmt";
                    ret.append(message + separator);
                    stmtCount = 0;
                    message = reader.readLine();
                    continue;
                }

                if (systemModel.isQueryPlan()) {
                    ret.append(message + separator);
                } else {
                    if ("plan".equals(flag)) {
                        ret.append(message + separator);
                    } else if ("stmt".equals(flag)) {
                        if (stmtCount == 0) {
                            ret.append(message + separator);
                            stmtCount++;
                        } else if (message.startsWith("/")) {
                            ret.append(message + separator);
                        }
                    }
                }

                message = reader.readLine();
            }
        } catch (Exception e) {
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                }
            }
        }
        return ret.toString();
    }

    /**
     * translate byte array to Hex String .
     *
     * @param b
     * @return
     */
    public static String toHexString(byte[] b) {
        StringBuffer buffer = new StringBuffer();
        for (int i = 0; i < b.length; ++i) {
            buffer.append(toHexString(b[i]));
        }
        return buffer.toString();
    }

    /**
     * replace dir for Windows OS which using cygwin.
     *
     * @param filename
     * @return
     */
    public static String replaceForCygwin(String filename) {
        LogUtil.log("ScheduleBO", "=== old filename: " + filename);
        System.out.println("=== old filename: " + filename);
        while (filename.indexOf("C:") != -1 || filename.indexOf("c:") != -1) {
            filename = filename.replaceAll("C:", "/cygdrive/c");
            filename = filename.replaceAll("c:", "/cygdrive/c");
        }
        while (filename.indexOf("D:") != -1 || filename.indexOf("d:") != -1) {
            filename = filename.replaceAll("D:", "/cygdrive/d");
            filename = filename.replaceAll("d:", "/cygdrive/d");
        }
        filename = filename.replaceAll("\\\\", "/");
        LogUtil.log("ScheduleBO", "=== new filename: " + filename);
        System.out.println("=== new filename: " + filename);
        return filename;
    }

    /**
     * translate byte to hex String
     *
     * @param b
     * @return
     */
    private static String toHexString(byte b) {
        char[] buffer = new char[2];
        buffer[0] = Character.forDigit((b >>> 4) & 0x0F, 16);
        buffer[1] = Character.forDigit(b & 0x0F, 16);
        return new String(buffer);
    }
}
