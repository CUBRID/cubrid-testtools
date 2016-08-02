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
import com.navercorp.cubridqa.common.Log;
import com.navercorp.cubridqa.isolation.Constants;
import com.navercorp.cubridqa.isolation.Context;
import com.navercorp.cubridqa.isolation.IsolationShellInput;
import com.navercorp.cubridqa.shell.common.SSHConnect;

public class DeployOneNode {

	Context context;
	SSHConnect ssh;
	String cubridPackageUrl;

	String currEnvId;
	String envIdentify;
	Log log;

	public DeployOneNode(Context context, String currEnvId, String host, Log log) throws Exception {
		this.context = context;
		this.currEnvId = currEnvId;

		String port = context.getInstanceProperty(currEnvId, "ssh.port");
		String user = context.getInstanceProperty(currEnvId, "ssh.user");
		String pwd = context.getInstanceProperty(currEnvId, "ssh.pwd");
		envIdentify = "EnvId=" + currEnvId + "[" + user + "@" + host + ":" + port + "]";
		this.ssh = new SSHConnect(host, port, user, pwd);

		this.cubridPackageUrl = context.getCubridPackageUrl();

		this.log = log;
	}

	public void deploy() {
		cleanProcess();

		String role = context.getProperty("main.testing.role", "").trim();
		log.print("Start Install Build");
		IsolationShellInput scripts = new IsolationShellInput();
		scripts.addCommand("run_cubrid_install " + role + " " + context.getCubridPackageUrl() + " " + context.getProperty("main.collaborate.url", "").trim() + " 2>&1");
		String buildId = context.getBuildId();
		String[] arr = buildId.split("\\.");
		if (Integer.parseInt(arr[0]) >= 10) {
			scripts.addCommand("echo inquire_on_exit=3 >> $CUBRID/conf/cubrid.conf");
		}

		String result;
		try {
			result = ssh.execute(scripts);
			log.println(result);
		} catch (Exception e) {
			log.println("[ERROR] " + e.getMessage());
		}
		
		updateCUBRIDConfigurations();
	}
	
	private void updateCUBRIDConfigurations(){
		String cubridPortId, brokerFirstPort, brokerSecondPort;
		cubridPortId = context.getInstanceProperty(this.currEnvId, "cubrid.cubrid_port_id");
		brokerFirstPort = context.getInstanceProperty(this.currEnvId, "broker1.BROKER_PORT");
		brokerSecondPort = context.getInstanceProperty(this.currEnvId, "broker2.BROKER_PORT");
		
		if (CommonUtils.isEmpty(cubridPortId) && CommonUtils.isEmpty(brokerFirstPort) && CommonUtils.isEmpty(brokerSecondPort)) {
			return;
		}
		
		IsolationShellInput scripts = new IsolationShellInput();
		
		if (!CommonUtils.isEmpty(cubridPortId)) {
			scripts.addCommand("ini.sh -s 'common' -u cubrid_port_id=" + cubridPortId + " $CUBRID/conf/cubrid.conf");
			scripts.addCommand("ini.sh -s 'broker' -u MASTER_SHM_ID=" + cubridPortId + " $CUBRID/conf/cubrid_broker.conf");
		}
		
		if (!CommonUtils.isEmpty(brokerFirstPort)) {
			scripts.addCommand("ini.sh -s '%query_editor' -u BROKER_PORT=" + brokerFirstPort + " $CUBRID/conf/cubrid_broker.conf");
			scripts.addCommand("ini.sh -s '%query_editor' -u APPL_SERVER_SHM_ID=" + brokerFirstPort + " $CUBRID/conf/cubrid_broker.conf");
		}
		
		if (!CommonUtils.isEmpty(brokerSecondPort)) {
			scripts.addCommand("ini.sh -s '%BROKER1' -u BROKER_PORT=" + brokerSecondPort + " $CUBRID/conf/cubrid_broker.conf");
			scripts.addCommand("ini.sh -s '%BROKER1' -u APPL_SERVER_SHM_ID=" + brokerSecondPort + " $CUBRID/conf/cubrid_broker.conf");
		}
		
		String result;
		
		try {
			result=ssh.execute(scripts);
			log.println(result);
		} catch (Exception e) {
			e.printStackTrace();
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
		IsolationShellInput scripts = new IsolationShellInput();
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
		IsolationShellInput scripts = new IsolationShellInput();
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
