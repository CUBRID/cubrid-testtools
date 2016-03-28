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

import java.io.IOException;
import java.util.ArrayList;

public class ParseActionFiles {

	private static String commonHome = System.getenv("COMMON_HOME");

	public static void main(String[] args) throws Exception {

		if (args == null || args.length == 0) {
			return;
		}

		System.out.println("#!/bin/sh");
		System.out.println("source " + commonHome + "/script/util_common.sh");
		System.out.println();

		String result;
		for (String fileName : args) {
			try {
				result = run(fileName);
				System.out.println("echo [ACTION FILE] start " + fileName);
				System.out.println(result);
				System.out.println();
			} catch (Exception e) {
				System.out.println("echo [ACTION FILE] fail to parse action file " + fileName);
			}
		}
	}

	private static String run(String fileName) throws IOException {
		ArrayList<String> lineList = CommonUtils.getLineList(fileName);
		boolean needEOF = false;
		String params = null;
		StringBuffer result = new StringBuffer();
		String espLine;

		for (String line : lineList) {
			if (line.indexOf("@CONNECT") != -1) {
				params = getConnectParameter(line);
				if (params == null) {
					result.append(line).append("\n");
					continue;
				}

				if (needEOF) {
					result.append("EEOOFF").append("\n");
					needEOF = false;
				}
				result.append("\n");

				if (params.toLowerCase().trim().equals("local")) {
					result.append(line).append("\n");
					needEOF = false;
				} else {
					result.append(line).append("\n");
					result.append("run_remote_script -initfile " + commonHome + "/script/util_common.sh ").append(params).append(" -f /dev/stdin <<\"EEOOFF\"").append("\n");
					needEOF = true;
				}
			} else {
				result.append(line).append("\n");
			}
		}
		if (needEOF) {
			result.append("EEOOFF").append("\n");
			needEOF = false;
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
