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
import com.navercorp.cubridqa.shell.common.SSHConnect;
import com.navercorp.cubridqa.shell.common.ShellScriptInput;
import com.navercorp.cubridqa.shell.main.Context;
import com.navercorp.cubridqa.shell.main.ShellHelper;

public class TestCaseGithub {

	Context context;
	SSHConnect ssh;
	String currEnvId;
	String envIdentify;

	public TestCaseGithub(Context context, String currEnvId) throws Exception {
		this.context = context;
		this.currEnvId = currEnvId;

		envIdentify = "EnvId=" + currEnvId + "[" + (ShellHelper.getTestNodeTitle(context, currEnvId)) + "] with " + context.getServiceProtocolType() + " protocol!";
		this.ssh = ShellHelper.createTestNodeConnect(context, currEnvId);

		if (context.isWindows()) {
			initWindows();
		}
	}

	public void update() throws Exception {
		context.getFeedback().onSvnUpdateStart(envIdentify);

		while (true) {
			if (doUpdate()) {
				break;
			}

			System.out.println("==>Retry to do case update after 5 seconds!");
			CommonUtils.sleep(5);
		}

		if (!context.getTestCaseWorkspace().equals(context.getTestCaseRoot())) {
			ShellScriptInput scripts = new ShellScriptInput();
			String wsRoot = context.getTestCaseWorkspace().replace('\\', '/');
			String tcRoot = context.getTestCaseRoot().replace('\\', '/');
			String fromStar = CommonUtils.concatFile(tcRoot, "*").replace('\\', '/');
			String toStar = CommonUtils.concatFile(wsRoot, "*").replace('\\', '/');

			scripts.addCommand("mkdir -p " + wsRoot);
			if (!CommonUtils.isEmpty(toStar)) {
				scripts.addCommand("rm -rf " + toStar);
			}
			scripts.addCommand("cp -r " + fromStar + " " + wsRoot);

			String result;
			try {
				result = ssh.execute(scripts);
				System.out.println(result);
			} catch (Exception e) {
				System.out.print("[ERROR] " + e.getMessage());
			}
		}

		context.getFeedback().onSvnUpdateStop(envIdentify);
	}

	private boolean doUpdate() {
		boolean isSucc = true;
		cleanProcess();
		if (context.needCleanTestCase()) {
			ShellScriptInput scripts = new ShellScriptInput();
			scripts.addCommand("run_git_update -f " + context.getTestCaseRoot() + " -b " + context.getTestCaseBranch() + "  2>&1");
			String result;
			try {
				result = ssh.execute(scripts);
				if (result != null && result.indexOf("ERROR") != -1) {
					isSucc = false;
				}
				System.out.println("Test cases updated results on " + ssh.toString() + ":\n" + result);
			} catch (Exception e) {
				isSucc = false;
				System.out.print("[ERROR] " + e.getMessage());
			}

			System.out.println("UPDATE TEST CASES " + (isSucc ? "COMPLETE !" : "FAIL !"));
		} else {
			System.out.println("SKIP TEST CASES UPDATE");
		}

		return isSucc;
	}

	public void cleanProcess() {
		String result = CommonUtils.resetProcess(ssh, context.isWindows(), context.isExecuteAtLocal());
		System.out.println("CLEAN PROCESSES:");
		System.out.println(result);
	}

	private void initWindows() {
		ShellScriptInput scripts = new ShellScriptInput();

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
