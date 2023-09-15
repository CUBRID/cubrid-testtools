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
package com.navercorp.cubridqa.cqt.webconsole;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;

public class Util {

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

    public static ArrayList<String> getLineList(String filename1) throws IOException {

        File f = new File(filename1);
        if (!f.exists()) return new ArrayList<String>();

        FileInputStream fis = null;
        InputStreamReader fsr = null;
        BufferedReader reader1 = null;
        ArrayList<String> list = new ArrayList<String>();
        try {
            fis = new FileInputStream(filename1);
            fsr = new InputStreamReader(fis, "UTF-8");
            reader1 = new BufferedReader(fsr);
            String line;

            while ((line = reader1.readLine()) != null) {
                list.add(line);
            }
        } finally {
            try {
                if (reader1 != null) reader1.close();
            } catch (Exception e) {
                e.printStackTrace();
            }

            try {
                if (fsr != null) fsr.close();
            } catch (Exception e) {
                e.printStackTrace();
            }

            try {
                if (fis != null) fis.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return list;
    }

    public static void writeFile(String filename, String content) throws IOException {
        File file = new File(filename);
        if (!file.exists()) {
            file.createNewFile();
        }
        FileOutputStream fos = new FileOutputStream(file);
        OutputStreamWriter writer = null;

        try {
            writer = new OutputStreamWriter(fos, "UTF-8");
            writer.write(content);
            writer.flush();
        } finally {
            if (writer != null) writer.close();
        }
    }

    public static String readFile(String filename1) throws IOException {

        File f = new File(filename1);
        if (!f.exists()) return null;

        FileInputStream fis = null;
        InputStreamReader fsr = null;

        StringBuilder out = new StringBuilder();

        int len;
        char[] buffer = new char[1024];

        try {
            fis = new FileInputStream(filename1);
            fsr = new InputStreamReader(fis, "UTF-8");

            while (true) {
                len = fsr.read(buffer);
                if (len == -1) break;
                out.append(buffer, 0, len);
            }
        } finally {

            try {
                if (fsr != null) fsr.close();
            } catch (Exception e) {
                e.printStackTrace();
            }

            try {
                if (fis != null) fis.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return out.toString();
    }

    public static String readFileOneLine(String filename, String keyInLine) throws Exception {
        File f = new File(filename);
        if (!f.exists()) return null;

        FileInputStream fis = null;
        InputStreamReader fsr = null;
        BufferedReader reader1 = null;

        String line;
        try {
            fis = new FileInputStream(filename);
            fsr = new InputStreamReader(fis, "UTF-8");
            reader1 = new BufferedReader(fsr);

            while ((line = reader1.readLine()) != null) {
                if (line.indexOf(keyInLine) != -1) {
                    break;
                }
            }
        } finally {
            try {
                if (reader1 != null) reader1.close();
            } catch (Exception e) {
                e.printStackTrace();
            }

            try {
                if (fsr != null) fsr.close();
            } catch (Exception e) {
                e.printStackTrace();
            }

            try {
                if (fis != null) fis.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return line;
    }

    public static String getFirstFileWithRecusive(String dirName, String fileName) {
        File file = new File(dirName + File.separator + fileName);
        if (file.exists()) return file.getAbsolutePath();
        if (!file.getParentFile().exists()) return null;

        File[] subList =
                file.getParentFile()
                        .listFiles(
                                new FilenameFilter() {

                                    public boolean accept(File dir, String name) {
                                        File f = new File(dir + File.separator + name);
                                        return f.isDirectory();
                                    }
                                });

        String result;
        for (File f : subList) {
            result = getFirstFileWithRecusive(f.getAbsolutePath(), fileName);
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    public static File[] getSubDirList(String dirFilename) {
        return getSubDirList(new File(dirFilename));
    }

    public static File[] getSubDirList(File dirFile) {

        File[] subFiles =
                dirFile.listFiles(
                        new FilenameFilter() {
                            @Override
                            public boolean accept(File dir, String name) {
                                File f = new File(dir + File.separator + name);
                                return f.isDirectory();
                            }
                        });
        return subFiles;
    }

    public static File[] getFileList(File dirFile, final String filenamePrefix) {

        File[] subFiles =
                dirFile.listFiles(
                        new FilenameFilter() {
                            @Override
                            public boolean accept(File dir, String name) {
                                File f = new File(dir + File.separator + name);
                                return f.isFile() && name.startsWith(filenamePrefix);
                            }
                        });
        return subFiles;
    }

    public static boolean isExist(String filename) {
        File file = new File(filename);
        return file.exists();
    }

    public static File searchFile(String scenarioRoot, String testPath) {
        File file = new File(testPath);
        if (file.exists()) return file;
        testPath = replace(testPath, "\\", "/");
        String[] arr = testPath.split("/");
        String subFilename = null;

        file = null;
        for (int i = arr.length - 1; i >= 0; i--) {
            subFilename = subFilename == null ? arr[i] : arr[i] + File.separator + subFilename;

            file = new File(scenarioRoot + File.separator + subFilename);
            // System.out.println("subFilename:" + file.getAbsolutePath());
            if (file.exists()) return file;
        }
        return null;
    }
}
