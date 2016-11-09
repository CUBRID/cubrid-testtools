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
package com.navercorp.cubridqa.ha_repl.deploy;

import java.util.ArrayList;

import com.navercorp.cubridqa.common.Log;
import com.navercorp.cubridqa.ha_repl.Context;
import com.navercorp.cubridqa.ha_repl.InstanceManager;
import com.navercorp.cubridqa.ha_repl.common.Constants;
import com.navercorp.cubridqa.shell.common.SSHConnect;

public class Deploy {

	String envId;
	InstanceManager hostManager;
	Boolean logStart = false;
	Context context;
	Log log;

	public Deploy(Context context, String envId) throws Exception {
		this.envId = envId;
		this.context = context;
		this.hostManager = new InstanceManager(context, envId);
		this.log = new Log(context.getCurrentLogDir() + "/deploy_" + envId + ".log", true, context.isContinueMode());
	}

	public void deployAll() throws Exception {
		SSHConnect ssh;

		log.log("Start deployAll");

		ssh = hostManager.getHost("master");
		deployOne(Constants.TYPE_MASTER, ssh);
		log.log("envId is " + envId + ". Master deployment finished." + ssh.getTitle());

		ArrayList<SSHConnect> list;

		list = hostManager.getAllHost("slave");
		for (SSHConnect slave : list) {
			deployOne(Constants.TYPE_SLAVE, slave);
			log.log("envId is " + envId + ". Slave deployment finished." + slave.getTitle());
		}

		list = hostManager.getAllHost("replica");
		for (final SSHConnect replica : list) {
			deployOne(Constants.TYPE_REPLICA, replica);
			log.log("envId is " + envId + ". Replica deployment finished." + replica.getTitle());
		}
	}

	public void close() {
		this.hostManager.close();
	}

	private void deployOne(int haRole, SSHConnect ssh) throws Exception {
		DeployNode deployNode = new DeployNode(context, haRole, hostManager, ssh);
		deployNode.deploy();
	}
}
