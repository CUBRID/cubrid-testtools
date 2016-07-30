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
package com.navercorp.cubridqa.ha_repl.impl;

import com.navercorp.cubridqa.ha_repl.Context;

import com.navercorp.cubridqa.ha_repl.Feedback;
import com.navercorp.cubridqa.ha_repl.HostManager;
import com.navercorp.cubridqa.common.Log;

public class FeedbackNull implements Feedback {

	public FeedbackNull(Context context) {

	}

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
	public void onConvertEventStart() {
		// TODO Auto-generated method stub

	}

	@Override
	public void onConvertEventStop() {
		// TODO Auto-generated method stub

	}

	@Override
	public void setTotalTestCase(int tbdNum, int macroSkippedNum, int tempSkippedNum) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onTestCaseStopEvent(String testCase, boolean flag, long elapseTime, String resultCont, String envIdentify, boolean isTimeOut, boolean hasCore, String skippedKind) {
		// TODO Auto-generated method stub

	}

	public int getTaskId() {
		return -1;
	}

	@Override
	public void onStopEnvEvent(HostManager hostManager, Log log) {

	}

}
