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

package com.navercorp.cubridqa.isolation;

import java.util.ArrayList;

import java.util.Properties;

import com.jcraft.jsch.JSchException;
import com.navercorp.cubridqa.isolation.dispatch.Dispatch;
import com.navercorp.cubridqa.common.CommonUtils;
import com.navercorp.cubridqa.common.Log;
import com.navercorp.cubridqa.shell.common.SSHConnect;

public class Test {

	Context context;
	String currEnvId;

	String testCaseFullName, testCaseDir;

	SSHConnect ssh;
	Log dispatchLog;
	Log workerLog;

	boolean testCaseSuccess;
	boolean isTimeOut = false;
	boolean hasCore = false;

	boolean shouldStop = false;
	boolean isStopped = false;

	long startTime = 0;

	ArrayList<String> resultItemList = new ArrayList<String>();
	String envIdentify;

	public Test(Context context, String currEnvId) throws JSchException {
		this.shouldStop = false;
		this.isStopped = false;
		this.isTimeOut = false;
		this.hasCore = false;
		this.context = context;
		this.currEnvId = currEnvId;
		this.startTime = 0;
		this.dispatchLog = new Log(CommonUtils.concatFile(context.getCurrentLogDir(), "dispatch_tc_FIN_" + currEnvId + ".txt"), false, context.isContinueMode());
		this.workerLog = new Log(CommonUtils.concatFile(context.getCurrentLogDir(), "test_" + currEnvId + ".log"), false, true);

		String host = context.getInstanceProperty(currEnvId, "ssh.host");
		String port = context.getInstanceProperty(currEnvId, "ssh.port");
		String user = context.getInstanceProperty(currEnvId, "ssh.user");
		envIdentify = "EnvId=" + currEnvId + "[" + user + "@" + host + ":" + port + "]";

		resetSSH();
	}

	public void runAll() throws JSchException {

		System.out.println("[ENV START] " + currEnvId);

		String testCase;
		Long endTime;
		int p;
		while (!shouldStop && !Dispatch.getInstance().isFinished()) {
			testCase = Dispatch.getInstance().nextTestFile();
			if (testCase == null) {
				break;
			}

			this.testCaseFullName = testCase;
			p = testCase.lastIndexOf("/");
			if (p == -1) {
				p = testCase.lastIndexOf("\\");
			}
			if (p == -1) {
				this.testCaseDir = ".";
			} else {
				this.testCaseDir = testCase.substring(0, p);
			}

			startTime = System.currentTimeMillis();
			context.getFeedback().onTestCaseStartEvent(this.testCaseFullName, envIdentify);

			workerLog.println("[TESTCASE] " + this.testCaseFullName);

			resetSSH();
			resultItemList.clear();
			this.testCaseSuccess = true;
			this.isTimeOut = false;
			this.hasCore = false;
			try {
				testCaseSuccess = runTestCase();
			} catch (Exception e) {
				this.testCaseSuccess = false;
				e.printStackTrace();
				this.addResultItem("NOK", "Runtime error (" + e.getMessage() + ")");
			} finally {
				endTime = System.currentTimeMillis();

				StringBuffer resultCont = new StringBuffer();
				for (String item : this.resultItemList) {
					workerLog.println(item);
					resultCont.append(item).append(Constants.LINE_SEPARATOR);
				}

				if (testCaseSuccess == false) {
					String cont = showDifferenceBetweenAnswerAndResult(this.testCaseFullName);
					resultCont.append("=================================================================== D I F F ===================================================================")
							.append(Constants.LINE_SEPARATOR);
					resultCont.append(cont).append(Constants.LINE_SEPARATOR);
				}
				context.getFeedback().onTestCaseStopEvent(this.testCaseFullName, testCaseSuccess, endTime - startTime, resultCont.toString(), envIdentify, isTimeOut, hasCore, Constants.SKIP_TYPE_NO);
				System.out.println("[TESTCASE] " + this.testCaseFullName + " EnvId=" + this.currEnvId + " " + (testCaseSuccess ? "[OK]" : "[NOK]"));
				workerLog.println("");
				dispatchLog.println(this.testCaseFullName);
			}
		}

		isStopped = true;
		close();
		context.getFeedback().onStopEnvEvent(currEnvId);
		System.out.println("[ENV STOP] " + currEnvId);
	}

	private String showDifferenceBetweenAnswerAndResult(String testCaseFullName) {
		testCaseFullName = CommonUtils.replace(testCaseFullName, "\\", "/");
		int p = testCaseFullName.lastIndexOf("/");
		String d = testCaseFullName.substring(0, p);
		String n = CommonUtils.replace(testCaseFullName.substring(p + 1), ".ctl", "");
		String answerFilename = d + "/answer/" + n + ".answer";
		String resultFilename = d + "/result/" + n + ".log";

		IsolationShellInput script;
		String result;

		script = new IsolationShellInput("cd ");
		script.addCommand("touch " + resultFilename);
		script.addCommand("mkdir -p " + d + "/result/");
		script.addCommand("diff -a -y -W 185 " + answerFilename + " " + resultFilename);
		try {
			result = ssh.execute(script);
		} catch (Exception e) {
			result = "DIFF ERROR: " + e.getMessage();
		}
		if (result != null && result.indexOf("\r\n") == -1) {
			result = CommonUtils.replace(result, "\n", "\r\n");
		}
		return result;
	}

	public boolean runTestCase() throws Exception {

		String result;
		IsolationShellInput script = new IsolationShellInput("");
		script.addCommand("ulimit -c unlimited");
		script.addCommand("export TEST_ID=" + this.context.getFeedback().getTaskId());
		script.addCommand("cd $ctlpath");
		String tc = testCaseFullName.trim();
		if (tc.startsWith("/") == false) {
			tc = "$HOME/" + tc;
		}
		script.addCommand("sh runone.sh -r " + (context.getRetryTimes() + 1) + " " + tc + " " + context.getProperty("main.testcase.timeout") + " " + context.getTestingDatabase() + " 2>&1");
		result = ssh.execute(script);
		workerLog.println(result);

		processCoreFile();

		String[] itemArrary = result.split("\n");
		if (itemArrary == null) {
			return false;
		} else {
			Properties props = new Properties();
			String[] pair;
			for (String item : itemArrary) {
				pair = item.split(":");
				if (pair.length == 2) {
					props.put(pair[0].trim().toLowerCase(), pair[1].trim().toUpperCase());
				}
			}
			String core = props.getProperty("core", "").trim();
			if (core.equals("") == false) {
				this.hasCore = true;
				this.addResultItem("NOK", "FOUND CORE(S): " + core);
			}

			String flag = props.getProperty("flag", "").trim();
			if (flag.equals("TIMEOUT")) {
				this.addResultItem("NOK", "TIMEOUT");
				isTimeOut = true;
				return false;
			} else {
				return (!hasCore) && flag.equals("OK");
			}
		}
	}

	private void processCoreFile() throws Exception {
		String result;

		String findCmd = "find $CUBRID ${CTP_HOME} ${init_path} " + this.testCaseDir + " -name 'core.*'";

		IsolationShellInput script;
		script = new IsolationShellInput("cd; " + findCmd + "| grep -v 'core.log'");

		result = ssh.execute(script);
		if (result != null && result.trim().equals("") == false) {
			this.hasCore = true;
			result = CommonUtils.replace(result.trim(), "\n", ", ");
			result = CommonUtils.replace(result, "\r", ", ");
			this.addResultItem("NOK", "FOUND CORE(S): " + result);

			script = new IsolationShellInput("cd; " + findCmd + " -exec rm -rf {} \\;");
			result = ssh.execute(script);
		}
	}

	public void close() {

		this.dispatchLog.close();
		this.workerLog.close();

		if (ssh != null)
			ssh.close();
		isStopped = true;
		shouldStop = true;
	}

	public void resetSSH() throws JSchException {
		try {
			if (ssh != null)
				ssh.close();
		} catch (Exception e) {
			e.printStackTrace();
		}

		String host = context.getInstanceProperty(currEnvId, "ssh.host");
		String port = context.getInstanceProperty(currEnvId, "ssh.port");
		String user = context.getInstanceProperty(currEnvId, "ssh.user");
		String pwd = context.getInstanceProperty(currEnvId, "ssh.pwd");

		this.ssh = new SSHConnect(host, port, user, pwd);
	}

	public void addResultItem(String flag, String message) {
		if (flag == null)
			this.resultItemList.add(message);
		else
			this.resultItemList.add(" : " + flag + " " + message);
	}

	public void stop() {
		this.shouldStop = true;
	}

	public boolean isStopped() {
		return this.isStopped;
	}

	public String getCurrentEnvId() {
		return this.currEnvId;
	}
}
