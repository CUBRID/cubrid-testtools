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

package com.navercorp.cubridqa.shell.main;

import java.util.ArrayList;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.navercorp.cubridqa.shell.common.CommonUtils;
import com.navercorp.cubridqa.shell.common.Constants;
import com.navercorp.cubridqa.shell.deploy.Deploy;
import com.navercorp.cubridqa.shell.deploy.TestCaseGithub;
import com.navercorp.cubridqa.shell.deploy.TestCaseSVN;
import com.navercorp.cubridqa.shell.dispatch.Dispatch;

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
		this.feedback = context.getFeedback();
	}
	
	private void addSkippedTestCases(ArrayList<String> list, String skippedType) {
		if(list==null) {
			return;
		}
		for (String tc : list) {
			context.getFeedback().onTestCaseStopEvent(tc, false, -1, "", "", false, false, skippedType, 0);
		}
	}

	public void execute() throws Exception {
		ArrayList<String> stableEnvList = (ArrayList<String>) context.getEnvList().clone();
		if (context.isContinueMode) {
			System.out.println("============= FETCH TEST CASES ==================");
			Dispatch.init(context);
			if(Dispatch.getInstance().getTotalTbdSize() == 0){
				System.out.println("NO TEST CASE TO TEST WITH CONTINUE MODE!");
				return;
			}
			
			checkRequirement(context);
			feedback.onTaskContinueEvent();
			
			System.out.println("============= UPDATE TEST CASES ==================");
			concurrentSVNUpdate(stableEnvList);
			System.out.println("DONE");
			
		} else {
			checkRequirement(context);
			
			feedback.onTaskStartEvent(context.getCubridPackageUrl());
			System.out.println("============= UPDATE TEST CASES ==================");
			concurrentSVNUpdate(stableEnvList);
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
			addSkippedTestCases(Dispatch.getInstance().getMacroSkippedList(), Constants.SKIP_TYPE_BY_MACRO);
			addSkippedTestCases(Dispatch.getInstance().getTempSkippedList(), Constants.SKIP_TYPE_BY_TEMP);
		}
		
		System.out.println("The Number of Test Case : " + Dispatch.getInstance().getTotalTbdSize());
		
		System.out.println("============= DEPLOY ==================");
		concurrentDeploy(stableEnvList, false);
		System.out.println("DONE");
		
		System.out.println("============= TEST ==================");
		concurrentTest(stableEnvList, false);
		System.out.println("STARTED");

		startConfigMonitor();
		
		while (!isAllTestsFinished()) {
			CommonUtils.sleep(1);
		}
		this.testPool.shutdown();
		this.configPool.shutdown();
		
		feedback.onTaskStopEvent();
		
		CommonUtils.generateFailBackupPackage(context);
		System.out.println("TEST COMPLETE");
	}

	private void joinTest(ArrayList<String> envList) throws Exception {
		
		ArrayList<String> stableEnvList = (ArrayList<String>) envList.clone();
		concurrentSVNUpdate(stableEnvList);
		
		System.out.println("============= DEPLOY ==================");
		concurrentDeploy(stableEnvList, true); //later join
		System.out.println("DONE");
		
		System.out.println("============= TEST ==================");
		concurrentTest(stableEnvList, true);
		System.out.println("STARTED");
	}

	private synchronized void concurrentTest(ArrayList<String> envList, boolean laterJoined) throws Exception {

		for (final String envId : envList) {
			final Test test = new Test(context, envId, laterJoined);
			testMap.put(envId, test);
			final TestMonitor monitor = new TestMonitor(context, test);
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
			testPool.execute(new Runnable() {
				@Override
				public void run() {
					try {
						monitor.startMonitor();
					} catch (Exception e) {
						e.printStackTrace();
					} finally {
						monitor.close();
					}
				}
			});
		}
	}

	private void startConfigMonitor() throws Exception {
		configPool.execute(new Runnable() {
			@Override
			public void run() {				
				ArrayList<String> newEnvList, oldEnvList, addedList, deletedList;
				Test test;
				while (!Dispatch.getInstance().isFinished()) {

					CommonUtils.sleep(5);
					
					oldEnvList = (ArrayList<String>) context.getEnvList().clone();
					
					try{
						context.reload();
					}catch(Exception e) {
						System.out.println("[ERROR] fail to reload context ( " + e.getMessage() + ")");
					}
					
					if (context.isExecuteAtLocal()) {
						continue;
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
					boolean pass;
					String envId;
					for (int i = addedList.size() - 1; i >= 0; i--) {
						envId = addedList.get(i);
						try {
							pass = checkRequirement(context, envId);
						} catch (Exception e) {
							pass = false;
						}
						if (pass) {
							System.out.println("[ENV CHECK] " + envId + " PASS");
						} else {
							addedList.remove(i);
							System.out.println("[ENV CHECK] " + envId + " REMOVE");
						}
					}
					
					if (addedList.size() > 0) {
						System.out.println("[ENV ADD] " + addedList + " ...");						
						try {
							joinTest(addedList);
						} catch (Exception e) {
							e.printStackTrace();
						}
					}

				}
			}
		});
	}

	private void concurrentDeploy(final ArrayList<String> envList, final boolean laterJoined) throws InterruptedException {
		ExecutorService deployPool = Executors.newCachedThreadPool();
		ArrayList<Callable<Boolean>> callers = new ArrayList<Callable<Boolean>>();

		for (final String envId : envList) {
			callers.add(new Callable<Boolean>() {
				Deploy deploy;

				@Override
				public Boolean call() throws Exception {
					try {
						deploy = new Deploy(context, envId, laterJoined);
						deploy.deploy();
					} catch (Exception e) {
						return false;
					} finally{
						if(deploy!=null) deploy.close();						
					}
					return true;
					
				}
			});
		}
		deployPool.invokeAll(callers);
		deployPool.shutdown();
	}
	
	private void concurrentSVNUpdate(final ArrayList<String> envList) throws InterruptedException {
		ExecutorService pool = Executors.newCachedThreadPool();
		ArrayList<Callable<Boolean>> callers = new ArrayList<Callable<Boolean>>();

		for (final String envId : envList) {
			callers.add(new Callable<Boolean>() {

				@Override
				public Boolean call() throws Exception {
					TestCaseGithub git = null;
					TestCaseSVN svn = null;
					try {
						if (context.isScenarioInGit()) {
							git = new TestCaseGithub(context, envId);
							git.update();
						} else {
							svn = new TestCaseSVN(context, envId);
							svn.update();
						}
					} catch (Exception e) {
						e.printStackTrace();
						return false;
					} finally{
						if(git!=null) git.close();
						if(svn!=null) svn.close();
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
	
	private static void checkRequirement(Context context) throws Exception {
		System.out.println("BEGIN TO CHECK: ");
		
		boolean pass = true;

		for (String envId : context.getEnvList()) {
			if (!checkRequirement(context, envId)) {
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
	
	private static boolean checkRequirement(Context context, String envId) throws Exception {
		CheckRequirement check;
		ArrayList<String> relatedHosts;
		boolean pass = true;
		
		check = new CheckRequirement(context, envId, context.getInstanceProperty(envId, "ssh.host"), false);
		if (!check.check()) {
			pass = false;
		}

		relatedHosts = context.getRelatedHosts(envId);
		for (String h : relatedHosts) {
			check = new CheckRequirement(context, envId, h, true);
			if (!check.check()) {
				pass = false;
			}
		}
		return pass;
	}
}
