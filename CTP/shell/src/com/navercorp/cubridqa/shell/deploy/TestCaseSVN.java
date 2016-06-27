package com.navercorp.cubridqa.shell.deploy;

import com.navercorp.cubridqa.shell.common.CommonUtils;
import com.navercorp.cubridqa.shell.common.Constants;
import com.navercorp.cubridqa.shell.common.SSHConnect;
import com.navercorp.cubridqa.shell.common.ShellInput;
import com.navercorp.cubridqa.shell.common.WinShellInput;
import com.navercorp.cubridqa.shell.main.Context;

public class TestCaseSVN {

	Context context;
	SSHConnect ssh;
	String currEnvId;
	String envIdentify;

	public TestCaseSVN(Context context, String currEnvId) throws Exception {
		this.context = context;
		this.currEnvId =currEnvId;
		String host = context.getProperty("env." + currEnvId + ".ssh.host");
		String port = context.getProperty("env." + currEnvId + ".ssh.port");
		String user = context.getProperty("env." + currEnvId + ".ssh.user");
		String pwd = context.getProperty("env." + currEnvId + ".ssh.pwd");
		
		envIdentify = "EnvId=" + currEnvId + "[" + user+"@"+host+":" + port + "]";

		this.ssh = new SSHConnect(host, port, user, pwd);
		
		
		if(context.isWindows()) {
			initWindows();
		}
	}
	
	public void update() throws Exception {
		context.getFeedback().onSvnUpdateStart(envIdentify);
		
		cleanProcess();
		
		//TODO if test case doesn't exist
		
		ShellInput scripts;
		if(context.isWindows()) {
			scripts = new WinShellInput();
		} else {
			scripts = new ShellInput();
		}
		
		String sedCmds;
		if(context.isWindows()) {
			sedCmds = "sed 's;\\\\;/;g'|";
		} else {
			sedCmds = "";
		}
		
		if(context.getCleanTestCase()) {
			scripts.addCommand("cd ");
			scripts.addCommand("cd " + context.getTestCaseRoot());
			scripts.addCommand("svn st " + context.getSVNUserInfo() + " | grep '~' | awk '{print $NF}' | " + sedCmds + " xargs -i rm -rf {} ");
			scripts.addCommand("svn st " + context.getSVNUserInfo() + " | grep '^M' | awk '{print $NF}' |" + sedCmds + " xargs -i rm -rf {} ");
			scripts.addCommand("svn st " + context.getSVNUserInfo() + " | grep '!' | awk '{print $NF}' | grep -v '\\.' |" + sedCmds + " xargs -i rm -rf {} ");

			scripts.addCommand("svn cleanup .");
			scripts.addCommand("svn revert -R -q .");
			scripts.addCommand("svn st " + context.getSVNUserInfo() + " | grep '?' | awk '{print $NF}' | " + sedCmds + "  xargs -i rm -rf {} " );
			scripts.addCommand("svn up " + context.getSVNUserInfo());

			scripts.addCommand("cd ");
			scripts.addCommand("cd $QA_REPOSITORY/lib/shell/common");
			scripts.addCommand("svn st " + context.getSVNUserInfo() + " | grep '~' | awk '{print $NF}' |" + sedCmds + "  xargs -i rm -rf {} ");
			scripts.addCommand("svn st " + context.getSVNUserInfo() + " | grep '!' | awk '{print $NF}' | grep -v '\\.' |" + sedCmds + "  xargs -i rm -rf {} ");
			scripts.addCommand("svn cleanup .");
			scripts.addCommand("svn revert -R -q .");
			scripts.addCommand("svn up " + context.getSVNUserInfo());
		}
		
		scripts.addCommand("cd ");
		scripts.addCommand("cd " + context.getTestCaseRoot());
		scripts.addCommand("echo 'EXPECT NOTHING FOR BELOW (" + this.currEnvId + ")'");
		scripts.addCommand("svn st " + context.getSVNUserInfo());
		scripts.addCommand("echo Above EnvId is " + this.currEnvId);
		if (!context.getTestCaseWorkspace().equals(context.getTestCaseRoot())) {
			String wsRoot = context.getTestCaseWorkspace().replace('\\', '/');
			String tcRoot = context.getTestCaseRoot().replace('\\', '/');
			String fromStar = CommonUtils.concatFile(tcRoot, "*").replace('\\', '/');
			String toStar = CommonUtils.concatFile(wsRoot, "*").replace('\\', '/');

			scripts.addCommand("mkdir -p " + wsRoot);
			scripts.addCommand("rm -rf " + toStar);
			scripts.addCommand("cp -r " + fromStar + " " + wsRoot);
		}
		
		String result;
		try {
			result = ssh.execute(scripts);
			System.out.println(result);
		} catch (Exception e) {
			System.out.print("[ERROR] " + e.getMessage());
			throw e;
		}
		System.out.println("SVN UPDATE COMPLETE");
		
		context.getFeedback().onSvnUpdateStop(envIdentify);
 	}
	
	public void cleanProcess() {
		String result = CommonUtils.resetProcess(ssh, context.isWindows());
		System.out.println("CLEAN PROCESSES:");
		System.out.println(result);
 	}

	
	
	private void initWindows() {
		WinShellInput scripts = new WinShellInput();

		scripts.addCommand("REG DELETE \"HKEY_LOCAL_MACHINE\\SYSTEM\\ControlSet001\\Control\\Session Manager\\Environment\" /v CUBRID_LANG /f");
		scripts.addCommand("REG DELETE \"HKEY_LOCAL_MACHINE\\SYSTEM\\ControlSet001\\Control\\Session Manager\\Environment\" /v CUBRID_CHARSET /f");
		scripts.addCommand("REG DELETE \"HKEY_LOCAL_MACHINE\\SYSTEM\\ControlSet001\\Control\\Session Manager\\Environment\" /v CUBRID_MSG_LANG /f");
		scripts.addCommand("REG ADD \"HKEY_LOCAL_MACHINE\\SYSTEM\\ControlSet001\\Control\\Session Manager\\Environment\" /v CUBRID_CHARSET /d en_US /f");
		scripts.addCommand("REG ADD \"HKEY_LOCAL_MACHINE\\SYSTEM\\ControlSet001\\Control\\Session Manager\\Environment\" /v CUBRID_LANG /d en_US /f");
		scripts.addCommand("REG ADD \"HKEY_LOCAL_MACHINE\\SYSTEM\\ControlSet001\\Control\\Session Manager\\Environment\" /v CUBRID_MSG_LANG /d en_US /f");

		try {
			ssh.execute(scripts);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void close() {
		ssh.close();
	}
}
