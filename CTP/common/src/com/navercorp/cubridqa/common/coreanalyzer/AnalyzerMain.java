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
package com.navercorp.cubridqa.common.coreanalyzer;

import java.io.File;
import java.sql.Timestamp;
import java.util.ArrayList;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.PosixParser;

public class AnalyzerMain {

	public static void main(String[] args) throws Exception {
		Options options = new Options();
		options.addOption("s", "save", false, "Save core stack and BTS issue key");
		options.addOption("f", "full", false, "Only show full stack");
		options.addOption("h", "help", false, "Show Help");

		CommandLineParser parser = null;
		CommandLine cmd = null;

		try {
			parser = new PosixParser();
			cmd = parser.parse(options, args);
		} catch (Exception e) {
			showHelp(e.getMessage(), options);
			return;
		}

		String[] args1 = cmd.getArgs();
		if (args.length == 0 || args1.length == 0 || cmd.hasOption("h")) {
			showHelp(null, options);
			return;
		}

		File coreFile = new File(args1[0]);
		if (!coreFile.exists()) {
			showHelp("Not exists for " + coreFile.getAbsolutePath(), options);
			return;
		}

		if (cmd.getArgs().length == 1 && cmd.hasOption("f")) {
			String[] result = fetchCoreFullStack(coreFile);
			System.out.println("SUMMARY:" + result[0]);
			System.out.println(result[1]);
			return;
		}

		if (args.length == 1 && args1.length == 1) {
			// do show
			showCore(coreFile);
			return;
		}

		if (cmd.hasOption("s")) {
			if (args1.length != 2) {
				showHelp("Please input core file and BTS issue key.", options);
				return;
			} else {
				// do save
				String issueKey = args1[1];
				issueKey = CommonUtil.replace(issueKey.trim().toUpperCase(), " ", "");
				if (issueKey.startsWith("CUBRIDSUS-") == false && issueKey.startsWith("CBRD-") == false) {
					showHelp("Incorrect for BTS issue key: " + issueKey, options);
					return;
				}
				saveCoreAndIssue(coreFile, issueKey);
				return;
			}
		}

		showHelp("Invalid parameters.", options);
	}

	private static void showCore(File coreFile) throws Exception {
		Analyzer analyzer = new Analyzer(coreFile.getAbsolutePath());
		analyzer.analyze(false);

		analyzer.showCoreInformation();
		CoreBO bo = new CoreBO();
		IssueBean bean;
		ArrayList<IssueBean> beanList = bo.selectIssueByCore(analyzer.getDigestStack());
		if (beanList.size() == 0) {
			printCoreResult("NEW ISSUE", "Please report it to BTS.");
		} else {
			bean = beanList.get(0);
			String issueKey = bean.getIssueKey();
			String issueStatus = bean.getIssueStatus();

			if (issueStatus.equals(Constants.ISSUE_STATUS_OPEN)) {
				printCoreResult("DUPLICATE WITH " + issueKey + "(" + issueStatus + ")", "Please ignore this core file. " + getIssueKeyList(beanList, 1));
			} else {
				printCoreResult("REGRESSION ISSUE", "Please report it to BTS. " + getIssueKeyList(beanList, 0));
			}
		}
	}

	public static String[] fetchCoreFullStack(File coreFile) throws Exception {
		Analyzer analyzer = new Analyzer(coreFile.getAbsolutePath());
		analyzer.analyze(true);
		return analyzer.getCoreFullStack();
	}

	private static String getIssueKeyList(ArrayList<IssueBean> list, int start) {
		StringBuilder refers = new StringBuilder();
		refers.append("And refer to more issues: ");
		int cnt = 0;
		for (int i = start; i < list.size(); i++) {
			refers.append(list.get(i).getIssueKey()).append("(").append(list.get(i).getIssueStatus()).append(")");
			cnt++;
			refers.append(i + 1 < list.size() ? "," : "");
		}

		return cnt == 0 ? "" : refers.toString();
	}

	private static void printCoreResult(String resultFlg, String resultNote) {
		System.out.println("--------------------------------------------------------");
		System.out.println("\tISSUE RESULT: " + resultFlg);
		System.out.println("\tDESCRIPTION : " + resultNote);
		System.out.println();
	}

	private static void saveCoreAndIssue(File coreFile, String issueKey) throws Exception {
		Analyzer analyzer = new Analyzer(coreFile.getAbsolutePath());
		analyzer.analyze(false);

		CoreBO bo = new CoreBO();
		IssueBean bean;
		ArrayList<IssueBean> beanList = bo.selectIssueByCore(analyzer.getDigestStack());
		if (beanList.size() > 0) {
			bean = beanList.get(0);
			if (bean.getIssueStatus().equals(Constants.ISSUE_STATUS_OPEN)) {
				System.out.println();
				System.out.println("FAIL: DUPLICATE WITH " + bean.getIssueKey() + ". Or close " + bean.getIssueKey() + " first.");
				System.out.println();
				return;
			}
		}

		bean = new IssueBean();
		bean.setProcessName(analyzer.getProcessName());
		bean.setDetailStack(analyzer.getDetailStack());
		bean.setDigestStack(analyzer.getDigestStack());
		bean.setIssueKey(issueKey);
		bean.setIssueStatus(Constants.ISSUE_STATUS_OPEN);
		Timestamp now = new Timestamp(System.currentTimeMillis());
		bean.setCreateTime(now);
		bean.setUpdateTime(now);
		bo.insertCoreIssue(bean);
		System.out.println();
		System.out.println("SUCCESS");
		System.out.println();
	}

	private static void showHelp(String error, Options options) {
		if (error != null) {
			System.out.println("Error: " + error);
		}
		HelpFormatter formatter = new HelpFormatter();
		formatter.printHelp("analyzer.sh [options] core_file <bts_issue_no>", options);

		System.out.println("\nfor example: ");
		System.out.println(" sh analyzer.sh core_file");
		System.out.println(" sh analyzer.sh --save core_file bts_issue_no");
		System.out.println();
	}
}
