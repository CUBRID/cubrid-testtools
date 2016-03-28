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
package com.navercorp.cubridqa.cqt.console;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

import com.navercorp.cubridqa.cqt.console.util.CommonFileUtile;
import com.navercorp.cubridqa.cqt.console.util.CubridUtil;
import com.navercorp.cubridqa.cqt.console.util.EnvGetter;
import com.navercorp.cubridqa.cqt.console.util.LogUtil;
import com.navercorp.cubridqa.cqt.console.util.StringUtil;
import com.navercorp.cubridqa.cqt.console.util.SystemUtil;


public abstract class Executor {

	public static final int PRINT_STDOUT = 0;

	public static final int PRINT_UI = 1;

	public static final int PRINT_LOG = 2;

	protected long startTime;

	protected long endTime;

	protected String logId = "Executor";

	private int printType = 1;

	protected List<String> envList = new ArrayList<String>();

	protected abstract void init();

	/**
	 * initialize the qa tools variable.
	 */
	public void initEnvs() {
		String pathSeparator = System.getProperty("path.separator");
		if (pathSeparator == null) {
			pathSeparator = ";";
		}

		String cmd = SystemUtil.getEnvSetCmd();
		String cubridHome = CubridUtil.getCubridPath();

		envList.add(cmd + " CUBRID=" + cubridHome);
		envList.add(cmd + " CUBRID_BROKER=" + cubridHome);
		envList.add(cmd + " CUBRID_DATABASES=" + cubridHome + "/databases");
		envList.add(cmd + " CUBRID_MANAGER=" + cubridHome + "/cubridmanager");
		envList.add(cmd + " CUBRID_MODE=client");
		envList.add(cmd + " CUBRID_LANG=En_US");
		String shLibPath = StringUtil.nullToEmpty(EnvGetter
				.getenv("SHLIB_PATH"));
		envList.add(cmd + " SHLIB_PATH=" + shLibPath
				+ (("".equals(shLibPath)) ? "" : pathSeparator) + "."
				+ pathSeparator + cubridHome + "/lib" + pathSeparator
				+ cubridHome + "/lib" + pathSeparator);
		String path = StringUtil.nullToEmpty(EnvGetter.getenv("PATH"));
		envList.add(cmd + " PATH=" + path
				+ (("".equals(path)) ? "" : pathSeparator) + cubridHome
				+ "/bin" + pathSeparator + cubridHome + "/cubridmanager"
				+ pathSeparator + System.getenv("PATH"));
		String libPath = StringUtil.nullToEmpty(EnvGetter.getenv("LIBPATH"));
		envList.add(cmd + " LIBPATH=" + libPath
				+ (("".equals(libPath)) ? "" : pathSeparator) + cubridHome
				+ "/lib" + pathSeparator);
		String ldLibPath = StringUtil.nullToEmpty(System
				.getenv("LD_LIBRARY_PATH"));
		envList.add(cmd + " LD_LIBRARY_PATH=" + ldLibPath
				+ (("".equals(ldLibPath)) ? "" : pathSeparator) + cubridHome
				+ "/lib" + pathSeparator);
		if (!CommonFileUtile.isLinux()) {
			for (int i = 0; i < envList.size(); i++) {
				String env = envList.get(i);
				StringTokenizer stringTokenizer = new StringTokenizer(env, "/");
				env = "";
				while (stringTokenizer.hasMoreTokens()) {
					env = env + stringTokenizer.nextToken() + File.separator;
				}
				env = env.substring(0, env.length() - 1);
				envList.remove(i);
				envList.add(i, env);
			}
		}
	}

	public void setStartTime(long startTime) {
		this.startTime = startTime;
	}

	public void setEndTime(long endTime) {
		this.endTime = endTime;
	}

	public void onMessage(String message) {
		switch (printType) {
		case PRINT_STDOUT:
			System.out.println(message);
			break;
		case PRINT_UI:
			ConsoleAgent.addMessage(message
					+ System.getProperty("line.separator"));
			break;
		case PRINT_LOG:
			LogUtil.log(logId, message);
		}
	}

	public String[] getEnvs() {
		Object[] envs = envList.toArray();
		String[] ret = new String[envs.length];
		for (int i = 0; i < envs.length; i++) {
			ret[i] = (String) envs[i];
		}
		return ret;
	}

	public int getPrintType() {
		return printType;
	}

	public void setPrintType(int printType) {
		this.printType = printType;
	}
}
