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

import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;

import com.navercorp.cubridqa.scheduler.common.CommonUtils;
import com.navercorp.cubridqa.scheduler.common.Constants;
import com.navercorp.cubridqa.scheduler.common.HttpUtil;
import com.navercorp.cubridqa.scheduler.common.Log;

public class Observer {

	Configure conf;
	ArrayList<String> finishedList;
	String finishedFilename;

	public Observer(Configure conf) throws IOException {
		this.conf = conf;
		this.finishedFilename = CommonUtils.concatFile(Constants.CTP_HOME + File.separator + "conf", "producer_finished.txt");
		File finishedFile = new File(finishedFilename);
		if(finishedFile.exists() == false) finishedFile.createNewFile();
		this.finishedList = this.getFinishedBuilds();
	}

	public ArrayList<File> getFreshBuilds() {
		
		ArrayList<File> freshList = new ArrayList<File>();
		
		String root = conf.getRepoRoot();

		File rootFile = new File(root);
		File[] storeList = rootFile.listFiles(new FilenameFilter() {
			@Override
			public boolean accept(File dir, String name) {
				return name.startsWith("store_");
			}
		});

		File[] buildList;
		File dropFile;
		String buildName;
		for (int i = 0; i < storeList.length; i++) {
			buildList = storeList[i].listFiles();
			for (int j = 0; j < buildList.length; j++) {
				dropFile = new File(CommonUtils.concatFile(buildList[j].getPath(), "drop"));
				if(!dropFile.exists()) continue;
				
				buildName = buildList[j].getName();
				
				if(isBuildFinished(buildName)) continue;
				
				//buildName is fresh build.
				freshList.add(buildList[j]);
			}
		}
		return freshList;
	}

	public ArrayList<String> getFinishedBuilds() throws IOException {
		ArrayList<String> finishedList = CommonUtils.getLineList(finishedFilename);
		return finishedList;
	}
	
	public boolean isBuildFinished(String buildName) {
		return this.finishedList.contains(buildName);
	}
	
	public void finishedBuild(boolean delivered, String buildName) {
		finishedList.add(buildName);
		
		if(delivered) {
			String url = conf.getProperty("url.notice_new_build");
			if (url != null) {
				try {
					url = CommonUtils.replace(url, "{buildId}", buildName);
					HttpUtil.getHtmlSource(url);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
		
		Log log = new Log(this.finishedFilename, false, true);
		log.println(buildName);
		log.close();
	}
}
