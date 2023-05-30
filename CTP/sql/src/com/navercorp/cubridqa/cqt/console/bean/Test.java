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
package com.navercorp.cubridqa.cqt.console.bean;

import java.io.BufferedWriter;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

public class Test {
	public static final int MODE_RUN = 0;

	public static final int MODE_MAKE_ANSWER = 1;

	public static final int MODE_RESULT = 2;

	public static final int MODE_NO_RESULT = 3;

	public static final int TYPE_FUNCTION = 0;

	public static final int TYPE_PERFORMANCE = 1;

	public static String urlProperties = "";

	private String testId;
	
	private String caseFilter;

	private String testType;

	private String testTypeAlias;

	private String testBit;

	private String path;

	private int siteRunTimes;

	private boolean editorExecute;

	private String version = "";

	private String codeset = "";

	private String language = "";

	private String collation = "";

	private String result_dir = "";

	private boolean i18n;

	private boolean qaview = false;

	private boolean isFirstTime = true;

	private String run_mode = null;

	private String runModeSecondary = null;

	private String holdcas = "";

	private String reset_scripts = "";

	private String autocommit = "";

	private String serverOutput = "off";

	private boolean needSummaryXML = false;

	private boolean needAnswerInSummary = false;

	private boolean needCheckServerStatus = false;

	private boolean needDebugHint = false;

	private String scenarioRootPath = "";
	
	private Map<String, List<File>> coreCaseMap = new HashMap<String, List<File>>();
	
	private List<String> allCoreList = new ArrayList<String>();

	public List<String> getAllCoreList() {
		return allCoreList;
	}

	public void setAllCoreList(List<String> allCoreList) {
		this.allCoreList = allCoreList;
	}

	public Map<String, List<File>> getCoreCaseMap() {
		return coreCaseMap;
	}
	
	public void putCoreCaseIntoMap(String caseFile, List<File> flist){
		this.coreCaseMap.put(caseFile, flist);
	}

	public String getScenarioRootPath() {
		return scenarioRootPath;
	}

	public void setScenarioRootPath(String scenarioRootPath) {
		this.scenarioRootPath = scenarioRootPath;
	}

	public String getUrlProperties() {
		return urlProperties;
	}

	public void setUrlProperties(String urlProperties) {
		this.urlProperties = urlProperties;
	}

	public boolean isNeedDebugHint() {
		return needDebugHint;
	}

	public void setNeedDebugHint(boolean needDebugHint) {
		this.needDebugHint = needDebugHint;
	}

	public boolean isNeedCheckServerStatus() {
		return needCheckServerStatus;
	}

	public void setNeedCheckServerStatus(boolean needCheckServerStatus) {
		this.needCheckServerStatus = needCheckServerStatus;
	}

	public boolean isNeedSummaryXML() {
		return needSummaryXML;
	}

	public void setNeedSummaryXML(boolean needSummaryXML) {
		this.needSummaryXML = needSummaryXML;
	}

	private BufferedWriter fileHandle = null;

	public BufferedWriter getFileHandle() {
		return fileHandle;
	}

	public void setFileHandle(BufferedWriter fileHandle) {
		this.fileHandle = fileHandle;
	}

	private TestCaseSummary[] failList;

	public TestCaseSummary[] getFailList() {
		return failList;
	}

	public void setFailList(TestCaseSummary[] failList) {
		this.failList = failList;
	}

	public void initFailSummary() {
		failList = new TestCaseSummary[100];
		this.setFailList(failList);
	}

	public String getAutocommit() {
		return autocommit;
	}

	public void setAutocommit(String autocommit) {
		this.autocommit = autocommit;
	}

	public String getHoldcas() {
		return holdcas;
	}

	public void setHoldcas(String holdcas) {
		this.holdcas = holdcas;
	}

	public String getServerOutput() {
		return serverOutput;
	}
	
	public void setServerOutput (String so) {
		this.serverOutput = so;
	}

	public String getReset_scripts() {
		return reset_scripts;
	}

	public void setReset_scripts(String reset_scripts) {
		this.reset_scripts = reset_scripts;
	}

	public String getRun_mode() {
		return run_mode;
	}

	public void setRun_mode(String run_mode) {
		this.run_mode = run_mode;
	}

	public String[] getScripts() {
		return scripts;
	}

	public void setScripts(String[] scripts) {
		this.scripts = scripts;
	}

	private String[] scripts;

	private String charset_file = "default_charset.xml";

	public boolean isFirstTime() {
		return isFirstTime;
	}

	public void setFirstTime(boolean isFirstTime) {
		this.isFirstTime = isFirstTime;
	}

	public int getSiteRunTimes() {
		return siteRunTimes;
	}

	public void setSiteRunTimes(int siteRunTimes) {
		this.siteRunTimes = siteRunTimes;
	}

	private int runMode;

	private String[] cases;

	private Map<String, Object> testInfoMap = new Hashtable<String, Object>();

	private List<String> caseFileList = new ArrayList<String>();

	private Map<String, CaseResult> caseMap = new Hashtable<String, CaseResult>();

	private Map<String, Summary> summaryMap = new Hashtable<String, Summary>();

	private Map<String, SummaryInfo> summaryInfoMap = new Hashtable<String, SummaryInfo>();

	private Map catMap = new Hashtable();

	private Map<String, String> caseDbMap = new Hashtable<String, String>();

	private String dbId = null;

	private String connId = "";

	private List<String> dirPath = new Vector<String>();

	private Summary summary;

	private SummaryInfo summaryInfo;

	private String dbVersion;

	private String dbBuild;

	private int sqlRunTime = 1;

	private int type = 0;

	private boolean isDebug;

	private Map<String, Object> connIDList = new Hashtable<String, Object>();

	private Set<String> resultDirSet = new HashSet();

	public Test(String testId) {
		this.testId = testId;
	}

	public Map getCatMap() {
		return catMap;
	}

	public String getConnId() {
		return connId;
	}

	public String getTestId() {
		return testId;
	}

	public String getCaseFilter() {
		return caseFilter;
	}

	public void setCaseFilter(String caseFilter) {
		this.caseFilter = caseFilter;
	}
	
	public Summary getSummary() {
		return summary;
	}

	public void setSummary(Summary testSummary) {
		this.summary = testSummary;
	}

	public boolean isNeedAnswerInSummary() {
		return needAnswerInSummary;
	}

	public void setNeedAnswerInSummary(boolean needAnswerInSummary) {
		this.needAnswerInSummary = needAnswerInSummary;
	}

	public List<String> getDirPath() {
		return dirPath;
	}

	public int getRunMode() {
		return runMode;
	}

	public void setRunMode(int runMode) {
		this.runMode = runMode;
	}

	public String getRunModeSecondary() {
		return runModeSecondary;
	}

	public void setRunModeSecondary(String runModeSecondary) {
		this.runModeSecondary = runModeSecondary;
	}

	public List<String> getCaseFileList() {
		return caseFileList;
	}

	public void putCaseResultToMap(String caseFile, CaseResult caseResult) {
		caseMap.put(caseFile, caseResult);
	}

	public CaseResult getCaseResultFromMap(String caseFile) {
		return (CaseResult) caseMap.get(caseFile);
	}

	public void putSummaryToMap(String path, Summary summary) {
		summaryMap.put(path, summary);
	}

	public Summary getSummaryFromMap(String path) {
		return (Summary) summaryMap.get(path);
	}

	public void putSummaryInfoToMap(String path, SummaryInfo summaryInfo) {
		summaryInfoMap.put(path, summaryInfo);
	}

	public SummaryInfo getSummaryInfoFromMap(String path) {
		return (SummaryInfo) summaryInfoMap.get(path);
	}

	public SummaryInfo getSummaryInfo() {
		return summaryInfo;
	}

	public void setSummaryInfo(SummaryInfo summaryInfo) {
		this.summaryInfo = summaryInfo;
	}

	public void setConnId(String connId) {
		this.connId = connId;
	}

	public String[] getCases() {
		return cases;
	}

	public void setCases(String[] cases) {
		this.cases = cases;
	}

	public Map<String, Object> getTestInfoMap() {
		return testInfoMap;
	}

	public void putCaseDbToMap(String caseFile, String db) {
		caseDbMap.put(caseFile, db);
	}

	public String getDbId(String caseFile) {
		return (String) caseDbMap.get(caseFile);
	}

	public String getDbId() {
		return dbId;
	}

	public void setDbId(String dbId) {
		this.dbId = dbId;
	}

	public String getPath() {
		return path;
	}

	public void setPath(String path) {
		this.path = path;
	}

	public String getDbVersion() {
		return dbVersion;
	}

	public void setDbVersion(String dbVersion) {
		this.dbVersion = dbVersion;
	}

	public String getDbBuild() {
		return dbBuild;
	}

	public void setDbBuild(String dbBuild) {
		this.dbBuild = dbBuild;
	}

	public int getType() {
		return type;
	}

	public void setType(int type) {
		this.type = type;
	}

	public String getTestTypeAlias() {
		return testTypeAlias;
	}

	public void setTestTypeAlias(String testTypeAlias) {
		this.testTypeAlias = testTypeAlias;
	}

	public int getSqlRunTime() {
		return sqlRunTime;
	}

	public void setSqlRunTime(int sqlRunTime) {
		this.sqlRunTime = sqlRunTime;
	}

	public Set<String> getResultDirSet() {
		return resultDirSet;
	}

	public void setResultDirSet(Set<String> resultDirSet) {
		this.resultDirSet = resultDirSet;
	}

	public void addResultDir(String resultDir) {
		resultDirSet.add(resultDir);
	}

	public boolean isEditorExecute() {
		return editorExecute;
	}

	public void setEditorExecute(boolean editorExecute) {
		this.editorExecute = editorExecute;
	}

	public String getVersion() {
		return version;
	}

	public void setVersion(String version) {
		this.version = version;
	}

	public String getCodeset() {
		return codeset;
	}

	public void setCodeset(String codeset) {
		this.codeset = codeset;
	}

	public String getLanguage() {
		return language;
	}

	public void setLanguage(String language) {
		this.language = language;
	}

	public String getCollation() {
		return collation;
	}

	public void setCollation(String collation) {
		this.collation = collation;
	}

	public boolean isI18n() {
		return i18n;
	}

	public void setI18n(boolean i18n) {
		this.i18n = i18n;
	}

	public boolean isQaview() {
		return qaview;
	}

	public void setQaview(boolean qaview) {
		this.qaview = qaview;
	}

	public boolean isDebug() {
		return isDebug;
	}

	public void setDebug(boolean isDebug) {
		this.isDebug = isDebug;
	}

	public String getCharset_file() {
		return charset_file;
	}

	public void setCharset_file(String charset_file) {
		this.charset_file = charset_file;
	}

	public String getResult_dir() {
		return result_dir;
	}

	public void setResult_dir(String result_dir) {
		this.result_dir = result_dir;
	}

	public Map<String, Object> getConnIDList() {
		return connIDList;
	}

	public void setConnIDList(Map<String, Object> connIDList) {
		this.connIDList = connIDList;
	}

	public String getTestType() {
		return testType;
	}

	public void setTestType(String testType) {
		this.testType = testType;
	}

	public String getTestBit() {
		return testBit;
	}

	public void setTestBit(String testBit) {
		this.testBit = testBit;
	}

}
