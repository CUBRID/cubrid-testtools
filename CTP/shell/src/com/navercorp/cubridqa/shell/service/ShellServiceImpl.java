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

package com.navercorp.cubridqa.shell.service;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.Properties;

import com.navercorp.cubridqa.shell.common.CommonUtils;
import com.navercorp.cubridqa.shell.common.LocalInvoker;

public class ShellServiceImpl extends UnicastRemoteObject implements ShellService {

	Properties props;
	String requiredUser;
	String requiredPwd;
	String requiredHosts;
	boolean isDebug;

	protected ShellServiceImpl(Properties props) throws RemoteException {
		super();
		this.props = props;
		
		String value = props.getProperty("main.service.acceptedhosts", "").trim();
		value = "," + CommonUtils.replace(value, " ", "") + ",";		
		this.requiredHosts  = value;
		
		value = props.getProperty("main.service.user", "").trim();
		this.requiredUser  = value;
		
		value = props.getProperty("main.service.pwd", "").trim();
		this.requiredPwd  = value;

		value = props.getProperty("main.service.mode", "").trim();
		this.isDebug  = value.equalsIgnoreCase("debug");
	}

	public String exec(String user, String pwd, String scripts) throws Exception {
		return exec(user, pwd, scripts, false);
	}
	
	public String exec(String user, String pwd, String scripts, boolean pureWindows) throws Exception {
		String clientHost = super.getClientHost();
		System.out.println();
		System.out.println("=========================================================================================");
		System.out.println("host: " + clientHost + ", user:" + user + "(" + new java.util.Date() + ")");
		System.out.println(scripts);
		if (requiredUser.equals(user) && requiredPwd.equals(pwd) && requiredHosts.indexOf( "," + clientHost + ",") != -1) {
			String result = LocalInvoker.exec(scripts, pureWindows, false);
			System.out.println("WELCOME");
			return result;
		} else {
			System.out.println("DENY");
			return null;
		}
	}
}
