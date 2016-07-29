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

import com.navercorp.cubridqa.isolation.Constants;
import com.navercorp.cubridqa.isolation.Context;
import com.navercorp.cubridqa.shell.common.SSHConnect;
import com.navercorp.cubridqa.shell.common.ShellInput;

public class TestCaseGithub {

	Context context;
	SSHConnect ssh;
	String currEnvId;
	String envIdentify;

	public TestCaseGithub(Context context, String currEnvId) throws Exception {
		this.context = context;
		this.currEnvId = currEnvId;
		String host = context.getProperty("env." + currEnvId + ".ssh.host");
		String port = context.getProperty("env." + currEnvId + ".ssh.port");
		String user = context.getProperty("env." + currEnvId + ".ssh.user");
		String pwd = context.getProperty("env." + currEnvId + ".ssh.pwd");

		envIdentify = "EnvId=" + currEnvId + "[" + user + "@" + host + ":" + port + "]";

		this.ssh = new SSHConnect(host, port, user, pwd);

	}

	public void update() throws Exception {
		context.getFeedback().onSvnUpdateStart(envIdentify);

		cleanProcess();

		ShellInput scripts;
		if (context.isWindows()) {
			scripts = new ShellInput();
		} else {
			scripts = new ShellInput();
		}

		if (context.getCleanTestCase()) {
			scripts.addCommand("run_git_update -f " + context.getTestCaseRoot() + " -b " + context.getTestCaseBranch());
		}

		if (context.isUpdateCTL()) {
			scripts.addCommand("cd ");
			scripts.addCommand("cd " + context.getCtlHome());
			scripts.addCommand("sh upgrade.sh");
		}
		scripts.addCommand("echo Above EnvId is " + this.currEnvId);
		String result;
		try {
			result = ssh.execute(scripts);
			System.out.println(result);
		} catch (Exception e) {
			System.out.print("[ERROR] " + e.getMessage());
			throw e;
		}
		System.out.println("TEST CASES AND CTLTOOL UPDATE COMPLETE");

		context.getFeedback().onSvnUpdateStop(envIdentify);
	}

	public void cleanProcess() {
		if (this.context.isWindows()) {
			cleanProcess_windows();
		} else {
			cleanProcess_linux();
		}
	}

	private void cleanProcess_linux() {
		ShellInput scripts = new ShellInput();
		scripts.addCommand(Constants.LIN_KILL_PROCESS);
		try {
			ssh.execute(scripts);
		} catch (Exception e) {
		}
	}

	private void cleanProcess_windows() {
		ShellInput scripts = new ShellInput();
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
