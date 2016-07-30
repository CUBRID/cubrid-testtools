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

package com.navercorp.cubridqa.ha_repl;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.Properties;

public class CheckDiff {
	public int check(String FilePath, String masterName, String slaveOrReplicaFile, String fileSuffix) {
		// String FilePath =
		// "E:/Informations/Projects/CUBRID_HA/HA/ha_repl_fm/NOKFiles_54_user1_09";
		String masterFile = FilePath + "." + masterName + ".dump";
		String slaveFile = FilePath + "." + slaveOrReplicaFile + ".dump";
		String master_slaveOrReplicaDiffFile = FilePath + "." + masterName + "." + slaveOrReplicaFile + "." + fileSuffix;
		String master_slaveOrReplicaDiffFileTemp = master_slaveOrReplicaDiffFile + ".temp";
		String command = "sh -c 'diff " + masterFile + " " + slaveFile + " > " + master_slaveOrReplicaDiffFileTemp + "'";
		String command1 = "sh -c 'diff " + master_slaveOrReplicaDiffFile + " " + master_slaveOrReplicaDiffFileTemp + "'";
		int result = 0;
		command = command.replace("\\", "/");
		command1 = command1.replace("\\", "/");

		Properties prop = new Properties(System.getProperties());
		String OS = prop.getProperty("os.name");

		if (OS.contains("Windows")) {
			if (ExecCommand(command) != 0) {
				result = ExecCommand(command1);
			}
		} else if (OS.contains("Linux")) {
			File file = new File(FilePath + ".sh");
			try {
				OutputStream out;
				out = new FileOutputStream(file);
				String str = command;
				byte[] b = str.getBytes();
				out.write(b);
				out.close();
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
			command = "sh " + FilePath + ".sh";
			if (ExecCommand(command) != 0) {
				File file1 = new File(FilePath + "1.sh");
				try {
					OutputStream out;
					out = new FileOutputStream(file1);
					String str = command1;
					byte[] b = str.getBytes();
					out.write(b);
					out.close();
				} catch (FileNotFoundException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				}
				command1 = "sh " + FilePath + "1.sh";
				result = ExecCommand(command1);
				file1.delete();
			}
			file.delete();
		} else // other OS
		{
			result = -1;
		}
		new File(master_slaveOrReplicaDiffFileTemp).delete();
		return result;
	}

	private int ExecCommand(String command) {
		int exitVal = 0;
		try {
			Runtime rt = Runtime.getRuntime();
			Process proc;
			proc = rt.exec(command);
			// any error message?
			StreamGobbler errorGobbler = new StreamGobbler(proc.getErrorStream(), "ERROR");

			// any output?
			StreamGobbler outputGobbler = new StreamGobbler(proc.getInputStream(), "OUTPUT");

			// kick them off
			errorGobbler.start();
			outputGobbler.start();

			// any error???
			exitVal = proc.waitFor();
			System.out.println("ExitValue: " + exitVal);
		} catch (IOException e) {
			e.printStackTrace();
			exitVal = -1;
		} catch (InterruptedException e) {
			e.printStackTrace();
			exitVal = -1;
		}
		return exitVal;
	}

	class StreamGobbler extends Thread {
		InputStream is;
		String type;

		StreamGobbler(InputStream is, String type) {
			this.is = is;
			this.type = type;
		}

		public void run() {
			try {
				InputStreamReader isr = new InputStreamReader(is);
				BufferedReader br = new BufferedReader(isr);
				String line = null;
				while ((line = br.readLine()) != null)
					System.out.println(type + ">" + line);
			} catch (IOException ioe) {
				ioe.printStackTrace();
			}
		}
	}

	public static void main(String[] args) {
		CheckDiff checkDiff = new CheckDiff();
		checkDiff.check("E:\\Informations\\Projects\\CUBRID_HA\\HA\\ha_repl_fm\\.\\test\\sql\\_02_user_authorization\\_02_authorization\\_002_revoke\\cases\\1061", "master", "slave1", "diff_1");
	}
}
