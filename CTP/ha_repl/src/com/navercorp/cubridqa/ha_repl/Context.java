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

package com.navercorp.cubridqa.ha_repl;

import java.io.IOException;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Properties;
import java.util.Set;

import com.navercorp.cubridqa.common.CommonUtils;
import com.navercorp.cubridqa.common.ConfigParameterConstants;
import com.navercorp.cubridqa.common.Constants;
import com.navercorp.cubridqa.ha_repl.impl.FeedbackDB;
import com.navercorp.cubridqa.ha_repl.impl.FeedbackFile;
import com.navercorp.cubridqa.ha_repl.impl.FeedbackNull;

public class Context {

	Properties config;

	String filename;

	Feedback feedback;

	private String rootLogDir;
	private String currentLogDir;

	private String buildBits;
	private String buildId;
	private ArrayList<String> testEnvList = new ArrayList<String>();
	private boolean enableCheckDiskSpace;
	private String reserveDiskSpaceSize;
	private boolean reInstallTestBuildYn = false;
	String mailNoticeTo;
	String scenario;
	private int haSyncDetectTimeoutInMs;
	private int haSyncFailureResolveMode;

	public Context(String filename) throws IOException {
		this.filename = filename;
		reload();
		setLogDir("ha_repl");
		this.scenario = CommonUtils.translateVariable(getProperty(ConfigParameterConstants.SCENARIO, "").trim());
	}

	public void reload() throws IOException {
		this.config = CommonUtils.getPropertiesWithPriority(filename);

		Set<Object> set = config.keySet();
		Iterator<Object> it = set.iterator();
		String key;
		while (it.hasNext()) {
			key = (String) it.next();
			if (key.startsWith(ConfigParameterConstants.TEST_INSTANCE_PREFIX) && key.endsWith(".master." + ConfigParameterConstants.TEST_INSTANCE_HOST_SUFFIX)) {
				testEnvList.add(key.substring(4, key.indexOf(".master." + ConfigParameterConstants.TEST_INSTANCE_HOST_SUFFIX)));
			}
		}

		this.enableCheckDiskSpace = CommonUtils.convertBoolean(getProperty(ConfigParameterConstants.ENABLE_CHECK_DISK_SPACE_YES_OR_NO, "FALSE").trim());
		this.mailNoticeTo = getProperty(ConfigParameterConstants.TEST_OWNER_EMAIL, "").trim();
		this.reserveDiskSpaceSize = getProperty(ConfigParameterConstants.RESERVE_DISK_SPACE_SIZE, ConfigParameterConstants.RESERVE_DISK_SPACE_SIZE_DEFAULT_VALUE).trim();
		
		try {
			haSyncDetectTimeoutInMs = Integer.parseInt(getProperty(ConfigParameterConstants.HA_SYNC_DETECT_TIMEOUT_IN_SECS)) * 1000;
			if (haSyncDetectTimeoutInMs < 0) {
				haSyncDetectTimeoutInMs = com.navercorp.cubridqa.ha_repl.common.Constants.HA_SYNC_DETECT_TIMEOUT_IN_MS_DEFAULT;
			}
		} catch (Exception e) {
			haSyncDetectTimeoutInMs = com.navercorp.cubridqa.ha_repl.common.Constants.HA_SYNC_DETECT_TIMEOUT_IN_MS_DEFAULT;
		}		
		
		String haSyncFailureResolveModeValue = getProperty(ConfigParameterConstants.HA_SYNC_FAILURE_RESOLVE_MODE);
		if (CommonUtils.isEmpty(haSyncFailureResolveModeValue)) {
			haSyncFailureResolveMode = com.navercorp.cubridqa.ha_repl.common.Constants.HA_SYNC_FAILURE_RESOLVE_MODE_CONTINUE;
		} else {
			haSyncFailureResolveModeValue = haSyncFailureResolveModeValue.trim().toUpperCase();
			if (haSyncFailureResolveModeValue.equals("STOP")) {
				haSyncFailureResolveMode = com.navercorp.cubridqa.ha_repl.common.Constants.HA_SYNC_FAILURE_RESOLVE_MODE_STOP;
			} else if (haSyncFailureResolveModeValue.equals("WAIT")) {
				haSyncFailureResolveMode = com.navercorp.cubridqa.ha_repl.common.Constants.HA_SYNC_FAILURE_RESOLVE_MODE_WAIT;
			} else {
				haSyncFailureResolveMode = com.navercorp.cubridqa.ha_repl.common.Constants.HA_SYNC_FAILURE_RESOLVE_MODE_CONTINUE;
			}
		}
	}

	public ArrayList<String> getTestEnvList() {
		return this.testEnvList;
	}

	public Feedback getFeedback() {

		if (this.feedback == null) {
			String feedbackType = getProperty(ConfigParameterConstants.FEEDBACK_TYPE, "file").trim();
			if (feedbackType.equalsIgnoreCase("file")) {
				this.feedback = new FeedbackFile(this);
			} else if (feedbackType.equalsIgnoreCase("database")) {
				this.feedback = new FeedbackDB(this);
			} else {
				this.feedback = new FeedbackNull(this);
			}
		}
		return this.feedback;
	}

	public String getProperty(String key, String defaultValue) {
		return this.config.getProperty(key, defaultValue);
	}

	public String getProperty(String key) {
		return getProperty(key, null);
	}

	public boolean isContinueMode() {
		return CommonUtils.convertBoolean(getProperty(ConfigParameterConstants.TEST_CONTINUE_YES_OR_NO, "false"));
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

	public Properties getProperties() {
		return this.config;
	}

	public String getTestmode() {
		String testmode = getProperty(ConfigParameterConstants.TEST_INTERFACE_TYPE, "jdbc");

		return testmode;
	}

	public boolean rebuildYn() {
		String rebuildEnv = getProperty(ConfigParameterConstants.TEST_REBUILD_ENV_YES_OR_NO, "true");
		return CommonUtils.convertBoolean(rebuildEnv);
	}

	public boolean isFailureBackup() {
		return getProperty(ConfigParameterConstants.ENABLE_SAVE_LOG_ONCE_FAIL_YES_OR_NO, "false").toUpperCase().trim().equals("TRUE");
	}

	public void setLogDir(String category) {
		this.rootLogDir = Constants.ENV_CTP_HOME + "/result/" + category;
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

	public String getCubridPackageUrl() {
		return getProperty(ConfigParameterConstants.CUBRID_DOWNLOAD_URL, "").trim();
	}

	public String getDiffMode() {
		return getProperty(ConfigParameterConstants.TESTCASE_ADDITIONAL_ANSWER, "diff_1").trim();
	}

	public String getTestCaseRoot() {
		return this.scenario;
	}

	public boolean isReInstallTestBuildYn() {
		return reInstallTestBuildYn;
	}

	public void setReInstallTestBuildYn(boolean reInstallTestBuildYn) {
		this.reInstallTestBuildYn = reInstallTestBuildYn;
	}

	public String getExcludedTestCaseFile() {
		return CommonUtils.translateVariable(getProperty(ConfigParameterConstants.TESTCASE_EXCLUDE_FROM_FILE, "").trim());
	}

	public boolean shouldCleanupAfterQuit() {
		return CommonUtils.convertBoolean(getProperty(ConfigParameterConstants.CLEAN_PROCESS_AFTER_EXECUTION_QUIT_YES_OR_NO, "true"));
	}

	public boolean enableCheckDiskSpace() {
		return enableCheckDiskSpace;
	}

	public boolean enableSkipMakeLocale() {
		return CommonUtils.convertBoolean(getProperty(ConfigParameterConstants.ENABLE_SIKP_MAKE_LOCALE_YES_OR_NO, "false"));
	}

	public String getTestPlatform() {
		return getProperty(ConfigParameterConstants.TEST_PLATFORM, "linux");
	}

	public String getTestCategory() {
		return getProperty(ConfigParameterConstants.TEST_CATEGORY, "ha_repl");
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
	
	public String getReserveDiskSpaceSize() {
		return this.reserveDiskSpaceSize;
	}
	
	public int getHaSyncDetectTimeoutInMs() {
		return this.haSyncDetectTimeoutInMs;
	}
	
	public int getHaSyncFailureResolveMode() {
		return this.haSyncFailureResolveMode;
	}	
}
