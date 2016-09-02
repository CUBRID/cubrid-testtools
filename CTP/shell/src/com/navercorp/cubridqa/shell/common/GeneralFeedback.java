package com.navercorp.cubridqa.shell.common;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.util.HashMap;

import com.navercorp.cubridqa.shell.main.Context;
import com.navercorp.cubridqa.shell.result.FeedbackDB;
import com.navercorp.cubridqa.shell.result.FeedbackFile;

public class GeneralFeedback {

	private Context context;
	private String messageFilename;
	private HashMap<String, String> keyData;

	String currentKey;
	StringBuffer currentValue;

	public GeneralFeedback(String configFilename, String messageFilename) throws IOException {
		this.context = new Context(configFilename);
		this.messageFilename = messageFilename;
		this.context.setLogDir(context.getProperty("main.testing.category", "general"));
		
		keyData = new HashMap<String, String>();

		context.setTestBuild(context.getProperty("main.testing.build_id"));
		context.setVersion(context.getProperty("main.testing.bits"));
		
		boolean skipToSaveSuccCase = convertToBool(context.getProperty("main.skip_to_save_passed_testcases_yn", "true"));
		context.setSkipToSaveSuccCase(skipToSaveSuccCase);
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
