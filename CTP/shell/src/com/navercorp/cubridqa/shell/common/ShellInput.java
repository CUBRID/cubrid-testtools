package com.navercorp.cubridqa.shell.common;


public class ShellInput {
	
	StringBuilder cmds;
	boolean isWindows;
	boolean userExperted = false;
	
	public static final String LINE_SEPARATOR = "\n";
	
	public static final String START_FLAG_MOCK ="ALL_${NOTEXIST}STARTED";
	public static final String COMP_FLAG_MOCK ="ALL_${NOTEXIST}COMPLETED";
	public static final String START_FLAG="ALL_STARTED";
	public static final String COMP_FLAG="ALL_COMPLETED";
	
	public ShellInput() {
		this(null, null, false);
	}
	
	public ShellInput(String cmd) {
		this(cmd, null, false);
	}

	public ShellInput(String cmd, boolean isWindows) {
		this(cmd, null, isWindows);
	}
	
	protected ShellInput(String cmd, String scriptToRun, boolean isWindows){
		this.isWindows = isWindows;
		cmds= new StringBuilder();
		if(scriptToRun==null && !isWindows){
			scriptToRun = ". ~/.bash_profile";
			addCommand(scriptToRun);
		} else {			
			addCommand("export HOME=`cd $QA_REPOSITORY/..;pwd`");
			addCommand("export CUBRID_CHARSET=en_US");
			addCommand("export CUBRID_LANG=en_US");
			addCommand("export CUBRID_MSG_LANG=en_US");
		}
		
		addCommand("echo " + START_FLAG_MOCK);
		if(cmd!=null) {
			addCommand(cmd);
		}
	}

	public void addCommand(String cmd){
		cmds.append(cmd).append(LINE_SEPARATOR);
	}
	
	public String getCommands(String user) {
		boolean shouldExportUser = false;
		if(isWindows && user != null && userExperted == false) {
			shouldExportUser = true;
			userExperted = true;
		}
		
		String result = (shouldExportUser ? "export USER=" + user + LINE_SEPARATOR : "") + cmds.toString() + "echo "+ COMP_FLAG_MOCK + LINE_SEPARATOR;
		
		if(!isWindows) {
			return result;
		} else if(SSHConnect.SERVICE_PROTOCOL.equals("ssh")){
			result = CommonUtils.replace(result, "\"", "\\\"");
			return "bash -c \"" + result + "\"";
		} else if(SSHConnect.SERVICE_PROTOCOL.equals("rmi")){
			return result;
		} else {
			return result;				
		}
	}

	@Override
	public String toString() {
		return getCommands(null);
	}	
}
