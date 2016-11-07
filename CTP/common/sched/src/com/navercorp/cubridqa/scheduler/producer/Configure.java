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
package com.navercorp.cubridqa.scheduler.producer;

import java.io.File;
import java.io.IOException;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Properties;
import java.util.Set;

import com.navercorp.cubridqa.scheduler.common.CommonUtils;
import com.navercorp.cubridqa.scheduler.common.Constants;
import com.navercorp.cubridqa.scheduler.producer.crontab.CUBJobContext;

public class Configure {

	Properties props;

	public Configure() throws Exception {

		props = new Properties();

		File file = new File(Constants.CTP_HOME + File.separator + "conf" + File.separator + "common.conf");
		if (file.exists()) {
			props.putAll(CommonUtils.getProperties(file));
		}

		file = new File(Constants.CTP_HOME + File.separator + "conf" + File.separator + "producer.conf");
		if (file.exists()) {
			props.putAll(CommonUtils.getProperties(file));
		}

		file = new File(Constants.CTP_HOME + File.separator + "conf" + File.separator + "job.conf");
		if (file.exists()) {
			props.putAll(CommonUtils.getProperties(file));
		}

		String driver = props.getProperty("qahome_db_driver");
		try {
			Class.forName(driver);
		} catch (Exception e) {
			System.out.println("[ERROR] fail to load JDBC Driver, please refer to qahome_db_driver parameter in dailydb.conf file.");
		}
	}

	public String getProperty(String key) {
		return getProperty(key, null);
	}

	public String getProperty(String key, String defaultValue) {
		return this.props.getProperty(key, defaultValue);
	}

	public String getRepoRoot() {
		return getProperty("build.repository.root");
	}

	public String getWebBaseUrl() {
		return getProperty("build.web.baseurl");
	}

	public String getWebBaseUrl_Kr() {
		return getProperty("build.web.baseurl_kr");
	}

	public String getWebBaseUrl_KrRepo1() {
		return getProperty("build.web.baseurl_kr_repo1");
	}

	public boolean isGenerateMessage() {
		return getProperty("build.message.generate", "true").equalsIgnoreCase("true");
	}

	public boolean isGenerateMessageTest() {
		return getProperty("build.message.generate", "true").equalsIgnoreCase("test");
	}

	public Properties getProperties() {
		return this.props;
	}

	public boolean isAcceptableBuildToListen(String buildId) {
		String v1 = getProperty("build.listener.acceptlist.service", "off");
		if (v1.equalsIgnoreCase("on")) {
			return contain("build.listener.acceptlist.content", buildId);
		}

		String v2 = getProperty("build.listener.denylist.service", "off");
		if (v2.equalsIgnoreCase("on")) {
			return !contain("build.listener.denylist.content", buildId);
		}

		return true;
	}

	public boolean isAcceptableBuildInRepository(String buildId) {
		String v1 = getProperty("build.repository.denylist.service", "off");
		if (v1.equalsIgnoreCase("on")) {
			return !contain("build.repository.denylist.content", buildId);
		}
		return true;
	}

	private boolean contain(String resKey, String buildId) {
		return CommonUtils.isBuildInRules(getProperty(resKey, ""), buildId);
	}

	public ArrayList<String[]> filterProps(String keyPrefix, String keySuffix, String expectedValue) {
		ArrayList<String[]> list = new ArrayList<String[]>();
		Set set = props.keySet();
		Iterator<String> it = set.iterator();
		String key, value;
		String[] pair;
		while (it.hasNext()) {
			key = it.next().trim();
			if (key == null)
				continue;

			key = key.trim();
			value = props.getProperty(key);
			if (value == null)
				continue;
			value = value.trim();

			if ((keyPrefix == null || key.startsWith(keyPrefix)) && (keySuffix == null || key.endsWith(keySuffix)) && (expectedValue == null || value.equalsIgnoreCase(expectedValue))) {
				pair = new String[2];
				pair[0] = key;
				pair[1] = value;
				list.add(pair);
			}
		}
		return list;
	}

	public ArrayList<CUBJobContext> findValidJobContexts() {
		ArrayList<CUBJobContext> resultList = new ArrayList<CUBJobContext>();
		ArrayList<String[]> mainKVList = filterProps("job", ".service", "ON");
		String jobId;
		for (String[] kv : mainKVList) {
			jobId = CommonUtils.replace(kv[0], ".service", "");
			CUBJobContext jctx = new CUBJobContext(this, jobId);
			resultList.add(jctx);
		}
		return resultList;
	}

	public static void main(String[] args) throws Exception {
		Configure c = new Configure();
		ArrayList<CUBJobContext> resultList = c.findValidJobContexts();
		for (CUBJobContext jctx : resultList) {
			System.out.println(jctx);
			for (Properties props : jctx.getTestList()) {
				System.out.println(props);
			}
		}
	}

}
