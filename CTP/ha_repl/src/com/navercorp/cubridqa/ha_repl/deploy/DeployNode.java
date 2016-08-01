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

import com.navercorp.cubridqa.common.CommonUtils;
import com.navercorp.cubridqa.common.Log;
import com.navercorp.cubridqa.ha_repl.Context;
import com.navercorp.cubridqa.ha_repl.InstanceManager;
import com.navercorp.cubridqa.shell.common.GeneralShellInput;
import com.navercorp.cubridqa.shell.common.SSHConnect;

public class DeployNode {

	SSHConnect ssh;
	int haRole;
	InstanceManager hostManager;
	Log log;
	Context context;

	public DeployNode(Context context, int haRole, InstanceManager instanceManager, SSHConnect ssh) throws Exception {
		this.ssh = ssh;
		this.haRole = haRole;
		this.context = context;
		this.hostManager = instanceManager;
		this.log = new Log(context.getCurrentLogDir() + "/deploy_" + instanceManager.getEnvId() + ".log", true, true);
	}

	public void deploy() throws Exception {
		updateCTP();
		clean();		
		installCUBRID();
		configCUBRID();
	}

	private void clean() throws Exception {
		StringBuffer s = new StringBuffer();
		s.append("cubrid service stop;");
		s.append("pkill cub").append(";");
		s.append("rm -rf ~/CUBRID").append(";");
		if (CommonUtils.isEmpty(hostManager.getTestDb()) == false) {
			s.append("rm -rf ~/" + hostManager.getTestDb()).append(";");
		}
		s.append("ipcs | grep $USER | awk '{print $2}' | xargs -i ipcrm -m {}").append(";");
		GeneralShellInput script = new GeneralShellInput(s.toString());
		try {
			log.log("==> Begin to clean on " + ssh.toString() + ": ");
			String result = ssh.execute(script);
			log.log(result);
			log.log("DONE.");
		} catch (Exception e) {
			throw new Exception("Fail to clean on " + ssh.toString() + ":" + e.getMessage());
		}
	}

	private void updateCTP() throws Exception {
		GeneralShellInput scripts = new GeneralShellInput();
		scripts.addCommand("cd ${CTP_HOME}/common/script");

		String ctpBranchName = System.getenv("CTP_BRANCH_NAME");
		if (!CommonUtils.isEmpty(ctpBranchName)) {
			scripts.addCommand("export CTP_BRANCH_NAME=" + ctpBranchName);
		}
		
		String skipUpgrade = System.getenv("SKIP_UPGRADE");
		if (!CommonUtils.isEmpty(ctpBranchName)) {
			scripts.addCommand("export SKIP_UPGRADE=" + skipUpgrade);
		}
		scripts.addCommand("chmod u+x upgrade.sh");
		scripts.addCommand("./upgrade.sh");
		
		String result;
		try {
			log.log("==> Begin to update CTP on " + ssh.toString() + ":");
			result = ssh.execute(scripts);
			log.log(result);
			log.log("DONE.");
		} catch (Exception e) {
			throw new Exception("Fail to update CTP on " + ssh.toString() + ":" + e.getMessage());
		}
	}
	
	private void installCUBRID() throws Exception {
		String role = context.getProperty("main.testing.role", "").trim();
		GeneralShellInput scripts = new GeneralShellInput();
		scripts.addCommand("chmod u+x ${CTP_HOME}/common/script/run_cubrid_install");
		scripts.addCommand("run_cubrid_install " + role + " " + context.getCubridPackageUrl() + " " + context.getProperty("main.testbuild.collaborate.url", "").trim() + " 2>&1");

		String result;
		try {
			log.log("==> Begin to install CUBRID on " + ssh.toString() + ":");
			result = ssh.execute(scripts);
			log.log(result);
			log.log("DONE.");
		} catch (Exception e) {
			throw new Exception("Fail to install CUBRID on " + ssh.toString() + ":" + e.getMessage());
		}
	}

	private void configCUBRID() throws Exception {
		GeneralShellInput scripts = new GeneralShellInput();
		// configure cubrid.conf
		String cubridPortId = this.hostManager.getInstanceProperty("cubrid.cubrid_port_id");
		String brokerPort = this.hostManager.getInstanceProperty("broker.BROKER_PORT");
		String haPortId = this.hostManager.getInstanceProperty("ha.ha_port_id");
		if (CommonUtils.isEmpty(haPortId)) {
			haPortId = "59901";
		}

		if (!CommonUtils.isEmpty(cubridPortId)) {
			scripts.addCommand("ini.sh -s 'common' $CUBRID/conf/cubrid.conf cubrid_port_id " + cubridPortId);
			scripts.addCommand("ini.sh -s 'broker' $CUBRID/conf/cubrid_broker.conf MASTER_SHM_ID " + cubridPortId);
		}
		scripts.addCommand("ini.sh -s 'common' $CUBRID/conf/cubrid.conf test_mode yes");
		scripts.addCommand("ini.sh -s 'common' $CUBRID/conf/cubrid.conf ha_mode on");

		if (CommonUtils.supportInquireOnExit(context.getBuildId())) {
			scripts.addCommand("ini.sh -s 'common' $CUBRID/conf/cubrid.conf inquire_on_exit 3");
		}

		scripts.addCommand("ini.sh -s '%query_editor' $CUBRID/conf/cubrid_broker.conf SERVICE OFF");

		if (!CommonUtils.isEmpty(brokerPort)) {
			scripts.addCommand("ini.sh -s '%BROKER1' $CUBRID/conf/cubrid_broker.conf BROKER_PORT " + brokerPort);
			scripts.addCommand("ini.sh -s '%BROKER1' $CUBRID/conf/cubrid_broker.conf APPL_SERVER_SHM_ID " + brokerPort);
		}

		scripts.addCommand("ini.sh -s '%BROKER1' -d CCI_DEFAULT_AUTOCOMMIT $CUBRID/conf/cubrid_broker.conf");

		scripts.addCommand("echo '[common]' > ~/CUBRID/conf/cubrid_ha.conf");
		scripts.addCommand("echo 'ha_mode=on' >> ~/CUBRID/conf/cubrid_ha.conf");
		scripts.addCommand("echo 'ha_port_id=" + haPortId + "' >> ~/CUBRID/conf/cubrid_ha.conf");
		scripts.addCommand("echo 'ha_node_list=" + calcHaNodeList() + "' >> ~/CUBRID/conf/cubrid_ha.conf");
		if (this.hostManager.supportReplica()) {
			scripts.addCommand("echo 'ha_replica_list=" + calcHaReplicaList() + "' >> ~/CUBRID/conf/cubrid_ha.conf");
		}
		scripts.addCommand("echo 'ha_db_list=" + hostManager.getTestDb() + "' >> ~/CUBRID/conf/cubrid_ha.conf");
		scripts.addCommand("echo 'ha_apply_max_mem_size=300'  >> ~/CUBRID/conf/cubrid_ha.conf");
		scripts.addCommand("echo 'ha_copy_sync_mode=sync:sync'  >> ~/CUBRID/conf/cubrid_ha.conf");

		scripts.addCommand("cp ~/CUBRID/conf/cubrid_locales.all.txt ~/CUBRID/conf/cubrid_locales.txt");
		scripts.addCommand("~/CUBRID/bin/make_locale.sh -t 64bit");

		String result;
		try {
			log.log("==> Begin to config HA on " + ssh.toString() + ": ");
			result = ssh.execute(scripts);
			log.log(result);
			log.log("DONE.");
		} catch (Exception e) {
			log.log("ip:" + ssh.getHost() + "  user:" + ssh.getUser() + " exception:" + e.getMessage());
			throw new Exception("The deploy configStep() exception. exception:" + e.getMessage());
		}
	}

	private String calcHaNodeList() throws Exception {
		StringBuffer haNodeList = new StringBuffer();

		String userName = hostManager.getInstanceProperty("master.ssh.user");
		haNodeList.append(userName).append('@');
		haNodeList.append(hostManager.getHost("master").execute("hostname").trim());

		ArrayList<SSHConnect> sshList = hostManager.getAllHost("slave");
		for (int i = 0; i < sshList.size(); i++) {
			haNodeList.append(':').append(sshList.get(i).execute("hostname").trim());
		}

		return haNodeList.toString();
	}

	private String calcHaReplicaList() throws Exception {
		StringBuffer haNodeList = new StringBuffer();

		String userName = hostManager.getInstanceProperty("master.ssh.user");
		haNodeList.append(userName).append('@');

		ArrayList<SSHConnect> sshList = hostManager.getAllHost("replica");
		for (int i = 0; i < sshList.size(); i++) {
			if (i > 0) {
				haNodeList.append(':');
			}
			haNodeList.append(sshList.get(i).execute("hostname").trim());
		}

		return haNodeList.toString();
	}
}
