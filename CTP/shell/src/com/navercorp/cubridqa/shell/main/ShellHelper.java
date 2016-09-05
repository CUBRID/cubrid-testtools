package com.navercorp.cubridqa.shell.main;

import com.jcraft.jsch.JSchException;
import com.navercorp.cubridqa.shell.common.SSHConnect;

public class ShellHelper {

	public final static String getTestNodeTitle(Context context, String envId) {
		String host = context.getInstanceProperty(envId, "ssh.host");
		return getTestNodeTitle(context, envId, host);
	}

	public final static String getTestNodeTitle(Context context, String envId, String host) {
		String title;
		if (context.isExecuteAtLocal()) {
			title = "local";
		} else {
			String port = context.getInstanceProperty(envId, "ssh.port");
			String user = context.getInstanceProperty(envId, "ssh.user");

			title = user + "@" + host + ":" + port;
		}

		return title;
	}

	public final static SSHConnect createTestNodeConnect(Context context, String envId) throws JSchException {
		String host = context.getInstanceProperty(envId, "ssh.host");
		return createTestNodeConnect(context, envId, host);
	}

	public final static SSHConnect createTestNodeConnect(Context context, String envId, String host) throws JSchException {
		SSHConnect ssh;
		if (context.isExecuteAtLocal()) {
			ssh = new SSHConnect();
		} else {
			String port = context.getInstanceProperty(envId, "ssh.port");
			String user = context.getInstanceProperty(envId, "ssh.user");
			String pwd = context.getInstanceProperty(envId, "ssh.pwd");
			
			ssh = new SSHConnect(host, port, user, pwd, context.getServiceProtocolType());			
		}
		return ssh;
	}
}
