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
package com.navercorp.cubridqa.cqt.console.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.StringTokenizer;

import com.navercorp.cubridqa.cqt.console.bean.CaseResult;
import com.navercorp.cubridqa.cqt.console.bean.Summary;
import com.navercorp.cubridqa.cqt.console.bean.SummaryInfo;
import com.navercorp.cubridqa.cqt.console.bean.SystemModel;
import com.navercorp.cubridqa.cqt.console.bean.Test;
import com.navercorp.cubridqa.cqt.console.util.EnvGetter;
import com.navercorp.cubridqa.cqt.console.util.PropertiesUtil;
import com.navercorp.cubridqa.cqt.console.util.XstreamHelper;


public class TestUtil {

	public static Map<String, String[]> SCENARIOMAP;

	public static final String SCENARIO = "/scenario/";

	public static final String RESULT = "/sql/result/";

	public static final String CASEFLAG = "cases";

	public static final String OTHER_ANSWERS = "answers";

	public static String OTHER_ANSWERS_32 = "answers32";

	public static String DEFAULT_CODESET = "UTF-8";

	public static final String CONFIG_NAME = "sql/configuration";

	public static final String TEST_CONFIG = "test_config";

	public static final String RUN_MODE = "run_mode";
	
	public static final String RUN_MODE_SECONDARY = "run_mode_secondary";
	
	public static final String HOLDCAS = "holdcas";

	public static final String AUTOCOMMIT = "autocommit";
	
	public static final String CHECK_SERVER_STATUS = "check_server_status";
	
	public static final String URL_PROPERTIES = "url_properties";
	
	public static final String ADD_DEBUG_HINT = "add_debug_hint";
	
	public static final String NEED_XML_SUMMARY = "need_xml_summary";
	
	public static final String NEED_ANSWER_In_Summary = "need_answer_in_summary";
	
	public static final String HINT_PREFIX = "/*+ RECOMPILE QASRC(";
	
	public static final String APPEND_HINT_PREFIX = " QASRC(";
	
	public static final String APPEND_HINT_SUFFIX = " ) ";
	
	public static final String HINT_SUFFIX = ") */";

	public static final String RESET = "reset";

	public static final String ROOT_NODE = "/test/";

	public static final String SQL_END = ";";

	public static final String TOTAL_SUMMARY_FILE = "/main.info";
	
	public static final String SUMMARYLIST_FILE = "/summary.xml";
	
	public static final String ScenarioTypes = ".sql";

	public static String getAnswer4SQLAndOther(String filePath) {
		String filename = FileUtil.getDir(StringUtil.replaceSlashBasedSystem(filePath));
		if (filename.endsWith(File.separator + CASEFLAG)) {
			filename = filename.replaceAll("\\\\", "/").replaceAll("/cases",
					"/" + TestUtil.OTHER_ANSWERS_32);
		}

		if (FileUtil.isFileExist(filename)) {
			return OTHER_ANSWERS_32;
		} else {
			return OTHER_ANSWERS;
		}
	}

	public static String getCharsetFile(String fileName) {
		String fName = "";
		fName = EnvGetter.getenv("CTP_HOME") + File.separator
				+ CONFIG_NAME + File.separator + TEST_CONFIG + File.separator
				+ fileName;
		return fName;
	}

	public static final String SUMMARY_KEY = "Summary";

	static {
		SCENARIOMAP = new Hashtable<String, String[]>();

		String scenarionTypes = PropertiesUtil.getValue("scenariontypes");
		if (scenarionTypes != null) {
			String[] types = scenarionTypes.split(";");
			for (int i = 0; i < types.length; i++) {
				String type = types[i];
				if (type == null) {
					continue;
				}
				type = type.trim();
				int position = type.indexOf("(");
				if (position > 0) {
					String dirName = type.substring(0, position);
					String fileType = type.substring(position);

					fileType = fileType.replaceAll("\\(", "").replaceAll("\\)",
							"");
					String[] postfixes = fileType.split(",");
					SCENARIOMAP.put(dirName, postfixes);
				}
			}
		}
	}

	/**
	 * get the testid through type and name .
	 * 
	 * @param type
	 * @param name
	 * @return
	 */
	public static String getTestId(String type, String name) {
		StringBuilder ret = new StringBuilder();

		if (type == null || type.trim().equals("")) {
			type = "common";
		}
		ret.append(type);
		

		String os = SystemUtil.getOS();
		ret.append("_" + os);

		if (name != null && !name.trim().equals("")) {
			ret.append("_" + name);
		}

		String date = "";
//		long s = System.currentTimeMillis();
//		date = Long.toString(s);
		SimpleDateFormat sdf = new SimpleDateFormat("ddHHmmss");
		Random rand = new Random();
		try {
			date = sdf.format(new Date());
		} catch (Exception e) {
		}
		ret.append("_" + date + rand.nextInt(99));

		String version = PropertiesUtil.getValue("dbversion");
		ret.append("_" + version);

		ret.append("." + (PropertiesUtil.getValue("dbbuildnumber")));

		return ret.toString();
	}

	/**
	 * 
	 * @return
	 */
	public static String getYearDirName() {
		return "y" + Calendar.getInstance().get(Calendar.YEAR);
	}

	/**
	 * 
	 * @return
	 */
	public static String getMonthDirName() {
		return "m" + (Calendar.getInstance().get(Calendar.MONTH) + 1);
	}
	
	
	public static String getResultDir(String resID)
	{
		String res_dir = "";
		res_dir = EnvGetter.getenv("CTP_HOME") + RESULT
		+ getYearDirName() + File.separator + getMonthDirName()
		+ File.separator + resID;
		
		res_dir = StringUtil.replaceSlashBasedSystem(res_dir);
		return res_dir;
	}

	/**
	 * get the test file type through case file .
	 * 
	 * @param caseFile
	 * @return
	 */
	public static int getTestType(String caseFile) {
		if (caseFile == null) {
			return -1;
		}

		int ret = -1;
		caseFile = caseFile.toLowerCase();
		if (caseFile.endsWith(".sql")) {
			ret = CaseResult.TYPE_SQL;
		} else if (caseFile.endsWith(".sh")) {
			ret = CaseResult.TYPE_SCRIPT;
		} else if (caseFile.endsWith(".grv")) {
			ret = CaseResult.TYPE_GROOVY;
		}
		return ret;
	}

	/**
	 * get the result directory through the case file and Test.
	 * 
	 * @param test
	 * @param caseFile
	 * @return
	 */
	public static String getResultDir(Test test, String caseFile) {
		if (test == null || caseFile == null || caseFile.trim().equals("")) {
			return null;
		}
		String ret = null;
		String scenarioRoot = test.getScenarioRootPath();
		int rootLength = scenarioRoot.length();
		String scenarioSuffer = caseFile.substring(rootLength);
		ret = StringUtil.replaceSlashBasedSystem(test.getResult_dir() + File.separator
				+ test.getTestType() + File.separator + scenarioSuffer);

		int pos = ret.lastIndexOf(File.separator + CASEFLAG);
		if (pos != -1) {
			ret = ret.substring(0, pos);
		}
		if (ret != null && ret.endsWith("/")) {
			ret = ret.substring(0, ret.length() - 1);
		}
		return ret;
	}

	/**
	 * get the result directory through testId
	 * 
	 * @param testId
	 * @return
	 */
	public static String getResultPreDir(String testId) {
		if (testId == null) {
			return null;
		}
		return getYearDirName() + "/" + getMonthDirName() + "/" + testId;
	}

	/**
	 * get the answer file directory through the case file .
	 * 
	 * @param caseFile
	 * @return
	 */
	public static String getAnswerDir(String caseFile) {
		int testType = TestUtil.getTestType(caseFile);
		String ret = FileUtil.getDir(caseFile);
		if (testType == CaseResult.TYPE_SQL
				|| testType == CaseResult.TYPE_GROOVY) {
			int position = ret.lastIndexOf(CASEFLAG);
			if (position != -1) {
				ret = ret.substring(0, position)
						+ getAnswer4SQLAndOther(caseFile);
			}
		}
		
		return StringUtil.replaceSlashBasedSystem(ret);
	}

	/**
	 * get the answer file name through case file .
	 * 
	 * @param caseFile
	 * @return
	 */
	public static String getAnswerFile(String caseFile) {
		String answerDir = getAnswerDir(caseFile);
		String caseName = FileUtil.getFileName(caseFile);
		return StringUtil.replaceSlashBasedSystem(answerDir + File.separator + caseName + ".answer");
	}

	/**
	 * get the post-fix through case file name.
	 * 
	 * @param file
	 * @return
	 */
	public static String[] getCaseFilePostFix(String file) {
		if (file == null) {
			return null;
		}
		String[] ret = new String[]{TestUtil.ScenarioTypes};
		return ret;
	}

	/**
	 * get all the cases file .
	 * 
	 * @param test
	 * @param filePath
	 * @param fileList
	 * @param postFixes
	 * @throws Exception
	 */
	public static void getCaseFiles(Test test, String filePath,
			List<String> fileList, String[] postFixes) throws Exception {
		if (filePath == null || postFixes == null || fileList == null) {
			return;
		}

		String localPath = StringUtil.replaceSlashBasedSystem(filePath);
		File dir = new File(localPath);
		if (!dir.exists()) {
			return;
		}

		String path = localPath;

		if (!path.endsWith(File.separator)) {
			path = path + File.separator;
		}

		int positionAnswers = path.indexOf(File.separator
				+ getAnswer4SQLAndOther(filePath) + File.separator);

		if (positionAnswers != -1) {
			return;
		}

		if (!dir.isDirectory()) {
			for (int i = 0; i < postFixes.length; i++) {
				String postfix = postFixes[i];
				if (postfix == null) {
					continue;
				}
				if (filePath.endsWith(postfix)) {
					fileList.add(filePath);
					if (test != null && postfix.equalsIgnoreCase(".sql")) {
						String db = test.getDbId();
						if (db != null && !db.trim().equals("")) {
							test.putCaseDbToMap(filePath, test.getDbId());
						} else {
							throw new Exception("-10001");
						}
					}
				}
			}
		} else {
			String dirPath = filePath;
			String separator = (dirPath.endsWith("/")) ? "" : "/";
			String[] files = dir.list();

			Arrays.sort(files);
			for (int i = 0; i < files.length; i++) {
				String file = dirPath + separator + files[i];
				File child = new File(file);
				System.out.println("child.getName: " + child.getName());
				if (child.isDirectory()
						&& (!("common".equals(child.getName()) || getAnswer4SQLAndOther(
								filePath).equals(child.getName())))) {
					getCaseFiles(test, file, fileList, postFixes);
				} else {
					for (int k = 0; k < postFixes.length; k++) {
						String postfix = postFixes[k];
						if (postfix == null) {
							continue;
						}
						if (file.endsWith(postfix)) {
							fileList.add(file);
							if (test != null
									&& postfix.equalsIgnoreCase(".sql")) {
								String db = test.getDbId();
								if (db != null && !db.trim().equals("")) {
									test.putCaseDbToMap(file, test.getDbId());
								} else {
									throw new Exception("-10001");
								}
							}
						}
					}
				}
			}
		}
	}

	/**
	 * generate the summary info .
	 * 
	 * @param test
	 * @param currentLayer
	 */
	public static void makeSummary(Test test, Map currentLayer) {
		List<String> dirPath = test.getDirPath();

		Summary summary = (Summary) currentLayer.get(SUMMARY_KEY);

		if (summary == null) {
			Iterator iter = currentLayer.keySet().iterator();
			while (iter.hasNext()) {
				String key = (String) iter.next();
				Map nextLayer = (Map) currentLayer.get(key);
				dirPath.add(key);
				makeSummary(test, nextLayer);
			}
			if (dirPath.size() > 0) {
				dirPath.remove(dirPath.size() - 1);
			}

			StringBuilder currentDir = new StringBuilder();
			for (int i = 0; i < dirPath.size(); i++) {
				currentDir.append(dirPath.get(i).toString() + "/");
			}
			String resultDir = currentDir.toString();

			if (test.getRunMode() == Test.MODE_NO_RESULT) {
				resultDir = TestUtil.resultDirToScenarioDir(resultDir);
			}
			if (resultDir.endsWith("/")) {
				resultDir = resultDir.substring(0, resultDir.length() - 1);
			}
			if (SystemUtil.getOS().indexOf("window") == -1) {
				resultDir = "/" + resultDir;
			}

			if (resultDir.indexOf(test.getTestId()) == -1) {
				return;
			}

			summary = new Summary();
			currentLayer.put(SUMMARY_KEY, summary);
			summary.setResultDir(resultDir);
			if (resultDir.equalsIgnoreCase("")) {

			} else {

			}
			if (dirPath.size() > 0) {
				if (dirPath.get(dirPath.size() - 1).equalsIgnoreCase("site")) {
					summary.setSiteRunTimes(test.getSiteRunTimes());
				} else {
					summary.setSiteRunTimes(1);
				}
			}
			
			String catPath = TestUtil.getTestCatBasedOnResultDir(resultDir, test);
			summary.setCatPath(catPath);
			iter = currentLayer.keySet().iterator();
			while (iter.hasNext()) {
				String key = (String) iter.next();
				if (SUMMARY_KEY.equals(key)) {
					continue;
				}

				Object nextLayer = currentLayer.get(key);
				Summary childSummary = (Summary) ((Map) nextLayer)
						.get(SUMMARY_KEY);
				if (dirPath.size() > 0) {
					if (dirPath.get(dirPath.size() - 1)
							.equalsIgnoreCase("site")) {
						summary.setSiteRunTimes(test.getSiteRunTimes());
					} else {
						summary.setSiteRunTimes(1);
					}
				}
				summary.setTotalCount(summary.getTotalCount()
						+ childSummary.getTotalCount());
				summary.setSuccessCount(summary.getSuccessCount()
						+ childSummary.getSuccessCount());
				summary.setFailCount(summary.getFailCount()
						+ childSummary.getFailCount());
				summary.setTotalTime(summary.getTotalTime()
						+ childSummary.getTotalTime());
				summary.getChildSummaryMap().put(childSummary.getResultDir(),
						childSummary);

				summary.getCaseList().addAll(childSummary.getCaseList());
			}

			test.setSummary(summary);
		} else {
			dirPath.clear();
			if (summary != null) {
				test.setSummary(summary);
				List<CaseResult> caseList = summary.getCaseList();
				for (int i = 0; i < caseList.size(); i++) {
					CaseResult caseResult = (CaseResult) caseList.get(i);
					summary.setTotalCount(summary.getTotalCount() + 1);
					if (!caseResult.isShouldRun()) {
						continue;
					}
					if (!caseResult.isSuccessFul()) {
						summary.setFailCount(summary.getFailCount() + 1);
					} else {
						summary.setSuccessCount(summary.getSuccessCount() + 1);
					}
					summary.setTotalTime(summary.getTotalTime()
							+ caseResult.getTotalTime());
				}
				String resultDir = summary.getResultDir();
				if (test.getRunMode() == Test.MODE_NO_RESULT) {
					resultDir = TestUtil.resultDirToScenarioDir(resultDir);
				}
				summary.setResultDir(resultDir);
				if (resultDir != null) {
					StringTokenizer token = new StringTokenizer(StringUtil.replaceSlash(resultDir), "/");
					while (token.hasMoreTokens()) {
						String dir = token.nextToken();
						dirPath.add(dir);
					}
				}
			}
		}
	}

	/**
	 * save the summary info to file .
	 * 
	 * @param test
	 * @param currentLayer
	 */
	public static void saveResultSummary(Test test, Map currentLayer) {
		Iterator iter = currentLayer.keySet().iterator();
		while (iter.hasNext()) {
			String key = (String) iter.next();
			if (key.equals(SUMMARY_KEY)) {
				Summary summary = (Summary) currentLayer.get(key);
				SummaryInfo summaryInfo = test.getSummaryInfoFromMap(summary
						.getResultDir());

				FileUtil.writeToFile(summary.getResultDir() + "/summary_info",
						summary.toString());
				SummaryInfo parent = summaryInfo.getParent();
				summaryInfo.setParent(null);
				String testType = test.getTestType();
				if (testType.equalsIgnoreCase(summaryInfo.getCatPath())
						&& !testType.equalsIgnoreCase(test.getTestTypeAlias())) {
					summaryInfo.setCatPath(test.getTestTypeAlias());
				}
				String xml = XstreamHelper.toXml(summaryInfo);
				FileUtil.writeToFile(summaryInfo.getResultDir()
						+ "/summary.info", xml);
				summaryInfo.setParent(parent);

				if (test.getRunMode() == Test.MODE_RESULT) {
					if (summary.getType() == Summary.TYPE_BOTTOM) {

					}
				}
			} else {
				Map nextLayer = (Map) currentLayer.get(key);
				saveResultSummary(test, nextLayer);
			}
		}
	}

	public static void saveSummaryMainInfo(Test test, Summary summary) {
		String collect_info = "";
		collect_info += "build:"
			+ PropertiesUtil.getValue("dbversion") + "." + PropertiesUtil.getValue("dbbuildnumber") + System.getProperty("line.separator");
		collect_info += "version:" + test.getTestBit()
			+ System.getProperty("line.separator");
		collect_info += "os:" + SystemUtil.getOS()
			+ System.getProperty("line.separator");
		collect_info += "category:" 
			+ test.getTestTypeAlias() + System.getProperty("line.separator");
		collect_info += "elapse_time:"
				+ summary.getTotalTime()+ System.getProperty("line.separator");
		collect_info += "success:" + summary.getSuccessCount()
				+ System.getProperty("line.separator");
		collect_info += "fail:" + summary.getFailCount()
				+ System.getProperty("line.separator");
		collect_info += "total:" + summary.getTotalCount()
				+ System.getProperty("line.separator");
		int execut_count = summary.getFailCount() + summary.getSuccessCount();
		collect_info += "execute_case:" + execut_count
				+ System.getProperty("line.separator");
		collect_info += "totalTime:" + summary.getTotalTime()
				+ System.getProperty("line.separator");
		collect_info += "result_path:" + test.getResult_dir()
		        + System.getProperty("line.separator");
		FileUtil.writeToFile(test.getResult_dir() + TOTAL_SUMMARY_FILE,
				collect_info);
	
	}

	/**
	 * save the result to file .
	 * 
	 * @param test
	 * @param currentLayer
	 */
	public static void saveResult(Test test, Map currentLayer) {
		Iterator iter = currentLayer.keySet().iterator();
		while (iter.hasNext()) {
			String key = (String) iter.next();
			if (key.equals(SUMMARY_KEY)) {
				Summary summary = (Summary) currentLayer.get(key);
				SummaryInfo summaryInfo = test.getSummaryInfoFromMap(summary
						.getResultDir());
				FileUtil.writeToFile(summary.getResultDir() + "/summary_info",
						summary.toString());
				SummaryInfo parent = summaryInfo.getParent();
				summaryInfo.setParent(null);
				String xml = XstreamHelper.toXml(summaryInfo);
				FileUtil.writeToFile(summaryInfo.getResultDir()
						+ "/summary.info", xml);
				summaryInfo.setParent(parent);

				if (test.getRunMode() == Test.MODE_RESULT) {
					if (summary.getType() == Summary.TYPE_BOTTOM) {
						List<CaseResult> caseList = summary.getCaseList();
						for (int i = 0; i < caseList.size(); i++) {
							CaseResult caseResult = (CaseResult) caseList
									.get(i);
							saveResult(caseResult, test.getCodeset());
						}
						
					}
				}
			} else {
				Map nextLayer = (Map) currentLayer.get(key);

				saveResult(test, nextLayer);

			}
		}
	}

	/**
	 * write the case execute result to file .
	 * 
	 * @param caseResult
	 */
	public static void saveResult(CaseResult caseResult, String charset) {
		if (!caseResult.isSuccessFul()) {
			String resultFile = caseResult.getResultDir() + "/"
					+ caseResult.getCaseName() + ".result";
			if (caseResult.getType() == CaseResult.TYPE_SQL) {
				FileUtil.writeToFile(resultFile, caseResult.getResult(),
						charset);
			} else {
				String tempResultFile = caseResult.getCaseDir() + "/"
						+ caseResult.getCaseName() + ".result";
				FileUtil.copyFile(tempResultFile, resultFile);
			}
		}
	}
	
	public static void copyCaseAnswerFile(CaseResult caseResult) {
		if (!caseResult.isSuccessFul()) {
			String targetSqlFile = caseResult.getResultDir() + "/"
					+ caseResult.getCaseName() + ".sql";
			String targetAnswerFile = caseResult.getResultDir() + "/"
			+ caseResult.getCaseName() + ".answer";
			if (caseResult.getType() == CaseResult.TYPE_SQL) {
				FileUtil.writeSQLFileIntoResultFolder(caseResult.getCaseFile(), targetSqlFile);
				FileUtil.writeSQLFileIntoResultFolder(caseResult.getAnswerFile(), targetAnswerFile);
			} 
		}
	}


	/**
	 * 
	 * @param test
	 * @param currentLayer
	 * @param parent
	 */
	public static void makeSummaryInfo(Test test, Map currentLayer,
			SummaryInfo parent) {
		if (test.getRunMode() != Test.MODE_RESULT) {
			return;
		}

		if (test == null || currentLayer == null) {
			return;
		}

		SummaryInfo summaryInfo = new SummaryInfo();
		if (parent == null) {
			Summary summary = null;
			String name = null;

			test.setSummaryInfo(summaryInfo);
			summary = test.getSummary();
			name = test.getTestId();
			int position = name.indexOf("_");
			if (position != -1) {
				name = name.substring(position + 1);
			}
			position = name.indexOf("_");
			if (position != -1) {
				name = name.substring(position + 1);
			}
			try {
				summaryInfo.setName(name);
				summaryInfo.setVersion(test.getVersion());
				summaryInfo.setResultDir(summary.getResultDir());
				summaryInfo.setCatPath(summary.getCatPath());
				summaryInfo.setTotalCount(summary.getTotalCount());
				summaryInfo.setSuccessCount(summary.getSuccessCount());
				summaryInfo.setSiteRunTimes(summary.getSiteRunTimes());
				summaryInfo.setFailCount(summary.getFailCount());
				summaryInfo.setCatPath(summary.getCatPath());
				summaryInfo.setTotalTime(summary.getTotalTime());
				test.putSummaryInfoToMap(summaryInfo.getResultDir(),
						summaryInfo);
			} catch (Exception e) {
				System.out.println("no sql case, please check the directory!");
			}
		} else {
			summaryInfo.setParent(parent);
			parent.addChild(summaryInfo);

		}
		Iterator iter = currentLayer.keySet().iterator();
		while (iter.hasNext()) {
			String key = (String) iter.next();
			if (key.equals(SUMMARY_KEY)) {
				Summary summary = (Summary) currentLayer.get(key);

				if (summaryInfo.getName() == null) {
					String catPath = summary.getCatPath();
					String name = catPath;
					int position = catPath.lastIndexOf("/");
					if (position != -1) {
						name = catPath.substring(position + 1);
					}
					summaryInfo.setName(name);
				}
				summaryInfo.setResultDir(summary.getResultDir());
				summaryInfo.setCatPath(summary.getCatPath());
				summaryInfo.setSiteRunTimes(summary.getSiteRunTimes());
				summaryInfo.setTotalCount(summary.getTotalCount());
				summaryInfo.setSuccessCount(summary.getSuccessCount());
				summaryInfo.setFailCount(summary.getFailCount());
				summaryInfo.setCatPath(summary.getCatPath());
				summaryInfo.setTotalTime(summary.getTotalTime());
				test.putSummaryInfoToMap(summaryInfo.getResultDir(),
						summaryInfo);

				// if test build is debug build, change catpat of the last
				// second one to [sql|medium|site|shell]_debug to show on qahome
				// page
				String site_type = null;
				String tmp = null;

				if (test.getDbId().indexOf("kcc") != -1) {
					site_type = "kcc";
				} else if (test.getDbId().indexOf("neis05") != -1) {
					site_type = "neis05";
				} else if (test.getDbId().indexOf("neis08") != -1) {
					site_type = "neis08";
				}

				if (test.isDebug()) {
					String cat = summary.getCatPath();
					// this should fully match with condition to make this
					// changes
					if ("sql".equals(cat) || "medium".equals(cat)
							|| "shell".equals(cat)) {
						tmp = cat + "_debug";
						summaryInfo.setCatPath(tmp);
					} else if ("site".equals(cat)) {
						tmp = site_type + "_debug";
						summaryInfo.setCatPath(tmp);
					}
				}

				if (summary.getType() == Summary.TYPE_BOTTOM) {
					List<CaseResult> caseList = summary.getCaseList();
					//comment out save summary collection function
					//saveSummaryMainInfo(test, summary);
					
					for (int i = 0; i < caseList.size(); i++) {
						CaseResult caseResult = (CaseResult) caseList.get(i);
						String caseFile = caseResult.getCaseFile();
						CaseResult result = new CaseResult();
						result.setCaseFile(caseFile);
						if (test.isNeedAnswerInSummary()) {
							String answerFile = caseResult.getAnswerFile();
							result.setAnswerFile(answerFile);
						}
						result.setSuccessFul(caseResult.isSuccessFul());
						result.setTotalTime(caseResult.getTotalTime());
						result.setSiteRunTimes(caseResult.getSiteRunTimes());
						if (!caseResult.isShouldRun()) {
							summaryInfo.addNotRunCase(caseResult);
						} else if (caseResult.isSuccessFul()) {
							summaryInfo.addOkCase(result);
						} else {
							summaryInfo.addNokCase(result);
						}
					}
				}
			} else {
				Map nextLayer = (Map) currentLayer.get(key);
				makeSummaryInfo(test, nextLayer, summaryInfo);
			}
		}
	}

	public static Map getCatMap(Test test, String file) {
		if (test == null || file == null) {
			return null;
		}

		Map currentLayer = test.getCatMap();
		String[] keys = TestUtil.getCatPath(file, test);
		for (int i = 0; i < keys.length; i++) {
			String key = keys[i];
			Map nextLayer = (Map) currentLayer.get(key);
			if (nextLayer == null) {
				nextLayer = new Hashtable();
				currentLayer.put(key, nextLayer);
			}
			currentLayer = nextLayer;
		}

		return currentLayer;
	}

	public static String resultDirToScenarioDir(String resultDir) {
		if (resultDir == null) {
			return null;
		}

		String ret = resultDir;
		int positionResult = ret.indexOf(RESULT);
		if (positionResult != -1) {
			String dir = ret.substring(0, positionResult) + SCENARIO;
			int position = getTypePosition(ret);
			if (position != -1) {
				ret = dir + ret.substring(position);
			}
		}
		return ret;
	}

	public static String getCaseSummary(String summaryFile) {
		StringBuilder sb = new StringBuilder();
		try {
			BufferedReader reader = new BufferedReader(new InputStreamReader(
					new FileInputStream(new File(summaryFile)), "UTF-8"));
			int lineNum = 0;
			String line = reader.readLine();
			while (line != null) {
				lineNum++;
				if (lineNum > 4) {
					sb.append(line + System.getProperty("line.separator"));
				}

				line = reader.readLine();
			}
			reader.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return sb.toString();
	}

	public static String getTestCatBasedOnResultDir(String resDir, Test test){
		if(resDir == null){
			return null;
		}
		
		String ret;
		String resultRootDir = test.getResult_dir();
        int resultRootDirLength = resultRootDir.length();
        
        ret = resDir.substring(resultRootDirLength);
        
		if (ret != null
				&& (ret.startsWith("/") || ret.startsWith("\\"))) {
			ret = ret.substring(1);
		}
        
        int pos = ret.lastIndexOf(File.separator + CASEFLAG);
		if (pos != -1) {
			ret = ret.substring(0, pos);
		}
		
		if (ret.endsWith(File.separator)) {
			ret = ret.substring(0, ret.length() - 1);
		}
		
		return ret;
		
	}
	public static String getTestCatPath(String file, Test test) {
		if (file == null) {
			return null;
		}

		String ret;
		String scenarioRoot = test.getScenarioRootPath();
		int rootLength = scenarioRoot.length();
		String scenarioSuffer = file.substring(rootLength);
		ret = StringUtil.replaceSlashBasedSystem(test.getTestType() + File.separator + scenarioSuffer);

		int pos = ret.lastIndexOf(File.separator + CASEFLAG);
		if (pos != -1) {
			ret = ret.substring(0, pos);
		}
		
		if (ret.endsWith(File.separator)) {
			ret = ret.substring(0, ret.length() - 1);
		}
		return ret;
	}

	public static boolean isPrintQueryPlan(String caseFile) {
		boolean flag = false;
		if (caseFile == null || caseFile.trim().equals("")) {
			return false;
		}

		int position = caseFile.lastIndexOf(".");
		if (position == -1) {
			return false;
		}
		SystemModel systemModel = (SystemModel) XstreamHelper
				.fromXml(EnvGetter.getenv("CTP_HOME") + File.separator 
						+ "sql/configuration/System.xml");
		if (systemModel.isQueryPlan()) {
			flag = true;
		} else {
			String fileName = caseFile.substring(0, position) + ".queryPlan";
			File file = new File(fileName);
			flag = file.exists();
		}
		return flag;
	}

	private static String[] getCatPath(String file, Test test) {
		String path = getTestCatPath(file, test);
		String standardPath= StringUtil.replaceSlash(path);
		String[] keys = standardPath.split("/");
		return keys;
	}

	public static int getTypePosition(String file) {
		if (file == null) {
			return -1;
		}
		int ret = -1;
		Iterator iter = SCENARIOMAP.keySet().iterator();
		while (iter.hasNext()) {
			String dirName = (String) iter.next();
			ret = file.indexOf("/" + dirName + "/");
			if (ret != -1) {
				break;
			}
		}
		return ret;
	}

}
