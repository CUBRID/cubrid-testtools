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
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class ShellFileMaker {

	private static int processValue;

	private static List list = new ArrayList();
	private static Process proces;

	/**
	 * read file
	 * 
	 * @param filename
	 * @return
	 */
	public static String reader(String filename, int type) {
		try {
			StringBuffer context = new StringBuffer("");
			BufferedReader br = null;
			if (type == 0) {
				InputStream ip = ShellFileMaker.class.getClassLoader().getResourceAsStream(filename);
				InputStreamReader sr = new InputStreamReader(ip);
				br = new BufferedReader(sr);
			} else {
				File file = new File(filename);
				FileReader fr = new FileReader(file);
				br = new BufferedReader(fr);
			}
			String temp = br.readLine();
			while (temp != null) {
				context = context.append(temp + "\n");
				temp = br.readLine();
			}
			br.close();

			return context.toString();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}

	/**
	 * setup shell head
	 * 
	 * @param value
	 * @return
	 */
	public static StringBuffer makeExpectShellHead() {
		StringBuffer context = new StringBuffer(SystemConst.EXPECT_HEAD);
		context.append("set timeout 20\r");
		context.append("spawn $env(SHELL)\r");
		return context;
	}

	public static StringBuffer makeExpectShellEnd(StringBuffer context) {
		context.append("expect eof\n");
		context.append("exit 0\n");
		return context;
	}

	/**
	 * write file
	 * 
	 * @param context
	 * @param path
	 * @return
	 */
	public static String writer(String context, String path) {
		try {
			File file = new File(path);
			FileWriter fw = new FileWriter(file);
			BufferedWriter bw = new BufferedWriter(fw);
			bw.write(context);
			bw.close();
			fw.close();
			return file.getAbsolutePath();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}

	/**
	 * remove file
	 * 
	 * @param path
	 * @return
	 */
	public static boolean removeFile(String path) {
		File file = new File(path);
		try {
			if (file.exists()) {
				file.delete();
				return true;
			}
		} catch (Exception e) {
			// TODO: handle exception
			e.printStackTrace();
		}
		return false;
	}

	/**
	 * check if the file is existed
	 * 
	 * @param filename
	 * @return
	 */
	public static boolean isExist(String filename) {
		File file = new File(filename);
		return file.exists();
	}

	/**
	 * execute shell
	 * 
	 * @param command
	 */
	public static int executeShell(String[] command) {
		try {
			proces = Runtime.getRuntime().exec(command);
			StreamGobbler gobbler = new StreamGobbler();

			list.clear();
			gobbler.setList(list);
			gobbler.setProcess(proces);
			Thread th2 = new Thread(gobbler);

			th2.start();
			th2.join();

			int value = proces.waitFor();

			return processValue;
		} catch (IOException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		return -1;
	}

	static class ProcessThread implements Runnable {
		String[] command;

		public String[] getCommand() {
			return command;
		}

		public void setCommand(String[] command) {
			this.command = command;
		}

		Process process;

		public void run() {
			try {
				for (String string : command) {
					System.out.println(string);
				}
				this.process = Runtime.getRuntime().exec(command);
				this.process.waitFor();
				proces = this.process;
				processValue = this.process.exitValue();
			} catch (IOException e) {

				e.printStackTrace();
			} catch (InterruptedException e) {

				e.printStackTrace();
			}
		}

	}

	public static List getList() {
		return list;
	}

}
