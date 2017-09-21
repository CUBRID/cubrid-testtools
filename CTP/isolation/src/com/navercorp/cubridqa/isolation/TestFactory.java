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

package com.navercorp.cubridqa.isolation;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.navercorp.cubridqa.common.CommonUtils;
import com.navercorp.cubridqa.common.Log;
import com.navercorp.cubridqa.isolation.deploy.Deploy;
import com.navercorp.cubridqa.isolation.deploy.TestCaseGithub;
import com.navercorp.cubridqa.isolation.dispatch.Dispatch;
import com.navercorp.cubridqa.shell.common.LocalInvoker;

public class TestFactory {

	Context context;

	ExecutorService testPool, configPool;

	HashMap<String, Test> testMap;

	Feedback feedback;

	public TestFactory(Context context) {
		this.context = context;
		this.testPool = Executors.newFixedThreadPool(100);
		this.configPool = Executors.newFixedThreadPool(1);
		this.testMap = new HashMap<String, Test>();
		this.feedback = this.context.getFeedback();
	}

	public void execute() throws Exception {
		ArrayList<String> stableEnvList = (ArrayList<String>) context.getEnvList().clone();

		if (context.isContinueMode()) {
			createSnapshotForConfiguration();
			feedback.onTaskContinueEvent();
			System.out.println("============= FETCH TEST CASES ==================");
			Dispatch.init(context);
			if (Dispatch.getInstance().getTotalTbdSize() == 0) {
				System.out.println("NO TEST CASE TO TEST WITH CONTINUE MODE!");
				return;
			}

			checkRequirement(context);

			System.out.println("============= UPDATE TEST CASES ==================");
			concurrentUpdateScenarios(stableEnvList);
			System.out.println("DONE");

		} else {
			CommonUtils.cleanFilesByDirectory(context.getCurrentLogDir());
			createSnapshotForConfiguration();
			checkRequirement(context);
			feedback.onTaskStartEvent(context.getCubridPackageUrl());
			System.out.println("============= UPDATE TEST CASES ==================");
			concurrentUpdateScenarios(stableEnvList);
			System.out.println("DONE");

			System.out.println("============= FETCH TEST CASES ==================");
			Dispatch.init(context);
			if (Dispatch.getInstance().getTotalTbdSize() == 0) {
				System.out.println("NO TEST CASE TO TEST!");
				return;
			}
		}

		feedback.setTotalTestCase(Dispatch.getInstance().getTotalTbdSize(), Dispatch.getInstance().getMacroSkippedSize(), Dispatch.getInstance().getTempSkippedSize());
		if (context.isContinueMode() == false) {
			addSkippedTestCases(feedback, Dispatch.getInstance().getMacroSkippedList(), Constants.SKIP_TYPE_BY_MACRO);
			addSkippedTestCases(feedback, Dispatch.getInstance().getTempSkippedList(), Constants.SKIP_TYPE_BY_TEMP);
		}

		System.out.println("The Number of Test Case : " + Dispatch.getInstance().getTotalTbdSize());

		System.out.println("============= DEPLOY ==================");
		concurrentDeploy(stableEnvList);
		System.out.println("DONE");

		System.out.println("============= TEST ==================");
		concurrentTest(stableEnvList);
		System.out.println("STARTED");

		// startConfigMonitor();

		while (!isAllTestsFinished()) {
			CommonUtils.sleep(1);
		}
		this.testPool.shutdown();
		this.configPool.shutdown();

		feedback.onTaskStopEvent();

		System.out.println("TEST COMPLETE");

		backupTestResults();
	}

	public void backupTestResults() {
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

		backupFileName = "isolation_result_" + context.getBuildId() + "_" + context.getBuildBits() + "_" + context.getFeedback().getTaskId() + "_" + curTimestamp + ".tar.gz";
		LocalInvoker.exec("mkdir -p " + context.getCurrentLogDir() + " >/dev/null 2>&1 ; cd " + context.getCurrentLogDir() + "; tar zvcf ../" + backupFileName + " . ", false, false);
	}

	private static void addSkippedTestCases(Feedback feedback, ArrayList<String> list, String skippedType) {
		if (list == null) {
			return;
		}
		for (String tc : list) {
			feedback.onTestCaseStopEvent(tc, false, -1, "", "", false, false, skippedType);
		}
	}

	private void joinTest(ArrayList<String> envList) throws Exception {

		ArrayList<String> stableEnvList = (ArrayList<String>) envList.clone();
		// concurrentSVNUpdate(stableEnvList);

		// System.out.println("============= DEPLOY ==================");
		// concurrentDeploy(stableEnvList);
		// System.out.println("DONE");

		System.out.println("============= TEST ==================");
		concurrentTest(stableEnvList);
		System.out.println("STARTED");
	}

	private synchronized void concurrentTest(ArrayList<String> envList) throws Exception {

		for (final String envId : envList) {
			final Test test = new Test(context, envId);
			testMap.put(envId, test);

			testPool.execute(new Runnable() {
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

			// comment out monitor for isolation since ctl will trace the status
			// and resolve timeout case
			// final TestMonitor monitor = new TestMonitor(context, test);
			// testPool.execute(new Runnable() {
			// @Override
			// public void run() {
			// try {
			// monitor.startMonitor();
			// } catch (Exception e) {
			// e.printStackTrace();
			// } finally {
			// monitor.close();
			// }
			// }
			// });
		}
	}

	private void startConfigMonitor() throws Exception {
		configPool.execute(new Runnable() {
			@Override
			public void run() {
				Properties newConfig;
				ArrayList<String> newEnvList, oldEnvList, addedList, deletedList;
				Test test;
				while (!Dispatch.getInstance().isFinished()) {

					CommonUtils.sleep(5);

					oldEnvList = (ArrayList<String>) context.getEnvList().clone();

					try {
						context.reload();
					} catch (Exception e) {
						System.out.println("[ERROR] fail to reload context ( " + e.getMessage() + ")");
					}

					newEnvList = context.getEnvList();

					deletedList = (ArrayList<String>) oldEnvList.clone();
					deletedList.removeAll(newEnvList);

					if (deletedList.size() > 0) {
						System.out.println("[ENV DELETE] " + deletedList + " ...");

						for (String envId : deletedList) {
							test = testMap.get(envId);
							if (test != null) {
								test.stop();
								while (!test.isStopped()) {
									CommonUtils.sleep(1);
								}
								System.out.println("[ENV DELETE] " + envId + " DONE");
							} else {
								System.out.println("[ENV DELETE] " + envId + " NOT USED");
							}
						}
					}

					addedList = (ArrayList<String>) newEnvList.clone();
					addedList.removeAll(oldEnvList);

					if (addedList.size() > 0) {
						System.out.println("[ENV ADD] " + addedList + " ...");

						ArrayList<String> _addedList = (ArrayList<String>) addedList.clone();
						try {
							joinTest(_addedList);
						} catch (Exception e) {
							e.printStackTrace();
						}
					}

				}
			}
		});
	}

	private void concurrentDeploy(final ArrayList<String> envList) throws InterruptedException {
		ExecutorService deployPool = Executors.newCachedThreadPool();
		ArrayList<Callable<Boolean>> callers = new ArrayList<Callable<Boolean>>();

		for (final String envId : envList) {
			callers.add(new Callable<Boolean>() {
				Deploy deploy;

				@Override
				public Boolean call() throws Exception {
					try {
						deploy = new Deploy(context, envId);
						deploy.deploy();
					} catch (Exception e) {
						return false;
					} finally {
						if (deploy != null)
							deploy.close();
					}
					return true;

				}
			});
		}
		deployPool.invokeAll(callers);
		deployPool.shutdown();
	}

	private void checkRequirement(Context context) throws Exception {
		System.out.println("BEGIN TO CHECK: ");
		ArrayList<String> envList = context.getEnvList();
		CheckRequirement check;
		boolean pass = true;
		for (String envId : envList) {
			check = new CheckRequirement(context, envId);
			if (!check.check()) {
				pass = false;
			}
		}
		if (pass) {
			System.out.println("CHECK RESULT: PASS");
		} else {
			System.out.println("CHECK RESULT: FAIL");
			System.out.println("QUIT");
			System.exit(-1);
		}
	}

	private void concurrentUpdateScenarios(final ArrayList<String> envList) throws InterruptedException {
		ExecutorService pool = Executors.newCachedThreadPool();
		ArrayList<Callable<Boolean>> callers = new ArrayList<Callable<Boolean>>();

		for (final String envId : envList) {
			callers.add(new Callable<Boolean>() {
				TestCaseGithub testcasegit;

				@Override
				public Boolean call() throws Exception {
					try {
						testcasegit = new TestCaseGithub(context, envId);
						testcasegit.update();
					} catch (Exception e) {
						return false;
					} finally {
						if (testcasegit != null)
							testcasegit.close();
					}
					return true;
				}
			});
		}
		pool.invokeAll(callers);
		pool.shutdown();
	}

	private boolean isAllTestsFinished() {
		if (Dispatch.getInstance().isFinished() == false) {
			return false;
		}
		Set<String> set = testMap.keySet();
		Iterator<String> it = set.iterator();
		Test test;

		while (it.hasNext()) {
			test = testMap.get(it.next());
			if (test.isStopped() == false) {
				return false;
			}
		}
		return true;
	}
	
	private void createSnapshotForConfiguration() {
		Log contextSnapshot = new Log(CommonUtils.concatFile(context.getCurrentLogDir(), "main_snapshot.properties"), true, false);
		Properties props = context.getProperties();
		Set set = props.keySet();
		for (Object key : set) {
			contextSnapshot.println(key + "=" + props.getProperty((String) key));
		}
		contextSnapshot.println("AUTO_BUILD_ID=" + context.getBuildId());
		contextSnapshot.println("AUTO_BUILD_BITS=" + context.getBuildBits());
		contextSnapshot.close();
	}
}
