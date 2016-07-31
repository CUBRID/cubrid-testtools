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


import com.navercorp.cubridqa.common.CommonUtils;
import com.navercorp.cubridqa.common.Log;
import com.navercorp.cubridqa.shell.common.SSHConnect;
import com.navercorp.cubridqa.shell.common.GeneralShellInput;

public class TestMonitor {

	Context context;
	Test test;
	SSHConnect master;
	ArrayList<SSHConnect> slaveList;
	Log log;
	InstanceManager hostManager;

	public TestMonitor(Context context, Test test) throws Exception {

		this.context = context;
		this.test = test;
		hostManager = test.getInstanceManager();
		
		this.log = new Log(CommonUtils.concatFile(context.getCurrentLogDir(),  "monitor_" + hostManager.getEnvId() + ".log"), false, context.isContinueMode());

		this.master = hostManager.getHost("master");
		this.slaveList = hostManager.getAllHost("slave");
	}

	public void startMonitor() {
		while (test.isTestCompleted() == false) {
			monitorOnce();
			CommonUtils.sleep(5);
		}
		hostManager.close();
		log.println("MONITOR COMPLETED");
	}

	private void monitorOnce() {

		//log.println("alive");

		// try{
		// resolveSlaveIsInActiveMode();
		// } catch(Exception e ) {
		// e.printStackTrace();
		// }
		//
		// try{
		// resolveMasterIsInStandbyMode();
		// } catch(Exception e ) {
		// e.printStackTrace();
		// }
		//
		// try{
		// resolveSlaveIsBlocked();
		// } catch(Exception e ) {
		// e.printStackTrace();
		// }
	}

	private void resolveMasterIsInStandbyMode() throws Exception {
		String result;

		for (int i = 0; i < 100; i++) {
			result = getCurrentChangeMode(master);
			if (result.indexOf("is standby") == -1) {
				return;
			}
			CommonUtils.sleep(1);
		}

		GeneralShellInput script;

		// resolve standby in master: stop slave to ensure fail over
		context.getFeedback().onTestCaseMonitor(test.currentTestFile, "[RESOLVE-01] stop heartbeat in slave", hostManager.getEnvId());
		log.print("[RESOLVE-1] stop heartbeat in slave (file: " + test.currentTestFile + ") ... ");

		script = new GeneralShellInput("cubrid hb stop");
		for (SSHConnect ssh : slaveList) {
			ssh.execute(script);
		}
		CommonUtils.sleep(30);

		script = new GeneralShellInput("cubrid hb start");
		for (SSHConnect ssh : slaveList) {
			ssh.execute(script);
		}
		log.println("DONE");

		CommonUtils.sleep(10);
	}

	private void resolveSlaveIsInActiveMode() throws Exception {
		String result;
		boolean hasActive = false;
		SSHConnect activeSlave = null;
		for (SSHConnect ssh : slaveList) {
			result = getCurrentChangeMode(ssh);
			if (result.indexOf("is active") != -1) {
				hasActive = true;
				activeSlave = ssh;
				break;
			}
		}
		if (!hasActive)
			return;

		GeneralShellInput script;

		// resolve active in slave: stop slave to ensure fail over
		context.getFeedback().onTestCaseMonitor(test.currentTestFile, "[RESOLVE-02] stop heartbeat in slave", hostManager.getEnvId());
		log.print("[RESOLVE-2] stop heartbeat in slave (file: " + test.currentTestFile + ") ... ");
		script = new GeneralShellInput("cubrid hb stop");
		activeSlave.execute(script);

		CommonUtils.sleep(30);

		script = new GeneralShellInput("cubrid hb start");
		activeSlave.execute(script);
		log.println("DONE");

		CommonUtils.sleep(10);
	}

	public String getCurrentChangeMode(SSHConnect ssh) throws Exception {
		GeneralShellInput scriptMode = new GeneralShellInput("cubrid changemode " + hostManager.getTestDb());
		return ssh.execute(scriptMode);
	}

	public void resolveSlaveIsBlocked() throws Exception {
		String result;
		String currPID, lastPID = null;

		GeneralShellInput script = new GeneralShellInput("cubrid killtran -d " + hostManager.getTestDb() + "| grep sql | awk '{print $4}'");
		GeneralShellInput resolveScript = new GeneralShellInput("csql -u  dba " + hostManager.getTestDb() + " -c \"drop table qa_system_tb_flag\"");

		boolean needResolve;
		for (SSHConnect ssh : slaveList) {
			needResolve = true;
			for (int i = 0; i < 60; i++) {
				result = ssh.execute(script);
				currPID = result.trim();
				if (currPID.equals("") || (lastPID != null && currPID.equals(lastPID) == false)) {
					needResolve = false;
					break;
				}
				lastPID = currPID;
				CommonUtils.sleep(2);
			}
			if (needResolve) {
				context.getFeedback().onTestCaseMonitor(test.currentTestFile, "[RESOLVE-03] drop qa_system_tb_flag", hostManager.getEnvId());
				log.print("[RESOLVE-3] drop qa_system_tb_flag (file: " + test.currentTestFile + ") ... ");
				master.execute(resolveScript);
				log.println("DONE");
			}
		}
	}
}
