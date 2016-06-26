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

import java.io.IOException;

import java.sql.SQLException;
import java.util.ArrayList;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.PosixParser;

public class IssueMain {

	public static void main(String[] args) throws IOException, ClassNotFoundException, SQLException {
		Options options = new Options();
		options.addOption("s", "show", false, "Show issue status");
		options.addOption("o", "open", false, "Change issue to " + Constants.ISSUE_STATUS_OPEN + " status");
		options.addOption("f", "fix", false, "Change issue to " + Constants.ISSUE_STATUS_FIX + " status");
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

		String issueKey = args1[0];
		if (issueKey == null || issueKey.trim().equals("")) {
			showHelp("Please input issue key such as CUBRIDSUS-13800 or CBRD-1234", options);
			return;
		}

		issueKey = CommonUtil.replace(issueKey.trim().toUpperCase(), " ", "");

		CoreBO bo = new CoreBO();

		System.out.println();
		if (cmd.hasOption("f")) {
			int cnt = bo.changeIssueStatus(issueKey, Constants.ISSUE_STATUS_FIX);
			if (cnt == 0) {
				System.out.println("FAIL. NOT FOUND.");
			} else {
				System.out.println("DONE. SET TO " + Constants.ISSUE_STATUS_FIX + " STATUS.");
			}
		} else if (cmd.hasOption("o")) {
			int cnt = bo.changeIssueStatus(issueKey, Constants.ISSUE_STATUS_OPEN);
			if (cnt == 0) {
				System.out.println("FAIL. NOT FOUND.");
			} else {
				System.out.println("DONE. SET TO " + Constants.ISSUE_STATUS_OPEN + " STATUS.");
			}
		} else {
			ArrayList<IssueBean> beanList = bo.showIssueStatus(issueKey);
			if (beanList == null || beanList.size() == 0) {
				System.out.println("FAIL. NOT FOUND.");
			} else {
				for (IssueBean bean : beanList) {
					System.out.println(bean);
				}
			}
		}
		System.out.println();
	}

	private static void showHelp(String error, Options options) {
		if (error != null)
			System.out.println("Error: " + error);
		HelpFormatter formatter = new HelpFormatter();
		formatter.printHelp("issue.sh [options] bts_issue_no", options);
		System.out.println();
	}

}
