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
package com.navercorp.cubridqa.common.coreanalyzer;

import java.io.File;
import java.util.ArrayList;

import com.navercorp.cubridqa.common.CommonUtils;

public class Analyzer {

	private String coreFilename;
	private String coreName;

	private String envHOME;
	private String envJAVA_HOME;

	private String detailStack;
	private String digestStack;
	private String processName;
	private String summary;

	public Analyzer(String coreFilename) {
		this.coreFilename = coreFilename;
		this.coreName = new File(coreFilename).getName();

		String cmd;
		String result;

		cmd = "echo $HOME";
		result = LocalInvoker.exec(cmd);
		this.envHOME = result.trim();

		cmd = "echo $JAVA_HOME";
		result = LocalInvoker.exec(cmd);
		this.envJAVA_HOME = result.trim();
	}

	public void showCoreInformation() {
		System.out.println("=================================== CORE ANALYZER ===================================");
		System.out.println("\tCORE FILE   : [" + coreFilename + "]");
		System.out.println("\tHOME        : " + "[" + this.envHOME + "]");
		System.out.println("\tJAVA_HOME   : " + "[" + this.envJAVA_HOME + "]");
		System.out.println("\tPROCESS NAME: " + "[" + processName + "]");
		System.out.println("\tSUMMARY     : " + "[" + this.summary + "]");
		System.out.println("\tDETAIL STACK:");
		System.out.println("--------------------------------------------------------");
		System.out.println(this.detailStack);
		System.out.println("--------------------------------------------------------");
		System.out.println("\tSTACK DIGEST:");
		System.out.println(this.digestStack);
		System.out.println("<END>");
		System.out.println();
	}

	public String[] getCoreFullStack() {
		String[] result = new String[2];
		result[0] = this.summary;
		result[1] = this.detailStack;
		return result;
	}

	public void analyze(boolean fullStack) throws Exception {

		String cmd;
		String result;

		cmd = "file " + coreFilename;
		result = LocalInvoker.exec(cmd);

		String processName = extractCoreProcessName(result);
		if (processName == null) {
			throw new Exception("Fail to extract process name from " + coreName);
		}

		this.processName = processName;

		cmd = "gdb `which " + processName + "` " + coreFilename + " <<EOF" + Constants.LINE_SEPARATOR;
		if (fullStack) {
			cmd += "bt full" + Constants.LINE_SEPARATOR;
		} else {
			cmd += "where" + Constants.LINE_SEPARATOR;
		}
		cmd += "EOF";

		result = LocalInvoker.exec(cmd);

		this.detailStack = extractCoreStack(result).trim();
		String[] info = extractCoreStackDigest(this.detailStack);
		this.summary = info[0];
		this.digestStack = info[1];
	}

	private static String extractCoreProcessName(String desc) {
		// core.27788: ELF 64-bit LSB core file AMD x86-64, version 1 (SYSV),
		// SVR4-style, from 'cub_server'
		int p1 = desc.indexOf("from '");

		if (p1 == -1) {
			return null;
		}

		int p2 = desc.indexOf("'", p1 + 6);

		String processName = (p2 == -1) ? desc.substring(p1 + 6) : desc.substring(p1 + 6, p2);

		if (processName.indexOf("cub_cas") != -1) {
			processName = "cub_cas";
		} else if (processName.indexOf("cub_server") != -1) {
			processName = "cub_server";
		} else if (processName.indexOf("cubrid ") != -1) {
			if ((processName.indexOf(" service") != -1) || (processName.indexOf(" server") != -1) || (processName.indexOf(" broker") != -1) || (processName.indexOf(" manager") != -1)
					|| (processName.indexOf(" heartbeat") != -1) || (processName.indexOf(" hb") != -1)) {
				processName = "cubrid";
			}else{
				processName = "cub_admin";
			}
			
		} else if (processName.indexOf("csql") != -1) {
			processName = "csql";
		} else {
			processName = processName.split(" ")[0].trim();
		}
		return processName;
	}

	private String extractCoreStack(String desc) {
		String searchKey = "(gdb)";
		int p1 = desc.lastIndexOf(searchKey);
		int p2 = desc.lastIndexOf(searchKey, p1 - 1);
		if (p1 == -1 || p2 == -1 || p1 <= p2) {
			return null;
		}

		String stack = desc.substring(p2 + searchKey.length(), p1).trim();
		return stack;
	}

	public static String[] extractCoreStackDigest(String detailStack) throws Exception {
		String[] result = new String[2];

		ArrayList<StackItem> itemList = extractStackItems(detailStack);
		StringBuilder digests = new StringBuilder();
		String excludedMethods = ",er_log,er_set_internal,abort_handler,er_set,";
		for (StackItem s : itemList) {
			digests.append(s.getDigest()).append("\n");
			if (CommonUtils.isEmpty(result[0])) {
				if (s.getOriFileCodes().startsWith("src/")) {
					if (excludedMethods.indexOf("," + s.getMethodCodes().trim() + ",") == -1) {
						result[0] = "Core dumped in " + s.getMethodCodes() + " at " + s.getOriFileCodes();
					}
				}
			}
		}

		result[1] = digests.toString().trim();
		return result;
	}

	private static ArrayList<StackItem> extractStackItems(String detailStack) throws Exception {
		ArrayList<StackItem> list = new ArrayList<StackItem>();

		int index = 0;
		int itemStartPos = 0, itemEndPos = 0;

		String itemText;
		String methodCodes, oriFileCodes, fileCodes;
		StackItem item;
		String[] arr;
		while (true) {
			itemStartPos = detailStack.indexOf("#" + index, itemEndPos);
			if (itemStartPos == -1) {
				break;
			}

			itemEndPos = detailStack.indexOf("#" + (index + 1), itemStartPos);
			if (itemEndPos == -1) {
				itemEndPos = detailStack.length();
			}

			itemText = detailStack.substring(itemStartPos, itemEndPos).trim();
			itemText = CommonUtil.replace(itemText, "\t", " ");
			itemText = CommonUtil.replace(itemText, "\n", " ");
			itemText = CommonUtil.replace(itemText, "\r", " ");
			itemText = CommonUtil.removeMoreBlanks(itemText).trim();

			arr = itemText.split(" ");

			if (arr != null && arr.length > 0) {

				if (arr[1].startsWith("0x") == false) {
					methodCodes = arr[1];
				} else {
					methodCodes = arr[3];
				}

				int prefixIndexForFileCodes = getFileCodesPrefixIndex(arr);
				if (prefixIndexForFileCodes != -1) {
					oriFileCodes = arr[prefixIndexForFileCodes + 1];
				} else {
					oriFileCodes = "";
				}

				methodCodes = refineMethodCodes(methodCodes);
				fileCodes = refineFileCodes(oriFileCodes);
				int p = oriFileCodes.lastIndexOf("src/");
				if (p != -1) {
					oriFileCodes = oriFileCodes.substring(p);
				}
				item = new StackItem(index, methodCodes, fileCodes, oriFileCodes);
				list.add(item);
			}

			index++;
		}

		return list;
	}

	private static int getFileCodesPrefixIndex(String[] arr) {
		String s;
		for (int i = 0; i < arr.length; i++) {
			s = arr[i].trim();
			if (s.equals("at") || s.equals("from")) {
				return i;
			}
		}
		return -1;
	}

	private static String refineMethodCodes(String desc) {
		return desc;

	}

	private static String refineFileCodes(String desc) {
		String result;
		int p = desc.lastIndexOf(":");
		if (p == -1) {
			result = desc;
		} else {
			result = desc.substring(0, p);
		}

		result = result.trim();
		if (result.equals("")) {
			return "<UNKNOWN>";
		}

		// result = CommonUtil.replace(result, envJAVA_HOME, "$JAVA_HOME");
		// result = CommonUtil.replace(result, envHOME, "$HOME");

		p = result.lastIndexOf("/src/");
		if (p == -1) {
			result = "<EXTERNAL LIBRARY>";
		} else {
			result = result.substring(p + 1);
		}

		return result;
	}

	public String getProcessName() {
		return this.processName;
	}

	public String getDetailStack() {
		return this.detailStack;
	}

	public String getDigestStack() {
		return this.digestStack;
	}
}
