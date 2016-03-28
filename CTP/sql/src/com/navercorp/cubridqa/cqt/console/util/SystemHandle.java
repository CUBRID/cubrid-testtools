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
package com.navercorp.cubridqa.cqt.console.util;

import java.util.List;

import com.navercorp.cubridqa.cqt.console.ConsoleAgent;
import com.navercorp.cubridqa.cqt.console.util.StringUtil;
import com.navercorp.cubridqa.cqt.console.util.SystemConst;

public class SystemHandle {
	/**
	 * system exit
	 */
	public static void exit() {
		System.out.println("System will exit!");
		System.exit(0);
	}

	/**
	 * get user current dir
	 * 
	 * @return
	 */
	public static String getUserDir() {
		return System.getProperty("user.dir");
	}

	/**
	 * execute use .
	 * 
	 * @param filename
	 * @return
	 */
	public static int executePointShell(String filename) {
		String[] cmd = new String[3];
		cmd[0] = "/bin/bash";
		cmd[1] = "-c";
		cmd[2] = ".  " + filename;
		return ShellFileMaker.executeShell(cmd);
	}

	public static int executeShShell(String filename) {
		String[] cmd = new String[3];
		cmd[0] = "/bin/bash";
		cmd[1] = "-c";
		cmd[2] = "sh  " + filename;
		return ShellFileMaker.executeShell(cmd);
	}

	/**
	 * execute use .
	 * 
	 * @param filename
	 * @return
	 */
	public static int executeCmdShell(String comd) {
		String[] cmd = new String[3];
		cmd[0] = "/bin/bash";
		cmd[1] = "-c";
		cmd[2] = comd;
		return ShellFileMaker.executeShell(cmd);
	}

	/**
	 * execute use expect
	 * 
	 * @param filename
	 * @return
	 */
	public static int executeExpectShell(String filename) {
		String[] cmd = new String[3];
		cmd[0] = "/bin/bash";
		cmd[1] = "-c";
		cmd[2] = "expect  " + filename;
		return ShellFileMaker.executeShell(cmd);
	}

	/**
	 * execute in windows
	 * 
	 * @param filename
	 * @return
	 */
	public static int executeWCmdShell(String filename) {
		filename = StringUtil.replaceForCygwin(filename);
		
		String[] cmd = new String[3];
		cmd[0] = "cmd.exe";
		cmd[1] = "/C";
		cmd[2] = "expect  " + filename;
		return ShellFileMaker.executeShell(cmd);
	}

	/**
	 * execute in windows
	 * 
	 * @param commands
	 * @return
	 */
/*	public static int executeWCmd(String... commands) {
		String[] cmd = new String[commands.length + 2];
		cmd[0] = "cmd.exe";
		cmd[1] = "/C";
		for (int i = 0; i < commands.length; i++) {
			cmd[i + 2] = commands[i];
		}
		return ShellFileMaker.executeShell(cmd);
	}*/

	/**
	 * check the result of shell execute
	 * 
	 * @param result
	 * @throws Exception
	 */
	public static void checkExecuteResult(int result) throws Exception {
		switch (result) {
		case -1:
			throw new Exception("Error: System error! ");
		case 99:
			throw new Exception("Error: Access denied! ");
		case 98:
			throw new Exception("Error: Connection refused! ");
		case 97:
			throw new Exception("Error: DbName already existed!");
		case 96:
			throw new Exception("Error: DbName doesnot existed!");
		case 95:
			throw new Exception("Error: Database still running!");
		case 1:
			break;
		}
	}




	/**
	 * copy file
	 * 
	 * @param sourcepath
	 * @param targetpath
	 * @param cp
	 * @throws Exception
	 */
	public static void copyFile(String sourcepath, String targetpath, String cp)
			throws Exception {
		StringBuffer context = ShellFileMaker.makeExpectShellHead();
		if (cp == null) {
			ConsoleAgent.addMessage("Error:  Copy Shell is null !");
			throw new Exception("Error : Copy Shell is null !");
		}
		cp = cp.replace("$source", sourcepath);
		cp = cp.replace("$target", targetpath);
		context = context.append(cp + SystemConst.LINE_SEPERATOR);
		context = ShellFileMaker.makeExpectShellEnd(context);
		String shellname = ShellFileMaker.writer(context.toString(),
				SystemConst.QA_PATH + "/temp/temp.sh");
		SystemHandle.checkExecuteResult(SystemHandle
				.executeExpectShell(shellname));
		ConsoleAgent.addMessage("Done");
		ShellFileMaker.removeFile(shellname);
	}

	public static List getList() {
		return ShellFileMaker.getList();
	}
	
	public static void main(String[] args){
		String abc = "C:/temp/";
		String tmp = abc.substring(0,1);
		abc = "/cygdrive/"+tmp+abc.substring(2);
		System.out.println(abc);
	}
}
