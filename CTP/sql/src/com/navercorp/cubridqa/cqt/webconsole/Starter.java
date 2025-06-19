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

import com.navercorp.cubridqa.common.CommonUtils;
import com.navercorp.cubridqa.common.IniData;
import com.navercorp.cubridqa.common.ShareMemory;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetAddress;

public class Starter {

    private static final String ctpHome = CommonUtils.getEnvInFile("CTP_HOME");

    public static void exec(String webconsoleConf, String webRoot, String op) throws Exception {
        System.out.println("Config: " + webconsoleConf);
        System.out.println("Web Root: " + webRoot);
        op = op.toUpperCase();

        String separator = System.getProperty("file.separator");

        IniData ini = new IniData(webconsoleConf);

        int port = 8888;
        try {
            port = Integer.parseInt(ini.get(null, "web_port"));
        } catch (Exception e) {
            System.out.println(
                    "Not found valid port value in parameter web_port. Web Console will adopt default port "
                            + port
                            + ".");
        }
        String resultRoot = ini.getAndTrans(null, "sql_result_root");
        if (resultRoot == null
                || resultRoot.trim().equals("")
                || new File(resultRoot).exists() == false) {
            resultRoot = ctpHome + File.separator + "sql" + File.separator + "result";
            System.out.println(
                    "Not found valid test result folder value in parameter sql_result_root. Web Console will adopt default value "
                            + resultRoot
                            + ".");
        }

        String scenarioRoot = ini.getAndTrans(null, "scenario_root");
        if (scenarioRoot == null
                || scenarioRoot.trim().equals("")
                || new File(scenarioRoot).exists() == false) {
            scenarioRoot = "/";
        }

        String[] cmd = new String[8];
        cmd[0] = System.getProperty("java.home") + separator + "bin" + separator + "java";
        ;
        cmd[1] = "-cp";
        cmd[2] =
                System.getProperty("java.class.path")
                        + File.pathSeparator
                        + webRoot
                        + "/../lib/cubridqa-cqt.jar";
        cmd[3] = WebServer.class.getCanonicalName();
        cmd[4] = String.valueOf(port);
        cmd[5] = webRoot;
        cmd[6] = resultRoot;
        cmd[7] = scenarioRoot;

        ShareMemory sm = new ShareMemory(webRoot + File.separator + "." + port + ".txt", 100);
        boolean succ = true;

        if (op.equals("START")) {
            sm.write(Constants.SM_PLEASE_STOP);
            Thread.sleep(2000);

            System.out.println("Begin to start ...");
            ProcessBuilder process = new ProcessBuilder(cmd);
            process.redirectErrorStream(true);
            Process p = process.start();

            StreamGobbler errorGobbler =
                    new Starter().new StreamGobbler(p.getErrorStream(), "ERROR", false);
            StreamGobbler outputGobbler =
                    new Starter().new StreamGobbler(p.getInputStream(), "OUTPUT", false);
            outputGobbler.start();
            errorGobbler.start();

            succ = sm.wait(Constants.SM_STARTED, 1000);
            System.out.println();
            System.out.println(succ ? "Done" : "Fail");

            if (succ) {
                System.out.println(
                        "URL:  http://" + InetAddress.getLocalHost().getHostAddress() + ":" + port);
            }
        } else if (op.equals("STOP")) {
            sm.write(Constants.SM_PLEASE_STOP);
            succ = sm.wait(Constants.SM_STOPPED, 1000);
            System.out.println();
            System.out.println(succ ? "Done" : "Fail");
        }

        System.exit(succ ? 0 : 1);
    }

    class StreamGobbler extends Thread {
        InputStream is;
        String type;
        boolean echo;

        public StreamGobbler(InputStream is, String type, boolean echo) {
            this.is = is;
            this.type = type;
            this.echo = echo;
        }

        @Override
        public void run() {
            try {
                InputStreamReader isr = new InputStreamReader(is);
                BufferedReader br = new BufferedReader(isr);
                String line = null;
                while ((line = br.readLine()) != null) {
                    if (echo) System.out.println(type + "> " + line);
                }
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
        }
    }
}
