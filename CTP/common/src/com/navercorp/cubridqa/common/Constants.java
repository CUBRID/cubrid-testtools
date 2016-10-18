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
package com.navercorp.cubridqa.common;

import java.io.File;
import java.util.Properties;

import com.navercorp.cubridqa.common.CommonUtils;

public class Constants {

	public static final String ENV_CTP_HOME_KEY = "CTP_HOME";
	public static final String ENV_HOME_KEY = "HOME";
	
	public static final String ENV_CTP_HOME = CommonUtils.getEnvInFile(ENV_CTP_HOME_KEY);
	public static final String ENV_HOME = CommonUtils.getEnvInFile(ENV_HOME_KEY);

	public final static String LINE_SEPARATOR = System.getProperty("line.separator");

	public static Properties COMMON_DAILYQA_CONF;
	static {
		try {
			COMMON_DAILYQA_CONF = CommonUtils.getConfig(CommonUtils
					.getEnvInFile(ENV_CTP_HOME_KEY)
					+ File.separator
					+ "conf"
					+ File.separator + "common.conf");
		} catch (Exception ex) {
			System.out.println("=> Skip common properties initialation");
			COMMON_DAILYQA_CONF = new Properties();
		}
	}

	public final static String MAIL_FROM;
	static {
		String nick = COMMON_DAILYQA_CONF.getProperty("mail_from_nickname", "").trim();
		String mail = COMMON_DAILYQA_CONF.getProperty("mail_from_address", "").trim();
		if (mail.indexOf("<") != -1) {
			MAIL_FROM = mail;
		} else {
			if (CommonUtils.isEmpty(nick)) {
				MAIL_FROM = mail;
			} else {
				MAIL_FROM = nick + "<" + mail + ">";
			}
		}
	}

	public static final String HAVE_CHARSET_10 = "10.0.0.0074";
	public static final String HAVE_CHARSET_9 = "9.2.0.0067";
}
