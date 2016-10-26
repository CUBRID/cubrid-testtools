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

package com.navercorp.cubridqa.isolation.deploy;

import com.navercorp.cubridqa.common.CommonUtils;
import com.navercorp.cubridqa.common.ConfigParameterConstants;
import com.navercorp.cubridqa.isolation.Constants;
import com.navercorp.cubridqa.isolation.Context;
import com.navercorp.cubridqa.isolation.IsolationHelper;
import com.navercorp.cubridqa.isolation.IsolationScriptInput;
import com.navercorp.cubridqa.shell.common.SSHConnect;

public class TestCaseGithub {

	Context context;
	SSHConnect ssh;
	String currEnvId;
	String envIdentify;

	public TestCaseGithub(Context context, String currEnvId) throws Exception {
		this.context = context;
		this.currEnvId = currEnvId;
		envIdentify = "EnvId=" + currEnvId + "[" + IsolationHelper.getTestNodeTitle(context, currEnvId) + "]";
		this.ssh = IsolationHelper.createTestNodeConnect(context, currEnvId);

	}

	public void update() {
		context.getFeedback().onTestCaseUpdateStart(envIdentify);
		while (true) {
			if (doUpdate()) {
				break;
			}

			System.out.println("==>Retry to do case update after 5 seconds!");
			CommonUtils.sleep(5);
		}
		context.getFeedback().onTestCaseUpdateStop(envIdentify);
	}

	public boolean doUpdate() {
		boolean isSucc = true;
		boolean needUpdateScenario = context.shouldUpdateTestCase();
		String msg = "SKIP TEST CASE UPDATE";
		cleanProcess();

		IsolationScriptInput scripts = new IsolationScriptInput();

		scripts.addCommand("cd ");
		scripts.addCommand("cd ${CTP_HOME}/common/script");

		String ctpBranchName = System.getenv(ConfigParameterConstants.CTP_BRANCH_NAME);
		if (!CommonUtils.isEmpty(ctpBranchName)) {
			scripts.addCommand("export CTP_BRANCH_NAME=" + ctpBranchName);
		}

		if (context.isExecuteAtLocal()) {
			scripts.addCommand("export CTP_SKIP_UPDATE=1");
		} else {
			String skipUpgrade = System.getenv(ConfigParameterConstants.CTP_SKIP_UPDATE);
			if (!CommonUtils.isEmpty(ctpBranchName)) {
				scripts.addCommand("export CTP_SKIP_UPDATE=" + skipUpgrade);
			}
		}

		scripts.addCommand("chmod u+x upgrade.sh");
		scripts.addCommand("./upgrade.sh");

		if (needUpdateScenario) {
			scripts.addCommand("cd ");
			scripts.addCommand("run_git_update -f " + context.getTestCaseRoot() + " -b " + context.getTestCaseBranch());
			msg = "TEST CASE UPDATE";
		}
		scripts.addCommand("cd ${CTP_HOME}/isolation/ctltool");
		scripts.addCommand("chmod u+x *.sh");

		scripts.addCommand("echo Above EnvId is " + this.currEnvId);
		String result;
		try {
			result = ssh.execute(scripts);
			if (!com.navercorp.cubridqa.common.CommonUtils.isEmpty(result) && result.indexOf("ERROR") != -1) {
				isSucc = false;
			}
			System.out.println(result);
		} catch (Exception e) {
			isSucc = false;
			System.out.print("[ERROR] " + e.getMessage());
		}

		if (isSucc) {
				System.out.println(msg + " AND CTLTOOL UPDATE COMPLETE!");
		} else {
				System.out.println(msg + " AND CTLTOOL UPDATE FAIL!");
		}

		return isSucc;
	}

	public void cleanProcess() {
		if (this.context.isWindows()) {
			cleanProcess_windows();
		} else {
			cleanProcess_linux();
		}
	}

	private void cleanProcess_linux() {
		IsolationScriptInput scripts = new IsolationScriptInput();
		scripts.addCommand(Constants.LIN_KILL_PROCESS);
		try {
			ssh.execute(scripts);
		} catch (Exception e) {
		}
	}

	private void cleanProcess_windows() {
		IsolationScriptInput scripts = new IsolationScriptInput();
		scripts.addCommand(Constants.WIN_KILL_PROCESS);

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
