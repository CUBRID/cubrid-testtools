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

package com.navercorp.cubridqa.isolation.deploy;

import com.navercorp.cubridqa.common.CommonUtils;
import com.navercorp.cubridqa.common.ConfigParameterConstants;
import com.navercorp.cubridqa.common.Log;
import com.navercorp.cubridqa.isolation.Constants;
import com.navercorp.cubridqa.isolation.Context;
import com.navercorp.cubridqa.isolation.IsolationHelper;
import com.navercorp.cubridqa.isolation.IsolationScriptInput;
import com.navercorp.cubridqa.shell.common.SSHConnect;
import com.navercorp.cubridqa.shell.common.ShellScriptInput;

public class DeployOneNode {

	Context context;
	SSHConnect ssh;
	String cubridPackageUrl;

	String currEnvId;
	String envIdentify;
	Log log;

	public DeployOneNode(Context context, String currEnvId, Log log) throws Exception {
		this.context = context;
		this.currEnvId = currEnvId;

		envIdentify = "EnvId=" + currEnvId + "[" + IsolationHelper.getTestNodeTitle(context, currEnvId) + "]";
		this.ssh = IsolationHelper.createTestNodeConnect(context, currEnvId);

		this.cubridPackageUrl = context.getCubridPackageUrl();

		this.log = log;
	}

	private boolean installCUBRID(){
		boolean isSucc = true;
		cleanProcess();

		String buildUrl = context.getCubridPackageUrl();
		IsolationScriptInput scripts = new IsolationScriptInput();
		if (!context.isReInstallTestBuildYn()) {
			log.print("Skip build installation since cubrid_download_url is not configured!!");
		} else {
			String role = context.getProperty(ConfigParameterConstants.CUBRID_INSTALL_ROLE, "").trim();
			log.print("Start Install Build");
			
			scripts.addCommand("run_cubrid_install " + role + " " + buildUrl + " " + context.getProperty(ConfigParameterConstants.CUBRID_ADDITIONAL_DOWNLOAD_URL, "").trim() + " 2>&1");
		}
		
		String buildId = context.getBuildId();
		String[] arr = buildId.split("\\.");
		if (Integer.parseInt(arr[0]) >= 10) {
			scripts.addCommand("echo inquire_on_exit=3 >> $CUBRID/conf/cubrid.conf");
		}

		String result;
		try {
			result = ssh.execute(scripts);
			if(!com.navercorp.cubridqa.common.CommonUtils.isEmpty(result) && (result.indexOf("ERROR") != -1 || result.indexOf("No such file") != -1)){
				isSucc = false;
				log.println("[ERROR] build install fail!");
			}
			log.println(result);
		} catch (Exception e) {
			isSucc = false;
			log.println("[ERROR] " + e.getMessage());
		}
		
		return isSucc;
	}
	public void deploy() {
		while(true){
			if(installCUBRID()){
				break;
			}
			
			System.out.println("[INFO]: Retry to install build!");
			CommonUtils.sleep(5);
		}
		
		updateCUBRIDConfigurations();
	}
	
	private void updateCUBRIDConfigurations(){
		String cubridEnginParamsList = com.navercorp.cubridqa.common.CommonUtils
				.parseInstanceParametersByRole(this.context.getProperties(),
						ConfigParameterConstants.TEST_INSTANCE_PREFIX
								+ this.currEnvId,
						ConfigParameterConstants.ROLE_ENGINE);
		String cubridHAParamsList = com.navercorp.cubridqa.common.CommonUtils
				.parseInstanceParametersByRole(this.context.getProperties(),
						ConfigParameterConstants.TEST_INSTANCE_PREFIX
								+ this.currEnvId,
						ConfigParameterConstants.ROLE_HA);
		String cubridCMParamsList = com.navercorp.cubridqa.common.CommonUtils
				.parseInstanceParametersByRole(this.context.getProperties(),
						ConfigParameterConstants.TEST_INSTANCE_PREFIX
								+ this.currEnvId,
						ConfigParameterConstants.ROLE_CM);
		String cubridBrokerCommonParamsList = com.navercorp.cubridqa.common.CommonUtils
				.parseInstanceParametersByRole(this.context.getProperties(),
						ConfigParameterConstants.TEST_INSTANCE_PREFIX
								+ this.currEnvId,
						ConfigParameterConstants.ROLE_BROKER_COMMON);
		String cubridBroker1ParamsList = com.navercorp.cubridqa.common.CommonUtils
				.parseInstanceParametersByRole(this.context.getProperties(),
						ConfigParameterConstants.TEST_INSTANCE_PREFIX
								+ this.currEnvId,
						ConfigParameterConstants.ROLE_BROKER1);
		String cubridBroker2ParamsList = com.navercorp.cubridqa.common.CommonUtils
				.parseInstanceParametersByRole(this.context.getProperties(),
						ConfigParameterConstants.TEST_INSTANCE_PREFIX
								+ this.currEnvId,
						ConfigParameterConstants.ROLE_BROKER2);

		if (CommonUtils.isEmpty(cubridEnginParamsList)
				&& CommonUtils.isEmpty(cubridHAParamsList)
				&& CommonUtils.isEmpty(cubridCMParamsList)
				&& CommonUtils.isEmpty(cubridBroker1ParamsList)
				&& CommonUtils.isEmpty(cubridBroker2ParamsList)) {
			return;
		}

		ShellScriptInput scripts = new ShellScriptInput();
		if (!CommonUtils.isEmpty(cubridEnginParamsList)) {
			scripts.addCommand("ini.sh -s 'common' --separator '||' -u '"
					+ cubridEnginParamsList + "' $CUBRID/conf/cubrid.conf");
		}

		if (!CommonUtils.isEmpty(cubridHAParamsList)) {
			scripts.addCommand("ini.sh -s 'common' --separator '||' -u '"
					+ cubridHAParamsList + "' $CUBRID/conf/cubrid_ha.conf");
		}

		if (!CommonUtils.isEmpty(cubridCMParamsList)) {
			scripts.addCommand("ini.sh -s 'cm' --separator '||' -u '"
					+ cubridCMParamsList + "' $CUBRID/conf/cm.conf");
		}

		if (!CommonUtils.isEmpty(cubridBroker1ParamsList)) {
			scripts.addCommand("ini.sh -s '%query_editor' --separator '||' -u '"
					+ cubridBroker1ParamsList
					+ "' $CUBRID/conf/cubrid_broker.conf");
		}

		if (!CommonUtils.isEmpty(cubridBroker2ParamsList)) {
			scripts.addCommand("ini.sh -s '%BROKER1' --separator '||' -u '"
					+ cubridBroker2ParamsList
					+ "' $CUBRID/conf/cubrid_broker.conf");
		}

		if (!CommonUtils.isEmpty(cubridBrokerCommonParamsList)) {
			scripts.addCommand("ini.sh -s 'broker' --separator '||' -u '"
					+ cubridBrokerCommonParamsList
					+ "' $CUBRID/conf/cubrid_broker.conf");
		}

		String cubridBrokerSHMId = this.context.getInstanceProperty(
				this.currEnvId, ConfigParameterConstants.ROLE_BROKER_COMMON
						+ "." + "MASTER_SHM_ID");
		if (CommonUtils.isEmpty(cubridBrokerSHMId)) {
			String cubridPortId = this.context.getInstanceProperty(
					this.currEnvId, ConfigParameterConstants.ROLE_ENGINE
							+ "." + "cubrid_port_id");
			if (!CommonUtils.isEmpty(cubridPortId)) {
				scripts.addCommand("ini.sh -s 'broker' -u MASTER_SHM_ID="
						+ cubridPortId + " $CUBRID/conf/cubrid_broker.conf");
			}
		}else{
			scripts.addCommand("ini.sh -s 'broker' -u MASTER_SHM_ID=" + cubridBrokerSHMId + " $CUBRID/conf/cubrid_broker.conf");
		}

		String result = "";

		try {
			result = ssh.execute(scripts);
			log.println(result);
		} catch (Exception e) {
			e.printStackTrace();
			log.print("[ERROR] " + e.getMessage());
		}
	}
	
	public void cleanProcess() {
		if (context.isWindows()) {
			cleanProcess_windows();
		} else {
			cleanProcess_linux();
		}
	}

	private void cleanProcess_windows() {
		IsolationScriptInput scripts = new IsolationScriptInput();
		scripts.addCommand(Constants.WIN_KILL_PROCESS);
		String result;
		try {
			result = ssh.execute(scripts);
			log.println(result);
		} catch (Exception e) {
			log.print("[ERROR] " + e.getMessage());
		}
	}

	private void cleanProcess_linux() {
		IsolationScriptInput scripts = new IsolationScriptInput();
		scripts.addCommand(Constants.LIN_KILL_PROCESS);
		String result;
		try {
			result = ssh.execute(scripts);
			log.println(result);
		} catch (Exception e) {
			log.print("[ERROR] " + e.getMessage());
		}
	}

	public void close() {
		ssh.close();
	}
}
