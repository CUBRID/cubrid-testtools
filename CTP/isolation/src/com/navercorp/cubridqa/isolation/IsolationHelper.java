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

package com.navercorp.cubridqa.isolation;

import com.jcraft.jsch.JSchException;
import com.navercorp.cubridqa.common.ConfigParameterConstants;
import com.navercorp.cubridqa.shell.common.SSHConnect;

public class IsolationHelper {

	public final static String getTestNodeTitle(Context context, String envId) {
		String title;
		if (context.isExecuteAtLocal()) {
			title = "local";
		} else {
			String host = context.getInstanceProperty(envId, ConfigParameterConstants.TEST_INSTANCE_HOST_SUFFIX);
			String port = context.getInstanceProperty(envId, ConfigParameterConstants.TEST_INSTANCE_PORT_SUFFIX);
			String user = context.getInstanceProperty(envId, ConfigParameterConstants.TEST_INSTANCE_USER_SUFFIX);
			title = user + "@" + host + ":" + port;
		}

		return title;
	}

	public final static SSHConnect createFirstTestNodeConnect(Context context) throws JSchException {
		String envId = context.getEnvList().get(0);
		return createTestNodeConnect(context, envId);
	}

	public final static SSHConnect createTestNodeConnect(Context context, String envId) throws JSchException {
		SSHConnect ssh;
		if (context.isExecuteAtLocal()) {
			ssh = new SSHConnect();
		} else {
			String host = context.getInstanceProperty(envId, ConfigParameterConstants.TEST_INSTANCE_HOST_SUFFIX);
			String port = context.getInstanceProperty(envId, ConfigParameterConstants.TEST_INSTANCE_PORT_SUFFIX);
			String user = context.getInstanceProperty(envId, ConfigParameterConstants.TEST_INSTANCE_USER_SUFFIX);
			String pwd = context.getInstanceProperty(envId, ConfigParameterConstants.TEST_INSTANCE_PASSWORD_SUFFIX);

			ssh = new SSHConnect(host, port, user, pwd);
		}
		return ssh;
	}
}
