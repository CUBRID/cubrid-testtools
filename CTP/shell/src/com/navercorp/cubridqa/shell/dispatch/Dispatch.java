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
import java.util.Properties;

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
	
	private int totalTbdSize;

	private ArrayList<TestCaseRequest> tbdList;	

	private ArrayList<String> macroSkippedList;
	private int macroSkippedSize = 0;

	private ArrayList<String> tempSkippedList;
	private int tempSkippedSize = 0;

	private Log all;
	
	private TestNodePool nodePool;

	private boolean isFinished;

	private Dispatch(Context context) throws Exception {
		this.context = context;
		this.tbdList = new ArrayList<TestCaseRequest>();
		this.totalTbdSize = 0;
		this.isFinished = false;
		
		nodePool = new TestNodePool();
		
		ArrayList<String> nodeList = new ArrayList<String>();
		nodeList.addAll(context.getEnvList());
		nodeList.addAll(context.getFollowerList());
		
		for(String envId: nodeList) {
			
			String paramTypeValue = context.getInstanceProperty(envId, "type");
			if(paramTypeValue!=null) {
				paramTypeValue = paramTypeValue.trim();
			}
			int type;
			if (paramTypeValue == null || paramTypeValue.equals("") || paramTypeValue.equals("default")) {
				type = TestNode.TYPE_DEFAULT;
			} else if (paramTypeValue.equals("follow")) {
				type = TestNode.TYPE_FOLLOW;
			} else if (paramTypeValue.equals("specific")) {
				type = TestNode.TYPE_SPECIFIC;
			} else {
				throw new Exception ("unknown type of instance (Type is " + paramTypeValue + ").");
			}
			
			nodePool.addTestNode(envId, type, context.getInstanceProperty(envId, ConfigParameterConstants.TEST_INSTANCE_HOST_SUFFIX));
		}
		
		load();
	}

	public static void init(Context context) throws Exception {
		instance = new Dispatch(context);
	}

	public static Dispatch getInstance() {
		return instance;
	}

	public synchronized TestCaseRequest nextTestFile(String envId) {
		if (isFinished) {
			return null;
		}

		if (this.tbdList.size() == 0) {
			isFinished = true;
			return null;
		}

		String expectedMachines;
		TestCaseRequest req;
		ArrayList<TestNode> borrowNodes;
		while (this.tbdList.size() > 0) {
			for (int i = 0; i < this.tbdList.size(); i++) {
				req = this.tbdList.get(i);
				expectedMachines = req.getExpectedMachines();
				if (CommonUtils.isEmpty(expectedMachines)) {					
					TestNode node = this.nodePool.borrowNode(envId, req.isExclusive());
					if(node == null) {
						continue;
					}
					req.addTestNode(node);
					req.setHasTestNodes(true);
					this.tbdList.remove(i);
					return req;
				} else {
					Selector s = context.getSelector(expectedMachines);
					if (s == null ) {
						TestNode node = this.nodePool.borrowNode(envId, req.isExclusive());
						if(node == null) {
							continue;
						}
						this.tbdList.remove(i);
						req.setHasTestNodes(false);
						return req;
					} else {
						borrowNodes = this.nodePool.borrowNodes(envId, s.getRule(), req.isExclusive());
						if (borrowNodes != null && borrowNodes.size() > 0) {
							req.setNodeList(borrowNodes);
							req.setHasTestNodes(true);
							this.tbdList.remove(i);
							return req;
						}
					}
				}
			}
		}
		return null;
	}
	
	public void finish(TestCaseRequest request) {
		this.nodePool.returnNodes(request.getNodeList());
	}

	private void load() throws Exception {

		if (context.isContinueMode()) {
			ArrayList<String> allList = CommonUtils.getLineList(getFileNameForDispatchAll());
			ArrayList<String> finList;

			File[] subList = new File(context.getCurrentLogDir()).listFiles(new FilenameFilter() {
				public boolean accept(File dir, String name) {
					return name.startsWith("dispatch_tc_FIN") && name.endsWith(".txt");
				}
			});
			for (File file : subList) {
				finList = CommonUtils.getLineList(file.getAbsolutePath());
				if (finList != null) {
					allList.removeAll(finList);
				}
			}
			this.tbdList = new ArrayList<TestCaseRequest>();
			for(String item: allList) {
				this.tbdList.add(new TestCaseRequest(item, null));
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
				System.out.println("# OF EXCLUDED = " + excluedList.size());
				System.out.println("****************************************");
				String checkItem;
				for (int i = 0; i < excluedList.size(); i++) {
					for (int j = this.tbdList.size() - 1; j >= 0; j--) {
						checkItem = this.tbdList.get(j).getTestCase();
						if (checkItem.endsWith("/") == false) {
							checkItem = checkItem + "/";
						}
						if (checkItem.indexOf(excluedList.get(i)) != -1) {
							System.out.println("Skipped File(Temp): " + this.tbdList.get(j));
							this.tempSkippedSize++;
							this.tempSkippedList.add(this.tbdList.get(j).getTestCase());
							this.tbdList.remove(j);
						}
					}
				}
			}

			this.all = new Log(getFileNameForDispatchAll(), false);
			for (TestCaseRequest item: tbdList) {
				all.println(item.getTestCase());
			}
			this.all.close();
		}
		
		ArrayList<TestCaseRequest> replaceWithList = findAllTestCasesHasTestConf();
		if (replaceWithList != null && replaceWithList.size() > 0) {
			int index;
			for (TestCaseRequest item : replaceWithList) {
				index = this.tbdList.indexOf(item);
				if (index >= 0) {
					this.tbdList.set(index, item);
				}
			}
		}
		
		this.totalTbdSize = this.tbdList.size();
		if (this.totalTbdSize == 0) {
			this.isFinished = true;
		}
	}

	private static String getAllTestCaseScripts(String dir) {
		return "find " + dir + " -name \"*.sh\" -type f -print | xargs -i echo {} | awk -F \"/\" '{ if( $(NF-2)\".sh\"== $NF) print }'";
	}
	
	private static String getAllTestCaseScriptsHasTestConf(String dir) {
		return "find " + dir + " -name 'test.conf' -type f -exec sh -c \"echo {}; cat {}\" \\;";
	}

	private ArrayList<TestCaseRequest> findAllTestCase() throws Exception {
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

		ArrayList<TestCaseRequest> testCaseList = new ArrayList<TestCaseRequest>();
		TestCaseRequest item;
		for (String tc : tcArray) {
			tc = tc.trim();
			if (tc.equals(""))
				continue;
			item = new TestCaseRequest(tc, null);
			testCaseList.add(item);
		}
		return testCaseList;
	}
	
	private ArrayList<TestCaseRequest> findAllTestCasesHasTestConf() throws Exception {
		String envId = context.getEnvList().get(0);

		SSHConnect ssh = ShellHelper.createTestNodeConnect(context, envId);
		ShellScriptInput script;
		String result;

		try {
			script = new ShellScriptInput();
			script.addCommand("cd ");
			script.addCommand(getAllTestCaseScriptsHasTestConf(context.getTestCaseWorkspace()));
			result = ssh.execute(script);
		} finally {
			if (ssh != null)
				ssh.close();
		}

		String[] lineArray = result.split("\n");

		ArrayList<TestCaseRequest> testCaseList = new ArrayList<TestCaseRequest>();
		TestCaseRequest item = null;
		String k, v;
		String[] arr;
		for (String line : lineArray) {
			line = line.trim();
			if (line.equals("") || line.startsWith("#"))
				continue;
			if (line.endsWith("cases/test.conf")) {
				arr = line.split("/");
				line = CommonUtils.replace(line, "/test.conf", "/" + arr[arr.length - 3] + ".sh");
				item = new TestCaseRequest(line, new Properties());
				
				testCaseList.add(item);
			} else {
				int p = line.indexOf(":");
				if (p != -1) {
					k = line.substring(0, p);
					v = line.substring(p + 1);
					if (item != null)
						item.setProperty(k, v);
				}
			}
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
		String excludedFilename = context.getExcludedFile();
		if (excludedFilename == null || excludedFilename.trim().equals(""))
			return null;

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

			if (context.needCleanTestCase() && !context.isScenarioInGit()) {
				script.addCommand("svn revert -R -q " + filename + " >/dev/null 2>&1");
				script.addCommand("svn up " + context.getSVNUserInfo() + " " + filename + " >/dev/null 2>&1");
				System.out.println("Update Excluded List result (" + envId + "): ");
			}
			script.addCommand("cat " + filename);
			result = ssh.execute(script);
			// System.out.println(result);
		} finally {
			if (ssh != null)
				ssh.close();
		}

		String[] tcArray = result.split("\n");

		ArrayList<String> testCaseList = new ArrayList<String>();
		String tc1;
		for (String tc : tcArray) {
			tc1 = tc.trim();
			if (tc1.equals(""))
				continue;
			if (tc1.startsWith("#"))
				continue;
			if (tc1.startsWith("--"))
				continue;
			if (tc1.endsWith("/") == false) {
				tc1 = tc1 + "/";
			}
			testCaseList.add(tc1);
		}
		return testCaseList;
	}

	private String getFileNameForDispatchAll() {
		return CommonUtils.concatFile(context.getCurrentLogDir(), "dispatch_tc_ALL.txt");
	}

	private String getFileNameForDispatchFin(String envId) {
		return CommonUtils.concatFile(context.getCurrentLogDir(), "dispatch_tc_FIN_" + envId + ".txt");
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
