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

import com.navercorp.cubridqa.cqt.common.CommonUtils;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.TreeSet;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

public class SummaryModel {

    String dir;
    String dispName;

    String version;
    String bit;
    String os;

    HashMap<String, String> summaryData = new HashMap<String, String>();
    TreeSet<SummaryItem> itemList;

    public SummaryModel(File dir, boolean brief) throws Exception {
        if (brief) {
            String infoFn = dir.getAbsolutePath() + File.separatorChar + "main.info";
            File infoFile = new File(infoFn);
            if (infoFile.exists() == false) {
                throw new Exception(infoFn + " does not exist");
            }

            this.dispName = dir.getName();
            this.dir = dir.getAbsolutePath();

            ArrayList<String> lineList = CommonUtils.getLineList(infoFn);
            String[] item;
            String key, value;
            for (String line : lineList) {
                item = line.trim().split(":");
                if (item.length >= 2) {
                    key = item[0].trim();
                    value = item[1];
                    summaryData.put(key, value);
                }
            }

            String summayFn = dir.getAbsolutePath() + File.separatorChar + "summary_info";
            String line = Util.readFileOneLine(summayFn, "test_error=");
            if (line != null && line.indexOf("=Y") != -1) {
                summaryData.put("test_error", "Y");
            } else {
                summaryData.put("test_error", "N");
            }
        } else {
            this.dir = dir.getCanonicalPath();
            File f = new File(this.dir);
            dispName = f.getName();

            summaryData = new HashMap<String, String>();
            itemList =
                    new TreeSet<SummaryItem>(
                            new Comparator<SummaryItem>() {
                                @Override
                                public int compare(SummaryItem o1, SummaryItem o2) {
                                    return o1.getCat().compareTo(o2.getCat());
                                }
                            });

            initTargetInfo();
            initSummaryInfo();
        }
    }

    public ArrayList<String> getFailureTestCaseList() throws Exception {
        ArrayList<String> failureTestCaseList = new ArrayList<String>();
        String fileName = dir + File.separator + "summary.info";
        File file = new File(fileName);
        if (file.exists()) {
            failureTestCaseList = initFailList(fileName);
        } else {
            File pfile = file.getParentFile();
            File[] subFiles = Util.getSubDirList(pfile);
            File[] summaryFiles;
            if (pfile.exists()) {
                for (File subFile : subFiles) {
                    summaryFiles = Util.getFileList(subFile, "summary.info");
                    if (summaryFiles == null) continue;
                    for (File summaryFile : summaryFiles) {
                        failureTestCaseList.addAll(initFailList(summaryFile.getAbsolutePath()));
                    }
                }
            }
        }
        return failureTestCaseList;
    }

    public ArrayList<File> searchFailureTestCaseList(File upperFile, ArrayList<File> resultList)
            throws Exception {

        File[] files = upperFile.listFiles();
        for (File f : files) {
            if (f.isDirectory()) {
                searchFailureTestCaseList(f, resultList);
            } else if (f.getAbsolutePath().endsWith(".sql")) {
                resultList.add(f);
            }
        }
        return resultList;
    }

    private ArrayList<String> initFailList(String fileName) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(fileName);
        XPathFactory xPathfactory = XPathFactory.newInstance();
        XPath xpath = xPathfactory.newXPath();
        XPathExpression expr = xpath.compile("//*/nokList/caseresult/caseFile/text()");

        Object result = expr.evaluate(doc, XPathConstants.NODESET);
        NodeList nodes = (NodeList) result;
        ArrayList<String> list = new ArrayList<String>();
        for (int i = 0; i < nodes.getLength(); i++) {
            list.add(nodes.item(i).getNodeValue());
        }
        return list;
    }

    private void initTargetInfo() throws Exception {
        // ArrayList<String> targetList = Util.getLineList(dir + File.separator
        // + "dailyQA_target.info");
        // this.os = targetList.size() >= 6 ? targetList.get(5): "";
        // this.bit = targetList.size() >= 4? targetList.get(3): "";
        // this.version = targetList.size() >= 2 ? targetList.get(1): "";

        String filename = Util.getFirstFileWithRecusive(dir, "summary.info");
        if (filename == null) return;

        String line = Util.readFileOneLine(filename, "<resultDir>");
        if (line == null) return;
        int p1 = line.indexOf("schedule_");
        if (p1 == -1) return;
        int p2 = line.indexOf("/", p1);
        if (p2 == -1) {
            p2 = line.indexOf("<", p1);
        }
        if (p2 == -1) return;
        String value = line.substring(p1, p2);
        String[] s1 = value.split("_");
        this.os = s1[1];
        this.bit = s1[3];
        this.version = s1[s1.length - 2] + "." + s1[s1.length - 1];
    }

    private void initSummaryInfo() throws Exception {
        File file = new File(dir + File.separator + "summary_info");

        ArrayList<String> summaryList = null;
        if (file.exists()) {
            summaryList = Util.getLineList(file.getAbsolutePath());
        } else {
            File[] subFiles = Util.getSubDirList(dir);
            for (File f : subFiles) {
                if (summaryList == null) {
                    summaryList =
                            Util.getLineList(f.getAbsolutePath() + File.separator + "summary_info");
                } else {
                    summaryList =
                            mergeList(
                                    summaryList,
                                    Util.getLineList(
                                            f.getAbsolutePath() + File.separator + "summary_info"));
                }
            }
        }

        if (summaryList == null || summaryList.size() == 0) return;

        int firstColon;
        String key, value;
        boolean isSummaryItem = false;
        SummaryItem item;

        String[] s1;
        int i1;
        boolean isDir;

        for (String line : summaryList) {
            firstColon = line.lastIndexOf(":");
            if (firstColon == -1) continue;
            key = line.substring(0, firstColon).trim();
            if (key.equals("msg_id")) {
                continue;
            }

            value = line.substring(firstColon + 1).trim();

            if (isSummaryItem) {
                isDir = key.trim().endsWith(".sql") == false;
                item = new SummaryItem();
                item.setCat(key);
                item.setDir(isDir);

                s1 = value.split(" ");
                i1 = 0;
                if (isDir) {
                    for (String s : s1) {
                        if (s.trim().equals("")) {
                            continue;
                        }
                        i1++;
                        if (i1 == 1) {
                            item.setTotal(Integer.parseInt(s));
                        } else if (i1 == 2) {
                            item.setSucc(Integer.parseInt(s));
                        } else if (i1 == 3) {
                            item.setFail(Integer.parseInt(s));
                        }
                    }
                } else {
                    for (String s : s1) {
                        if (s.trim().equals("")) {
                            continue;
                        }
                        i1++;
                        if (i1 == 1) {
                            item.setFlag(s);
                        } else if (i1 == 2) {
                            item.setElapse(s);
                        }
                    }
                }

                itemList.add(item);
                continue;
            }
            if (key.equals("SiteRunTimes")) {
                isSummaryItem = true;
            }

            summaryData.put(key, value);
        }
    }

    public String getDir() {
        return dir;
    }

    public void setDir(String dir) {
        this.dir = dir;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getBit() {
        return bit;
    }

    public void setBit(String bit) {
        this.bit = bit;
    }

    public String getOs() {
        return os;
    }

    public void setOs(String os) {
        this.os = os;
    }

    public String getDispName() {
        return dispName;
    }

    public String getMoreData(String key) {
        String v = this.summaryData.get(key);
        return v == null ? "" : v;
    }

    public SummaryItem[] getItemList() {
        SummaryItem[] list = new SummaryItem[this.itemList.size()];
        this.itemList.toArray(list);
        return list;
    }

    private static ArrayList<String> mergeList(ArrayList<String> a1, ArrayList<String> a2) {

        if (a1 == null || a1.size() == 0) return a2;
        if (a2 == null || a2.size() == 0) return a1;

        ArrayList<String> list1 = new ArrayList<String>();
        ArrayList<String> list2 = new ArrayList<String>();
        list1.addAll(a1);
        list2.addAll(a2);

        String line1, line2, newLine;

        for (int i = 0; i < 5; i++) {
            line1 = list1.get(i);
            line2 = list2.get(i);
            newLine = mergeLine(line1, line2);
            if (newLine != null) {
                list1.set(i, newLine);
            }
        }
        list1.addAll(list2.subList(5, list2.size()));

        return list1;
    }

    private static String mergeLine(String s1, String s2) {
        int firstColon1 = s1.indexOf(":");
        String key1 = s1.substring(0, firstColon1).trim();
        String value1 = s1.substring(firstColon1 + 1).trim();

        int firstColon2 = s2.indexOf(":");
        String key2 = s2.substring(0, firstColon2).trim();
        String value2 = s2.substring(firstColon2 + 1).trim();
        if (key1.equals(key2)) {
            if (",total,success,fail,".indexOf("," + key1 + ",") != -1) {
                return key1 + ":" + (Integer.parseInt(value1) + Integer.parseInt(value2));
            } else if ("totalTime".equals(key1)) {
                value1 = Util.replace(value1, "ms", "");
                value2 = Util.replace(value2, "ms", "");
                return key1 + ":" + (Integer.parseInt(value1) + Integer.parseInt(value2)) + "ms";
            } else if ("SiteRunTimes".equals(key1)) {
                return s1;
            }
        }
        return null;
    }

    public String getComment() throws IOException {
        String filename1 = this.dir + File.separator + "NOTE";
        if (Util.isExist(filename1)) {
            return Util.readFile(filename1);
        }
        return "";
    }
}
