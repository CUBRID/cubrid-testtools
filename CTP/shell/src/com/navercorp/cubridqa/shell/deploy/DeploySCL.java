/**
 * Copyright (c) 2016, CUBRID Corporation. All rights reserved.

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

import com.jcraft.jsch.JSchException;
import com.navercorp.cubridqa.common.ConfigParameterConstants;
import com.navercorp.cubridqa.shell.common.CommonUtils;
import com.navercorp.cubridqa.shell.common.Log;
import com.navercorp.cubridqa.shell.common.SSHConnect;
import com.navercorp.cubridqa.shell.common.ShellScriptInput;
import com.navercorp.cubridqa.shell.main.Context;
import com.navercorp.cubridqa.shell.main.ShellHelper;

public class DeploySCL {
	Context context;
	String tranSvrEnvId;
	String pageSvrIp;
	SSHConnect ssh;
	String port, user, pwd, transHost;

	Log log;

	public DeploySCL(Context context, String tranSvrEnvId, String pageSvrEnvIP, Log log) throws JSchException {
		this.context = context;
		this.tranSvrEnvId = tranSvrEnvId;
		this.pageSvrIp = pageSvrEnvIP;

		port = context.getInstanceProperty(tranSvrEnvId, ConfigParameterConstants.TEST_INSTANCE_PORT_SUFFIX);
		user = context.getInstanceProperty(tranSvrEnvId, ConfigParameterConstants.TEST_INSTANCE_USER_SUFFIX);
		pwd = context.getInstanceProperty(tranSvrEnvId, ConfigParameterConstants.TEST_INSTANCE_PASSWORD_SUFFIX);
		transHost = context.getInstanceProperty(tranSvrEnvId, ConfigParameterConstants.TEST_INSTANCE_HOST_SUFFIX);

		this.ssh = ShellHelper.createTestNodeConnect(context, tranSvrEnvId);
		this.log = log;
	}

	public void deploy() {
		log.print("===== Start to update SCL.properties =====");
		ShellScriptInput scripts = new ShellScriptInput();
		scripts.addCommand("ini.sh -u TRANSACTION_SERVER_IP=" + transHost + " $init_path/SCL.properties");
		scripts.addCommand("ini.sh -u TRANSACTION_SERVER_USER=" + this.user + " $init_path/SCL.properties");
		scripts.addCommand("ini.sh -u TRANSACTION_SERVER_PW=" + this.pwd + " $init_path/SCL.properties ");
		scripts.addCommand("ini.sh -u TRANSACTION_SERVER_SSH_PORT=" + this.port + " $init_path/SCL.properties");

		scripts.addCommand("ini.sh -u PAGE_SERVER_IP=" + pageSvrIp + " $init_path/SCL.properties");
		scripts.addCommand("ini.sh -u PAGE_SERVER_USER=" + this.user + " $init_path/SCL.properties");
		scripts.addCommand("ini.sh -u PAGE_SERVER_PW=" + this.pwd + " $init_path/SCL.properties ");
		scripts.addCommand("ini.sh -u PAGE_SERVER_SSH_PORT=" + this.port + " $init_path/SCL.properties");

		scripts.addCommand("cubrid_port_id_value=`ini.sh -s 'common' $CUBRID/conf/cubrid.conf cubrid_port_id`");
		scripts.addCommand("ini.sh -u CUBRID_PORT_ID=$cubrid_port_id_value $init_path/SCL.properties");
		scripts.addCommand("cubrid_master_shm_id_value=`ini.sh -s 'broker' $CUBRID/conf/cubrid_broker.conf MASTER_SHM_ID`");
		scripts.addCommand("ini.sh -u MASTER_SHM_ID=$cubrid_master_shm_id_value $init_path/SCL.properties");
		scripts.addCommand("cubrid_broker1_port_value=`ini.sh -s '%query_editor' $CUBRID/conf/cubrid_broker.conf BROKER_PORT` ");
		scripts.addCommand("ini.sh -u BROKER_PORT1=$cubrid_broker1_port_value $init_path/SCL.properties ");
		scripts.addCommand("cubrid_broker1_app_server_shm_value=`ini.sh -s '%query_editor' $CUBRID/conf/cubrid_broker.conf APPL_SERVER_SHM_ID`");
		scripts.addCommand("ini.sh -u APPL_SERVER_SHM_ID1=$cubrid_broker1_app_server_shm_value $init_path/SCL.properties ");
		scripts.addCommand("cubrid_broker2_port_value=`ini.sh -s '%BROKER1' $CUBRID/conf/cubrid_broker.conf BROKER_PORT` ");
		scripts.addCommand("ini.sh -u BROKER_PORT2=$cubrid_broker2_port_value $init_path/SCL.properties ");
		scripts.addCommand("cubrid_broker2_app_server_shm_value=`ini.sh -s '%BROKER1' $CUBRID/conf/cubrid_broker.conf APPL_SERVER_SHM_ID`");
		scripts.addCommand("ini.sh -u APPL_SERVER_SHM_ID2=$cubrid_broker2_app_server_shm_value $init_path/SCL.properties");

		String result = "";
		try {
			result = ssh.execute(scripts);
			log.println(result);
		} catch (Exception e) {
			e.printStackTrace();
			log.print("[ERROR] " + e.getMessage());
		}

		log.print("===== End to update SCL.properties =====");
	}

	public void close() {
		ssh.close();
	}
}
