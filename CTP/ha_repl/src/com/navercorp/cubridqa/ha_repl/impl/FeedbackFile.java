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
package com.navercorp.cubridqa.ha_repl.impl;

import java.util.ArrayList;
import java.util.Date;

import com.navercorp.cubridqa.common.CommonUtils;
import com.navercorp.cubridqa.common.Log;
import com.navercorp.cubridqa.ha_repl.Context;
import com.navercorp.cubridqa.ha_repl.Feedback;
import com.navercorp.cubridqa.ha_repl.InstanceManager;
import com.navercorp.cubridqa.ha_repl.common.Constants;
import com.navercorp.cubridqa.shell.common.GeneralShellInput;
import com.navercorp.cubridqa.shell.common.SSHConnect;

public class FeedbackFile implements Feedback {

	String logName;
	Log feedbackLog;
	long taskStartTime;
	Context context;

	public FeedbackFile(Context context) {
		logName = CommonUtils.concatFile(context.getCurrentLogDir(), "feedback.log");
		this.context = context;
	}

	@Override
	public void onTaskStartEvent() {
		feedbackLog = new Log(logName, false, false);
		taskStartTime = System.currentTimeMillis();
		println("[TASK START] Current Time is " + new Date());
	}

	@Override
	public void onTaskContinueEvent() {
		feedbackLog = new Log(logName, false, true);
		println("[TASK CONTINUE] Current Time is " + new Date());
	}

	@Override
	public void onTaskStopEvent() {
		long taskStopTime = System.currentTimeMillis();
		println("[TASK STOP] Current Time is " + new Date(), "Elapse Time:" + ((taskStopTime - this.taskStartTime)));
		feedbackLog.close();
	}

	@Override
	public void setTotalTestCase(int tbdNum, int macroSkippedNum, int tempSkippedNum) {
		println("Total: " + (tbdNum + macroSkippedNum + tempSkippedNum) + ", tbd: " + tbdNum + ", skipped: " + (tempSkippedNum + macroSkippedNum));
	}

	@Override
	public void onTestCaseStopEvent(String testCase, boolean flag, long elapseTime, String resultCont, String envIdentify, boolean isTimeOut, boolean hasCore, String skippedType) {
		String head;
		if (skippedType.equals(Constants.SKIP_TYPE_NO)) {
			head = flag ? "[OK]" : "[NOK]";
		} else if (skippedType.equals(Constants.SKIP_TYPE_BY_MACRO)) {
			head = "[SKIP_BY_MACRO]";
		} else if (skippedType.equals(Constants.SKIP_TYPE_BY_TEMP)) {
			head = "[SKIP_BY_BUG]";
		} else {
			head = "[UNKNOWN]";
		}

		println(head + " " + testCase + " " + elapseTime + "ms " + envIdentify + " " + (hasCore ? "FOUND CORE" : "") + " " + (isTimeOut ? "TIMEOUT" : ""), resultCont, "");
	}

	@Override
	public void onTestCaseStartEvent(String testCase, String envIdentify) {
	}

	protected synchronized void println(String... conts) {
		for (String cont : conts) {
			feedbackLog.println(cont);
		}
	}

	@Override
	public void onTestCaseMonitor(String testCase, String action, String envIdentify) {
		println("[MONITOR]" + action + " " + testCase + " " + envIdentify);
	}

	@Override
	public void onDeployStart(String envIdentify) {
		println("[DEPLOY] START " + envIdentify);
	}

	@Override
	public void onDeployStop(String envIdentify) {
		println("[DEPLOY] STOP " + envIdentify);
	}

	@Override
	public void onConvertEventStart() {
		println("[CONVERT] START");

	}

	@Override
	public void onConvertEventStop() {
		println("[CONVERT] STOP");
	}

	public int getTaskId() {
		return -1;
	}

	@Override
	public void onStopEnvEvent(InstanceManager hostManager, Log log) {
		String role = context.getProperty("main.testing.role", "").trim();

		if (role.indexOf("coverage") != -1) {
			String build_id = context.getBuildId();
			String category = context.getProperty("main.testing.category", "").trim();
			String c_user = context.getProperty("main.coverage.user", "").trim();
			String c_pwd = context.getProperty("main.coverage.pwd", "").trim();
			String c_ip = context.getProperty("main.coverage.ip", "").trim();
			String c_port = context.getProperty("main.coverage.port", "").trim();
			String c_dir = context.getProperty("main.coverage.dir", "").trim();

			ArrayList<SSHConnect> list = hostManager.getAllNodeList();
			for (SSHConnect ssh : list) {
				try {

					GeneralShellInput scripts = new GeneralShellInput();

					scripts.addCommand(
							"run_coverage_collect_and_upload -n " + build_id + " -c " + category + " -user " + c_user + " -pwd " + c_pwd + " -host " + c_ip + " -to " + c_dir + " -port " + c_port);
					scripts.addCommand(" ");

					String result = ssh.execute(scripts);
					log.println(scripts.toString());
					log.println(result);
				} catch (Exception e) {
					log.println("ip:" + ssh.getHost() + "  user:" + ssh.getUser() + " exception:" + e.getMessage());
				}
			}
		}
	}
}
