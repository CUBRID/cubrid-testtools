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

package com.navercorp.cubridqa.shell.dispatch;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;

import com.navercorp.cubridqa.common.ConfigParameterConstants;
import com.navercorp.cubridqa.shell.common.CommonUtils;
import com.navercorp.cubridqa.shell.common.Log;
import com.navercorp.cubridqa.shell.common.SSHConnect;
import com.navercorp.cubridqa.shell.common.ShellScriptInput;
import com.navercorp.cubridqa.shell.main.Context;
import com.navercorp.cubridqa.shell.main.ShellHelper;

public class Dispatch {

	public static Dispatch instance;

	private Context context;

	private ArrayList<String> tbdList;
	private int totalTbdSize;
	
	private ArrayList<String> macroSkippedList;
	private int macroSkippedSize = 0;

	private ArrayList<String> tempSkippedList;
	private int tempSkippedSize = 0;

	private int nextTestFileIndex;
	private Log all;
	
	private boolean isFinished;
	
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
		
		if(isFinished) return null;
		
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

			ArrayList<String> rawSkippedList = findSkippedTestCases();
			this.macroSkippedList = new ArrayList<String>();
			if (rawSkippedList == null) {
				this.macroSkippedSize = 0;
			} else {
				for (int i = 0; i < rawSkippedList.size(); i++) {
					if (this.tbdList.remove(rawSkippedList.get(i))) {
						System.out.println("Skipped File(macro): " + rawSkippedList.get(i));
						macroSkippedList.add(rawSkippedList.get(i));
					}
				}
				this.macroSkippedSize = macroSkippedList.size();
			}
			
			this.tempSkippedList = new ArrayList<String>();
			ArrayList<String> excluedList = findExcludedList();
				
			if (excluedList != null) {
				System.out.println("****************************************");
				System.out.println("SIZE=" + excluedList.size());
				System.out.println("****************************************");
				for (int i = 0; i < excluedList.size(); i++) {
					for (int j = 0; j < this.tbdList.size(); j++) {
						if (this.tbdList.get(j).indexOf(excluedList.get(i)) != -1) {
							System.out.println("Skipped File(Temp): " + this.tbdList.get(j));
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
		if(this.totalTbdSize == 0) {
			this.isFinished = true;
		}
	}
	
	private static String getAllTestCaseScripts(String dir) {
		return "find " + dir + " -name \"*.sh\" -type f -print | xargs -i echo {} | awk -F \"/\" '{ if( $(NF-2)\".sh\"== $NF) print }'";
	}

	private ArrayList<String> findAllTestCase() throws Exception {
		String envId = context.getEnvList().get(0);

		SSHConnect ssh = ShellHelper.createTestNodeConnect(context, envId);
		ShellScriptInput script;
		String result;
		
		try {
			script = new ShellScriptInput();			
			script.addCommand("cd ");
			script.addCommand(getAllTestCaseScripts(context.getTestCaseWorkspace()));
			result = ssh.execute(script);
		} finally {
			if (ssh != null)
				ssh.close();
		}

		String[] tcArray = result.split("\n");

		ArrayList<String> testCaseList = new ArrayList<String>();
		for (String tc : tcArray) {
			tc = tc.trim();
			if(tc.equals("")) continue;
			testCaseList.add(tc);
		}
		return testCaseList;

	}

	private ArrayList<String> findSkippedTestCases() throws Exception {

		if (context.getTestCaseSkipKey() == null) {
			return null;
		}

		String envId = context.getEnvList().get(0);

		SSHConnect ssh = ShellHelper.createTestNodeConnect(context, envId);
		ShellScriptInput script;
		String result;

		try {
			script = new ShellScriptInput();
			script.addCommand("cd ");
			script.addCommand("grep \"" + context.getTestCaseSkipKey() + "\" ` " + getAllTestCaseScripts(context.getTestCaseWorkspace()) + " `");
			result = ssh.execute(script);
		} finally {
			if (ssh != null)
				ssh.close();
		}

		result = result.replace('\r', '\n');
		String[] tcArray = result.split("\n");

		ArrayList<String> testCaseList = new ArrayList<String>();
		String[] lineItems;
		String s1, s2;
		for (String tc : tcArray) {
			lineItems = tc.split(":");
			if (lineItems == null || lineItems.length != 2) {
				continue;
			}
			s1 = lineItems[0].trim();
			s2 = lineItems[1].replace('\t', ' ');
			s2 = CommonUtils.replace(s2, " ", "");

			if (s2.startsWith(context.getTestCaseSkipKey())) {
				testCaseList.add(s1);
			}
		}
		return testCaseList;
	}
	
	private ArrayList<String> findExcludedList() throws Exception {
		String excludedFilename = context.getProperty(ConfigParameterConstants.TESTCASE_EXCLUDE_FROM_FILE);
		if(excludedFilename == null || excludedFilename.trim().equals("")) return null;
		
		String envId = context.getEnvList().get(0);

		SSHConnect ssh = ShellHelper.createTestNodeConnect(context, envId);
		ShellScriptInput script;
		String result;
		
		try {			
			script = new ShellScriptInput();
			String dir, filename;
			excludedFilename = excludedFilename.replace('\\', '/');
			int p0 = excludedFilename.lastIndexOf("/");
			if (p0 == -1) {
				dir = ".";
				filename = excludedFilename;
			} else {
				dir = excludedFilename.substring(0, p0);
				filename = excludedFilename.substring(p0 + 1);
			}
			
			script.addCommand("cd " + dir + " > /dev/null 2>&1");
			
			if (context.needCleanTestCase() && ! context.isScenarioInGit()) {
				script.addCommand("svn revert -R -q " + filename + " >/dev/null 2>&1");
				script.addCommand("svn up " + context.getSVNUserInfo() + " " + filename + " >/dev/null 2>&1");
				System.out.println("Update Excluded List result ("+ envId + "): ");
			}
			script.addCommand("cat " + filename);
			result = ssh.execute(script);			
//			System.out.println(result);
		} finally {
			if (ssh != null)
				ssh.close();
		}

		String[] tcArray = result.split("\n");

		ArrayList<String> testCaseList = new ArrayList<String>();
		String tc1;
		for (String tc : tcArray) {
			tc1 = tc.trim();
			if(tc1.equals("")) continue;
			if(tc1.startsWith("#")) continue;
			if(tc1.startsWith("--")) continue;
			testCaseList.add(tc1);
		}
		return testCaseList;
	}
	
	private String getFileNameForDispatchAll(){
		return CommonUtils.concatFile(context.getCurrentLogDir(), "dispatch_tc_ALL.txt");
	}

	private String getFileNameForDispatchFin(String envId){
		return CommonUtils.concatFile(context.getCurrentLogDir(),"dispatch_tc_FIN_" + envId + ".txt");
	}

	private void clean() throws IOException {
		File allFile;
		File finishedFile;

		File[] subList = new File(context.getCurrentLogDir()).listFiles(new FilenameFilter() {
			public boolean accept(File dir, String name) {
				return name.startsWith("dispatch_tc_");
			}
		});
		for (File file : subList) {
			file.delete();
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
}
