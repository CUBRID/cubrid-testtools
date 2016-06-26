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


import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;

public class MakeFile {

	PrintWriter pw = null;

	String fileName;
	String filePath;

	boolean echo = false;

	public MakeFile(String fileName, boolean echo) {
		this(fileName, echo, false);
	}

	public MakeFile(String fileName, boolean echo, boolean append) {

		this.echo = echo;
		this.fileName = fileName;

		File file = new File(fileName);

		if (!file.exists()) {
			try {
				file.getParentFile().mkdirs();
				file.createNewFile();
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}

		// add to get file path
		try {
			filePath = file.getCanonicalPath();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		try {
			pw = new PrintWriter(new FileOutputStream(file, append), true);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

	}

	public void println(String msg) {
		println(msg, true);
	}

	public void print(String msg) {
		println(msg, false);
	}

	public void log(String msg) {
		println(msg, true);
	}

	public void println(String msg, boolean newLine) {
		if (msg == null)
			return;
		if (echo)
			System.out.println(msg);
		try {
			pw.write(msg);
		} catch (Exception e) {
			throw new RuntimeException(e);
		} finally {
			try {
				if (newLine)
					pw.write(File.separator);
				pw.flush();
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}
	}

	public String getFileName() {
		return this.fileName;
	}

	public String getFilePath() {
		return filePath;
	}

	public void close() {
		if (pw != null) {
			try {
				pw.flush();
				pw.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
}
