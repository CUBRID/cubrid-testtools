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

package com.navercorp.cubridqa.isolation;

import com.navercorp.cubridqa.common.CommonUtils;
import com.navercorp.cubridqa.common.Log;
import com.navercorp.cubridqa.shell.common.GeneralScriptInput;
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

		GeneralScriptInput script;
		String result;
		try {
			script = new GeneralScriptInput("echo $" + var.trim());
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

		GeneralScriptInput scripts = new GeneralScriptInput();
		scripts.addCommand("source ${CTP_HOME}/common/script/util_common.sh");
		scripts.addCommand("check_disk_space `df -P $HOME | grep -v Filesystem | awk '{print $1}'` 2G \"" + context.getMailNoticeTo() + "\"" + " \"" + context.getMailNoticeCC() + "\"");
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

		GeneralScriptInput script;
		String result;
		try {
			script = new GeneralScriptInput("which " + cmd + " 2>&1 ");
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

		GeneralScriptInput script;
		String result;
		try {
			script = new GeneralScriptInput("if [ -d \"" + dir + "\" ]; then echo PASS; else echo FAIL; fi");
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
