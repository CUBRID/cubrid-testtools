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
package com.navercorp.cubridqa.cqt.common;

import java.util.ArrayList;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.PosixParser;

import com.navercorp.cubridqa.cqt.common.CommonUtils;

public class RunRemoteScript {

	/**
	 * @param args
	 * @throws Exception
	 */
	public static void main(String[] args) throws Exception {

	}

	public static boolean runRemoteScript(String... params) {
		boolean ret = false;
		Options options = new Options();
		options.addOption("host", true, "Remote ssh host");
		options.addOption("port", true, "Remote ssh port");
		options.addOption("user", true, "Remote ssh user");
		options.addOption("password", true, "Remote ssh password");
		options.addOption("initfile", true, "file which contains common functions");
		options.addOption("c", true, "Script commands to run");
		options.addOption("f", true, "Script file to run");
		options.addOption("tillcontains", true, "execute repeatedly till result contains some string");
		options.addOption("tillequals", true, "execute repeatedly till result equals some string");
		options.addOption("maxattempts", true, "results equal some string. default: 200");
		options.addOption("interval", true, "second(s) that refresh interval. default: 1");
		options.addOption("help", false, "List help");

		CommandLineParser parser = null;
		CommandLine cmd = null;

		try {
			parser = new PosixParser();
			cmd = parser.parse(options, params);
		} catch (Exception e) {
			showHelp(e.getMessage(), options);
			return false;
		}

		if (params.length == 0 || cmd.hasOption("help")) {
			showHelp(null, options);
			return false;
		}

		if (!cmd.hasOption("host") || !cmd.hasOption("user") || !cmd.hasOption("password")) {
			showHelp("Please input remote <host>, <port>, <user>, <password>.", options);
			return false;
		}

		if (!cmd.hasOption("f") && !cmd.hasOption("c")) {
			showHelp("Please input scripts to run.", options);
			return false;
		}

		String sshHost = cmd.getOptionValue("host");
		String sshPort = cmd.getOptionValue("port");
		if (sshPort == null || sshPort.trim().equals(""))
			sshPort = "22";

		String sshUser = cmd.getOptionValue("user");
		String sshPassword = cmd.getOptionValue("password");

		String scriptCmd = cmd.getOptionValue("c");
		String scriptFile = null;
		if (cmd.hasOption("f")) {
			scriptFile = cmd.getOptionValue("f");
		}

		String initFile = null;
		if (cmd.hasOption("initfile")) {
			initFile = cmd.getOptionValue("initfile");
		}

		int maxattempts = 1;
		String tillcontains = null;
		if (cmd.hasOption("tillcontains")) {
			tillcontains = cmd.getOptionValue("tillcontains");
			if (tillcontains != null) {
				tillcontains = tillcontains.trim();
				if (tillcontains.equals("")) {
					tillcontains = null;
				} else {
					maxattempts = 200;
				}
			}
		}

		String tillequals = null;
		if (cmd.hasOption("tillequals")) {
			tillequals = cmd.getOptionValue("tillequals");
			if (tillequals != null) {
				tillequals = tillequals.trim();
				if (tillequals.equals("")) {
					tillequals = null;
				} else {
					maxattempts = 200;
				}
			}
		}

		if (cmd.hasOption("maxattempts")) {
			String s = cmd.getOptionValue("maxattempts");
			maxattempts = Integer.parseInt(s);
		}

		if (tillcontains == null && tillequals == null) {
			maxattempts = 1;
		}

		int interval = 1;
		if (cmd.hasOption("interval")) {
			String s = cmd.getOptionValue("interval");
			interval = Integer.parseInt(s);
		}

		SSHConnect ssh = null;
		ShellInput input = null;
		ArrayList<String> list;
		String result = null;
		try {
			ssh = new SSHConnect(sshHost, Integer.parseInt(sshPort), sshUser, sshPassword);
			input = new ShellInput("");

			if (initFile != null && initFile.trim().equals("") == false) {
				list = CommonUtils.getLineList(initFile);
				if (list != null) {
					for (String s1 : list) {
						input.addCommand(s1);
					}
				}
			}

			if (scriptCmd != null) {
				input.addCommand(CommonUtils.replace(scriptCmd, "\\n", "\n"));
			}

			list = CommonUtils.getLineList(scriptFile);
			if (list != null) {
				for (String s1 : list) {
					input.addCommand(s1);
				}
			}

			for (int i = 0; i < maxattempts; i++) {
				result = ssh.execute(input);
				result = result.trim();
				if (tillcontains != null) {
					if (result.indexOf(tillcontains) != -1) {
						break;
					} else {
						Thread.sleep(interval * 1000);
						continue;
					}
				} else if (tillequals != null) {
					if (result.equals(tillequals)) {
						break;
					} else {
						Thread.sleep(interval * 1000);
						continue;
					}
				}
			}
			System.out.println(result);
			ret = true;
		} catch (Exception e) {
			System.out.println("ERROR:" + e.getMessage());
		} finally {
			if (ssh != null)
				ssh.close();
		}

		return ret;
	}

	private static void showHelp(String error, Options options) {
		if (error != null)
			System.out.println("Error: " + error);
		HelpFormatter formatter = new HelpFormatter();
		formatter.printHelp("run_remote_script: to execute scripts on remote host", options);
		System.out.println();
	}
}
