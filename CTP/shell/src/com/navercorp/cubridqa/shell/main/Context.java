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
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;

import com.navercorp.cubridqa.common.ConfigParameterConstants;
import com.navercorp.cubridqa.shell.common.CommonUtils;
import com.navercorp.cubridqa.shell.common.Constants;
import com.navercorp.cubridqa.shell.dispatch.Selector;
import com.navercorp.cubridqa.shell.result.FeedbackDB;
import com.navercorp.cubridqa.shell.result.FeedbackFile;
import com.navercorp.cubridqa.shell.result.FeedbackNull;

public class Context {

	Properties config;

	boolean isContinueMode = false;

	ArrayList<String> envList;
	ArrayList<String> followerList;
	ArrayList<Selector> selectorList;

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

	Date startDate;

	String bigSpaceDir;

	int maxRetryCount;

	String defaultDbCharset;

	boolean enableCheckDiskSpace = false;
	
	String reserveDiskSpaceSize;

	String mailNoticeTo;

	boolean enableSaveNormalErrorLog = false;

	String toolHome;

	String serviceProtocolType;

	String msgId;

	String enableSkipUpdate;

	String ctpBranchName;

	String testCategory;

	Map<String, String> envMap = null;

	String currentLogDir;
	String rootLogDir;

	boolean skipToSaveSuccCase = false;
	boolean reInstallTestBuildYn = false;
	private boolean isExecuteAtLocal = false;
	String scenario;

	public Context(String filename) throws IOException {
		this.filename = filename;
		this.startDate = new java.util.Date();
		this.envMap = new HashMap<String, String>();

		reload();
		setLogDir(getResultName());

		// to get msg id from environment variable
		putEnvVriableIntoMapByKey("MSG_ID");

		if (this.envList.size() == 0) {
			isExecuteAtLocal = true;
			this.envList.add("local");
		} else {
			isExecuteAtLocal = false;
		}
		this.scenario = getProperty(ConfigParameterConstants.SCENARIO, "").trim();
	}

	public void reload() throws IOException {
		this.config = CommonUtils.getPropertiesWithPriority(filename);

		if (isExecuteAtLocal == false) {
			this.envList = initEnvList(config);
			this.followerList = initFollowerList(config);
			this.selectorList = initSelectorList(config);
		}

		this.toolHome = com.navercorp.cubridqa.common.CommonUtils.getEnvInFile(Constants.ENV_CTP_HOME_KEY);
		this.cleanTestCase = com.navercorp.cubridqa.common.CommonUtils.convertBoolean(getProperty(ConfigParameterConstants.TESTCASE_UPDATE_YES_OR_NO, "false"));
		this.isWindows = getTestPlatform().equalsIgnoreCase("windows");

		this.testCaseSkipKey = getProperty(ConfigParameterConstants.TESTCASE_EXCLUDE_BY_MACRO, "").trim().toUpperCase();
		if (this.testCaseSkipKey.equals("")) {
			this.testCaseSkipKey = null;
		}

		this.bigSpaceDir = getProperty(ConfigParameterConstants.LARGE_SPACE_DIR, "").trim();
		this.maxRetryCount = Integer.parseInt(CommonUtils.isEmpty(getProperty(ConfigParameterConstants.TESTCASE_RETRY_NUM, "0").trim()) ? "0" : getProperty(
				ConfigParameterConstants.TESTCASE_RETRY_NUM, "0").trim());
		this.defaultDbCharset = getProperty(ConfigParameterConstants.CUBRID_DB_CHARSET, "en_US").trim();

		this.enableCheckDiskSpace = com.navercorp.cubridqa.common.CommonUtils.convertBoolean(getProperty(ConfigParameterConstants.ENABLE_CHECK_DISK_SPACE_YES_OR_NO, "FALSE").trim());
		this.mailNoticeTo = getProperty(ConfigParameterConstants.TEST_OWNER_EMAIL, "").trim();

		this.enableSaveNormalErrorLog = com.navercorp.cubridqa.common.CommonUtils.convertBoolean(getProperty(ConfigParameterConstants.ENABLE_SAVE_LOG_ONCE_FAIL_YES_OR_NO, "FALSE").trim());

		this.isContinueMode = com.navercorp.cubridqa.common.CommonUtils.convertBoolean(getProperty(ConfigParameterConstants.TEST_CONTINUE_YES_OR_NO, "false").trim());
		this.cubridPackageUrl = getProperty(ConfigParameterConstants.CUBRID_DOWNLOAD_URL);

		this.serviceProtocolType = getProperty(ConfigParameterConstants.AGENT_PROTOCOL, "ssh").trim().toLowerCase();
		this.enableSkipUpdate = getPropertyFromEnv(ConfigParameterConstants.CTP_SKIP_UPDATE, "1");
		this.ctpBranchName = getPropertyFromEnv(ConfigParameterConstants.CTP_BRANCH_NAME, "master");
		this.skipToSaveSuccCase = com.navercorp.cubridqa.common.CommonUtils.convertBoolean(getProperty(ConfigParameterConstants.FEEDBACK_DB_SKIP_SAVE_SUCC_TESTCASE_YES_OR_NO, "false").trim());
		this.testCategory = getProperty(ConfigParameterConstants.TEST_CATEGORY, getResultName()).trim();
		this.reserveDiskSpaceSize = getProperty(ConfigParameterConstants.RESERVE_DISK_SPACE_SIZE, ConfigParameterConstants.RESERVE_DISK_SPACE_SIZE_DEFAULT_VALUE).trim();
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
		String envId;
		String envType;
		while (it.hasNext()) {
			key = (String) it.next();
			if (key.startsWith(ConfigParameterConstants.TEST_INSTANCE_PREFIX) && key.endsWith("." + ConfigParameterConstants.TEST_INSTANCE_HOST_SUFFIX)) {
				envId = key.substring(ConfigParameterConstants.TEST_INSTANCE_PREFIX.length(), key.indexOf("." + ConfigParameterConstants.TEST_INSTANCE_HOST_SUFFIX));
				envType = config.getProperty(ConfigParameterConstants.TEST_INSTANCE_PREFIX + envId + "." + ConfigParameterConstants.INSTANCE_TYPE);
				if (envType == null || envType.trim().equals("") || envType.trim().equals("follow") == false) {
					resultList.add(envId);
				}
			}
		}
		return resultList;
	}
	
	public static ArrayList<String> initFollowerList(Properties config) {
		ArrayList<String> resultList = new ArrayList<String>();
		Set<Object> set = config.keySet();
		Iterator<Object> it = set.iterator();
		String key;
		String envId;
		String envType;
		while (it.hasNext()) {
			key = (String) it.next();
			if (key.startsWith(ConfigParameterConstants.TEST_INSTANCE_PREFIX) && key.endsWith("." + ConfigParameterConstants.TEST_INSTANCE_HOST_SUFFIX)) {
				envId = key.substring(ConfigParameterConstants.TEST_INSTANCE_PREFIX.length(), key.indexOf("." + ConfigParameterConstants.TEST_INSTANCE_HOST_SUFFIX));
				envType = config.getProperty(ConfigParameterConstants.TEST_INSTANCE_PREFIX + envId + "." + ConfigParameterConstants.INSTANCE_TYPE);
				if (envType != null && envType.trim().equals("follow")) {
					resultList.add(envId.trim());
				}
			}
		}
		return resultList;
	}
	
	public static ArrayList<Selector> initSelectorList(Properties config) {
		ArrayList<Selector> resultList = new ArrayList<Selector>();
		Set<Object> set = config.keySet();
		Iterator<Object> it = set.iterator();
		String key, k, v;
		Selector s;
		while (it.hasNext()) {
			key = (String) it.next();
			if (key.startsWith(ConfigParameterConstants.TEST_SELECTOR_PREFIX) && key.endsWith("." + ConfigParameterConstants.TEST_SELECTOR_SUFFIX)) {
				k = key.substring(ConfigParameterConstants.TEST_SELECTOR_PREFIX.length(), key.indexOf("." + ConfigParameterConstants.TEST_SELECTOR_SUFFIX));
				s = new Selector(k, config.getProperty(key));
				resultList.add(s);
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

	public String getInstanceProperty(String envId, String key) {
		String value = getProperty(ConfigParameterConstants.TEST_INSTANCE_PREFIX + envId + "." + key);
		return value == null ? getProperty("default." + key, "") : value;
	}

	public String getExcludedFile() {
		return getProperty(ConfigParameterConstants.TESTCASE_EXCLUDE_FROM_FILE, "").trim();
	}

	public String getProperty(String key, String defaultValue) {
		return this.config.getProperty(key, defaultValue);
	}

	public String getPropertyFromEnv(String key, String defaultValue) {
		if (key == null || key.length() <= 0)
			return defaultValue;

		String val = System.getenv(key);
		return (val == null || defaultValue.length() <= 0) ? defaultValue : val;
	}

	public ArrayList<String> getRelatedHosts(String envId) {
		String[] relates = getProperty(ConfigParameterConstants.TEST_INSTANCE_PREFIX + envId + "." + ConfigParameterConstants.TEST_INSTANCE_RELATED_HOSTS_SUFFIX, "").split(",");
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
	
	public ArrayList<String> getFollowerList() {
		return this.followerList;
	}

	public ArrayList<Selector> getSelectorList() {
		return this.selectorList;
	}
	
	public Selector getSelector(String id) {
		if (selectorList == null) {
			return null;
		}

		for (Selector s : selectorList) {
			if (s.getId().equals(id)) {
				return s;
			}
		}
		return null;
	}

	public boolean isContinueMode() {
		return isContinueMode;
	}

	public void setContinueMode(boolean isContinueMode) {
		this.isContinueMode = isContinueMode;
	}

	public String getTestCaseRoot() {
		return this.scenario;
	}

	public void setTestCaseRoot(String scenario) {
		this.scenario = scenario;
	}

	public String getTestCaseBranch() {
		return getProperty(ConfigParameterConstants.TESTCASE_GIT_BRANCH, "").trim();
	}

	public String getTestCaseWorkspace() {
		String ws = getProperty(ConfigParameterConstants.TESTCASE_WORKSPACE_DIR, "").trim();
		if (ws.equals("")) {
			return getTestCaseRoot();
		} else {
			return ws;
		}
	}

	public String getTestPlatform() {
		return getProperty(ConfigParameterConstants.TEST_PLATFORM, "linux");
	}

	public String getTestCategory() {
		return this.testCategory;
	}

	public void setTestCategory(String category) {
		this.testCategory = category;
	}

	public boolean needCleanTestCase() {
		return this.cleanTestCase;
	}

	public String getTestCaseTimeout() {
		return getProperty(ConfigParameterConstants.TESTCASE_TIMEOUT_IN_SECS, "-1");
	}

	public boolean needEnableMonitorTrace() {
		return com.navercorp.cubridqa.common.CommonUtils.convertBoolean(getProperty(ConfigParameterConstants.ENABLE_STATUS_TRACE_YES_OR_NO, "false"));
	}

	public String getExcludedCoresByAssertLine() {
		return getProperty(ConfigParameterConstants.IGNORE_CORE_BY_KEYWORDS);
	}

	public boolean needDeleteTestCaseAfterTest() {
		return com.navercorp.cubridqa.common.CommonUtils.convertBoolean(getProperty(ConfigParameterConstants.DELETE_TESTCASE_AFTER_EACH_EXECUTION_YES_OR_NO, "false").trim());
	}

	public void setCubridPackageUrl(String cubridPackageUrl) {
		this.cubridPackageUrl = cubridPackageUrl;
	}

	public String getCubridPackageUrl() {
		return this.cubridPackageUrl;
	}

	public String getSVNUserInfo() {
		String user = getProperty(ConfigParameterConstants.SVN_USER, "");
		String pwd = getProperty(ConfigParameterConstants.SVN_PASSWORD, "");
		if (user.trim().equals("") || pwd.trim().equals(""))
			return "";
		return " --username " + user + " --password " + pwd + " --non-interactive ";
	}

	public boolean isScenarioInGit() {
		String git = getTestCaseBranch();
		return git != null && git.trim().length() > 0;
	}

	private void putMKeyEnvVaribleIntoMap() {
		Map map = System.getenv();
		Iterator it = map.entrySet().iterator();
		while (it.hasNext()) {
			Entry entry = (Entry) it.next();
			if (entry.getKey() != null && entry.getKey().toString().indexOf("MKEY") != -1) {
				envMap.put(entry.getKey().toString(), entry.getValue().toString());
			}
		}
	}

	private void putEnvVriableIntoMapByKey(String key) {
		if (key == null || key.length() <= 0)
			return;

		String val = System.getenv(key);
		envMap.put(key, val);

	}

	public boolean isWindows() {
		return this.isWindows;
	}

	public Feedback getFeedback() {
		if (this.feedback == null) {
			String feedbackType = getProperty(ConfigParameterConstants.FEEDBACK_TYPE, "file").trim();
			if (feedbackType.equalsIgnoreCase("file")) {
				this.feedback = new FeedbackFile(this);
			} else if (feedbackType.equalsIgnoreCase("database")) {
				this.feedback = new FeedbackDB(this);
			} else {
				this.feedback = new FeedbackNull();
			}
		}
		return this.feedback;
	}

	public String getCurrentLogDir() {
		return currentLogDir;
	}

	public String getRootLogDir() {
		return rootLogDir;
	}

	public boolean isReInstallTestBuildYn() {
		return reInstallTestBuildYn;
	}

	public void setReInstallTestBuildYn(boolean reInstallTestBuildYn) {
		this.reInstallTestBuildYn = reInstallTestBuildYn;
	}

	public String getFeedbackDbUrl() {
		String host = getProperty(ConfigParameterConstants.FEEDBACK_DB_HOST, "");
		String port = getProperty(ConfigParameterConstants.FEEDBACK_DB_PORT, "");
		String dbname = getProperty(ConfigParameterConstants.FEEDBACK_DB_NAME, "");

		String url = "jdbc:cubrid:" + host + ":" + port + ":" + dbname + ":::";

		return url;
	}

	public String getFeedbackDbUser() {
		String user = getProperty(ConfigParameterConstants.FEEDBACK_DB_USER, "");

		return user;
	}

	public String getFeedbackDbPwd() {
		String pwd = getProperty(ConfigParameterConstants.FEEDBACK_DB_PASSWORD, "");

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

	public Properties getProperties() {
		return this.config;
	}

	public String getTestCaseSkipKey() {
		return this.testCaseSkipKey;
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

	public String getEnableSkipUpdate() {
		return enableSkipUpdate;
	}

	public void setEnableSkipUpgrade(String enableSkipUpdate) {
		this.enableSkipUpdate = enableSkipUpdate;
	}

	public String getMailNoticeTo() {
		return this.mailNoticeTo;
	}

	public String getMailNoticeCC() {
		String cc = getProperty(ConfigParameterConstants.TEST_CC_EMAIL, "").trim();
		if (CommonUtils.isEmpty(cc)) {
			return com.navercorp.cubridqa.common.Constants.MAIL_FROM;
		} else {
			return cc;
		}
	}

	public boolean enableCheckDiskSpace() {
		return enableCheckDiskSpace;
	}
	
	public String getReserveDiskSpaceSize() {
		return this.reserveDiskSpaceSize;
	}

	public boolean isSkipToSaveSuccCase() {
		return skipToSaveSuccCase;
	}

	public void setSkipToSaveSuccCase(boolean skipToSaveSuccCase) {
		this.skipToSaveSuccCase = skipToSaveSuccCase;
	}

	public String getProperty(String key1, String key2, boolean reverse) {
		String value1 = getProperty(key1);
		String value2 = System.getProperty(key2);
		if (CommonUtils.isEmpty(value2)) {
			value2 = System.getenv(key2);
		}

		if (reverse) {
			return CommonUtils.isEmpty(value2) ? value1 : value2;
		} else {
			return CommonUtils.isEmpty(value1) ? value2 : value1;
		}
	}

	public boolean isExecuteAtLocal() {
		return isExecuteAtLocal;
	}
	
	private String getResultName() {
		String name = System.getProperty("TEST_CATEGORY");
		if (CommonUtils.isEmpty(name)) {
			return "shell";
		} else {
			return name;
		}
	}
	
	public String getSelectorProperty(String selectorName, String key) {
		if (CommonUtils.isEmpty(selectorName)) {
			return null;
		}
		return getProperty(ConfigParameterConstants.TEST_SELECTOR_PREFIX + selectorName.trim() + "." + key.trim());
	}
}
