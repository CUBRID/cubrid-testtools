package com.navercorp.cubridqa.isolation;

import com.jcraft.jsch.JSchException;
import com.navercorp.cubridqa.shell.common.SSHConnect;

public class IsolationHelper {

	public final static String getTestNodeTitle(Context context, String envId) {
		String title;
		if (context.isExecuteAtLocal()) {
			title = "local";
		} else {
			String host = context.getInstanceProperty(envId, "ssh.host");
			String port = context.getInstanceProperty(envId, "ssh.port");
			String user = context.getInstanceProperty(envId, "ssh.user");
			title = user + "@" + host + ":" + port;
		}

		return title;
	}
	
	public final static SSHConnect createFirstTestNodeConnect(Context context) throws JSchException {
		String envId = context.getEnvList().get(0);
		return createTestNodeConnect(context, envId);
	}

	public final static SSHConnect createTestNodeConnect(Context context, String envId) throws JSchException {
		SSHConnect ssh;
		if (context.isExecuteAtLocal()) {
			ssh = new SSHConnect();
		} else {
			String host = context.getInstanceProperty(envId, "ssh.host");
			String port = context.getInstanceProperty(envId, "ssh.port");
			String user = context.getInstanceProperty(envId, "ssh.user");
			String pwd = context.getInstanceProperty(envId, "ssh.pwd");

			ssh = new SSHConnect(host, port, user, pwd);
		}
		return ssh;
	}
}
