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
package com.navercorp.cubridqa.cqt.console;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

import com.navercorp.cubridqa.cqt.console.bean.CaseResult;
import com.navercorp.cubridqa.cqt.console.bean.ProcessMonitor;
import com.navercorp.cubridqa.cqt.console.bean.Summary;
import com.navercorp.cubridqa.cqt.console.bean.Test;
import com.navercorp.cubridqa.cqt.console.bo.ConsoleBO;
import com.navercorp.cubridqa.cqt.console.util.LogUtil;
import com.navercorp.cubridqa.cqt.console.util.PropertiesUtil;
import com.navercorp.cubridqa.cqt.console.util.StdOutJob;
import com.navercorp.cubridqa.cqt.console.util.SystemUtil;
import com.navercorp.cubridqa.cqt.console.util.TestUtil;


public class ConsoleAgent {
	private static final int COME_FROM_CQT_32 = 32;
	private static final int COME_FROM_CQT_64 = 64;

	private static List<String> messageList = new ArrayList<String>();


	private static ConsoleBO Innerbo;

	public static ProcessMonitor getProcessMonitor() {
		return Innerbo.getProcessMonitor();
	}

	/*--
	 * execute test case .
	 * 
	 * @param files
	 *            the file path array that to be executed.
	 * 
	 * @param runMode
	 *            the test model. 0:running the case with no answer file. 
	 *            1:running the case and make the result to be answer . 
	 *            2:running the case that has answer and save the result to the result directory.
	 *            3:running the case that has answer and save the result to the scenario directory.
	 *            
	 * @param testInfoMap
	 *            the parameter needed for the test.
	 * @param printResult
	 * 			  if print the test result.
	 * @param comefrom
	 * 			  comefrom=1:GUI  comefrom=2:CQT  
	 * @return
	 */
	@SuppressWarnings("deprecation")
	public static void runTest(String[] files, String testType, String typeAlias,
			boolean printResult, int comefrom, String charset_file) {
		if (files == null) {
			return ;
		}

		boolean useMonitor = false;
		boolean saveEveryone = true;
		String bit = "";
		String testCategory = "";
		
		StdOutJob stdOutJob = null;
		String codeset = TestUtil.DEFAULT_CODESET;
		try {
			stdOutJob = new StdOutJob(System.out, StdOutJob.START);
			stdOutJob.start();
			ConsoleBO bo = new ConsoleBO(useMonitor, saveEveryone);
			Innerbo = bo;
			
			String scenarioTypeName = null;
			if (comefrom == COME_FROM_CQT_32) {
				scenarioTypeName = "schedule";
				testCategory = testType + "_32bit";
				bit = "32bit";
			} else if (comefrom == COME_FROM_CQT_64) {
				scenarioTypeName = "schedule";
				testCategory = testType + "_64bit";
				bit = "64bit";
			}
			String testId = TestUtil.getTestId(scenarioTypeName, testCategory);
			String charsetfile = TestUtil.getCharsetFile(charset_file);
			String resDir = TestUtil.getResultDir(testId);
			Test test = new Test(testId);
			test.setRunMode(Test.MODE_RESULT);
			test.setCodeset(codeset);
			test.setTestType(testType);
			test.setResult_dir(resDir);
			test.setTestTypeAlias(typeAlias);
			test.setTestBit(bit);
			test.setCharset_file(charset_file);
			PropertiesUtil.initConfig(charsetfile, test);
			if (Boolean.parseBoolean(PropertiesUtil.getValueWithDefault(
					"isdebug", "false").trim())) {
				test.setDebug(true);
			} else {
				test.setDebug(false);
			}
			// set qaview from local.properties
			try {
				if (Boolean.parseBoolean(PropertiesUtil.getValueWithDefault(
						"qaview", "false").trim())) {
					test.setQaview(true);
				}
			} catch (Exception e) {
				System.err
						.println("There is an exception "
								+ "when getting variable 'qaview' from local.properties:  "
								+ "\n" + e.getMessage());
			}
			

			String testOs = SystemUtil.getOS();
			if (comefrom == COME_FROM_CQT_32) {
				test.setVersion("32bits");
			}else if(comefrom == COME_FROM_CQT_64 && "windows".equalsIgnoreCase(testOs))
			{
				test.setVersion("64bits");
			}
			else {
				test.setVersion("Main");
			}

			test.setCases(files.clone());

			// Print Result Root Dir
			System.out.println("Result Root Dir:" + test.getResult_dir());
			LogUtil.log("Result Root Dir:", test.getResult_dir());

			bo.setPrintType(printResult ? Executor.PRINT_STDOUT
					: Executor.PRINT_UI);
			Summary summary = bo.runTest(test);
			if(test.isNeedSummaryXML())
				TestUtil.saveSummaryMainInfo(test, summary);
			if (summary != null) {
				if (printResult) {
					System.out.println("total:" + summary.getTotalCount());
					System.out.println("success:" + summary.getSuccessCount());
					System.out.println("fail:" + summary.getFailCount());
					System.out.println("SiteRunTimes:"
							+ summary.getSiteRunTimes());
					System.out.println("totalTime:" + summary.getTotalTime()
							+ "ms");
				}
				Map caseMap = new HashMap();
				List caseFileList = test.getCaseFileList();
				for (int i = 0; i < caseFileList.size(); i++) {
					String caseFile = (String) caseFileList.get(i);
					CaseResult caseResult = (CaseResult) test
							.getCaseResultFromMap(caseFile);
					if (!caseResult.isShouldRun()) {
						caseMap.put(caseResult.getCaseFile(), "");
					} else {
						caseMap.put(caseResult.getCaseFile(), (caseResult
								.isSuccessFul() ? "ok" : "nok"));
					}

					String ret = (String) caseMap.get(caseResult.getCaseFile());
					if (printResult) {
						System.out.println(caseResult.getCaseFile() + "    "
								+ ret);
					}
				}
				List SiteRunTimesList = new ArrayList();
				for (int i = 0; i < caseFileList.size(); i++) {
					String caseFile = (String) caseFileList.get(i);
					CaseResult caseResult = (CaseResult) test
							.getCaseResultFromMap(caseFile);
					SiteRunTimesList.add(caseResult.getSiteRunTimes());

				}

			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			try {
				stdOutJob.setEnd(StdOutJob.END);
				stdOutJob.stop();
			} catch (RuntimeException e) {
			}
		}
	}

	public static Map checkAnswers(String directory) {
		return checkAnswers(directory, false);
	}

	/**
	 * check the answer of specified directory.
	 * 
	 * @param directory
	 * @param printResult
	 * @return
	 */
	public static Map checkAnswers(String directory, boolean printResult) {
		ConsoleBO bo = new ConsoleBO();
		Map ret = bo.checkAnswers(directory);
		if (printResult) {
			System.out.println(ret);
		}
		return ret;
	}

	/**
	 * get the message to be output.
	 * 
	 * @return
	 */
	public static String getMessage() {
		StringBuilder ret = new StringBuilder();
		synchronized (messageList) {
			if (messageList.size() > 0) {
				ret.append(messageList.get(0));
				messageList.remove(0);
			}
		}
		if (ret.length() > 0) {
			return ret.toString();
		} else {
			return null;
		}
	}

	/**
	 * store the message .
	 * 
	 * @param message
	 */
	public static void addMessage(Object message) {
		if (message == null) {
			return;
		}
		synchronized (messageList) {
			messageList.add(message.toString());
			messageList.notifyAll();
		}
	}

	public static void main2(String[] args) {
		String charset_xml="test_default.xml";
		String command="runCQT";
		String typeAlias="sql_ext_ccc";
		String type="sql";
		String version="64";
		String[] files = { "G:/dailyqa/trunk/scenario2/sql_ext/_01_object/_01_type/?db=basic_qa" };
		int comefrom = "32".equalsIgnoreCase(version) ? COME_FROM_CQT_32
				: COME_FROM_CQT_64;
		runTest(files, type, typeAlias, true, comefrom, charset_xml);

	}
	
	/**
	 * @deprecated
	 * @param args
	 */
	public static void main(String[] args) {
		if (args == null || args.length == 0) {
			return;
		}

		String charset_xml = "test_default.xml";

		String command = args[0];
		if ("runCQT".equals(command)) {
			if (args.length < 6) {
				System.out
						.println("There should be more than five arguments for the command runCQT.");
				return;
			}

			String type = args[1];
			String typeAlias = args[2];
			String version = args[3];

			if (args[4] != null) {
				charset_xml = args[4];
			}

			String[] files = new String[args.length - 5];
			System.arraycopy(args, 5, files, 0, args.length - 5);

			Map testInfoMap = new Hashtable();
			if (type != null) {
				testInfoMap.put("type", type);
			}
			int comefrom = "32".equalsIgnoreCase(version) ? COME_FROM_CQT_32
					: COME_FROM_CQT_64;
			runTest(files, type, typeAlias,  true, comefrom, charset_xml);
		}
	}
}