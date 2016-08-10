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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.PosixParser;

import com.navercorp.cubridqa.common.CommonUtils;
import com.navercorp.cubridqa.common.IniData;
import com.navercorp.cubridqa.common.IniData.Section;

public class IniCommand {

	/**
	 * @param args
	 * @throws Exception
	 */
	public static void main(String[] args) throws Exception {

		Options options = new Options();
		options.addOption("s", "section", true, "section name in ini file");
		options.addOption("u", "update", true, "store multiple values into ini file");
		options.addOption(null, "clear-first", false, "before update, clear all in section scope");
		options.addOption(null, "update-from-file", true, "update from file");
		options.addOption("d", "delete", true, "delete keys");
		options.addOption(null, "separator", true, "separator, default is to return");
		options.addOption(null, "linux-path", false, "means the value is a path and be convert to cygwin-style");

		options.addOption("h", "help", false, "show help");

		CommandLineParser parser = null;
		CommandLine cmd = null;

		try {
			parser = new PosixParser();
			cmd = parser.parse(options, args);
		} catch (Exception e) {
			showHelp(e.getMessage(), options);
			return;
		}

		if (cmd.hasOption("h") || cmd.getArgList().size() == 0) {
			showHelp(null, options);
			return;
		}

		String separator = "\n";
		if (cmd.hasOption("separator")) {
			separator = cmd.getOptionValue("separator").trim();
		}
		boolean toLinuxPath = cmd.hasOption("linux-path");

		String sectionName = null;
		if (cmd.hasOption("s")) {
			sectionName = cmd.getOptionValue("s").trim();
		}

		ArrayList<String> stdList = readInputStream(cmd.getOptionValue("update-from-file"));

		String configFilename = CommonUtils.getFixedPath(cmd.getArgs()[0].trim());

		File configFile = new File(configFilename);
		configFilename = configFile.getAbsolutePath();

		if (cmd.getArgList().size() == 3 || cmd.hasOption("u") || stdList.size() > 0) {
			if (configFile.exists() == false) {
				configFile.createNewFile();
			}
		}

		IniData config = new IniData(configFilename);

		String batch, key, value;
		String[] arr, kv;
		if (cmd.hasOption("u") || stdList.size() > 0) {

			if (cmd.hasOption("clear-first")) {
				config.remove(sectionName, true);
			}

			if (stdList == null) {
				stdList = new ArrayList<String>();
			}
			if (cmd.hasOption("u")) {
				batch = cmd.getOptionValue("u").trim();
				arr = batch.split("\\|\\|");
				for (String pair : arr) {
					stdList.add(pair);
				}
			}

			for (String pair : stdList) {
				// System.out.println(pair);
				kv = pair.split("=");
				if (kv.length < 2)
					continue;
				key = kv[0];
				value = kv[1];
				if (key.startsWith("-")) {
					key = key.substring(1);
					config.remove(sectionName, key, true);
				} else {
					config.put(sectionName, key, value, true);
				}
			}
		}

		if (cmd.hasOption("d")) {
			batch = cmd.getOptionValue("d").trim();
			arr = batch.split("\\|\\|");
			for (String k : arr) {
				config.remove(sectionName, k, true);
			}
		}

		key = null;
		value = null;

		if (cmd.getArgList().size() == 1 && cmd.hasOption("s") && !cmd.hasOption("d") && !cmd.hasOption("u") && stdList.size() == 0) {
			Section section = config.getSection(sectionName);
			if (section != null) {
				HashMap map = section.getData();
				Set set = map.keySet();
				Iterator it = set.iterator();
				while (it.hasNext()) {
					key = (String) it.next();
					value = section.getAndTrans(key);
					System.out.print(key + "=" + value + separator);
				}
				System.out.println();
			}
		}

		key = null;
		value = null;
		if (cmd.getArgList().size() == 2 && stdList.size() == 0) {
			key = cmd.getArgs()[1];

			value = config.getAndTrans(sectionName, key);
			if (value != null) {
				value = IniData.translateValue(value);
				if (toLinuxPath) {
					value = CommonUtils.getLinuxStylePath(value);
				}
				System.out.print(value + "\n");

			}
		} else if (cmd.getArgList().size() == 3) {
			key = cmd.getArgs()[1];
			value = cmd.getArgs()[2];
			config.put(sectionName, key, value, true);
		}
	}

	private static ArrayList<String> readInputStream(String fname) throws IOException {
		ArrayList<String> list = new ArrayList<String>();
		if (fname == null)
			return list;

		File file = new File(CommonUtils.getFixedPath(fname));
		if (file.exists() == false)
			return list;

		FileReader fread = new FileReader(file);
		BufferedReader reader = new BufferedReader(fread);
		String line;
		try {
			while (true) {
				line = reader.readLine();
				if (line == null)
					break;
				else {
					if (line.trim().equals("") == false) {
						list.add(line.trim());
					}
				}
			}
		} finally {
			try {
				reader.close();
			} catch (Exception e) {
			}
			try {
				fread.close();
			} catch (Exception e) {
			}
		}
		return list;
	}

	private static void showHelp(String error, Options options) {
		if (error != null) {
			System.out.println("Error: " + error);
			System.out.println();
		} else {
			System.out.println("A utility to read/write ini file");
		}
		HelpFormatter formatter = new HelpFormatter();
		formatter.printHelp("ini <options> <config_file> <key> {value} ", options);
		System.out.println();
		System.out.println("For example: ");
		System.out.println("	ini -s common cubrid.conf cubrid_port_id   # read one parameter");
		System.out.println("	ini -s common cubrid.conf cubrid_port_id 15533  # set new value for one parameter");
		System.out.println("	ini -s common cubrid.conf # read all parameters within section");
		System.out.println();
	}
}
