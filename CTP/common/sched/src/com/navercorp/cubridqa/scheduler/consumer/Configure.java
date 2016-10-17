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

package com.navercorp.cubridqa.scheduler.consumer;

import java.io.File;

import java.io.IOException;

import java.util.Properties;

import com.navercorp.cubridqa.scheduler.common.CommonUtils;
import com.navercorp.cubridqa.scheduler.common.Constants;

public class Configure {

	Properties props;

	public Configure() throws IOException {
		props = new Properties();

		File file = new File(Constants.CTP_HOME + File.separator + "conf" + File.separator + "common.conf");
		if (file.exists()) {
			props.putAll(CommonUtils.getProperties(file));
		}
		file = new File(Constants.CTP_HOME + File.separator + "conf" + File.separator + "consumer.conf");
		if (file.exists()) {
			props.putAll(CommonUtils.getProperties(file));
		}

		String driver = props.getProperty("qahome_db_driver");
		try {
			Class.forName(driver);
		} catch (Exception e) {
			System.out.println("[ERROR] fail to load JDBC Driver, please refer to qahome_db_driver parameter in common.conf file.");
		}
	}

	public String getProperty(String key) {
		return getProperty(key, null);
	}

	public String getProperty(String key, String defaultValue) {
		return this.props.getProperty(key, defaultValue);
	}

}
