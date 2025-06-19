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

import com.navercorp.cubridqa.cqt.console.util.TestUtil;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.Reader;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Properties;

public class CommonUtils {

    public static Properties getConfig(String configFile) {
        FileInputStream fis = null;

        try {
            fis = new FileInputStream(configFile);
            InputStream is = new BufferedInputStream(fis);
            Properties props = new Properties();
            props.load(is);
            return props;
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            if (fis != null) {
                try {
                    fis.close();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    public static boolean containPath(String p1, String p2) {
        boolean res = false;
        if (p1 == null || p1.length() == 0) return res;
        if (p2 == null || p2.length() == 0) return res;

        p1 = p1.trim().replace('\\', '/');
        p2 = p2.trim().replace('\\', '/');

        p2 = (p2.endsWith("/") || p2.endsWith(TestUtil.ScenarioTypes)) ? p2 : p2 + "/";

        if (p1.indexOf(p2) >= 0) {
            res = true;
        }

        return res;
    }

    public static String concatFile(String p1, String p2) {
        String p;
        if (p1 == null) p1 = "";
        if (p2 == null) p2 = "";

        p1 = p1.trim().replace('\\', '/');
        p2 = p2.trim().replace('\\', '/');
        p = p1 + "/" + p2;
        String t;
        while (true) {
            t = replace(p, "//", "/");
            if (p.equals(t)) {
                break;
            } else {
                p = t;
            }
        }
        return p.replace('/', File.separatorChar);
    }

    public static String replace(String strSource, String strFrom, String strTo) {
        if (strFrom == null || strFrom.equals("")) return strSource;

        String strDest = "";
        int intFromLen = strFrom.length();
        int intPos;
        while ((intPos = strSource.indexOf(strFrom)) != -1) {
            strDest = strDest + strSource.substring(0, intPos);
            strDest = strDest + strTo;
            strSource = strSource.substring(intPos + intFromLen);
        }
        strDest = strDest + strSource;
        return strDest;
    }

    public static Timestamp getCurrentTimestamp() {
        return new Timestamp(System.currentTimeMillis());
    }

    public static Reader readFile(String file) {
        try {
            return new FileReader(file);
        } catch (FileNotFoundException e) {
            return null;
        }
    }

    public static int getRadomNum(int max) {
        return (int) Math.floor(Math.random() * max);
    }

    public static String getCurrentTimeStamp(String format) {
        SimpleDateFormat f = new SimpleDateFormat(format);
        String timeStamp = f.format(new Date());
        return timeStamp;
    }

    public static ArrayList<String> getLineList(String filename) throws IOException {
        if (filename == null) return null;
        File file = new File(filename);
        if (!file.exists()) {
            return null;
        }
        ArrayList<String> resultList = new ArrayList<String>();
        FileInputStream fis = new FileInputStream(file);
        InputStreamReader reader = new InputStreamReader(fis, "UTF-8");

        LineNumberReader lineReader = new LineNumberReader(reader);
        String line;

        while ((line = lineReader.readLine()) != null) {
            if (line.trim().equals("")) continue;
            resultList.add(line.trim());
        }
        lineReader.close();
        reader.close();
        fis.close();
        return resultList;
    }
}
