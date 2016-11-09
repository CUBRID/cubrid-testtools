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

package com.navercorp.cubridqa.scheduler.common;

import java.io.File;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.Properties;

import javax.jms.JMSException;

public class FileReady {

	File file;
	long lastSizeForSingle = -1;
	long lastTimestampForSingle = -1;

	long lastSizeForAll = -1;
	long lastTimestampForAll = -1;
	String logFilename;

	public FileReady(File file) {
		this(file, false);
	}

	public FileReady(File file, boolean saveLog) {
		this.file = file;
		logFilename = CommonUtils.concatFile(file.getParentFile().getAbsolutePath(), ".job.log");
	}

	private void saveLog(String fname) {
		Log log = new Log(logFilename, false, true);
		log.println("fn." + fname + "=1");
		log.close();
	}

	public void waitFile() throws JMSException, NoSuchAlgorithmException, IOException {
		Properties props;
		String v;
		while (true) {

			props = CommonUtils.getProperties(logFilename);
			if (props == null) {
				props = new Properties();
			}
			v = props.getProperty("fn." + file.getName());
			if (v != null) {
				return;
			} else {
				v = props.getProperty("fn.all");
				if (v != null) {
					return;
				}
			}

			if (isExactFileCompleted()) {
				saveLog(file.getName());
				break;
			}
			if (isAllFilesCompleted()) {
				saveLog("all");
				break;
			}
			CommonUtils.sleep(10);
		}
	}

	private boolean isExactFileCompleted() {
		long curTime = System.currentTimeMillis();

		long currentSize = 0;

		if (!file.exists()) {
			return false;
		}
		currentSize += file.length();

		if (currentSize == this.lastSizeForSingle) {
			return ((curTime - this.lastTimestampForSingle) > 120 * 1000);
		} else {
			this.lastSizeForSingle = currentSize;
			this.lastTimestampForSingle = curTime;
			return false;
		}
	}

	private boolean isAllFilesCompleted() {
		File[] files = file.getParentFile().listFiles();
		long curTime = System.currentTimeMillis();
		long currentSize = 0;

		for (File f : files) {
			currentSize += f.length();
		}

		if (currentSize == this.lastSizeForAll) {
			return ((curTime - this.lastTimestampForAll) > 120 * 1000);
		} else {
			this.lastSizeForAll = currentSize;
			this.lastTimestampForAll = curTime;
			return false;
		}
	}
}
