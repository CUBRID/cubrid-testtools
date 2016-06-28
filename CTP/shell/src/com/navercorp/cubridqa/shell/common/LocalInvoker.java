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

package com.navercorp.cubridqa.shell.common;


import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class LocalInvoker {
	
	public static String exec(String cmds, boolean pureWindows, boolean isDebug) {
		File tmpFile;
		try {
			tmpFile = File.createTempFile(".localexec", pureWindows ? ".bat": ".sh");
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		
		FileWriter writer = null;
		try {
			writer = new FileWriter(tmpFile);
			writer.write(cmds);
			writer.flush();
		} catch (IOException e) {
			throw new RuntimeException(e);
		} finally{
			if(writer!=null) {
				try {
					writer.close();
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			}
		}
		
		if (isDebug) {
			System.out.println("Script File: " + tmpFile);
		}
		
		String result = execPlainShell(tmpFile.getAbsolutePath(), pureWindows, isDebug);
		try{
			tmpFile.delete();
		} catch(Exception e) {
			System.out.println("Fail to delete " + tmpFile + " but NO HARM");
		}
		return result;
	}
	
	private static String execPlainShell(String scriptFilename, boolean pureWindows, boolean isDebug) {
		String cmds;
		String pathConversionResult = null;
		if (pureWindows) {
			cmds = scriptFilename + " 2>&1";
		} else {
			while (true) {
				pathConversionResult = execCommands("cygpath " + scriptFilename, false);
				if (pathConversionResult != null && pathConversionResult.trim().length() > 0) {
					break;
				}
			}
			
			cmds = "bash.exe -c " + pathConversionResult.trim() + " 2>&1 \"";
		}
		return execCommands(cmds, true);		
	}

	private static String execCommands(String cmds, boolean isDebug) {
		Runtime run = Runtime.getRuntime();
		Process p = null;
		int pos;

		String result;

		try {			
			p = run.exec(cmds);
            StreamGobbler stdout = new StreamGobbler(p.getInputStream());
            StreamGobbler errout = new StreamGobbler(p.getErrorStream());
            stdout.start();
			errout.start();
			p.waitFor();
			
			result = stdout.getResult() + errout.getResult();
			if(isDebug) {
				System.out.println(result);
			}
			pos = result.indexOf(ShellInput.START_FLAG);
			if (pos != -1) {
				result = result.substring(pos + ShellInput.START_FLAG.length());
			}
			pos = result.indexOf(ShellInput.COMP_FLAG);
			if (pos != -1) {
				result = result.substring(0, pos);
			}
			  
		} catch (Exception e) {  
			throw new RuntimeException(e);  
		}
		return result.toString();
	}
	
	public static void main(String[] args) {
		System.out.println(exec(Constants.WIN_KILL_PROCESS_NATIVE, true, true));
	}
}

class StreamGobbler extends Thread {
	InputStream in;
	StringBuffer buffer = new StringBuffer();

	StreamGobbler(InputStream in) {
		this.in = in;
	}

	public void run() {
		String line;
		InputStreamReader reader = null;
		BufferedReader breader = null;
		
		try {
			reader = new InputStreamReader(in);
			breader = new BufferedReader(reader);
			
			while ((line = breader.readLine()) != null) {
				buffer.append(line).append(Constants.LINE_SEPARATOR);
				if (buffer.toString().indexOf(ShellInput.COMP_FLAG) > 0) {
					break;
				}
			}
		} catch (IOException e) {
			buffer.append("Throw Java IOException: " + e.getMessage());
		} finally{
			try{
				this.in.close();
			} catch(Exception e) {
				buffer.append("Throw Java IOException: " + e.getMessage());
			}
			try{
				if(reader != null ) reader.close();
			} catch(Exception e) {
				buffer.append("Throw Java IOException: " + e.getMessage());
			}
			try{
				if(breader != null ) breader.close();
			} catch(Exception e) {
				buffer.append("Throw Java IOException: " + e.getMessage());
			}			
		}
	}
	
	public String getResult() {
		return buffer.toString();
	}
}
