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

import java.io.File;

import com.navercorp.cubridqa.cqt.console.Executor;

public class CommandUtil extends Thread {
	private Object parameter;
	private Executor listener;
	int runmethod = 0;

	public CommandUtil() {
		super.setName("CommandUtil");
	}

	public CommandUtil(String[] commands, Executor listener) {
		this();
		runmethod = 0;
		this.parameter = commands;
		this.listener = listener;
	}

	public CommandUtil(String commands, Executor listener) {
		super.setName("CommandUtil");
		runmethod = 1;
		this.parameter = commands;
		this.listener = listener;

	}

	@Override
	public void run() {
		if (runmethod == 0)
			execute((String[]) parameter, listener);
		else if (runmethod == 1)
			execute((String) parameter, listener);
	}

	/**
	 * 
	 * @Title: execute
	 * @Description:Execute command.On Windows system write all commands into a
	 *                      ".bat" file first,then execute this ".bat" file.On
	 *                      Linux system write all commands into a ".sh" file
	 *                      and use "sh" commands execute this file.
	 * @param @param commands
	 * @param @param listener:Is used to output message.
	 * @param @return
	 * @return String
	 * @throws
	 */
	public static String execute(String[] commands, Executor listener) {
		if (commands == null) {
			return null;
		}

		String os = SystemUtil.getOS();
		String firstCommand = StringUtil.nullToEmpty(commands[0]).trim();

		if (listener != null) {
			String[] envs = listener.getEnvs();
			if (envs != null) {
				String[] temp = commands;
				commands = new String[envs.length + 1 + commands.length];
				System.arraycopy(envs, 0, commands, 0, envs.length);
				System.arraycopy(temp, 0, commands, envs.length + 1, temp.length);
			}
		} else {
			String[] temp = commands;
			commands = new String[1 + commands.length];
			System.arraycopy(temp, 0, commands, 1, temp.length);
		}

		String ret = "";
		String id = System.currentTimeMillis() + "" + ((long) (Math.random() * 100000));
		String tempFile = "run" + id + ".sh";

		String cmd = "sh";
		if (os.startsWith("window")) {
			tempFile = "runBat" + id + ".bat";
			cmd = "";
		}
		tempFile = SystemUtil.getUserHomePath() + "/" + tempFile;

		try {
			StringBuilder sb = new StringBuilder();
			if (!os.startsWith("window")) {
				sb.append("#!/bin/sh" + System.getProperty("line.separator"));
			}

			for (int i = 0; i < commands.length; i++) {
				String command = commands[i];
				if (command == null || command.trim().equals("") || command.indexOf("(") != -1 || command.indexOf("LS_COLORS") != -1) {
					continue;
				}
				if ("".equals(firstCommand)) {
					firstCommand = command;
				}
				sb.append(command + System.getProperty("line.separator"));
			}

			FileUtil.writeToFile(tempFile, sb.toString());

			if (listener != null) {
				listener.setStartTime(System.currentTimeMillis());
			}
			CommandExecutor shell = new CommandExecutor(cmd + " " + tempFile, firstCommand, listener);
			ret = shell.execute();
			if (listener != null) {
				listener.setEndTime(System.currentTimeMillis());
			}

		} catch (Exception e) {
			e.printStackTrace();
			if (listener != null) {
				listener.onMessage("Execute Error");
			}
		} finally {
			File f = new File(tempFile);
			f.delete();
		}
		return ret;
	}

	/**
	 * 
	 * @Title: getExecuteFile
	 * @Description:Execute command.On Windows system write all commands into a
	 *                      ".bat" file first,then execute this ".bat" file.On
	 *                      Linux system write all commands into a ".sh" file
	 *                      and use "sh" commands execute this file.
	 * @param @param commands:
	 * @param @param listener
	 * @param @return
	 * @return String
	 * @throws
	 */
	public static String getExecuteFile(String[] commands, Executor listener) {
		if (commands == null) {
			return null;
		}

		String os = SystemUtil.getOS();
		String firstCommand = StringUtil.nullToEmpty(commands[0]).trim();

		if (listener != null) {
			String[] envs = listener.getEnvs();
			if (envs != null) {
				String[] temp = commands;
				commands = new String[envs.length + 1 + commands.length];
				System.arraycopy(envs, 0, commands, 0, envs.length);
				System.arraycopy(temp, 0, commands, envs.length + 1, temp.length);
			}
		} else {
			String[] temp = commands;
			commands = new String[1 + commands.length];
			System.arraycopy(temp, 0, commands, 1, temp.length);
		}

		String ret = "";
		String tempFile = "";
		try {
			StringBuilder sb = new StringBuilder();
			if (!os.startsWith("window")) {
				sb.append("#!/bin/sh" + System.getProperty("line.separator"));
			}

			for (int i = 0; i < commands.length; i++) {
				String command = commands[i];
				if (command == null || command.trim().equals("") || command.indexOf("(") != -1 || command.indexOf("LS_COLORS") != -1) {
					continue;
				}
				if ("".equals(firstCommand)) {
					firstCommand = command;
				}
				sb.append(command + System.getProperty("line.separator"));
			}

			/*
			 * String cmd = "sh"; String id = System.currentTimeMillis() + "" +
			 * ((long) (Math.random() * 100000)); tempFile = "run" + id + ".sh";
			 * if (os.startsWith("window")) { tempFile = "runBat" + id + ".bat";
			 * cmd = ""; } tempFile = SystemUtil.getUserHomePath() + "/" +
			 * tempFile; FileUtil.writeToFile(tempFile, sb.toString());
			 */
		} catch (Exception e) {
			e.printStackTrace();
			if (listener != null) {
				listener.onMessage("Execute Error");
			}
		}
		return tempFile;
	}

	/**
	 * 
	 * @Title: execute
	 * @Description:Is used to execute commands.
	 * @param @param command
	 * @param @param listener
	 * @param @return
	 * @return String
	 * @throws
	 */
	public static String execute(String command, Executor listener) {
		if (command == null) {
			return null;
		}

		String[] commands = command.split(System.getProperty("line.separator"));
		return execute(commands, listener);
	}

	/**
	 * 
	 * @Title: svnSubmitFile
	 * @Description:Commit scenarios to SVN server.
	 * @param @param file
	 * @param @param listener
	 * @return void
	 * @throws
	 */
	public static void svnSubmitFile(String file, Executor listener) {
		if (file == null) {
			return;
		}

		CommandUtil.execute("svn add " + file, listener);
		CommandUtil.execute("svn commit -m \"\" " + file + " --non-interactive --username " + PropertiesUtil.getValue("svnuser") + " --password " + PropertiesUtil.getValue("svnpassword"), listener);
		return;
	}

	/**
	 * 
	 * @Title: svnUpdate
	 * @Description:Update scenarios from SVN server.
	 * @param @param file
	 * @param @param listener
	 * @return void
	 * @throws
	 */
	public static void svnUpdate(String file, Executor listener) {
		if (file == null) {
			return;
		}

		CommandUtil.execute("svn update -r HEAD " + file + " --non-interactive --username " + PropertiesUtil.getValue("svnuser") + " --password " + PropertiesUtil.getValue("svnpassword"), listener);
	}

}
