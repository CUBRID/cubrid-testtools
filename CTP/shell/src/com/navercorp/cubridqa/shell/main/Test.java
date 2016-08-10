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


import java.util.Date;

import com.jcraft.jsch.JSchException;
import com.navercorp.cubridqa.shell.common.CommonUtils;
import com.navercorp.cubridqa.shell.common.Constants;
import com.navercorp.cubridqa.shell.common.Log;
import com.navercorp.cubridqa.shell.common.SSHConnect;
import com.navercorp.cubridqa.shell.common.ShellScriptInput;
import com.navercorp.cubridqa.shell.dispatch.Dispatch;

public class Test {

	Context context;
	String currEnvId;

	String testCaseFullName, testCaseName, testCaseDir, testCaseResultName;

	String sshHost, sshPort, sshUser, sshPwd;
	SSHConnect ssh;
	Log dispatchLog;
	Log workerLog;

	boolean testCaseSuccess;
	boolean isTimeOut = false;
	boolean hasCore = false;

	boolean shouldStop = false;
	boolean isStopped = false;
	boolean needDropTestCase = false;

	long startTime = 0;
	int maxRetryCount = 0;

	ArrayList<String> resultItemList = new ArrayList<String>();
	String envIdentify;

	public Test(Context context, String currEnvId, boolean laterJoined) throws JSchException {
		this.shouldStop = false;
		this.isStopped = false;
		this.isTimeOut = false;
		this.hasCore = false;
		this.context = context;
		this.currEnvId = currEnvId;
		this.startTime = 0;
		this.dispatchLog = new Log(CommonUtils.concatFile(context.getCurrentLogDir(), "dispatch_tc_FIN_" + currEnvId + ".txt"), false, laterJoined ? true : context.isContinueMode());
		this.workerLog = new Log(CommonUtils.concatFile(context.getCurrentLogDir(), "test_" + currEnvId + ".log"), false, true);

		sshHost = context.getInstanceProperty(currEnvId, "ssh.host");
		sshPort = context.getInstanceProperty(currEnvId, "ssh.port");
		sshUser = context.getInstanceProperty(currEnvId, "ssh.user");
		sshPwd = context.getInstanceProperty(currEnvId, "ssh.pwd");
		
		this.needDropTestCase = context.getProperty("main.testing.delete_test_case_after_execution", "false").trim().toLowerCase().equals("true");

		envIdentify = "EnvId=" + currEnvId + "[" + sshUser + "@" + sshHost + ":" + sshPort + "]";
		this.maxRetryCount = this.context.getMaxRetryCount();

		resetSSH();
	}

	public void runAll() throws JSchException {

		System.out.println("[ENV START] " + currEnvId);

		String testCase;
		Long endTime;
		String consoleOutput;
		
		int p;
		while (!shouldStop && !Dispatch.getInstance().isFinished()) {

			if (this.context.getServiceProtocolType()!=null && this.context.getServiceProtocolType().equals("rmi") && context.isWindows()) {
				ShellScriptInput aliveScript = new ShellScriptInput("echo HELLO");
				try {
					String aliveResult = ssh.execute(aliveScript);
					if (!aliveResult.trim().equals("HELLO")) {
						throw new Exception("result is not expected");
					}
				} catch (Exception e) {
					this.workerLog.println("ERROR: NOT ALIVE.");
					CommonUtils.sleep(1);
					continue;
				}
			}

			testCase = Dispatch.getInstance().nextTestFile();
			if (testCase == null) {
				break;
			}

			consoleOutput = "";
			this.testCaseFullName = testCase;
			p = testCase.lastIndexOf("cases");
			this.testCaseDir = testCase.substring(0, p + 5);
			this.testCaseName = testCase.substring(p + 6);
			this.testCaseResultName = testCaseName.substring(0, testCaseName.lastIndexOf(".sh")) + ".result";

			context.getFeedback().onTestCaseStartEvent(this.testCaseFullName, envIdentify);

			workerLog.println("[TESTCASE] " + this.testCaseFullName);

			boolean needRetry = true;
			int retryCount = 0;
			
			do {
				/*
				 * Reset test environment Kill CUBRID process, clear SSH and
				 * clear result item list
				 */
				resetProcess();
				resetCUBRID();
				resetSSH();
				startTime = -1;
				if (this.context.enableCheckDiskSpace()) {
					checkDiskSpace();
				}
				
				resultItemList.clear();
				startTime = System.currentTimeMillis();
				this.isTimeOut = false;
				this.testCaseSuccess = true;
				this.hasCore = false;

				try {
					consoleOutput = runTestCase();
					doFinalCheck();					
					collectGeneralResult();
				} catch (Exception e) {
					this.addResultItem("NOK", "Runtime error (" + e.getMessage() + ")");
				} finally {
					endTime = System.currentTimeMillis();

					StringBuffer resultCont = new StringBuffer();
					for (String item : this.resultItemList) {
						if (testCaseSuccess) {
							if (item.indexOf("NOK") != -1) {
								this.testCaseSuccess = false;
							}
						}
						if (hasCore == false) {
							if (item.indexOf("NOK found core file") != -1 || item.indexOf("NOK found fatal error") != -1) {
								this.hasCore = true;
							}
						}

						workerLog.println(item);
						resultCont.append(item).append(Constants.LINE_SEPARATOR);
					}
					
					if (testCaseSuccess == false && hasCore == false && context.getEnableSaveNormalErrorLog()== true){
						String saveErrorLogResult = doSaveNormalErrorLog();
						resultCont.append(saveErrorLogResult).append(Constants.LINE_SEPARATOR);
					}	
					if (testCaseSuccess == false) {
						System.out.println("Execute retry Time - " + retryCount + ", Max retry count - " + maxRetryCount);
						workerLog.println("Execute retry Time - " + retryCount + ", Max retry count - " + maxRetryCount);
						if (hasCore) {
							needRetry = false;
						} else {
							needRetry = true;
						}
						
						resultCont.append("============================= CONSOLE OUTPUT =============================").append(Constants.LINE_SEPARATOR);
						resultCont.append(consoleOutput);

					} else {
						needRetry = false;
					}
					//If retryCount already reach the maxRetryCount, tool need stop retry
					if(retryCount >= maxRetryCount)
					{
						needRetry = false;
					}
					
					if (needRetry) {
						/*
						 * For each testing, the retry count just will be updated as 1.
						 */
						context.getFeedback().onTestCaseStopEventForRetry(this.testCaseFullName, testCaseSuccess, endTime - startTime, resultCont.toString(), envIdentify, isTimeOut, hasCore,
								Constants.SKIP_TYPE_NO, 1);
					} else {
						context.getFeedback().onTestCaseStopEvent(this.testCaseFullName, testCaseSuccess, endTime - startTime, resultCont.toString(), envIdentify, isTimeOut, hasCore,
								Constants.SKIP_TYPE_NO, retryCount);
						System.out.println("[TESTCASE] " + this.testCaseFullName + " EnvId=" + this.currEnvId + " " + (testCaseSuccess ? "[OK]" : "[NOK]"));
					}					
					
					workerLog.println("");
					retryCount++;

				}
			} while(needRetry);
			
			if (needDropTestCase) {
				dropTestCaseAfterTest();
			}			
			dispatchLog.println(this.testCaseFullName);
		}

		isStopped = true;
		close();
		context.getFeedback().onStopEnvEvent(currEnvId);
		System.out.println("[ENV STOP] " + currEnvId);
	}

	public String runTestCase() throws Exception {
		if (this.context.isWindows) {
			return runTestCase_windows();
		} else {
			return runTestCase_linux();
		}
	}

	public String runTestCase_windows() throws Exception {

		ShellScriptInput script;
		String result;

		script = new ShellScriptInput("cd ");
		script.addCommand("cd " + testCaseDir);

		script.addCommand("export TEST_BIG_SPACE=$(echo $TEST_BIG_SPACE)");
		script.addCommand("export TEST_BIG_SPACE=`if [ \"$TEST_BIG_SPACE\" = '' ]; then echo " + context.getBigSpaceDir() + " ; else echo $TEST_BIG_SPACE; fi`");
		script.addCommand("if [ \"${TEST_BIG_SPACE}\" != '' ]; then export TEST_BIG_SPACE=`cygpath ${TEST_BIG_SPACE}`; fi");
		script.addCommand("if [ \"$TEST_BIG_SPACE\" != '' ]; then mkdir -p $TEST_BIG_SPACE; rm -rf $TEST_BIG_SPACE/*; fi");

		if (context.getDefaultDbcharset() != null && context.getDefaultDbcharset().trim().equals("") == false) {
			script.addCommand("export CUBRID_CHARSET=" + context.getDefaultDbcharset());
		}

		script.addCommand("sh " + testCaseName + " 2>&1");
		result = ssh.execute(script);

		workerLog.println(result);
		
		waitNetReady();//to make sure the TCP connection of  next case and result collection will be established

		return result;
	}

	public String runTestCase_linux() throws Exception {

		ShellScriptInput script;
		String result;

		script = new ShellScriptInput("cd " + testCaseDir);
		script.addCommand("ulimit -c unlimited");
		script.addCommand("if [ \"$JAVA_HOME_" + context.getVersion().trim().toUpperCase() + "\" ]; then");
		script.addCommand("        export JAVA_HOME=$JAVA_HOME_" + (context.getVersion().trim().toUpperCase()));
		script.addCommand("fi");
		script.addCommand("export TEST_BIG_SPACE=$(echo $TEST_BIG_SPACE)");
		script.addCommand("export TEST_BIG_SPACE=`if [ \"$TEST_BIG_SPACE\" = '' ]; then echo " + context.getBigSpaceDir() + " ; else echo $TEST_BIG_SPACE; fi`");
		script.addCommand("if [ \"$TEST_BIG_SPACE\" != '' ]; then mkdir -p $TEST_BIG_SPACE; rm -rf $TEST_BIG_SPACE/*; fi");

		if (context.getDefaultDbcharset() != null && context.getDefaultDbcharset().trim().equals("") == false) {
			script.addCommand("export CUBRID_CHARSET=" + context.getDefaultDbcharset());
		}

		String excludedCoresByAssertLine = context.getProperty("main.testing.excluded_cores_by_assert_line");
		if (excludedCoresByAssertLine != null && excludedCoresByAssertLine.trim().equals("") == false) {
			script.addCommand("export EXCLUDED_CORES_BY_ASSERT_LINE=\"" + excludedCoresByAssertLine + "\"" );
		}

		script.addCommand("echo > " + testCaseResultName);
		script.addCommand("sh " + testCaseName + " 2>&1");
		result = ssh.execute(script);

		workerLog.println(result);
        
		return result;
	}

	public void waitNetReady() throws Exception {
		String result;

		int timeout = 60 * 4;
		long startTime = System.currentTimeMillis();
		long currentTime;
		long len;
		
		String cubridPortId = context.getInstanceProperty(this.currEnvId, "cubrid.cubrid_port_id", "1523");
		String brokerFirstPort = context.getInstanceProperty(this.currEnvId, "broker1.BROKER_PORT", "30000");
		String brokerSecondPort = context.getInstanceProperty(this.currEnvId, "broker2.BROKER_PORT", "33000");
		String haPortId = context.getInstanceProperty(this.currEnvId, "ha.ha_port_id", "59901");
		String cmPortId = context.getInstanceProperty(this.currEnvId, "cm.cm_port", "8001");

		ShellScriptInput script = new ShellScriptInput("netstat -abfno | grep -E 'TIME_WAIT|FIN_WAIT1|FIN_WAIT2|CLOSING' | grep -E ':" + brokerFirstPort + "|:" + brokerSecondPort + "|:" + cubridPortId + "|:"
				+ haPortId + "|:" + cmPortId + "' | wc -l");
		while (true) {
			currentTime = System.currentTimeMillis();
			len = (currentTime - startTime) / 1000;

			if (len > timeout) {
				workerLog.println("NET NOT READY AND TIMEOUT: " + len);
				break;
			}

			result = ssh.execute(script).trim();
			if (result.equals("0")) {
				workerLog.println("NET GOOD in " + len + " seconds");
				break;
			}
			workerLog.println("NET fail: " + result + "(" + len + " secons)");
			CommonUtils.sleep(1);
		}
	}

	public boolean isTestCaseSkipped() throws Exception {

		String key = this.context.getTestCaseSkipKey();

		if (key == null || key.equals("")) {
			return false;
		}

		ShellScriptInput script;
		String result;

		script = new ShellScriptInput("cd " + testCaseDir);
		script.addCommand("grep " + key + " " + testCaseName);
		result = ssh.execute(script);

		result = result.replace('\r', '\n');
		String[] items = result.split("\n");
		for (String s : items) {
			s = s.replace('\t', ' ');
			s = CommonUtils.replace(s, " ", "");

			if (s.startsWith(key)) {
				return true;
			}
		}
		return false;
	}

	public void close() {

		resetProcess();
		this.dispatchLog.close();
		this.workerLog.close();

		if (ssh != null)
			ssh.close();
		isStopped = true;
		shouldStop = true;
	}

	public void resetCUBRID() {
		if (context.isWindows) {
			resetCUBRID_windows();
		} else {
			resetCUBRID_linux();
		}
	}

	public String resetCUBRID_windows() {
		ShellScriptInput scripts = new ShellScriptInput();
		scripts.addCommand("cd $CUBRID/..");
		scripts.addCommand("rm -rf CUBRID/conf/*");
		scripts.addCommand("cp -rf .CUBRID_SHELL_FM/conf/* CUBRID/conf/");
		scripts.addCommand("rm -rf CUBRID/databases/*");
		scripts.addCommand("cp -rf .CUBRID_SHELL_FM/databases/* CUBRID/databases/");
		scripts.addCommand("rm -rf CUBRID/lib/libcubrid_??_??.dll");
		scripts.addCommand("rm -rf CUBRID/lib/libcubrid_all_locales.dll");
		scripts.addCommand("ls CUBRID/conf/*");
		scripts.addCommand("find CUBRID/log -type f -print | xargs -i rm -rf {} ");

		String result = "";
		try {
			result = ssh.execute(scripts);
			workerLog.println("[INFO] Reset CUBRID: " + result);
		} catch (Exception e) {
			workerLog.println("[ERROR] Fail to reset CUBRID (" + e.getMessage() + ")");
		}

		return result;
	}

	public void resetCUBRID_linux() {
		ShellScriptInput scripts = new ShellScriptInput();
		scripts.addCommand("rm -rf ~/CUBRID/conf/*");
		scripts.addCommand("cp -rf ~/.CUBRID_SHELL_FM/conf/* ~/CUBRID/conf/");
		scripts.addCommand("rm -rf ~/CUBRID/databases/*");
		scripts.addCommand("cp -rf ~/.CUBRID_SHELL_FM/databases/* ~/CUBRID/databases/");
		scripts.addCommand("rm -rf ~/CUBRID/lib/libcubrid_??_??.so");
		scripts.addCommand("rm -rf ~/CUBRID/lib/libcubrid_all_locales.so");
		scripts.addCommand("find ~/CUBRID/log -type f -print | xargs -i rm -rf {} ");
        scripts.addCommand("find ~/CUBRID/ -name \"core.[0-9][0-9]*\" | xargs -i rm -rf {} ");
        scripts.addCommand("find ~/CUBRID/ -name \"core\" | xargs -i rm -rf {} ");
        
		ArrayList<String> relatedHosts = context.getRelatedHosts(currEnvId);
		String serviceProtocol = context.getServiceProtocolType();
		if (relatedHosts.size() > 0) {
			SSHConnect sshRelated;
			for (String h : relatedHosts) {
				sshRelated = null;
				try {
					sshRelated = new SSHConnect(h, sshPort, sshUser, sshPwd, serviceProtocol);
					sshRelated.execute(scripts);
					workerLog.println("[INFO] remove core file successfully on " + h + ".");
				} catch (Exception e) {
					workerLog.println("[INFO] fail to remove core file on " + h + ":" + e.getMessage());
				} finally {
					if (sshRelated != null) {
						sshRelated.close();
					}
				}

			}
		}

        scripts.addCommand("find " + this.testCaseDir + " -name \"core.[0-9][0-9]*\" | xargs -i rm -rf {} ");
        scripts.addCommand("find " + this.testCaseDir + " -name \"core\" | xargs -i rm -rf {} ");

		String result;
		try {
			result = ssh.execute(scripts);
			workerLog.println("[INFO] Reset CUBRID: " + result);
		} catch (Exception e) {
			workerLog.println("[ERROR] Fail to reset CUBRID (" + e.getMessage() + ")");
		}
	}

	public void resetSSH() throws JSchException {
		try {
			if (ssh != null)
				ssh.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		this.ssh = new SSHConnect(sshHost, sshPort, sshUser, sshPwd, context.getServiceProtocolType());
	}

	public void resetProcess() {
		String result = CommonUtils.resetProcess(ssh, context.isWindows);
		workerLog.println("[INFO] CLEAN PROCESSES: " + result);
		
		ArrayList<String> relatedHosts = context.getRelatedHosts(currEnvId);
		if (relatedHosts == null || relatedHosts.size() == 0) {
			return;
		}
		
		SSHConnect sshRelated;
		for (String h : relatedHosts) {
			sshRelated = null;
			try {
				sshRelated = new SSHConnect(h, sshPort, sshUser, sshPwd, context.getServiceProtocolType());
				result = CommonUtils.resetProcess(sshRelated, context.isWindows);
				workerLog.println("[INFO] CLEAN PROCESSES(" + h + "): " + result);
			} catch (Exception e) {
				workerLog.println("[ERROR] CLEAN PROCESSES(" + h + "): " + e.getMessage());
			} finally {
				if (sshRelated != null) {
					sshRelated.close();
				}
			}
		}
	}

	public void collectGeneralResult() {

		String result = "";		
		ShellScriptInput scripts;
		
		scripts = new ShellScriptInput();
		scripts.addCommand("cd ");
		scripts.addCommand("cd " + testCaseDir);
		scripts.addCommand("cat " + testCaseResultName);

		try {
			int retryCount = 5;
			do {
				result = ssh.execute(scripts);
				CommonUtils.sleep(1);
			} while (retryCount-- > 0 && result.trim().length() == 0);

			if (result.trim().equals("")) {
				addResultItem("NOK", "blank result - " + new Date() + " - " + testCaseDir + "\\" + testCaseResultName);
			}

			if (result.trim().equals("")) {
				addResultItem("NOK", "blank result");
			}

			String[] itemArrary = result.split("\n");
			if (itemArrary != null) {
				for (String item : itemArrary) {
					if (!item.trim().equals("")) {
						addResultItem(null, item);
					}
				}
			}
		} catch (Exception e) {
			addResultItem("NOK", "Runtime error (" + e.getMessage() + ")");
		}
	}

	public void dropTestCaseAfterTest() {
		
		if (testCaseDir == null || testCaseDir.trim().equals("")) {
			return;
		}
		
		String result;
		ShellScriptInput scripts;
		scripts = new ShellScriptInput();
		scripts.addCommand("rm -rf " + testCaseDir.trim() + "/*");
		try {
			result = ssh.execute(scripts);
			workerLog.println("[INFO] Done for removing files after test." + result);
		} catch (Exception e) {
			workerLog.println("[ERROR] fail to drop files after test. " + e.getMessage());
		}
	}
	
	private void doFinalCheck() {
		String result = null;

		ShellScriptInput scripts = new ShellScriptInput("source /dev/stdin <<EOF");
		scripts.addCommand("`grep -E \"SKIP_CHECK_FATAL_ERROR\" " + this.testCaseFullName + " `");
		scripts.addCommand("EOF");
		
		String excludedCoresByAssertLine = context.getProperty("main.testing.excluded_cores_by_assert_line");
		if (excludedCoresByAssertLine != null && excludedCoresByAssertLine.trim().equals("") == false) {
			scripts.addCommand("export EXCLUDED_CORES_BY_ASSERT_LINE=\"" + excludedCoresByAssertLine + "\"" );
		}
		
		scripts.addCommand("source $init_path/shell_utils.sh && do_check_more_errors \"" + this.testCaseDir + "\"");
		if (context.isWindows == false) {
			try {
				result = ssh.execute(scripts);
			} catch (Exception e) {
				addResultItem("NOK", "Runtime error. Fail to check more errors on main host " + sshHost + ": " + e.getMessage());
			}
		}

		ArrayList<String> relatedHosts = context.getRelatedHosts(currEnvId);
		String serviceProtocol = context.getServiceProtocolType();
		if (relatedHosts.size() > 0) {
			SSHConnect sshRelated;
			for (String h : relatedHosts) {
				sshRelated = null;
				result = null;
				try {
					sshRelated = new SSHConnect(h, sshPort, sshUser, sshPwd, serviceProtocol);
					result = sshRelated.execute(scripts);
					String[] itemArrary = result.split("\n");
					if (itemArrary != null) {
						for (String item : itemArrary) {
							if (!item.trim().equals("")) {
								addResultItem(null, item);
							}
						}
					}
				} catch (Exception e) {
					addResultItem("NOK", "Runtime error. Fail to connect to the related host " + h + ": " + e.getMessage());
				} finally {
					if (sshRelated != null) {
						sshRelated.close();
					}
				}
			}
		}
	}

	public void addResultItem(String flag, String message) {
		if (flag == null)
			this.resultItemList.add(message);
		else
			this.resultItemList.add(" : " + flag + " " + message);
	}

	public void stop() {
		this.shouldStop = true;
	}

	public boolean isStopped() {
		return this.isStopped;
	}

	public String getCurrentEnvId() {
		return this.currEnvId;
	}
	
	public void checkDiskSpace() throws JSchException {
		checkDiskSpace(ssh, false);
			
		ArrayList<String> relatedHosts = context.getRelatedHosts(currEnvId);
		String serviceProtocol = context.getServiceProtocolType();
		if (relatedHosts.size() > 0) {
			SSHConnect sshRelated;
			for (String h : relatedHosts) {
				sshRelated = new SSHConnect(h, sshPort, sshUser, sshPwd, serviceProtocol);
				checkDiskSpace(sshRelated, true);
			}
		}
	}
	
	private void checkDiskSpace(SSHConnect ssh1, boolean closeSSH) {
		
		ShellScriptInput scripts = new ShellScriptInput();
		scripts.addCommand("source ${init_path}/../../common/script/util_common.sh");
		scripts.addCommand("check_disk_space `df -P $HOME | grep -v Filesystem | awk '{print $1}'` 2G " + context.getMailNoticeTo());
		String result;
		try {
			result = ssh1.execute(scripts);
			workerLog.println("[INFO] Check disk space PASS on " + ssh1.toString());
		} catch (Exception e) {
			workerLog.println("[FAIL] Check disk space FAIL on " + ssh1.toString());
			workerLog.println(e.getMessage());
		} finally {
			if(closeSSH) {
				if (ssh1 != null) {
					ssh1.close();
				}
			}
		}
	}
	
	public String doSaveNormalErrorLog() throws JSchException {
		String ret = "";		
		ShellScriptInput scripts = new ShellScriptInput();
		scripts.addCommand("source $init_path/shell_utils.sh && do_save_normal_error_logs \"" + this.testCaseDir + "\"");
		StringBuffer sb = new StringBuffer();
		String result = "";
		try {
			result = ssh.execute(scripts);
			workerLog.println(result);
			sb.append("[INFO] Normal error log locations:" + result).append(Constants.LINE_SEPARATOR);
			workerLog.println("[INFO] finish save log successfully on" + result);
		} catch (Exception e) {
			workerLog.println("[ERROR] fail to save log on " + e.getMessage());
		}
			
		ArrayList<String> relatedHosts = context.getRelatedHosts(currEnvId);
		String serviceProtocol = context.getServiceProtocolType();
		if (relatedHosts.size() > 0) {
			SSHConnect sshRelated;
			for (String h : relatedHosts) {
				sshRelated = null;
				try {
					sshRelated = new SSHConnect(h, sshPort, sshUser, sshPwd, serviceProtocol);
					sshRelated.execute(scripts);
				    sb.append("[INFO] Normal error log locations on related server:" + result).append(Constants.LINE_SEPARATOR);
					workerLog.println("[INFO] finish save log successfully on " + h + ".");
				} catch (Exception e) {
					workerLog.println("[ERROR] fail to save log on " + h + ":" + e.getMessage());
				} finally {
					if (sshRelated != null) {
						sshRelated.close();
					}
				}
			}
		}
		ret = sb.toString();
		return ret;
	}
	

}
