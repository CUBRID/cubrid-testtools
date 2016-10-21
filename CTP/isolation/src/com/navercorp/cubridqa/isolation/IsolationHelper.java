package com.navercorp.cubridqa.isolation;

import com.jcraft.jsch.JSchException;
import com.navercorp.cubridqa.common.ConfigParameterConstants;
import com.navercorp.cubridqa.shell.common.SSHConnect;

public class IsolationHelper {

	public final static String getTestNodeTitle(Context context, String envId) {
		String title;
		if (context.isExecuteAtLocal()) {
			title = "local";
		} else {
			String host = context.getInstanceProperty(envId, ConfigParameterConstants.TEST_INSTANCE_HOST_SUFFIX);
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
		SSHConnect ssh;
		if (context.isExecuteAtLocal()) {
			ssh = new SSHConnect();
		} else {
			String host = context.getInstanceProperty(envId, ConfigParameterConstants.TEST_INSTANCE_HOST_SUFFIX);
			String port = context.getInstanceProperty(envId, ConfigParameterConstants.TEST_INSTANCE_PORT_SUFFIX);
			String user = context.getInstanceProperty(envId, ConfigParameterConstants.TEST_INSTANCE_USER_SUFFIX);
			String pwd = context.getInstanceProperty(envId, ConfigParameterConstants.TEST_INSTANCE_PASSWORD_SUFFIX);

			ssh = new SSHConnect(host, port, user, pwd);
		}
		return ssh;
	}
}
