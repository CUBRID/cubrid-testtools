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

package com.navercorp.cubridqa.shell.deploy;

import com.navercorp.cubridqa.shell.common.CommonUtils;
import com.navercorp.cubridqa.shell.common.Constants;
import com.navercorp.cubridqa.shell.common.SSHConnect;
import com.navercorp.cubridqa.shell.common.ShellInput;
import com.navercorp.cubridqa.shell.common.WinShellInput;
import com.navercorp.cubridqa.shell.main.Context;

public class TestCaseGithub {

	Context context;
	SSHConnect ssh;
	String currEnvId;
	String envIdentify;

	public TestCaseGithub(Context context, String currEnvId) throws Exception {
		this.context = context;
		this.currEnvId =currEnvId;
		String host = context.getProperty("env." + currEnvId + ".ssh.host");
		String port = context.getProperty("env." + currEnvId + ".ssh.port");
		String user = context.getProperty("env." + currEnvId + ".ssh.user");
		String pwd = context.getProperty("env." + currEnvId + ".ssh.pwd");
		
		envIdentify = "EnvId=" + currEnvId + "[" + user+"@"+host+":" + port + "]";

		this.ssh = new SSHConnect(host, port, user, pwd);
		
		
		if(context.isWindows()) {
			initWindows();
		}
	}
	
	public void update() throws Exception {
		context.getFeedback().onSvnUpdateStart(envIdentify);
		
		cleanProcess();
		
		//TODO if test case doesn't exist
		
		ShellInput scripts;
		if(context.isWindows()) {
			scripts = new WinShellInput();
		} else {
			scripts = new ShellInput();
		}
		
		String sedCmds;
		if(context.isWindows()) {
			sedCmds = "sed 's;\\\\;/;g'|";
		} else {
			sedCmds = "";
		}
		
		if(context.getCleanTestCase()) {
			scripts.addCommand("run_git_update -f " + context.getTestCaseRoot() + " -b " + context.getTestCaseBranch());
		}
		
		//scripts.addCommand("cd ");
		//scripts.addCommand("cd " + context.getTestCaseRoot());
		//scripts.addCommand("echo 'EXPECT NOTHING FOR BELOW (" + this.currEnvId + ")'");
		//scripts.addCommand("svn st " + context.getSVNUserInfo());
		//scripts.addCommand("echo Above EnvId is " + this.currEnvId);
		if (!context.getTestCaseWorkspace().equals(context.getTestCaseRoot())) {
			String wsRoot = context.getTestCaseWorkspace().replace('\\', '/');
			String tcRoot = context.getTestCaseRoot().replace('\\', '/');
			String fromStar = CommonUtils.concatFile(tcRoot, "*").replace('\\', '/');
			String toStar = CommonUtils.concatFile(wsRoot, "*").replace('\\', '/');

			scripts.addCommand("mkdir -p " + wsRoot);
			scripts.addCommand("rm -rf " + toStar);
			scripts.addCommand("cp -r " + fromStar + " " + wsRoot);
		}
		
		String result;
		try {
			result = ssh.execute(scripts);
			System.out.println(result);
		} catch (Exception e) {
			System.out.print("[ERROR] " + e.getMessage());
			throw e;
		}
		System.out.println("SVN UPDATE COMPLETE");
		
		context.getFeedback().onSvnUpdateStop(envIdentify);
 	}
	
	public void cleanProcess() {
		String result = CommonUtils.resetProcess(ssh, context.isWindows());
		System.out.println("CLEAN PROCESSES:");
		System.out.println(result);
 	}

	
	
	private void initWindows() {
		WinShellInput scripts = new WinShellInput();

		scripts.addCommand("REG DELETE \"HKEY_LOCAL_MACHINE\\SYSTEM\\ControlSet001\\Control\\Session Manager\\Environment\" /v CUBRID_LANG /f");
		scripts.addCommand("REG DELETE \"HKEY_LOCAL_MACHINE\\SYSTEM\\ControlSet001\\Control\\Session Manager\\Environment\" /v CUBRID_CHARSET /f");
		scripts.addCommand("REG DELETE \"HKEY_LOCAL_MACHINE\\SYSTEM\\ControlSet001\\Control\\Session Manager\\Environment\" /v CUBRID_MSG_LANG /f");
		scripts.addCommand("REG ADD \"HKEY_LOCAL_MACHINE\\SYSTEM\\ControlSet001\\Control\\Session Manager\\Environment\" /v CUBRID_CHARSET /d en_US /f");
		scripts.addCommand("REG ADD \"HKEY_LOCAL_MACHINE\\SYSTEM\\ControlSet001\\Control\\Session Manager\\Environment\" /v CUBRID_LANG /d en_US /f");
		scripts.addCommand("REG ADD \"HKEY_LOCAL_MACHINE\\SYSTEM\\ControlSet001\\Control\\Session Manager\\Environment\" /v CUBRID_MSG_LANG /d en_US /f");

		try {
			ssh.execute(scripts);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void close() {
		ssh.close();
	}
}
