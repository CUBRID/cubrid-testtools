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
package com.navercorp.cubridqa.ctp;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Date;
import java.util.List;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.PosixParser;

import com.navercorp.cubridqa.common.CommonUtils;
import com.navercorp.cubridqa.common.IniData;
import com.navercorp.cubridqa.common.LocalInvoker;

public class CTP {

	private final static String ctpHome = CommonUtils.getEnvInFile("CTP_HOME");

	private static Options OPTIONS = new Options();
	static {
		OPTIONS.addOption("c", "config", true, "provide a configuration file");
		OPTIONS.addOption(null, "interactive", false, "interactive mode to run single test case or cases in a folder");
		OPTIONS.addOption("h", "help", false, "show help");
		OPTIONS.addOption("v", "version", false, "show version");
	}

	/**
	 * @param args
	 * @throws Exception
	 */
	public static void main(String[] args) throws Exception {

		File ctpHomeFile = new File(ctpHome);
		if (ctpHomeFile.exists() == false) {
			showHelp("Not Found CTP_HOME value");
			return;
		}

		// System.out.println();
		// System.out.println("CTP_HOME: " + ctpHome);

		CommandLineParser parser = null;
		CommandLine cmd = null;

		try {
			parser = new PosixParser();
			cmd = parser.parse(OPTIONS, args);
		} catch (Exception e) {
			showHelp(e.getMessage());
			return;
		}

		if (cmd.hasOption("h")) {
			showHelp(null);
			return;
		}

		if (cmd.hasOption("v")) {
			showVersion();
			return;
		}

		if (cmd.getArgList().size() == 0) {
			showHelp("Not found any task to execute");
			return;
		}

		String configFilename = CommonUtils.getFixedPath(cmd.getOptionValue("c"));

		@SuppressWarnings("unchecked")
		List<String> taskList = cmd.getArgList();

		boolean isUtility = false;

		ComponentEnum component;

		try {
			component = ComponentEnum.valueOf(taskList.get(0).trim().toUpperCase());
		} catch (Exception e) {
			showHelp(e.getMessage());
			return;
		}

		switch (component) {
		case WEBCONSOLE:
			isUtility = true;
			executeWebConsole(taskList);
			break;
		}

		if (isUtility)
			return;

		String taskLabel;
		long elapseTime;
		Date startDate, endDate;
		boolean interactiveMode = cmd.hasOption("interactive");
		for (String task : taskList) {
			taskLabel = task.toUpperCase();
			startDate = new java.util.Date();
			System.out.println();
			System.out.println("====================================== " + taskLabel + " ==========================================");
			try {
				try {
					component = ComponentEnum.valueOf(task.trim().toUpperCase());
				} catch (Exception e) {
					showHelp(e.getMessage());
					continue;
				}

				System.out.println("[" + taskLabel + "] TEST STARTED (" + startDate + ")");
				System.out.println();
				switch (component) {
				case SQL:
					executeSQL(getConfigData(taskLabel, configFilename, "sql"), "sql", interactiveMode, false);
					break;
				case MEDIUM:
					executeSQL(getConfigData(taskLabel, configFilename, "medium"), "medium", interactiveMode, false);
					break;
				case KCC:
					executeSQL(getConfigData(taskLabel, configFilename, "kcc"), "kcc", interactiveMode, false);
					break;
				case NEIS05:
					executeSQL(getConfigData(taskLabel, configFilename, "neis05"), "neis05", interactiveMode, false);
					break;
				case NEIS08:
					executeSQL(getConfigData(taskLabel, configFilename, "neis08"), "neis08", interactiveMode, false);
					break;
				case SQL_BY_CCI:
					executeSQL(getConfigData(taskLabel, configFilename, "sql_by_cci"), "sql_by_cci", interactiveMode, true);
					break;
				case SHELL:
					executeShell(getConfigData(taskLabel, configFilename, "shell"), "shell");
					break;
				case ISOLATION:
					executeIsolation(getConfigData(taskLabel, configFilename, "isolation"), "isolation");
					break;
				case HA_REPL:
					executeHaRepl(getConfigData(taskLabel, configFilename, "ha_repl"), "ha_repl");
					break;
				case JDBC:
					executeJdbc(getConfigData(taskLabel, configFilename, "jdbc"), "jdbc");
					break;
				case UNITTEST:
					if (CommonUtils.isEmpty(configFilename)) {
						executeUnitTest(null, "unittest");
					} else {
						executeUnitTest(getConfigData(taskLabel, configFilename, "unittest"), "unittest");
					}
					break;
				}

				endDate = new java.util.Date();
				elapseTime = (long) ((endDate.getTime() - startDate.getTime()) / 1000.0);
				if (!interactiveMode) {
					System.out.println("[" + taskLabel + "] TEST END (" + endDate + ")");
					System.out.println("[" + taskLabel + "] ELAPSE TIME: " + elapseTime + " seconds");
				}
			} catch (Exception e) {
				e.printStackTrace();
				System.out.println("[" + taskLabel + "] ERROR: " + e.getMessage());
			}
		}
	}

	private static void executeSQL(IniData config, String suite, boolean interactiveMode, boolean useCCI) throws IOException {
		String configFilePath = CommonUtils.getLinuxStylePath(config.getFilename());
		boolean enableMemoryLeak = CommonUtils.convertBoolean(config.get("sql", "enable_memory_leak"));
		
		String runStmt = "sh ${CTP_HOME}/sql/bin/" + (enableMemoryLeak ? "run_memory.sh" : "run.sh") + " -s " + suite + " -f " + configFilePath;
		
		if(interactiveMode) {
			addContScript("export sql_interface_type=" + (useCCI ? "cci" : "jdbc"));
			addContScript("export sql_interactive=yes");
			addContScript(runStmt);
		} else {
			LocalInvoker.exec("export sql_interface_type=" + (useCCI ? "cci" : "jdbc") + "; " + runStmt, CommonUtils.getShellType(false), true);
		}		
	}
	
	private static void executeSQL_By_CCI(IniData config, String suite) throws IOException {
		String configFilePath = CommonUtils.getLinuxStylePath(config.getFilename());
		LocalInvoker.exec("sh ${CTP_HOME}/sql_by_cci/bin/run.sh" + " -s " + suite + " -f " + configFilePath, CommonUtils.getShellType(false), true);
	}

	private static void executeJdbc(IniData config, String suite) throws IOException {
		String configFilePath = CommonUtils.getLinuxStylePath(config.getFilename());
		LocalInvoker.exec("sh ${CTP_HOME}/jdbc/bin/run.sh " + configFilePath, CommonUtils.getShellType(false), true);
	}

	private static void executeShell(IniData config, String suite) {
		String jar = ctpHome + File.separator + "shell" + File.separator + "lib" + File.separator + "cubridqa-shell.jar";
		try {
			URL url = new URL("file:" + jar);
			URLClassLoader clzLoader = new URLClassLoader(new URL[] { url }, Thread.currentThread().getContextClassLoader());
			Class<?> clz = clzLoader.loadClass("com.navercorp.cubridqa.shell.main.Main");
			Method m = clz.getDeclaredMethod("exec", String.class);
			String configFilename;
			if (CommonUtils.isWindowsPlatform()) {
				configFilename = CommonUtils.getWindowsStylePath(config.getFilename());
			} else {
				configFilename = config.getFilename();
			}			
			m.invoke(clz, configFilename);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	private static void executeIsolation(IniData config, String suite) {
		String jar = ctpHome + File.separator + "isolation" + File.separator + "lib" + File.separator + "cubridqa-isolation.jar";
		try {
			URL url = new URL("file:" + jar);
			URLClassLoader clzLoader = new URLClassLoader(new URL[] { url }, Thread.currentThread().getContextClassLoader());
			Class<?> clz = clzLoader.loadClass("com.navercorp.cubridqa.isolation.Main");
			Method m = clz.getDeclaredMethod("exec", String.class);
			String configFilename;
			if (CommonUtils.isWindowsPlatform()) {
				configFilename = CommonUtils.getWindowsStylePath(config.getFilename());
			} else {
				configFilename = config.getFilename();
			}			
			m.invoke(clz, configFilename);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	private static void executeHaRepl(IniData config, String suite) {
		String jar = ctpHome + File.separator + "ha_repl" + File.separator + "lib" + File.separator + "cubridqa-ha_repl.jar";
		try {
			URL url = new URL("file:" + jar);
			URLClassLoader clzLoader = new URLClassLoader(new URL[] { url }, Thread.currentThread().getContextClassLoader());
			Class<?> clz = clzLoader.loadClass("com.navercorp.cubridqa.ha_repl.Main");
			Method m = clz.getDeclaredMethod("exec", String.class);
			String configFilename;
			if (CommonUtils.isWindowsPlatform()) {
				configFilename = CommonUtils.getWindowsStylePath(config.getFilename());
			} else {
				configFilename = config.getFilename();
			}			
			m.invoke(clz, configFilename);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	private static void executeUnitTest(IniData config, String suite) {
		String jar = ctpHome + File.separator + "shell" + File.separator + "lib" + File.separator + "cubridqa-shell.jar";
		System.setProperty("TEST_TYPE", "unittest");
		System.setProperty("TEST_CATEGORY", "unittest");
		try {
			URL url = new URL("file:" + jar);
			URLClassLoader clzLoader = new URLClassLoader(new URL[] { url }, Thread.currentThread().getContextClassLoader());
			Class<?> clz = clzLoader.loadClass("com.navercorp.cubridqa.shell.main.GeneralLocalTest");
			Method m = clz.getDeclaredMethod("exec", String.class);

			if (config == null) {
				m.invoke(clz, (String) null);
			} else {
				String configFilename;
				if (CommonUtils.isWindowsPlatform()) {
					configFilename = CommonUtils.getWindowsStylePath(config.getFilename());
				} else {
					configFilename = config.getFilename();
				}
				m.invoke(clz, configFilename);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private static IniData getConfigData(String taskLable, String configFilename, String suite) throws Exception {
		File configFile;
		if (configFilename == null) {
			configFile = new File(CommonUtils.concatFile(CommonUtils.concatFile(ctpHome, "conf"), suite + ".conf"));
		} else {
			configFile = new File(configFilename);
		}

		System.out.println("[" + taskLable + "] CONFIG FILE: " + configFile.getCanonicalPath());
		if (configFile.exists() == false) {
			throw new Exception("Not found configuration file");
		}

		IniData config = null;
		try {
			config = new IniData(configFile);
			System.out.println(config);
			System.out.println("----------END OF FILE----------");
		} catch (Exception e) {
			throw new Exception("Fail to read configuration file. Please check whether it exists or not.");
		}
		return config;
	}

	private static void showHelp(String error) {
		if (error != null) {
			System.out.println("Error: " + error);
			System.out.println();
		} else {
			System.out.println("Welcome to use CUBRID Test Program (CTP)");
		}
		HelpFormatter formatter = new HelpFormatter();
		formatter.printHelp("ctp.sh <sql|medium> -c <config_file>", OPTIONS);
		System.out.println();
		System.out.println("utility: ctp.sh webconsole <start|stop>");
		System.out.println();
		System.out.println("For example: ");
		System.out.println("	ctp.sh sql -c conf/sql.conf");
		System.out.println("	ctp.sh medium -c conf/medium.conf");
		System.out.println("	ctp.sh sql		#use default configuration file: " + ctpHome + File.separator + "conf" + File.separator + "sql.conf");
		System.out.println("	ctp.sh medium		#use default configuration file: " + ctpHome + File.separator + "conf" + File.separator + "medium.conf");
		System.out.println("	ctp.sh sql medium  	#run both sql and medium with default configuration");
		System.out.println("	ctp.sh medium medium 	#execute medium twice");
		System.out.println("	ctp.sh webconsole start	#start web console to view sql test results");

		System.out.println();
	}

	private static void executeWebConsole(List<String> cmds) {
		if (cmds.size() <= 1) {
			System.out.println("Usage: ctp.sh webconsole <start|stop>");
			System.out.println();
			return;
		}
		String jar = ctpHome + File.separator + "sql" + File.separator + "lib" + File.separator + "cubridqa-cqt.jar";
		try {
			URL url = new URL("file:" + jar);
			URLClassLoader clzLoader = new URLClassLoader(new URL[] { url }, Thread.currentThread().getContextClassLoader());
			Class<?> clz = clzLoader.loadClass("com.navercorp.cubridqa.cqt.webconsole.Starter");
			Method m = clz.getDeclaredMethod("exec", String.class, String.class, String.class);
			String webRoot = ctpHome + File.separator + "sql" + File.separator + "webconsole";
			String webconsoleConf = ctpHome + File.separator + "conf" + File.separator + "webconsole.conf";
			m.invoke(clz, webconsoleConf, webRoot, cmds.get(1));
		} catch (Exception e) {
			e.printStackTrace();
		}
	}



	private static void showVersion() {
		System.out.println("CUBRID Test Program (CTP) " + Version.getVersion());
	}
	
	private static void addContScript(String stmt) {
		if (stmt == null || stmt.trim().equals("")) {
			return;
		}
		System.out.print(stmt.trim() + "    #SCRIPTCONT\n");
	}
}
