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
import java.util.HashMap;
import java.util.Properties;

import com.navercorp.cubridqa.scheduler.common.CommonUtils;

public class GeneralExtendedSuite extends AbstractExtendedSuite {

	private static HashMap<String, GeneralExtendedSuite> instance = new HashMap<String, GeneralExtendedSuite>();

	private GeneralExtendedSuite(Configure conf, String extendedConfig) {
		super(conf, extendedConfig);
	}

	public static void init(Configure conf, String extendedConfig) {
		instance.put(extendedConfig, new GeneralExtendedSuite(conf, extendedConfig));
	}

	public static synchronized GeneralExtendedSuite getInstance(Configure conf, String extendedConfig, boolean clearFirst) {
		if (clearFirst) {
			if (extendedConfig == null) {
				instance.clear();
				return null;
			} else {
				instance.put(extendedConfig, null);
			}
		}
		GeneralExtendedSuite obj = instance.get(extendedConfig);
		if (obj == null) {
			init(conf, extendedConfig);
		}

		return instance.get(extendedConfig);
	}

	/**
	 * for example: EXT_KEY_FOR_FIX_MAX=COMPAT_BUILD_ID
	 * EXT_KEY_FOR_FIX_MAX_FOLLOW=COMPAT_BUILD_URLS
	 * EXT_KEY_FOR_FIX_MAX_FOLLOW_KR=COMPAT_BUILD_URLS_KR
	 * EXT_KEY_FOR_TEST_CATAGORY=COMPAT_TEST_CATAGORY
	 * 
	 * EXT_KEY_FOR_FIX_MAX=DBIMG_BUILD_ID
	 * EXT_KEY_FOR_FIX_MAX_FOLLOW=DBIMG_BUILD_URLS
	 * EXT_KEY_FOR_FIX_MAX_FOLLOW_KR=DBIMG_BUILD_URLS_KR
	 * EXT_KEY_FOR_TEST_CATAGORY=DBIMG_TEST_CATAGORY
	 * 
	 * EXT_KEY_FOR_TEST_CATAGORY=I18N_TEST_CATAGORY
	 */
	@Override
	public boolean loadMsgProperties(File file, Properties props) {

		String ext_KEY_FOR_FIX_MAX = getProperty("EXT_KEY_FOR_FIX_MAX");
		String ext_KEY_FOR_FIX_MAX_FOLLOW = getProperty("EXT_KEY_FOR_FIX_MAX_FOLLOW");
		String ext_KEY_FOR_FIX_MAX_FOLLOW_KR = getProperty("EXT_KEY_FOR_FIX_MAX_FOLLOW_KR");
		String ext_KEY_FOR_TEST_CATAGORY = getProperty("EXT_KEY_FOR_TEST_CATAGORY");

		String compatBuildId = ext_KEY_FOR_FIX_MAX == null ? null : props.getProperty(ext_KEY_FOR_FIX_MAX);

		if (compatBuildId != null) {
			String[] exactInfo = getExactBuildInfo(compatBuildId);
			if (exactInfo != null) {
				compatBuildId = exactInfo[0];
				String storeId = exactInfo[1];
				props.put(ext_KEY_FOR_FIX_MAX, compatBuildId);

				String compatBuildUrls = props.getProperty(ext_KEY_FOR_FIX_MAX_FOLLOW);
				if (compatBuildUrls != null) {
					String urls = null;
					String urls_kr = null;
					compatBuildUrls = CommonUtils.replace(compatBuildUrls, "{BUILD_ID}", compatBuildId);

					urls = conf.getWebBaseUrl() + "/" + storeId + "/" + compatBuildId + "/drop/" + compatBuildUrls;
					urls_kr = conf.getWebBaseUrl_Kr() + "/" + compatBuildId + "/drop/" + compatBuildUrls;
					props.put(ext_KEY_FOR_FIX_MAX_FOLLOW, urls);
					if (ext_KEY_FOR_FIX_MAX_FOLLOW_KR != null) {
						props.put(ext_KEY_FOR_FIX_MAX_FOLLOW_KR, urls_kr);
					}
				}
			}
		}

		if (ext_KEY_FOR_TEST_CATAGORY != null) {
			props.put(ext_KEY_FOR_TEST_CATAGORY, CommonUtils.replace(file.getName(), ".msg", ""));
		}
		return true;
	}
}
