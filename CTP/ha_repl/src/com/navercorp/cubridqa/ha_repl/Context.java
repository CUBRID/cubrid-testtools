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
import java.util.Properties;

import com.navercorp.cubridqa.ha_repl.common.CommonUtils;

public class Context {

	Properties config;

	String filename;

	Feedback feedback;

	private String versionId;
	private String bits;

	public Context(String filename) throws IOException {
		this.filename = filename;
		reload();
	}

	public void reload() throws IOException {
		this.config = CommonUtils.getPropertiesWithPriority(filename);
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

	public String getVersionId() {
		return versionId;
	}

	public void setVersionId(String versionId) {
		this.versionId = versionId;
	}

	public String getBits() {
		return bits;
	}

	public void setBits(String bits) {
		this.bits = bits;
	}

	public boolean isContinueMode() {
		String value = getProperty("main.mode.continue", "false");
		return value.equalsIgnoreCase("true");
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

}
