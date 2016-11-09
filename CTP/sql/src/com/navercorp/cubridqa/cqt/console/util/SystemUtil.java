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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.lang.SystemUtils;

import com.navercorp.cubridqa.cqt.console.util.EnvGetter;

public class SystemUtil {

	private static final String[] crontabEnv = { "PROPERTIES_PATH", "init_path", "LD_LIBRARY_PATH", "LIBPATH", "PATH", "SHLIB_PATH", "CUBRID", "CUBRID_MANAGER", "CUBRID_BROKER", "CUBRID_DATABASES",
			"CUBRID_LANG", "CUBRID_MODE", "JAVA_HOME", "HOSTNAME", "SHELL", "TMOUT", "CLASSPATH", "QA_REPOSITORY", "SHELL_CONFIG_PATH" };

	private static String os = null;

	static {
		os = System.getProperty("os.name");
		int position = os.indexOf(" ");
		if (position != -1) {
			os = os.substring(0, position);
		}
		os = os.replaceAll("-", "").toLowerCase().trim();
	}

	public static String getOS() {
		return os;
	}

	public static String getUserHomePath() {
		String ret = StringUtil.replaceSlash(EnvGetter.getenv("HOME"));
		if (null == ret || ret.trim().length() == 0) {
			ret = SystemUtils.USER_HOME.trim();
		}
		return ret;
	}

	public static String getEnvSetCmd() {
		String ret = "export";
		String os = SystemUtil.getOS();
		if (os.startsWith("window")) {
			ret = "set";
		}
		return ret;
	}

	/**
	 * print the system property to file .
	 */
	public static void printSysEnvAndProperties() {
		StringBuilder sb = new StringBuilder();
		Map<String, String> env = EnvGetter.getenv();
		Iterator<String> iter = env.keySet().iterator();
		while (iter.hasNext()) {
			String key = iter.next().toString();
			String value = env.get(key).toString();
			sb.append("" + key + "=" + value + System.getProperty("line.separator"));
		}

		Properties props = System.getProperties();
		Iterator propIter = props.keySet().iterator();
		while (propIter.hasNext()) {
			String key = propIter.next().toString();
			String value = props.get(key).toString();
			sb.append("" + key + "=" + value + System.getProperty("line.separator"));
		}
		FileUtil.writeToFile("env_props.txt", sb.toString());
	}

	/**
	 * get the cron tab variable .
	 * 
	 * @return
	 */
	public static List<String> getSysEnvsForCrontab() {
		List<String> list = new ArrayList<String>();

		for (int i = 0; i < crontabEnv.length; i++) {
			String env = crontabEnv[i];
			String value = EnvGetter.getenv(env);
			if (value != null) {
				list.add(env + "=" + value);
			}
		}
		return list;
	}
}
