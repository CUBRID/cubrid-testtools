package com.navercorp.cubridqa.shell.result;

import com.navercorp.cubridqa.shell.main.Feedback;

public class FeedbackNull implements Feedback {

	@Override
	public void onTaskStartEvent(String buildFilename) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onTaskContinueEvent() {
		// TODO Auto-generated method stub

	}

	@Override
	public void onTaskStopEvent() {
		// TODO Auto-generated method stub

	}

	@Override
	public void setTotalTestCase(int tbdNum, int macroSkippedNum, int tempSkippedNum) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onTestCaseStopEvent(String testCase, boolean flag, long elapseTime, String resultCont, String envIdentify, boolean isTimeOut, boolean hasCore, String skippedType, int retryCount) {
		// TODO Auto-generated method stub

	}
	

	@Override
	public void onTestCaseStopEventForRetry(String testCase, boolean flag, long elapseTime, String resultCont, String envIdentify, boolean isTimeOut, boolean hasCore, String skippedKind , int retryCount) {
		// TODO Auto-generated method stub
		
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub
	}

	@Override
	public void onTestCaseStartEvent(String testCase, String envIdentify) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onTestCaseMonitor(String testCase, String action, String envIdentify) {
		// TODO Auto-generated method stub
		
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

	@Override
	public void onStopEnvEvent(String envIdentify) {
		// TODO Auto-generated method stub
		
	}


}
