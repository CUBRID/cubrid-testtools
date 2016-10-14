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

import com.navercorp.cubridqa.common.ConfigParameterConstants;
import com.navercorp.cubridqa.shell.common.CommonUtils;
import com.navercorp.cubridqa.shell.common.Log;
import com.navercorp.cubridqa.shell.common.SSHConnect;
import com.navercorp.cubridqa.shell.common.ShellScriptInput;
import com.navercorp.cubridqa.shell.main.Context;
import com.navercorp.cubridqa.shell.main.ShellHelper;

public class DeployOneNode {

	Context context;
	SSHConnect ssh;
	String cubridPackageUrl;
	boolean isNewBuildNumberSystem;

	String envIdentify;
	String currentEnvId;
	Log log;

	public DeployOneNode(Context context, String currEnvId, String host, Log log) throws Exception {
		this.context = context;
		this.currentEnvId =currEnvId;

		envIdentify = "EnvId=" + currEnvId + "[" + (ShellHelper.getTestNodeTitle(context, currEnvId, host)) + "] with " + context.getServiceProtocolType() + " protocol!";

		this.ssh = ShellHelper.createTestNodeConnect(context, currEnvId, host);

		this.cubridPackageUrl = context.getCubridPackageUrl();
		this.isNewBuildNumberSystem= context.getIsNewBuildNumberSystem();
		
		this.log = log;
	}
	
	public void deploy() {
		deploy_ctp();

		if(context.isWindows()) {
			String ret="";
			deploy_build_on_windows();
			//update CUBRID ports
			updateCUBRIDConfigurations();
			
			while(true)
			{
				ret = backup_windows();
				int idx = ret.indexOf("conf");
				if(idx!=-1)
				{
					break;
				}
			}
		} else {
			deploy_build_on_linux();
			//update CUBRID ports
			updateCUBRIDConfigurations();
			backup_linux();
		}
		
	}

	private void deploy_build_on_windows() {
		cleanProcess();

		String role = context.getProperty(ConfigParameterConstants.CUBRID_INSTALL_ROLE, "").trim();
		ShellScriptInput scripts = new ShellScriptInput();
		if (!context.isReInstallTestBuildYn()) {
			log.print("Skip build installation since main.testbuild.url is not configured!!");
		} else {
			log.print("Start Install Build");
			scripts.addCommand("run_cubrid_install " + role + " " + context.getCubridPackageUrl() + " " + context.getProperty(ConfigParameterConstants.CUBRID_ADDITIONAL_DOWNLOAD_URL, "").trim() + " 2>&1");
			scripts.addCommand("cd $CUBRID/..");
			scripts.addCommand("chmod -R u+x CUBRID");
		}
		
		String buildId = context.getTestBuild();
		String[] arr = buildId.split("\\.");
		if ( Integer.parseInt(arr[0]) >= 10 )
		{
			scripts.addCommand("echo inquire_on_exit=3 >> $CUBRID/conf/cubrid.conf");
		}
                scripts.addCommand("echo error_log_size=800000000 >> $CUBRID/conf/cubrid.conf");

		String result;
		try {
			result = ssh.execute(scripts);
			log.println(result);
		} catch (Exception e) {
			log.print("[ERROR] " + e.getMessage());
		}
	}
	
	private void deploy_build_on_linux() {
		cleanProcess();
		
		String buildUrl = context.getCubridPackageUrl();
		ShellScriptInput scripts = new ShellScriptInput();
		if (!context.isReInstallTestBuildYn()) {
			log.print("Skip build installation since main.testbuild.url is not configured!!");
		} else {
			String role = context.getProperty(ConfigParameterConstants.CUBRID_INSTALL_ROLE, "").trim();
			log.print("Start Install Build");
			scripts.addCommand("echo 'ulimit -c unlimited' >> ~/.bash_profile");
			scripts.addCommand("cat ~/.bash_profile | uniq >  ~/.bash_profile_tmp; cp ~/.bash_profile_tmp ~/.bash_profile");
			scripts.addCommand(CommonUtils.getExportsOfMEKYParams());
			scripts.addCommand("run_cubrid_install " + role + " " + buildUrl + " " + context.getProperty(ConfigParameterConstants.CUBRID_ADDITIONAL_DOWNLOAD_URL, "").trim() + " 2>&1");
		}
		
		String buildId = context.getTestBuild();
		String[] arr = buildId.split("\\.");
		if (Integer.parseInt(arr[0]) >= 10) {
			scripts.addCommand("echo inquire_on_exit=3 >> $CUBRID/conf/cubrid.conf");
		}
		scripts.addCommand("echo error_log_size=800000000 >> $CUBRID/conf/cubrid.conf");
		
		String result;
		try {
			result = ssh.execute(scripts);
			log.println(result);
		} catch (Exception e) {
			log.print("[ERROR] " + e.getMessage());
		}
	}
		
	private void deploy_ctp() {
		String enableSkipUpgrade = context.getEnableSkipUpdate();
		String branchName = context.getCtpBranchName();
		
		ShellScriptInput scripts = new ShellScriptInput();
		scripts.addCommand("echo 'BEGIN TO UPGRADE CTP'");
		scripts.addCommand("export CTP_BRANCH_NAME=" + branchName);
		if(context.isExecuteAtLocal()) {
			scripts.addCommand("export SKIP_UPGRADE=1");
		} else {
			scripts.addCommand("export SKIP_UPGRADE=" + enableSkipUpgrade);
		}
		scripts.addCommand("cd ${init_path}/../../");
		scripts.addCommand("chmod u+x ./common/script/upgrade.sh");
		scripts.addCommand("chmod u+x ./bin/ini.sh");
		scripts.addCommand("./common/script/upgrade.sh 2>&1");
		scripts.addCommand("cd -");
		
		String result;
		try {
			result = ssh.execute(scripts);
			log.println(result);
		} catch (Exception e) {
			log.print("[ERROR] " + e.getMessage());
		}
	}
	
	private void updateCUBRIDConfigurations(){
		String cubridEnginParamsList = com.navercorp.cubridqa.common.CommonUtils.parseInstanceParametersByRole(this.context.getProperties(), ConfigParameterConstants.TEST_INSTANCE_PREFIX + this.currentEnvId, ConfigParameterConstants.ROLE_ENGINE);
		String cubridHAParamsList = com.navercorp.cubridqa.common.CommonUtils.parseInstanceParametersByRole(this.context.getProperties(), ConfigParameterConstants.TEST_INSTANCE_PREFIX + this.currentEnvId, ConfigParameterConstants.ROLE_HA);
		String cubridCMParamsList = com.navercorp.cubridqa.common.CommonUtils.parseInstanceParametersByRole(this.context.getProperties(), ConfigParameterConstants.TEST_INSTANCE_PREFIX + this.currentEnvId, ConfigParameterConstants.ROLE_CM);
		String cubridBrokerCommonParamsList = com.navercorp.cubridqa.common.CommonUtils.parseInstanceParametersByRole(this.context.getProperties(), ConfigParameterConstants.TEST_INSTANCE_PREFIX + this.currentEnvId, ConfigParameterConstants.ROLE_BROKER_COMMON);
		String cubridBroker1ParamsList = com.navercorp.cubridqa.common.CommonUtils.parseInstanceParametersByRole(this.context.getProperties(), ConfigParameterConstants.TEST_INSTANCE_PREFIX + this.currentEnvId, ConfigParameterConstants.ROLE_BROKER1);
		String cubridBroker2ParamsList = com.navercorp.cubridqa.common.CommonUtils.parseInstanceParametersByRole(this.context.getProperties(), ConfigParameterConstants.TEST_INSTANCE_PREFIX + this.currentEnvId, ConfigParameterConstants.ROLE_BROKER2);
		
		if (CommonUtils.isEmpty(cubridEnginParamsList) && CommonUtils.isEmpty(cubridHAParamsList) && CommonUtils.isEmpty(cubridCMParamsList)&& CommonUtils.isEmpty(cubridBroker1ParamsList)&& CommonUtils.isEmpty(cubridBroker2ParamsList)) {
			return;
		}
		
		ShellScriptInput scripts = new ShellScriptInput();
		if (!CommonUtils.isEmpty(cubridEnginParamsList)) {
			scripts.addCommand("ini.sh -s 'common' --separator '||' -u '" + cubridEnginParamsList + "' $CUBRID/conf/cubrid.conf");
		}
		
		if (!CommonUtils.isEmpty(cubridHAParamsList)) {
			scripts.addCommand("ini.sh -s 'common' --separator '||' -u '" + cubridHAParamsList + "' $CUBRID/conf/cubrid_ha.conf");
		}
		
		if (!CommonUtils.isEmpty(cubridCMParamsList)) {
			scripts.addCommand("ini.sh -s 'cm' --separator '||' -u '" + cubridCMParamsList + "' $CUBRID/conf/cm.conf");
		}
		
		if (!CommonUtils.isEmpty(cubridBroker1ParamsList)) {
			scripts.addCommand("ini.sh -s '%query_editor' --separator '||' -u '" + cubridBroker1ParamsList + "' $CUBRID/conf/cubrid_broker.conf");
		}
		
		if (!CommonUtils.isEmpty(cubridBroker2ParamsList)) {
			scripts.addCommand("ini.sh -s '%BROKER1' --separator '||' -u '" + cubridBroker2ParamsList + "' $CUBRID/conf/cubrid_broker.conf");
		}
		
		if (!CommonUtils.isEmpty(cubridBrokerCommonParamsList)) {
			scripts.addCommand("ini.sh -s 'broker' --separator '||' -u '" + cubridBrokerCommonParamsList + "' $CUBRID/conf/cubrid_broker.conf");
		}
		
		String cubridBrokerSHMId = this.context.getInstanceProperty(this.currentEnvId, ConfigParameterConstants.ROLE_BROKER_COMMON + "."  + "MASTER_SHM_ID");
		if(CommonUtils.isEmpty(cubridBrokerSHMId)){
			String cubridPortId = this.context.getInstanceProperty(this.currentEnvId, ConfigParameterConstants.ROLE_ENGINE + "."  + "cubrid_port_id");
			if(!CommonUtils.isEmpty(cubridPortId)){
				scripts.addCommand("ini.sh -s 'broker' -u MASTER_SHM_ID=" + cubridPortId + " $CUBRID/conf/cubrid_broker.conf");
			}
		}
		
		String result="";
		
		try {
			result=ssh.execute(scripts);
			log.println(result);
		} catch (Exception e) {
			e.printStackTrace();
			log.print("[ERROR] " + e.getMessage());
		}
	}
	
	public String backup_windows() {
		ShellScriptInput scripts = new ShellScriptInput();
		scripts.addCommand("cd $CUBRID/..");
		scripts.addCommand("rm -rf .CUBRID_SHELL_FM > /dev/null 2>&1");
		scripts.addCommand("cp -r CUBRID .CUBRID_SHELL_FM");
		scripts.addCommand("ls .CUBRID_SHELL_FM/conf/*");
		String result="";
		try {
			result = ssh.execute(scripts);
			log.println(result);
		} catch (Exception e) {
			log.print("[ERROR] " + e.getMessage());
		}
		
		return result;
	}

	public void backup_linux() {
		ShellScriptInput scripts = new ShellScriptInput();
		scripts.addCommand("rm -rf ~/.CUBRID_SHELL_FM > /dev/null 2>&1");
		scripts.addCommand("cp -r ~/CUBRID ~/.CUBRID_SHELL_FM");
		String result;
		try {
			result = ssh.execute(scripts);
			log.println(result);
		} catch (Exception e) {
			log.print("[ERROR] " + e.getMessage());
		}
	}
	
	public void cleanProcess() {
		String result = CommonUtils.resetProcess(ssh, context.isWindows(), context.isExecuteAtLocal());
		System.out.println("CLEAN PROCESSES:");
		System.out.println(result);	
	}

	public void close() {
		ssh.close();
	}
}
