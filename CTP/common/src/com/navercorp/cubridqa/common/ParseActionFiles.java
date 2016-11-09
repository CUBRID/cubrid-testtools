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
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public class ParseActionFiles {

	public static void main(String[] args) throws Exception {

		if (args == null || args.length == 0) {
			return;
		}

		System.out.print("#!/bin/sh\n");
		System.out.print("cd ${CTP_HOME}/common/\n");
		System.out.print(getCommonEnv());
		System.out.print("source ./script/util_common.sh\n");
		System.out.print("\n");

		String result;
		for (String fileName : args) {
			try {
				result = run(fileName);
				System.out.print("echo [ACTION FILE] start " + fileName + "\n");
				System.out.print(result + "\n");
				System.out.print("\n");
			} catch (Exception e) {
				System.out.print("echo [ACTION FILE] fail to parse action file " + fileName + "\n");
			}
		}
	}

	private static String run(String fileName) throws IOException {
		String scriptFilename = "." + new File(fileName).getName() + ".sh";
		ArrayList<String> lineList = CommonUtils.getLineList(fileName);
		boolean needEOF = false;
		String params = null;
		StringBuffer result = new StringBuffer();

		for (String line : lineList) {
			if (line.indexOf("@CONNECT") != -1) {
				if (needEOF) {
					result.append("EEOOFF").append("\n");
					result.append("run_remote_script -initfile ./script/util_common.sh ").append(params).append(" -f ./" + scriptFilename + " \n");
					needEOF = false;
				}

				needEOF = false;
				params = getConnectParameter(line);

				result.append("\n");
				result.append(line).append("\n");

				if (params.toLowerCase().trim().equals("local") == false) {
					result.append("cat <<\"EEOOFF\" > " + scriptFilename).append("\n");
					result.append(getCommonEnv());
					needEOF = true;
				}
				result.append("echo HOST: ").append(params).append("\n");
			} else {
				result.append(line).append("\n");
			}
		}
		if (needEOF) {
			result.append("EEOOFF").append("\n");
			result.append("run_remote_script -initfile ./script/util_common.sh ").append(params).append(" -f ./" + scriptFilename + " \n");
			needEOF = false;
		}

		result.append("rm -rf " + scriptFilename + "\n");
		return result.toString();
	}

	private static String getCommonEnv() {
		Map<String, String> map = System.getenv();
		Set set = map.keySet();
		Iterator it = set.iterator();
		StringBuffer result = new StringBuffer();
		String key;
		String value;
		while (it.hasNext()) {
			key = (String) it.next();
			if (key != null && key.startsWith("KEY_")) {
				result.append("export ").append(key).append("=\'").append(map.get(key)).append("\'\n");
			}
		}
		return result.toString();
	}

	private static String getConnectParameter(String cmd) {
		if (cmd == null) {
			return null;
		}

		int i = cmd.indexOf(":");
		if (i == -1) {
			return null;
		} else {
			return cmd.substring(i + 1);
		}
	}
}
