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

package com.navercorp.cubridqa.shell.result;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import com.jcraft.jsch.JSchException;
import com.navercorp.cubridqa.common.ConfigParameterConstants;
import com.navercorp.cubridqa.common.junit.JunitCase;
import com.navercorp.cubridqa.common.junit.JunitSuite;
import com.navercorp.cubridqa.common.junit.JunitXml;
import com.navercorp.cubridqa.shell.common.CommonUtils;
import com.navercorp.cubridqa.shell.common.Constants;
import com.navercorp.cubridqa.shell.common.Log;
import com.navercorp.cubridqa.shell.common.SSHConnect;
import com.navercorp.cubridqa.shell.common.ShellScriptInput;
import com.navercorp.cubridqa.shell.main.Context;
import com.navercorp.cubridqa.shell.main.Feedback;
import com.navercorp.cubridqa.shell.main.ShellHelper;

public class FeedbackFile implements Feedback {

	String logName;
	String statusLogName;
	String xmlLogName;
	File rerunLogDir;
	Log feedbackLog;
	Log statusLog;
	long taskStartTime;
	Context context;
	int task_id;
	int totalCaseNum = 0;
	int totalExecutedCaseNum = 0;
	int totalSuccNum = 0;
	int totalFailNum = 0;
	int totalSkipNum = 0;
	
	JunitSuite xmlSuite;
	JunitXml xml;
	long xmlStartTime;
	Map<String, CaseRuns> caseRuns = new HashMap<String, CaseRuns>();

	private static class CaseRuns {
		final TreeMap<Integer, JunitCase> earlierRuns = new TreeMap<Integer, JunitCase>();
		JunitCase lastRun;
		int earlierRunCount;
	}

	public FeedbackFile(Context context) {
		logName = CommonUtils.concatFile(context.getCurrentLogDir(), "feedback.log");
		statusLogName = CommonUtils.concatFile(context.getCurrentLogDir(), "test_status.data");
		rerunLogDir = new File(context.getCurrentLogDir(), "junit_reruns");
		this.context = context;
		xmlLogName = CommonUtils.concatFile(context.getCurrentLogDir(), xmlFileName(null));
	}

	// <os>_<category>_<bits>_<build type>.xml (e.g. linux_shell_64bit_optdebug.xml), the name form of the SQL runner's report.
	// Bits and build type come only from cubrid_rel of the tested build.
	private String xmlFileName(String cubridRel) {
		StringBuilder sb = new StringBuilder(context.isWindows() ? "windows" : "linux");
		sb.append('_').append(context.getTestCategory());
		String[] build = com.navercorp.cubridqa.common.CommonUtils.parseCubridRel(cubridRel);
		if (build != null) {
			sb.append('_').append(build[0]).append('_').append(build[1]);
		}
		return sb.append(".xml").toString();
	}

	// The file opens before TestFactory installs the test build, so the name is settled when it closes.
	// A test node that does not answer must not hold up the end of the task: after a minute the name stays as it is.
	// The watchdog also drops the SSH session, whose JSch thread is not a daemon and would keep the JVM alive.
	private void nameXmlByTestedBuild() {
		final int lookupSecs = 60;
		FutureTask<String> cubridRel = new FutureTask<String>(new Callable<String>() {
			public String call() throws Exception {
				SSHConnect ssh = ShellHelper.createFirstTestNodeConnect(context);
				ssh.setReadTimeoutSecs(lookupSecs);
				try {
					return ssh.execute(new ShellScriptInput("cubrid_rel"));
				} finally {
					ssh.close();
				}
			}
		});
		Thread lookup = new Thread(cubridRel, "xml-name-lookup");
		lookup.setDaemon(true);
		lookup.start();
		try {
			File named = new File(context.getCurrentLogDir(), xmlFileName(cubridRel.get(lookupSecs, TimeUnit.SECONDS)));
			File current = new File(xmlLogName);
			if (named.getAbsolutePath().equals(current.getAbsolutePath())) {
				return;
			}
			if (current.renameTo(named)) {
				xmlLogName = named.getPath();
			} else {
				System.err.println("Error naming XML file by cubrid_rel: cannot rename " + current + " to " + named);
			}
		} catch (Exception e) {
			cubridRel.cancel(true);
			System.err.println("Error naming XML file by cubrid_rel: " + e);
		}
	}

	@Override
	public void onTaskStartEvent(String buildFilename) {
		feedbackLog = new Log(logName, false, false);
		Log log = new Log(CommonUtils.concatFile(context.getCurrentLogDir(), "current_task_id"), false, false);
		this.task_id = 0;
		log.println(String.valueOf(task_id));
		context.setTaskId(task_id);
		log.close();
		taskStartTime = System.currentTimeMillis();
		println("[Task Id] is " + this.task_id);
		println("[TASK START] Current Time is " + new Date() + ", start MSG Id is " + this.context.getMsgId());
		
		initializeXmlWriter();
	}

	@Override
	public void onTaskContinueEvent() {
		feedbackLog = new Log(logName, false, true);
		String cont = null;
		try {
			cont = CommonUtils.getFileContent(CommonUtils.concatFile(context.getCurrentLogDir(), "current_task_id"));
			this.task_id = Integer.parseInt(cont.trim());
			context.setTaskId(task_id);
		} catch (Exception e) {
			this.task_id = -1;
			e.printStackTrace();
		}

		initStatisticsForContinue();
		println("[Task Id] is " + this.task_id);
		println("[TASK CONTINUE] Current Time is " + new Date());
		
		initializeXmlWriter();

	}

	@Override
	public void onTaskStopEvent() {
		showTestResult();

		long taskStopTime = System.currentTimeMillis();
		println("[TEST STOP] Current Time is " + new Date(), "Elapse Time:" + ((taskStopTime - this.taskStartTime)));
		
		finalizeXmlWriter();
		
		feedbackLog.close();
	}

	@Override
	public void setTotalTestCase(int tbdNum, int macroSkippedNum, int tempSkippedNum) {
		if (context.isContinueMode())
			return;

		this.totalCaseNum = tbdNum;
		this.totalSkipNum = macroSkippedNum + tempSkippedNum;
		println("Test Category:" + context.getTestCategory());
		System.out.println("Test Category:" + context.getTestCategory());
		println("The Number of Test Cases: " + tbdNum + " (macro skipped: " + macroSkippedNum + ", bug skipped: " + tempSkippedNum + ")");
		System.out.println("The Number of Test Cases: " + tbdNum + " (macro skipped: " + macroSkippedNum + ", bug skipped: " + tempSkippedNum + ")");

		updateTestingStatistics();				
	}

	public void showTestResult() {
		println("============= PRINT SUMMARY ==================");
		System.out.println("============= PRINT SUMMARY ==================");
		Properties prop;
		try {
			prop = com.navercorp.cubridqa.common.CommonUtils.getProperties(this.statusLogName);
			println("Test Category:" + context.getTestCategory());
			println("Total Case:" + prop.getProperty("total_case_count"));
			println("Total Execution Case:" + prop.getProperty("total_executed_case_count"));
			println("Total Success Case:" + prop.getProperty("total_success_case_count"));
			println("Total Fail Case:" + prop.getProperty("total_fail_case_count"));
			println("Total Skip Case:" + prop.getProperty("total_skip_case_count"));
			System.out.println("Test Category:" + context.getTestCategory());
			System.out.println("Total Case:" + prop.getProperty("total_case_count"));
			System.out.println("Total Execution Case:" + prop.getProperty("total_executed_case_count"));
			System.out.println("Total Success Case:" + prop.getProperty("total_success_case_count"));
			System.out.println("Total Fail Case:" + prop.getProperty("total_fail_case_count"));
			System.out.println("Total Skip Case:" + prop.getProperty("total_skip_case_count"));
			System.out.println();
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	private void initStatisticsForContinue() {
		Properties prop;
		try {
			prop = com.navercorp.cubridqa.common.CommonUtils.getProperties(this.statusLogName);
		} catch (FileNotFoundException ex) {
			prop = new Properties();
		} catch (IOException e) {
			System.out.println("[WARN] file " + this.statusLogName + " read fail!");
			prop = new Properties();
			e.printStackTrace();
		}

		this.totalCaseNum = Integer.parseInt(prop.getProperty("total_case_count", "0").trim());
		this.totalSuccNum = Integer.parseInt(prop.getProperty("total_success_case_count", "0").trim());
		this.totalFailNum = Integer.parseInt(prop.getProperty("total_fail_case_count", "0").trim());
		this.totalSkipNum = Integer.parseInt(prop.getProperty("total_skip_case_count", "0").trim());
		this.totalExecutedCaseNum = Integer.parseInt(prop.getProperty("total_executed_case_count", "0").trim());
	}

	private synchronized void updateTestingStatistics() {
		this.totalExecutedCaseNum = totalSuccNum + totalFailNum;
		this.totalCaseNum = this.totalExecutedCaseNum + this.totalSkipNum;
		Properties prop = new Properties();
		prop.setProperty("total_case_count", String.valueOf(this.totalCaseNum));
		prop.setProperty("total_executed_case_count", String.valueOf(this.totalExecutedCaseNum));
		prop.setProperty("total_success_case_count", String.valueOf(this.totalSuccNum));
		prop.setProperty("total_fail_case_count", String.valueOf(this.totalFailNum));
		prop.setProperty("total_skip_case_count", String.valueOf(this.totalSkipNum));
		try {
			com.navercorp.cubridqa.common.CommonUtils.writeProperties(this.statusLogName, prop);
		} catch (IOException e) {
			System.out.println("[ERROR]: Update test status into " + this.statusLogName + " fail!");
			println("[ERROR]: Update test status into " + this.statusLogName + " fail!");
			e.printStackTrace();
		}
	}

	@Override
	public void onTestCaseStopEvent(String testCase, boolean flag, long elapseTime, String resultCont, String envIdentify, boolean isTimeOut, boolean hasCore, String skippedType, int retryCount) {
		String head;
		String name = extractRelativePath(testCase);

		if (skippedType.equals(Constants.SKIP_TYPE_NO)) {
			head = flag ? "[OK]: " : "[NOK]: " + Constants.RETRY_FLAG + " = " + retryCount;
			if (flag) {
				this.totalSuccNum++;
				recordCase(testCase, JunitCase.passed(name, testCase, elapseTime), retryCount);
			} else {
				this.totalFailNum++;
				recordCase(testCase, failedRun(name, testCase, elapseTime, resultCont, envIdentify, isTimeOut, hasCore), retryCount);
			}
		} else if (skippedType.equals(Constants.SKIP_TYPE_BY_MACRO)) {
			head = "[SKIP_BY_MACRO]";
			recordCase(testCase, JunitCase.skipped(name, testCase, "Skipped by macro"), 0);
		} else if (skippedType.equals(Constants.SKIP_TYPE_BY_TEMP)) {
			head = "[SKIP_BY_BUG]";
			recordCase(testCase, JunitCase.skipped(name, testCase, "Skipped by bug"), 0);
		} else {
			head = "[UNKNOWN]";
			recordCase(testCase, new JunitCase(name, testCase, elapseTime, JunitCase.Result.ERROR, "Unknown test status", "UnknownStatus", null), 0);
		}

		println(head + " " + testCase + " " + elapseTime + " " + envIdentify, resultCont, "");
		updateTestingStatistics();
	}

	@Override
	public void onTestCaseStopEvent(String testCase, boolean flag, long elapseTime, String resultCont, String lastPassResultCont, String envIdentify, boolean isTimeOut, boolean hasCore,
			String skippedType, int retryCount) {
		onTestCaseStopEvent(testCase, flag, elapseTime, resultCont, envIdentify, isTimeOut, hasCore, skippedType, retryCount);
	}

	@Override
	public void onTestCaseStopEventForRetry(String testCase, boolean flag, long elapseTime, String resultCont, String envIdentify, boolean isTimeOut, boolean hasCore, String skippedType,
			int retryCount) {
		String head;
		head = flag ? "[OK]: " : "[NOK]: ";
		if (!flag) {
			addEarlierRun(testCase, retryCount, failedRun(extractRelativePath(testCase), testCase, elapseTime, resultCont, envIdentify, isTimeOut, hasCore));
		}

		println(head + " " + testCase + " " + elapseTime + " " + envIdentify, resultCont, " (" + Constants.RETRY_FLAG + " = " + retryCount + ")");
	}

	@Override
	public void onTestCaseStartEvent(String testCase, String envIdentify) {
		// TODO Auto-generated method stub
	}

	private synchronized void println(String... conts) {
		for (String cont : conts) {
			if (cont == null)
				continue;
			feedbackLog.println(cont);
		}
	}

	@Override
	public void onTestCaseMonitor(String testCase, String action, String envIdentify) {
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
	public void onSvnUpdateStart(String envIdentify) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onSvnUpdateStop(String envIdentify) {
		// TODO Auto-generated method stub
	}

	@Override
	public int getTestId() {
		return 0;
	}

	private void collectCoverageOnOneNode(Context context, String currEnvId, String host) {
		SSHConnect ssh = null;
		println("[Code Coverage] Current Time is " + new Date());
		feedbackLog.print("[Code Coverage] start code coverage data collection!");
		String covHost = context.getProperty(ConfigParameterConstants.COVERAGE_CONTROLLER_IP, "").trim();
		String covUser = context.getProperty(ConfigParameterConstants.COVERAGE_CONTROLLER_USER, "").trim();
		String covPwd = context.getProperty(ConfigParameterConstants.COVERAGE_CONTROLLER_PASSWORD, "").trim();
		String covPort = context.getProperty(ConfigParameterConstants.COVERAGE_CONTROLLER_PORT, "").trim();
		String covTargetDir = context.getProperty(ConfigParameterConstants.COVERAGE_CONTROLLER_RESULT, "").trim();
		String category = context.getTestCategory();// flag will mark category
													// as a new for slave node
		String covParams = "-n " + context.getTestBuild() + " -c " + category + " -user " + covUser + " -pwd '" + covPwd + "' -host " + covHost + " -to " + covTargetDir + " -port " + covPort;

		String envIdentify = "EnvId=" + currEnvId + "[" + (ShellHelper.getTestNodeTitle(context, currEnvId, host)) + "] with " + context.getServiceProtocolType() + " protocol!";
		try {
			ssh = ShellHelper.createTestNodeConnect(context, currEnvId, host);

			ShellScriptInput scripts = new ShellScriptInput();
			scripts.addCommand("run_coverage_collect_and_upload " + covParams);
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

			println("[Code Coverage] end code coverage collection on " + envIdentify);
			feedbackLog.print("[Code Coverage] end code coverage collection on " + envIdentify);
		}
	}

	@Override
	public void onStopEnvEvent(String envIdentify) {
		String role = context.getProperty(ConfigParameterConstants.CUBRID_INSTALL_ROLE, "").trim();
		String host = context.getInstanceProperty(envIdentify, ConfigParameterConstants.TEST_INSTANCE_HOST_SUFFIX);

		if (role.indexOf("coverage") != -1) {
			collectCoverageOnOneNode(this.context, envIdentify, host);

			ArrayList<String> relatedHosts = context.getRelatedHosts(envIdentify);
			for (String h : relatedHosts) {
				collectCoverageOnOneNode(this.context, envIdentify, h);
			}

		}

	}
	
	private void initializeXmlWriter() {
		if (xmlSuite != null) {
			return;
		}

		xmlStartTime = System.currentTimeMillis();
		xmlSuite = new JunitSuite(context.getTestCategory(), xmlStartTime);
		xmlSuite.addProperty("category", context.getTestCategory());
		xmlSuite.addProperty("os", context.isWindows() ? "windows" : "linux");
		xmlSuite.addProperty("cubrid_build", context.getTestBuild());
		xmlSuite.addProperty("bits", context.getVersion());
		xmlSuite.addProperty("testcase_branch", context.getTestCaseBranch());
		try {
			xml = new JunitXml(new File(xmlLogName), xmlSuite);
		} catch (Exception e) {
			System.err.println("Error creating XML file: " + e.getMessage());
			e.printStackTrace();
		}
	}

	private JunitCase failedRun(String name, String testCase, long elapseTime, String resultCont, String envIdentify, boolean isTimeOut, boolean hasCore) {
		String body = null;
		if (resultCont != null && !resultCont.trim().isEmpty()) {
			body = "\nEnvIdentify: " + envIdentify + "\nHostname: " + System.getenv("HOSTNAME") + "\n" + resultCont + "\n";
		}
		if (hasCore) {
			return new JunitCase(name, testCase, elapseTime, JunitCase.Result.ERROR, "core file or fatal error found", "CoreDump", body);
		}
		if (isTimeOut) {
			return new JunitCase(name, testCase, elapseTime, JunitCase.Result.ERROR, "timeout", "Timeout", body);
		}
		return new JunitCase(name, testCase, elapseTime, JunitCase.Result.FAILURE, "Test failed", "TestFailure", body);
	}

	private synchronized void addEarlierRun(String testCase, int retryCount, JunitCase run) {
		if (xml == null) {
			return;
		}
		try {
			run.moveBodyTo(rerunLogDir);
		} catch (IOException e) {
			System.err.println("Error keeping a retried run's log in a file: " + e.getMessage());
		}
		CaseRuns c = runsOf(testCase);
		c.earlierRuns.put(retryCount, run);
		if (c.lastRun != null && c.earlierRuns.size() >= c.earlierRunCount) {
			writeCase(testCase);
		}
	}

	// Dispatch hands out a retry before the run that failed reports it, so the last run can arrive
	// first: it waits here until the earlier runs it counts (retryCount) have come.
	private synchronized void recordCase(String testCase, JunitCase lastRun, int earlierRunCount) {
		if (xml == null) {
			return;
		}
		CaseRuns c = runsOf(testCase);
		c.lastRun = lastRun;
		c.earlierRunCount = earlierRunCount;
		if (c.earlierRuns.size() >= earlierRunCount) {
			writeCase(testCase);
		}
	}

	private CaseRuns runsOf(String testCase) {
		CaseRuns c = caseRuns.get(testCase);
		if (c == null) {
			c = new CaseRuns();
			caseRuns.put(testCase, c);
		}
		return c;
	}

	// Surefire makes the first failed run the case; the last is kept so gha-ci shows the log of the run that decided it.
	private void writeCase(String testCase) {
		CaseRuns c = caseRuns.remove(testCase);
		for (JunitCase run : c.earlierRuns.values()) {
			c.lastRun.addRerun(run);
		}
		try {
			xml.add(c.lastRun);
		} catch (Exception e) {
			System.err.println("Error writing testcase: " + e.getMessage());
			e.printStackTrace();
		}
	}

	private synchronized void finalizeXmlWriter() {
		if (xml == null) {
			return;
		}
		for (String testCase : new ArrayList<String>(caseRuns.keySet())) {
			if (caseRuns.get(testCase).lastRun != null) {
				writeCase(testCase);
			}
		}
		// What is left are earlier runs whose last run never came: the case has no result to write.
		for (CaseRuns c : caseRuns.values()) {
			for (JunitCase run : c.earlierRuns.values()) {
				run.deleteBodyFiles();
			}
		}
		caseRuns.clear();
		rerunLogDir.delete();
		try {
			xmlSuite.setMillis(System.currentTimeMillis() - xmlStartTime);
			xml.close();
		} catch (Exception e) {
			System.err.println("Error writing XML file: " + e.getMessage());
			e.printStackTrace();
		} finally {
			xml = null;
			xmlSuite = null;
		}
		nameXmlByTestedBuild();
	}

	private String extractRelativePath(String testCase) {
		if (testCase == null || context.getTestCategory() == null) {
			return testCase == null ? "" : testCase;
		}

		String testCategory = context.getTestCategory();
		int categoryIndex = testCase.indexOf("/" + testCategory + "/");
		if (categoryIndex != -1) {
			return testCase.substring(categoryIndex + 1);
		}
		return testCase;
	}
}
