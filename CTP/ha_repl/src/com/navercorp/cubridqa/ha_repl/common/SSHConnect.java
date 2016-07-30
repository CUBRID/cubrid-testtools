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
package com.navercorp.cubridqa.ha_repl.common;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Properties;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

public class SSHConnect {

	private Session session;

	String host;
	int port;
	String user;
	String pwd;
	String title;

	public SSHConnect(String host, int port, String user, String pwd) throws JSchException {

		this.host = host;
		this.port = port;
		this.user = user;
		this.pwd = pwd;

		reconnect();
	}

	public String toString() {
		return user + "@" + host + ":" + port + ":" + title;
	}

	private void reconnect() throws JSchException {
		JSch jsch = new JSch();
		this.session = jsch.getSession(user, host, port);
		this.session.setPassword(pwd);
		Properties config = new Properties();
		config.setProperty("StrictHostKeyChecking", "no");
		session.setConfig("PreferredAuthentications", "password,publickey,keyboard-interactive,");
		this.session.setConfig(config);
		this.session.connect();
	}

	public String execute(ShellInput scripts) throws Exception {

		if (session == null || !session.isConnected())
			reconnect();

		ChannelExec exec = (ChannelExec) session.openChannel("exec");

		InputStream in = exec.getInputStream();
		BufferedReader reader = new BufferedReader(new InputStreamReader(in));

		exec.setCommand(scripts.getCommands());
		exec.connect();

		StringBuffer buffer = new StringBuffer();

		String line;
		while ((line = reader.readLine()) != null) {
			buffer.append(line + Constants.LINE_SEPARATOR);
			if (buffer.indexOf(ShellInput.COMP_FLAG) > 0) {
				break;
			}
		}

		exec.disconnect();

		String result = buffer.toString();

		int p = result.indexOf(ShellInput.START_FLAG);
		if (p != -1) {
			result = result.substring(p + ShellInput.START_FLAG.length());
		}
		p = result.indexOf(ShellInput.COMP_FLAG);
		if (p != -1) {
			result = result.substring(0, p);
		}
		return result.trim();
	}

	public void wait(ShellInput scripts, String expectKeyworkInclude) throws Exception {
		String result;
		System.out.println();
		while (true) {
			try {
				System.out.print(".");
				result = execute(scripts);
				if (result != null && result.trim().indexOf(expectKeyworkInclude) != -1)
					break;
				Thread.sleep(2 * 1000);
			} catch (Exception e) {
			}
		}
	}

	public void reboot() throws Exception {
		ShellInput scripts = new ShellInput("reboot && echo REBOOT_OK");
		String result = execute(scripts);
		if (result.indexOf("REBOOT_OK") == -1) {
			throw new Exception("fail to reboot");
		}

		String kw = "ACTIVE_FLAG";
		scripts = new ShellInput("echo " + kw);
		while (true) {
			try {
				result = execute(scripts);
				if (result != null && result.trim().indexOf(kw) != -1) {
					Thread.sleep(2 * 1000);
				} else {
					break;
				}
			} catch (Exception e) {
				break;
			}
		}
		System.out.println("fail to connect and wait to start");
		wait(scripts, kw);
	}

	public void close() {
		try {
			session.disconnect();
			session = null;
		} catch (Exception e) {
		}
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getTitle() {
		return title;
	}

	public String getHost() {
		return host;
	}

	public int getPort() {
		return port;
	}

	public String getUser() {
		return user;
	}

	public String getPwd() {
		return pwd;
	}
}
