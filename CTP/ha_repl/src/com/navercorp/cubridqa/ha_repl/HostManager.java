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
package com.navercorp.cubridqa.ha_repl;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Properties;
import java.util.Set;

import com.navercorp.cubridqa.ha_repl.common.SSHConnect;
import com.navercorp.cubridqa.ha_repl.common.ShellInput;

public class HostManager {

	private Hashtable<String, SSHConnect> hostTable;
	Properties props;

	public HostManager(Properties props) throws Exception {
		this.hostTable = new Hashtable<String, SSHConnect>();
		this.props = props;

		addHost("master", -1);
		addHost("slave");
		addHost("replica");
	}

	private void addHost(String role) throws Exception {
		int num = 1;
		while (true) {
			try {
				addHost(role, num++);
			} catch (Exception e) {
				break;
			}
		}
	}

	private SSHConnect addHost(String role, int num) throws Exception {
		String hostId = role + (num < 1 ? "" : num);
		String prefix = "ha." + hostId;

		String host = props.getProperty(prefix + ".ssh.host");
		int port = Integer.parseInt(props.getProperty(prefix + ".ssh.port"));
		String user = props.getProperty(prefix + ".ssh.user");
		String pwd = props.getProperty(prefix + ".ssh.password");
		return addHost(hostId, host, port, user, pwd);
	}

	public SSHConnect addHost(String hostId, String host, int port, String user, String pwd) throws Exception {
		System.out.print("connect " + user + "@" + host + ":" + port);

		if (this.hostTable.contains(hostId)) {
			throw new Exception("host already existed");
		}
		SSHConnect ssh = new SSHConnect(host, port, user, pwd);
		ssh.setTitle(hostId);
		System.out.println("  success");
		this.hostTable.put(hostId, ssh);
		return ssh;
	}

	public void refreshConnection() {
		Set set = hostTable.keySet();
		Iterator it = set.iterator();
		SSHConnect ssh;
		ShellInput script = new ShellInput("echo HELLO");
		String result;
		while (it.hasNext()) {
			ssh = hostTable.get(it.next());
			try {
				result = ssh.execute(script);
				if (result == null || !result.trim().equals("HELLO")) {
					ssh.close();
				}
			} catch (Exception e) {
				ssh.close();
			}
		}
	}

	public SSHConnect getHost(String hostId) {
		return hostTable.get(hostId);
	}

	public void removeHost(String hostId) {
		SSHConnect ssh = getHost(hostId);
		if (ssh != null) {
			ssh.close();
		}
	}

	public ArrayList<SSHConnect> getAllHost(String preHost) {
		ArrayList<SSHConnect> list = new ArrayList<SSHConnect>();
		int index = 1;
		SSHConnect ssh;
		while (true) {
			ssh = this.hostTable.get(preHost + (index++));
			if (ssh == null)
				break;
			list.add(ssh);
		}
		return list;
	}

	public void close() {
		for (String hostId : hostTable.keySet()) {
			try {
				removeHost(hostId);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
}
