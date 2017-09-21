/**
 * Copyright (c) 2016, Search Solution Corporation. All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without 
 * modification, are permitted provided that the following conditions are met:
 * 
 *   * Redistributions of source code must retain the above copyright notice, 
 *     this list of conditions and the following disclaimer.
 * 
 *   * Redistributions in binary form must reproduce the above copyright 
 *     notice, this list of conditions and the following disclaimer in 
 *     the documentation and/or other materials provided with the distribution.
 * 
 *   * Neither the name of the copyright holder nor the names of its contributors may be used to endorse or promote products 
 *     derived from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, 
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE 
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, 
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR 
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE 
 * USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE. 
 */
package com.navercorp.cubridqa.shell.main;

import com.navercorp.cubridqa.common.CommonUtils;
import com.navercorp.cubridqa.common.ConfigParameterConstants;
import com.navercorp.cubridqa.common.Log;
import com.navercorp.cubridqa.shell.common.SSHConnect;
import com.navercorp.cubridqa.shell.common.ShellScriptInput;

public class CheckRequirement {

	Context context;
	SSHConnect ssh;
	String envId;
	String sshTitle;
	Log log;
	boolean finalPass = true;
	String host;
	boolean isRelated;

	public CheckRequirement(Context context, String envId, String host, boolean isRelated) {
		this.context = context;
		this.envId = envId;
		this.isRelated = isRelated;
		this.host = host;

		this.sshTitle = ShellHelper.getTestNodeTitle(context, envId, host);

		this.log = new Log(CommonUtils.concatFile(context.getCurrentLogDir(), "check_" + envId + ".log"), true, context.isContinueMode());
		this.finalPass = true;
	}

	public boolean check() throws Exception {

		log.println("=================== Check " + sshTitle + "============================");

		checkSSH();

		checkVariable("HOME");
		checkVariable("USER");
		checkVariable("JAVA_HOME");
		checkVariable("CTP_HOME");
		checkVariable("init_path");
		checkVariable("CUBRID");

		checkCommand("java");
		checkCommand("javac");
		checkCommand("diff");
		checkCommand("wget");
		checkCommand("find");
		checkCommand("cat");
		checkCommand("kill");
		checkCommand("dos2unix");
		checkCommand("tar");
		if (context.needCleanTestCase()) {
			checkCommand("git");
		}

		checkDirectory("${CTP_HOME}/bin");
		checkDirectory("${CTP_HOME}/common/script");

		if (!isRelated) {
			checkDirectory(context.getTestCaseRoot());
		}

		if (context.isWindows()) {
			checkCommand("unzip");
		}

		String excludedFilename = context.getProperty(ConfigParameterConstants.TESTCASE_EXCLUDE_FROM_FILE);
		if (!CommonUtils.isEmpty(excludedFilename) && isRelated == false) {
			checkFile(excludedFilename);
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
			this.log.print("==> Check connection(" + context.getServiceProtocolType() + ") ");
			this.ssh = ShellHelper.createTestNodeConnect(context, envId, host);
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

		ShellScriptInput script;
		String result;
		try {
			script = new ShellScriptInput("echo $" + var.trim());
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
		this.log.println("If insufficient available disk space (<" + context.getReserveDiskSpaceSize() + "), you will receive a mail in '" + context.getMailNoticeTo()
				+ "'. And checking will hang till you resovle it.");

		ShellScriptInput scripts = new ShellScriptInput();
		scripts.addCommand("source ${CTP_HOME}/common/script/util_common.sh");
		scripts.addCommand("check_disk_space `df -P $HOME | grep -v Filesystem | awk '{print $1}'` " + context.getReserveDiskSpaceSize() + " \"" + context.getMailNoticeTo() + "\"" + " \""
				+ context.getMailNoticeCC() + "\"");
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

		ShellScriptInput script;
		String result;
		try {
			script = new ShellScriptInput("which " + cmd + " 2>&1 ");
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

		ShellScriptInput script;
		String result;
		try {
			script = new ShellScriptInput("if [ -d \"" + dir + "\" ]; then echo PASS; else echo FAIL; fi");
			result = ssh.execute(script);
			if (result.indexOf("PASS") != -1) {
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

	private void checkFile(String file) {
		this.log.print("==> Check file '" + file + "' ");

		ShellScriptInput script;
		String result;
		try {
			script = new ShellScriptInput("if [ -f \"" + file + "\" ]; then echo PASS; else echo FAIL; fi");
			result = ssh.execute(script);
			if (result.indexOf("PASS") != -1) {
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
