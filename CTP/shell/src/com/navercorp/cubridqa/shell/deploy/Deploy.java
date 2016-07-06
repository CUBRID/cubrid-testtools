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

import java.util.ArrayList;

import com.navercorp.cubridqa.shell.common.CommonUtils;
import com.navercorp.cubridqa.shell.common.Constants;
import com.navercorp.cubridqa.shell.common.Log;
import com.navercorp.cubridqa.shell.main.Context;

public class Deploy {

	Context context;
	String currEnvId;
	String cubridPackageUrl;
	
	String host, port, user, pwd;
	String envIdentify;
	
	Log log;
	
	public Deploy(Context context, String currEnvId, boolean laterJoined) throws Exception {
		this.context = context;
		this.currEnvId = currEnvId;

		this.host = context.getProperty("env." + currEnvId + ".ssh.host");
		this.port = context.getProperty("env." + currEnvId + ".ssh.port");
		this.user = context.getProperty("env." + currEnvId + ".ssh.user");
		envIdentify = "EnvId=" + currEnvId + "[" + user+"@"+host+":" + port + "]";

		this.cubridPackageUrl = context.getCubridPackageUrl();

		this.log = new Log(CommonUtils.concatFile(Constants.CURRENT_LOG_DIR, "test_" + currEnvId + ".log"), false, laterJoined ? true : context.isContinueMode());
	}
	
	public void deploy() throws Exception {
		context.getFeedback().onDeployStart(envIdentify);
		
		DeployOneNode d = new DeployOneNode(context, currEnvId, host, log);
		d.deploy();
		d.close();
		
		ArrayList<String> relatedHosts = context.getRelatedHosts(currEnvId);
		for(String h: relatedHosts) {
			d = new DeployOneNode(context, currEnvId, h, log);
			d.deploy();
			d.close();
		}
		
		context.getFeedback().onDeployStop(envIdentify);
	}

	public void close() {
		this.log.close();
	}
	
}
