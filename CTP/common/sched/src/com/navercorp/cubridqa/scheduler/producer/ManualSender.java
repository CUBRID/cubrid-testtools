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
import java.util.Properties;

import com.navercorp.cubridqa.scheduler.common.CommonUtils;
import com.navercorp.cubridqa.scheduler.common.Constants;
import com.navercorp.cubridqa.scheduler.common.Message;
import com.navercorp.cubridqa.scheduler.common.SendMessage;

public class ManualSender {

	public static void main(String[] args) throws Exception {
		Configure conf = new Configure();

		if (args.length < 4) {
			showHelp();
			return;
		}

		boolean isI18N = false;
		boolean isDBIMG = false;
		String queue = args[0];		
		String[] allUrls = getUrl(args[1]);		
		String url = allUrls[0];
		String scenario = args[2];
		String priority = args[3];
		if (priority.equalsIgnoreCase("default"))
			priority = "4";
		
		Properties msgProps = getMsgProps(args[args.length - 1]);
		String confFilename;
		if(msgProps == null) {
			confFilename = args.length == 5 ? args[4] : null;
		} else {
			confFilename = args.length == 6 ? args[4] : null;
		}

		if (queue.equalsIgnoreCase(Constants.QUEUE_CUBRID_QA_I18N_LINUX_64)) {
			isI18N = true;
		}

		if (queue.indexOf("COMPAT_DBIMG") != -1) {
			isDBIMG = true;
		}

		// String queue = "QUEUE_CUBRID_QA_NBD_LINUX";
		// String url =
		// "http://10.34.64.209/REPO_ROOT/store_01/8.4.4.0129/drop/CUBRID-8.4.4.0129-linux.x86_64.sh";
		// String scenario = "dots";

		String[] arr = url.split("/");

		String fileName = arr[arr.length - 1];
		String BUILD_ID = arr[arr.length - 3];
		String BUILD_STORE_ID = arr[arr.length - 4];

		String BUILD_SVN_BRANCH = CommonUtils.getSVNBranch(conf.getProperty("svn.trunk.version"), BUILD_ID);
		String BUILD_SVN_BRANCH_NEW = CommonUtils.getSVNBranchIgnorePatch(conf.getProperty("svn.trunk.version"), BUILD_ID);
		String BUILD_URLS = url;
		String BUILD_URLS_KR = conf.getWebBaseUrl_Kr() + "/" + BUILD_ID + "/drop/" + fileName;
		String BUILD_URLS_KR_REPO1 = conf.getWebBaseUrl_KrRepo1() == null ? null : conf.getWebBaseUrl_KrRepo1() + "/" + BUILD_ID + "/drop/" + fileName;

		String BUILD_URLS_CNT = String.valueOf(allUrls.length);

		File file = new File(conf.getRepoRoot() + File.separator + BUILD_STORE_ID + File.separator + BUILD_ID + File.separator + "drop" + File.separator + fileName);
		if (file.exists() == false) {
			showHelp();
			System.out.println("Err: File not found for " + file.getPath());
			return;
		}
		
		String BUILD_ABSOLUTE_PATH= file.getParentFile().getAbsolutePath();

		String BUILD_CREATE_TIME = String.valueOf(file.lastModified());

		String BUILD_BIT = "";
		if (fileName.indexOf("-linux.x86_64") != -1) {
			BUILD_BIT = "64";
		} else if (fileName.indexOf("-linux.i386") != -1) {
			BUILD_BIT = "32";
		} else if (fileName.indexOf("-x64-") != -1) {
			BUILD_BIT = "64";
		} else if (fileName.indexOf("-x86-") != -1) {
			BUILD_BIT = "32";
		} else if (fileName.indexOf("ppc64") != -1) {
			BUILD_BIT = "64";
		} else {
			BUILD_BIT = "0";
		}

		String BUILD_SCENARIOS = scenario;

		long now = System.currentTimeMillis();
		String BUILD_SEND_DELAY = String.valueOf((now - file.lastModified()) / 1000);
		String BUILD_SEND_TIME = String.valueOf(now);

		System.out.println("");
		System.out.println("Message: ");
		System.out.println("");

		String buildType;
		if (fileName.toLowerCase().indexOf("-gcov-") != -1) {
			buildType = "coverage";
		} else if (fileName.toLowerCase().indexOf("debug") != -1) {
			buildType = "debug";
		} else {
			buildType = "general";
		}
		
		String mainVersionIgnorePatch = CommonUtils.getVersionIgnorePatch(BUILD_ID);

		Message message = new Message(queue, "Test for build " + BUILD_ID + " by CUBRID QA Team, China");
		message.setProperty(Constants.MSG_BUILD_URLS, BUILD_URLS);
		message.setProperty(Constants.MSG_BUILD_URLS_KR, BUILD_URLS_KR);
		if (BUILD_URLS_KR_REPO1 != null) {
			message.setProperty(Constants.MSG_BUILD_URLS_KR_REPO1, BUILD_URLS_KR_REPO1);
		}
		
		if (allUrls.length > 1) {
			String fn;
			File f;
			for (int i = 1; i < allUrls.length; i++) {
				fn = getFileNameInUrl(allUrls[i]);
				f = new File(conf.getRepoRoot() + File.separator + BUILD_STORE_ID + File.separator + BUILD_ID + File.separator + "drop" + File.separator + fn);
				if (f.exists() == false) {
					showHelp();
					System.out.println("Err: File not found for " + file.getPath());
					return;
				}
				message.setProperty(Constants.MSG_BUILD_URLS + "_" + i, allUrls[i]);
				message.setProperty(Constants.MSG_BUILD_URLS_KR + "_" + i, conf.getWebBaseUrl_Kr() + "/" + BUILD_ID + "/drop/" + fn);
				if (conf.getWebBaseUrl_KrRepo1() != null) {
					message.setProperty(Constants.MSG_BUILD_URLS_KR_REPO1 + "_" + i, conf.getWebBaseUrl_KrRepo1() + "/" + BUILD_ID + "/drop/" + fn);
				}
			}
		}
		
		String isBuildFromGit = Integer.parseInt(CommonUtils.getFirstVersion(BUILD_ID)) >= 10 ? "1" : "0";
		
		message.setProperty(Constants.MSG_BUILD_URLS_CNT, BUILD_URLS_CNT);
		message.setProperty(Constants.MSG_BUILD_ID, BUILD_ID);
		message.setProperty(Constants.MSG_BUILD_STORE_ID, BUILD_STORE_ID);
		message.setProperty(Constants.MSG_BUILD_BIT, BUILD_BIT);
		message.setProperty(Constants.MSG_BUILD_CREATE_TIME, BUILD_CREATE_TIME);
		message.setProperty(Constants.MSG_BUILD_SEND_TIME, BUILD_SEND_TIME);
		message.setProperty(Constants.MSG_BUILD_SEND_DELAY, BUILD_SEND_DELAY);
		message.setProperty(Constants.MSG_BUILD_SCENARIOS, BUILD_SCENARIOS);
		message.setProperty(Constants.MSG_BUILD_SVN_BRANCH, BUILD_SVN_BRANCH);
		message.setProperty(Constants.MSG_BUILD_SVN_BRANCH_NEW, BUILD_SVN_BRANCH_NEW);
		message.setProperty(Constants.MSG_BUILD_TYPE, buildType);
		message.setProperty(Constants.MSG_BUILD_PACKAGE_PATTERN, CommonUtils.replace(fileName, BUILD_ID, "{1}"));
		message.setProperty(Constants.MSG_BUILD_ABSOLUTE_PATH, BUILD_ABSOLUTE_PATH);
		message.setProperty(Constants.MSG_BUILD_IS_FROM_GIT, isBuildFromGit);
		if (isBuildFromGit.equals("1")) {
			String minorVersion = CommonUtils.getFirstVersion(BUILD_ID) + "." + CommonUtils.getSecondVersion(BUILD_ID);
			String scenarioBranchForCurrBuild = conf.getProperty("scenario_branch_on_git_for_" + minorVersion);
			if (scenarioBranchForCurrBuild == null || scenarioBranchForCurrBuild.trim().equals("")) {
				scenarioBranchForCurrBuild = "release/" + minorVersion;
			}
			message.setProperty(Constants.MSG_BUILD_SCENARIO_BRANCH_GIT, scenarioBranchForCurrBuild);
		}
		message.setProperty(Constants.MSG_BUILD_GENERATE_MSG_WAY, "MANUAL");
		
		message.putAll(msgProps);

		message.setPriority(Integer.parseInt(priority));

		SendMessage sendMsg = new SendMessage(conf.props);

		String content = "";
		String mainVersion = CommonUtils.getVersion(BUILD_ID);

		if (confFilename == null) {
			content = message.toString();
			sendMsg.addMessage(message);
		} else {
			if (isI18N) {
				GeneralExtendedSuite instance = GeneralExtendedSuite.getInstance(conf, "i18n.conf", false);
				if (confFilename.trim().equalsIgnoreCase("-i18nAll")) {

					ArrayList<Properties> list = instance.getMsgProperties(getI18NKey(queue, mainVersion));
					if (list == null) {
						list = instance.getMsgProperties(getI18NKey(queue, mainVersionIgnorePatch));
					}
					
					for (Properties props : list) {
						Message m = (Message) message.clone();
						m.putAll(props);
						content = content + m.toString() + "\n\n";
						sendMsg.addMessage(m);
					}

				} else {
					String compatKey = "MANUAL_INRUNTIME";					
					File f = new File(confFilename);					
					instance.setCompatibility(compatKey, f.getName());
					ArrayList<Properties> list = instance.getMsgProperties(compatKey);

					if (list.size() == 0) {
						System.out.println("Not found i18 message configuration in " + confFilename);
					} else {
						message.putAll(list.get(0));
						sendMsg.addMessage(message);
					}
					content = message.toString();
				}
			} else if (isDBIMG) {
				GeneralExtendedSuite instance = GeneralExtendedSuite.getInstance(conf, "compat_dbimg.conf", false);
				if (confFilename.trim().equalsIgnoreCase("-dbimgAll")) {

					ArrayList<Properties> list = instance.getMsgProperties(getCompatDBIMGKey(queue, mainVersion));
					if (list == null) {
						list = instance.getMsgProperties(getCompatDBIMGKey(queue, mainVersionIgnorePatch));
					}

					for (Properties props : list) {
						Message m = (Message) message.clone();
						m.putAll(props);
						content = content + m.toString() + "\n\n";
						sendMsg.addMessage(m);
					}
				} else {
					String compatKey = "MANUAL_INRUNTIME";					
					File f = new File(confFilename);					
					instance.setCompatibility(compatKey, f.getName());
					ArrayList<Properties> list = instance.getMsgProperties(compatKey);
					if (list.size() == 0) {
						System.out.println("Not found dbimg compat configuration in " + confFilename);
					} else {
						message.putAll(list.get(0));
						sendMsg.addMessage(message);
					}
					content = message.toString();
				}
			} else {
				GeneralExtendedSuite instance = GeneralExtendedSuite.getInstance(conf, "compat.conf", false);
				
				if (confFilename.trim().equalsIgnoreCase("-compatAll")) {
					ArrayList<Properties> list = instance.getMsgProperties(getCompatKey(queue, mainVersion));
					
					if(list == null) {
						list = instance.getMsgProperties(getCompatKey(queue, mainVersionIgnorePatch));
					}

					for (Properties props : list) {
						Message m = (Message) message.clone();
						m.putAll(props);
						content = content + m.toString() + "\n\n";
						sendMsg.addMessage(m);
					}

				} else {					
					String compatKey = "MANUAL_INRUNTIME";					
					File f = new File(confFilename);					
					instance.setCompatibility(compatKey, f.getName());
					ArrayList<Properties> list = instance.getMsgProperties(compatKey);
					if (list.size() == 0) {
						System.out.println("Not found compatibility configuration in " + confFilename);
					} else {
						message.putAll(list.get(0));
						sendMsg.addMessage(message);
					}
					content = message.toString();
				}
			}
		}

		System.out.println(content);
		System.out.println();

		if (acceptConfirm()) {
			sendMsg.send();
		}
	}

	private static boolean acceptConfirm() throws IOException {
		int c;
		System.out.println("Do you accept above message [Y/N]:");
		while (true) {
			c = System.in.read();
			if (c == 'Y' || c == 'N')
				break;
			if (c == '\n')
				System.out.println("Please input [Y/N]:");
		}
		return c == 'Y';
	}

	private static void showHelp() {
		System.out.println("Usage: java com.navercorp.cubridqa.scheduler.producer.ManualSender <queue> <url1,url2,...> <scenario> <priority> [compat_file|-i18nAll|-dbimgAll|-compatAll] [PROPS:MKEY_TEST1=v1;MKEY_TEST2=v2]");
		System.out.println();
	}
	
	private static String getCompatKey(String queue, String mainVersion) {
		String compatKey = "";
		if (queue.equals(Constants.QUEUE_CUBRID_QA_COMPAT_JDBC_SQL_DRIVER)) {
			compatKey = "jdbc_compatibility_for_" + mainVersion + "_D";
		} else if (queue.equals(Constants.QUEUE_CUBRID_QA_COMPAT_JDBC_SQL_SERVER_64)) {
			compatKey = "jdbc_compatibility_for_" + mainVersion + "_S64";
		} else if (queue.equals(Constants.QUEUE_CUBRID_QA_COMPAT_CCI_SHELL_DRIVER_64)) {
			compatKey = "cci_compatibility_for_" + mainVersion + "_D64";
		} else if (queue.equals(Constants.QUEUE_CUBRID_QA_COMPAT_CCI_SHELL_SERVER_64)) {
			compatKey = "cci_compatibility_for_" + mainVersion + "_S64";
		}
		
		return compatKey;
	}
	
	private static String getCompatDBIMGKey(String queue, String mainVersion) {
		String compatKey = "";
		if (queue.equals(Constants.QUEUE_CUBRID_QA_COMPAT_DBIMG_INS)) {
			compatKey = "dbimg_for_" + mainVersion + "_INS";
		} else if (queue.equals(Constants.QUEUE_CUBRID_QA_COMPAT_DBIMG_SRV)) {
			compatKey = "dbimg_for_" + mainVersion + "_SRV";
		}
		return compatKey;
	}
	
	private static String getI18NKey(String queue, String mainVersion) {
		String i18nKey = "";
		if (queue.equals(Constants.QUEUE_CUBRID_QA_I18N_LINUX_64)) {
			i18nKey = "i18n_for_" + mainVersion;
		}
		return i18nKey;
	}
	
	private static Properties getMsgProps(String cont) {
		if(cont == null ) return null;
		
		String s = cont.trim();
		if (!s.startsWith("PROPS:")) {
			return null;
		}
		s = s.substring(s.indexOf(":") + 1);
		
		Properties props  = new Properties();
		String[] arr = s.split(";");
		String key, value;
		int index = -1;
		for(String pair: arr) {
			index = pair.indexOf("=");
			if(index == -1 ) continue;
			key = pair.substring(0, index).trim();
			value = pair.substring(index + 1).trim();
			props.setProperty(key, value);			
		}
		return props;
	}
	
	private static String[] getUrl(String urls) {
		return urls.split(",");
	}
	
	private static String getFileNameInUrl(String url) {
		String[] arr = url.split("/");
		String fileName = arr[arr.length - 1];
		return fileName;
	}
}
