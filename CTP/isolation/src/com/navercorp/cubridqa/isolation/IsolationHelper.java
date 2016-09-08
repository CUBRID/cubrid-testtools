package com.navercorp.cubridqa.isolation;

import com.jcraft.jsch.JSchException;
import com.navercorp.cubridqa.shell.common.SSHConnect;

public class IsolationHelper {

	public final static SSHConnect createFirstTestNodeConnect(Context context) throws JSchException {
		String envId = context.getEnvList().get(0);
		String host = context.getInstanceProperty(envId, "ssh.host");
		return createTestNodeConnect(context, envId, host);
	}

	public final static SSHConnect createTestNodeConnect(Context context, String envId, String host) throws JSchException {
		String port = context.getInstanceProperty(envId, "ssh.port");
		String user = context.getInstanceProperty(envId, "ssh.user");
		String pwd = context.getInstanceProperty(envId, "ssh.pwd");

		SSHConnect ssh = new SSHConnect(host, port, user, pwd);
		return ssh;
	}
}