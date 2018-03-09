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

package com.navercorp.cubridqa.isolation;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Properties;
import java.util.Set;

import com.navercorp.cubridqa.common.CommonUtils;
import com.navercorp.cubridqa.common.ConfigParameterConstants;
import com.navercorp.cubridqa.isolation.impl.FeedbackDB;
import com.navercorp.cubridqa.isolation.impl.FeedbackFile;
import com.navercorp.cubridqa.isolation.impl.FeedbackNull;

public class Context {

	private String ctpHome;
	private String rootLogDir;
	private String currentLogDir;

	private Properties config;

	private boolean isContinueMode = false;

	private ArrayList<String> envList;

	private boolean shouldUpdateTestCase = false;

	private String filename;

	private boolean isWindows = false;

	private Feedback feedback;

	private String buildId;

	private String buildBits;

	private String mailNoticeTo;
	private boolean enableCheckDiskSpace;
	private String reserveDiskSpaceSize;
	private boolean reInstallTestBuildYn = false;
	private String scenario;
	private boolean isExecuteAtLocal = false;

	public Context(String filename) throws IOException {
		this.filename = filename;
		reload();

		setLogDir("isolation");

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
		}

		this.ctpHome = com.navercorp.cubridqa.common.CommonUtils.getEnvInFile(com.navercorp.cubridqa.common.Constants.ENV_CTP_HOME_KEY);

		this.shouldUpdateTestCase = CommonUtils.convertBoolean(getProperty(ConfigParameterConstants.TESTCASE_UPDATE_YES_OR_NO, "false")) && !CommonUtils.isEmpty(getTestCaseBranch());
		this.isWindows = getTestPlatform().equalsIgnoreCase("windows");
		this.isContinueMode = CommonUtils.convertBoolean(getProperty(ConfigParameterConstants.TEST_CONTINUE_YES_OR_NO, "false"));

		this.enableCheckDiskSpace = CommonUtils.convertBoolean(getProperty(ConfigParameterConstants.ENABLE_CHECK_DISK_SPACE_YES_OR_NO, "FALSE").trim());
		this.reserveDiskSpaceSize = getProperty(ConfigParameterConstants.RESERVE_DISK_SPACE_SIZE, ConfigParameterConstants.RESERVE_DISK_SPACE_SIZE_DEFAULT_VALUE).trim();
		this.mailNoticeTo = getProperty(ConfigParameterConstants.TEST_OWNER_EMAIL, "").trim();
	}

	public static ArrayList<String> initEnvList(Properties config) {
		ArrayList<String> resultList = new ArrayList<String>();
		Set<Object> set = config.keySet();
		Iterator<Object> it = set.iterator();
		String key;
		while (it.hasNext()) {
			key = (String) it.next();
			if (key.startsWith(ConfigParameterConstants.TEST_INSTANCE_PREFIX) && key.endsWith("." + ConfigParameterConstants.TEST_INSTANCE_HOST_SUFFIX)) {
				resultList.add(key.substring(4, key.indexOf("." + ConfigParameterConstants.TEST_INSTANCE_HOST_SUFFIX)));
			}
		}
		return resultList;
	}

	public String getProperty(String key, String defaultValue) {
		return this.config.getProperty(key, defaultValue);
	}

	public String getProperty(String key) {
		return getProperty(key, null);
	}

	public ArrayList<String> getEnvList() {
		return this.envList;
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

	public boolean isContinueMode() {
		return this.isContinueMode;
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

	public String getTestCategory() {
		return getProperty(ConfigParameterConstants.TEST_CATEGORY, "isolation");
	}

	public String getTestPlatform() {
		return getProperty(ConfigParameterConstants.TEST_PLATFORM, "linux");
	}

	public String getCubridPackageUrl() {
		return getProperty(ConfigParameterConstants.CUBRID_DOWNLOAD_URL);
	}

	public boolean shouldUpdateTestCase() {
		return shouldUpdateTestCase;
	}

	public boolean isWindows() {
		return this.isWindows;
	}

	public String getTestCaseTimeoutInSec() {
		return getProperty(ConfigParameterConstants.TESTCASE_TIMEOUT_IN_SECS, String.valueOf(Integer.MAX_VALUE));
	}

	public String getExclucdedFile() {
		return getProperty(ConfigParameterConstants.TESTCASE_EXCLUDE_FROM_FILE, "");
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

	public void setBuildId(String buildId) {
		this.buildId = buildId;
	}

	public String getBuildId() {
		return this.buildId;
	}

	public void setBuildBits(String bits) {
		this.buildBits = bits;
	}

	public String getBuildBits() {
		return this.buildBits;
	}

	public Properties getProperties() {
		return this.config;
	}

	public String getTestCaseBranch() {
		return getProperty(ConfigParameterConstants.TESTCASE_GIT_BRANCH).trim();
	}

	public boolean isReInstallTestBuildYn() {
		return reInstallTestBuildYn;
	}

	public void setReInstallTestBuildYn(boolean reInstallTestBuildYn) {
		this.reInstallTestBuildYn = reInstallTestBuildYn;
	}

	public String getTestingDatabase() {
		String v = this.config.getProperty(ConfigParameterConstants.CUBRID_TESTDB_NAME);
		if (v == null) {
			v = "cubrid";
		} else {
			v = v.trim().toLowerCase();
			if (v.equals("")) {
				v = "cubrid";
			}
		}
		return Constants.DB_TEST_MAP.get(v);
	}

	public void setLogDir(String category) {
		this.rootLogDir = ctpHome + "/result/" + category;
		this.currentLogDir = this.rootLogDir + "/current_runtime_logs";
		com.navercorp.cubridqa.common.CommonUtils.ensureExistingDirectory(this.rootLogDir);
		com.navercorp.cubridqa.common.CommonUtils.ensureExistingDirectory(this.currentLogDir);
	}

	public String getCurrentLogDir() {
		return this.currentLogDir;
	}

	public String getLogRootDir() {
		return this.rootLogDir;
	}

	public int getRetryTimes() {
		int retryTimes = 0;
		try {
			retryTimes = Integer.parseInt(getProperty(ConfigParameterConstants.TESTCASE_RETRY_NUM, "0"));
			if (retryTimes <= 0) {
				retryTimes = 0;
			}
		} catch (Exception e) {
			System.out.println("Fail to read 'testcase_retry_num' value");
		}
		return retryTimes;
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

	public boolean isExecuteAtLocal() {
		return isExecuteAtLocal;
	}
	
	public String getReserveDiskSpaceSize() {
		return this.reserveDiskSpaceSize;
	}
}
