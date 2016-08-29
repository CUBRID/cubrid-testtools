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
import java.util.Calendar;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.navercorp.cubridqa.common.CommonUtils;
import com.navercorp.cubridqa.common.Log;
import com.navercorp.cubridqa.ha_repl.common.Constants;
import com.navercorp.cubridqa.ha_repl.deploy.Deploy;
import com.navercorp.cubridqa.ha_repl.dispatch.Dispatch;
import com.navercorp.cubridqa.ha_repl.migrate.Convert;
import com.navercorp.cubridqa.shell.common.LocalInvoker;
import com.navercorp.cubridqa.shell.common.SSHConnect;

public class Main {
	public static void exec(String configFilename) throws Exception {
		
		Class.forName("cubrid.jdbc.driver.CUBRIDDriver").newInstance();

		Context context = new Context(configFilename);
		ArrayList<String> envList = context.getTestEnvList();
		System.out.println("Available Env: " + envList);
		
		if (envList.size() == 0) {
			throw new Exception("Not found any HA instance to test.");
		}
		
		String testRoot = context.getTestCaseRoot();
		boolean hasError = false;
		if (testRoot == null || testRoot.trim().equals("")) {
			hasError = true;
		} else {
			testRoot = testRoot.trim();
			File file = new File(testRoot);
			hasError = !file.exists();
		}
		if (hasError)
			throw new Exception("Not found test cases directory. Please check 'main.testcase.root' in test configuration file.");

		String cubridPackageUrl = context.getCubridPackageUrl();
		if (cubridPackageUrl != null && cubridPackageUrl.trim().length() > 0) {
			context.setBuildId(CommonUtils.getBuildId(cubridPackageUrl));
			context.setBuildBits(CommonUtils.getBuildBits(cubridPackageUrl));
			context.setRebuildYn(true);
		} else {

			String envId = context.getTestEnvList().get(0);
			String host = context.getInstanceProperty(envId, "ssh.host");
			String port = context.getInstanceProperty(envId, "ssh.port");
			String user = context.getInstanceProperty(envId, "ssh.user");
			String pwd = context.getInstanceProperty(envId, "ssh.pwd");
			SSHConnect ssh = new SSHConnect(host, port, user, pwd, "ssh"); 
			context.setBuildId(CommonUtils.getBuildId(com.navercorp.cubridqa.shell.common.CommonUtils.getBuildVersionInfo(ssh)));
			context.setBuildBits(CommonUtils.getBuildBits(com.navercorp.cubridqa.shell.common.CommonUtils.getBuildVersionInfo(ssh)));
			context.setRebuildYn(false);
		}

		System.out.println("BUILD ID: " + context.getBuildId());
		System.out.println("BUILD BITS: " + context.getBuildBits());		
		System.out.println("Continue Mode: " + context.isContinueMode());


		
		checkRequirement(context);

		Properties props = context.getProperties();
		Set set = props.keySet();
		Log contextSnapshot = new Log(CommonUtils.concatFile(context.getCurrentLogDir(), "main_snapshot.properties"), true, false);
		for (Object key : set) {
			contextSnapshot.println(key + "=" + props.getProperty((String) key));
		}
		contextSnapshot.println("AUTO_TEST_BUILD_ID=" + context.getBuildId());
		contextSnapshot.println("AUTO_TEST_BUILD_BITS=" + context.getBuildBits());
		contextSnapshot.close();

		if (context.isContinueMode()) {
			context.getFeedback().onTaskContinueEvent();
		} else {
			context.getFeedback().onTaskStartEvent();
			context.getFeedback().onConvertEventStart();
			Convert c = new Convert(testRoot);
			c.convert();
			context.getFeedback().onConvertEventStop();
		}

		System.out.println("============= DEPLOY STEP ==================");
		concurrentDeploy(context, envList);
		System.out.println("DONE.");
		
		/*
		 * dispatch phase
		 */
		System.out.println("============= DISPATCH STEP ==================");
		Dispatch.init(context);
		context.getFeedback().setTotalTestCase(Dispatch.getInstance().getTbdSize(), Dispatch.getInstance().getMacroSkippedSize(), Dispatch.getInstance().getTempSkippedSize());
		if (context.isContinueMode() == false) {
			addSkippedTestCases(context.getFeedback(), Dispatch.getInstance().getMacroSkippedList(), Constants.SKIP_TYPE_BY_MACRO);
			addSkippedTestCases(context.getFeedback(), Dispatch.getInstance().getTempSkippedList(), Constants.SKIP_TYPE_BY_TEMP);
		}
		System.out.println("Total: " + Dispatch.getInstance().getTotalSize() + ", tbd: " + Dispatch.getInstance().getTbdSize() + ", skipped: "
				+ (Dispatch.getInstance().getMacroSkippedSize() + Dispatch.getInstance().getTempSkippedSize()));
		System.out.println("DONE.");

		/*
		 * test phase
		 */
		System.out.println("============= TEST STEP ==================");
		concurrentTest(context);
		context.getFeedback().onTaskStopEvent();
		System.out.println("DONE.");
		
		System.out.println("============= BACKUP TEST RESULTS ==================");
		backupTestResults(context);		
		System.out.println("DONE.");
		
		System.out.println("TEST COMPLETED.");

	}

	private static void concurrentTest(Context context) throws Exception {
		ExecutorService pool;

		pool = Executors.newFixedThreadPool(context.getTestEnvList().size() * 2);

		for (final String envId : context.getTestEnvList()) {			
			final Test test = new Test(context, envId);
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

	private static void concurrentDeploy(final Context context, ArrayList<String> envList) throws InterruptedException {
		ExecutorService pool;

		pool = Executors.newFixedThreadPool(envList.size());

		for (final String envId : envList) {
			pool.execute(new Runnable() {
				Deploy deploy;

				@Override
				public void run() {
					try {
						context.getFeedback().onDeployStart(envId);
						deploy = new Deploy(context, envId);
						deploy.deployAll();
						context.getFeedback().onDeployStop(envId);
					} catch (Exception e) {
						e.printStackTrace();
					} finally{
						deploy.close();
					}
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
	
	private static void backupTestResults(Context context) {
		if (CommonUtils.isWindowsPlatform()) {
			return;
		}

		String backupFileName = "";
		Calendar cal = Calendar.getInstance();
		int year = cal.get(Calendar.YEAR);
		int month = cal.get(Calendar.MONTH) + 1;
		int day = cal.get(Calendar.DAY_OF_MONTH);
		int hour = cal.get(Calendar.HOUR);
		int minute = cal.get(Calendar.MINUTE);
		int second = cal.get(Calendar.SECOND);
		String curTimestamp = year + "." + month + "." + day + "_" + hour + "." + minute + "." + second;

		backupFileName = "ha_repl_result_" + context.getBuildId() + "_" + context.getBuildBits() + "_" + context.getFeedback().getTaskId() + "_" + curTimestamp + ".tar.gz";
		LocalInvoker.exec("mkdir -p " + context.getCurrentLogDir() + " >/dev/null 2>&1 ; cd " + context.getCurrentLogDir() + "; tar zvcf ../" + backupFileName + " . " + " `cat "
				+ context.getCurrentLogDir() + "/test*.log | grep \"\\[FAIL\\]\" | awk '{print $2}' |sed 's/test$/*/'` ", false, true);
	}
	
	private static void checkRequirement(Context context) throws Exception {
		System.out.println("BEGIN TO CHECK: ");
		ArrayList<String> envList = context.getTestEnvList();
		CheckRequirement check;
		boolean pass = true;
		InstanceManager im;
		ArrayList<SSHConnect> sshList;
		for(String envId: envList) {
			im = new InstanceManager(context,envId);
			sshList = im.getAllNodeList();
			for(SSHConnect s: sshList) {
				check = new CheckRequirement(context, envId, s);
				if (!check.check()) {				
					pass = false;
				}
			}
			im.close();
		}
		if (pass) {
			System.out.println("CHECK RESULT: PASS");
		} else {
			System.out.println("CHECK RESULT: FAIL");
			System.out.println("QUIT");
			System.exit(-1);
		}
	}
}
