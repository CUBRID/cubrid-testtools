package com.navercorp.cubridqa.isolation;

import com.navercorp.cubridqa.common.CommonUtils;
import com.navercorp.cubridqa.common.Log;
import com.navercorp.cubridqa.shell.common.GeneralShellInput;
import com.navercorp.cubridqa.shell.common.SSHConnect;

public class CheckRequirement {

	Context context;
	SSHConnect ssh;
	String envId;
	String sshTitle;
	Log log;
	boolean finalPass = true;
	String host, port, user, pwd;

	public CheckRequirement(Context context, String envId) {
		this.context = context;
		this.envId = envId;

		this.log = new Log(CommonUtils.concatFile(context.getCurrentLogDir(), "check_" + envId + ".log"), true, context.isContinueMode());
		this.finalPass = true;

		this.host = context.getInstanceProperty(envId, "ssh.host");
		this.port = context.getInstanceProperty(envId, "ssh.port");
		this.user = context.getInstanceProperty(envId, "ssh.user");
		this.pwd = context.getInstanceProperty(envId, "ssh.pwd");

		this.sshTitle = user + "@" + host + ":" + port;
	}

	public boolean check() throws Exception {

		log.println("=================== Check " + sshTitle + "============================");

		checkSSH();

		checkVariable("JAVA_HOME");
		checkVariable("CTP_HOME");
		checkVariable("CUBRID");

		checkCommand("java");
		checkCommand("diff");
		checkCommand("wget");
		checkCommand("find");
		checkCommand("cat");

		checkDirectory(context.getTestCaseRoot());
		checkDirectory("${CTP_HOME}/isolation/ctltool");

		if (context.shouldUpdateTestCase()) {
			checkCommand("git");
		}

		if (context.enableCheckDiskSpace()) {
			checkDiskSpace();
		}

		if (ssh != null) {
			ssh.close();
		}

		if (!finalPass) {
			log.println("Log: " + log.getFileName());
		}
		log.println("");

		return finalPass;
	}

	private void checkSSH() throws Exception {
		try {
			this.log.print("==> Check ssh connection ");
			this.ssh = new SSHConnect(host, port, user, pwd);
			log.print("...... PASS");
		} catch (Exception e) {
			log.print("...... FAIL: " + e.getMessage());
			setFail();
			throw e;
		}
		log.println("");
	}

	private void checkVariable(String var) {
		this.log.print("==> Check variable '" + var + "' ");

		GeneralShellInput script;
		String result;
		try {
			script = new GeneralShellInput("echo $" + var.trim());
			result = ssh.execute(script);
			if (CommonUtils.isEmpty(result)) {
				log.print("...... FAIL. Please set " + var + ".");
				setFail();
			} else {
				log.print("...... PASS");
			}
		} catch (Exception e) {
			log.print("...... FAIL: " + e.getMessage());
			setFail();
		}
		log.println("");
	}

	private void checkDiskSpace() {
		this.log.println("==> Check disk space ");
		this.log.println("If insufficient available disk space (<2G), you will receive a mail in '" + context.getMailNoticeTo() + "'. And checking will hang till you resovle it.");

		GeneralShellInput scripts = new GeneralShellInput();
		scripts.addCommand("source ${CTP_HOME}/common/script/util_common.sh");
		scripts.addCommand("check_disk_space `df -P $HOME | grep -v Filesystem | awk '{print $1}'` 2G " + context.getMailNoticeTo());
		String result;
		try {
			result = ssh.execute(scripts);
			log.println(result);
			log.println("...... PASS");
			
		} catch (Exception e) {
			log.println("...... FAIL: " + e.getMessage());
			setFail();
		}
	}

	private void checkCommand(String cmd) {
		this.log.print("==> Check command '" + cmd + "' ");

		GeneralShellInput script;
		String result;
		try {
			script = new GeneralShellInput("which " + cmd + " 2>&1 ");
			result = ssh.execute(script);
			if (result.indexOf("no " + cmd) == -1) {
				log.print("...... PASS");
			} else {
				log.print("...... Result: FAIL. Not found executable " + cmd);
				setFail();
			}
		} catch (Exception e) {
			log.print("...... FAIL: " + e.getMessage());
			setFail();
		}
		log.println("");
	}

	private void checkDirectory(String dir) {
		this.log.print("==> Check directory '" + dir + "' ");

		GeneralShellInput script;
		String result;
		try {
			script = new GeneralShellInput("if [ -d \"" + dir + "\" ]; then echo PASS; else echo FAIL; fi");
			result = ssh.execute(script);
			if (result.indexOf("PASS") == -1) {
				log.print("...... PASS");
			} else {
				log.print("...... FAIL. Not found");
				setFail();
			}
		} catch (Exception e) {
			log.print("...... FAIL: " + e.getMessage());
			setFail();
		}

		log.println("");
	}

	private void setFail() {
		finalPass = false;
	}
}
