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
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Properties;

import javax.jms.JMSException;

import com.navercorp.cubridqa.scheduler.common.CommonUtils;
import com.navercorp.cubridqa.scheduler.common.Constants;
import com.navercorp.cubridqa.scheduler.common.Message;
import com.navercorp.cubridqa.scheduler.common.SendMessage;

public class FileProcess {

	ArrayList<FileItem> itemList;
	File buildFileRoot;
	SendMessage sendMsg;
	int buildPkgType;
	String pkgPattern;
	Configure conf;
	String storeId;
	String buildId;
	
	long lastSizeForSingle = -1;
	long lastTimestampForSingle = -1;

	long lastSizeForAll = -1;
	long lastTimestampForAll = -1;

	public FileProcess(Configure conf, File buildFileRoot, ArrayList<FileItem> itemList, int buildType) {
		this.conf = conf;
		this.buildFileRoot = buildFileRoot;
		this.itemList = itemList;
		this.buildPkgType = buildType;
		this.pkgPattern = itemList.get(0).getFilePattern();
		this.sendMsg = new SendMessage(this.conf.props);

		storeId = buildFileRoot.getParentFile().getParentFile().getName();
		buildId = buildFileRoot.getParentFile().getName();
	}

	public void process() throws JMSException, NoSuchAlgorithmException, IOException {
		boolean gotallfile = false;
		while (true) {
			if (isExactBuildCompleted()) {
				gotallfile = true;
				break;
			}
			if (isAllBuildCompleted()) {
				break;
			}

			CommonUtils.sleep(60);
		}

		if (gotallfile == false) {
			System.out.println("ERROR AS BELOW:");
			System.out.println("BUILD_ID=" + buildId);
			System.out.println("BUILD_TYPE=" + buildPkgType);
			System.out.println("BUILD_ROOT=" + buildFileRoot.getPath());
			for (int i = 0; i < itemList.size(); i++) {
				System.out.println("BUILD_FILES." + i + "=" + itemList.get(i));
			}
			System.out.println();
			return;
		}

		switch (buildPkgType) {
		case Constants.BUILD_TYPE_SERVER_SH_LINUX_X86_64: {
			sendMessage(Constants.QUEUE_CUBRID_QA_SQL_PERF_LINUX, 64, "sql", "general");
			sendMessage(Constants.QUEUE_CUBRID_QA_SQL_LINUX, 64, "medium", "general");
			sendMessage(Constants.QUEUE_CUBRID_QA_SQL_LINUX, 64, "site", "general");

			sendMessage(Constants.QUEUE_CUBRID_QA_SQL_CCI_LINUX, 64, "sql", "general");
			
			sendMessage(Constants.QUEUE_CUBRID_QA_SHELL_LINUX, 64, "shell", "general");
			//sendMessage(Constants.QUEUE_CUBRID_QA_HA_REPL_LINUX, 64, "ha_repl", "general");
			sendMessage(Constants.QUEUE_CUBRID_QA_HA_REPL_PERF_LINUX, 64, "ha_repl_perf", "general");

			sendMessage(Constants.QUEUE_CUBRID_QA_YCSB_LINUX, 64, "ycsb", "general");
			sendMessage(Constants.QUEUE_CUBRID_QA_SYSBENCH_LINUX, 64, "sysbench", "general");
			//sendMessage(Constants.QUEUE_CUBRID_QA_DOTS_LINUX, 64, "dots", "general");
			sendMessage(Constants.QUEUE_CUBRID_QA_TPCC_LINUX, 64, "tpc-c", "general");
			sendMessage(Constants.QUEUE_CUBRID_QA_TPCW_LINUX, 64, "tpc-w", "general");

			sendMessage(Constants.QUEUE_CUBRID_QA_MEMORY_LEAK_LINUX, 64, "memoryleak", "general");
			
			sendMessage(Constants.QUEUE_CUBRID_QA_BASIC_PERF_LINUX, 64, "basic_perf", "general");
			
			sendMessage(Constants.QUEUE_CUBRID_QA_NBD_LINUX, 64, "nbd", "general");

			sendMessage(Constants.QUEUE_CUBRID_QA_CCI_LINUX, 64, "cci", "general");

			//sendMessage(Constants.QUEUE_CUBRID_QA_SHELL_HA_LINUX, 64, "shell_ha", "general");
			
			sendMessage(Constants.QUEUE_CUBRID_QA_CAS4MYSQL_NTEST, 64, "cas4mysql_ntest", "general");
			sendMessage(Constants.QUEUE_CUBRID_QA_CAS4ORACLE_NTEST, 64, "cas4oracle_ntest", "general");
			sendMessage(Constants.QUEUE_CUBRID_QA_CAS4MYSQL_UNITTEST, 64, "cas4mysql_unit", "general");
			sendMessage(Constants.QUEUE_CUBRID_QA_CAS4ORACLE_UNITTEST, 64, "cas4oracle_unit", "general");

			//sendMessage(Constants.QUEUE_CUBRID_QA_SHARD_LINUX, 64, "shard", "general");
			
//			String mainVersion = getVersion(buildId);
//			
//			sendI18NMessage(Constants.QUEUE_CUBRID_QA_I18N_LINUX_64, 64, "i18n", "general", "i18n_for_" + mainVersion);
//			
//			sendMessage(Constants.QUEUE_CUBRID_QA_COMPAT_JDBC_SQL_DRIVER, 64, "compat_jdbc", "general", "jdbc_compatibility_for_"+mainVersion+"_D");
//			sendMessage(Constants.QUEUE_CUBRID_QA_COMPAT_JDBC_SQL_SERVER_64, 64, "compat_jdbc", "general", "jdbc_compatibility_for_"+mainVersion+"_S64");
//
//			sendMessage(Constants.QUEUE_CUBRID_QA_COMPAT_CCI_SHELL_DRIVER_64, 64, "compat_cci", "general", "cci_compatibility_for_"+mainVersion+"_D64");
//			sendMessage(Constants.QUEUE_CUBRID_QA_COMPAT_CCI_SHELL_SERVER_64, 64, "compat_cci", "general", "cci_compatibility_for_"+mainVersion+"_S64");
//			
//			sendDBImageMessage(Constants.QUEUE_CUBRID_QA_COMPAT_DBIMG_SRV, 64, "compat_dbimg", "general", "dbimg_for_"+mainVersion+"_SRV");
//			sendDBImageMessage(Constants.QUEUE_CUBRID_QA_COMPAT_DBIMG_INS, 64, "compat_dbimg", "general", "dbimg_for_"+mainVersion+"_INS");

			break;
		}
		case Constants.BUILD_TYPE_SERVER_SH_AIX_64: {
			sendMessage(Constants.QUEUE_CUBRID_QA_SQL_AIX_64, 64, "sql", "general");
			sendMessage(Constants.QUEUE_CUBRID_QA_SQL_AIX_64, 64, "medium", "general");
			sendMessage(Constants.QUEUE_CUBRID_QA_SQL_AIX_64, 64, "site", "general");
			sendMessage(Constants.QUEUE_CUBRID_QA_SHELL_AIX_64, 64, "shell", "general");
			sendMessage(Constants.QUEUE_CUBRID_QA_NBD_AIX_64, 64, "nbd", "general");
			sendMessage(Constants.QUEUE_CUBRID_QA_CCI_AIX_64, 64, "cci", "general");
			break;
		}

		case Constants.BUILD_TYPE_SERVER_SH_LINUX_X86_64_DEBUG: {
			sendMessage(Constants.QUEUE_CUBRID_QA_SQL_LINUX, 64, "sql", "debug");
			sendMessage(Constants.QUEUE_CUBRID_QA_SQL_LINUX, 64, "medium", "debug");
			sendMessage(Constants.QUEUE_CUBRID_QA_SQL_LINUX, 64, "site", "debug");
//			sendMessage(Constants.QUEUE_CUBRID_QA_SHELL_LINUX, 64, "shell", "debug");
			sendMessage(Constants.QUEUE_CUBRID_QA_CCI_LINUX, 64, "cci", "debug");
			break;
		}
		case Constants.BUILD_TYPE_SERVER_SH_LINUX_I386: {
			sendMessage(Constants.QUEUE_CUBRID_QA_SQL_PERF_LINUX, 32, "sql", "general");
			sendMessage(Constants.QUEUE_CUBRID_QA_SQL_LINUX, 32, "medium", "general");
			sendMessage(Constants.QUEUE_CUBRID_QA_SQL_LINUX, 32, "site", "general");
			sendMessage(Constants.QUEUE_CUBRID_QA_SHELL_LINUX, 32, "shell", "general");
			sendMessage(Constants.QUEUE_CUBRID_QA_NBD_LINUX, 32, "nbd", "general");
			sendMessage(Constants.QUEUE_CUBRID_QA_BASIC_PERF_LINUX, 32, "basic_perf", "general");
			break;
		}
		case Constants.BUILD_TYPE_SERVER_ZIP_WINDOWS_X64: {
			sendMessage(Constants.QUEUE_CUBRID_QA_SQL_WIN64, 64, "sql", "general");
			sendMessage(Constants.QUEUE_CUBRID_QA_SQL_WIN64, 64, "medium", "general");
			sendMessage(Constants.QUEUE_CUBRID_QA_SQL_WIN64, 64, "site", "general");
			sendMessage(Constants.QUEUE_CUBRID_QA_SHELL_WIN64, 64, "shell", "general");
			sendMessage(Constants.QUEUE_CUBRID_QA_NBD_WIN64, 64, "nbd", "general");
			break;
		}
		case Constants.BUILD_TYPE_SERVER_ZIP_WINDOWS_X86: {
			sendMessage(Constants.QUEUE_CUBRID_QA_SQL_WIN32, 32, "sql", "general");
			sendMessage(Constants.QUEUE_CUBRID_QA_SQL_WIN32, 32, "medium", "general");
			sendMessage(Constants.QUEUE_CUBRID_QA_SQL_WIN32, 32, "site", "general");
			sendMessage(Constants.QUEUE_CUBRID_QA_SHELL_WIN32, 32, "shell", "general");
			sendMessage(Constants.QUEUE_CUBRID_QA_NBD_WIN32, 32, "nbd", "general");
			break;
		}
		
		case Constants.BUILD_TYPE_SERVER_SH_LINUX_X86_64_AND_JDBC: {
			sendMessage(Constants.QUEUE_CUBRID_QA_JDBC_UNITTEST_LINUX, 64, "jdbc_unit", "general");
		}

		}

		if (conf.isGenerateMessage()) {
			sendMsg.send();
		}
	}

	public void close() {

	}

	private void sendMessage(String queue, int bits, String scenarios, String buildType) throws NoSuchAlgorithmException, IOException {
		sendMessage(queue, bits, scenarios, 4, buildType, null);
	}
	
//	private void sendMessage(String queue, int bits, String scenarios, String buildType, String compatKey) throws NoSuchAlgorithmException, IOException {
//		ArrayList<Properties> list = Compatibility.getInstance().getMsgProperties(compatKey);
//		if(list==null) {
//			System.out.println("[ERROR] queue="+ queue + ", bits=" + bits + ", scenarios=" + scenarios + ", buildType=" + buildType + ", compatKey=" + compatKey);
//			return;
//		}
//		for(Properties props: list){
//			sendMessage(queue, bits, scenarios, 4, buildType, props);	
//		}
//	}
//	
//	private void sendDBImageMessage(String queue, int bits, String scenarios, String buildType, String compatKey) throws NoSuchAlgorithmException, IOException {
//		ArrayList<Properties> list = CompatDatabaseImage.getInstance().getMsgProperties(compatKey);
//		if(list==null) {
//			System.out.println("[ERROR] queue="+ queue + ", bits=" + bits + ", scenarios=" + scenarios + ", buildType=" + buildType + ", compatKey=" + compatKey);
//			return;
//		}
//		for(Properties props: list){
//			sendMessage(queue, bits, scenarios, 4, buildType, props);
//		}
//	}
//	
//	private void sendI18NMessage(String queue, int bits, String scenarios, String buildType, String i18nKey) throws NoSuchAlgorithmException, IOException {
//		ArrayList<Properties> list = I18N.getInstance().getMsgProperties(i18nKey);
//		
//		if(list==null) return;
//		
//		for(Properties props: list){
//			sendMessage(queue, bits, scenarios, 4, buildType, props);	
//		}
//	}
//	
//	private void sendMessage(String queue, int bits, String scenarios, String buildType, Properties additionalProps) throws NoSuchAlgorithmException, IOException {
//		sendMessage(queue, bits, scenarios, 4, buildType, additionalProps);
//	}

	private void sendMessage(String queue, int bits, String scenarios, int priority, String buildType, Properties additionalProps) throws NoSuchAlgorithmException, IOException {
		
		long curTime = System.currentTimeMillis();
		Message message = new Message(queue, "Test for build " + buildId + " by CUBRID QA Team, China");

		String urls = null;
		String urls_kr = null;
		long maxCreateTime = 0;
		for (int i = 0; i < itemList.size(); i++) {
			urls = conf.getWebBaseUrl() + "/" + storeId + "/" + buildId + "/drop/" + itemList.get(i).getExactFile().getName();
			urls_kr = conf.getWebBaseUrl_Kr() + "/" + buildId + "/drop/" + itemList.get(i).getExactFile().getName();
			if (itemList.get(i).getExactFile().lastModified() > maxCreateTime) {
				maxCreateTime = itemList.get(i).getExactFile().lastModified();
			}
			
			if(i==0) {
				message.setProperty(Constants.MSG_BUILD_URLS, urls);
				message.setProperty(Constants.MSG_BUILD_URLS_KR, urls_kr);
			} else {
				message.setProperty(Constants.MSG_BUILD_URLS + "_" + ( i + 1 ), urls);
				message.setProperty(Constants.MSG_BUILD_URLS_KR + "_" + ( i + 1 ), urls_kr);
			}
		}

		message.setProperty(Constants.MSG_BUILD_URLS_CNT, String.valueOf(itemList.size()));
		message.setProperty(Constants.MSG_BUILD_ID, buildId);
		message.setProperty(Constants.MSG_BUILD_STORE_ID, storeId);
		message.setProperty(Constants.MSG_BUILD_BIT, String.valueOf(bits));
		message.setProperty(Constants.MSG_BUILD_CREATE_TIME, String.valueOf(maxCreateTime));
		message.setProperty(Constants.MSG_BUILD_SEND_TIME, String.valueOf(curTime));
		message.setProperty(Constants.MSG_BUILD_SEND_DELAY, String.valueOf((curTime - maxCreateTime) / 1000));
		message.setProperty(Constants.MSG_BUILD_SCENARIOS, (scenarios == null ? "" : scenarios));
		message.setProperty(Constants.MSG_BUILD_SVN_BRANCH, CommonUtils.getSVNBranch(conf.getProperty("svn.trunk.version"), this.buildId));
		message.setProperty(Constants.MSG_BUILD_SVN_BRANCH_NEW, CommonUtils.getSVNBranchIgnorePatch(conf.getProperty("svn.trunk.version"), this.buildId));
		message.setProperty(Constants.MSG_BUILD_TYPE, buildType);
		message.setProperty(Constants.MSG_BUILD_PACKAGE_PATTERN, String.valueOf(this.pkgPattern));
		message.setProperty(Constants.MSG_BUILD_GENERATE_MSG_WAY, "AUTO");
		
		if(additionalProps!=null) message.putAll(additionalProps);

		String pri = conf.getProperty("msg.priority.for_" + CommonUtils.getVersion(this.buildId), "4");
		message.setPriority(Integer.parseInt(pri));

		System.out.println(message);
		String md5 = message.getMD5();
		System.out.println("MD5:" + md5);

		if (conf.isGenerateMessage() && !MessageFinished.getInstantce().exists(md5)) {
			sendMsg.addMessage(message);
			MessageFinished.getInstantce().add(md5);
		}
	}

	// private static void waitBuildReady(File buildFile) {
	// long lastSize = 0, currSize = 0;
	// int count = 0;
	//
	// while (true) {
	// CommonUtils.sleep(3);
	// currSize = calculateSize(buildFile);
	// if (currSize != lastSize) {
	// lastSize = currSize;
	// count = 0;
	// } else {
	// count++;
	// if (count * 3 > 60)
	// break;
	// }
	// }
	// }

	private boolean isAllBuildCompleted() {
		File[] files = buildFileRoot.listFiles();
		long curTime = System.currentTimeMillis();
		long currentSize = 0;

		for (File f : files) {
			//System.out.println("[ALL] " + f.getPath() + " size=" + f.length() + " time_interval(ms)="+ (curTime - f.lastModified()));
			currentSize += f.length();
		}
		
		if (currentSize == this.lastSizeForAll ) {			
			return ((curTime - this.lastTimestampForAll) > 120 * 1000);
		} else {
			this.lastSizeForAll= currentSize;
			this.lastTimestampForAll = curTime;
			return false;
		}
	}

	private boolean isExactBuildCompleted() {
		long curTime = System.currentTimeMillis();

		File file;
		String filename;
		long currentSize = 0;
		for (FileItem item : itemList) {
			filename = CommonUtils.concatFile(this.buildFileRoot.getPath(), item.getActualFilename(this.buildFileRoot, this.buildId));
			file = new File(filename);

			if (!file.exists()) {
				//System.out.println("[EXACT] " + filename + " not found");
				return false;
			}
			System.out.println("[EXACT] " + file.getPath() + " size=" + file.length() + " time_interval(ms)="+ (curTime - file.lastModified()));
			item.setExactFile(file);
			
			currentSize += file.length();

		}
		
		if (currentSize == this.lastSizeForSingle ) {			
			return ((curTime - this.lastTimestampForSingle) > 120 * 1000);
		} else {
			this.lastSizeForSingle = currentSize;
			this.lastTimestampForSingle = curTime;
			return false;
		}
	}

	// private static long calculateSize(File file) {
	// if (file == null || !file.exists())
	// return -1;
	//
	// int size = 0;
	// File[] subFiles = file.listFiles();
	// for (File f : subFiles) {
	// if (f.isFile()) {
	// size += f.length();
	// } else {
	// size += calculateSize(f);
	// }
	// }
	// return size;
	// }
	
	
}
