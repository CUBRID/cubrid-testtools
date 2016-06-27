package com.navercorp.cubridqa.shell.common;

import java.io.InputStream;
import java.rmi.Naming;
import java.util.Properties;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.navercorp.cubridqa.shell.service.ShellService;

public class SSHConnect {

	private Session session;

	String host;
	int port;
	String user;
	String pwd;
	String title;
	
	final int MAX_TRY_TIME = 10;
	
	public static String SERVICE_PROTOCOL;
	static {
		try {
			Properties props = CommonUtils.getProperties(CommonUtils.concatFile(Constants.DIR_CONF, "main.properties"));
			SERVICE_PROTOCOL = props.getProperty("main.service.protocol", "ssh").trim().toLowerCase();
		} catch (Exception e) {
		}
	}
	
	public static void main(String[] args)  throws Exception {
		
		SSHConnect ssh = new SSHConnect("10.99.209.188", "1099", "qa", "cubridqa135!#%");

		WinShellInput script = new WinShellInput();

		for(int i=0;i<1;i++) {
		String result = ssh.execute("wmic PROCESS WHERE ( name = 'java.exe' AND NOT CommandLine LIKE '%com.nhncorp.cubrid.service.Server%' ) get Name,CommandLine", true);
		System.out.println(result);
		Thread.sleep(1000);
		}
		ssh.close();
	
	}
	
	public SSHConnect(String host, String port, String user, String pwd) throws JSchException {
		this(host, Integer.parseInt(port), user, pwd);
	}

	public SSHConnect(String host, int port, String user, String pwd) throws JSchException {

		this.host = host;
		this.port = port;
		this.user = user;
		this.pwd = pwd;
	}

	public String toString() {
		return user + "@" + host + ":" + port + ":" + title;
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
			this.session.connect();
			if ((session != null && session.isConnected()) || count > MAX_TRY_TIME) {
				break;
			}
			count++;
		}
	}
	
	public String execute(ShellInput scripts) throws Exception {
		return execute(scripts.getCommands(user));
	}
	
	public String execute(String scripts) throws Exception {
		return execute(scripts, false);
	}

	public String execute(String scripts, boolean pureWindows) throws Exception {
		//System.out.println(scripts);
		if (SERVICE_PROTOCOL.equals("rmi")) {
			ShellService srv = null;
			String url = "rmi://" + host + ":" + port + "/shellService";
			while(true) {
				try{					
					srv = (ShellService) Naming.lookup(url);
					break;
				} catch(Exception e) {
					System.out.println("RMI FAIL: " + url);
					CommonUtils.sleep(1);
					continue;
				}				
			}			
			return pureWindows ? srv.exec(user, pwd, scripts, true): srv.exec(user, pwd, scripts);
		}

		if (session == null || !session.isConnected())
			reconnect();

		ChannelExec exec = (ChannelExec) session.openChannel("exec");

		InputStream in = exec.getInputStream();
		byte[] b = new byte[1024];

		exec.setCommand(scripts);
		exec.connect();

		StringBuffer buffer = new StringBuffer();

		int len = 0;
		while ((len = in.read(b)) > 0) {
			buffer.append(new String(b, 0, len));

			if (buffer.toString().indexOf(ShellInput.COMP_FLAG) > 0) {
				break;
			}
		}

		exec.disconnect();

		String result = buffer.toString();

		int p = result.indexOf(ShellInput.START_FLAG);
		if (p != -1) {
			result = result.substring(p + ShellInput.START_FLAG.length());
		}
		p = result.indexOf(ShellInput.COMP_FLAG);
		if (p != -1) {
			result = result.substring(0, p);
		}
		//System.out.println(result.trim());
		return result.trim();
	}

	public void wait(ShellInput scripts, String expectKeyworkInclude) throws Exception {
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
		ShellInput scripts = new ShellInput("reboot && echo REBOOT_OK");
		String result = execute(scripts);
		if (result.indexOf("REBOOT_OK") == -1) {
			throw new Exception("fail to reboot");
		}

		String kw = "ACTIVE_FLAG";
		scripts = new ShellInput("echo " + kw);
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
