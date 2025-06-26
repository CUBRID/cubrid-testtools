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
package com.navercorp.cubridqa.cdc_repl.deploy;

import java.util.ArrayList;

import com.navercorp.cubridqa.common.CommonUtils;
import com.navercorp.cubridqa.common.ConfigParameterConstants;
import com.navercorp.cubridqa.common.Log;
import com.navercorp.cubridqa.cdc_repl.Context;
import com.navercorp.cubridqa.cdc_repl.InstanceManager;
import com.navercorp.cubridqa.shell.common.GeneralScriptInput;
import com.navercorp.cubridqa.shell.common.SSHConnect;

public class DeployNode {

	SSHConnect ssh;
	InstanceManager hostManager;
	Log log;
	Context context;

	public DeployNode(Context context, InstanceManager instanceManager, SSHConnect ssh) throws Exception {
		this.ssh = ssh;
		this.context = context;
		this.hostManager = instanceManager;
		this.log = new Log(context.getCurrentLogDir() + "/deploy_" + instanceManager.getEnvId() + ".log", true, true);
	}

	public void deploy() throws Exception {
		updateCTP();
		clean();
		if (context.isReInstallTestBuildYn()) {
			while (true) {
				if (installCUBRID()) {
					break;
				}

				System.out.println("[INFO]: Retry to install build!");
				CommonUtils.sleep(5);
			}
		}
		configCUBRID();
	}

	private void clean() throws Exception {
		StringBuffer s = new StringBuffer();
		s.append("cubrid service stop;");
		s.append("pkill cub").append(";");
		s.append("ipcs | grep $USER | awk '{print $2}' | xargs -i ipcrm -m {}").append(";");
		GeneralScriptInput script = new GeneralScriptInput(s.toString());
		script.addCommand("source ${CTP_HOME}/common/script/util_common.sh; clean_processes");
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
		GeneralScriptInput scripts = new GeneralScriptInput();
		scripts.addCommand("cd ${CTP_HOME}/common/script");

		String ctpBranchName = System.getenv(ConfigParameterConstants.CTP_BRANCH_NAME);
		if (!CommonUtils.isEmpty(ctpBranchName)) {
			scripts.addCommand("export CTP_BRANCH_NAME=" + ctpBranchName);
		}

		String skipUpgrade = System.getenv(ConfigParameterConstants.CTP_SKIP_UPDATE);
		if (!CommonUtils.isEmpty(ctpBranchName)) {
			scripts.addCommand("export CTP_SKIP_UPDATE=" + skipUpgrade);
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

	private boolean installCUBRID() throws Exception {
		boolean isSucc = true;
		String role = context.getProperty(ConfigParameterConstants.CUBRID_INSTALL_ROLE, "").trim();
		GeneralScriptInput scripts = new GeneralScriptInput();
		scripts.addCommand("chmod u+x ${CTP_HOME}/common/script/run_cubrid_install");
		scripts.addCommand("run_cubrid_install " + role + " " + context.getCubridPackageUrl() + " " + context.getProperty(ConfigParameterConstants.CUBRID_ADDITIONAL_DOWNLOAD_URL, "").trim() + " 2>&1");

		String result;
		try {
			log.log("==> Begin to install CUBRID on " + ssh.toString() + ":");
			result = ssh.execute(scripts);
			if (!com.navercorp.cubridqa.common.CommonUtils.isEmpty(result) && (result.indexOf("ERROR") != -1)) {
				isSucc = false;
				log.println("[ERROR] build install fail!");
			}
			log.log(result);
			log.log("DONE.");
		} catch (Exception e) {
			isSucc = false;
			throw new Exception("Fail to install CUBRID on " + ssh.toString() + ":" + e.getMessage());
		}

		return isSucc;
	}

	private void configCUBRID() throws Exception {
		GeneralScriptInput scripts = new GeneralScriptInput();
		// configure cubrid.conf
		String cubridEnginParamsList = com.navercorp.cubridqa.common.CommonUtils.parseInstanceParametersByRole(this.context.getProperties(), ConfigParameterConstants.TEST_INSTANCE_PREFIX
				+ this.hostManager.getEnvId(), ConfigParameterConstants.ROLE_ENGINE);
		String cubridCMParamsList = com.navercorp.cubridqa.common.CommonUtils.parseInstanceParametersByRole(this.context.getProperties(), ConfigParameterConstants.TEST_INSTANCE_PREFIX
				+ this.hostManager.getEnvId(), ConfigParameterConstants.ROLE_CM);
		String cubridBrokerCommonParamsList = com.navercorp.cubridqa.common.CommonUtils.parseInstanceParametersByRole(this.context.getProperties(), ConfigParameterConstants.TEST_INSTANCE_PREFIX
				+ this.hostManager.getEnvId(), ConfigParameterConstants.ROLE_BROKER_COMMON);
		String cubridBroker1ParamsList = com.navercorp.cubridqa.common.CommonUtils.parseInstanceParametersByRole(this.context.getProperties(), ConfigParameterConstants.TEST_INSTANCE_PREFIX
				+ this.hostManager.getEnvId(), ConfigParameterConstants.ROLE_BROKER1);
		String cubridBroker2ParamsList = com.navercorp.cubridqa.common.CommonUtils.parseInstanceParametersByRole(this.context.getProperties(), ConfigParameterConstants.TEST_INSTANCE_PREFIX
				+ this.hostManager.getEnvId(), ConfigParameterConstants.ROLE_BROKER2);

		if (CommonUtils.isEmpty(cubridEnginParamsList) && CommonUtils.isEmpty(cubridCMParamsList) && CommonUtils.isEmpty(cubridBroker1ParamsList)
				&& CommonUtils.isEmpty(cubridBroker2ParamsList)) {
			return;
		}

		if (!CommonUtils.isEmpty(cubridEnginParamsList)) {
			scripts.addCommand("ini.sh -s 'common' --separator '||' -u '" + cubridEnginParamsList + "' $CUBRID/conf/cubrid.conf");
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

		String cubridBrokerSHMId = hostManager.getInstanceProperty(ConfigParameterConstants.ROLE_BROKER_COMMON + "." + "MASTER_SHM_ID");
		if (CommonUtils.isEmpty(cubridBrokerSHMId)) {
			String cubridPortId = hostManager.getInstanceProperty(ConfigParameterConstants.ROLE_ENGINE + "." + "cubrid_port_id");
			if (!CommonUtils.isEmpty(cubridPortId)) {
				scripts.addCommand("ini.sh -s 'broker' -u MASTER_SHM_ID=" + cubridPortId + " ${CUBRID}/conf/cubrid_broker.conf");
			}
		} else {
			scripts.addCommand("ini.sh -s 'broker' -u MASTER_SHM_ID=" + cubridBrokerSHMId + " $CUBRID/conf/cubrid_broker.conf");
		}

		if (CommonUtils.supportInquireOnExit(context.getBuildId())) {
			scripts.addCommand("ini.sh -s 'common' $CUBRID/conf/cubrid.conf inquire_on_exit 3");
		}

		scripts.addCommand("ini.sh -s 'common' ${CUBRID}/conf/cubrid.conf test_mode yes");

		if (!context.enableSkipMakeLocale()) {
			scripts.addCommand("cp ${CUBRID}/conf/cubrid_locales.all.txt ${CUBRID}/conf/cubrid_locales.txt");
			scripts.addCommand("${CUBRID}/bin/make_locale.sh -t 64bit");
		}

		String result;
		try {
			log.log("==> Begin to config on " + ssh.toString() + ": ");
			result = ssh.execute(scripts);
			log.log(result);
			log.log("DONE.");
		} catch (Exception e) {
			log.log("ip:" + ssh.getHost() + "  user:" + ssh.getUser() + " exception:" + e.getMessage());
			throw new Exception("The deploy configStep() exception. exception:" + e.getMessage());
		}
	}

}
