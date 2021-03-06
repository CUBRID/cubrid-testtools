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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Properties;

import com.navercorp.cubridqa.common.ConfigParameterConstants;
import com.navercorp.cubridqa.common.LocalInvoker;
import com.navercorp.cubridqa.shell.common.CommonUtils;
import com.navercorp.cubridqa.shell.common.Constants;

public class GeneralLocalTest {

	private Context context;

	public GeneralLocalTest(Context context) {
		this.context = context;

		String category = context.getProperty(ConfigParameterConstants.TEST_CATEGORY, (ConfigParameterConstants.TEST_CATEGORY).toUpperCase(), false);
		if (CommonUtils.isEmpty(category)) {
			category = "general";
		}
		context.setTestCategory(category);

		String logDirname = System.getProperty("TEST_TYPE");
		if (CommonUtils.isEmpty(logDirname)) {
			logDirname = "general";
		}
		this.context.setLogDir(logDirname);

		String buildId = context.getProperty(ConfigParameterConstants.TEST_BUILD_ID, (ConfigParameterConstants.TEST_BUILD_ID).toUpperCase(), false);
		if (!CommonUtils.isEmpty(buildId)) {
			context.setTestBuild(buildId);
		}

		String buildBit = context.getProperty(ConfigParameterConstants.TEST_BUILD_BITS, (ConfigParameterConstants.TEST_BUILD_BITS).toUpperCase(), false);
		if (!CommonUtils.isEmpty(buildBit)) {
			context.setVersion(buildBit);
		}
	}

	public void start() {
		System.out.println("=> Init Step: ");
		System.out.println(invoke("init"));

		System.out.println();

		ArrayList<String> testCaseList = getTestCaseList();
		if (testCaseList == null || testCaseList.size() == 0) {
			System.out.println("[ERROR] Not found any test cases.");
			return;
		}

		context.getFeedback().onTaskStartEvent(context.getTestBuild());
		context.getFeedback().setTotalTestCase(testCaseList.size(), 0, 0);
		Result result;
		String testCaseName;
		boolean isSucc;

		System.out.println("=> Execute Step: ");
		for (int i = 0; i < testCaseList.size(); i++) {
			testCaseName = testCaseList.get(i);

			context.getFeedback().onTestCaseStartEvent(testCaseName, "local");
			System.out.print("[TESTCASE-" + (i + 1) + "] " + testCaseName);
			result = invoke("execute " + testCaseName + " 2>&1 ", "IS_SUCC");
			isSucc = com.navercorp.cubridqa.common.CommonUtils.convertBoolean(result.getProperty("IS_SUCC"));
			System.out.println(isSucc ? " [SUCC]" : " [FAIL]");
			context.getFeedback().onTestCaseStopEvent(testCaseName, isSucc, 0, result.getOutput(), "local", false, false, Constants.SKIP_TYPE_NO, 0);
		}

		System.out.println("=> Finish Step: ");
		System.out.println(invoke("finish"));

		System.out.println();
		context.getFeedback().onTaskStopEvent();
	}

	private ArrayList<String> getTestCaseList() {
		System.out.println("=> List Step: ");
		Result output = invoke("list");
		System.out.println(output);
		System.out.println();
		String[] arr = output.getOutput().split("\n");
		ArrayList<String> list = new ArrayList<String>();
		for (String i : arr) {
			if(i.trim().equals("")) {
				continue;
			}
			list.add(i);
		}
		return list;
	}

	private Result invoke(String scripts) {
		return invoke(scripts, (String[]) null);
	}

	private Result invoke(String scripts, String... keys) {
		if (scripts == null)
			return null;

		String testType = context.getProperty("TEST_TYPE", "TEST_TYPE", false);

		scripts = "cd ${CTP_HOME}; source shell/local/" + testType + ".sh; " + scripts;
		if (keys != null && keys.length > 0) {
			scripts = scripts + "; echo GPROPSTART\n";
			for (String k : keys) {
				scripts = scripts + "echo G_PROPERTY_" + k + "=${" + k + "}EEOOKK\n";
			}
		}
		String result = LocalInvoker.exec(scripts, LocalInvoker.SHELL_TYPE_LINUX, false);

		String output;
		int pos = result.indexOf("GPROPSTART");
		if (pos == -1) {
			output = result;
		} else {
			output = result.substring(0, pos);
		}

		Properties props = null;
		if (keys != null && keys.length > 0) {
			props = new Properties();
			String rKey, rValue;
			int p1, p2;
			for (String k : keys) {
				rKey = "G_PROPERTY_" + k + "=";
				p1 = result.indexOf(rKey);
				if (p1 == -1)
					continue;
				p2 = result.indexOf("EEOOKK", p1);
				rValue = result.substring(p1 + rKey.length(), p2);
				props.setProperty(k, rValue.trim());
			}
		}
		return new Result(output, props);
	}

	class Result {
		String output;
		Properties props;

		public Result(String output, Properties props) {
			this.output = output;
			this.props = props;
		}

		public String getOutput() {
			return this.output;
		}

		public String getProperty(String key) {
			if (props == null)
				return null;
			return props.getProperty(key);
		}

		public String toString() {
			return this.output;
		}
	}

	public static void exec(String configFilename) throws IOException {
		Context context = new Context(configFilename);
		GeneralLocalTest test = new GeneralLocalTest(context);
		test.start();
	}
}
