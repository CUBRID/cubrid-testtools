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

package com.navercorp.cubridqa.shell.service;

import java.io.File;
import java.rmi.server.UnicastRemoteObject;
import java.util.Properties;

import com.navercorp.cubridqa.common.ConfigParameterConstants;
import com.navercorp.cubridqa.shell.common.CommonUtils;
import com.navercorp.cubridqa.shell.common.LocalInvoker;

public class ShellServiceImpl extends UnicastRemoteObject implements ShellService {

	Properties props;
	String requiredUser;
	String requiredPwd;
	String requiredHosts;
	String userHomePureWin;
	String userHome;

	protected ShellServiceImpl(Properties props) throws Exception {
		super();
		this.props = props;

		String value = props.getProperty(ConfigParameterConstants.AGENT_WHITELIST_HOSTS, "").trim();
		value = "," + CommonUtils.replace(value, " ", "") + ",";
		this.requiredHosts = value;

		value = props.getProperty(ConfigParameterConstants.AGENT_LOGIN_USER, "").trim();
		this.requiredUser = value;

		value = props.getProperty(ConfigParameterConstants.AGENT_LOGIN_PASSWORD, "").trim();
		this.requiredPwd = value;

		userHome = props.getProperty(ConfigParameterConstants.AGENT_LOGIN_HOME_DIR);
//		if (userHome == null || userHome.trim().equals("")) {
//			userHome = System.getenv("HOME");
//		}		
		if (userHome == null || userHome.trim().equals("") || userHome.toUpperCase().equals("NULL")) {
			userHome = new File(System.getenv("init_path")).getParentFile().getParentFile().getParentFile().getAbsolutePath();
		}
		
		File userHomeFile = new File(userHome);
		if (userHomeFile.exists() == false) {
			throw new Exception("Not found " + userHomeFile.getAbsolutePath() + ". Please check 'main.service.userhome' property");
		}

		userHomePureWin = userHome;

		if (com.navercorp.cubridqa.common.CommonUtils.isWindowsPlatform()) {
			userHome = com.navercorp.cubridqa.common.CommonUtils.getLinuxStylePath(userHome, true);
		}
	}

	public String exec(String user, String pwd, String scripts) throws Exception {
		return exec(user, pwd, scripts, false);
	}

	public String exec(String user, String pwd, String scripts, boolean pureWindows) throws Exception {
		String clientHost = super.getClientHost();
		System.out.println();
		System.out.println("===========================================================");
		System.out.println("host: " + clientHost + ", user:" + user + "(" + new java.util.Date() + ") (v20160804)");
		String preScript;

		if (requiredUser.equals(user) && requiredPwd.equals(pwd) && requiredHosts.indexOf("," + clientHost + ",") != -1) {
			if(scripts.equals("PLEASE_RESTART_AGENT")) {
				System.out.println("Service will restart. Quit.");
				System.exit(0);
			}
			if (pureWindows) {
				preScript = "set HOME=" + userHomePureWin + "\n\r";
				preScript = preScript + "set USER=" + user + "\n\r";
				preScript = preScript + "cd %HOME%" + "\n\r";

			} else {
				preScript = "export HOME=" + userHome + ";";
				preScript = preScript + "export USER=" + user + ";";
				preScript = preScript + "cd $HOME;";
				
				String initPathforShell = System.getenv("init_path");
				if (initPathforShell != null && initPathforShell.trim().equals("") == false) {
					if (com.navercorp.cubridqa.common.CommonUtils.isWindowsPlatform()) {
						initPathforShell = com.navercorp.cubridqa.common.CommonUtils.getLinuxStylePath(initPathforShell, true);
						preScript = preScript + "export init_path=" + initPathforShell + "; ";
					}
				}
				//preScript = preScript + "if  [ -f ~/.bash_profile ]; then . ~/.bash_profile; fi; ";
			}

			scripts = preScript + scripts;
			System.out.println(scripts);
			String result = LocalInvoker.exec(scripts, pureWindows, false);
			int pos;
			pos = result.lastIndexOf(com.navercorp.cubridqa.shell.common.ScriptInput.START_FLAG);
			if (pos != -1) {
				result = result.substring(pos + com.navercorp.cubridqa.shell.common.ScriptInput.START_FLAG.length());
			}
			pos = result.indexOf(com.navercorp.cubridqa.shell.common.ScriptInput.COMP_FLAG);
			if (pos != -1) {
				result = result.substring(0, pos);
			}
			System.out.println("WELCOME");
			return result.trim();
		} else {
			System.out.println("DENY");
			return null;
		}
	}
}
