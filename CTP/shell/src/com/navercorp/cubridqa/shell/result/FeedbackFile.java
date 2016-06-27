package com.navercorp.cubridqa.shell.result;

import java.util.ArrayList;
import java.util.Date;

import com.jcraft.jsch.JSchException;
import com.navercorp.cubridqa.shell.common.CommonUtils;
import com.navercorp.cubridqa.shell.common.Constants;
import com.navercorp.cubridqa.shell.common.Log;
import com.navercorp.cubridqa.shell.common.SSHConnect;
import com.navercorp.cubridqa.shell.common.ShellInput;
import com.navercorp.cubridqa.shell.deploy.DeployOneNode;
import com.navercorp.cubridqa.shell.main.Context;
import com.navercorp.cubridqa.shell.main.Feedback;

public class FeedbackFile implements Feedback {
	
	String logName;
	Log feedbackLog;
	long taskStartTime;
	Context context;
	
	public FeedbackFile(Context context){
		logName = CommonUtils.concatFile(Constants.DIR_LOG_ROOT, "feedback.log");
		this.context = context;
	}
	
	@Override
	public void onTaskStartEvent(String buildFilename) {
		feedbackLog = new Log(logName, false, false);
		taskStartTime = System.currentTimeMillis();
		println("[TASK START] Current Time is " + new Date() + ", start MSG Id is " + this.context.getMsgId());
	}

	@Override
	public void onTaskContinueEvent() {
		feedbackLog = new Log(logName, false, true);
		println("[TASK CONTINUE] Current Time is " + new Date());
	}

	@Override
	public void onTaskStopEvent() {
		long taskStopTime = System.currentTimeMillis();
		println("[TEST STOP] Current Time is " + new Date(), "Elapse Time:" + ((taskStopTime - this.taskStartTime)));
		feedbackLog.close();
	}

	@Override
	public void setTotalTestCase(int tbdNum, int macroSkippedNum, int tempSkippedNum) {
		println("The Number of Test Cases: " + tbdNum + " (macro skipped: " + macroSkippedNum + ", bug skipped: " + tempSkippedNum + ")");
	}

	@Override
	public void onTestCaseStopEvent(String testCase, boolean flag, long elapseTime, String resultCont, String envIdentify, boolean isTimeOut, boolean hasCore, String skippedType, int retryCount) {
		String head;
		if (skippedType.equals(Constants.SKIP_TYPE_NO)) {
			head = flag ? "[OK]: " : "[NOK]: " + Constants.RETRY_FLAG + " = " + retryCount;	
		} else if (skippedType.equals(Constants.SKIP_TYPE_BY_MACRO)) {
			head = "[SKIP_BY_MACRO]";
		} else if (skippedType.equals(Constants.SKIP_TYPE_BY_TEMP)) {
			head = "[SKIP_BY_BUG]";
		} else {
			head = "[UNKNOWN]";
		}
		
		println(head + " " + testCase + " " + elapseTime + " " + envIdentify, resultCont, "");
	}
	
	@Override
	public void onTestCaseStopEventForRetry(String testCase, boolean flag, long elapseTime, String resultCont, String envIdentify, boolean isTimeOut, boolean hasCore, String skippedType, int retryCount) {
		onTestCaseStopEvent(testCase, flag, elapseTime, resultCont, envIdentify, isTimeOut, hasCore, skippedType, retryCount);
	}

	@Override
	public void onTestCaseStartEvent(String testCase, String envIdentify) {
		// TODO Auto-generated method stub
	}
	
	private synchronized void println(String... conts){
		for(String cont: conts){
			feedbackLog.println(cont);
		}
	}

	@Override
	public void onTestCaseMonitor(String testCase, String action, String envIdentify) {
		println(action + " " + testCase + " " + envIdentify);		
	}

	@Override
	public void onDeployStart(String envIdentify) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onDeployStop(String envIdentify) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onSvnUpdateStart(String envIdentify) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onSvnUpdateStop(String envIdentify) {
		// TODO Auto-generated method stub
	}
	
	@Override
	public int getTestId() {
		return 0;
	}
	
	private void collectCoverageOnOneNode(Context context, String currEnvId, String host){
		SSHConnect ssh = null;
		println("[Code Coverage] Current Time is " + new Date());
		feedbackLog.print("[Code Coverage] start code coverage data collection!");
		String covHost = context.getProperty("main.coverage.controller.ip", "").trim();
		String covUser = context.getProperty("main.coverage.controller.user", "").trim();
		String covPwd = context.getProperty("main.coverage.controller.pwd", "").trim();
		String covPort = context.getProperty("main.coverage.controller.port", "").trim();
		String covTargetDir = context.getProperty("main.coverage.controller.result", "").trim();
		String category = context.getProperty("main.testing.category");//flag will mark category as a new for slave node 
		String covParams = "-n " + context.getTestBuild() + " -c " + category + " -user " + covUser + " -pwd '" + covPwd + "' -host " + covHost + " -to " + covTargetDir + " -port " + covPort;
		
		String port = context.getProperty("env." + currEnvId + ".ssh.port");
		String user = context.getProperty("env." + currEnvId + ".ssh.user");
		String pwd = context.getProperty("env." + currEnvId + ".ssh.pwd");
		String envIdentify = "EnvId=" + currEnvId + "[" + user + "@" + host + ":" + port + "]";
		try {
			ssh = new SSHConnect(host, port, user, pwd);

			ShellInput scripts = new ShellInput();
			scripts.addCommand("run_coverage_collect_and_upload " + covParams);
			String result;
			try {
				result = ssh.execute(scripts);
				feedbackLog.println(result);
			} catch (Exception e) {
				feedbackLog.print("[ERROR] " + e.getMessage());
			}
		} catch (JSchException jschE) {
			jschE.printStackTrace();
		}finally{
			if(ssh != null){
				ssh.close();
			}
			
			println("[Code Coverage] end code coverage collection on " + envIdentify);
			feedbackLog.print("[Code Coverage] end code coverage collection on " + envIdentify);
		}
	}

	@Override
	public void onStopEnvEvent(String envIdentify) {
		String role = context.getProperty("main.testing.role", "").trim();
		String host = context.getProperty("env." + envIdentify + ".ssh.host");
		
		if (role.indexOf("coverage") != -1) {
			collectCoverageOnOneNode(this.context, envIdentify, host);
			
			ArrayList<String> relatedHosts = context.getRelatedHosts(envIdentify);
			int idx = 0;
			for (String h : relatedHosts) {
				collectCoverageOnOneNode(this.context, envIdentify, h);
				idx++;
			}

		}

	}
	
}
