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
package com.navercorp.cubridqa.scheduler.producer;

import java.io.File;
import java.util.ArrayList;
import java.util.concurrent.Callable;

public class FileProcessCaller implements Callable<Boolean> {

	ArrayList<FileItem> itemList;
	File buildFileRoot;
	int buildType;
	Configure conf;

	public FileProcessCaller(Configure conf, File buildFileRoot, String fileFM, int buildType) {
		this.conf = conf;
		this.buildFileRoot = buildFileRoot;
		this.itemList = new ArrayList<FileItem>();
		this.itemList.add(new FileItem(fileFM));
		this.buildType = buildType;
	}
	
	public FileProcessCaller(Configure conf, File buildFileRoot, String filePrefix, String fileSurfix, int buildType) {
		this.conf = conf;
		this.buildFileRoot = buildFileRoot;
		this.itemList = new ArrayList<FileItem>();
		this.itemList.add(new FileItem(filePrefix, fileSurfix));
		this.buildType = buildType;
	}

	public FileProcessCaller(Configure conf, File buildFileRoot, ArrayList<FileItem> itemList, int buildType) {
		this.conf = conf;
		this.buildFileRoot = buildFileRoot;
		this.itemList = itemList;
		this.buildType = buildType;
	}

	FileProcess fileProcess;

	public Boolean call() throws Exception {
		try {
			fileProcess = new FileProcess(conf, buildFileRoot, itemList, buildType);
			fileProcess.process();
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		} finally {
			if (fileProcess != null)
				fileProcess.close();
		}
		return true;
	}

}
