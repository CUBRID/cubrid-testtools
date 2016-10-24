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
package com.navercorp.cubridqa.ha_repl.dispatch;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;

import com.navercorp.cubridqa.common.CommonUtils;
import com.navercorp.cubridqa.common.Log;
import com.navercorp.cubridqa.ha_repl.Context;

public class Dispatch {

	private static Dispatch instance;

	Context context;

	String testCaseRoot;
	ArrayList<String> envList;
	boolean isContinueMode;

	ArrayList<String> tbdList;
	int tbdSize;

	int nextTestFileIndex;
	Log all;

	private ArrayList<String> macroSkippedList;
	private int macroSkippedSize = 0;

	private ArrayList<String> tempSkippedList;
	private int tempSkippedSize = 0;

	private Dispatch(Context context) throws Exception {
		this.context = context;
		this.testCaseRoot = context.getTestCaseRoot();
		this.envList = context.getTestEnvList();
		this.isContinueMode = context.isContinueMode();

		this.tbdList = new ArrayList<String>();
		this.tbdSize = 0;
		this.nextTestFileIndex = -1;

		load();
	}

	public synchronized static void init(Context context) throws Exception {
		instance = new Dispatch(context);
	}

	public static Dispatch getInstance() {
		return instance;
	}

	public synchronized String nextTestFile() {
		if (tbdSize == 0 || this.nextTestFileIndex >= tbdSize) {
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

		if (this.isContinueMode) {
			this.tbdList = CommonUtils.getLineList(getFileNameForDispatchAll());
			ArrayList<String> finList;
			for (String envId : envList) {
				finList = CommonUtils.getLineList(getFileNameForDispatchFin(envId));
				if (finList != null) {
					this.tbdList.removeAll(finList);
				}
			}
		} else {
			initInstanceFiles();
			travel(new File(testCaseRoot));
			this.macroSkippedSize = 0;
			this.macroSkippedList = new ArrayList<String>();

			this.tempSkippedList = new ArrayList<String>();
			ArrayList<String> excludedList = findExcludedList();
			if (excludedList != null) {
				for (int i = 0; i < excludedList.size(); i++) {
					for (int j = 0; j < this.tbdList.size(); j++) {
						if (this.tbdList.get(j).indexOf(excludedList.get(i)) != -1) {
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
		this.tbdSize = this.tbdList.size();
	}

	private void travel(File testCaseFile) {
		if (testCaseFile.isDirectory()) {
			File[] subList = testCaseFile.listFiles();
			for (File subFile : subList) {
				travel(subFile);
			}
		} else {
			if (testCaseFile.getName().toUpperCase().endsWith(".TEST")) {
				tbdList.add(testCaseFile.getAbsolutePath());
			}
		}
	}

	private String getFileNameForDispatchAll() {
		return CommonUtils.concatFile(context.getCurrentLogDir(), "dispatch_tc_ALL.txt");
	}

	private String getFileNameForDispatchFin(String envName) {
		return CommonUtils.concatFile(context.getCurrentLogDir(), "dispatch_tc_FIN_" + envName + ".txt");
	}

	private ArrayList<String> findExcludedList() throws Exception {
		String excludedFilename = context.getExcludedTestCaseFile();
		if (CommonUtils.isEmpty(excludedFilename))
			return null;

		ArrayList<String> lineList = CommonUtils.getLineList(excludedFilename.trim());

		if (lineList == null || lineList.size() == 0)
			return null;

		ArrayList<String> excludedList = new ArrayList<String>();

		for (String tc : lineList) {
			if (tc.trim().equals(""))
				continue;
			if (tc.trim().startsWith("#"))
				continue;
			if (tc.trim().startsWith("--"))
				continue;
			excludedList.add(tc);
		}
		return excludedList;
	}

	private void initInstanceFiles() throws IOException {
		File allFile;
		File finishedFile;

		allFile = new File(getFileNameForDispatchAll());
		allFile.createNewFile();

		for (String envName : envList) {
			finishedFile = new File(getFileNameForDispatchFin(envName));
			finishedFile.createNewFile();
		}
	}

	public int getTbdSize() {
		return tbdSize;
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
	
	public int getTotalSize() {
		return tbdSize + macroSkippedSize + tempSkippedSize;
	}
}
