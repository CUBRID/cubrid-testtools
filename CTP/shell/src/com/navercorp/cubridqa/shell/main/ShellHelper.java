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

package com.navercorp.cubridqa.shell.main;

import com.jcraft.jsch.JSchException;
import com.navercorp.cubridqa.common.ConfigParameterConstants;
import com.navercorp.cubridqa.shell.common.SSHConnect;

public class ShellHelper {

	public final static String getTestNodeTitle(Context context, String envId) {
		String host = context.getInstanceProperty(envId, ConfigParameterConstants.TEST_INSTANCE_HOST_SUFFIX);
		return getTestNodeTitle(context, envId, host);
	}

	public final static String getTestNodeTitle(Context context, String envId, String host) {
		String title;
		if (context.isExecuteAtLocal()) {
			String circleNodeIndex = System.getenv("CIRCLE_NODE_INDEX");
			if (circleNodeIndex != null && !circleNodeIndex.trim().isEmpty()) {
				title = "parallel-" + circleNodeIndex;
			} else {
				title = "local";
			}
		} else {
			String port = context.getInstanceProperty(envId, ConfigParameterConstants.TEST_INSTANCE_PORT_SUFFIX);
			String user = context.getInstanceProperty(envId, ConfigParameterConstants.TEST_INSTANCE_USER_SUFFIX);

			title = user + "@" + host + ":" + port;
		}

		return title;
	}

	public final static SSHConnect createFirstTestNodeConnect(Context context) throws JSchException {
		String envId = context.getEnvList().get(0);
		return createTestNodeConnect(context, envId);
	}

	public final static SSHConnect createTestNodeConnect(Context context, String envId) throws JSchException {
		String host = context.getInstanceProperty(envId, ConfigParameterConstants.TEST_INSTANCE_HOST_SUFFIX);
		return createTestNodeConnect(context, envId, host);
	}

	public final static SSHConnect createTestNodeConnect(Context context, String envId, String host) throws JSchException {
		SSHConnect ssh;
		if (context.isExecuteAtLocal()) {
			ssh = new SSHConnect();
		} else {
			String port = context.getInstanceProperty(envId, ConfigParameterConstants.TEST_INSTANCE_PORT_SUFFIX);
			String user = context.getInstanceProperty(envId, ConfigParameterConstants.TEST_INSTANCE_USER_SUFFIX);
			String pwd = context.getInstanceProperty(envId, ConfigParameterConstants.TEST_INSTANCE_PASSWORD_SUFFIX);

			ssh = new SSHConnect(host, port, user, pwd, context.getServiceProtocolType());
		}

		/*
		 * NOTE: the SSH read deadline is intentionally NOT set here. This factory is
		 * shared by discovery (Dispatch's one-time ~17k-file find), where a per-test
		 * deadline would wrongly abort the whole run. Callers that need a backstop
		 * apply one explicitly via applyTestcaseReadDeadline (worker main exec) or
		 * applySecondaryReadTimeout (related-host / monitor / cleanup exec).
		 */
		return ssh;
	}

	/*
	 * Shorter read timeout (seconds) for secondary/cleanup exec — related-host
	 * checks and the monitor's cleanup/trace exec. These are quick operations, so a
	 * tight bound lets the worker and the (single) monitor thread recover from a
	 * reachable-but-wedged node quickly instead of blocking for the full
	 * per-testcase deadline. Kept >= the keepalive window (60s x 10 = 600s).
	 */
	public static final int SECONDARY_READ_TIMEOUT_SECS = 600;

	/*
	 * Apply the per-testcase read deadline (testCaseTimeout + margin) to the
	 * worker's MAIN connection, the one that runs the test case. This is the hard
	 * backstop guaranteeing a hung case cannot block the worker forever.
	 * testCaseTimeout <= 0 keeps the legacy unbounded behavior.
	 */
	public final static void applyTestcaseReadDeadline(SSHConnect conn, Context context) {
		if (conn == null) {
			return;
		}
		try {
			int testCaseTimeout = Integer.parseInt(context.getTestCaseTimeout());
			if (testCaseTimeout > 0) {
				conn.setReadTimeoutSecs(testCaseTimeout + 300);
			}
		} catch (Exception e) {
			// leave default (-1, disabled)
		}
	}

	/*
	 * Apply the shorter secondary read timeout to a related-host / monitor /
	 * cleanup connection so every blocking SSH exec on a worker/monitor thread is
	 * bounded (not just the worker's main connection). Discovery connections are
	 * deliberately left unbounded.
	 */
	public final static void applySecondaryReadTimeout(SSHConnect conn) {
		if (conn == null) {
			return;
		}
		conn.setReadTimeoutSecs(SECONDARY_READ_TIMEOUT_SECS);
	}
}
