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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Properties;

import com.navercorp.cubridqa.scheduler.common.CommonUtils;
import com.navercorp.cubridqa.scheduler.common.Constants;

//TODO: to be modified to extend AbstractExtendedSuite.java
public class Compatibility {

	private static Compatibility instance;

	Configure conf;
	HashMap<String, Properties> compatMap;
	Properties compatConfig;

	private Compatibility(Configure conf) {
		this.conf = conf;
		try {
			this.compatConfig = CommonUtils.getProperties(Constants.CTP_HOME + File.separator + "conf" + File.separator + "compat.conf");
		} catch (Exception e) {
			this.compatConfig = new Properties();
		}

		this.compatMap = new HashMap<String, Properties>();

		File[] files = new File(Constants.CTP_HOME + File.separator + "conf" + File.separator + "compat").listFiles();
		for (File f : files) {
			if (f.getName().endsWith(".msg")) {
				this.compatMap.put(f.getName(), loadMsgProperties(f));
			}
		}
	}

	public static void init(Configure conf) {
		instance = new Compatibility(conf);
	}

	public static Compatibility getInstance() {
		return instance;
	}

	private Properties loadMsgProperties(File file) {
		Properties props = null;
		try {
			props = CommonUtils.getProperties(file.getAbsolutePath());
		} catch (Exception e) {
			e.printStackTrace();
		}

		if (props == null)
			return props;

		String compatBuildId = props.getProperty(Constants.MSG_COMPAT_BUILD_ID);
		if (compatBuildId == null)
			return null;

		String compatBuildUrls = props.getProperty(Constants.MSG_COMPAT_BUILD_URLS);

		String urls = null;
		String urls_kr = null;

		String[] exactInfo = getExactBuildInfo(compatBuildId);
		if (exactInfo == null)
			return null;

		compatBuildId = exactInfo[0];
		props.put(Constants.MSG_COMPAT_BUILD_ID, compatBuildId);
		String storeId = exactInfo[1];
		compatBuildUrls = CommonUtils.replace(compatBuildUrls, "{max}", exactInfo[2]);
		urls = conf.getWebBaseUrl() + "/" + storeId + "/" + compatBuildId + "/drop/" + compatBuildUrls;
		urls_kr = conf.getWebBaseUrl_Kr() + "/" + compatBuildId + "/drop/" + compatBuildUrls;
		props.put(Constants.MSG_COMPAT_BUILD_URLS, urls);
		props.put(Constants.MSG_COMPAT_BUILD_URLS_KR, urls_kr);

		props.put(Constants.MSG_COMPAT_TEST_CATAGORY, CommonUtils.replace(file.getName(), ".msg", ""));
		props.put(Constants.MSG_MSG_FILEID, CommonUtils.replace(file.getName(), ".msg", ""));

		return props;
	}

	public void setCompatibility(String name, String value) {
		this.compatConfig.setProperty(name, value);
	}

	public ArrayList<Properties> getMsgProperties(String compatKey) {
		String value = this.compatConfig.getProperty(compatKey);
		if (value == null)
			return null;
		String[] list = value.split(",");

		ArrayList<Properties> resultList = new ArrayList<Properties>();

		Properties p;
		for (String item : list) {
			if (item.trim().equals(""))
				continue;

			p = this.compatMap.get(item.trim());

			if (p == null) {
				continue;
			}
			resultList.add(p);
		}
		return resultList;
	}

	// [0] buildId, [1] storeId
	private String[] getExactBuildInfo(String buildId) {

		File[] storeArray = new File(conf.getRepoRoot()).listFiles();
		File[] buildArray;
		String maxNum = "0";
		File justFile = null;

		int searchIndexForMax = buildId.indexOf("{max}");
		String searchPrefix = null;
		if (searchIndexForMax != -1) {
			searchPrefix = buildId.substring(0, searchIndexForMax);
		}

		String curBuildId, s1;
		int j1, j2;

		for (File storeFile : storeArray) {
			if (searchIndexForMax == -1 && justFile != null)
				break;
			if (storeFile.isDirectory() == false)
				continue;

			buildArray = storeFile.listFiles();
			for (File buildFile : buildArray) {
				if (buildFile.isDirectory() == false)
					continue;

				curBuildId = buildFile.getName();

				if (searchIndexForMax != -1) {
					if (conf.isAcceptableBuildInRepository(curBuildId) && curBuildId.startsWith(searchPrefix)) {
						j1 = Integer.parseInt(maxNum);
						try {
							s1 = curBuildId.substring(curBuildId.lastIndexOf(".") + 1);
							j2 = Integer.parseInt(s1);
						} catch (Exception e) {
							s1 = null;
							j2 = -1;
						}
						if (j2 > j1) {
							maxNum = s1;
							justFile = buildFile;
						}
					}
				} else {
					if (curBuildId.equals(buildId)) {
						justFile = buildFile;
						break;
					}
				}
			}
		}

		if (justFile == null)
			return null;

		String[] result = new String[3];
		result[0] = justFile.getName();
		result[1] = justFile.getParentFile().getName();
		if (searchIndexForMax != -1) {
			result[2] = maxNum;
		} else {
			result[2] = buildId.substring(buildId.lastIndexOf(".") + 1);
		}

		return result;
	}
}
