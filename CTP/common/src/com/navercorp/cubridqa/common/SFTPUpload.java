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
import java.io.IOException;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.PosixParser;

public class SFTPUpload {

	private static boolean enableDebug = CommonUtils.convertBoolean(System.getenv(ConfigParameterConstants.CTP_DEBUG_ENABLE), false);
	private static String TOKEN = ".CTPPROXY";

	public static void main(String[] args) throws Exception {
		Options options = new Options();
		options.addOption("host", true, "Remote ssh host");
		options.addOption("port", true, "Remote ssh port");
		options.addOption("user", true, "Remote ssh user");
		options.addOption("password", true, "Remote ssh password");

		options.addOption(null, "proxy-host", true, "Proxy ssh host");
		options.addOption(null, "proxy-port", true, "Proxy ssh port");
		options.addOption(null, "proxy-user", true, "Proxy ssh user");
		options.addOption(null, "proxy-password", true, "Proxy ssh password");
		options.addOption(null, "proxy-first", false, "Proxy first");

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

		String proxyHost = cmd.getOptionValue("proxy-host");
		if (CommonUtils.isEmpty(proxyHost)) {
			proxyHost = System.getenv(ConfigParameterConstants.CTP_PROXY_HOST);
		}

		String proxyPort = cmd.getOptionValue("proxy-port");
		if (CommonUtils.isEmpty(proxyPort)) {
			proxyPort = System.getenv(ConfigParameterConstants.CTP_PROXY_PORT);
			if (CommonUtils.isEmpty(proxyPort)) {
				proxyPort = "22";
			}
		}
		String proxyUser = cmd.getOptionValue("proxy-user");
		if (CommonUtils.isEmpty(proxyUser)) {
			proxyUser = System.getenv(ConfigParameterConstants.CTP_PROXY_USER);
			if (CommonUtils.isEmpty(proxyUser)) {
				proxyUser = sshUser;
			}
		}

		String proxyPwd = cmd.getOptionValue("proxy-password");
		if (CommonUtils.isEmpty(proxyPwd)) {
			proxyPwd = System.getenv(ConfigParameterConstants.CTP_PROXY_PASSWORD);
			if (CommonUtils.isEmpty(proxyPwd)) {
				proxyPwd = sshPassword;
			}
		}

		boolean proxyPriority = cmd.hasOption("proxy-first") || CommonUtils.convertBoolean(System.getenv(ConfigParameterConstants.CTP_PROXY_PRIORITY), false);
		boolean hasProxy = proxyHost != null && proxyHost.trim().equals("") == false;

		String from = cmd.getOptionValue("from");
		String to = cmd.hasOption("to") ? cmd.getOptionValue("to") : ".";

		String pkgFrom = compressSources(from);

		boolean succ = false;
		if (hasProxy == false || proxyPriority == false) {
			try {
				upload(sshHost, sshPort, sshUser, sshPassword, pkgFrom, to, false);
				deleteLocalPkgTemp(pkgFrom);
				succ = true;
			} catch (Exception e) {
				succ = false;
			}
		}

		if (hasProxy && succ == false) {
			System.out.println("PROXY: local -> " + proxyUser + "@" + proxyHost + " -> " + sshUser + "@" + sshHost);
			upload(proxyHost, proxyPort, proxyUser, proxyPwd, pkgFrom, ".", true);
			deleteLocalPkgTemp(pkgFrom);

			String proxyCmd = "run_upload -host " + sshHost + " -port " + sshPort + " -user " + sshUser + " -password " + sshPassword + " -from " + pkgFrom + " -to " + to;
			proxyCmd += "; rm -rf " + pkgFrom + ";";
			String[] remoteArgs = new String[10];
			int i = 0;
			remoteArgs[i++] = "-host";
			remoteArgs[i++] = proxyHost;
			remoteArgs[i++] = "-port";
			remoteArgs[i++] = proxyPort;
			remoteArgs[i++] = "-user";
			remoteArgs[i++] = proxyUser;
			remoteArgs[i++] = "-password";
			remoteArgs[i++] = proxyPwd;
			remoteArgs[i++] = "-c";
			remoteArgs[i++] = proxyCmd;
			RunRemoteScript.skipProxy = true;
			RunRemoteScript.main(remoteArgs);
		}
	}

	private static String compressSources(String from) throws IOException {
		if (from.startsWith(TOKEN)) {
			return from;
		}

		File fromFile = new File(CommonUtils.getFixedPath(from));
		boolean isFolder = fromFile.isDirectory();
		String fn = fromFile.getName().trim();
		String pkgName = TOKEN + System.currentTimeMillis() + "_" + fn + "_" + (isFolder ? "D" : "F") + ".tar.gz";

		StringBuffer script = new StringBuffer();
		script.append("current_dir=`pwd`").append(";");
		if (isFolder) {
			script.append("cd " + from).append(";");
			script.append("tar czvf " + "${current_dir}/" + pkgName + " .").append(";");
		} else {
			String dir = getDir(from);
			if (CommonUtils.isEmpty(dir)) {
				dir = ".";
			}
			script.append("cd " + dir).append(";");
			script.append("tar czvf " + "${current_dir}/" + pkgName + " " + fn).append(";");
		}

		int shellType = CommonUtils.getShellType(false);
		System.out.println("==> Files to upload: ");
		LocalInvoker.exec(script.toString(), shellType, true);
		return pkgName;
	}

	private static void deleteLocalPkgTemp(String from) {
		if (!from.startsWith(TOKEN)) {
			return;
		}
		int shellType = CommonUtils.getShellType(false);
		LocalInvoker.exec("rm -rf " + from, shellType, true);
	}

	private static void upload(String sshHost, String sshPort, String sshUser, String sshPassword, String from, String to, boolean isProxy) throws IOException, Exception {
		SSHConnect ssh = null;
		SFTP sftp = null;

		String oriFn = from.substring(from.indexOf("_") + 1, from.lastIndexOf(".tar.gz") - 2);
		boolean isSourceDir = from.endsWith("D.tar.gz");
		String hello;

		try {
			ssh = new SSHConnect(sshHost, Integer.parseInt(sshPort), sshUser, sshPassword, enableDebug);
			hello = ssh.execute(new ShellInput("echo OOO${NOT_EXIST}KKK"));
			if (hello.trim().equals("OOOKKK")) {
				System.out.println("SSH CONNECTED + " + sshUser + "@" + sshHost);
			} else {
				throw new Exception("Fail to connect to host: " + sshHost);
			}

			System.out.println("[INFO] START TO UPLOAD: " + oriFn);

			sftp = ssh.createSFTP();
			if (isProxy) {
				sftp.upload(from, to);
			} else {
				boolean existDir = sftp.existDir(to);
				boolean existFile = sftp.existFile(to);
				ShellInput script = new ShellInput();
				if (existDir) {
					sftp.upload(from, to);

					script.addCommand("cd " + to);
					script.addCommand("tar xzvf " + from);
					script.addCommand("rm -rf " + from);
				} else if (existFile) {
					String dir = getDir(to);
					String fn = getFn(to);
					if (CommonUtils.isEmpty(dir)) {
						dir = ".";
					} else {
						dir = dir.trim();
					}
					if (fn != null) {
						fn = fn.trim();
					}

					sftp.upload(from, dir);

					if (isSourceDir) {
						script.addCommand("cd " + dir);
						if (CommonUtils.isEmpty(fn) == false && fn.equals(".") == false && fn.indexOf("..") == -1 && fn.equals("/") == false && fn.length() > 0) {
							script.addCommand("rm -rf ./" + fn);
							script.addCommand("mkdir " + fn);
							script.addCommand("cd " + fn);
							script.addCommand("tar xzvf ../" + from);
							script.addCommand("rm -rf ../" + from);
						}
					} else {
						script.addCommand("cd " + dir);
						script.addCommand("tar xzvf " + from);
						script.addCommand("mv " + fn + " " + oriFn);
						script.addCommand("rm -rf " + from);
					}

				} else {
					sftp.mkdirs(to);
					sftp.upload(from, ".");
					script.addCommand("cd " + to);
					script.addCommand("tar xzvf " + from);
					script.addCommand("rm -rf " + from);
				}
				ssh.execute(script);
			}

			System.out.println("[INFO] upload done");
		} finally {
			if (sftp != null) {
				sftp.close();
			}
			if (ssh != null)
				ssh.close();
		}
	}

	public static String getDir(String fn) {
		if (fn == null)
			return null;

		fn = CommonUtils.replace(fn, "\\", "/");

		int i = fn.lastIndexOf("/");
		if (i == -1) {
			return null;
		} else {
			return fn.substring(0, i);
		}
	}

	public static String getFn(String fn) {
		if (fn == null)
			return null;

		fn = CommonUtils.replace(fn, "\\", "/");

		int i = fn.lastIndexOf("/");
		if (i == -1) {
			return fn;
		} else {
			return fn.substring(i + 1);
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
