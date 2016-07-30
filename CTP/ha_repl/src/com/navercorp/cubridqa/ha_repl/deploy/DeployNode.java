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
import java.util.Set;

import com.navercorp.cubridqa.ha_repl.Context;
import com.navercorp.cubridqa.ha_repl.HostManager;
import com.navercorp.cubridqa.ha_repl.common.Constants;
import com.navercorp.cubridqa.common.Log;
import com.navercorp.cubridqa.ha_repl.common.SSHConnect;
import com.navercorp.cubridqa.ha_repl.common.ShellInput;

public class DeployNode {

	Properties props;
	String cubridPackageUrl;
	SSHConnect ssh;
	int type;
	HostManager hostManager;
	String testDb;
	Log deploylog;
	String cubrid_port_id;
	String ha_port_id;
	String ha_cubrid_broker_port;
	String ha_cubrid_master_shm_id;
	String ha_node_list;
	String ha_replica_list;
	String ha_master_host_name;
	String ha_slave_host_name_list = "";
	String ha_replic_host_name_list = "";
	ArrayList<SSHConnect> slaveList, replicaList;
	SSHConnect masterSSH;
	Context context;

	public DeployNode(String cubridPackageUrl, Properties props, SSHConnect ssh, int type, HostManager hostManager, String envId, Context context) {
		this.cubridPackageUrl = cubridPackageUrl;
		this.props = props;
		this.ssh = ssh;
		this.type = type;
		this.hostManager = hostManager;
		this.context = context;
		this.deploylog = new Log(Constants.DIR_LOG_ROOT + "/deploy_" + envId + ".log", true, true);
	}

	public void deploy() throws Exception {
		initparam();
		cleanStep();
		deploy_cubrid_common();
		deploy_linux_by_cubrid_common();
		makeI18N();
		configStep();
		// startServiceAndVerifyStatus();
	}

	private void initparam() throws Exception {
		if (cubridPackageUrl == null || props == null || ssh == null || hostManager == null) {
			deploylog.log("FAIL: cubridPackageUrl is " + cubridPackageUrl + ";" + "props is " + props + ";" + "ssh is " + ssh + ";" + "hostManager is " + hostManager + ";");
			throw new Exception("The deploy init param exception.");
		}

		testDb = props.getProperty("ha.testdb");
		cubrid_port_id = props.getProperty("cubrid.port");
		ha_port_id = props.getProperty("cubrid.ha.port");
		ha_cubrid_broker_port = props.getProperty("cubrid.ha.broker.port");
		ha_cubrid_master_shm_id = props.getProperty("cubrid.ha.master.shm.id");
		ha_master_host_name = props.getProperty("ha.master.ssh.hostname");

		Set<Object> a = props.keySet();

		String user = props.getProperty("ha.master.ssh.user");

		ha_node_list = "";
		ha_replica_list = "";
		this.masterSSH = hostManager.getHost("master");
		this.slaveList = hostManager.getAllHost("slave");
		this.replicaList = hostManager.getAllHost("replica");

		if (masterSSH == null) {
			deploylog.log("FAIL: masterSSH is " + masterSSH + ";");
			throw new Exception("The deploy init param exception. masterSSH is null");
		}

		// Get ha_node_list
		ha_node_list += user + "@";
		ha_node_list += ha_master_host_name;
		// ha_node_list += masterSSH.getHost();

		int idx = 1;
		for (SSHConnect ssh : slaveList) {
			ha_slave_host_name_list += props.getProperty("ha.slave" + idx + ".ssh.hostname");
			ha_node_list += ":" + ha_slave_host_name_list;
			++idx;
			// ha_node_list += ":" + ssh.getHost();
		}

		// Get ha_replica_list
		if (!replicaList.isEmpty()) {
			ha_replica_list += user + "@";

			int length = replicaList.size();
			int replic_idx = 1;
			for (SSHConnect ssh : replicaList) {
				length--;
				if (length > 0) {
					ha_replic_host_name_list += props.getProperty("ha.replica" + replic_idx + ".ssh.hostname");
					++replic_idx;
					ha_replica_list += ha_replic_host_name_list + ":";
					// ha_replica_list += ssh.getHost() + ":";
				} else {
					ha_replic_host_name_list += props.getProperty("ha.replica" + replic_idx + ".ssh.hostname");
					++replic_idx;
					ha_replica_list += ha_replic_host_name_list;
				}
			}
		}

	}

	private void cleanStep() throws Exception {
		StringBuffer s = new StringBuffer();
		s.append("cubrid service stop;");
		s.append("pkill cub").append(";");
		s.append("rm -rf ~/CUBRID").append(";");
		s.append("ipcs | grep $USER | awk '{print $2}' | xargs -i ipcrm -m {}").append(";");
		ShellInput script = new ShellInput(s.toString());
		try {
			ssh.execute(script);
		} catch (Exception e) {
			deploylog.log("ip:" + ssh.getHost() + "  user:" + ssh.getUser() + " exception:" + e.getMessage());
			throw new Exception("The deploy cleanStep() exception. exception:" + e.getMessage());
		}
	}

	private void deploy_linux_by_cubrid_common() {

		String role = context.getProperty("main.testing.role", "").trim();
		deploylog.log("Start Install Build");
		ShellInput scripts = new ShellInput();

		scripts.addCommand("if [ \"`ulimit -c`\" != \"unlimited\" ] ; then ");
		scripts.addCommand("    echo 'ulimit -c unlimited' >> .bash_profile");
		scripts.addCommand("fi");
		scripts.addCommand("  run_cubrid_install " + role + " " + this.cubridPackageUrl + " " + context.getProperty("main.testbuild.collaborate.url", "").trim() + " 2>&1");

		String result;
		try {
			result = ssh.execute(scripts);
			deploylog.log(scripts.toString());
			deploylog.log(result);
		} catch (Exception e) {
			deploylog.log("[ERROR] " + e.getMessage());
		}
	}

	private void deploy_cubrid_common() {
		String gitUser = context.getProperty("main.git.user", "").trim();
		String gitPwd = context.getProperty("main.git.pwd", "").trim();
		String gitMail = context.getProperty("main.git.mail", "").trim();
		ShellInput scripts = new ShellInput();
		scripts.addCommand("function git_update_cubrid_common {");
		scripts.addCommand("  ( cd $HOME/cubrid_common; sh upgrade.sh)");
		scripts.addCommand("   export PATH=$HOME/cubrid_common:$PATH");
		scripts.addCommand("}");

		scripts.addCommand("git_update_cubrid_common");

		String result;
		try {
			result = ssh.execute(scripts);
			deploylog.log(scripts.toString());
			deploylog.log(result);
		} catch (Exception e) {
			deploylog.log("[ERROR] " + e.getMessage());
		}

	}

	private void makeI18N() throws Exception {
		ShellInput scripts = new ShellInput();
		scripts.addCommand("cp ~/CUBRID/conf/cubrid_locales.all.txt ~/CUBRID/conf/cubrid_locales.txt");
		scripts.addCommand("~/CUBRID/bin/make_locale.sh -t 64bit");
		scripts.addCommand("cubrid deletedb demodb");

		// Configuration supporting internationalization , we first use the
		// default Settings, so commented out
		// scripts.addCommand("echo 'export CUBRID_LANG=ko_KR.utf8' >>
		// ~/.bash_profile");
		try {
			ssh.execute(scripts);
		} catch (Exception e) {
			deploylog.println(e.getMessage());
			throw e;
		}
	}

	private void configStep() throws Exception {
		ShellInput scripts = new ShellInput();
		// configure cubrid.conf
		scripts.addCommand("sed -i 's/cubrid_port_id=1523/cubrid_port_id=" + this.cubrid_port_id + "/' ~/CUBRID/conf/cubrid.conf");
		scripts.addCommand("sed -i 's/data_buffer_size=512M/data_buffer_size=512M/' ~/CUBRID/conf/cubrid.conf");
		scripts.addCommand("cat ~/CUBRID/conf/cubrid.conf| grep -v \"ha_mode\" > ~/CUBRID/conf/cubrid.conf_tmp");
		scripts.addCommand("mv ~/CUBRID/conf/cubrid.conf_tmp ~/CUBRID/conf/cubrid.conf");
		scripts.addCommand("echo 'ha_mode=on' >> ~/CUBRID/conf/cubrid.conf");
		scripts.addCommand("echo 'test_mode=yes' >> ~/CUBRID/conf/cubrid.conf");
		scripts.addCommand("echo 'max_plan_cache_entries=1000' >> ~/CUBRID/conf/cubrid.conf");
		if (true == is_above_banana_vesion()) {
			scripts.addCommand("echo 'inquire_on_exit=3' >> ~/CUBRID/conf/cubrid.conf");
		}
		// for prefetch test.
		if (context.getProperty("main.testing.prefetch", "").trim().equalsIgnoreCase("true")) {
			scripts.addCommand("echo 'ha_prefetchlogdb_enable=true' >> ~/CUBRID/conf/cubrid.conf");
		}
		// configure cubrid_broker.conf
		scripts.addCommand("sed -i '/query_editor/,/CCI_DEFAULT_AUTOCOMMIT/d' ~/CUBRID/conf/cubrid_broker.conf");
		scripts.addCommand("sed -i 's/30001/" + ha_cubrid_master_shm_id + "/' ~/CUBRID/conf/cubrid_broker.conf");
		scripts.addCommand("sed -i 's/33000/" + ha_cubrid_broker_port + "/' ~/CUBRID/conf/cubrid_broker.conf");

		// configure cubrid_ha.conf
		scripts.addCommand("echo '[common]'>> ~/CUBRID/conf/cubrid_ha.conf");
		scripts.addCommand("echo 'ha_mode=on' >> ~/CUBRID/conf/cubrid_ha.conf");
		scripts.addCommand("echo 'ha_port_id=" + this.ha_port_id + "' >> ~/CUBRID/conf/cubrid_ha.conf");
		scripts.addCommand("echo 'ha_node_list=" + this.ha_node_list + "' >> ~/CUBRID/conf/cubrid_ha.conf");
		if (this.ha_replica_list.length() > 0) {
			scripts.addCommand("echo 'ha_replica_list=" + this.ha_replica_list + "' >> ~/CUBRID/conf/cubrid_ha.conf");
		}
		scripts.addCommand("echo 'ha_db_list=" + this.testDb + "' >> ~/CUBRID/conf/cubrid_ha.conf");
		scripts.addCommand("echo 'ha_apply_max_mem_size=300'  >> ~/CUBRID/conf/cubrid_ha.conf");
		scripts.addCommand("echo 'ha_copy_sync_mode=sync:sync'  >> ~/CUBRID/conf/cubrid_ha.conf");
		scripts.addCommand("EOF");

		try {
			ssh.execute(scripts);
		} catch (Exception e) {
			deploylog.log("ip:" + ssh.getHost() + "  user:" + ssh.getUser() + " exception:" + e.getMessage());
			throw new Exception("The deploy configStep() exception. exception:" + e.getMessage());
		}
	}

	private boolean is_above_banana_vesion() throws Exception {
		boolean flag = false;

		String buildId = context.getVersionId();
		String arr[] = buildId.split("\\.");

		deploylog.log("Version number:" + buildId);

		if (Integer.parseInt(arr[0]) >= 10) {
			flag = true;
		}

		return flag;
	}

}
