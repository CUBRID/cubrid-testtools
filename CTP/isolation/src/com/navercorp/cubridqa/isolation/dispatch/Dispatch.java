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

package com.navercorp.cubridqa.isolation.dispatch;

import java.io.File;


import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.util.ArrayList;

import com.navercorp.cubridqa.isolation.Constants;
import com.navercorp.cubridqa.isolation.Context;
import com.navercorp.cubridqa.isolation.IsolationScriptInput;
import com.navercorp.cubridqa.common.CommonUtils;
import com.navercorp.cubridqa.common.Log;
import com.navercorp.cubridqa.shell.common.SSHConnect;

public class Dispatch {

	public static Dispatch instance;

	private Context context;

	private ArrayList<String> tbdList;
	private int totalTbdSize;

	private int nextTestFileIndex;
	private Log all;

	private boolean isFinished;

	private ArrayList<String> macroSkippedList;
	private int macroSkippedSize = 0;

	private ArrayList<String> tempSkippedList;
	private int tempSkippedSize = 0;

	private Dispatch(Context context) throws Exception {
		this.context = context;
		this.tbdList = new ArrayList<String>();
		this.totalTbdSize = 0;
		this.isFinished = false;
		this.nextTestFileIndex = -1;
		load();
	}

	public static void init(Context context) throws Exception {
		instance = new Dispatch(context);
	}

	public static Dispatch getInstance() {
		return instance;
	}

	public synchronized String nextTestFile() {

		if (isFinished)
			return null;

		if (totalTbdSize == 0 || this.nextTestFileIndex >= totalTbdSize) {
			isFinished = true;
			return null;
		}
		if (this.nextTestFileIndex < 0) {
			this.nextTestFileIndex = 0;
		}
		String nextTestFile = tbdList.get(this.nextTestFileIndex);
		this.nextTestFileIndex++;
		return nextTestFile;
	}

	private void load() throws Exception {

		if (context.isContinueMode()) {
			this.tbdList = CommonUtils.getLineList(getFileNameForDispatchAll());
			ArrayList<String> finList;

			File[] subList = new File(context.getCurrentLogDir()).listFiles(new FilenameFilter() {
				public boolean accept(File dir, String name) {
					return name.startsWith("dispatch_tc_FIN") && name.endsWith(".txt");
				}
			});
			for (File file : subList) {
				finList = CommonUtils.getLineList(file.getAbsolutePath());
				if (finList != null) {
					this.tbdList.removeAll(finList);
				}
			}

		} else {
			clean();
			this.tbdList = findAllTestCase();

			this.macroSkippedSize = 0;
			this.macroSkippedList = new ArrayList<String>();

			this.tempSkippedSize = 0;
			this.tempSkippedList = new ArrayList<String>();

			ArrayList<String> excluedList = findExcludedList();
			if (excluedList != null) {
				for (int i = 0; i < excluedList.size(); i++) {
					for (int j = 0; j < this.tbdList.size(); j++) {
						if (this.tbdList.get(j).indexOf(excluedList.get(i)) != -1) {
							System.out.println("Excluded File: " + this.tbdList.get(j));

							this.tempSkippedSize++;
							this.tempSkippedList.add(this.tbdList.get(j));

							this.tbdList.remove(j);
							break;
						}
					}
				}
			}

			this.all = new Log(getFileNameForDispatchAll(), false);
			for (String line : tbdList) {
				all.println(line);
			}
			this.all.close();
		}
		this.nextTestFileIndex = -1;
		this.totalTbdSize = this.tbdList.size();
		if (this.totalTbdSize == 0) {
			this.isFinished = true;
		}
	}

	private ArrayList<String> findAllTestCase() throws Exception {
		String envId = context.getEnvList().get(0);

		String host = context.getInstanceProperty(envId, "ssh.host");
		String port = context.getInstanceProperty(envId, "ssh.port");
		String user = context.getInstanceProperty(envId, "ssh.user");
		String pwd = context.getInstanceProperty(envId, "ssh.pwd");

		SSHConnect ssh = new SSHConnect(host, port, user, pwd);
		IsolationScriptInput script;
		String result;

		try {
			script = new IsolationScriptInput();
			script.addCommand("cd ");
			script.addCommand("find " + context.getTestCaseRoot() + " -name \"*.ctl\" -type f -print");
			result = ssh.execute(script);
			// System.out.println(result);
		} finally {
			if (ssh != null)
				ssh.close();
		}

		String[] tcArray = result.split("\n");

		ArrayList<String> testCaseList = new ArrayList<String>();
		for (String tc : tcArray) {
			if (tc.trim().equals(""))
				continue;
			testCaseList.add(tc.trim());
		}
		return testCaseList;

	}

	private ArrayList<String> findExcludedList() throws Exception {
		String excludedFilename = context.getProperty("main.testcase.excluded");
		if (excludedFilename == null || excludedFilename.trim().equals(""))
			return null;

		String envId = context.getEnvList().get(0);

		String host = context.getInstanceProperty(envId, "ssh.host");
		String port = context.getInstanceProperty(envId, "ssh.port");
		String user = context.getInstanceProperty(envId, "ssh.user");
		String pwd = context.getInstanceProperty(envId, "ssh.pwd");

		SSHConnect ssh = new SSHConnect(host, port, user, pwd);
		IsolationScriptInput script;
		String result;

		try {
			script = new IsolationScriptInput();
			script.addCommand("cd > /dev/null 2>&1");
			script.addCommand("cat " + excludedFilename);
			result = ssh.execute(script);
		} finally {
			if (ssh != null)
				ssh.close();
		}

		// ArrayList<String> tcArray =
		// CommonUtils.getLineList(excludedFilename);
		String[] tcArray = result.split("\n");

		ArrayList<String> testCaseList = new ArrayList<String>();
		for (String tc : tcArray) {
			if (tc.trim().equals(""))
				continue;
			if (tc.trim().startsWith("#"))
				continue;
			if (tc.trim().startsWith("--"))
				continue;
			testCaseList.add(tc);
		}
		return testCaseList;
	}

	private void clean() throws IOException {
		File allFile;
		File finishedFile;

		File[] subList = new File(context.getCurrentLogDir()).listFiles();
		for (File file : subList) {
			if(file.isFile()) file.delete();
		}

		allFile = new File(getFileNameForDispatchAll());
		allFile.createNewFile();

		ArrayList<String> envList = context.getEnvList();
		for (String envId : envList) {
			finishedFile = new File(getFileNameForDispatchFin(envId));
			finishedFile.createNewFile();
		}
	}

	public int getTotalTbdSize() {
		return totalTbdSize;
	}

	public boolean isFinished() {
		return this.isFinished;
	}

	public int getMacroSkippedSize() {
		return this.macroSkippedSize;
	}

	public ArrayList<String> getMacroSkippedList() {
		return this.macroSkippedList;
	}

	public int getTempSkippedSize() {
		return this.tempSkippedSize;
	}

	public ArrayList<String> getTempSkippedList() {
		return this.tempSkippedList;
	}

	public String getFileNameForDispatchAll() {
		return CommonUtils.concatFile(context.getCurrentLogDir(), "dispatch_tc_ALL.txt");
	}

	public String getFileNameForDispatchFin(String envId) {
		return CommonUtils.concatFile(context.getCurrentLogDir(), "dispatch_tc_FIN_" + envId + ".txt");
	}

	public static String getFileContent(String filename) throws IOException {
		File file = new File(filename);
		if (!file.exists()) {
			return null;
		}
		StringBuffer result = new StringBuffer();
		FileInputStream fis = new FileInputStream(file);
		InputStreamReader reader = new InputStreamReader(fis, "UTF-8");

		LineNumberReader lineReader = new LineNumberReader(reader);
		String line;

		while ((line = lineReader.readLine()) != null) {
			if (line.trim().equals(""))
				continue;
			result.append(line.trim()).append(Constants.LINE_SEPARATOR);
		}
		lineReader.close();
		reader.close();
		fis.close();
		return result.toString();
	}

}
