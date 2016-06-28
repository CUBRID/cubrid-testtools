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
import com.navercorp.cubridqa.shell.common.Log;
import com.navercorp.cubridqa.shell.common.SSHConnect;
import com.navercorp.cubridqa.shell.common.ShellInput;
import com.navercorp.cubridqa.shell.common.WinShellInput;
import com.navercorp.cubridqa.shell.main.Context;

public class DeployOneNode {

	Context context;
	SSHConnect ssh;
	String cubridPackageUrl;
	boolean isNewBuildNumberSystem;

	String envIdentify;
	Log log;

	public DeployOneNode(Context context, String currEnvId, String host, Log log) throws Exception {
		this.context = context;

		String port = context.getProperty("env." + currEnvId + ".ssh.port");
		String user = context.getProperty("env." + currEnvId + ".ssh.user");
		String pwd = context.getProperty("env." + currEnvId + ".ssh.pwd");
		envIdentify = "EnvId=" + currEnvId + "[" + user+"@"+host+":" + port + "]";

		this.ssh = new SSHConnect(host, port, user, pwd);

		this.cubridPackageUrl = context.getCubridPackageUrl();
		this.isNewBuildNumberSystem= context.getIsNewBuildNumberSystem();
		
		this.log = log;
	}
	
	public void deploy() {
		deploy_cubrid_common();
		deploy_cqt();

		if(context.isWindows()) {
			String ret="";
			deploy_windows();
			while(true)
			{
				ret = backup_windows();
				int idx = ret.indexOf("conf");
				if(idx!=-1)
				{
					break;
				}
			}
		} else {
			deploy_core_analyzer();
			deploy_linux_by_cubrid_common();
			backup_linux();
		}
	}

	private void deploy_windows() {
		cleanProcess();

		String cubridInstallName = cubridPackageUrl.substring(cubridPackageUrl.indexOf("CUBRID-"));

		// TODO: check cubridInstallName

		WinShellInput scripts = new WinShellInput();
		scripts.addCommand("cd $CUBRID/..");
		scripts.addCommand("rm -rf CUBRID 2>&1");
		scripts.addCommand("ls -l CUBRID");
		scripts.addCommand("ps -W");
		scripts.addCommand("rm -rf " + cubridInstallName + "* ");
		scripts.addCommand("if [ ! -f " + cubridInstallName + " ] "); // TODO:
																		// no
																		// need
		scripts.addCommand("then");
		scripts.addCommand("    wget " + cubridPackageUrl);
		scripts.addCommand("fi");

		if (isNewBuildNumberSystem) {
			scripts.addCommand("unzip " + cubridInstallName);
			scripts.addCommand("cd CUBRID");
			scripts.addCommand("mkdir databases");
		} else {
			scripts.addCommand("mkdir CUBRID");
			scripts.addCommand("cd CUBRID");
			scripts.addCommand("unzip -o ../" + cubridInstallName);
		}

		scripts.addCommand("chmod -R u+x ../CUBRID");

		String buildId = context.getTestBuild();
		String[] arr = buildId.split("\\.");
		if ( Integer.parseInt(arr[0]) >= 10 )
		{
			scripts.addCommand("echo inquire_on_exit=3 >> $CUBRID/conf/cubrid.conf");
		}
                scripts.addCommand("echo error_log_size=800000000 >> $CUBRID/conf/cubrid.conf");
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
		
		//TODO: check cubridInstallName
		ShellInput scripts = new ShellInput();
		scripts.addCommand("echo 'ulimit -c unlimited' >> ~/.bash_profile");
		scripts.addCommand("cat ~/.bash_profile | uniq >  ~/.bash_profile_tmp; cp ~/.bash_profile_tmp ~/.bash_profile");
		scripts.addCommand("rm -rf ~/CUBRID");
		scripts.addCommand("rm -rf " + cubridInstallName + "*");
		scripts.addCommand("if [ ! -f " + cubridInstallName + " ] ");//TODO: no need
		scripts.addCommand("then");
		scripts.addCommand("    wget " + cubridPackageUrl);
		scripts.addCommand("fi");
		scripts.addCommand("sh " + cubridInstallName + " > /dev/null <<EOF");
		scripts.addCommand("yes");
		scripts.addCommand("");
		scripts.addCommand("");
		scripts.addCommand("");
		scripts.addCommand("EOF");
		scripts.addCommand("sh ~/.change_cubrid_ports.sh > /dev/null 2>&1");
		String buildId = context.getTestBuild();
		String[] arr = buildId.split("\\.");
		if ( Integer.parseInt(arr[0]) >= 10 )
		{
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
		scripts.addCommand(CommonUtils.getExportsOfMEKYParams());
		scripts.addCommand("run_cubrid_install " + role + " " + context.getCubridPackageUrl() + " " + context.getProperty("main.collaborate.url", "").trim());
		scripts.addCommand("sh ~/.change_cubrid_ports.sh > /dev/null 2>&1");
		String buildId = context.getTestBuild();
		String[] arr = buildId.split("\\.");
		if (Integer.parseInt(arr[0]) >= 10) {
			scripts.addCommand("echo inquire_on_exit=3 >> $CUBRID/conf/cubrid.conf");
		}
		scripts.addCommand("echo error_log_size=800000000 >> $CUBRID/conf/cubrid.conf");
		
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
		scripts.addCommand("echo 'BEGIN TO UPGRADE cubrid_common'");
		scripts.addCommand("cd $HOME/cubrid_common");
		scripts.addCommand("sh upgrade.sh 2>&1");
		
		String result;
		try {
			result = ssh.execute(scripts);
			log.println(result);
		} catch (Exception e) {
			log.print("[ERROR] " + e.getMessage());
		}
	}
	
	private void deploy_core_analyzer() {
		
		ShellInput scripts = new ShellInput();
		scripts.addCommand("echo 'BEGIN TO UPGRADE core_analyzer'");
		scripts.addCommand("cd $HOME/core_analyzer");
		scripts.addCommand("sh upgrade.sh 2>&1");
		
		String result;
		try {
			result = ssh.execute(scripts);
			log.println(result);
		} catch (Exception e) {
			log.print("[ERROR] " + e.getMessage());
		}
	}
	
	private void deploy_cqt() {
		
		ShellInput scripts = new ShellInput();
		scripts.addCommand("echo 'BEGIN TO UPGRADE CQT'");
		scripts.addCommand("cd $HOME/CQT");
		scripts.addCommand("sh upgrade.sh 2>&1");
		
		String result;
		try {
			result = ssh.execute(scripts);
			log.println(result);
		} catch (Exception e) {
			log.print("[ERROR] " + e.getMessage());
		}
	}
	
	public String backup_windows() {
		WinShellInput scripts = new WinShellInput();
		scripts.addCommand("cd $CUBRID/..");
		scripts.addCommand("rm -rf .CUBRID_SHELL_FM > /dev/null 2>&1");
		scripts.addCommand("cp -r CUBRID .CUBRID_SHELL_FM");
		scripts.addCommand("ls .CUBRID_SHELL_FM/conf/*");
		String result="";
		try {
			result = ssh.execute(scripts);
			log.println(result);
		} catch (Exception e) {
			log.print("[ERROR] " + e.getMessage());
		}
		
		return result;
	}

	public void backup_linux() {
		ShellInput scripts = new ShellInput();
		scripts.addCommand("rm -rf ~/.CUBRID_SHELL_FM > /dev/null 2>&1");
		scripts.addCommand("cp -r ~/CUBRID ~/.CUBRID_SHELL_FM");
		String result;
		try {
			result = ssh.execute(scripts);
			log.println(result);
		} catch (Exception e) {
			log.print("[ERROR] " + e.getMessage());
		}
	}
	
	public void cleanProcess() {
		String result = CommonUtils.resetProcess(ssh, context.isWindows());
		System.out.println("CLEAN PROCESSES:");
		System.out.println(result);	
	}

	public void close() {
		ssh.close();
	}
}
