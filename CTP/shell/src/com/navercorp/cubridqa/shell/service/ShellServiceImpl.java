package com.navercorp.cubridqa.shell.service;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.Properties;

import com.navercorp.cubridqa.shell.common.CommonUtils;
import com.navercorp.cubridqa.shell.common.LocalInvoker;

public class ShellServiceImpl extends UnicastRemoteObject implements ShellService {

	Properties props;
	String requiredUser;
	String requiredPwd;
	String requiredHosts;
	boolean isDebug;

	protected ShellServiceImpl(Properties props) throws RemoteException {
		super();
		this.props = props;
		
		String value = props.getProperty("main.service.acceptedhosts", "").trim();
		value = "," + CommonUtils.replace(value, " ", "") + ",";		
		this.requiredHosts  = value;
		
		value = props.getProperty("main.service.user", "").trim();
		this.requiredUser  = value;
		
		value = props.getProperty("main.service.pwd", "").trim();
		this.requiredPwd  = value;

		value = props.getProperty("main.service.mode", "").trim();
		this.isDebug  = value.equalsIgnoreCase("debug");
	}

	public String exec(String user, String pwd, String scripts) throws Exception {
		return exec(user, pwd, scripts, false);
	}
	
	public String exec(String user, String pwd, String scripts, boolean pureWindows) throws Exception {
		String clientHost = super.getClientHost();
		System.out.println();
		System.out.println("=========================================================================================");
		System.out.println("host: " + clientHost + ", user:" + user + "(" + new java.util.Date() + ")");
		System.out.println(scripts);
		if (requiredUser.equals(user) && requiredPwd.equals(pwd) && requiredHosts.indexOf( "," + clientHost + ",") != -1) {
			String result = LocalInvoker.exec(scripts, pureWindows, isDebug);
			System.out.println("WELCOME");
			return result;
		} else {
			System.out.println("DENY");
			return null;
		}
	}
}
