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

package com.navercorp.cubridqa.shell.common;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.util.HashMap;

import com.navercorp.cubridqa.common.ConfigParameterConstants;
import com.navercorp.cubridqa.shell.main.Context;

public class GeneralFeedback {

	private Context context;
	private String messageFilename;
	private HashMap<String, String> keyData;

	String currentKey;
	StringBuffer currentValue;

	public GeneralFeedback(String configFilename, String messageFilename) throws IOException {
		this.context = new Context(configFilename);
		this.messageFilename = messageFilename;
		String category = context.getProperty(ConfigParameterConstants.TEST_CATEGORY, "TEST_CATEGORY", false);
		if (CommonUtils.isEmpty(category)) {
			category = "general";
		}
		context.setTestCategory(category);
		this.context.setLogDir(category);

		keyData = new HashMap<String, String>();

		String buildId = context.getProperty(ConfigParameterConstants.TEST_BUILD_ID, "BUILD_ID", false);
		if (!CommonUtils.isEmpty(buildId)) {
			context.setTestBuild(buildId);
		}

		String buildBit = context.getProperty(ConfigParameterConstants.TEST_BUILD_BITS, "BUILD_BITS", false);
		if (!CommonUtils.isEmpty(buildBit)) {
			context.setVersion(buildBit);
		}
	}

	public static void main(String[] args) throws IOException {
		String messageFilename = args[1];
		GeneralFeedback test = new GeneralFeedback(args[0], messageFilename);

		System.out.println("STARTED");
		System.out.println("[%]SSTTAARRTT");
		test.receiveMessages();
	}

	private void receiveMessages() {
		File file = null;
		FileInputStream fis = null;
		InputStreamReader reader = null;
		LineNumberReader lineReader = null;
		String line = null;
		try {
			file = new File(messageFilename);
			if (file.exists()) {
				file.delete();
			}
			file.createNewFile();

			fis = new FileInputStream(file);
			reader = new InputStreamReader(fis, "UTF-8");
			lineReader = new LineNumberReader(reader);

			while (true) {
				line = lineReader.readLine();
				if (line != null) {
					if (CommonUtils.isEmpty(currentKey) == false && line.startsWith("KEYSTOP") == false) {
						currentValue.append(line).append(Constants.LINE_SEPARATOR);
						continue;
					}

					if (line.trim().startsWith("QUIT")) {
						System.out.println("[%]QQUUIITT");
						System.exit(0);
					} else {
						handleMessages(line);
						System.out.println("[%]DDOONNEE");
					}
					System.out.println(line);

				} else {
					try {
						Thread.sleep(1);
					} catch (InterruptedException e) {
					}
				}
			}

		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				if (lineReader != null)
					lineReader.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
			try {
				if (reader != null)
					reader.close();
			} catch (IOException e) {
				e.printStackTrace();
			}

			try {
				if (fis != null)
					fis.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

	}

	private void handleMessages(String line) {
		String[] cmd = line.split(" ");
		String method = cmd[0].toUpperCase().trim();

		if (method.equals("TASKSTART")) {
			String buildFilename = cmd.length > 1 ? cmd[1] : null;
			this.context.getFeedback().onTaskStartEvent(buildFilename);
			System.out.println("TASK_ID:" + this.context.getFeedback().getTestId());
		} else if (method.equals("TASKCONTINUE")) {
			this.context.getFeedback().onTaskContinueEvent();
		} else if (method.equals("TASKSTOP")) {
			this.context.getFeedback().onTaskStopEvent();
		} else if (method.equals("TOTALTESTCASE")) {
			int totalNum = cmd.length > 1 ? Integer.parseInt(cmd[1]) : 0;
			int macroSkippedNum = cmd.length > 2 ? Integer.parseInt(cmd[2]) : 0;
			int tempSkippedNum = cmd.length > 3 ? Integer.parseInt(cmd[3]) : 0;
			this.context.getFeedback().setTotalTestCase(totalNum, macroSkippedNum, tempSkippedNum);
		} else if (method.equals("TESTCASESTART")) {
			String testCase = cmd.length > 1 ? cmd[1] : null;
			String envIdentify = cmd.length > 2 ? cmd[2] : null;
			this.context.getFeedback().onTestCaseStartEvent(testCase, envIdentify);
		} else if (method.equals("TESTCASESTOP")) {
			String testCase = cmd.length > 1 ? cmd[1] : "null";
			boolean flag = cmd.length > 2 ? convertToBool(cmd[2]) : false;
			long elapseTime = cmd.length > 3 ? Long.parseLong(cmd[3]) : -1;
			String resultCont = cmd.length > 4 ? cmd[4] : "";
			String envIdentify = cmd.length > 5 ? cmd[5] : "local";
			boolean isTimeOut = cmd.length > 6 ? convertToBool(cmd[6]) : false;
			boolean hasCore = cmd.length > 7 ? convertToBool(cmd[7]) : false;
			String skippedKind = cmd.length > 8 ? cmd[8] : Constants.SKIP_TYPE_NO;
			int retryCount = cmd.length > 9 ? Integer.parseInt(cmd[9]) : 0;

			if (resultCont != null && resultCont.equals("\"\"") || resultCont.equals("''") || resultCont.equalsIgnoreCase("null")) {
				resultCont = null;
			}
			resultCont = explainKey(resultCont);
			this.context.getFeedback().onTestCaseStopEvent(testCase, flag, elapseTime, resultCont, envIdentify, isTimeOut, hasCore, skippedKind, retryCount);
		} else if (method.equals("KEYSTART")) {
			currentKey = null;
			currentValue = null;
			String key = cmd.length > 1 ? cmd[1] : null;
			if (CommonUtils.isEmpty(key)) {
				System.out.println("Error. Not found a named key.");
			} else {
				currentKey = key;
				currentValue = new StringBuffer();
			}
		} else if (method.equals("KEYSTOP")) {
			if (CommonUtils.isEmpty(currentKey) == false) {
				this.keyData.put(currentKey, currentValue.toString());
			}
			currentKey = null;
			currentValue = null;
		} else {
			System.out.println("Error. Not found such method: " + method);
		}
	}

	private String explainKey(String express) {
		if (express == null)
			return express;
		int p1 = express.indexOf("$[");
		if (p1 == -1)
			return express;
		int p2 = express.indexOf("]", p1);
		if (p2 == -1)
			return express;
		String key = express.substring(p1 + 2, p2);
		if (CommonUtils.isEmpty(key)) {
			return express;
		} else {
			return CommonUtils.replace(express, "$[" + key + "]", this.keyData.get(key));
		}

	}

	private boolean convertToBool(String v) {
		return com.navercorp.cubridqa.common.CommonUtils.convertBoolean(v);
	}
}
