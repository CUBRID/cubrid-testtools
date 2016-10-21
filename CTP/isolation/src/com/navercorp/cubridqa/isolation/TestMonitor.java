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

package com.navercorp.cubridqa.isolation;

import com.navercorp.cubridqa.common.CommonUtils;
import com.navercorp.cubridqa.common.ConfigParameterConstants;

import com.navercorp.cubridqa.shell.common.SSHConnect;

public class TestMonitor {

	Test test;
	SSHConnect ssh;
	Context context;
	int testCaseTimeout;

	public TestMonitor(Context context, Test test) throws Exception {
		this.context = context;
		this.test = test;

		this.ssh = IsolationHelper.createTestNodeConnect(context, test.getCurrentEnvId());

		try {
			testCaseTimeout = Integer.parseInt(this.context.getTestCaseTimeoutInSec());
		} catch (Exception e) {
			testCaseTimeout = -1;
		}
	}

	public void startMonitor() {
		while (test.isStopped() == false) {
			monitorOnce();
			CommonUtils.sleep(5);
		}
	}

	private void monitorOnce() {

		try {
			resolveTimeout();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void resolveTimeout() {

		if (testCaseTimeout < 0 || test.startTime == 0)
			return;

		long endTime = System.currentTimeMillis();

		// System.out.println("end - start = " + (endTime -
		// test.startTime)/1000);
		// System.out.println("isTimeOut: " + test.isTimeOut);

		if (endTime - test.startTime < testCaseTimeout * 1000)
			return;

		IsolationScriptInput scripts = new IsolationScriptInput();
		scripts.addCommand(Constants.LIN_KILL_PROCESS);

		try {
			ssh.execute(scripts);
		} catch (Exception e) {
			e.printStackTrace();
		}
		test.testCaseSuccess = false;
		test.addResultItem("NOK", "timeout");
		test.isTimeOut = true;
		context.getFeedback().onTestCaseMonitor(test.testCaseFullName, "[RESOLVE] " + testCaseTimeout + " + timeout", test.envIdentify);
	}

	public void close() {
		this.ssh.close();
	}
}
