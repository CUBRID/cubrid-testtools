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
import com.navercorp.cubridqa.common.ConfigParameterConstants;
import com.navercorp.cubridqa.common.Log;
import com.navercorp.cubridqa.common.coreanalyzer.CommonUtil;
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

		envIdentify = "EnvId=" + currEnvId + "[" + IsolationHelper.getTestNodeTitle(context, currEnvId) + "]";

		resetSSH();
	}

	public void runAll() throws Exception {

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
					resultCont.append("=================================================================== D I F F ===================================================================").append(
							Constants.LINE_SEPARATOR);
					resultCont.append(cont).append(Constants.LINE_SEPARATOR);
				}
				context.getFeedback().onTestCaseStopEvent(this.testCaseFullName, testCaseSuccess, endTime - startTime, resultCont.toString(), envIdentify, isTimeOut, hasCore, Constants.SKIP_TYPE_NO);
				System.out.println("[TESTCASE] " + this.testCaseFullName + " EnvId=" + this.currEnvId + " " + (testCaseSuccess ? "[OK]" : "[NOK]"));
				workerLog.println("");
				dispatchLog.println(this.testCaseFullName);
			}
		}

		stopCUBRIDService();
		close();
		context.getFeedback().onStopEnvEvent(currEnvId);
		isStopped = true;
		System.out.println("[ENV STOP] " + currEnvId);
	}

	private String showDifferenceBetweenAnswerAndResult(String testCaseFullName) {
		testCaseFullName = CommonUtils.replace(testCaseFullName, "\\", "/");
		int p = testCaseFullName.lastIndexOf("/");
		String d = testCaseFullName.substring(0, p);
		String n = CommonUtils.replace(testCaseFullName.substring(p + 1), ".ctl", "");
		String answerFilename = d + "/answer/" + n + ".answer";
		String resultFilename = d + "/result/" + n + ".log";

		IsolationScriptInput script;
		String result;

		script = new IsolationScriptInput("cd ");
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
		IsolationScriptInput script = new IsolationScriptInput("");
		script.addCommand("ulimit -c unlimited");
		script.addCommand("export TEST_ID=" + this.context.getFeedback().getTaskId());
		script.addCommand("cd $ctlpath");
		String tc = testCaseFullName.trim();
		if (tc.startsWith("/") == false) {
			tc = "$HOME/" + tc;
		}

		script.addCommand("sh runone.sh -r " + (context.getRetryTimes() + 1) + " " + tc + " " + context.getTestCaseTimeoutInSec() + " " + context.getTestingDatabase() + " 2>&1");
		result = ssh.execute(script);
		workerLog.println(result);

		boolean passFlag = true;
		int p1 = result.lastIndexOf("flag: NOK");
		int p2 = result.lastIndexOf("flag: OK");
		if (p1 > p2) {
			passFlag = false;
		}

		ArrayList<String> errors = extractItems(result, "found core file");
		errors.addAll(extractItems(result, "found fatal error"));
		if (errors.size() > 0) {
			passFlag = false;
			this.hasCore = true;
			for (String e : errors) {
				this.addResultItem("NOK", e);
			}
		}

		if (passFlag == true) {
			passFlag = p2 != -1;
			if (passFlag == false) {
				this.addResultItem("NOK", "Not found OK word.");
			}
		}
		return passFlag;

		// processCoreFile();
	}
	
	private static ArrayList<String> extractItems(String source, String find) {
		ArrayList<String> resultList = new ArrayList<String>();
		if (source == null) {
			return resultList;
		}
		int startPos = 0, endPos;
		String item;
		while (true) {
			startPos = source.indexOf(find, startPos);
			if (startPos == -1) {
				break;
			}
			endPos = source.indexOf("\n", startPos);
			if (endPos == -1) {
				item = source.substring(startPos);
			} else {
				item = source.substring(startPos, endPos);
				startPos = endPos;
			}
			item = item.trim();
			item = CommonUtil.replace(item, "\'", "").trim();
			
			if (resultList.contains(item) == false) {
				resultList.add(item);
			}

			if (endPos == -1) {
				break;
			}
		}
		return resultList;
	}

	private void processCoreFile() throws Exception {
		String result;

		String findCmd = "find $CUBRID ${CTP_HOME} ${init_path} " + this.testCaseDir + " -name 'core.*'";

		IsolationScriptInput script;
		script = new IsolationScriptInput("cd; " + findCmd + "| grep -v 'core.log'");

		result = ssh.execute(script);
		if (result != null && result.trim().equals("") == false) {
			this.hasCore = true;
			result = CommonUtils.replace(result.trim(), "\n", ", ");
			result = CommonUtils.replace(result, "\r", ", ");
			this.addResultItem("NOK", "FOUND CORE(S): " + result);

			script = new IsolationScriptInput("cd; " + findCmd + " -exec rm -rf {} \\;");
			result = ssh.execute(script);
		}
	}
	
	private void stopCUBRIDService() throws Exception
	{
		this.workerLog.println("Stop service for " + envIdentify + "]");
		String result;
		IsolationScriptInput script;
		script = new IsolationScriptInput("cubrid service stop");
		result = ssh.execute(script);
		this.workerLog.println(result);
	}

	public void close() {
		this.dispatchLog.close();
		this.workerLog.close();

		if (ssh != null)
			ssh.close();
		isStopped = true;
		shouldStop = true;
		this.startTime = 0;
	}

	public void resetSSH() throws JSchException {
		try {
			if (ssh != null)
				ssh.close();
		} catch (Exception e) {
			e.printStackTrace();
		}

		this.ssh = IsolationHelper.createTestNodeConnect(context, currEnvId);
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
