package com.navercorp.cubridqa.shell.service;

import java.rmi.Naming;
import java.rmi.registry.LocateRegistry;
import java.util.Properties;

import com.navercorp.cubridqa.shell.common.CommonUtils;
import com.navercorp.cubridqa.shell.common.Constants;

public class Server {

	public static void main(String[] args) {
		try {

			Properties props = CommonUtils.getProperties(CommonUtils.concatFile(Constants.DIR_CONF, "service.properties"));
			int port = Integer.parseInt(props.getProperty("main.service.port", "1099"));
			
			LocateRegistry.createRegistry(port);			
			
			ShellService shellService = new ShellServiceImpl(props);
			
			LocateRegistry.getRegistry(port).rebind("shellService", shellService);			
			
			//Naming.rebind("rmi://:" + port + "/shellService", shellService);

			System.out.println("Service Start!");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
