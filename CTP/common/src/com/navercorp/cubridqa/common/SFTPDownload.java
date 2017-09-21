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

public class SFTPDownload {

	private static boolean enableDebug = CommonUtils.convertBoolean(System.getenv(ConfigParameterConstants.CTP_DEBUG_ENABLE), false);
	private static String TOKEN = ".CTPPROXY";

	public static void main(String[] args) throws Exception {
		Options options = new Options();
		options.addOption("host", true, "Remote ssh host");
		options.addOption("port", true, "Remote ssh port");
		options.addOption("user", true, "Remote ssh user");
		options.addOption("password", true, "Remote ssh password");
		options.addOption("from", true, "Files to download");
		options.addOption("to", true, "Local directory to save");

		options.addOption(null, "proxy-host", true, "Proxy ssh host");
		options.addOption(null, "proxy-port", true, "Proxy ssh port");
		options.addOption(null, "proxy-user", true, "Proxy ssh user");
		options.addOption(null, "proxy-password", true, "Proxy ssh password");
		options.addOption(null, "proxy-first", false, "Proxy first");

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
			showHelp("Please give files to download", options);
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

		String pkgName = to.startsWith(TOKEN) ? to : TOKEN + System.currentTimeMillis() + ".tar.gz";

		boolean succ = false;
		if (hasProxy == false || proxyPriority == false) {
			try {
				download(sshHost, sshPort, sshUser, sshPassword, from, pkgName, false);
				succ = true;
			} catch (Exception e) {
				succ = false;
			}
		}

		if (hasProxy && succ == false) {
			//System.out.println("PROXY: local -> " + proxyUser + "@" + proxyHost + " -> " + sshUser + "@" + sshHost);
			String proxyCmd = "run_download -host " + sshHost + " -port " + sshPort + " -user " + sshUser + " -password " + sshPassword + " -from " + from + " -to " + pkgName + " 2>&1";
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
			RunRemoteScript.run(remoteArgs, true);

			download(proxyHost, proxyPort, proxyUser, proxyPwd, from, pkgName, true);
		}

		expandSources(pkgName, to);
	}

	private static SSHConnect createSSH(String sshHost, String sshPort, String sshUser, String sshPassword) throws Exception {
		SSHConnect ssh = new SSHConnect(sshHost, Integer.parseInt(sshPort), sshUser, sshPassword, enableDebug);

		String result = ssh.execute(new ShellInput("echo OOO${NOT_EXIST}KKK"));
		if (result.trim().equals("OOOKKK")) {
			System.out.println("SSH CONNECTED  ==>  " + sshUser + "@" + sshHost);
		} else {
			throw new Exception("Fail to connect to host: " + sshHost);
		}
		return ssh;
	}

	private static void compressSources(SSHConnect ssh, String from, String pkgFrom) throws Exception {
		if (from.startsWith(TOKEN)) {
			return;
		}
		ShellInput script = null;

		script = new ShellInput();
		script.addCommand("if [ -d " + from + " ]; then");
		script.addCommand("		cd " + from);
		script.addCommand("     echo DIR > FILE_TYPE");
		script.addCommand("		tar czvf ~/" + pkgFrom + " .");
		script.addCommand("fi");
		script.addCommand("if [ -f " + from + " ]; then");
		String dir = SFTPUpload.getDir(from);
		if (CommonUtils.isEmpty(dir)) {
			dir = ".";
		}
		String fn = SFTPUpload.getFn(from);
		script.addCommand("		cd " + dir);
		script.addCommand("     echo FILE> FILE_TYPE");
		script.addCommand("		tar czvf ~/" + pkgFrom + " " + fn + " FILE_TYPE");
		script.addCommand("fi");

		String result = ssh.execute(script);
		System.out.println("==>Files to download: ");
		System.out.println(result);
	}

	private static void expandSources(String pkgFrom, String to) throws IOException {
		if (to != null && to.startsWith(TOKEN)) {
			return;
		}
		
		String pkgFromWithoutExt = pkgFrom.substring(0, pkgFrom.lastIndexOf(".tar.gz"));
		String dirForTo = SFTPUpload.getDir(to);
		if (CommonUtils.isEmpty(dirForTo)) {
			dirForTo = ".";
		}
		String fnForTo = SFTPUpload.getFn(to);
		
		StringBuilder script = new StringBuilder();
		script.append("current_dir=" + CommonUtils.getLinuxStylePath(new File(".").getCanonicalPath())).append("\n");
		script.append("cd ${current_dir}").append("\n");
		script.append("mkdir " + pkgFromWithoutExt).append("\n");
		script.append("cd " + pkgFromWithoutExt).append("\n");
		script.append("tar xzvf ../" + pkgFrom).append("\n");
		script.append("is_src_dir=`cat FILE_TYPE | grep DIR | wc -l`").append("\n");
		script.append("rm -rf FILE_TYPE").append("\n");
		script.append("cd ..").append("\n");
		
		script.append("if [ -d " + to + " ]; then").append("\n");
		script.append("    mv ${current_dir}/" + pkgFromWithoutExt + "/* " + to + " ").append("\n");
		script.append("elif [ -f " + to + " ]; then ").append("\n");
		script.append("    if [ ${is_src_dir} -eq 1 ]; then").append("\n");
		script.append("        cd " + dirForTo + "; rm -rf " + fnForTo + "; mkdir " + fnForTo).append("\n");
		script.append("        mv ${current_dir}/" + pkgFromWithoutExt + "/* " + fnForTo + "/").append("\n");		
		script.append("    else").append("\n");
		script.append("        mv ${current_dir}/" + pkgFromWithoutExt + "/* " + to).append("\n");
		script.append("    fi").append("\n");
		script.append("else").append("\n");
		script.append("    if [ ${is_src_dir} -eq 1 ]; then").append("\n");
		script.append("        mkdir -p " + to).append("\n");
		script.append("        mv ${current_dir}/" + pkgFromWithoutExt + "/* " + to + "/").append("\n");
		script.append("    else").append("\n");
		script.append("        mkdir -p " + dirForTo + " >/dev/null 2>&1").append("\n");
		script.append("        mv ${current_dir}/" + pkgFromWithoutExt + "/* " + to).append("\n");
		script.append("    fi").append("\n");
		script.append("fi").append("\n");		
		script.append("rm -rf ${current_dir}/" + pkgFromWithoutExt).append("\n");
		script.append("rm -rf ${current_dir}/" + pkgFrom).append("\n");
		if (enableDebug) {
			System.out.println(script.toString());
		}
		int shellType = CommonUtils.getShellType(false);
		LocalInvoker.exec(script.toString(), shellType, enableDebug);
	}

	private static void download(String sshHost, String sshPort, String sshUser, String sshPassword, String from, String pkgFrom, boolean isProxy) throws IOException, Exception {
		SSHConnect ssh = null;
		SFTP sftp = null;
		try {
			ssh = createSSH(sshHost, sshPort, sshUser, sshPassword);
			if (isProxy == false && from.startsWith(TOKEN) == false) {
				compressSources(ssh, from, pkgFrom);
			}
			sftp = ssh.createSFTP();
			sftp.download(pkgFrom, ".");
			System.out.println("Download " + from + " from " + sshUser + "@" + sshHost);
			ssh.execute(new ShellInput("rm -rf " + pkgFrom));
		} finally {
			if (ssh != null)
				ssh.close();
		}
	}

	private static void showHelp(String error, Options options) {
		if (error != null)
			System.out.println("Error: " + error);
		HelpFormatter formatter = new HelpFormatter();
		formatter.printHelp("run_download: download files from remote host by SFTP protocol", options);
		System.out.println();
	}
}
