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

import java.io.File;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.navercorp.cubridqa.ha_repl.common.CommonUtils;
import com.navercorp.cubridqa.ha_repl.common.Constants;
import com.navercorp.cubridqa.common.Log;
import com.navercorp.cubridqa.ha_repl.common.SSHConnect;
import com.navercorp.cubridqa.ha_repl.common.ShellInput;
import com.navercorp.cubridqa.ha_repl.deploy.Deploy;
import com.navercorp.cubridqa.ha_repl.dispatch.Dispatch;
import com.navercorp.cubridqa.ha_repl.impl.*;
import com.navercorp.cubridqa.ha_repl.migrate.Convert;

public class Main {
	public static void main(String[] args1) throws Exception {

		Context context = new Context(CommonUtils.concatFile(Constants.DIR_CONF, "main.properties"));

		/*
		 * read test root
		 */
		String testRoot = context.getProperty("main.testcase.root");
		boolean hasError = false;
		if (testRoot == null || testRoot.trim().equals("")) {
			hasError = true;
		} else {
			testRoot = testRoot.trim();
			File file = new File(testRoot);
			hasError = !file.exists();
		}
		if (hasError)
			throw new Exception("Not found root directory for test cases. Please set it in main.properties.");

		/*
		 * continue mode
		 */

		String cubridPackageUrl = context.getProperty("main.testbuild.url", "").trim();
		String differMode = context.getProperty("main.differ.mode", "diff_1");

		String versionId = null;
		Pattern pattern = Pattern.compile("\\d+\\.\\d+\\.\\d+\\.\\d+");
		Matcher matcher = pattern.matcher(cubridPackageUrl);
		while (matcher.find()) {
			versionId = matcher.group();
		}
		if (CommonUtils.isNewBuildNumberSystem(versionId)) {
			String extensionversionId;

			int p1 = cubridPackageUrl.lastIndexOf(versionId);
			int p2 = cubridPackageUrl.indexOf("-", p1 + versionId.length() + 1);

			if (p2 == -1) {
				p2 = cubridPackageUrl.indexOf(".", p1 + versionId.length() + 1);
			}

			extensionversionId = p2 == -1 ? cubridPackageUrl.substring(p1) : cubridPackageUrl.substring(p1, p2);
			context.setVersionId(extensionversionId);
		} else {
			context.setVersionId(versionId);
		}
		System.out.println("Version Id: " + context.getVersionId());

		// get version (64bit or 32bit)
		String bits = null;
		int idx1 = cubridPackageUrl.indexOf("_64");
		int idx2 = cubridPackageUrl.indexOf("x64");
		int idx3 = cubridPackageUrl.indexOf("ppc64"); // AIX BUILD.
														// CUBRID-8.4.4.0136-AIX-ppc64.sh

		if (idx1 >= 0 || idx2 >= 0 || idx3 >= 0) {
			bits = "64bits";
		} else {
			bits = "32bits";
		}
		context.setBits(bits);
		System.out.println("Test Version: " + bits);

		System.out.println("Test MODE: " + context.isContinueMode());
		System.out.println("Test Build: " + cubridPackageUrl);

		ArrayList<String> envList = getIntanceList();
		System.out.println("Available Env: " + envList);

		if (envList.size() == 0) {
			throw new Exception("Not found any HA instance to test. (ha_instance_<EnvId>_<ON|OFF>.properties)");
		}

		Feedback feedback;
		String feedbackType = context.getProperty("main.feedback.type", "").trim();
		if (feedbackType.equalsIgnoreCase("file")) {
			feedback = new FeedbackFile(context);
		} else if (feedbackType.equalsIgnoreCase("database")) {
			feedback = new FeedbackDB(context);
		} else {
			feedback = new FeedbackNull(context);
		}
		context.setFeedback(feedback);

		Properties props = context.getProperties();
		Set set = props.keySet();
		Log contextSnapshot = new Log(CommonUtils.concatFile(Constants.DIR_CONF, "main_snapshot.properties"), true, false);
		for (Object key : set) {
			contextSnapshot.println(key + "=" + props.getProperty((String) key));
		}
		contextSnapshot.println("AUTO_TEST_VERSION=" + context.getVersionId());
		contextSnapshot.println("AUTO_TEST_BITS=" + context.getBits());
		contextSnapshot.close();

		if (!context.isContinueMode()) {
			feedback.onTaskStartEvent(cubridPackageUrl);
			//
			// /*
			// * SVN UP for SQL
			// */
			//
			// /*
			// * SQL Patch phase: TBD
			// */
			//
			// /*
			// * convert phase
			// */
			feedback.onConvertEventStart();
			Convert c = new Convert(testRoot);
			c.convert();
			feedback.onConvertEventStop();
		} else {
			feedback.onTaskContinueEvent();
		}

		String rebuildEnv = context.getProperty("main.deploy.rebuild", "true");
		if (rebuildEnv.trim().equalsIgnoreCase("true")) {
			/*
			 * deploy phase
			 */
			System.out.println("============= DEPLOY STEP ==================");
			concurrentDeploy(context, envList, cubridPackageUrl);
			System.out.println("DONE.");
		}

		/*
		 * dispatch phase
		 */
		System.out.println("============= DISPATCH STEP ==================");
		Dispatch.init(context, envList, context.isContinueMode(), testRoot);
		feedback.setTotalTestCase(Dispatch.getInstance().getTbdSize(), Dispatch.getInstance().getMacroSkippedSize(), Dispatch.getInstance().getTempSkippedSize());
		if (context.isContinueMode() == false) {
			addSkippedTestCases(feedback, Dispatch.getInstance().getMacroSkippedList(), Constants.SKIP_TYPE_BY_MACRO);
			addSkippedTestCases(feedback, Dispatch.getInstance().getTempSkippedList(), Constants.SKIP_TYPE_BY_TEMP);
		}

		// System.out.println("============= DISPATCH STEP ==================");
		// startServiceAndVerifyTestEnv(context, envList);

		/*
		 * test phase
		 */
		System.out.println("============= TEST STEP ==================");
		concurrentTest(context, envList, context.isContinueMode(), differMode);
		feedback.onTaskStopEvent();

		System.out.println("DONE.");

	}

	public static ArrayList<String> getIntanceList() {
		ArrayList<String> resultList = new ArrayList<String>();
		String[] subList = new File(Constants.DIR_CONF).list();
		String reg = "ha_instance_(.*)_(.*?).properties";
		Pattern pattern = Pattern.compile(reg);
		Matcher matcher;
		String envId;
		String flag;
		boolean findit;
		for (String filename : subList) {
			matcher = pattern.matcher(filename);
			findit = matcher.find();
			if (!findit)
				continue;
			envId = matcher.group(1);
			flag = matcher.group(2);

			if (flag.equals("ON")) {
				resultList.add(envId);
			}
		}
		return resultList;
	}

	private static void concurrentTest(Context context, final ArrayList<String> envList, final boolean isContinueMode, final String differMode) throws Exception {
		ExecutorService pool;

		pool = Executors.newFixedThreadPool(envList.size() * 2);

		for (final String envId : envList) {
			String envConfigFilename = CommonUtils.concatFile(Constants.DIR_CONF, "ha_instance_" + envId + "_ON.properties");
			final Test test = new Test(context, envId, envConfigFilename, isContinueMode, differMode);
			final TestMonitor monitor = new TestMonitor(context, test);
			pool.execute(new Runnable() {
				@Override
				public void run() {
					try {
						test.runAll();
					} catch (Exception e) {
						e.printStackTrace();
					} finally {
						test.close();
					}
				}
			});
			pool.execute(new Runnable() {
				@Override
				public void run() {
					try {
						monitor.startMonitor();
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			});
		}
		pool.shutdown();
		pool.awaitTermination(Integer.MAX_VALUE, TimeUnit.DAYS);
		pool = null;
	}

	private static void concurrentDeploy(final Context context, ArrayList<String> envList, final String cubridPackageUrl) throws InterruptedException {
		ExecutorService pool;

		pool = Executors.newFixedThreadPool(envList.size());

		for (final String envId : envList) {
			pool.execute(new Runnable() {
				String envConfigFilename;
				Deploy deploy;

				@Override
				public void run() {
					envConfigFilename = CommonUtils.concatFile(Constants.DIR_CONF, "ha_instance_" + envId + "_ON.properties");
					try {
						// context.getFeedback().onDeployStart(envId);
						deploy = new Deploy(envId, envConfigFilename, cubridPackageUrl, context);
						deploy.deployAll();
						// context.getFeedback().onDeployStop(envId);
					} catch (Exception e) {
						e.printStackTrace();
					}
					deploy.close();
				}
			});
		}
		pool.shutdown();
		pool.awaitTermination(Integer.MAX_VALUE, TimeUnit.DAYS);
		pool = null;
	}

	private static void addSkippedTestCases(Feedback feedback, ArrayList<String> list, String skippedType) {
		if (list == null) {
			return;
		}
		for (String tc : list) {
			feedback.onTestCaseStopEvent(tc, false, -1, "", "", false, false, skippedType);
		}
	}
}
