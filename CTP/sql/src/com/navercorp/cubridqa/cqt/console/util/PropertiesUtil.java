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

import com.navercorp.cubridqa.cqt.console.bean.Test;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;

public class PropertiesUtil {

    private static String propertiesFilePath =
            EnvGetter.getenv("CTP_HOME")
                    + File.separator
                    + TestUtil.CONFIG_NAME
                    + File.separator
                    + "local.properties";

    public static String getValue(String key) {
        if (propertiesFilePath != null
                && new File(propertiesFilePath) != null
                && new File(propertiesFilePath).exists()) {
            return getValue(key, propertiesFilePath);
        } else {
            return "";
        }
    }

    public static String getValueWithDefault(String key, String defaultValue) {
        String value = getValue(key);
        return value == null ? defaultValue : value;
    }

    public static void setValue(String key, String value) {
        if (propertiesFilePath != null
                && new File(propertiesFilePath) != null
                && new File(propertiesFilePath).exists()) {
            setValue(key, value, propertiesFilePath);
        }
    }

    public static void initConfig(String configFile, Test test) throws Exception {
        SAXReader reader = new SAXReader();
        String val = "";
        String script = "";
        if (configFile == null || test == null) {
            throw new Exception("Test configuration file is null or Test object creation fail!");
        }

        Document document = reader.read(configFile);
        Element root = document.getRootElement();
        List rmd = root.selectNodes(TestUtil.ROOT_NODE + TestUtil.RUN_MODE);
        if (!rmd.isEmpty()) {
            Element run_mode = (Element) rmd.get(0);
            val = run_mode.getText();
            test.setRun_mode(val);
        }

        List rmdSuffix = root.selectNodes(TestUtil.ROOT_NODE + TestUtil.RUN_MODE_SECONDARY);
        if (!rmdSuffix.isEmpty()) {
            Element run_mode_suffix = (Element) rmdSuffix.get(0);
            val = run_mode_suffix.getText();
            test.setRunModeSecondary(val);
        }

        List ac = root.selectNodes(TestUtil.ROOT_NODE + TestUtil.AUTOCOMMIT);
        if (!ac.isEmpty()) {
            Element autocommit = (Element) ac.get(0);
            val = autocommit.getText();
            test.setAutocommit(val);
        }

        List hc = root.selectNodes(TestUtil.ROOT_NODE + TestUtil.HOLDCAS);
        if (!hc.isEmpty()) {
            Element autocommit = (Element) hc.get(0);
            val = autocommit.getText();
            test.setHoldcas(val);
        }

        List sol = root.selectNodes(TestUtil.ROOT_NODE + TestUtil.SERVER_OUTPUT);
        if (!sol.isEmpty()) {
            Element so = (Element) sol.get(0);
            val = so.getText();
            test.setServerOutput(val);
        }

        List checkAlive = root.selectNodes(TestUtil.ROOT_NODE + TestUtil.CHECK_SERVER_STATUS);
        if (!checkAlive.isEmpty()) {
            Element serverStatus = (Element) checkAlive.get(0);
            val = serverStatus.getText();
            test.setNeedCheckServerStatus(Boolean.parseBoolean(val));
        }

        List urlProperties = root.selectNodes(TestUtil.ROOT_NODE + TestUtil.URL_PROPERTIES);
        if (!urlProperties.isEmpty()) {
            Element urlProp = (Element) urlProperties.get(0);
            val = urlProp.getText();
            test.setUrlProperties(val);
        }

        List needXmlSummary = root.selectNodes(TestUtil.ROOT_NODE + TestUtil.NEED_XML_SUMMARY);
        if (!needXmlSummary.isEmpty()) {
            Element needXml = (Element) needXmlSummary.get(0);
            val = needXml.getText();
            test.setNeedSummaryXML(Boolean.parseBoolean(val));
        }

        List needAnswerInSummary =
                root.selectNodes(TestUtil.ROOT_NODE + TestUtil.NEED_ANSWER_In_Summary);
        if (!needAnswerInSummary.isEmpty()) {
            Element needAnswer = (Element) needAnswerInSummary.get(0);
            val = needAnswer.getText();
            test.setNeedAnswerInSummary(Boolean.parseBoolean(val));
        }

        List addHint = root.selectNodes(TestUtil.ROOT_NODE + TestUtil.ADD_DEBUG_HINT);
        if (!addHint.isEmpty()) {
            Element serverStatus = (Element) addHint.get(0);
            val = serverStatus.getText();
            test.setNeedDebugHint(Boolean.parseBoolean(val));
        }

        List rt = root.selectNodes(TestUtil.ROOT_NODE + TestUtil.RESET);
        if (!rt.isEmpty()) {
            Element el = (Element) rt.get(0);
            List nodes = el.elements();
            for (Iterator iters = nodes.iterator(); iters.hasNext(); ) {
                Element item = (Element) iters.next();
                script +=
                        item.getTextTrim()
                                + TestUtil.SQL_END
                                + System.getProperty("line.separator");
            }
        }
        test.setReset_scripts(script);
    }

    /**
     * get the value from property file .
     *
     * @param key
     * @param propertiesPath
     * @return
     */
    public static String getValue(String key, String propertiesPath) {
        Properties p = new Properties();
        InputStream is = null;
        try {
            is = new FileInputStream(new File(propertiesPath));
            p.load(is);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        } finally {
            try {
                is.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (p.containsKey(key)) {
            String value = p.getProperty(key);
            if (value != null) {
                return value.replaceAll("\\:", ":").trim();
            } else {
                return value;
            }
        }
        return null;
    }

    /**
     * add the key value map to the property file
     *
     * @param key
     * @param value
     * @param propertiesPath
     */
    public static void setValue(String key, String value, String propertiesPath) {
        if (!new File(propertiesPath).exists()) {
            try {
                new File(propertiesPath).createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        Properties p = new Properties();
        InputStream is = null;
        FileOutputStream os = null;
        try {
            is = new FileInputStream(new File(propertiesPath));
            p.load(is);
            p.setProperty(key, value.replaceAll("\\\\:", ":"));
            os = new FileOutputStream(propertiesPath);
            p.store(os, "");
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                is.close();
                os.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * remove the property load from a file and save it to the file.
     *
     * @param propertiesPath
     * @param key
     */
    public static void removeProperty(String propertiesPath, String key) {
        Properties p = new Properties();
        InputStream is = null;
        FileOutputStream os = null;
        try {
            is = new FileInputStream(new File(propertiesPath));
            p.load(is);
            if (!p.containsKey(key)) {
                return;
            }
            p.remove(key);
            os = new FileOutputStream(propertiesPath);
            p.store(os, "");
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                is.close();
                os.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * load the qa tools configuration file .
     *
     * @return
     */
    public static Map<String, String> loadProperties() {
        HashMap<String, String> ret = new HashMap<String, String>();
        InputStream is = null;
        try {
            is = new FileInputStream(new File(propertiesFilePath));
            Properties p = new Properties();
            p.load(is);

            Iterator iter = p.keySet().iterator();
            while (iter.hasNext()) {
                String key = (String) iter.next();
                String value = p.getProperty(key);
                if (value != null) {
                    value = value.replaceAll("\\:", ":");
                    ret.put(key, value);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        } finally {
            try {
                is.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return ret;
    }
}
