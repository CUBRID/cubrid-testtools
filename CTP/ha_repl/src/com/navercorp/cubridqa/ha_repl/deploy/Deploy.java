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



import java.util.Properties;

import com.navercorp.cubridqa.ha_repl.Context;
import com.navercorp.cubridqa.ha_repl.HostManager;
import com.navercorp.cubridqa.ha_repl.common.Constants;
import com.navercorp.cubridqa.common.CommonUtils;
import com.navercorp.cubridqa.common.Log;
import com.navercorp.cubridqa.shell.common.SSHConnect;
import com.navercorp.cubridqa.shell.common.GeneralShellInput;

public class Deploy {

	String envId;
	HostManager hostManager;
	Properties props;
	Boolean logStart = false;
	String cubridPackageUrl;
	Context context;

	public Deploy(String envId, String configFilename, String cubridPackageUrl, Context context) throws Exception {
		this.envId = envId;
		this.props = CommonUtils.getProperties(configFilename);
		this.hostManager = new HostManager(props);

		this.cubridPackageUrl = cubridPackageUrl;
		this.context = context;
	}

	private void logMessage(String strMessage) {
		Log deploylog = null;
		if (logStart == false) {
			deploylog = new Log(context.getCurrentLogDir() + "/deploy_" + envId + ".log", true, false);
			logStart = true;
		} else {
			deploylog = new Log(context.getCurrentLogDir() + "/deploy_" + envId + ".log", true, true);
		}
		deploylog.log(strMessage);
		deploylog.close();

	}

	public void deployAll() throws Exception {
		// if(1==1) return;
		String tempMessage = "Start deployAll";
		logMessage(tempMessage);
		SSHConnect master = hostManager.getHost("master");
		deployOne(master, Constants.TYPE_MASTER, hostManager, envId);
		tempMessage = "envId is " + envId + ". Master delployment finished.";
		logMessage(tempMessage);
		ArrayList<SSHConnect> list;
		list = hostManager.getAllHost("slave");
		for (final SSHConnect slave : list) {
			deployOne(slave, Constants.TYPE_SLAVE, hostManager, envId);
		}
		tempMessage = "envId is " + envId + ". Slave delployment finished. Slave count is " + list.size() + ".";
		logMessage(tempMessage);
		list = hostManager.getAllHost("replica");
		for (final SSHConnect replica : list) {
			deployOne(replica, Constants.TYPE_REPLICA, hostManager, envId);
		}
		tempMessage = "envId is " + envId + ". Replica delployment finished. Replica count is " + list.size() + ".";
		logMessage(tempMessage);
	}

	public void close() {
		this.hostManager.close();
	}

	public void deployOne(SSHConnect ssh, int type, HostManager hostManager, String envId) throws Exception {
		DeployNode deployNode = new DeployNode(cubridPackageUrl, props, ssh, type, hostManager, envId, context);
		deployNode.deploy();
	}
}
