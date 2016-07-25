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
package com.navercorp.cubridqa.common;

import java.io.File;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.PosixParser;

public class SFTPUpload {

	public static void main(String[] args) throws Exception {
		Options options = new Options();
		options.addOption("host", true, "Remote ssh host");
		options.addOption("port", true, "Remote ssh port");
		options.addOption("user", true, "Remote ssh user");
		options.addOption("password", true, "Remote ssh password");
		options.addOption("from", true, "Files to upload");
		options.addOption("to", true, "Remote directory");
		options.addOption("help", false, "List help");

		CommandLineParser parser = null;
		CommandLine cmd = null;

		try {
			parser = new PosixParser();
			cmd = parser.parse(options, args);
		} catch (Exception e) {
			showHelp(e.getMessage(), options);
			return;
		}

		if (args.length == 0 || cmd.hasOption("help")) {
			showHelp(null, options);
			return;
		}

		if (!cmd.hasOption("host") || !cmd.hasOption("user") || !cmd.hasOption("password")) {
			showHelp("Please input remote <host>, <port>, <user>, <password>.", options);
			return;
		}

		if (!cmd.hasOption("from")) {
			showHelp("Please give files to upload", options);
			return;
		}

		String sshHost = cmd.getOptionValue("host");
		String sshPort = cmd.getOptionValue("port");
		if (sshPort == null || sshPort.trim().equals(""))
			sshPort = "22";

		String sshUser = cmd.getOptionValue("user");
		String sshPassword = cmd.getOptionValue("password");

		String from = cmd.getOptionValue("from");
		String to = cmd.hasOption("to") ? cmd.getOptionValue("to") : ".";

		boolean needLog = Boolean.parseBoolean(System.getProperty("main.need.debug.info", "false"));

		SSHConnect ssh = null;

		File fromFile = new File(CommonUtils.getFixedPath(from));
		boolean isFolder = fromFile.exists() && fromFile.isDirectory();
		SFTP sftp = null;

		try {
			System.out.println("[INFO] START TO UPDATE: " + from);
			ssh = new SSHConnect(sshHost, Integer.parseInt(sshPort), sshUser, sshPassword, needLog);
			sftp = ssh.createSFTP();

			if (isFolder == false) {
				sftp.upload(fromFile.getCanonicalPath(), to);
				System.out.println("[INFO] upload done");
			} else {
				String pkgName = null;
				StringBuffer scriptLocal;
				ShellInput scriptRemote;
				int shellType = CommonUtils.getShellType(false);

				pkgName = ".UP_" + fromFile.getName().trim() + "_" + System.currentTimeMillis() + ".tar.gz";

				scriptLocal = new StringBuffer();
				scriptLocal.append("cd " + from).append(";");
				scriptLocal.append("tar czvf " + "../" + pkgName + " .").append(";");
				LocalInvoker.exec(scriptLocal.toString(), shellType, true);
				System.out.println("[INFO] package done in local: " + pkgName);

				scriptRemote = new ShellInput();
				scriptRemote.addCommand("mkdir -p " + to);
				ssh.execute(scriptRemote);
				System.out.println("[INFO] create dest name done in remote: " + to);

				sftp.upload(fromFile.getParentFile().getCanonicalPath() + "/" + pkgName, to);
				System.out.println("[INFO] upload done");

				scriptRemote = new ShellInput();
				scriptRemote.addCommand("cd " + to);
				scriptRemote.addCommand("tar xzvf " + pkgName);
				scriptRemote.addCommand("rm -rf " + pkgName);
				ssh.execute(scriptRemote);
				System.out.println("[INFO] expand package done");

				scriptLocal = new StringBuffer();
				scriptLocal.append("cd " + from).append(";");
				scriptLocal.append("rm -rf ../" + pkgName).append(";");
				LocalInvoker.exec(scriptLocal.toString(), shellType, true);
				System.out.println("[INFO] clean temporary files in local and remote");
			}

			System.out.println("DONE");
		} finally {
			if (ssh != null)
				ssh.close();
		}
	}

	private static void showHelp(String error, Options options) {
		if (error != null)
			System.out.println("Error: " + error);
		HelpFormatter formatter = new HelpFormatter();
		formatter.printHelp("run_upload: upload files to remote host by SFTP protocol", options);
		System.out.println();
	}
}
