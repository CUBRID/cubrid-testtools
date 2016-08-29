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

import com.navercorp.cubridqa.isolation.impl.FeedbackDB;
import com.navercorp.cubridqa.isolation.impl.FeedbackFile;
import com.navercorp.cubridqa.isolation.impl.FeedbackNull;
import com.navercorp.cubridqa.common.CommonUtils;

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
	private boolean rebuildYn = false;
	
	public Context(String filename) throws IOException {
		this.filename = filename;
		reload();
	}

	public void reload() throws IOException {
		this.config = CommonUtils.getPropertiesWithPriority(filename);
		this.envList = initEnvList(config);
		this.ctpHome = com.navercorp.cubridqa.common.CommonUtils.getEnvInFile (com.navercorp.cubridqa.common.Constants.ENV_CTP_HOME_KEY);
		setLogDir("isolation");
		
		this.shouldUpdateTestCase = getProperty("main.testcase.update_yn", "false").equalsIgnoreCase("true");
		this.isWindows = getProperty("main.testing.platform", "linux").equalsIgnoreCase("windows");
		this.isContinueMode = getProperty("main.mode.continue", "false").equalsIgnoreCase("true");
		String feedbackType = getProperty("main.feedback.type", "").trim();
		if (feedbackType.equalsIgnoreCase("file")) {
			this.feedback = new FeedbackFile(this);
		} else if (feedbackType.equalsIgnoreCase("database")) {
			this.feedback = new FeedbackDB(this);
		} else {
			this.feedback = new FeedbackNull();
		}
		
		this.enableCheckDiskSpace = CommonUtils.convertBoolean(getProperty("main.testing.enable_check_disk_space", "FALSE").trim());
		this.mailNoticeTo = getProperty("main.owner.mail", "").trim();
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

	public String getInstanceProperty(String envId, String key){
		String value = getProperty("env." + envId + "." + key);
		return value == null ? getProperty("default." + key, "") : value;
	}

	public boolean isContinueMode() {
		return this.isContinueMode;
	}

	public void setContinueMode(boolean isContinueMode) {
		this.isContinueMode = isContinueMode;
	}

	public String getTestCaseRoot() {
		return getProperty("main.testcase.root");
	}

	public String getCubridPackageUrl() {
		return getProperty("main.testbuild.url");
	}

	public boolean shouldUpdateTestCase() {
		return shouldUpdateTestCase;
	}

	public boolean isWindows() {
		return this.isWindows;
	}

	public void setFeedback(Feedback feedback) {
		this.feedback = feedback;
	}

	public Feedback getFeedback() {
		return this.feedback;
	}

	public String getFeedbackDbUrl() {
		String host = getProperty("feedback.db.host", "");
		String port = getProperty("feedback.db.port", "");
		String dbname = getProperty("feedback.db.name", "");

		String url = "jdbc:cubrid:" + host + ":" + port + ":" + dbname + ":::";

		return url;
	}

	public String getFeedbackDbUser() {
		String user = getProperty("feedback.db.user", "");
		return user;
	}

	public String getFeedbackDbPwd() {
		String pwd = getProperty("feedback.db.pwd", "");
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

	public boolean isRebuildYn() {
		return rebuildYn;
	}

	public void setRebuildYn(boolean rebuildYn) {
		this.rebuildYn = rebuildYn;
	}
	
	public boolean isStartMonitor() {
		return this.config.getProperty("main.testing.monitor", "false").equalsIgnoreCase("true");
	}

	public String getTestCaseBranch() {
		return getProperty("main.testcase.branch_git", "").trim();
	}

	public String getTestingDatabase() {
		String v = this.config.getProperty("main.testing.database");
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
			retryTimes = Integer.parseInt(getProperty("main.testcase.retry", "0"));
			if (retryTimes <= 0) {
				retryTimes = 0;
			}
		} catch (Exception e) {
			System.out.println("Fail to read 'main.testcase.retry' value");
		}
		return retryTimes;
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
