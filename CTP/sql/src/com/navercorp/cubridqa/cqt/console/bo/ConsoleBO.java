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
package com.navercorp.cubridqa.cqt.console.bo;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.navercorp.cubridqa.common.coreanalyzer.AnalyzerMain;
import com.navercorp.cubridqa.cqt.common.CommonUtils;
import com.navercorp.cubridqa.cqt.console.Executor;
import com.navercorp.cubridqa.cqt.console.bean.CaseResult;
import com.navercorp.cubridqa.cqt.console.bean.ProcessMonitor;
import com.navercorp.cubridqa.cqt.console.bean.Sql;
import com.navercorp.cubridqa.cqt.console.bean.SqlParam;
import com.navercorp.cubridqa.cqt.console.bean.Summary;
import com.navercorp.cubridqa.cqt.console.bean.Test;
import com.navercorp.cubridqa.cqt.console.bean.TestCaseSummary;
import com.navercorp.cubridqa.cqt.console.dao.ConsoleDAO;
import com.navercorp.cubridqa.cqt.console.util.CommonFileUtile;
import com.navercorp.cubridqa.cqt.console.util.ConfigureUtil;
import com.navercorp.cubridqa.cqt.console.util.CubridConnection;
import com.navercorp.cubridqa.cqt.console.util.CubridUtil;
import com.navercorp.cubridqa.cqt.console.util.EnvGetter;
import com.navercorp.cubridqa.cqt.console.util.ErrorInterruptUtil;
import com.navercorp.cubridqa.cqt.console.util.FileUtil;
import com.navercorp.cubridqa.cqt.console.util.LogUtil;
import com.navercorp.cubridqa.cqt.console.util.RepositoryPathUtil;
import com.navercorp.cubridqa.cqt.console.util.StringUtil;
import com.navercorp.cubridqa.cqt.console.util.SystemUtil;
import com.navercorp.cubridqa.cqt.console.util.TestUtil;

import cubrid.jdbc.driver.CUBRIDConnection;

public class ConsoleBO extends Executor {
	private ProcessMonitor processMonitor = new ProcessMonitor();
	private boolean useMonitor;
	private boolean saveEveryone = true;
	private String os = SystemUtil.getOS();
	private String homePath = "d:\\";
	private boolean isFirstTime = true;

	public boolean isUseMonitor() {
		return useMonitor;
	}

	public void setUseMonitor(boolean useMonitor) {
		this.useMonitor = useMonitor;
	}

	public boolean isSaveEveryone() {
		return saveEveryone;
	}

	public void setSaveEveryone(boolean saveEveryone) {
		this.saveEveryone = saveEveryone;
	}

	private Test test;

	private ConfigureUtil configureUtil;

	private ConsoleDAO dao;

	private boolean hasSql = false;

	public ConsoleBO() {
		this.logId = "ConsoleBO";
		this.useMonitor = false;
		this.saveEveryone = true;
	}

	public ConsoleBO(boolean usemonitor) {
		this.useMonitor = usemonitor;
		this.logId = "ConsoleBO";
	}

	public ConsoleBO(boolean usemonitor, boolean saveeveryone) {
		this.useMonitor = usemonitor;
		this.saveEveryone = saveeveryone;
		this.logId = "ConsoleBO";
		initConsoleBoLog(this.logId);
	}

	private void initConsoleBoLog(String logId) {
		LogUtil.clearLog(logId);
		LogUtil.log(logId, "[time]clearLog:" + (System.currentTimeMillis() - startTime));
	}

	/**
	 * run the test case .
	 * 
	 * @param test
	 * @return
	 */
	@SuppressWarnings("deprecation")
	public Summary runTest(Test test) {
		if (test == null) {
			return null;
		}

		this.test = test;
		configureUtil = new ConfigureUtil();
		long startTime = System.currentTimeMillis();
		processMonitor.setStartTime(startTime);

		if (useMonitor) {
			File f = new File(RepositoryPathUtil.getTestResultDir(test.getTestId()));
			if (!f.exists())
				f.mkdirs();

		}
		LogUtil.log(logId, "[time]startMonitor:" + (System.currentTimeMillis() - startTime));
		try {
			startTime = System.currentTimeMillis();
			init();
			LogUtil.log(logId, "[time]init:" + (System.currentTimeMillis() - startTime));

			startTime = System.currentTimeMillis();
			dao = new ConsoleDAO(test, configureUtil);
			LogUtil.log(logId, "[time]createDAO:" + (System.currentTimeMillis() - startTime));

			startTime = System.currentTimeMillis();
			buildTest(test);
			LogUtil.log(logId, "[time]buildTest:" + (System.currentTimeMillis() - startTime));

			processMonitor.setAllFile(test.getCaseFileList().size());

			startTime = System.currentTimeMillis();
			boolean ok = checkDb(test);
			LogUtil.log(logId, "[time]checkDb:" + (System.currentTimeMillis() - startTime));
			if (!ok) {
				onMessage("-10000");
				LogUtil.log(logId, "-10000");
				processMonitor.doException();
				return null;
			}

			createResultDirs(test);

			if (test.isNeedSummaryXML()) {
				// create test case list into summary file
				test.setFileHandle(FileUtil.openOneFileHandle(test.getResult_dir() + TestUtil.SUMMARYLIST_FILE));
				LogUtil.log(logId, "[Test Configuration File]:" + test.getCharset_file());
				// write test case list head
				FileUtil.writeHeadForXML(test.getFileHandle());
			}

			startTime = System.currentTimeMillis();

			System.out.println("---------------execute begin--------------------");
			execute(test);
			System.out.println("---------------execute end  --------------------");

			if (this.processMonitor.getCurrentstate() == this.processMonitor.Status_Stoping) {
				this.processMonitor.setCurrentstate(this.processMonitor.Status_Stoped);
				return null;
			}
			LogUtil.log(logId, "[time]execute:" + (System.currentTimeMillis() - startTime));

			if (test.getType() == Test.TYPE_FUNCTION) {
				startTime = System.currentTimeMillis();
				if (test.getRunMode() == Test.MODE_RESULT) {
					createResultDirs(test);
				} else if (test.getRunMode() == Test.MODE_MAKE_ANSWER) {
					createAnswerDirs(test);
				}
				LogUtil.log(logId, "[time]createDir:" + (System.currentTimeMillis() - startTime));

				startTime = System.currentTimeMillis();

				if (!saveEveryone) {
					saveTempResults(test);
					LogUtil.log(logId, "[time]saveTempResult:" + (System.currentTimeMillis() - startTime));

					startTime = System.currentTimeMillis();
					if (test.getRunMode() == Test.MODE_RESULT || test.getRunMode() == Test.MODE_NO_RESULT) {
						saveResults(test);
					} else if (test.getRunMode() == Test.MODE_MAKE_ANSWER) {
						saveAnswers(test);
					}
				}
				LogUtil.log(logId, "[time]saveResultOrAnswer:" + (System.currentTimeMillis() - startTime));
			} else if (test.getType() == Test.TYPE_PERFORMANCE) {
				startTime = System.currentTimeMillis();
				LogUtil.log(logId, "[time]savePerformanceResult:" + (System.currentTimeMillis() - startTime));
			}
			onMessage("*******results saved.");
		} catch (Throwable e) {
			e.printStackTrace();
			onMessage(e.getMessage());
			LogUtil.log(logId, LogUtil.getExceptionMessage(new Exception(e.getMessage(), e)));
		} finally {
			startTime = System.currentTimeMillis();
			if (dao != null) {
				dao.release();
			}
			LogUtil.log(logId, "[time]DAO release:" + (System.currentTimeMillis() - startTime));
			startTime = System.currentTimeMillis();
			LogUtil.log(logId, "[time]stopMonitor:" + (System.currentTimeMillis() - startTime));
		}
		LogUtil.log(logId, "*******Done.");
		onMessage("*******Done.");

		// end fail summary.xml file writing
		if (test.getFileHandle() != null) {
			FileUtil.writeFooterForXml(test.getFileHandle());
			FileUtil.closeFileHandle(test.getFileHandle());
		}
		return test.getSummary();
	}

	/**
	 * get the summary info from directory
	 * 
	 * @param directory
	 * @return
	 */
	public Map<String, Integer> checkAnswers(String directory) {
		List<String> caseFileList = new ArrayList<String>();
		List<Integer> SiteRunTimes = new ArrayList<Integer>();
		String[] postFixes = TestUtil.getCaseFilePostFix(directory);
		try {
			TestUtil.getCaseFiles(null, directory, caseFileList, postFixes);
		} catch (Exception e) {
			e.printStackTrace();
		}

		int caseCount = caseFileList.size();
		int answerCount = 0;
		for (int i = 0; i < caseFileList.size(); i++) {
			String caseFile = (String) caseFileList.get(i);
			String answerFile = TestUtil.getAnswerFile(caseFile);
			File file = new File(answerFile);
			if (file.exists()) {
				answerCount++;
			}
		}
		Map<String, Integer> ret = new Hashtable<String, Integer>();
		ret.put("caseCount", caseCount);
		ret.put("answerCount", answerCount);
		return ret;
	}

	/**
	 * 
	 * @param test
	 * @throws Exception
	 */
	private void buildTest(Test test) throws Exception {
		LogUtil.log(logId, "*********build cases...");
		this.onMessage("*********build cases...");
		getCaseFiles(test);
		build(test);
	}

	/**
	 * @deprecated
	 * @param test
	 * @throws Exception
	 */
	private void getCaseFiles(Test test) throws Exception {
		String[] files = test.getCases();
		ArrayList SiteRuntimeList = new ArrayList();
		int fileSize = 0;
		for (int i = 0; i < files.length; i++) {
			if (files[i] == null) {
				continue;
			}
			String file = files[i];
			String db = null;
			String filter = null;
			int position = file.toLowerCase().indexOf("?db=");
			if (position != -1) {
				file = files[i].substring(0, position);
				if(files[i].indexOf("&filter=")>=0){
					db = files[i].substring(position + "?db=".length(), files[i].indexOf("&filter="));
					filter = files[i].substring(files[i].indexOf("&filter=") + "&filter=".length());
				}else{
					db = files[i].substring(position + "?db=".length());
				}
				
				test.setDbId(db);
				test.setCaseFilter(filter);
				test.setScenarioRootPath(file);
				dao.addDb(db);
			}
			String[] postFixes = TestUtil.getCaseFilePostFix(file);
			TestUtil.getCaseFiles(test, file, test.getCaseFileList(), postFixes);
			TestUtil.filterExcludedCaseFile(test.getCaseFileList(), filter, file);
			
		}
	}
	

	/**
	 * build the test .
	 * 
	 * @param test
	 */
	private void build(Test test) {
		List<String> caseFileList = test.getCaseFileList();
		if (caseFileList == null || caseFileList.size() == 0) {
			this.onMessage("[ERROR] Please your case directory contains valid case files");
			System.exit(1);
		}

		for (int i = 0; i < caseFileList.size(); i++) {
			String caseFile = (String) caseFileList.get(i);
			CaseResult caseResult = test.getCaseResultFromMap(caseFile);
			if (caseResult != null) {
				caseFileList.remove(i--);
				continue;
			}

			int testType = TestUtil.getTestType(caseFile);
			String caseName = FileUtil.getFileName(caseFile);

			if (testType == CaseResult.TYPE_SQL) {
				this.hasSql = true;
			}

			String resultDir = TestUtil.getResultDir(test, caseFile);
			String catPath = TestUtil.getTestCatPath(caseFile, test);
			String answerFile = TestUtil.getAnswerFile(caseFile);
			boolean hasAnswer = true;
			boolean shouldRun = true;
			if (testType == CaseResult.TYPE_SQL || testType == CaseResult.TYPE_GROOVY) {
				hasAnswer = new File(answerFile).exists();
				if (!hasAnswer) {
					if (test.getRunMode() == Test.MODE_RESULT || test.getRunMode() == Test.MODE_NO_RESULT) {
						// caseFileList.remove(i--);
						// continue;
						shouldRun = false;
					}
				} else {
					if (test.getRun_mode() != null && test.getRun_mode().length() > 0) {
						String runModeExtensionAnswer = answerFile + "_" + test.getRun_mode();
						String runModeExtSecondaryAnswer = answerFile + "_" + test.getRunModeSecondary();
						boolean hasRunModeSecondaryAnswer = new File(runModeExtSecondaryAnswer).exists();
						boolean hasRunModeExtAnswer = new File(runModeExtensionAnswer).exists();
						if (hasRunModeExtAnswer) {
							answerFile = runModeExtensionAnswer;
						} else if (hasRunModeSecondaryAnswer) {
							answerFile = runModeExtSecondaryAnswer;
						}
					}
				}
			}

			// summary
			Map currentLayer = TestUtil.getCatMap(test, caseFile);
			if (!currentLayer.containsKey(TestUtil.SUMMARY_KEY)) {
				Summary summary = new Summary();
				summary.setType(Summary.TYPE_BOTTOM);
				summary.setResultDir(resultDir);
				summary.setCatPath(catPath);
				summary.setSiteRunTimes(test.getSiteRunTimes());
				currentLayer.put(TestUtil.SUMMARY_KEY, summary);
				test.putSummaryToMap(summary.getResultDir(), summary);
			}

			// case
			caseResult = new CaseResult();
			caseResult.setShouldRun(shouldRun);
			caseResult.setType(testType);
			caseResult.setCaseFile(caseFile);
			caseResult.setHasAnswer(hasAnswer);
			caseResult.setCaseName(caseName);
			caseResult.setResultDir(resultDir);
			caseResult.setCaseDir(FileUtil.getDir(caseFile));
			caseResult.setAnswerFile(answerFile);
			caseResult.setPrintQueryPlan(TestUtil.isPrintQueryPlan(caseFile));
			// caseResult.setSiteRunTimes(test.getSiteRunTimes());

			test.putCaseResultToMap(caseResult.getCaseFile(), caseResult);
			Summary summary = test.getSummaryFromMap(resultDir);
			summary.getCaseList().add(caseResult);
		}
	}

	/**
	 * run the test case .
	 * 
	 * @param test
	 */
	private void execute(Test test) {
		try {
			this.onMessage("*********execute...");
			List<String> caseFileList = test.getCaseFileList();
			processMonitor.setAllFile(test.getCaseFileList().size());
			processMonitor.setProcessName(test.getTestId());
			processMonitor.setProcessDesc(TestUtil.getResultPreDir(test.getTestId()));
			boolean endfile = false;
			int currentCount = 0;
			int totalCaseCount = caseFileList.size();
			
			for (int i = 0; i < totalCaseCount; i++) {
				if (processMonitor.getCurrentstate() == processMonitor.Status_Stoping) {
					break;
				} else if (processMonitor.getCurrentstate() == processMonitor.Status_Starting) {
					processMonitor.setCurrentstate(processMonitor.Status_Started);
				}
				endfile = i == totalCaseCount - 1;
				String caseFile = (String) caseFileList.get(i);
				currentCount++;
				NumberFormat format = NumberFormat.getPercentInstance();
				format.setMinimumFractionDigits(2);
				float ratio = (float) currentCount / totalCaseCount * 100;
				String completeRatio = format.format((ratio / 100.0));
				printMessage("Testing " + caseFile + " ("+ currentCount + "/"+ totalCaseCount +" " + completeRatio + ")", true, false);
				
				CaseResult caseResult = test.getCaseResultFromMap(caseFile);
				if (caseResult == null || !caseResult.isShouldRun()) {
					processMonitor.setCompleteFile(processMonitor.getCompleteFile() + 1);
					processMonitor.setFailedFile(processMonitor.getFailedFile() + 1);
					continue;
				}

				int testType = caseResult.getType();
				processMonitor.setCurrentfiletype(testType);
				executeSqlFile(test, caseResult);
				processMonitor.setCompleteFile(processMonitor.getCompleteFile() + 1);
				if (saveEveryone) {
					saveTempResults(caseFile);
					if (test.getType() == Test.TYPE_FUNCTION) {
						if (test.getRunMode() == Test.MODE_RESULT || test.getRunMode() == Test.MODE_NO_RESULT) {
							saveResults(caseFile);
						} else if (test.getRunMode() == Test.MODE_MAKE_ANSWER) {
							saveAnswers(caseFile);
						}
					}
					caseResult.setResult("");
				}
				
				boolean isSucc = caseResult.isSuccessFul();
				if(!isSucc){
					List<File> coreFileList = CommonFileUtile.getCoreFiles(CubridUtil.getCubridPath(), test.getAllCoreList());
					if (coreFileList != null && coreFileList.size() > 0) {
							test.putCoreCaseIntoMap(caseFile, coreFileList);
							caseResult.setHasCore(true);
					}
				}
				printMessage(isSucc?" [OK]": " [NOK]", false, true);
				if (ErrorInterruptUtil.isCaseRunError(this, caseFile)) {
					this.onMessage("[ERROR]: Run case interrupt error!");
					break;
				}
			}
			if (processMonitor.getCurrentstate() == processMonitor.Status_Stoping)
				return;
			if ((test.getType() == Test.TYPE_FUNCTION)) {
				if (saveEveryone) {
					if (test.getRunMode() != Test.MODE_RUN) {
						TestUtil.makeSummary(test, test.getCatMap());
						TestUtil.makeSummaryInfo(test, test.getCatMap(), null);
						TestUtil.saveResultSummary(test, test.getCatMap());
					}
				} else {
					saveTempResults(test);
					TestUtil.makeSummary(test, test.getCatMap());
					TestUtil.makeSummaryInfo(test, test.getCatMap(), null);
					TestUtil.saveResult(test, test.getCatMap());
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			LogUtil.log(logId, LogUtil.getExceptionMessage(e));
			processMonitor.doException();
		}
	}

	/**
	 * create the directory for test answer.
	 * 
	 * @param test
	 */
	private void createAnswerDirs(Test test) {
		List<String> caseFileList = test.getCaseFileList();
		this.onMessage("*********create answer dirs...");
		for (int i = 0; i < caseFileList.size(); i++) {
			String caseFile = (String) caseFileList.get(i);
			if (TestUtil.getTestType(caseFile) == CaseResult.TYPE_SQL || TestUtil.getTestType(caseFile) == CaseResult.TYPE_GROOVY) {
				CaseResult caseResult = (CaseResult) test.getCaseResultFromMap(caseFile);
				FileUtil.createDir(FileUtil.getDirPath(caseResult.getAnswerFile()));
			}
		}
	}

	/**
	 * create directory for test result .
	 * 
	 * @param test
	 */
	private void createResultDirs(Test test) {
		List<String> caseFileList = test.getCaseFileList();
		this.onMessage("*********create result dirs...");
		for (int i = 0; i < caseFileList.size(); i++) {
			String caseFile = (String) caseFileList.get(i);
			String resultPath = TestUtil.getResultDir(test, caseFile);
			FileUtil.createDir(resultPath);
			test.addResultDir(resultPath);
		}
	}

	/**
	 * save the temporary result .
	 * 
	 * @param test
	 */
	private void saveTempResults(Test test) {
		this.onMessage("*********save temp results...");
		List<String> caseFileList = test.getCaseFileList();
		for (int i = 0; i < caseFileList.size(); i++) {
			String caseFile = (String) caseFileList.get(i);
			saveTempResults(caseFile);
		}
	}

	/**
	 * save the temporary result.
	 * 
	 * @param caseFile
	 */
	private void saveTempResults(String caseFile) {
		CaseResult caseResult = (CaseResult) test.getCaseResultFromMap(caseFile);
		if (caseResult.getType() == CaseResult.TYPE_SQL || caseResult.getType() == CaseResult.TYPE_GROOVY) {
			String caseDir = caseResult.getCaseDir();
			String resultFile = caseDir + "/" + caseResult.getCaseName() + ".result";
			FileUtil.writeToFile(resultFile, caseResult.getResult());
		}
	}

	/**
	 * save the result file .
	 * 
	 * @param test
	 */
	private void saveResults(Test test) {
		LogUtil.log(logId, "*********save results...");
		this.onMessage("*********save results...");
		List<String> caseFileList = test.getCaseFileList();
		if (caseFileList.size() == 0) {
			return;
		}

		try {
			for (int i = 0; i < caseFileList.size(); i++) {

				String caseFile = (String) caseFileList.get(i);
				saveResults(caseFile);
				CaseResult temp_caseresult = test.getCaseResultFromMap(caseFile);
				TestUtil.saveResult(temp_caseresult, test.getCodeset());
			}
		} catch (Exception e) {
			LogUtil.log(logId, LogUtil.getExceptionMessage(e));
		}

		TestUtil.makeSummary(test, test.getCatMap());
		TestUtil.makeSummaryInfo(test, test.getCatMap(), null);
		TestUtil.saveResult(test, test.getCatMap());
	}

	/**
	 * save the result file .
	 * 
	 * 
	 * @param caseFile
	 */
	private synchronized void saveResults(String caseFile) {
		CaseResult caseResult = (CaseResult) test.getCaseResultFromMap(caseFile);
		if (!caseResult.isShouldRun()) {
			return;
		}
		if (caseResult.getType() == CaseResult.TYPE_SQL || caseResult.getType() == CaseResult.TYPE_GROOVY) {
			String result = caseResult.getResult();
			String answer = FileUtil.readFile(caseResult.getAnswerFile());
			result = result.replaceAll("\r", "").replaceAll("\n", "");
			answer = answer.replaceAll("\r", "").replaceAll("\n", "");
			TestCaseSummary fs = new TestCaseSummary();

			if (!answer.equals(result)) {
				fs.setTestResult("fail");
				caseResult.setSuccessFul(false);
				TestUtil.saveResult(caseResult, test.getCodeset());
				processMonitor.setFailedFile(processMonitor.getFailedFile() + 1);
			} else {
				processMonitor.setSuccessFile(processMonitor.getSuccessFile() + 1);
				fs.setTestResult("success");
			}

			if (test.isNeedSummaryXML()) {
				String caseFileDir = "";
				String answerFileDir = "";
				String ansFile = StringUtil.replaceSlashBasedSystem(caseResult.getAnswerFile());
				String rootPath = test.getScenarioRootPath();
				String testType = test.getTestType();

				int rootPathLength = rootPath.length();
				caseFileDir = testType + File.separator + StringUtil.replaceSlashBasedSystem(caseFile).substring(rootPathLength);
				answerFileDir = testType + File.separator + ansFile.substring(rootPathLength);

				fs.setCaseFile(StringUtil.replaceSlash(caseFileDir));
				fs.setAnswerFile(StringUtil.replaceSlash(answerFileDir));
				fs.setElapseTime(String.valueOf(caseResult.getTotalTime()));
				TestUtil.copyCaseAnswerFile(caseResult);
				FileUtil.writeDataToFileWithHandle(test.getFileHandle(), fs.toXmlString());
			}

		} else {
			int position = caseFile.indexOf(".sh");
			String resultFile = caseFile.substring(0, position) + ".result";
			boolean isOk = isScriptCaseOk(resultFile);
			if (!isOk) {
				caseResult.setSuccessFul(false);
				TestUtil.saveResult(caseResult, TestUtil.DEFAULT_CODESET);
				processMonitor.setFailedFile(processMonitor.getFailedFile() + 1);
			} else {

				processMonitor.setSuccessFile(processMonitor.getSuccessFile() + 1);
			}
		}
	}

	public void saveCoreCallStackFile(String caseFile, List<File> coreFileList){
		CaseResult caseResult = (CaseResult) test.getCaseResultFromMap(caseFile);
		String caseResultDir = caseResult.getResultDir();
		String coreFile = caseResultDir + File.separator + caseResult.getCaseName() + ".err";
		StringBuffer headerText = new StringBuffer();
		StringBuffer bodyText = new StringBuffer();
		int coreFileListSize = coreFileList == null ? 0 : coreFileList.size();
		String rootCoreBackupDir = EnvGetter.getenv("CORE_BACKUP_DIR");
		if (coreFileListSize != 0){
			headerText.append("SUMMARY:");
			headerText.append(System.getProperty("line.separator"));
			if (rootCoreBackupDir != null && rootCoreBackupDir.length() > 0) {
				headerText.append("CORE_DIR:" + rootCoreBackupDir + File.separator + test.getTestId());
				headerText.append(System.getProperty("line.separator"));
			}else{
				headerText.append("CORE_DIR:" + CubridUtil.getCubridPath());
				headerText.append(System.getProperty("line.separator"));
			}
		}
		
		for(int i=0; i<coreFileList.size();i++){
			File coreFileName = coreFileList.get(i);
			try {
				String[] callStackInfo = AnalyzerMain.fetchCoreFullStack(coreFileName);
				String coreName = coreFileName.getName();
				if(callStackInfo!=null &&callStackInfo.length>1){
					headerText.append(coreName + " [" + callStackInfo[2] + "] " + callStackInfo[0] + System.getProperty("line.separator"));
					bodyText.append(System.getProperty("line.separator") + "==================" + coreName + "==================" + System.getProperty("line.separator"));
					bodyText.append("");
					bodyText.append(callStackInfo[1]);
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		
		CommonFileUtile.writeFile(headerText.toString() + bodyText.toString(), coreFile);
		
	}
	/**
	 * save the answer file .
	 * 
	 * @param test
	 */
	private void saveAnswers(Test test) {
		this.onMessage("*********save answers...");
		List<String> caseFileList = test.getCaseFileList();
		for (int i = 0; i < caseFileList.size(); i++) {
			String caseFile = (String) caseFileList.get(i);
			saveAnswers(caseFile);
		}
	}

	/**
	 * save the answer file .
	 * 
	 * @param caseFile
	 */
	private void saveAnswers(String caseFile) {
		CaseResult caseResult = (CaseResult) test.getCaseResultFromMap(caseFile);
		if (caseResult.getType() == CaseResult.TYPE_SQL || caseResult.getType() == CaseResult.TYPE_GROOVY) {
			FileUtil.writeToFile(caseResult.getAnswerFile(), caseResult.getResult());

		}
	}

	/**
	 * determine the case file is correct or not .
	 * 
	 * @param resultFile
	 * @return
	 */
	private boolean isScriptCaseOk(String resultFile) {
		boolean isOk = true;

		BufferedReader reader = null;
		try {
			File file = new File(resultFile);
			if (!file.exists()) {
				return false;
			}

			reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
			String line = reader.readLine();
			while (line != null) {
				String[] subCaseData = line.split(":");
				if (subCaseData.length < 2 || !subCaseData[1].trim().equalsIgnoreCase("ok")) {
					return false;
				}

				line = reader.readLine();
			}
		} catch (Exception e) {
		} finally {
			if (reader != null) {
				try {
					reader.close();
				} catch (IOException e) {
				}
			}
		}

		return isOk;
	}

	// this function is in order to make sure to execute set names before do
	// each sql file testing
	public void resetConnection(CubridConnection cubCon, Test test) {
		String charset_sql = "";
		Statement stmt = null;
		ResultSet rs = null;
		Connection conn = null;
		cubCon.isAvlible();
		conn = cubCon.getConn();

		if (test.getReset_scripts() == null || (test.getReset_scripts()).length() == 0 || "".equalsIgnoreCase((test.getReset_scripts()).trim())) {
			if (test.getAutocommit() != null && test.getAutocommit().length() != 0 && conn != null && !"medium".equalsIgnoreCase(test.getTestType())) {
				try {
					conn.setAutoCommit(Boolean.valueOf(test.getAutocommit()));
					resetHoldCas(conn);
				} catch (SQLException e) {
					String exceptionMessage = TestUtil.TEST_CONFIG + " file:" + test.getCharset_file() + System.getProperty("line.separator");
					this.onMessage(exceptionMessage);
					e.printStackTrace();
				}
				return;
			} else {
				return;
			}
		}

		charset_sql = test.getReset_scripts() + TestUtil.SQL_END;
		try {
			if (test.getAutocommit() != null) {
				conn.setAutoCommit(Boolean.valueOf(test.getAutocommit()));
			}

			// reset hold cas
			resetHoldCas(conn);
			stmt = conn.createStatement();
			stmt.execute(charset_sql);
		} catch (SQLException e) {
			String exceptionMessage = TestUtil.TEST_CONFIG + " file:" + test.getCharset_file() + System.getProperty("line.separator");
			String msg = "Set System Parameter Error: ";
			this.onMessage(msg + exceptionMessage);
		} finally {
			if (rs != null) {
				try {
					rs.close();
				} catch (SQLException e) {
					// TODO Auto-generated catch block
					this.onMessage(e.getMessage());
				}
			}
			if (stmt != null) {
				try {
					stmt.close();
				} catch (SQLException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
	}

	private void resetHoldCas(Connection conn) {
		String holdcas = test.getHoldcas();
		try {
			if ("on".equalsIgnoreCase(holdcas)) {
				Method method = conn.getClass().getMethod("setCASChangeMode", new Class[] { int.class });
				method.invoke(conn, new Object[] { CUBRIDConnection.CAS_CHANGE_MODE_KEEP });
			} else if ("off".equalsIgnoreCase(holdcas)) {
				Method method = conn.getClass().getMethod("setCASChangeMode", new Class[] { int.class });
				method.invoke(conn, new Object[] { CUBRIDConnection.CAS_CHANGE_MODE_AUTO });
			}
		} catch (Exception e) {
			String message = "Exception: the current version can't support hold cas!";
			this.onMessage(message);
		}
	}

	public void checkServerStatus(CubridConnection cubCon) {
		int timer = 0;
		while (true) {
			// timeout length is 180 seconds, if server will not alive after 180
			// second, test will go on
			if (isAliveForServerStatus(cubCon) || timer >= 180) {
				String message = "";
				if (timer >= 180) {
					message = "Server still cann't recovery dead, so timeout was execute!";
				} else {
					message = "Server was recoveryed at " + timer + "seconds!";
				}

				this.onMessage(message);
				break;
			} else {
				// sleep 5 seconds to check server status again
				try {
					Thread.sleep(5000);
				} catch (InterruptedException e) {
					this.onMessage(e.getMessage());
				}
				timer += 5;
				String message = "Server status is not alive, CQT will try to check it again after  " + timer + " seconds!!";
				this.onMessage(message);
			}
		}
	}

	public boolean isAliveForServerStatus(CubridConnection cubCon) {
		boolean status = false;
		String sql = "SELECT 1;";
		Statement stmt = null;
		ResultSet rs = null;
		Connection conn = null;
		int value = 0;
		cubCon.isAvlible();
		conn = cubCon.getConn();

		try {
			stmt = conn.createStatement();
			rs = stmt.executeQuery(sql);
			while (rs.next()) {
				value = rs.getInt(1);
				if (value == 1) {
					return true;
				}
			}

		} catch (SQLException e) {
			e.printStackTrace();
		} finally {
			if (rs != null) {
				try {
					rs.close();
				} catch (SQLException e) {
					e.printStackTrace();
				}
			}

			if (stmt != null) {
				try {
					stmt.close();
				} catch (SQLException e) {
					e.printStackTrace();
				}
			}

		}

		return status;
	}

	/**
	 * execute the sql test .
	 * 
	 * @param test
	 * @param caseResult
	 * @return
	 */
	private String executeSqlFile(Test test, CaseResult caseResult) {
		ArrayList<Connection> ConnList = new ArrayList<Connection>();
		if (test == null || caseResult == null) {
			return null;
		}
		if (processMonitor.getCurrentstate() == processMonitor.Status_Stoping) {
			return null;
		}

		String caseFile = caseResult.getCaseFile();
		StringBuilder result = new StringBuilder();
		List<Sql> sqlList = parseSqlFile(caseFile, test);
		if (sqlList == null) {
			return null;
		}

		// clear connection id list for each sql file
		if (!test.getConnIDList().isEmpty()) {
			test.getConnIDList().clear();
		}

		for (int i = 0; i < test.getSqlRunTime(); i++) {
			this.onMessage((new StringBuilder()).append("Now Running File  ..... \t").append(caseFile).toString());
			if (processMonitor.getCurrentstate() == processMonitor.Status_Stoping) {
				return null;
			}
			try {
				test.setDbId(test.getDbId(caseFile));

				String dbId = test.getDbId();
				String connId = test.getConnId();
				CubridConnection cubridConnection = dao.getCubridConnection(dbId, connId, test.getType());

				test.getConnIDList().put(connId, cubridConnection);

				// check if CQT need check server status when start each file
				// test executing
				if (test.isNeedCheckServerStatus()) {
					checkServerStatus(cubridConnection);
				}

				// set db charset for test
				resetConnection(cubridConnection, test);

				long startTime = System.currentTimeMillis();
				for (int k = 0; k < sqlList.size(); k++) {
					if (processMonitor.getCurrentstate() == processMonitor.Status_Stoping) {
						return null;
					}
					Sql sql = (Sql) sqlList.get(k);
					if (sql == null) {
						continue;
					}

					// isQueryPlan from two ways, one is "--@queryplan" in sql
					// file, another is "XXX.queryPlan" file whose name is same
					// as sql file

					String thisConnId = sql.getConnId();
					if (!thisConnId.equals("") && !thisConnId.equals(connId)) {
						cubridConnection = dao.getCubridConnection(dbId, thisConnId, test.getType());
						if (!test.getConnIDList().containsKey(thisConnId)) {
							test.getConnIDList().put(thisConnId, cubridConnection);
							// set connection reset for test
							resetConnection(cubridConnection, test);
						}
					}

					String script = sql.getScript();
					cubridConnection.isAvlible();
					Connection conn = cubridConnection.getConn();
					ConnList.add(conn);

					// ===============2010-10-20==========================
					// int pos = script.indexOf("autocommit ");
					// if (pos != -1) {
					// String onOff = script.substring(pos
					// + "autocommit ".length());
					// conn.setAutoCommit(onOff.indexOf("on") != -1);
					// String message = "@" + test.getConnId()
					// + ": autocommit " + onOff;
					// this.onMessage(message);
					// } else {
					// String message = "@" + test.getDbId() + "/"
					// + test.getConnId() + ":" + sql.getScript();
					// this.onMessage(message);
					//
					// dao.execute(conn, sql, caseResult.isPrintQueryPlan());
					//
					// if (i == 0) {
					// result
					// .append("===================================================");
					// result.append(System.getProperty("line.separator"));
					//
					// result.append(sql.getResult());
					// }
					// }
					// ===============2010-10-20==========================

					// ===============replace above=======================
					boolean isOn = isSetAutoCommitOn(script);
					boolean isOff = isSetAutoCommitOff(script);
					boolean isHoldCasOn = isHoldCASON(script);
					boolean isHoldCasOff = isHoldCASOFF(script);

					if (isOn) {
						conn.setAutoCommit(isOn);
						String message = "@" + test.getConnId() + ": autocommit " + isOn;
						this.onMessage(message);
					} else if (isOff) {
						conn.setAutoCommit(!isOff);
						String message = "@" + test.getConnId() + ": autocommit " + !isOff;
						this.onMessage(message);
					} else if (isHoldCasOn) {
						try {
							String message = "@" + test.getConnId() + ": hold cas " + isHoldCasOn;

							Method method = conn.getClass().getMethod("setCASChangeMode", new Class[] { int.class });
							method.invoke(conn, new Object[] { CUBRIDConnection.CAS_CHANGE_MODE_KEEP });

							this.onMessage(message);
						} catch (Exception e) {
							String message = "Exception: the current version can't support hold cas!";
							this.onMessage(message);
						}
					} else if (isHoldCasOff) {
						try {
							String message = "@" + test.getConnId() + ": hold cas " + isHoldCasOff;
							Method method = conn.getClass().getMethod("setCASChangeMode", new Class[] { int.class });
							method.invoke(conn, new Object[] { CUBRIDConnection.CAS_CHANGE_MODE_AUTO });
							this.onMessage(message);
						} catch (Exception e) {
							String message = "Exception: the current version can't support hold cas!";
							this.onMessage(message);
						}
					} else {
						String message = "@" + test.getDbId() + "/" + test.getConnId() + ":" + sql.getScript();
						this.onMessage(message);
						dao.execute(conn, sql, caseResult.isPrintQueryPlan());

						if (i == 0) {
							result.append("===================================================");
							result.append(System.getProperty("line.separator"));

							result.append(sql.getResult());
						}
					}

					cubridConnection.free();
				}

				long totalTime = System.currentTimeMillis() - startTime;
				caseResult.setTotalTime(totalTime);
				caseResult.setSiteRunTimes(1);
			} catch (Exception e) {
				if (i == 0) {
					result.append(e.getMessage() + System.getProperty("line.separator"));
				}
				this.onMessage(e.getMessage());
			} finally {
				if (i == 0) {
					caseResult.setResult(result.toString());
					caseResult.setSiteRunTimes(1);
				}
			}

			Statement stmt;
			ResultSet rs;
			int charset;
			String sql;

			for (Connection conn1 : ConnList) {
				try {
					conn1.commit();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}

			if (test.isQaview()) {
				for (Connection conn : ConnList) {
					try {

						if (conn.getAutoCommit() == false)
							this.onMessage("[QA REVIEW] FOGOT TO RESET AUTOCOMMIT" + " (" + caseFile + ")");

						conn.setAutoCommit(true);

						stmt = conn.createStatement();

						sql = "select class_name,class_type from db_class where is_system_class<>'YES'";
						rs = stmt.executeQuery(sql);
						while (rs.next()) {
							this.onMessage("[QA REVIEW] FOGOT TO DELETE " + rs.getString(1) + " with " + rs.getString(2) + " type" + " (" + caseFile + ")");
						}
						rs.close();

						sql = "select name from db_serial";
						rs = stmt.executeQuery(sql);
						while (rs.next()) {
							this.onMessage("[QA REVIEW] FOGOT TO DELETE serial " + rs.getString(1) + " (" + caseFile + ")");
						}
						rs.close();

						sql = "select name from db_trigger";
						rs = stmt.executeQuery(sql);
						while (rs.next()) {
							this.onMessage("[QA REVIEW] FOGOT TO DELETE trigger " + rs.getString(1) + " (" + caseFile + ")");
						}
						rs.close();

						sql = "select name from db_user where name not in ('DBA', 'PUBLIC');";
						rs = stmt.executeQuery(sql);
						while (rs.next()) {
							this.onMessage("[QA REVIEW] FOGOT TO DELETE user " + rs.getString(1) + " (" + caseFile + ")");
						}
						rs.close();

						sql = "select sp_name from db_stored_procedure";
						rs = stmt.executeQuery(sql);
						while (rs.next()) {
							this.onMessage("[QA REVIEW] FOGOT TO DELETE SP " + rs.getString(1) + " (" + caseFile + ")");
						}
						rs.close();

						sql = "select USER";
						rs = stmt.executeQuery(sql);
						String user;
						while (rs.next()) {
							user = rs.getString(1);
							if (!user.equalsIgnoreCase("DBA")) {
								this.onMessage("[QA REVIEW] FOGOT TO RECOVERY USER: " + user + " (" + caseFile + ")");
							}
						}
						rs.close();

						rs = stmt.executeQuery("SELECT charset from db_root");
						rs.next();
						charset = rs.getInt(1);
						rs.close();

						String collation;
						sql = "select collation('a')";
						try {
							rs = stmt.executeQuery(sql);
							rs.next();
							collation = rs.getString(1);
						} catch (Exception e) {
							if (rs != null)
								rs.close();
							if (stmt != null)
								stmt.close();
							continue;
						}

						if (charset == 4) {
							if (!collation.equalsIgnoreCase("euckr_bin")) {
								this.onMessage("[QA REVIEW] FOGOT TO RECOVERY collation: " + collation + " (" + caseFile + ")");
							}
						} else if (charset == 5) {
							if (!collation.equalsIgnoreCase("utf8_bin")) {
								this.onMessage("[QA REVIEW] FOGOT TO RECOVERY collation: " + collation + " (" + caseFile + ")");
							}
						} else if (charset == 3) {
							if (!collation.equalsIgnoreCase("iso88591_bin")) {
								this.onMessage("[QA REVIEW] FOGOT TO RECOVERY collation: " + collation + " (" + caseFile + ")");
							}
						} else {
							if (!collation.equalsIgnoreCase("iso88591_bin")) {
								this.onMessage("[QA REVIEW] FOGOT TO RECOVERY collation: " + collation + " (" + caseFile + ")");
							}
						}

						if (charset == 4) {
							stmt.executeUpdate("SET NAMES euckr collate euckr_bin");
						} else if (charset == 5) {
							stmt.executeUpdate("SET NAMES utf8 collate utf8_bin");
						} else if (charset == 3) {
							stmt.executeUpdate("SET NAMES iso88591 collate iso88591_bin");
						} else {
							stmt.executeUpdate("SET NAMES iso88591 collate iso88591_bin");
						}

						stmt.close();

					} catch (SQLException e) {
						this.onMessage(e.getMessage());
					}
				}
			}

		}
		return caseResult.toString();
	}

	public boolean isHoldCasStatement(String sql) {
		boolean ret = false;
		String str = "";
		if (sql.startsWith("--+")) {
			str = sql.trim().replaceAll(" ", "");
			if ("--+holdcas".equalsIgnoreCase(str.substring(0, "--+holdcas".length()))) {
				ret = true;
			}
		}

		return ret;
	}

	/**
	 * parse the sql script file .
	 * 
	 * @param sqlFile
	 * @return
	 */
	private List<Sql> parseSqlFile(String sqlFile, Test test) {
		List<Sql> list = new ArrayList<Sql>();
		BufferedReader reader = null;
		try {
			// reader = new BufferedReader(new InputStreamReader(
			// new FileInputStream(new File(sqlFile)), "UTF-8"));
			reader = new BufferedReader(new InputStreamReader(new FileInputStream(new File(sqlFile)), test.getCodeset()));
			String line = reader.readLine();
			int lineCount = 1;
			StringBuilder ret = new StringBuilder();
			List<SqlParam> paramList = null;
			String connId = "";
			boolean isCall = false;
			boolean isNewStatement = true;
			boolean isQueryplan = false;
			boolean isHoldCas = false;
			while (line != null) {
				line = line.trim();

				if ("".equals(line) || line.startsWith("--")) {
					// When this line is "--@queryplan", the next line should
					// show
					// query plan
					isHoldCas = isHoldCasStatement(line);
					if ("--@queryplan".equals(line.trim())) {
						isQueryplan = true;
					} else if (isHoldCas) {
						line = line.replaceFirst("--\\+", "").trim();
						ret.append(line + "" + System.getProperty("line.separator"));
						Sql sql = new Sql(connId, ret.toString(), paramList, isCall);
						sql.setQueryplan(isQueryplan);
						list.add(sql);

						isNewStatement = true;
						isQueryplan = false;
						ret = new StringBuilder();
						paramList = null;
						isCall = false;
						connId = "";
					}
					
					line = reader.readLine();
					lineCount++;
					continue;
				}
				
				boolean isStatement = !(line.startsWith("$"));
				if (!isStatement) { // parameters
					paramList = new ArrayList<SqlParam>();

					if (line.endsWith(";")) {
						line = line.substring(0, line.length() - 1);
					}

					String[] parts = line.split("\\,( )*\\$");
					if (parts.length % 2 != 0) {
						if (line.endsWith(",$")) {
							String[] temp = new String[parts.length + 1];
							System.arraycopy(parts, 0, temp, 0, parts.length);
							temp[temp.length - 1] = "";
							parts = temp;
						} else {
							throw new Exception("parameters is invalid in sqlFile:" + sqlFile);
						}
					}

					int index = 1;
					String paramType = null;
					String typeName = null;
					int type = Types.VARCHAR;
					for (int i = 0; i < parts.length; i++) {
						String value = parts[i];
						if (i % 2 == 0) { // type
							typeName = value.trim().toUpperCase();
							if (typeName.startsWith("$")) {
								typeName = typeName.substring(1);
							}
							int position = typeName.indexOf(":");
							if (position != -1) {
								paramType = typeName.substring(0, position);
								typeName = typeName.substring(position + 1);
							} else {
								paramType = null;
							}
							type = CubridUtil.getSqlType(typeName);
						} else { // value
							if (value.startsWith("$")) {
								value = value.substring(1);
							}
							Object param = null;
							param = CubridUtil.getObject(type, value);
							SqlParam sqlParam = new SqlParam(paramType, index, param, type);
							paramList.add(sqlParam);

							index++;
						}
					}
				} else { // statement
					int pos = line.indexOf(":");
					if (line.endsWith(";")) {
						String lineCopy = line;
						if (lineCopy.startsWith("@") && pos != -1) {
							lineCopy = lineCopy.substring(pos);
						}
						int positionCall = lineCopy.replaceAll(" ", "").indexOf("call");
						if (positionCall != -1) {
							if (positionCall == 0) {
								int position1 = line.indexOf("(");
								int position2 = line.indexOf(")");
								if (position1 != -1 && position2 != -1) {
									String params = line.substring(position1, position2);
									if (params.indexOf("?") != -1) {
										isCall = true;
									} else {
										isCall = false;
									}
								} else {
									isCall = false;
								}
							} else if (lineCopy.replaceAll(" ", "").indexOf("?=call") == 0) {
								isCall = true;
							}
						}
					}

					if (isNewStatement) {
						if (line.startsWith("@")) {
							connId = line.substring(1, pos);
							line = line.substring(pos + 1);
						}
					}

					// set sql hint for debug
					if (test.isNeedDebugHint()) {
						String tmpSQL = line;
						String hint_sql = addHintForSQL(tmpSQL, isNewStatement, lineCount, sqlFile);

						ret.append(hint_sql + "\n");
					} else {
						ret.append(line + "\n");
					}

					if (!isCall) {
						if (line.endsWith(";")) {
							Sql sql = new Sql(connId, ret.toString(), paramList, isCall);
							sql.setQueryplan(isQueryplan);
							list.add(sql);
						}
					} else {
						Sql sql = new Sql(connId, ret.toString(), paramList, isCall);
						sql.setQueryplan(isQueryplan);
						list.add(sql);
					}
				}

				if (isStatement && line.endsWith(";")) {
					isNewStatement = true;
					isQueryplan = false;

					ret = new StringBuilder();
					paramList = null;
					isCall = false;
					connId = "";
				} else {
					isNewStatement = false;
				}

				line = reader.readLine();
				lineCount++;
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (reader != null) {
				try {
					reader.close();
				} catch (IOException e) {
				}
			}
		}
		return list;
	}

	private boolean hasHint(String sql) {
		boolean ret = false;
		String copySQL = sql;
		int hasCompileKey = -1;
		int hasSlashAndStar = -1;
		if (copySQL != null && copySQL.length() != 0) {
			hasCompileKey = copySQL.toUpperCase().indexOf("RECOMPILE");
			hasSlashAndStar = copySQL.indexOf("/*");
		}

		if (hasCompileKey != -1 && hasSlashAndStar != -1) {
			ret = true;
		}

		return ret;
	}

	private String reConstructSQLForHint(String origSQL, String prefSQL, String suffSQL, String sqlFile, int lineNum, boolean hasHint) {
		String ret = "";

		if (hasHint) {
			int start_pos = origSQL.indexOf("*/");
			String sub_Prefix = origSQL.substring(0, start_pos);
			String sub_Suff = origSQL.substring(start_pos, origSQL.length());
			ret = sub_Prefix + TestUtil.APPEND_HINT_PREFIX + sqlFile + ":" + lineNum + TestUtil.APPEND_HINT_SUFFIX + " " + sub_Suff;
		} else {
			ret = prefSQL + " " + TestUtil.HINT_PREFIX + sqlFile + ":" + lineNum + TestUtil.HINT_SUFFIX + " " + suffSQL;
		}

		return ret;
	}

	// add hint for CREATE/ALTER/INSERT/UPDATE/DELETE/DROP/MERGE/PREPARE/REPLACE
	// sql
	private String addHintForSQL(String sql, boolean isNewLine, int lineNum, String sqlFile) {
		String ret = "";
		String prefix_tmpStr = "";
		String prefix_sql = "";
		String suff_sql = "";
		int prefix_pos = -1;
		boolean hasHint = false;
		String copy_SQL = sql;
		String tmpStr = sql;
		String script = sql.toLowerCase();
		tmpStr = tmpStr.replaceAll(" ", "");
		if (tmpStr.length() >= 4) {
			prefix_tmpStr = tmpStr.substring(0, 4);

		} else {
			return sql;
		}

		if (isNewLine) {
			if ("CREA".equalsIgnoreCase(prefix_tmpStr)) {
				prefix_pos = script.indexOf("create") + 6;
				prefix_sql = copy_SQL.substring(0, prefix_pos);
				suff_sql = copy_SQL.substring(prefix_pos, copy_SQL.length());
				hasHint = hasHint(copy_SQL);
				ret = reConstructSQLForHint(copy_SQL, prefix_sql, suff_sql, sqlFile, lineNum, hasHint);

			} else if ("ALTE".equalsIgnoreCase(prefix_tmpStr)) {
				prefix_pos = script.indexOf("alter") + 5;
				prefix_sql = copy_SQL.substring(0, prefix_pos);
				suff_sql = copy_SQL.substring(prefix_pos, copy_SQL.length());
				hasHint = hasHint(copy_SQL);
				ret = reConstructSQLForHint(copy_SQL, prefix_sql, suff_sql, sqlFile, lineNum, hasHint);

				// }else if("DROP".equalsIgnoreCase(prefix_tmpStr))
				// {
				// prefix_pos = script.indexOf("drop") + 4;
				// prefix_sql = copy_SQL.substring(0, prefix_pos);
				// suff_sql = copy_SQL.substring(prefix_pos, copy_SQL.length());
				// hasHint = hasHint(copy_SQL);
				// ret = reConstructSQLForHint(copy_SQL, prefix_sql, suff_sql,
				// sqlFile, lineNum, hasHint);
				//
			} else if ("UPDA".equalsIgnoreCase(prefix_tmpStr)) {
				prefix_pos = script.indexOf("update") + 6;
				prefix_sql = copy_SQL.substring(0, prefix_pos);
				suff_sql = copy_SQL.substring(prefix_pos, copy_SQL.length());
				hasHint = hasHint(copy_SQL);
				ret = reConstructSQLForHint(copy_SQL, prefix_sql, suff_sql, sqlFile, lineNum, hasHint);

			} else if ("SELE".equalsIgnoreCase(prefix_tmpStr)) {
				prefix_pos = script.indexOf("select") + 6;
				prefix_sql = copy_SQL.substring(0, prefix_pos);
				suff_sql = copy_SQL.substring(prefix_pos, copy_SQL.length());
				hasHint = hasHint(copy_SQL);
				ret = reConstructSQLForHint(copy_SQL, prefix_sql, suff_sql, sqlFile, lineNum, hasHint);

			} else if ("DELE".equalsIgnoreCase(prefix_tmpStr)) {
				prefix_pos = script.indexOf("delete") + 6;
				prefix_sql = copy_SQL.substring(0, prefix_pos);
				suff_sql = copy_SQL.substring(prefix_pos, copy_SQL.length());
				hasHint = hasHint(copy_SQL);
				ret = reConstructSQLForHint(copy_SQL, prefix_sql, suff_sql, sqlFile, lineNum, hasHint);

			} else if ("MERG".equalsIgnoreCase(prefix_tmpStr)) {
				prefix_pos = script.indexOf("merge") + 5;
				prefix_sql = copy_SQL.substring(0, prefix_pos);
				suff_sql = copy_SQL.substring(prefix_pos, copy_SQL.length());
				hasHint = hasHint(copy_SQL);
				ret = reConstructSQLForHint(copy_SQL, prefix_sql, suff_sql, sqlFile, lineNum, hasHint);
			} else if ("INSE".equalsIgnoreCase(prefix_tmpStr)) {
				prefix_pos = script.indexOf("insert") + 6;
				prefix_sql = copy_SQL.substring(0, prefix_pos);
				suff_sql = copy_SQL.substring(prefix_pos, copy_SQL.length());
				hasHint = hasHint(copy_SQL);
				ret = reConstructSQLForHint(copy_SQL, prefix_sql, suff_sql, sqlFile, lineNum, hasHint);
			} else if ("PREP".equalsIgnoreCase(prefix_tmpStr)) {
				prefix_pos = script.indexOf("select") + 6;
				prefix_sql = copy_SQL.substring(0, prefix_pos);
				suff_sql = copy_SQL.substring(prefix_pos, copy_SQL.length());
				hasHint = hasHint(copy_SQL);
				ret = reConstructSQLForHint(copy_SQL, prefix_sql, suff_sql, sqlFile, lineNum, hasHint);
			} else if ("REPL".equalsIgnoreCase(prefix_tmpStr)) {
				prefix_pos = script.indexOf("replace") + 7;
				prefix_sql = copy_SQL.substring(0, prefix_pos);
				suff_sql = copy_SQL.substring(prefix_pos, copy_SQL.length());
				hasHint = hasHint(copy_SQL);
				ret = reConstructSQLForHint(copy_SQL, prefix_sql, suff_sql, sqlFile, lineNum, hasHint);
			} else {
				ret = sql;
			}

		} else {
			ret = sql;
		}

		return ret;
	}

	/**
	 * determine if Database is normal.
	 * 
	 * @param test
	 * @return
	 */
	@SuppressWarnings("deprecation")
	private boolean checkDb(Test test) {
		boolean flag = true;
		if (hasSql && !dao.isDbOk()) {
			flag = false;
		}
		return flag;
	}

	/**
	 * 
	 */
	protected void init() {
		long startTime = System.currentTimeMillis();
		startTime = System.currentTimeMillis();
		String dbVersion = configureUtil.getProperty("dbversion");
		String dbBuild = configureUtil.getProperty("dbbuildnumber");
		test.setDbVersion(dbVersion);
		test.setDbBuild(dbBuild);
		LogUtil.log(logId, "[time]getProperties:" + (System.currentTimeMillis() - startTime));

		startTime = System.currentTimeMillis();
	}

	public ProcessMonitor getProcessMonitor() {
		return processMonitor;
	}

	/**
	 * this inner class created by anson cheung,it used for execute sql
	 * muti-thread
	 * 
	 * @author Administrator
	 * 
	 */
	class MutiSqlEx extends Thread {
		private Test test;
		private int index;

		private CaseResult caseResult;

		public void setTest(Test test) {
			this.test = test;
		}

		public void setCaseResult(CaseResult caseResult) {
			this.caseResult = caseResult;
		}

		public CaseResult getCaseResult() {
			return caseResult;
		}

		private ConsoleBO bo;

		public void setBo(ConsoleBO bo) {
			this.bo = bo;
		}

		public MutiSqlEx(String name) {
			super(name);
		}

		@Override
		public void run() {
			List<Connection> ConnList = new ArrayList<Connection>();
			if (test == null || caseResult == null || processMonitor.getCurrentstate() == processMonitor.Status_Stoping || processMonitor.getCurrentstate() == processMonitor.Status_Stoping) {
				return;
			}
			String caseFile = caseResult.getCaseFile();
			StringBuilder result = new StringBuilder();
			List<Sql> sqlList = parseSqlFile(caseFile, test);

			String dbId = test.getDbId();
			String connId = test.getConnId();
			CubridConnection cubridConnection = dao.getCubridConnection(dbId, connId, test.getType());

			// check if CQT need check server status when start each file test
			// executing
			if (test.isNeedCheckServerStatus()) {
				checkServerStatus(cubridConnection);
			}

			// set db charset for test
			resetConnection(cubridConnection, test);

			if (sqlList == null) {
				return;
			}
			try {
				test.setDbId(test.getDbId(caseFile));
				long startTime = System.currentTimeMillis();
				for (int k = 0; k < sqlList.size(); k++) {
					if (processMonitor.getCurrentstate() == processMonitor.Status_Stoping || processMonitor.getCurrentstate() == processMonitor.Status_Stoping) {
						return;
					}
					Sql sql = (Sql) sqlList.get(k);
					if (sql == null) {
						continue;
					}

					String script = sql.getScript();
					// String dbId = test.getDbId();
					// String connId = test.getConnId();
					String ThreadConnId = connId;
					String thisConnId = sql.getConnId();
					if (!thisConnId.equals("") && !thisConnId.equals(connId)) {
						ThreadConnId = thisConnId;
						cubridConnection = dao.getCubridConnection(dbId, ThreadConnId, index, test.getType());
						resetConnection(cubridConnection, test);
					}

					cubridConnection.isAvlible();
					Connection conn = cubridConnection.getConn();
					ConnList.add(conn);
					boolean isOn = isSetAutoCommitOn(script);
					boolean isOff = isSetAutoCommitOff(script);
					boolean isHoldCasOn = isHoldCASON(script);
					boolean isHoldCasOff = isHoldCASOFF(script);

					if (isOn) {
						conn.setAutoCommit(isOn);
						String message = "@" + test.getConnId() + ": autocommit " + isOn;
						bo.onMessage("[THREAD:" + index + "]" + message);
					} else if (isOff) {
						conn.setAutoCommit(!isOff);
						String message = "@" + test.getConnId() + ": autocommit " + !isOff;
						bo.onMessage("[THREAD:" + index + "]" + message);
					} else if (isHoldCasOn) {
						try {
							String message = "@" + test.getConnId() + ": hold cas " + isHoldCasOn;

							Method method = conn.getClass().getMethod("setCASChangeMode", new Class[] { int.class });
							method.invoke(conn, new Object[] { CUBRIDConnection.CAS_CHANGE_MODE_KEEP });

							bo.onMessage("--- hold cas -- " + isHoldCasOn);
						} catch (Exception e) {
							String message = "Exception: the current version can't support hold cas!";
							bo.onMessage(message);
						}
					} else if (isHoldCasOff) {
						try {
							String message = "@" + test.getConnId() + ": hold cas " + isHoldCasOff;
							Method method = conn.getClass().getMethod("setCASChangeMode", new Class[] { int.class });
							method.invoke(conn, new Object[] { CUBRIDConnection.CAS_CHANGE_MODE_AUTO });
							bo.onMessage("--- hold cas -- " + isHoldCasOff);
						} catch (Exception e) {
							String message = "Exception: the current version can't support hold cas!";
							bo.onMessage(message);
						}
					} else {
						//System.out.println("--- use default autocommit -- " + conn.getAutoCommit());
						String message = "@" + test.getDbId() + "/" + test.getConnId() + ":" + sql.getScript();
						bo.onMessage("[THREAD:" + index + "]" + message);

						dao.execute(conn, sql, caseResult.isPrintQueryPlan());
						result.append("===================================================");
						result.append(System.getProperty("line.separator"));
						result.append(sql.getResult());
					}

					cubridConnection.free();
				}
				long totalTime = System.currentTimeMillis() - startTime;
				caseResult.setTotalTime(totalTime);

			} catch (Exception e) {

				result.append(e.getMessage() + System.getProperty("line.separator"));

				e.printStackTrace();
			} finally {
				caseResult.setResult(result.toString());
				caseResult.setSiteRunTimes(test.getSiteRunTimes());

			}
			for (Connection conn : ConnList) {
				try {
					conn.commit();
				} catch (SQLException e) {
					e.printStackTrace();
				}
			}
		}

		public int getIndex() {
			return index;
		}

		public void setIndex(int index) {
			this.index = index;
		}

	}

	public Test getTest() {
		return test;
	}

	private boolean isHoldCASON(String sql) {
		String tmp = sql.trim();
		String regex = "holdcas\\s+on\\s*;";
		Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
		Matcher matcher = pattern.matcher(tmp);
		return matcher.find();
	}

	private boolean isHoldCASOFF(String sql) {
		String tmp = sql.trim();
		String regex = "holdcas\\s+off\\s*;";
		Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
		Matcher matcher = pattern.matcher(tmp);
		return matcher.find();
	}

	private boolean isSetAutoCommitOn(String s) {
		// String s = " autocommit on ; ";
		String tmp = s.trim();
		String regex = "autocommit\\s+on\\s*;";
		Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
		Matcher matcher = pattern.matcher(tmp);
		return matcher.find();
	}

	private boolean isSetAutoCommitOff(String s) {
		// String s = " autocommit on ; ";
		String tmp = s.trim();
		String regex = "autocommit\\s+off\\s*;";
		Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
		Matcher matcher = pattern.matcher(tmp);
		return matcher.find();
	}

}
