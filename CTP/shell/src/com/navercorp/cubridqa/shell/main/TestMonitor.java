/**
 * Copyright (c) 2016, Search Solution Corporation. All rights reserved.

 * 
 * Redistribution and use in source and binary forms, with or without 
 * modification, are permitted provided that the following conditions are met:
 * 
 *   * Redistributions of source code must retain the above copyright notice, 
 *     this list of conditions and the following disclaimer.
 * 
 *   * Redistributions in binary form must reproduce the above copyright 
 *     notice, this list of conditions and the following disclaimer in 
 *     the documentation and/or other materials provided with the distribution.
 * 
 *   * Neither the name of the copyright holder nor the names of its contributors may be used to endorse or promote products 
 *     derived from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, 
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE 
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, 
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR 
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE 
 * USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE. 
 */

package com.navercorp.cubridqa.shell.main;

import java.util.ArrayList;

import com.navercorp.cubridqa.shell.common.CommonUtils;
import com.navercorp.cubridqa.shell.common.Constants;
import com.navercorp.cubridqa.shell.common.Log;
import com.navercorp.cubridqa.shell.common.SSHConnect;
import com.navercorp.cubridqa.shell.common.ShellScriptInput;

public class TestMonitor {

	Test test;
	SSHConnect ssh;
	Context context;
	int testCaseTimeout;
	Log log;
	Log logRelated;
	boolean enableTracing = false;
	ArrayList<SSHConnect> sshRelateds = null;
	ShellScriptInput traceScript;

	public TestMonitor(Context context, Test test) throws Exception {
		this.context = context;
		this.test = test;

		this.log = new Log(CommonUtils.concatFile(context.getCurrentLogDir(), "monitor_" + test.getCurrentEnvId() + ".log"), false, context.isContinueMode);

		this.ssh = ShellHelper.createTestNodeConnect(context, test.getCurrentEnvId());
		this.initRelatedSSH();

		try {
			testCaseTimeout = Integer.parseInt(context.getTestCaseTimeout());
		} catch (Exception e) {
			testCaseTimeout = -1;
		}

		try {
			enableTracing = context.needEnableMonitorTrace();
			traceScript = initTraceScript();
		} catch (Exception e) {
			enableTracing = false;
		}

		if (enableTracing && sshRelateds != null && sshRelateds.size() > 0) {
			this.logRelated = new Log(CommonUtils.concatFile(context.getCurrentLogDir(), "monitor_" + test.getCurrentEnvId() + "_related.log"), false, context.isContinueMode);
		}
	}

	private void initRelatedSSH() {
		if (this.sshRelateds == null) {
			this.sshRelateds = new ArrayList<SSHConnect>();
		} else {
			for (SSHConnect s : this.sshRelateds) {
				try {
					s.close();
				} catch (Exception e) {
					// no need concern
				}
			}
			this.sshRelateds.clear();
		}

		ArrayList<String> relatedHosts = context.getRelatedHosts(test.getCurrentEnvId());
		if (relatedHosts != null && relatedHosts.size() > 0) {
			SSHConnect s;
			for (String host : relatedHosts) {
				try {
					s = ShellHelper.createTestNodeConnect(context, test.getCurrentEnvId(), host);
					this.sshRelateds.add(s);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
	}

	public void startMonitor() {
		while (test.isStopped() == false) {
			monitorOnce();
			if (enableTracing) {
				collectOnce();
			}
			CommonUtils.sleep(3);
		}
	}

	private void monitorOnce() {

		try {
			resolveTimeout();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void collectOnce() {
		try {
			collectRuntimeData();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private ShellScriptInput initTraceScript() {
		ShellScriptInput scripts = new ShellScriptInput();

		scripts.addCommand("date");

		scripts.addCommand("echo --------------MEMORY ---------------");
		scripts.addCommand("free");

		scripts.addCommand("echo --------------DISK SPACE ---------------");
		scripts.addCommand("df");

		scripts.addCommand("echo --------------ALL PROCESS ---------------");
		if (context.isWindows) {
			scripts.addCommand("tasklist /V");
		} else {
			scripts.addCommand("ps -ef");
		}

		scripts.addCommand("echo --------------USER PROCESS ---------------");
		if (context.isWindows) {
			scripts.addCommand("ps -W");
		} else {
			scripts.addCommand("ps -u $USER -f");
		}

		scripts.addCommand("echo --------------CUBRID/log/* ---------------");
		scripts.addCommand("cat $CUBRID/log/*");

		scripts.addCommand("echo --------------CUBRID/log/server ---------------");
		scripts.addCommand("cat $CUBRID/log/server/*");

		scripts.addCommand("echo --------------CUBRID/databases ---------------");
		scripts.addCommand("cat $CUBRID/databases/databases*");

		scripts.addCommand("echo --------------CUBRID/conf ---------------");
		scripts.addCommand("diff $CUBRID/../.CUBRID_SHELL_FM/conf $CUBRID/conf");

		if (context.isWindows) {
			scripts.addCommand("echo --------------Windows/System32/cub* ---------------");
			scripts.addCommand("cat /c/windows/system32/cub*");
		}

		scripts.addCommand("echo --------------NETSTAT---------------");
		if (context.isWindows) {
			scripts.addCommand("netstat -a -b -f -n -o");
		} else {
			scripts.addCommand("netstat -n -e -p -a");
		}
		return scripts;

	}

	private void collectRuntimeData() {

		try {
			String result = this.ssh.execute(this.traceScript);
			log.println("[COLLECT]" + test.testCaseFullName + " (" + new java.util.Date() + ")");
			log.println(result);

			if (this.sshRelateds != null) {
				for (SSHConnect s : sshRelateds) {
					result = s.execute(this.traceScript);
					logRelated.println("[COLLECT-" + s.getHost() + "]" + test.testCaseFullName + " (" + new java.util.Date() + ")");
					logRelated.println(result);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void resolveTimeout() {

		synchronized (test) {
			if (testCaseTimeout < 0 || test.startTime <= 0)
				return;

			long endTime = System.currentTimeMillis();

			if (endTime - test.startTime < testCaseTimeout * 1000) {
				return;
			}

			long elapse_time = (endTime - test.startTime) / 1000;

			String result = CommonUtils.resetProcess(ssh, context.isWindows, context.isExecuteAtLocal());

			test.testCaseSuccess = false;
			test.addResultItem("NOK", "timeout");
			test.isTimeOut = true;
			if (elapse_time > 120 && context.isWindows()) {
				this.log.println("Try to restart remote aganet to resovle timeout problem.");
				try {
					ssh.restartRemoteAgent();
					this.log.println("Restart done");
				} catch (Exception e) {
					this.log.println("Restart fail: " + e.getMessage());
				}
			}
			context.getFeedback().onTestCaseMonitor(test.testCaseFullName,
					"[RESOLVE] " + testCaseTimeout + " + timeout (actual: " + elapse_time + " seconds)" + Constants.LINE_SEPARATOR + "CLEAN PROCESSES: " + Constants.LINE_SEPARATOR + result,
					test.envIdentify);
		}
	}

	public void close() {
		this.ssh.close();
		this.log.close();
		/*
		 * If enableTracing is false and not ha test, logRelated will be null
		 */
		if (this.logRelated != null) {
			this.logRelated.close();
		}

		if (this.sshRelateds != null) {
			for (SSHConnect s : sshRelateds) {
				try {
					s.close();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
	}
}
