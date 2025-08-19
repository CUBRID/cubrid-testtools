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

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Properties;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import com.jcraft.jsch.JSchException;
import com.navercorp.cubridqa.common.ConfigParameterConstants;
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
	
	// XML StAX writer for streaming XML generation
	private XMLStreamWriter xmlWriter;
	private FileOutputStream xmlFileStream;
	private boolean xmlInitialized = false;

	public FeedbackFile(Context context) {
		logName = CommonUtils.concatFile(context.getCurrentLogDir(), "feedback.log");
		statusLogName = CommonUtils.concatFile(context.getCurrentLogDir(), "test_status.data");
		xmlLogName = CommonUtils.concatFile(context.getCurrentLogDir(), "test-" + context.getTestCategory() + ".xml");
		this.context = context;
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
		
		// Initialize XML output
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
		
		// Initialize XML output for continue mode
		initializeXmlWriter();

	}

	@Override
	public void onTaskStopEvent() {
		showTestResult();

		long taskStopTime = System.currentTimeMillis();
		println("[TEST STOP] Current Time is " + new Date(), "Elapse Time:" + ((taskStopTime - this.taskStartTime)));
		
		// Finalize and close XML output
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
		
		// Start testsuite element now that we know the test counts
		writeTestSuiteStart();
		
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
		double timeInSeconds = elapseTime / 1000.0;
		
		if (skippedType.equals(Constants.SKIP_TYPE_NO)) {
			head = flag ? "[OK]: " : "[NOK]: " + Constants.RETRY_FLAG + " = " + retryCount;
			if (flag) {
				this.totalSuccNum++;
				// Write XML for successful test
				writeTestCase(testCase, envIdentify, timeInSeconds, null, null, null);
			} else {
				this.totalFailNum++;
				// Write XML for failed test
				writeTestCase(testCase, envIdentify, timeInSeconds, "failure", "Test failed", resultCont);
			}
		} else if (skippedType.equals(Constants.SKIP_TYPE_BY_MACRO)) {
			head = "[SKIP_BY_MACRO]";
			// Write XML for skipped test by macro
			writeTestCase(testCase, envIdentify, 0.0, "skipped", "Skipped by macro", null);
		} else if (skippedType.equals(Constants.SKIP_TYPE_BY_TEMP)) {
			head = "[SKIP_BY_BUG]";
			// Write XML for skipped test by bug
			writeTestCase(testCase, envIdentify, 0.0, "skipped", "Skipped by bug", null);
		} else {
			head = "[UNKNOWN]";
			// Write XML for unknown status
			writeTestCase(testCase, envIdentify, timeInSeconds, "error", "Unknown test status", null);
		}

		println(head + " " + testCase + " " + elapseTime + " " + envIdentify, resultCont, "");
		updateTestingStatistics();
	}

	@Override
	public void onTestCaseStopEventForRetry(String testCase, boolean flag, long elapseTime, String resultCont, String envIdentify, boolean isTimeOut, boolean hasCore, String skippedType,
			int retryCount) {
		String head;
		head = flag ? "[OK]: " : "[NOK]: ";
		
		// Do not write retry attempts to XML - only log to feedback.log
		// XML should only contain the final result from onTestCaseStopEvent
		
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
	
	/**
	 * Initialize XML StAX writer and write document header
	 */
	private void initializeXmlWriter() {
		if (xmlInitialized) {
			return;
		}
		
		try {
			xmlFileStream = new FileOutputStream(xmlLogName);
			XMLOutputFactory factory = XMLOutputFactory.newInstance();
			
			// Set indentation properties
			factory.setProperty("javax.xml.stream.isRepairingNamespaces", Boolean.FALSE);
			
			xmlWriter = factory.createXMLStreamWriter(xmlFileStream, "UTF-8");
			
			// Write XML declaration
			xmlWriter.writeStartDocument("UTF-8", "1.0");
			
			// Write root element
			xmlWriter.writeStartElement("testsuites");
			
			xmlInitialized = true;
			
		} catch (XMLStreamException e) {
			System.err.println("Error initializing XML writer: " + e.getMessage());
			e.printStackTrace();
		} catch (IOException e) {
			System.err.println("Error initializing XML writer: " + e.getMessage());
			e.printStackTrace();
		}
	}
	
	/**
	 * Write testsuite start element with attributes
	 */
	private void writeTestSuiteStart() {
		if (!xmlInitialized || xmlWriter == null) {
			return;
		}
		
		try {
			xmlWriter.writeStartElement("testsuite");
			xmlWriter.writeAttribute("name", context.getTestCategory());
			xmlWriter.writeAttribute("tests", String.valueOf(this.totalCaseNum));
			// xmlWriter.writeAttribute("failures", "0"); // Will be updated in statistics
			// xmlWriter.writeAttribute("errors", "0");
			xmlWriter.writeAttribute("skipped", String.valueOf(this.totalSkipNum));
			// xmlWriter.writeAttribute("time", "0"); // Will be calculated at end
			xmlWriter.writeAttribute("timestamp", new Date().toString());
			
		} catch (XMLStreamException e) {
			System.err.println("Error writing testsuite start: " + e.getMessage());
			e.printStackTrace();
		}
	}
	
	/**
	 * Write testcase element
	 */
	private void writeTestCase(String testCase, String envIdentify, double timeInSeconds, 
			String resultType, String message, String details) {
		if (!xmlInitialized || xmlWriter == null) {
			return;
		}
		
		try {
			xmlWriter.writeStartElement("testcase");
			xmlWriter.writeAttribute("classname", extractRelativePath(testCase, "url"));
			xmlWriter.writeAttribute("name", extractRelativePath(testCase, "name"));
			xmlWriter.writeAttribute("file", testCase);
			xmlWriter.writeAttribute("time", String.valueOf(timeInSeconds));
			
			// Add result element if needed
			if (resultType != null) {
				xmlWriter.writeStartElement(resultType);
				if (message != null) {
					xmlWriter.writeAttribute("message", message);
				}
				if ("failure".equals(resultType)) {
					xmlWriter.writeAttribute("type", "TestFailure");
				} else if ("error".equals(resultType)) {
					xmlWriter.writeAttribute("type", "UnknownStatus");
				}
				
				// Add details as child elements or comments
				if (details != null && !details.trim().isEmpty()) {
					xmlWriter.writeComment(" Failure description or stack trace ");
					xmlWriter.writeCData(details);
				}
				
				xmlWriter.writeEndElement(); // End result element
			}
			
			xmlWriter.writeEndElement(); // End testcase element
			
		} catch (XMLStreamException e) {
			System.err.println("Error writing testcase: " + e.getMessage());
			e.printStackTrace();
		}
	}
		
	/**
	 * Finalize XML document and close writer
	 */
	private void finalizeXmlWriter() {
		if (!xmlInitialized || xmlWriter == null) {
			return;
		}
		
		try {
			// End testsuite element
			xmlWriter.writeEndElement(); // End testsuite
			
			// End testsuites element
			xmlWriter.writeEndElement(); // End testsuites
			
			// End document
			xmlWriter.writeEndDocument();
			
			// Close resources
			xmlWriter.flush();
			xmlWriter.close();
			xmlFileStream.close();
			
		} catch (XMLStreamException e) {
			System.err.println("Error finalizing XML writer: " + e.getMessage());
			e.printStackTrace();
		} catch (IOException e) {
			System.err.println("Error finalizing XML writer: " + e.getMessage());
			e.printStackTrace();
		} finally {
			xmlInitialized = false;
		}
	}
	
	/**
	 * Extract relative path from testCase starting from the test category or generate GitHub URL
	 * Example: /home/cubrid-testcases-private-ex/shell/_01_utility/_01_sqlx/bug_xdbms_sus1198/cases/bug_xdbms_sus1198.sh
	 * type="name": Returns shell/_01_utility/_01_sqlx/bug_xdbms_sus1198/cases/bug_xdbms_sus1198.sh
	 * type="url": Returns https://github.com/CUBRID/cubrid-testcases-private-ex/blob/develop/shell/_01_utility/_01_sqlx/bug_xdbms_sus1198/cases/bug_xdbms_sus1198.sh
	 */
	private String extractRelativePath(String testCase, String type) {
		if (testCase == null) {
			return "";
		}
		
		String testCategory = context.getTestCategory();
		if (testCategory == null) {
			return testCase;
		}
		
		// Find the position of test category in the path
		int categoryIndex = testCase.indexOf("/" + testCategory + "/");
		String relativePath;
		
		if (categoryIndex != -1) {
			// Extract from category onwards (including category)
			relativePath = testCase.substring(categoryIndex + 1); // +1 to skip the leading slash
		} else if (testCase.startsWith(testCategory + "/")) {
			relativePath = testCase;
		} else {
			// If still not found, return the original testCase
			if ("url".equals(type)) {
				return testCase; // Cannot generate URL for non-standard path
			}
			return testCase;
		}
		
		// Return based on requested type
		if ("url".equals(type)) {
			// Generate GitHub URL
			return "https://github.com/CUBRID/cubrid-testcases-private-ex/blob/develop/" + relativePath;
		} else {
			// Default: return name (relative path)
			return relativePath;
		}
	}
}
