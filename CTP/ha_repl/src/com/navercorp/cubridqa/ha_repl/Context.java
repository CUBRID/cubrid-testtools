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
import com.navercorp.cubridqa.ha_repl.impl.FeedbackDB;
import com.navercorp.cubridqa.ha_repl.impl.FeedbackFile;
import com.navercorp.cubridqa.ha_repl.impl.FeedbackNull;

public class Context {

	Properties config;

	String filename;

	Feedback feedback;
	
	private String ctpHome;
	private String rootLogDir;
	private String currentLogDir;

	private String buildBits;
	private String buildId;
	private ArrayList<String> testEnvList = new ArrayList<String>();
	private boolean enableCheckDiskSpace;
	String mailNoticeTo;

	public Context(String filename) throws IOException {
		this.filename = filename;
		reload();
	}

	public void reload() throws IOException {
		this.config = CommonUtils.getPropertiesWithPriority(filename);
		
		this.ctpHome = CommonUtils.getEnvInFile (com.navercorp.cubridqa.common.Constants.ENV_CTP_HOME_KEY);
		setLogDir("ha_repl");
		
		Feedback feedback;
		String feedbackType = getProperty("main.feedback.type", "").trim();
		if (feedbackType.equalsIgnoreCase("file")) {
			feedback = new FeedbackFile(this);
		} else if (feedbackType.equalsIgnoreCase("database")) {
			feedback = new FeedbackDB(this);
		} else {
			feedback = new FeedbackNull(this);
		}
		setFeedback(feedback);
		
		Set<Object> set = config.keySet();
		Iterator<Object> it = set.iterator();
		String key;
		while (it.hasNext()) {
			key = (String) it.next();
			if (key.startsWith("env.") && key.endsWith(".master.ssh.host")) {
				testEnvList.add(key.substring(4, key.indexOf(".master.ssh.host")));
			}
		}
		
		this.enableCheckDiskSpace = CommonUtils.convertBoolean(getProperty("main.testing.enable_check_disk_space", "FALSE").trim());
		this.mailNoticeTo = getProperty("main.owner.mail", "").trim();
	}
	
	public ArrayList<String> getTestEnvList() {
		return this.testEnvList;
	}

	public void setFeedback(Feedback feedback) {
		this.feedback = feedback;
	}

	public Feedback getFeedback() {
		return this.feedback;
	}

	public String getProperty(String key, String defaultValue) {
		return this.config.getProperty(key, defaultValue);
	}

	public String getProperty(String key) {
		return getProperty(key, null);
	}

	public boolean isContinueMode() {
		return CommonUtils.convertBoolean(getProperty("main.mode.continue", "false"));
	}

	public String getDBUrl() {
		String host = getProperty("ha.master.ssh.host", "");
		String port = getProperty("feedback.db.port", "");
		String dbname = getProperty("feedback.db.name", "");

		String url = "jdbc:cubrid:" + host + ":" + port + ":" + dbname + ":::";

		return url;

	}

	public String getDbUser() {
		String user = getProperty("ha.master.ssh.user", "");

		return user;
	}

	public String getDbPwd() {
		String pwd = getProperty("ha.master.ssh.password", "");

		return pwd;
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

	public Properties getProperties() {
		return this.config;
	}

	public String getTestmode() {
		String testmode = getProperty("main.testmode", "jdbc");

		return testmode;
	}

	public boolean isFailureBackup() {
		return getProperty("main.testing.failure.backup", "false").toUpperCase().trim().equals("TRUE");
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
	
	public String getCtpHome() {
		return this.ctpHome;
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
		return getProperty("main.testbuild.url", "").trim();
	}
	
	public String getDiffMode() {
		return getProperty("main.differ.mode", "diff_1").trim();
	}
	
	public String getTestCaseRoot() {
		return getProperty("main.testcase.root", "").trim();
	}
	
	public boolean rebuildYn() {
		String rebuildEnv = getProperty("main.deploy.rebuild_yn", "true");
		return CommonUtils.convertBoolean(rebuildEnv);
	}
	
	public String getExcludedTestCaseFile() {
		return getProperty("main.testcase.excluded");
	}
	
	public String getInstanceProperty(String envId, String key) {
		String value = getProperty("env." + envId + "." + key);
		if (CommonUtils.isEmpty(value)) {
			value = getProperty("default." + key);
		}
		return value;
	}
	
	public boolean shouldCleanupAfterQuit() {
		return CommonUtils.convertBoolean(getProperty("main.testing.cleanup_after_quit_yn", "true"));
	}
	
	public boolean enableCheckDiskSpace() {
		return enableCheckDiskSpace;
	}
	
	public String getMailNoticeTo() {
		return this.mailNoticeTo;		
	}

}
