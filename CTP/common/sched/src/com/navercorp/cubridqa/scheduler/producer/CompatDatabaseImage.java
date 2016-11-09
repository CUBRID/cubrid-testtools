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
import java.util.Properties;

import com.navercorp.cubridqa.scheduler.common.CommonUtils;
import com.navercorp.cubridqa.scheduler.common.Constants;

public class CompatDatabaseImage extends AbstractExtendedSuite {

	private static CompatDatabaseImage instance;

	private CompatDatabaseImage(Configure conf, String extendedConfig) {
		super(conf, extendedConfig);
	}

	public static void init(Configure conf, String extendedConfig) {
		instance = new CompatDatabaseImage(conf, extendedConfig);
	}

	public static CompatDatabaseImage getInstance() {
		return instance;
	}

	@Override
	public boolean loadMsgProperties(File file, Properties props) {

		String compatBuildId = props.getProperty(Constants.MSG_DBIMG_BUILD_ID);
		if (compatBuildId == null)
			return false;

		String compatBuildUrls = props.getProperty(Constants.MSG_DBIMG_BUILD_URLS);

		String urls = null;
		String urls_kr = null;

		String[] exactInfo = getExactBuildInfo(compatBuildId);
		if (exactInfo == null)
			return false;

		compatBuildId = exactInfo[0];
		props.put(Constants.MSG_DBIMG_BUILD_ID, compatBuildId);
		String storeId = exactInfo[1];
		compatBuildUrls = CommonUtils.replace(compatBuildUrls, "{max}", exactInfo[2]);
		urls = conf.getWebBaseUrl() + "/" + storeId + "/" + compatBuildId + "/drop/" + compatBuildUrls;
		urls_kr = conf.getWebBaseUrl_Kr() + "/" + compatBuildId + "/drop/" + compatBuildUrls;
		props.put(Constants.MSG_DBIMG_BUILD_URLS, urls);
		props.put(Constants.MSG_DBIMG_BUILD_URLS_KR, urls_kr);

		props.put(Constants.MSG_DBIMG_TEST_CATAGORY, CommonUtils.replace(file.getName(), ".msg", ""));
		return true;
	}
}
