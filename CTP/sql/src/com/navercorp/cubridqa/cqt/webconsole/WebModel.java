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
package com.navercorp.cubridqa.cqt.webconsole;

import java.io.File;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.TreeSet;

import com.navercorp.cubridqa.cqt.common.CommonUtils;
import com.navercorp.cubridqa.cqt.webconsole.compare.Compare;

public class WebModel {
	public static String SCENARIO_ROOT;
	String dailyQARoot;

	public WebModel(String dailyQARoot) throws IOException {
		File file = new File(dailyQARoot);
		this.dailyQARoot = file.getCanonicalPath();
	}

	public String showTopTestList() throws Exception {

		TreeSet<File> fileList = new TreeSet<File>(new Comparator<File>() {

			public int compare(File o1, File o2) {
				if (o1 == null || o2 == null)
					return -1;
				return new Long(new File(o2.getAbsolutePath() + File.separator + "main.info").lastModified()).compareTo(new Long(new File(o1.getAbsolutePath() + File.separator + "main.info")
						.lastModified()));
			}
		});

		findAllTestResults(new File(dailyQARoot), fileList);

		StringBuffer html = new StringBuffer();

		html.append("<table align='left' width=85% border=1 cellspacing=0><thead><tr>");
		html.append("<td> Details</td>");
		html.append("<td> total</td>");
		html.append("<td> success</td>");
		html.append("<td> fail</td>");
		html.append("<td> totalTime(msec)</td>");
		html.append("<td> build</td>");
		html.append("<td> OS</td>");
		html.append("<td> Comment </td>");
		html.append("</tr></thead>");

		String fail;
		SummaryModel model;
		for (File test : fileList) {
			model = new SummaryModel(test, true);
			html.append("<tr>");
			html.append("<td>&nbsp;<a href='show.jsp?p=" + test.getAbsolutePath() + "'>" + model.getDispName() + "</a> </td>");
			html.append("<td>&nbsp;" + model.getMoreData("total") + " </td>");
			html.append("<td>&nbsp;" + model.getMoreData("success") + " </td>");
			fail = model.getMoreData("fail");
			if (fail == null || fail.equals("") || fail.trim().equals("0")) {
				fail = "0";
				html.append("<td>&nbsp;" + fail + "</td>");
			} else {
				html.append("<td>&nbsp;<a href='failure.jsp?p=" + test.getAbsolutePath() + "'>" + model.getMoreData("fail") + "</a> </td>");
			}
			html.append("<td>&nbsp;" + model.getMoreData("totalTime") + " </td>");
			html.append("<td>&nbsp;" + model.getMoreData("build") + " (" + model.getMoreData("version") + ") </td>");
			html.append("<td>&nbsp;" + model.getMoreData("os") + " </td>");
			html.append("<td>&nbsp;" + model.getComment() + " </td>");
			html.append("</tr>");
		}
		html.append("</table><br><br>");

		return html.toString();
	}

	private void findAllTestResults(File upperFile, TreeSet<File> resultList) {
		if (upperFile.isDirectory()) {
			File f = new File(upperFile.getAbsolutePath() + File.separator + "main.info");
			if (f.exists()) {
				resultList.add(upperFile);
				return;
			}
		} else {
			return;
		}
		File[] files = upperFile.listFiles();
		for (File f : files) {
			if (f.isDirectory()) {
				findAllTestResults(f, resultList);
			}
		}
	}

	private void checkFileExist(String filename) throws Exception {
		File f = new File(filename);
		String absDirName = f.getCanonicalPath();

		if (!absDirName.startsWith(this.dailyQARoot))
			throw new Exception("permission deny");

	}

	public String showFailure(String folder) throws Exception {
		checkFileExist(folder);

		SummaryModel sum = new SummaryModel(new File(folder), false);
		ArrayList<File> list = sum.searchFailureTestCaseList(new File(folder), new ArrayList<File>());

		StringBuffer html = new StringBuffer();
		html.append("<div><h3>Current Test:" + folder + "</h3></div><br>");

		String sqlFilename, label, link;
		String testResultRoot;
		if (list == null || list.size() == 0) {
			html.append("<NO FAILURE>");
		} else {
			html.append("<table align='left' width=95% border=1 cellspacing=0>");

			for (int i = 0; i < list.size(); i++) {
				sqlFilename = list.get(i).getAbsolutePath();
				testResultRoot = getScheduleFolerName(sqlFilename);
				label = sqlFilename.substring(sqlFilename.lastIndexOf(testResultRoot) + testResultRoot.length() + 1);
				label = CommonUtils.replace(label, "\\", "/");
				link = "compare.jsp?p=" + sqlFilename;
				html.append("<tr><td width=30>" + (i + 1) + "</td><td><a href=" + link + " target=_blank>" + label + "</a></td></tr>");
			}
			html.append("</table>");
		}

		return html.toString();
	}

	public String showCompareResult(String sqlFilename) throws Exception {
		if (!sqlFilename.endsWith(".sql")) {
			throw new Exception("permission deny");
		}

		// int p1 = sqlFilename.lastIndexOf(File.separator + "cases" +
		// File.separator);
		// int p2 = sqlFilename.lastIndexOf(File.separator) + 1;
		// String answerFilename = sqlFilename.substring(0, p1) + File.separator
		// + "answers" + File.separator + sqlFilename.substring(p2,
		// sqlFilename.lastIndexOf(".sql")) + ".answer";
		String resultFilename = sqlFilename.substring(0, sqlFilename.lastIndexOf(".")) + ".result";
		String answerFilename = sqlFilename.substring(0, sqlFilename.lastIndexOf(".")) + ".answer";

		String snapshot = resultFilename + "_diff_snapshot";
		File file = new File(snapshot);
		String content;
		if (file.exists()) {
			return Util.readFile(snapshot);
		} else {
			Compare compare = new Compare(sqlFilename, answerFilename, resultFilename);
			compare.compare();
			content = compare.getResult();
			Util.writeFile(snapshot, content);
		}
		return content;
	}

	private String getScheduleFolerName(String absDirName) {
		int p1 = absDirName.indexOf(File.separator + "schedule_");
		int p2 = absDirName.indexOf(File.separator, p1 + 1);
		return p2 == -1 ? absDirName.substring(p1 + 1) : absDirName.substring(p1 + 1, p2);
	}

	public String showTestCase(String folder) throws Exception {
		checkFileExist(folder);

		File file = new File(folder);
		String absDirName = file.getCanonicalPath();

		SummaryModel summary = new SummaryModel(file, false);
		SummaryItem[] itemList = summary.getItemList();

		SummaryItem item;
		StringBuilder result = new StringBuilder();

		boolean isDir = itemList.length == 0 || itemList[0].isDir();

		result.append("<div><h3>Current Directory: " + absDirName + "</h3></div>");
		result.append("<table border=1 cellspacing=0 width=95%>");
		result.append("<tr>");
		if (isDir) {
			result.append("<td>Directory</td>");
			result.append("<td width=100>Total</td>");
			result.append("<td width=100>Success</td>");
			result.append("<td width=100>Failure</td>");
		} else {
			result.append("<td>Case</td>");
			result.append("<td width=100>Succ</td>");
			result.append("<td width=100>Elapse</td>");
		}
		result.append("</tr>");

		String subPath;
		String finalPath;
		String testRootName;
		int p1, p2;
		for (int i = 0; i < itemList.length; i++) {
			item = itemList[i];
			result.append("<tr>");
			testRootName = getScheduleFolerName(absDirName);
			// System.out.println("testRootName:" +testRootName);

			p1 = item.getCat().indexOf(testRootName);
			if (p1 == -1) {
				subPath = item.getCat();
			} else {
				subPath = item.getCat().substring(item.getCat().indexOf(testRootName) + testRootName.length());
			}
			// System.out.println("subPath:" + subPath);

			if (isDir) {
				finalPath = CommonUtils.concatFile(CommonUtils.concatFile(absDirName.substring(0, absDirName.indexOf(testRootName)), testRootName), subPath);
				result.append("<td><a href='show.jsp?p=" + finalPath + "'>").append(subPath).append("</a></td>");
				result.append("<td>").append(item.getTotal()).append("</td>");
				result.append("<td>").append(item.getSucc()).append("</td>");
				if (item.getFail() > 0) {
					result.append("<td><a href='failure.jsp?p=" + finalPath + "'>").append(item.getFail()).append("</a></td>");
				} else {
					result.append("<td>").append(item.getFail()).append("</td>");
				}
			} else {
				if (item.getFlag().equals("ok")) {
					// sql is ok
					File f = Util.searchFile(SCENARIO_ROOT, item.getCat());
					String label;
					if (f == null) {
						label = item.getCat();
					} else {
						label = f.getAbsolutePath();
					}
					result.append("<td><a href='source.jsp?p=" + label + "'>").append(label).append("</a></td>");
				} else {
					// sql is nok
					String fname = item.getCat().substring(CommonUtils.replace(item.getCat(), "\\", "/").lastIndexOf("/") + 1);
					fname = folder + fname;
					result.append("<td><a href='compare.jsp?p=" + fname + "'>").append(item.getCat()).append("</a></td>");
				}
				result.append("<td>").append(item.getFlag()).append("</td>");
				result.append("<td>").append(item.getElapse()).append("</td>");
			}
		}
		result.append("</tr>");
		result.append("</table>");
		return result.toString();
	}

	public String showSource(String sqlFilename) throws Exception {

		if (!sqlFilename.endsWith(".sql")) {
			throw new Exception("permission deny");
		}

		File f = Util.searchFile(SCENARIO_ROOT, sqlFilename);
		if (f != null) {
			sqlFilename = f.getAbsolutePath();
		}

		if (new File(sqlFilename).exists() == false) {
			throw new Exception("File not found: " + sqlFilename);
		}

		int p1 = sqlFilename.lastIndexOf(File.separator + "cases" + File.separator);
		int p2 = sqlFilename.lastIndexOf(File.separator) + 1;
		String answerFilename = sqlFilename.substring(0, p1) + File.separator + "answers" + File.separator + sqlFilename.substring(p2, sqlFilename.lastIndexOf(".sql")) + ".answer";

		String resultFilename = sqlFilename.substring(0, sqlFilename.lastIndexOf(".sql")) + ".result";

		// String answerFilename = sqlFilename.substring(0,
		// sqlFilename.lastIndexOf(".sql")) + ".answer";

		Compare compare = new Compare(sqlFilename, answerFilename, resultFilename);
		compare.compare();

		return compare.getResult();
	}
}
