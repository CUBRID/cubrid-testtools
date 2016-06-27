package com.navercorp.cubridqa.shell.deploy;

import java.util.ArrayList;

import com.navercorp.cubridqa.shell.common.CommonUtils;
import com.navercorp.cubridqa.shell.common.Constants;
import com.navercorp.cubridqa.shell.common.Log;
import com.navercorp.cubridqa.shell.main.Context;

public class Deploy {

	Context context;
	String currEnvId;
	String cubridPackageUrl;
	
	String host, port, user, pwd;
	String envIdentify;
	
	Log log;
	
	public Deploy(Context context, String currEnvId, boolean laterJoined) throws Exception {
		this.context = context;
		this.currEnvId = currEnvId;

		this.host = context.getProperty("env." + currEnvId + ".ssh.host");
		this.port = context.getProperty("env." + currEnvId + ".ssh.port");
		this.user = context.getProperty("env." + currEnvId + ".ssh.user");
		envIdentify = "EnvId=" + currEnvId + "[" + user+"@"+host+":" + port + "]";

		this.cubridPackageUrl = context.getCubridPackageUrl();

		this.log = new Log(CommonUtils.concatFile(Constants.DIR_LOG_ROOT, "test_" + currEnvId + ".log"), false, laterJoined ? true : context.isContinueMode());
	}
	
	public void deploy() throws Exception {
		context.getFeedback().onDeployStart(envIdentify);
		
		DeployOneNode d = new DeployOneNode(context, currEnvId, host, log);
		d.deploy();
		d.close();
		
		ArrayList<String> relatedHosts = context.getRelatedHosts(currEnvId);
		for(String h: relatedHosts) {
			d = new DeployOneNode(context, currEnvId, h, log);
			d.deploy();
			d.close();
		}
		
		context.getFeedback().onDeployStop(envIdentify);
	}

	public void close() {
		this.log.close();
	}
	
}
