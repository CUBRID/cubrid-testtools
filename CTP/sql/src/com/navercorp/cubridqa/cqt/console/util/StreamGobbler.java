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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.List;

import com.navercorp.cubridqa.cqt.console.ConsoleAgent;

public class StreamGobbler implements Runnable {
	InputStream iserror, isstdout;
	String type;
	OutputStream os;
	Process process;
	private List list;

	public List getList() {
		return list;
	}

	public void setList(List list) {
		this.list = list;
	}

	StreamGobbler() {
		this(null);
	}

	StreamGobbler(OutputStream redirect) {
		this.os = redirect;
	}

	public void run() {
		try {
			iserror = process.getErrorStream();
			isstdout = process.getInputStream();
			InputStreamReader isr = new InputStreamReader(iserror);
			BufferedReader errorBr = new BufferedReader(isr);
			String line = null;

			InputStreamReader irstd = new InputStreamReader(isstdout);
			BufferedReader stdBr = new BufferedReader(irstd);
			// line = null;
			// while ((line = errorBr.readLine()) != null) {
			// System.out.println("Error.....  " + line);
			// }
			// line = null;
			while ((line = stdBr.readLine()) != null) {
				list.add(line);
				System.out.println("OUTPUT ........  " + line);
				ConsoleAgent.addMessage(line + SystemConst.LINE_SEPERATOR);
			}
		} catch (IOException ioe) {
			ioe.printStackTrace();
		}
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public Process getProcess() {
		return process;
	}

	public void setProcess(Process process) {
		this.process = process;
	}

}
