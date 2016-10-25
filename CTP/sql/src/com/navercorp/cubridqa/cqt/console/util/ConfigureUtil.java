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
package com.navercorp.cubridqa.cqt.console.util;

import java.io.File;
import java.util.Map;

import org.apache.avalon.framework.configuration.Configuration;

public class ConfigureUtil {

	private Map<String, String> propMap;

	private Configuration qaToolXml;

	private Configuration systemXml;

	private String repositoryPath;

	public ConfigureUtil() {
		loadConf();
	}

	/**
	 * 
	 * @Title: loadConf
	 * @Description:Load configuration of CUBRID QA tool.
	 * @param
	 * @return void
	 * @throws
	 */
	public void loadConf() {
		propMap = PropertiesUtil.loadProperties();
		repositoryPath = StringUtil.replaceSlashBasedSystem(EnvGetter.getenv("CTP_HOME") + File.separator + "sql");
	}

	public String getRepositoryPath() {
		return repositoryPath;
	}

	public String getProperty(String key) {
		return propMap.get(key);
	}

	public int getIntFromSystemXml(String key) {
		int ret = -1;

		String value = this.getStringFormSystemXml(key);
		try {
			ret = Integer.parseInt(value);
		} catch (Exception e) {
		}
		return ret;
	}

	public String getStringFormSystemXml(String key) {
		if (key == null) {
			return null;
		}

		String ret = null;
		try {
			ret = systemXml.getChild(key).getValue();
		} catch (Exception e) {
		}
		return ret;
	}

	public int getIntFromQaToolXml(String key) {
		int ret = -1;

		String value = this.getStringFormQaToolXml(key);
		try {
			ret = Integer.parseInt(value);
		} catch (Exception e) {
		}
		return ret;
	}

	public String getStringFormQaToolXml(String key) {
		if (key == null) {
			return null;
		}

		String ret = null;
		try {
			ret = qaToolXml.getChild(key).getValue();
		} catch (Exception e) {
		}
		return ret;
	}

	public String getDbBuildServer() {
		return propMap.get("dbbuildserver");
	}

	public String getDbBuildServerPath() {
		String ret = propMap.get("dbbuildserver.path");
		if (ret.endsWith("/")) {
			ret = ret.substring(0, ret.length() - 1);
		}
		return ret;
	}

	public String getDbBuildServerUser() {
		return propMap.get("dbbuildserver.user");
	}

	public String getDbBuildServerPassword() {
		return propMap.get("dbbuildserver.password");
	}
}
