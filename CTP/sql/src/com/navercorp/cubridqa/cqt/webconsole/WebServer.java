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

import com.navercorp.cubridqa.common.ShareMemory;
import java.io.File;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.webapp.WebAppContext;

public class WebServer {

    public static void main(String[] args) throws Exception {

        int port = Integer.parseInt(args[0]);
        String webRoot = args[1];

        String dailyqaRoot = args[2];
        WebModel.SCENARIO_ROOT = args.length >= 4 ? args[3] : null;
        if (WebModel.SCENARIO_ROOT == null) WebModel.SCENARIO_ROOT = System.getenv("HOME");

        WebAppContext context = new WebAppContext(webRoot, "/");
        context.setAttribute("DAILYQA_ROOT", dailyqaRoot);

        ShareMemory sm = new ShareMemory(webRoot + File.separator + "." + port + ".txt", 100);

        Server server = null;
        try {
            sm.write(Constants.SM_STARTING);
            server = new Server(port);
            server.setHandler(context);
            server.setStopAtShutdown(true);
            server.setSendServerVersion(true);
            server.start();
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
            System.exit(1);
        }
        sm.write(Constants.SM_STARTED);
        new WebServer().new CheckForStop(server, sm).start();
    }

    private class CheckForStop extends Thread {
        private ShareMemory sm;
        private Server server;

        public CheckForStop(Server server, ShareMemory sm) {
            this.server = server;
            this.sm = sm;
        }

        @Override
        public void run() {
            String order = null;
            while (true) {
                try {
                    Thread.sleep(100);
                } catch (Exception e) {
                }

                try {
                    order = sm.read();
                } catch (Exception e) {
                }
                if (order != null && order.equals(Constants.SM_PLEASE_STOP)) {
                    try {
                        server.stop();

                    } catch (Exception e) {
                    }
                    try {
                        sm.write(Constants.SM_STOPPED);
                    } catch (Exception e) {
                    }
                    System.exit(0);
                }
            }
        }
    }
}
