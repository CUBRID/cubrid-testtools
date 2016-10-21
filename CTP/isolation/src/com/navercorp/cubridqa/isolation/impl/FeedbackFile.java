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

package com.navercorp.cubridqa.isolation.impl;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Date;
import java.util.Properties;

import com.jcraft.jsch.JSchException;
import com.navercorp.cubridqa.common.CommonUtils;
import com.navercorp.cubridqa.common.ConfigParameterConstants;
import com.navercorp.cubridqa.common.Log;
import com.navercorp.cubridqa.isolation.Constants;
import com.navercorp.cubridqa.isolation.Context;
import com.navercorp.cubridqa.isolation.Feedback;
import com.navercorp.cubridqa.isolation.IsolationHelper;
import com.navercorp.cubridqa.isolation.IsolationScriptInput;
import com.navercorp.cubridqa.shell.common.SSHConnect;

public class FeedbackFile implements Feedback {

	String logName;
	Log feedbackLog;
	String statusLogName;
	long taskStartTime;
	Context context;
	int totalCaseNum = 0;
	int totalExecutedCaseNum = 0;
	int totalSuccNum = 0;
	int totalFailNum = 0;
	int totalSkipNum = 0;

	public FeedbackFile(Context context) {
		logName = CommonUtils.concatFile(context.getCurrentLogDir(),
				"feedback.log");
		statusLogName = CommonUtils.concatFile(context.getCurrentLogDir(),
				"test_status.data");
		this.context = context;
	}

	@Override
	public void onTaskStartEvent(String buildFilename) {
		feedbackLog = new Log(logName, false, false);
		taskStartTime = System.currentTimeMillis();
		println("[TASK START] Current Time is " + new Date());
	}

	@Override
	public void onTaskContinueEvent() {
		initStatisticsForContinue();
		feedbackLog = new Log(logName, false, true);
		println("[TASK CONTINUE] Current Time is " + new Date());
	}

	@Override
	public void onTaskStopEvent() {
		showTestResult();

		long taskStopTime = System.currentTimeMillis();
		println("[TEST STOP] Current Time is " + new Date(), "Elapse Time:"
				+ ((taskStopTime - this.taskStartTime)));
		feedbackLog.close();
	}

	@Override
	public void setTotalTestCase(int tbdNum, int macroSkippedNum,
			int tempSkippedNum) {
		if (context.isContinueMode())
			return;

		this.totalCaseNum = tbdNum;
		this.totalSkipNum = macroSkippedNum + tempSkippedNum;
		println("The Number of Test Cases: " + tbdNum + " (macro skipped: "
				+ macroSkippedNum + ", bug skipped: " + tempSkippedNum + ")");
		updateTestingStatistics();
	}

	@Override
	public void onTestCaseStopEvent(String testCase, boolean flag,
			long elapseTime, String resultCont, String envIdentify,
			boolean isTimeOut, boolean hasCore, String skippedType) {
		String head;
		if (skippedType.equals(Constants.SKIP_TYPE_NO)) {
			head = flag ? "[OK]" : "[NOK]";
			if (flag) {
				this.totalSuccNum++;
			} else {
				this.totalFailNum++;
			}
		} else if (skippedType.equals(Constants.SKIP_TYPE_BY_MACRO)) {
			head = "[SKIP_BY_MACRO]";
		} else if (skippedType.equals(Constants.SKIP_TYPE_BY_TEMP)) {
			head = "[SKIP_BY_BUG]";
		} else {
			head = "[UNKNOWN]";
		}

		println(head + " " + testCase + " " + elapseTime + " " + envIdentify,
				resultCont, "");
		updateTestingStatistics();
	}

	public void showTestResult() {
		println("============= PRINT SUMMARY ==================");
		System.out.println("============= PRINT SUMMARY ==================");
		Properties prop;
		try {
			prop = com.navercorp.cubridqa.common.CommonUtils
					.getProperties(this.statusLogName);

			println("Total Case:" + prop.getProperty("total_case_count"));
			println("Total Execution Case:"
					+ prop.getProperty("total_executed_case_count"));
			println("Total Success Case:"
					+ prop.getProperty("total_success_case_count"));
			println("Total Fail Case:"
					+ prop.getProperty("total_fail_case_count"));
			println("Total Skip Case:"
					+ prop.getProperty("total_skip_case_count"));
			System.out.println("Total Case:"
					+ prop.getProperty("total_case_count"));
			System.out.println("Total Execution Case:"
					+ prop.getProperty("total_executed_case_count"));
			System.out.println("Total Success Case:"
					+ prop.getProperty("total_success_case_count"));
			System.out.println("Total Fail Case:"
					+ prop.getProperty("total_fail_case_count"));
			System.out.println("Total Skip Case:"
					+ prop.getProperty("total_skip_case_count"));
			System.out.println();
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	private void initStatisticsForContinue() {
		Properties prop;
		try {
			prop = com.navercorp.cubridqa.common.CommonUtils
					.getProperties(this.statusLogName);
		} catch (FileNotFoundException ex) {
			prop = new Properties();
		} catch (IOException e) {
			System.out.println("[WARN] file " + this.statusLogName
					+ " read fail!");
			prop = new Properties();
			e.printStackTrace();
		}

		this.totalCaseNum = Integer.parseInt(prop.getProperty(
				"total_case_count", "0").trim());
		this.totalSuccNum = Integer.parseInt(prop.getProperty(
				"total_success_case_count", "0").trim());
		this.totalFailNum = Integer.parseInt(prop.getProperty(
				"total_fail_case_count", "0").trim());
		this.totalSkipNum = Integer.parseInt(prop.getProperty(
				"total_skip_case_count", "0").trim());
		this.totalExecutedCaseNum = Integer.parseInt(prop.getProperty(
				"total_executed_case_count", "0").trim());
	}

	private synchronized void updateTestingStatistics() {
		this.totalExecutedCaseNum = totalSuccNum + totalFailNum;
		this.totalCaseNum = this.totalExecutedCaseNum + this.totalSkipNum;
		
		Properties prop = new Properties();
		prop.setProperty("total_case_count", String.valueOf(this.totalCaseNum));
		prop.setProperty("total_executed_case_count",
				String.valueOf(this.totalExecutedCaseNum));
		prop.setProperty("total_success_case_count",
				String.valueOf(this.totalSuccNum));
		prop.setProperty("total_fail_case_count",
				String.valueOf(this.totalFailNum));
		prop.setProperty("total_skip_case_count",
				String.valueOf(this.totalSkipNum));
		try {
			com.navercorp.cubridqa.common.CommonUtils.writeProperties(
					this.statusLogName, prop);
		} catch (IOException e) {
			System.out.println("[ERROR]: Update test status into "
					+ this.statusLogName + " fail!");
			println("[ERROR]: Update test status into " + this.statusLogName
					+ " fail!");
			e.printStackTrace();
		}
	}

	@Override
	public void onTestCaseStartEvent(String testCase, String envIdentify) {
		// TODO Auto-generated method stub
	}

	private synchronized void println(String... conts) {
		for (String cont : conts) {
			feedbackLog.println(cont);
		}
	}

	@Override
	public void onTestCaseMonitor(String testCase, String action,
			String envIdentify) {
		println(action + " " + testCase + " " + envIdentify);
	}

	@Override
	public void onDeployStart(String envIdentify) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onDeployStop(String envIdentify) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onTestCaseUpdateStart(String envIdentify) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onTestCaseUpdateStop(String envIdentify) {
		// TODO Auto-generated method stub
	}

	public String getTaskId() {
		return "0";
	}

	@Override
	public void onStopEnvEvent(String envIdentify) {
		String role = context.getProperty(
				ConfigParameterConstants.CUBRID_INSTALL_ROLE, "").trim();
		SSHConnect ssh = null;
		if (role.indexOf("coverage") != -1) {
			println("[Code Coverage] Current Time is " + new Date());
			feedbackLog
					.print("[Code Coverage] start code coverage data collection!");

			String covHost = context.getProperty(
					ConfigParameterConstants.COVERAGE_CONTROLLER_IP, "").trim();
			String covUser = context.getProperty(
					ConfigParameterConstants.COVERAGE_CONTROLLER_USER, "")
					.trim();
			String covPwd = context.getProperty(
					ConfigParameterConstants.COVERAGE_CONTROLLER_PASSWORD, "")
					.trim();
			String covPort = context.getProperty(
					ConfigParameterConstants.COVERAGE_CONTROLLER_PORT, "")
					.trim();
			String covTargetDir = context.getProperty(
					ConfigParameterConstants.COVERAGE_CONTROLLER_RESULT, "")
					.trim();
			String category = context.getTestCategory();
			String covParams = "-n " + context.getBuildId() + " -c " + category
					+ " -user " + covUser + " -pwd '" + covPwd + "' -host "
					+ covHost + " -to " + covTargetDir + " -port " + covPort;
			envIdentify = "EnvId=" + envIdentify + "["
					+ IsolationHelper.getTestNodeTitle(context, envIdentify)
					+ "]";
			try {
				ssh = IsolationHelper.createTestNodeConnect(context,
						envIdentify);

				IsolationScriptInput scripts = new IsolationScriptInput();
				scripts.addCommand("run_coverage_collect_and_upload "
						+ covParams);
				String result;
				try {
					result = ssh.execute(scripts);
					feedbackLog.println(result);
				} catch (Exception e) {
					feedbackLog.print("[ERROR] " + e.getMessage());
				}
			} catch (JSchException jschE) {
				jschE.printStackTrace();
			} finally {
				if (ssh != null) {
					ssh.close();
				}

				println("[Code Coverage] end code coverage collection on "
						+ envIdentify);
				feedbackLog
						.print("[Code Coverage] end code coverage collection on "
								+ envIdentify);
			}

		}

	}
}
