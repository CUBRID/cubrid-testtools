package com.navercorp.cubridqa.shell.main;

public interface Feedback {

	public void onTaskStartEvent(String buildFilename);

	public void onTaskContinueEvent();
	
	public void onTaskStopEvent();
	
	public void setTotalTestCase(int tbdNum, int macroSkippedNum, int tempSkippedNum);
	
	public void onTestCaseStartEvent(String testCase, String envIdentify);
	
	public void onTestCaseStopEvent(String testCase, boolean flag, long elapseTime, String resultCont, String envIdentify, boolean isTimeOut, boolean hasCore, String skippedKind, int retryCount);
	
	public void onTestCaseStopEventForRetry(String testCase, boolean flag, long elapseTime, String resultCont, String envIdentify, boolean isTimeOut, boolean hasCore, String skippedKind, int retryCount);
	
	public void onTestCaseMonitor(String testCase, String action, String envIdentify);
	
	public void onDeployStart(String envIdentify);
	
	public void onDeployStop(String envIdentify);

	public void onSvnUpdateStart(String envIdentify);
	
	public void onSvnUpdateStop(String envIdentify);
	
	public int getTestId();
	
	public void onStopEnvEvent(String envIdentify);

}
