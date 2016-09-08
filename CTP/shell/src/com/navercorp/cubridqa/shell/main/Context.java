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

package com.navercorp.cubridqa.shell.main;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.Map.Entry;

import com.navercorp.cubridqa.shell.common.CommonUtils;
import com.navercorp.cubridqa.shell.common.Constants;
import com.navercorp.cubridqa.shell.result.FeedbackDB;
import com.navercorp.cubridqa.shell.result.FeedbackFile;
import com.navercorp.cubridqa.shell.result.FeedbackNull;

public class Context {

	Properties config;

	boolean isContinueMode = false;

	ArrayList<String> envList;

	String cubridPackageUrl;
	
	private boolean cleanTestCase = false; 
	
	private String filename;
	
	boolean isWindows = false;
	
	Feedback feedback;
	
	String build;
	
	String version;
	
	Integer taskId;

	boolean isNewBuildNumberSystem = true;
	
	int total_scenario;
	
	String testCaseSkipKey;
	
	boolean isSaveCorefile = false;
	
	Date startDate;
	
	String bigSpaceDir;
	
	int maxRetryCount;
          
	String defaultDbCharset;

	boolean enableCheckDiskSpace = false;

	String mailNoticeTo;

	boolean enableSaveNormalErrorLog = false;

    String toolHome;
    
    String serviceProtocolType;
    
	String msgId;
	
	String enableSkipUpgrade;
	
	String ctpBranchName;
	
	Map<String, String> envMap = null;
	
	String currentLogDir;
	String rootLogDir;
	boolean reInstallTestBuildYn = false;
	String scenario;

	public Context(String filename) throws IOException {
		this.filename = filename;
		this.startDate = new java.util.Date();
		this.envMap = new HashMap<String, String>();
		reload();
		this.scenario = getProperty("scenario", "").trim();
	}
	
	public void reload() throws IOException{
		this.config = CommonUtils.getPropertiesWithPriority(filename);
		this.envList = initEnvList(config);
		this.toolHome = com.navercorp.cubridqa.common.CommonUtils.getEnvInFile (Constants.ENV_CTP_HOME_KEY);
		
		this.cleanTestCase = getProperty("main.testcase.clean", "false").equalsIgnoreCase("true") && !CommonUtils.isEmpty(getTestCaseBranch());
		this.isWindows = getTestPlatform().equalsIgnoreCase("windows");
		
		this.testCaseSkipKey = getProperty("main.testcase.skip_key", "").trim().toUpperCase();
		if(this.testCaseSkipKey.equals("")) {
			this.testCaseSkipKey = null;
		}
                 		
		this.isSaveCorefile = getProperty("main.testing.savecorefile", "FALSE").trim().toUpperCase().equals("TRUE");
		this.bigSpaceDir = getProperty("main.testing.bigspace_dir", "").trim();
		this.maxRetryCount = Integer.parseInt(getProperty("max.retry.count", "0").trim());
        this.defaultDbCharset = getProperty("main.testing.default_charset", "en_US").trim();
       
		this.enableCheckDiskSpace = com.navercorp.cubridqa.common.CommonUtils.convertBoolean(getProperty("main.testing.enable_check_disk_space", "FALSE").trim());
		this.mailNoticeTo = getProperty("main.owner.mail", "").trim();
		
		this.enableSaveNormalErrorLog = getProperty("main.testing.do_save_normal_error_log", "FALSE").trim().toUpperCase().equals("TRUE");        
      
		this.isContinueMode = com.navercorp.cubridqa.common.CommonUtils.convertBoolean(getProperty("main.mode.continue", "false"), false);
		this.cubridPackageUrl = getProperty("main.testbuild.url");

		this.serviceProtocolType = getProperty("main.service.protocol", "ssh").trim().toLowerCase();
		this.enableSkipUpgrade = getPropertyFromEnv("SKIP_UPGRADE", "1");
		this.ctpBranchName = getPropertyFromEnv("CTP_BRANCH_NAME", "master");		
		
		setLogDir("shell");
		
		// to get msg id from environment variable
		putEnvVriableIntoMapByKey("MSG_ID");
		
		if(this.feedback == null) {
			String feedbackType = getProperty("main.feedback.type", "file").trim();
			if (feedbackType.equalsIgnoreCase("file")) {
				this.feedback = new FeedbackFile(this);
			} else if (feedbackType.equalsIgnoreCase("database")) {
				this.feedback = new FeedbackDB(this);
			} else {
				this.feedback = new FeedbackNull();
			}
		}
    }
	
	public void setLogDir(String category) {
		this.rootLogDir = getToolHome() + "/result/" + category;
		this.currentLogDir = this.rootLogDir + "/current_runtime_logs";		
	}

	public static ArrayList<String> initEnvList(Properties config) {
		ArrayList<String> resultList = new ArrayList<String>();
		Set<Object> set = config.keySet();
		Iterator<Object> it = set.iterator();
		String key;
		while (it.hasNext()) {
			key = (String) it.next();
			if (key.startsWith("env.") && key.endsWith(".ssh.host")) {
				resultList.add(key.substring(4, key.indexOf(".ssh.host")));
			}
		}
		return resultList;
	}
	
	public String getInstanceProperty(String envId, String key, String defaultValue) {
		String value = getInstanceProperty(envId, key);
		if (value == null || value.trim().equals("")) {
			return defaultValue;
		} else {
			return value;
		}
	}

	public String getInstanceProperty(String envId, String key){
		String value = getProperty("env." + envId + "." + key);
		return value == null ? getProperty("default." + key, "") : value;
	}
	
	public String getProperty(String key, String defaultValue) {
		return this.config.getProperty(key, defaultValue);
	}
	
	public String getPropertyFromEnv(String key, String defaultValue){
		if(key == null || key.length() <= 0)
			return defaultValue;
		
		String val = System.getenv(key);
		return (val == null || defaultValue.length() <=0) ? defaultValue : val;
	}
	
	public ArrayList<String> getRelatedHosts(String envId) {
		String[] relates = getProperty("env." + envId + ".ssh.host.related", "").split(",");
		ArrayList<String> list = new ArrayList<String>();
		String h;
		for (int i = 0; i < relates.length; i++) {
			h = relates[i].trim();
			if (h.equals(""))
				continue;
			list.add(h);
		}
		return list;
	}
	
	public String getProperty(String key) {
		return getProperty(key, null);
	}

	public ArrayList<String> getEnvList() {
		return this.envList;
	}

	public boolean isContinueMode() {
		return isContinueMode;
	}

	public void setContinueMode(boolean isContinueMode) {
		this.isContinueMode = isContinueMode;
	}

	public String getTestCaseRoot() {
		// return getProperty("main.testcase.root", "").trim();
		return this.scenario;
	}
	
	public void setTestCaseRoot(String scenario) {
		this.scenario = scenario;
	}
	
	public String getTestCaseBranch() {
		return getProperty("main.testcase.branch_git", "").trim();
	}
	
	public String getTestCaseWorkspace() {
		String ws = getProperty("main.testcase.workspace", "").trim();
		if (ws.equals("")) {
			return getTestCaseRoot();
		} else {
			return ws;
		}
	}
	
	public String getTestPlatform(){
		return getProperty("main.testing.platform", "linux");
	}
	
	public String getTestCategory()
	{
		return getProperty("main.testing.category", "shell");
	}
	
	public boolean needCleanTestCase()
	{
		return this.cleanTestCase;
	}
	
	public String getTestCaseTimeout()
	{
		return getProperty("main.testcase.timeout", "-1");
	}
	
	public boolean needEnableMonitorTrace()
	{
		return com.navercorp.cubridqa.common.CommonUtils.convertBoolean(getProperty("main.monitor.enable_tracing", "false"));
	}
	
	public String getExcludedCoresByAssertLine()
	{
		return getProperty("main.testing.excluded_cores_by_assert_line");
	}
	
	public boolean needDeleteTestCaseAfterTest()
	{
		return getProperty("main.testing.delete_test_case_after_execution", "false").trim().toLowerCase().equals("true");
	}
	
	public void setCubridPackageUrl(String cubridPackageUrl) {
		this.cubridPackageUrl = cubridPackageUrl;
	}

	public String getCubridPackageUrl() {
		return this.cubridPackageUrl;
	}
	
	public String getSVNUserInfo(){
		String user = getProperty("main.svn.user", "");
		String pwd = getProperty("main.svn.pwd", "");
		if (user.trim().equals("") || pwd.trim().equals(""))
			return "";
		return " --username " + user + " --password " + pwd + " --non-interactive "; 
	}
	
	public String getGitUserName(){
		return getProperty("main.git.user", "").trim();
	}
	
	public String getGitUserEmail(){
		return getProperty("main.git.email", "").trim();
	}
	
	public String getGitUserPwd(){
		return getProperty("main.git.pwd", "").trim();
	}
	
	public boolean isScenarioInGit() {
		String git = getTestCaseBranch();
		return git != null && git.trim().length() > 0;
	}
	
	private void putMKeyEnvVaribleIntoMap(){
		Map map = System.getenv();  
		Iterator it = map.entrySet().iterator();  
		while(it.hasNext())  
		{  
		    Entry entry = (Entry)it.next(); 
		    if(entry.getKey()!=null && entry.getKey().toString().indexOf("MKEY") != -1)
		    {
		    	envMap.put(entry.getKey().toString(), entry.getValue().toString());
		    }
		}		
	}
	
	private void putEnvVriableIntoMapByKey(String key)
	{
		if(key == null || key.length() <= 0)
			return;
		
		String val = System.getenv(key);
		envMap.put(key, val);
		
	}
	
	public boolean isWindows () {
		 return this.isWindows;
	}
	
	public Feedback getFeedback() {
		return this.feedback;
	}
	
	public String getCurrentLogDir(){
		return currentLogDir;		
	}
	
	public String getRootLogDir(){		
		return rootLogDir;
	}
	
	public boolean isReInstallTestBuildYn() {
		return reInstallTestBuildYn;
	}

	public void setReInstallTestBuildYn(boolean reInstallTestBuildYn) {
		this.reInstallTestBuildYn = reInstallTestBuildYn;
	}
	
	public String getFeedbackDbUrl(){
		String host = getProperty("feedback.db.host", "");
		String port = getProperty("feedback.db.port", "");
		String dbname = getProperty("feedback.db.name", "");
		
		String url = "jdbc:cubrid:" + host + ":" + port + ":" + dbname + ":::";
		
		return url;
	}
	
	public String getFeedbackDbUser(){
		String user = getProperty("feedback.db.user", "");
		
		return user;
	}
	
	public String getFeedbackDbPwd(){
		String pwd = getProperty("feedback.db.pwd", "");
		
		return pwd;
	}
	
	public void setTestBuild(String build) {
		this.build = build;
	}

	public String getTestBuild() {
		return this.build;
	}
	
	public void setIsNewBuildNumberSystem(boolean isNewBuildNumberSystem) {
		this.isNewBuildNumberSystem = isNewBuildNumberSystem;
	}

	public boolean getIsNewBuildNumberSystem() {
		return this.isNewBuildNumberSystem;
	}
	
	public void setVersion(String version) {
		this.version = version;
	}

	public String getVersion() {
		return this.version;
	}
	
	public Properties getProperties(){
		return this.config;
	}
	
	public String getTestCaseSkipKey() {
		return this.testCaseSkipKey;
	}

	public boolean isSaveCorefile() {
		return this.isSaveCorefile;
	}
	
	public Date getStartDate() {
		return this.startDate;
	}
	
	public String getBigSpaceDir() {
		return bigSpaceDir;
	}
	
	public int getMaxRetryCount() {
		return maxRetryCount;
	}

	public void setMaxRetryCount(int maxRetryCount) {
		this.maxRetryCount = maxRetryCount;
	}

        public String getDefaultDbcharset() {
		return defaultDbCharset;
	}

	public String getMsgId() {
		return msgId;
	}

	public void setMsgId(String msgId) {
		this.msgId = msgId;
	}
    
	public boolean getEnableSaveNormalErrorLog() {
		return enableSaveNormalErrorLog;
	}

	public Integer getTaskId() {
		return taskId;
	}

	public void setTaskId(Integer taskId) {
		this.taskId = taskId;
	}
	
	public String getServiceProtocolType() {
		return serviceProtocolType;
	}

	public void setServiceProtocolType(String serviceProtocolType) {
		this.serviceProtocolType = serviceProtocolType;
	}
	
    public String getToolHome() {
		return toolHome;
	}

	public void setToolHome(String toolHome) {
		this.toolHome = toolHome;
	}
	
	public String getCtpBranchName() {
		return ctpBranchName;
	}
	
	public void setCtpBranchName(String ctpBranchName) {
		this.ctpBranchName = ctpBranchName;
	}
	
	public String getEnableSkipUpgrade() {
		return enableSkipUpgrade;
	}

	public void setEnableSkipUpgrade(String enableSkipUpgrade) {
		this.enableSkipUpgrade = enableSkipUpgrade;
	}
	
	public String getMailNoticeTo() {
		return this.mailNoticeTo;
	}

	public String getMailNoticeCC() {
		String cc = getProperty("main.stakeholder.mail", "").trim();
		if (CommonUtils.isEmpty(cc)) {
			return com.navercorp.cubridqa.common.Constants.MAIL_FROM;
		} else {
			return cc;
		}
	}
	
	public boolean enableCheckDiskSpace() {
		return enableCheckDiskSpace;
	}
}
