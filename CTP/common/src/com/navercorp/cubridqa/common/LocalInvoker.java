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

import java.io.BufferedReader;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import com.navercorp.cubridqa.common.CommonUtils;

public class LocalInvoker {

	public final static int SHELL_TYPE_WINDOWS = 1;
	public final static int SHELL_TYPE_LINUX = 2;
	public final static int SHELL_TYPE_CYGWIN = 3;

	public static final String LINE_SEPARATOR = "\n";

	public static final String COMP_FLAG = "ALL_COMPLETED";

	public static String exec(String cmds, int shellType, boolean showInConsole) {
		//System.out.println("commands: " + cmds + ", " + shellType + "," + showInConsole);
		File tmpFile;
		try {
			tmpFile = File.createTempFile(".localexec", shellType == SHELL_TYPE_WINDOWS ? ".bat" : ".sh");
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		FileWriter writer = null;
		try {
			writer = new FileWriter(tmpFile);
			writer.write(cmds);
			writer.write(LINE_SEPARATOR);
			writer.write("echo " + COMP_FLAG);
			writer.write(LINE_SEPARATOR);
			writer.write(COMP_FLAG);
			writer.write(LINE_SEPARATOR);
			writer.flush();
		} catch (IOException e) {
			throw new RuntimeException(e);
		} finally {
			if (writer != null) {
				try {
					writer.close();
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			}
		}

		String result;
		String invokedCmd;
		switch (shellType) {
		case SHELL_TYPE_LINUX: {
			invokedCmd = "sh " + tmpFile.getAbsolutePath() + " 2>&1";
			break;
		}
		case SHELL_TYPE_WINDOWS: {
			invokedCmd = tmpFile.getAbsolutePath() + " 2>&1";
			break;
		}
		case SHELL_TYPE_CYGWIN: {
			invokedCmd = "bash.exe -c " + CommonUtils.getLinuxStylePath(tmpFile.getAbsolutePath()) + " 2>&1";
			break;
		}
		default: {
			throw new RuntimeException();
		}
		}

		result = execCommands(invokedCmd, showInConsole);

		try {
			tmpFile.delete();
		} catch (Exception e) {
			System.out.println("Fail to delete " + tmpFile + " but NO HARM");
		}
		return result;
	}

	public static String execCommands(String cmds, boolean showInConsole) {
		Runtime run = Runtime.getRuntime();
		Process p = null;
		int pos;

		String result;

		try {
			p = run.exec(cmds);
			StreamReader stdout = new StreamReader(p.getInputStream(), showInConsole);
			StreamReader errout = new StreamReader(p.getErrorStream(), showInConsole);
			stdout.start();
			errout.start();
			p.waitFor();

			if (showInConsole) {
				result = null;
			} else {
				result = stdout.getResult() + errout.getResult();
				pos = result.indexOf(COMP_FLAG);
				if (pos != -1) {
					result = result.substring(0, pos);
				}
			}

		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		return result;
	}
}

class StreamReader extends Thread {
	InputStream in;
	StringBuffer buffer = new StringBuffer();
	boolean showInConsole = false;

	StreamReader(InputStream in, boolean showInConsole) {
		this.in = in;
		this.showInConsole = showInConsole;
	}

	public void run() {
		String line;
		InputStreamReader reader = null;
		BufferedReader breader = null;

		try {
			reader = new InputStreamReader(in);
			breader = new BufferedReader(reader);

			while ((line = breader.readLine()) != null) {
				if (line.indexOf(LocalInvoker.COMP_FLAG) >= 0) {
					break;
				}

				if (showInConsole) {
					System.out.println(line);
				} else {
					buffer.append(line).append(LocalInvoker.LINE_SEPARATOR);
				}
			}
		} catch (IOException e) {
			buffer.append("Throw Java IOException: " + e.getMessage());
		} finally {
			try {
				this.in.close();
			} catch (Exception e) {
				buffer.append("Throw Java IOException: " + e.getMessage());
			}
			try {
				if (reader != null)
					reader.close();
			} catch (Exception e) {
				buffer.append("Throw Java IOException: " + e.getMessage());
			}
			try {
				if (breader != null)
					breader.close();
			} catch (Exception e) {
				buffer.append("Throw Java IOException: " + e.getMessage());
			}
		}
	}

	public String getResult() {
		return buffer.toString();
	}
}
