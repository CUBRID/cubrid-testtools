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
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.navercorp.cubridqa.scheduler.common.CommonUtils;
import com.navercorp.cubridqa.scheduler.common.Constants;

public class Producer {

	Configure conf;
	Observer observer;

	public Producer(Configure conf) throws IOException {
		this.conf = conf;
		this.observer = new Observer(conf);
	}

	public void startup() {

		ArrayList<File> freshBuildList;

		while (true) {
			CommonUtils.sleep(1);
			freshBuildList = observer.getFreshBuilds();
			for (int i = 0; i < freshBuildList.size(); i++) {
				try {
					processBuild(freshBuildList.get(i));
				} catch (Exception e) {
					e.printStackTrace();
				}
			}

			if (conf.isGenerateMessage() == false) {
				break;
			}
		}
	}

	private void processBuild(File buildFile) throws InterruptedException, IOException {
		System.out.println("New Build: " + buildFile.getPath());
		String buildId = buildFile.getName();
		boolean delivered = false;
		if (conf.isGenerateMessage() || conf.isGenerateMessageTest()) {
			if (conf.isAcceptableBuildToListen(buildId)) {
				delivered = true;
				deliverTasksMessages(buildFile);
			}
		}

		if (conf.isGenerateMessageTest() == false) {
			MessageFinished.getInstantce().clear();
			observer.finishedBuild(delivered, buildFile.getName());
		}

		System.out.println("Finished for " + buildFile.getPath());
		System.out.println();
	}

	private void deliverTasksMessages(File buildDir) throws InterruptedException, IOException {

		File buildFileRoot = new File(CommonUtils.concatFile(buildDir.getPath(), "drop"));

		ExecutorService pool = Executors.newCachedThreadPool();
		ArrayList<Callable<Boolean>> callers = new ArrayList<Callable<Boolean>>();

		Compatibility.init(conf);
		I18N.init(conf);
		CompatDatabaseImage.init(conf, "compat_dbimg.conf");

		FileProcessCaller task;

		task = new FileProcessCaller(conf, buildFileRoot, "CUBRID-{1}-linux.x86_64.sh", Constants.BUILD_TYPE_SERVER_SH_LINUX_X86_64);
		callers.add(task);

		ArrayList<FileItem> itemList = new ArrayList<FileItem>();
		itemList.add(new FileItem("CUBRID-{1}-linux.x86_64.sh"));
		itemList.add(new FileItem("JDBC-{1}-cubrid-src.jar"));
		task = new FileProcessCaller(conf, buildFileRoot, itemList, Constants.BUILD_TYPE_SERVER_SH_LINUX_X86_64_AND_JDBC);
		callers.add(task);

		task = new FileProcessCaller(conf, buildFileRoot, "CUBRID-{1}-linux.x86_64-debug.sh", Constants.BUILD_TYPE_SERVER_SH_LINUX_X86_64_DEBUG);
		callers.add(task);

		task = new FileProcessCaller(conf, buildFileRoot, "CUBRID-{1}-linux.i386.sh", Constants.BUILD_TYPE_SERVER_SH_LINUX_I386);
		callers.add(task);

		task = new FileProcessCaller(conf, buildFileRoot, "CUBRID-Windows-x64-{1}.zip", Constants.BUILD_TYPE_SERVER_ZIP_WINDOWS_X64);
		callers.add(task);

		task = new FileProcessCaller(conf, buildFileRoot, "CUBRID-Windows-x86-{1}.zip", Constants.BUILD_TYPE_SERVER_ZIP_WINDOWS_X86);
		callers.add(task);

		// task = new FileProcessCaller(conf, buildFileRoot, "CUBRID-{1}",
		// "-AIX-ppc.sh", Constants.BUILD_TYPE_SERVER_SH_AIX_64);
		task = new FileProcessCaller(conf, buildFileRoot, "CUBRID-{1}-AIX-ppc64.sh", Constants.BUILD_TYPE_SERVER_SH_AIX_64);
		callers.add(task);

		pool.invokeAll(callers);
		pool.shutdown();

	}
}
