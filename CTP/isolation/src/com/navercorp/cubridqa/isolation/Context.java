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

import com.navercorp.cubridqa.shell.common.CommonUtils;

public class Context {

	Properties config;

	private boolean isContinueMode = false;

	ArrayList<String> envList;

	String cubridPackageUrl;

	private boolean cleanTestCase = false;

	private String filename;

	boolean isWindows = false;

	Feedback feedback;

	String build;

	String version;

	public Context(String filename) throws IOException {
		this.filename = filename;
		reload();
	}

	public void reload() throws IOException {
		this.config = CommonUtils.getPropertiesWithPriority(filename);
		this.envList = initEnvList(config);

		this.cleanTestCase = getProperty("main.testcase.clean", "false").equalsIgnoreCase("true");
		this.isWindows = getProperty("main.testing.platform", "linux").equalsIgnoreCase("windows");
		this.isContinueMode = getProperty("main.mode.continue", "false").equalsIgnoreCase("true");
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

	public boolean getCleanTestCase() {
		return cleanTestCase;
	}

	public String getSVNUserInfo() {
		String user = getProperty("main.svn.user", "");
		String pwd = getProperty("main.svn.pwd", "");
		if (user.trim().equals("") || pwd.trim().equals(""))
			return "";
		return " --username " + user + " --password " + pwd + " --non-interactive ";
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

	public void setTestBuild(String build) {
		this.build = build;
	}

	public String getTestBuild() {
		return this.build;
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

	public String getCtlHome() {
		return this.config.getProperty("ctl.home").trim();
	}

	public boolean isUpdateCTL() {
		return this.config.getProperty("ctl.git.update", "false").equalsIgnoreCase("true");
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

}
