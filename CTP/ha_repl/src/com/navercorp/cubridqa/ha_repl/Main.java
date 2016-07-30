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

import java.util.ArrayList;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.navercorp.cubridqa.common.CommonUtils;
import com.navercorp.cubridqa.common.Log;
import com.navercorp.cubridqa.ha_repl.common.Constants;
import com.navercorp.cubridqa.ha_repl.deploy.Deploy;
import com.navercorp.cubridqa.ha_repl.dispatch.Dispatch;
import com.navercorp.cubridqa.ha_repl.impl.FeedbackDB;
import com.navercorp.cubridqa.ha_repl.impl.FeedbackFile;
import com.navercorp.cubridqa.ha_repl.impl.FeedbackNull;
import com.navercorp.cubridqa.ha_repl.migrate.Convert;
import com.navercorp.cubridqa.ha_repl.Context;

public class Main {
	public static void exec(String configFilename) throws Exception {

		Context context = new Context(configFilename);

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

		context.setBuildId(CommonUtils.getBuildId(cubridPackageUrl));
		context.setBuildBits(CommonUtils.getBuildBits(cubridPackageUrl));

		System.out.println("BUILD URL: " + cubridPackageUrl);
		System.out.println("BUILD D: " + context.getBuildId());
		System.out.println("BUILD BITS: " + context.getBuildBits());		
		System.out.println("Test Mode: " + context.isContinueMode());

		ArrayList<String> envList = getIntanceList(context);
		System.out.println("Available Env: " + envList);

		if (envList.size() == 0) {
			throw new Exception("Not found any HA instance to test. (ha_instance_<EnvId>_<ON|OFF>.properties)");
		}



		Properties props = context.getProperties();
		Set set = props.keySet();
		Log contextSnapshot = new Log(CommonUtils.concatFile(context.getCurrentLogDir(), "main_snapshot.properties"), true, false);
		for (Object key : set) {
			contextSnapshot.println(key + "=" + props.getProperty((String) key));
		}
		contextSnapshot.println("AUTO_TEST_BUILD_ID=" + context.getBuildId());
		contextSnapshot.println("AUTO_TEST_BUILD_BITS=" + context.getBuildBits());
		contextSnapshot.close();

		if (!context.isContinueMode()) {
			context.getFeedback().onTaskStartEvent(cubridPackageUrl);
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
			context.getFeedback().onConvertEventStart();
			Convert c = new Convert(testRoot);
			c.convert();
			context.getFeedback().onConvertEventStop();
		} else {
			context.getFeedback().onTaskContinueEvent();
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
		context.getFeedback().setTotalTestCase(Dispatch.getInstance().getTbdSize(), Dispatch.getInstance().getMacroSkippedSize(), Dispatch.getInstance().getTempSkippedSize());
		if (context.isContinueMode() == false) {
			addSkippedTestCases(context.getFeedback(), Dispatch.getInstance().getMacroSkippedList(), Constants.SKIP_TYPE_BY_MACRO);
			addSkippedTestCases(context.getFeedback(), Dispatch.getInstance().getTempSkippedList(), Constants.SKIP_TYPE_BY_TEMP);
		}

		// System.out.println("============= DISPATCH STEP ==================");
		// startServiceAndVerifyTestEnv(context, envList);

		/*
		 * test phase
		 */
		System.out.println("============= TEST STEP ==================");
		concurrentTest(context, envList, context.isContinueMode(), differMode);
		context.getFeedback().onTaskStopEvent();

		System.out.println("DONE.");

	}

	public static ArrayList<String> getIntanceList(Context context) {
		ArrayList<String> resultList = new ArrayList<String>();
		String[] subList = new File(context.getCurrentLogDir()).list();
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
			String envConfigFilename = CommonUtils.concatFile(context.getCurrentLogDir(), "ha_instance_" + envId + "_ON.properties");
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
					envConfigFilename = CommonUtils.concatFile(context.getCurrentLogDir(), "ha_instance_" + envId + "_ON.properties");
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
