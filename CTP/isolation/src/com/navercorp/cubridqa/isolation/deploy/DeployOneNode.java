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
import com.navercorp.cubridqa.shell.common.Log;
import com.navercorp.cubridqa.shell.common.SSHConnect;
import com.navercorp.cubridqa.shell.common.ShellInput;

public class DeployOneNode {

	Context context;
	SSHConnect ssh;
	String cubridPackageUrl;

	String envIdentify;
	Log log;

	public DeployOneNode(Context context, String currEnvId, String host, Log log) throws Exception {
		this.context = context;

		String port = context.getProperty("env." + currEnvId + ".ssh.port");
		String user = context.getProperty("env." + currEnvId + ".ssh.user");
		String pwd = context.getProperty("env." + currEnvId + ".ssh.pwd");
		envIdentify = "EnvId=" + currEnvId + "[" + user + "@" + host + ":" + port + "]";

		this.ssh = new SSHConnect(host, port, user, pwd);

		this.cubridPackageUrl = context.getCubridPackageUrl();

		this.log = log;
	}

	public void deploy() {
		if (context.isWindows()) {
			deploy_windows();
		} else {
			deploy_cubrid_common();
			deploy_linux_by_cubrid_common();
			prepareCtl();
		}
	}

	private void deploy_windows() {
		cleanProcess();

		String cubridInstallName = cubridPackageUrl.substring(cubridPackageUrl.indexOf("CUBRID-"));

		// TODO: check cubridInstallName

		ShellInput scripts = new ShellInput();
		scripts.addCommand("cd $CUBRID/..");
		scripts.addCommand("rm -rf CUBRID");
		scripts.addCommand("rm -rf " + cubridInstallName + "* ");
		scripts.addCommand("if [ ! -f " + cubridInstallName + " ] ");
		scripts.addCommand("then");
		scripts.addCommand("    wget " + cubridPackageUrl);
		scripts.addCommand("fi");
		scripts.addCommand("mkdir CUBRID");
		scripts.addCommand("cd CUBRID");
		scripts.addCommand("unzip -o ../" + cubridInstallName);
		scripts.addCommand("chmod -R u+x ../CUBRID");
		String buildId = context.getTestBuild();
		String[] arr = buildId.split("\\.");
		if (Integer.parseInt(arr[0]) >= 10) {
			scripts.addCommand("echo inquire_on_exit=3 >> $CUBRID/conf/cubrid.conf");
		}
		scripts.addCommand("rm -rf ../" + cubridInstallName + "* ");

		String result;
		try {
			result = ssh.execute(scripts);
			log.println(result);
		} catch (Exception e) {
			log.print("[ERROR] " + e.getMessage());
		}
	}

	private void deploy_linux() {
		cleanProcess();

		String cubridInstallName = cubridPackageUrl.substring(cubridPackageUrl.indexOf("CUBRID-"));

		// TODO: check cubridInstallName

		ShellInput scripts = new ShellInput();
		scripts.addCommand("echo 'ulimit -c unlimited' >> ~/.bash_profile");
		scripts.addCommand("cat ~/.bash_profile | uniq >  ~/.bash_profile_tmp; cp ~/.bash_profile_tmp ~/.bash_profile");
		scripts.addCommand("rm -rf ~/CUBRID");
		scripts.addCommand("rm -rf " + cubridInstallName + "*");
		scripts.addCommand("if [ ! -f " + cubridInstallName + " ] ");
		scripts.addCommand("then");
		scripts.addCommand("    wget " + cubridPackageUrl);
		scripts.addCommand("fi");

		scripts.addCommand("sh " + cubridInstallName + " > /dev/null <<EOF");
		scripts.addCommand("yes");
		scripts.addCommand("");
		scripts.addCommand("");
		scripts.addCommand("");
		scripts.addCommand("EOF");
		scripts.addCommand("sh ~/.cubrid_cc_fm.sh > /dev/null 2>&1");
		String buildId = context.getTestBuild();
		String[] arr = buildId.split("\\.");
		if (Integer.parseInt(arr[0]) >= 10) {
			scripts.addCommand("echo inquire_on_exit=3 >> $CUBRID/conf/cubrid.conf");
		}
		scripts.addCommand("rm -rf " + cubridInstallName + "*");

		String result;
		try {
			result = ssh.execute(scripts);
			log.println(result);
		} catch (Exception e) {
			log.print("[ERROR] " + e.getMessage());
		}
	}

	private void deploy_linux_by_cubrid_common() {
		cleanProcess();

		String role = context.getProperty("main.testing.role", "").trim();
		log.print("Start Install Build");
		ShellInput scripts = new ShellInput();
		scripts.addCommand("echo 'ulimit -c unlimited' >> ~/.bash_profile");
		scripts.addCommand("cat ~/.bash_profile | uniq >  ~/.bash_profile_tmp; cp ~/.bash_profile_tmp ~/.bash_profile");
		scripts.addCommand("run_cubrid_install " + role + " " + context.getCubridPackageUrl() + " " + context.getProperty("main.collaborate.url", "").trim());
		String buildId = context.getTestBuild();
		String[] arr = buildId.split("\\.");
		if (Integer.parseInt(arr[0]) >= 10) {
			scripts.addCommand("echo inquire_on_exit=3 >> $CUBRID/conf/cubrid.conf");
		}

		String result;
		try {
			result = ssh.execute(scripts);
			log.println(result);
		} catch (Exception e) {
			log.print("[ERROR] " + e.getMessage());
		}
	}

	private void deploy_cubrid_common() {

		ShellInput scripts = new ShellInput();
		scripts.addCommand("cd $HOME/cubrid_common");
		scripts.addCommand("sh upgrade.sh");

		String result;
		try {
			result = ssh.execute(scripts);
			log.println(result);
		} catch (Exception e) {
			log.print("[ERROR] " + e.getMessage());
		}

	}

	private void prepareCtl() {
		ShellInput scripts = new ShellInput();
		scripts.addCommand("sh $HOME/" + context.getCtlHome() + "/prepare.sh");

		String result;
		try {
			result = ssh.execute(scripts);
			log.println(result);
		} catch (Exception e) {
			log.print("[ERROR] " + e.getMessage());
		}
	}

	public void cleanProcess() {
		if (context.isWindows()) {
			cleanProcess_windows();
		} else {
			cleanProcess_linux();
		}
	}

	private void cleanProcess_windows() {
		ShellInput scripts = new ShellInput();
		scripts.addCommand(Constants.WIN_KILL_PROCESS);
		String result;
		try {
			result = ssh.execute(scripts);
			log.println(result);
		} catch (Exception e) {
			log.print("[ERROR] " + e.getMessage());
		}
	}

	private void cleanProcess_linux() {
		ShellInput scripts = new ShellInput();
		scripts.addCommand(Constants.LIN_KILL_PROCESS);
		String result;
		try {
			result = ssh.execute(scripts);
			log.println(result);
		} catch (Exception e) {
			log.print("[ERROR] " + e.getMessage());
		}
	}

	public void close() {
		ssh.close();
	}
}
