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
package com.navercorp.cubridqa.scheduler.producer.crontab;

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
import com.navercorp.cubridqa.scheduler.producer.Configure;
import com.navercorp.cubridqa.scheduler.producer.GeneralExtendedSuite;

public class FileProcess {

	String queue;
	String scenario;
	long delay;
	File exactFile;
	File[] exactMoreFiles;
	SendMessage sendMsg;
	String pkgPattern;
	String pkgBits;
	Configure conf;
	String storeId;
	String buildId;
	String pkgType;
	String buildAbsolutePath;

	String extConfig;
	String extKeys;

	Properties msgProps;

	public FileProcess(Configure conf, File exactFile, File[] exactMoreFiles, String pkgPattern, String pkgBits, String pkgType, String queue, String scenario, long delay, String extConfig,
			String extKeys, Properties msgProps) {
		this.conf = conf;
		this.pkgPattern = pkgPattern;
		this.pkgBits = pkgBits;
		this.sendMsg = new SendMessage(this.conf.getProperties());
		this.pkgType = pkgType;
		this.queue = queue;
		this.scenario = scenario;
		this.delay = delay;
		this.exactFile = exactFile;
		this.exactMoreFiles = exactMoreFiles;

		storeId = exactFile.getParentFile().getParentFile().getParentFile().getName();
		buildId = exactFile.getParentFile().getParentFile().getName();
		this.buildAbsolutePath = exactFile.getParentFile().getAbsolutePath();

		this.extConfig = extConfig;
		this.extKeys = extKeys;
		this.msgProps = msgProps;
	}

	public void process() throws JMSException, NoSuchAlgorithmException, IOException {

		if (extConfig == null || extKeys == null) {
			sendMessage(queue, scenario, 4, null, this.msgProps);
		} else {

			ArrayList<Properties> list;
			String[] arr = extKeys.split(",");
			for (String k : arr) {
				if (k == null)
					continue;
				k = k.trim();
				if (k.equals(""))
					continue;
				k = CommonUtils.replace(k, "{version}", CommonUtils.getVersion(buildId));

				list = GeneralExtendedSuite.getInstance(conf, extConfig, false).getMsgProperties(k);
				if (list == null || list.size() == 0) {
					String s = CommonUtils.replace(k, "{version}", CommonUtils.getVersionIgnorePatch(buildId));
					list = GeneralExtendedSuite.getInstance(conf, extConfig, false).getMsgProperties(s);
				}
				if (list != null) {
					for (Properties props : list) {
						sendMessage(queue, scenario, 4, props, this.msgProps);
					}
				}
			}
		}

		if (conf.isGenerateMessage()) {
			sendMsg.send(delay);
		}
	}

	public void close() {

	}

	private void sendMessage(String queue, String scenarios, int priority, Properties extProps, Properties msgProps) throws NoSuchAlgorithmException, IOException {

		long curTime = System.currentTimeMillis();
		Message message = new Message(queue, "Test for build " + buildId + " by CUBRID QA Team, China");

		String urls = null;
		String urls_kr = null;
		String urls_kr_reop1 = null;
		long maxCreateTime = 0;

		urls = convToURL(conf, exactFile, storeId, buildId, false);
		urls_kr = convToURL(conf, exactFile, storeId, buildId, true);
		urls_kr_reop1 = convToKrRepo1Url(conf, exactFile, buildId);

		if (exactFile.lastModified() > maxCreateTime) {
			maxCreateTime = exactFile.lastModified();
		}

		message.setProperty(Constants.MSG_BUILD_URLS, urls);
		message.setProperty(Constants.MSG_BUILD_URLS_KR, urls_kr);
		if (urls_kr_reop1 != null) {
			message.setProperty(Constants.MSG_BUILD_URLS_KR_REPO1, urls_kr_reop1);
		}

		if (this.exactMoreFiles != null) {
			for (int i = 0; i < this.exactMoreFiles.length; i++) {
				urls = convToURL(conf, exactMoreFiles[i], storeId, buildId, false);
				urls_kr = convToURL(conf, exactMoreFiles[i], storeId, buildId, true);
				urls_kr_reop1 = convToKrRepo1Url(conf, exactMoreFiles[i], buildId);
				message.setProperty(Constants.MSG_BUILD_URLS + "_" + (i + 1), urls);
				message.setProperty(Constants.MSG_BUILD_URLS_KR + "_" + (i + 1), urls_kr);
				if (urls_kr_reop1 != null) {
					message.setProperty(Constants.MSG_BUILD_URLS_KR_REPO1 + "_" + (i + 1), urls_kr_reop1);
				}
			}
		}

		String isBuildFromGit = Integer.parseInt(CommonUtils.getFirstVersion(buildId)) >= 10 ? "1" : "0";

		message.setProperty(Constants.MSG_BUILD_URLS_CNT, "0");
		message.setProperty(Constants.MSG_BUILD_ID, buildId);
		message.setProperty(Constants.MSG_BUILD_STORE_ID, storeId);
		message.setProperty(Constants.MSG_BUILD_BIT, pkgBits);
		message.setProperty(Constants.MSG_BUILD_CREATE_TIME, String.valueOf(maxCreateTime));
		message.setProperty(Constants.MSG_BUILD_SEND_TIME, String.valueOf(curTime));
		message.setProperty(Constants.MSG_BUILD_SEND_DELAY, String.valueOf((curTime - maxCreateTime) / 1000));
		message.setProperty(Constants.MSG_BUILD_SCENARIOS, (scenarios == null ? "" : scenarios));
		message.setProperty(Constants.MSG_BUILD_SVN_BRANCH, CommonUtils.getSVNBranch(conf.getProperty("svn.trunk.version"), this.buildId));
		message.setProperty(Constants.MSG_BUILD_SVN_BRANCH_NEW, CommonUtils.getSVNBranchIgnorePatch(conf.getProperty("svn.trunk.version"), this.buildId));
		message.setProperty(Constants.MSG_BUILD_TYPE, pkgType);
		message.setProperty(Constants.MSG_BUILD_PACKAGE_PATTERN, String.valueOf(this.pkgPattern));
		message.setProperty(Constants.MSG_BUILD_ABSOLUTE_PATH, this.buildAbsolutePath);
		message.setProperty(Constants.MSG_BUILD_IS_FROM_GIT, isBuildFromGit);
		if (isBuildFromGit.equals("1")) {
			String minorVersion = CommonUtils.getFirstVersion(buildId) + "." + CommonUtils.getSecondVersion(buildId);
			String scenarioBranchForCurrBuild = conf.getProperty("scenario_branch_on_git_for_" + minorVersion);
			if (scenarioBranchForCurrBuild == null || scenarioBranchForCurrBuild.trim().equals("")) {
				scenarioBranchForCurrBuild = "release/" + minorVersion;
			}
			message.setProperty(Constants.MSG_BUILD_SCENARIO_BRANCH_GIT, scenarioBranchForCurrBuild);
		}
		message.setProperty(Constants.MSG_BUILD_GENERATE_MSG_WAY, "AUTO");

		if (extProps != null) {
			message.putAll(extProps);
		}

		if (msgProps != null) {
			message.putAll(msgProps);
		}

		String pri = conf.getProperty("msg.priority.for_" + CommonUtils.getVersion(this.buildId), "4");
		message.setPriority(Integer.parseInt(pri));

		System.out.println("=======================================================================================================");
		System.out.println("queue: " + queue);
		System.out.println("send date: " + new java.util.Date());
		System.out.println("delay: " + this.delay + " millisecond(s)");
		System.out.println(message);

		if (conf.isGenerateMessage()) {
			sendMsg.addMessage(message);
		}
	}

	private static String convToURL(Configure conf, File f, String storeId, String buildId, boolean isKorean) {
		if (isKorean) {
			return conf.getWebBaseUrl_Kr() + "/" + buildId + "/drop/" + f.getName();
		} else {
			return conf.getWebBaseUrl() + "/" + storeId + "/" + buildId + "/drop/" + f.getName();
		}
	}

	private static String convToKrRepo1Url(Configure conf, File f, String buildId) {
		if (conf.getWebBaseUrl_KrRepo1() == null || conf.getWebBaseUrl_KrRepo1().trim().equals("")) {
			return null;
		}
		return conf.getWebBaseUrl_KrRepo1() + "/" + buildId + "/drop/" + f.getName();
	}
}
