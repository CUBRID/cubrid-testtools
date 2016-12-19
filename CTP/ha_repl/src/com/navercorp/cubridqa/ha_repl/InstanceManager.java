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
import java.util.Set;

import com.navercorp.cubridqa.common.CommonUtils;
import com.navercorp.cubridqa.common.ConfigParameterConstants;
import com.navercorp.cubridqa.ha_repl.common.Constants;
import com.navercorp.cubridqa.shell.common.GeneralScriptInput;
import com.navercorp.cubridqa.shell.common.SSHConnect;

public class InstanceManager {

	private Hashtable<String, SSHConnect> hostTable;
	private String currEnvId;
	private Context context;
	private String testDb;

	public InstanceManager(Context context, String currEnvId) throws Exception {
		this.currEnvId = currEnvId;
		this.context = context;
		this.hostTable = new Hashtable<String, SSHConnect>();
		this.testDb = context.getProperty(ConfigParameterConstants.CUBRID_TESTDB_NAME, "xdb");

		addHost("master", -1);
		try {
			addHost("slave", -1);
		} catch (Exception ex) {
			addHost("slave");
		}

		try {
			addHost("replica", -1);
		} catch (Exception ex) {
			addHost("replica");
		}
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
		String host = getInstanceProperty(hostId, ConfigParameterConstants.TEST_INSTANCE_HOST_SUFFIX);
		if (CommonUtils.isEmpty(host)) {
			throw new Exception("Not Found More Hosts");
		}
		String port = getInstanceProperty(hostId, ConfigParameterConstants.TEST_INSTANCE_PORT_SUFFIX);
		String user = getInstanceProperty(hostId, ConfigParameterConstants.TEST_INSTANCE_USER_SUFFIX);
		String pwd = getInstanceProperty(hostId, ConfigParameterConstants.TEST_INSTANCE_PASSWORD_SUFFIX);
		return addHost(hostId, host, port, user, pwd);
	}

	public String getUserNameForMasterInstance() {
		return getInstanceProperty("master", ConfigParameterConstants.TEST_INSTANCE_USER_SUFFIX);
	}

	public String getInstanceProperty(String roleId, String key) {
		String val = getInstanceProperty(roleId + "." + key);
		if (CommonUtils.isEmpty(val)) {
			return context.getProperty("default." + key);
		} else {
			return val;
		}
	}

	public String getInstanceProperty(String key) {
		String value = context.getProperty(ConfigParameterConstants.TEST_INSTANCE_PREFIX + currEnvId + "." + key);
		if (CommonUtils.isEmpty(value)) {
			value = context.getProperty("default." + key);
		}
		return value;
	}

	public String getAvailablePort(SSHConnect ssh){
		if(ssh == null) return "";
		GeneralScriptInput script = new GeneralScriptInput(Constants.GET_BROKER_PORT_CMD);
		String result = "";
		try {
			result = ssh.execute(script);
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return result.trim();
	}
	
	public String getTestDb() {
		return this.testDb;
	}

	public SSHConnect addHost(String hostId, String host, String port, String user, String pwd) throws Exception {
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
		GeneralScriptInput script = new GeneralScriptInput("echo HELLO");
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

	public boolean supportReplica() {
		return this.hostTable.get("replica") != null || this.hostTable.get("replica1") != null;
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
		SSHConnect ssh = this.hostTable.get(preHost);
		if (ssh != null) {
			list.add(ssh);
			return list;
		}

		int index = 1;
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

	public ArrayList<SSHConnect> getAllNodeList() {
		ArrayList<SSHConnect> allList = new ArrayList<SSHConnect>();
		allList.add(getHost("master"));
		allList.addAll(getAllSlaveAndReplicaList());
		return allList;
	}

	public ArrayList<SSHConnect> getAllSlaveAndReplicaList() {
		ArrayList<SSHConnect> slaveAndReplicaList = new ArrayList<SSHConnect>();
		slaveAndReplicaList.addAll(getAllHost("slave"));
		slaveAndReplicaList.addAll(getAllHost("replica"));
		return slaveAndReplicaList;
	}

	public String getEnvId() {
		return this.currEnvId;
	}
}
