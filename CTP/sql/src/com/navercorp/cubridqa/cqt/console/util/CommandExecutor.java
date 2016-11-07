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

import com.navercorp.cubridqa.cqt.console.Executor;

public class CommandExecutor {

	private boolean startOut = true;

	private boolean startError = true;

	private Thread threadOut;

	private Thread threadError;

	private Process process;

	private InputStream inputStream;

	private BufferedReader outReader;

	private InputStream errorStream;

	private BufferedReader errorReader;

	private StringBuilder message = new StringBuilder();

	private Executor listener;

	private boolean endFlag = false;

	private boolean beginCommand = false;

	private boolean beginOutput = false;

	private String firstLine;

	private String os = SystemUtil.getOS();

	public CommandExecutor(String command, String firstLine, Executor listener) {
		this.firstLine = firstLine;
		this.listener = listener;
		try {
			process = Runtime.getRuntime().exec(command);
		} catch (IOException e) {
			System.out.println(e.getMessage());
			e.printStackTrace();
		}
	}

	/**
	 * 
	 * @Title: execute
	 * @Description:Execute commands.
	 * @param @return
	 * @return String
	 * @throws
	 */
	public String execute() {
		if (process == null) {
			return "";
		}

		try {
			init();
			process.waitFor();
			Thread.sleep(100);
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			endFlag = true;
		}

		return message.toString();
	}

	/**
	 * 
	 * @Title: init
	 * @Description:Init input reader and error reader which is used to obtain
	 *                   execute information.
	 * @param
	 * @return void
	 * @throws
	 */
	private void init() {
		inputStream = process.getInputStream();
		errorStream = process.getErrorStream();

		if (startError) {
			try {
				errorReader = new BufferedReader(new InputStreamReader(errorStream));
				threadError = new Thread() {
					@Override
					public void run() {
						try {
							String line = null;
							while (!endFlag && (line = errorReader.readLine()) != null) {
								appendMessage(line);
							}
						} catch (Exception e) {
						}
					}
				};
				threadError.start();
			} catch (Exception e) {
			}
		}
		if (startOut) {
			try {
				outReader = new BufferedReader(new InputStreamReader(inputStream));
				threadOut = new Thread() {
					@Override
					public void run() {
						try {
							String line = null;
							while (!endFlag && (line = outReader.readLine()) != null) {
								appendMessage(line);
							}
						} catch (Exception e) {
						}
					}
				};
				threadOut.start();
			} catch (Exception e) {
			}
		}
	}

	/**
	 * 
	 * @Title: appendMessage
	 * @Description:Output execute information.
	 * @param @param line:execute information.
	 * @return void
	 * @throws
	 */
	private synchronized void appendMessage(String line) {
		if (line == null) {
			return;
		}

		String temp = line.toLowerCase().trim();
		if (temp.indexOf("not found") != -1) {
			return;
		}

		message.append(line + System.getProperty("line.separator"));
		if (listener != null) {
			listener.onMessage(line);
		}
	}
}
