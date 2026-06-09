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

package com.navercorp.cubridqa.shell.common;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.rmi.Naming;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.navercorp.cubridqa.shell.service.ShellService;

public class SSHConnect {

	public final static String SERVICE_TYPE_SSH = "ssh";
	public final static String SERVICE_TYPE_RMI = "rmi";
	public final static String SERVICE_TYPE_LOCAL = "local";

	private Session session;

	String host;
	int port;
	String user;
	String pwd;
	String title;
	String serviceProtocol;

	final int MAX_TRY_TIME = 10;

	/*
	 * Connect timeout and keepalive settings (milliseconds) to detect dead
	 * nodes/sockets. Keepalive is kept generous (60s x 10 = 10 minutes) so a
	 * healthy but heavily-loaded/swapping test node is not falsely severed while
	 * still making progress; the execute() read deadline (testCaseTimeout + 300)
	 * is the real upper bound. Keepalive only shortens detection of a truly dead
	 * node from the full read deadline down to ~10 minutes.
	 */
	private static final int CONNECT_TIMEOUT_MS = 30 * 1000;
	private static final int SERVER_ALIVE_INTERVAL_MS = 60 * 1000;
	private static final int SERVER_ALIVE_COUNT_MAX = 10;

	/*
	 * Total read deadline for a single execute() over SSH, in seconds. -1 means
	 * disabled (legacy behavior: block until ALL_COMPLETED or channel EOF). When
	 * set, a watchdog forcibly disconnects the channel/session after the deadline
	 * so a hung test case cannot block the worker thread forever.
	 */
	private int readTimeoutSecs = -1;

	/* shared daemon watchdog scheduler for all SSHConnect instances */
	private static final ScheduledExecutorService WATCHDOG = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
		public Thread newThread(Runnable r) {
			Thread t = new Thread(r, "ssh-exec-watchdog");
			t.setDaemon(true);
			return t;
		}
	});

	public SSHConnect() throws JSchException {
		this(null, -1, null, null, SERVICE_TYPE_LOCAL);
	}

	public SSHConnect(String host, String port, String user, String pwd) throws JSchException {
		this(host, Integer.parseInt(port), user, pwd, SERVICE_TYPE_SSH);
	}

	public SSHConnect(String host, String port, String user, String pwd, String serviceProtocol) throws JSchException {
		this(host, Integer.parseInt(port), user, pwd, serviceProtocol);
	}

	public SSHConnect(String host, int port, String user, String pwd, String serviceProtocol) throws JSchException {
		this.host = host;
		this.port = port;
		this.user = user;
		this.pwd = pwd;
		this.serviceProtocol = serviceProtocol;
	}

	public String toString() {
		if (serviceProtocol != null && serviceProtocol.equals(SERVICE_TYPE_LOCAL)) {
			return "local";
		}

		String result = user + "@" + host + ":" + port;
		if (title != null) {
			result = result + ":" + title;
		}
		return result;
	}

	private void reconnect() throws JSchException {
		int count = 0;
		while (true) {
			JSch jsch = new JSch();
			this.session = jsch.getSession(user, host, port);
			this.session.setPassword(pwd);
			Properties config = new Properties();
			config.setProperty("StrictHostKeyChecking", "no");
			session.setConfig("PreferredAuthentications", "password,publickey,keyboard-interactive,");
			this.session.setConfig(config);
			this.session.setServerAliveInterval(SERVER_ALIVE_INTERVAL_MS);
			this.session.setServerAliveCountMax(SERVER_ALIVE_COUNT_MAX);
			this.session.connect(CONNECT_TIMEOUT_MS);
			if ((session != null && session.isConnected()) || count > MAX_TRY_TIME) {
				break;
			}
			count++;
		}
	}

	/**
	 * Set the total read deadline (in seconds) for a single SSH execute(). A value
	 * of -1 (default) disables the deadline and keeps the legacy blocking behavior.
	 */
	public void setReadTimeoutSecs(int readTimeoutSecs) {
		this.readTimeoutSecs = readTimeoutSecs;
	}

	public int getReadTimeoutSecs() {
		return this.readTimeoutSecs;
	}

	public String execute(ScriptInput scripts) throws Exception {
		return execute(scripts.getCommands(), scripts.isPureWindows);
	}

	public String execute(String scripts) throws Exception {
		return execute(scripts, false);
	}

	public String execute(String scripts, boolean pureWindows) throws Exception {
		// System.out.println(scripts);
		if (serviceProtocol.equals(SERVICE_TYPE_RMI)) {
			ShellService srv = null;
			String url = "rmi://" + host + ":" + port + "/shellService";
			while (true) {
				try {
					srv = (ShellService) Naming.lookup(url);
					break;
				} catch (Exception e) {
					System.out.println("RMI FAIL: " + url);
					CommonUtils.sleep(1);
					continue;
				}
			}
			return srv.exec(user, pwd, scripts, pureWindows);
		} else if (serviceProtocol.equals(SERVICE_TYPE_LOCAL)) {
			String raw = LocalInvoker.exec(scripts, pureWindows, false);
			return extractOutput(raw);
		}

		if (session == null || !session.isConnected())
			reconnect();

		ChannelExec exec = (ChannelExec) session.openChannel("exec");

		InputStream in = exec.getInputStream();
		ByteArrayOutputStream out = new ByteArrayOutputStream();

		byte[] b = new byte[1024];

		exec.setCommand(scripts);
		exec.connect();

		/*
		 * A hung test case never prints ALL_COMPLETED and may keep the channel's
		 * stdout fd open (background/grandchild processes), so neither the
		 * COMP_FLAG nor the channel EOF condition is ever reached and in.read()
		 * blocks forever. JSch's socket read is not interruptible via
		 * Thread.interrupt(), so we arm a watchdog that forcibly disconnects the
		 * channel and session once the read deadline passes; that makes in.read()
		 * return/throw and lets the worker thread escape with an exception.
		 */
		final ChannelExec execRef = exec;
		final Session sessionRef = this.session;
		final AtomicBoolean timedOut = new AtomicBoolean(false);
		ScheduledFuture<?> watch = null;
		if (readTimeoutSecs > 0) {
			watch = WATCHDOG.schedule(new Runnable() {
				public void run() {
					timedOut.set(true);
					try {
						execRef.disconnect();
					} catch (Exception e) {
					}
					try {
						/* disconnect only the session this watchdog was armed for, never a later reused session */
						if (sessionRef != null) {
							sessionRef.disconnect();
						}
					} catch (Exception e) {
					}
				}
			}, readTimeoutSecs, TimeUnit.SECONDS);
		}

		try {
			try {
				int len = 0;
				while ((len = in.read(b)) > 0) {
					out.write(b, 0, len);
					if (out.toString().indexOf(ScriptInput.COMP_FLAG) > 0) {
						break;
					}
				}
			} catch (Exception readEx) {
				if (timedOut.get()) {
					throw new SSHTimeoutException("SSH exec timeout after " + readTimeoutSecs + " seconds on " + toString());
				}
				throw readEx;
			}

			if (timedOut.get()) {
				throw new SSHTimeoutException("SSH exec timeout after " + readTimeoutSecs + " seconds on " + toString());
			}

			return extractOutput(out.toString());
		} finally {
			/*
			 * Always release the channel and cancel the watchdog, regardless of
			 * normal completion, timeout, or a read error. Otherwise a non-timeout
			 * I/O/JSch exception would leave the ChannelExec open and leak channels
			 * on the reused session over a long regression run. disconnect() is
			 * idempotent, so it is safe even if the watchdog already disconnected.
			 */
			if (watch != null) {
				watch.cancel(false);
			}
			try {
				exec.disconnect();
			} catch (Exception e) {
			}
		}
	}

	private static String extractOutput(String raw) {
		if (raw == null) {
			return raw;
		}

		String result = raw;

		int p = result.indexOf(ScriptInput.START_FLAG);
		if (p != -1) {
			result = result.substring(p + ScriptInput.START_FLAG.length());
		}
		p = result.indexOf(ScriptInput.COMP_FLAG);
		if (p != -1) {
			result = result.substring(0, p);
		}
		return result.trim();

	}

	public void restartRemoteAgent() throws Exception {
		try {
			execute("PLEASE_RESTART_AGENT");
		} catch (Exception e) {
		}

		wait("\n\necho COME BACK\n", "COME BACK");
	}

	public void wait(ScriptInput scripts, String expectKeyworkInclude) throws Exception {
		wait(scripts.getCommands(), expectKeyworkInclude);
	}

	public void wait(String scripts, String expectKeyworkInclude) throws Exception {
		String result;
		System.out.println();
		while (true) {
			try {
				System.out.print(".");
				result = execute(scripts);
				if (result != null && result.trim().indexOf(expectKeyworkInclude) != -1)
					break;
				Thread.sleep(2 * 1000);
			} catch (Exception e) {
			}
		}
	}

	public void reboot() throws Exception {
		ScriptInput scripts = new ScriptInput("reboot && echo REBOOT_OK");
		String result = execute(scripts);
		if (result.indexOf("REBOOT_OK") == -1) {
			throw new Exception("fail to reboot");
		}

		String kw = "ACTIVE_FLAG";
		scripts = new ScriptInput("echo " + kw);
		while (true) {
			try {
				result = execute(scripts);
				if (result != null && result.trim().indexOf(kw) != -1) {
					Thread.sleep(2 * 1000);
				} else {
					break;
				}
			} catch (Exception e) {
				break;
			}
		}
		System.out.println("fail to connect and wait to start");
		wait(scripts, kw);
	}

	public void close() {
		if (session != null) {
			try {
				session.disconnect();
			} catch (Exception e) {
				e.printStackTrace();
			}
			session = null;
		}
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getTitle() {
		return title;
	}

	public String getHost() {
		return host;
	}

	public int getPort() {
		return port;
	}

	public String getUser() {
		return user;
	}

	public String getPwd() {
		return pwd;
	}
}
