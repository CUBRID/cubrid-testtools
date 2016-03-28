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
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.CountDownLatch;

public class LocalInvoker {

	public static final String LINE_SEPARATOR = "\n";

	public static final String START_FLAG = "'CUBRID'_'COMMON'_'STARTED'";
	public static final String COMP_FLAG = "'CUBRID'_'COMMON'_'COMPLETED'";

	public static final String START_FLAG_RESULT = "CUBRID_COMMON_STARTED";
	public static final String COMP_FLAG_RESULT = "CUBRID_COMMON_COMPLETED";

	public static String exec(String... cmds) {
		StringBuilder sb = new StringBuilder();
		for (String s : cmds) {
			sb.append(exec(s));
		}
		return sb.toString();
	}

	public static String exec(String cmds) {
		boolean isWindows = System.getProperty("os.name").toUpperCase().indexOf("WINDOWS") != -1;

		File tmpFile;
		try {
			tmpFile = File.createTempFile(".localexec", isWindows ? ".bat" : ".sh");
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		FileWriter writer = null;
		try {
			writer = new FileWriter(tmpFile);
			writer.write("echo " + START_FLAG + Constants.LINE_SEPARATOR);			
			writer.write(cmds + Constants.LINE_SEPARATOR);
			writer.write("echo " + COMP_FLAG + Constants.LINE_SEPARATOR);
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

		return execPlainShell(tmpFile.getAbsolutePath(), isWindows);
	}

	private static String execPlainShell(String scriptFilename, boolean isWindows) {
		//System.out.println(scriptFilename);
		Runtime run = Runtime.getRuntime();
		Process p = null;
		InputStream in = null;
		int pos;

		String result;

		try {
			if (isWindows) {
				p = run.exec(scriptFilename + " 2>&1");
			} else {
				p = run.exec("sh " + scriptFilename + " 2>&1");
			}

			CountDownLatch endLatch = new CountDownLatch(2);
			StreamGobbler stdout = new StreamGobbler(endLatch, p.getInputStream());
			StreamGobbler errout = new StreamGobbler(endLatch, p.getErrorStream());
			stdout.start();
			errout.start();
			endLatch.await();

			result = stdout.getResult() + errout.getResult();
			pos = result.indexOf(START_FLAG_RESULT);
			if (pos != -1) {
				result = result.substring(pos + START_FLAG_RESULT.length());
			}
			pos = result.indexOf(COMP_FLAG_RESULT);
			if (pos != -1) {
				result = result.substring(0, pos);
			}

		} catch (Exception e) {
			throw new RuntimeException(e);
		} finally {
			if (in != null) {
				try {
					in.close();
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			}
		}
		return result.toString();
	}
}

class StreamGobbler extends Thread {
	InputStream in;
	StringBuffer buffer = new StringBuffer();
	
	CountDownLatch endLatch;

	StreamGobbler(CountDownLatch endLatch, InputStream in) {
		this.in = in;
		this.endLatch = endLatch;
	}

	public void run() {
		int len;
		byte[] b = new byte[1024];
		String cont;

		try {
			while ((len = in.read(b)) > 0) {
				cont = new String(b, 0, len);
				buffer.append(cont);
				if (buffer.toString().indexOf(LocalInvoker.COMP_FLAG_RESULT) > 0) {
					break;
				}
			}
		} catch (IOException e) {
			buffer.append("Throw Java IOException: " + e.getMessage());
		}
		this.endLatch.countDown();
	}

	public String getResult() {
		return buffer.toString();
	}
}
