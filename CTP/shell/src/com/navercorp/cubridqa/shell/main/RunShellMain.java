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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.PosixParser;
import org.apache.http.ParseException;
import org.quartz.CronScheduleBuilder;
import org.quartz.CronTrigger;
import org.quartz.JobBuilder;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.TriggerBuilder;
import org.quartz.impl.StdSchedulerFactory;

import com.navercorp.cubridqa.common.CommonUtils;
import com.navercorp.cubridqa.common.Constants;
import com.navercorp.cubridqa.common.MailSender;
import com.navercorp.cubridqa.common.MakeFile;
import com.navercorp.cubridqa.shell.common.HttpUtil;
import com.navercorp.cubridqa.shell.common.LocalInvoker;

public class RunShellMain {

	private final static int ERROR_READ_FILE = 1;
	private final static int ERROR_EMPTY_RESULT = 2;
	private final static int ERROR_TEST_NOK = 3;
	private final static String RESULT_DIR_NAME = ".qa";
	private final static String RESULT_TEST_LOG = "test.log";
	public final static String TOKEN_FOR_END = "========";
	public static final String REPORT_DATE_FM = "yyyy-MM-dd HH:mm:ss.SSS";

	private Scheduler scheduler = StdSchedulerFactory.getDefaultScheduler();

	String testCaseName;
	String testCaseDir;
	private boolean keepUpdated = false;
	private boolean withLoop = false;
	private boolean enableReport = false;
	private String reportCron;
	String mailTo;
	String mailCc;
	String issueNo;
	private String extendScript;
	private boolean extendVerify = false;
	private String config;

	private MakeFile testLog;
	private ResultSummary report;

	String homeDir;
	String cubridHomeDir;
	String ctpHomeDir;
	String resultDir;

	String userInfo;
	String machineInfo;
	String cubridRelCont;
	String cubridBuildId;
	String cubridBits;
	String cubridOs;
	String cubridRelType;

	String cubridBuildsUrl;

	public RunShellMain(String testCaseDir, String testCaseName, boolean keepUpdated, boolean withLoop, boolean enableReport, String reportCron, String mailTo, String mailCc, String issueNo,
			String extendScript, String config) throws Exception {
		this.testCaseDir = testCaseDir;
		this.testCaseName = testCaseName;
		this.keepUpdated = keepUpdated;
		this.withLoop = withLoop;
		this.enableReport = enableReport;
		this.reportCron = reportCron;
		this.mailTo = mailTo;
		this.mailCc = mailCc;
		this.issueNo = issueNo;
		this.extendScript = extendScript;
		this.config = config;

		init();
	}

	private void init() throws Exception {
		this.homeDir = CommonUtils.getEnvInFile("HOME");
		this.ctpHomeDir = CommonUtils.getEnvInFile("CTP_HOME");
		this.cubridHomeDir = CommonUtils.getEnvInFile("CUBRID");
		this.resultDir = this.homeDir + File.separator + RESULT_DIR_NAME + File.separator + testCaseName + "_" + Math.abs(testCaseDir.hashCode());
		File f = new File(resultDir);
		if (f.exists() == false) {
			f.mkdirs();
		}

		this.report = new ResultSummary(this);
		String testLog = this.resultDir + File.separator + RESULT_TEST_LOG;
		this.report.loadData(testLog);
		this.testLog = new MakeFile(testLog, false, true);

		this.userInfo = LocalInvoker.exec("echo $USER@`hostname -i | awk '{print $1}'`", false, false).trim();
		this.machineInfo = LocalInvoker.exec("uname -a", false, false).trim();

		if (this.report.getCategorySize() > 0) {
			System.out.println(report.getSummary());
			if (continueConfirm()) {
				restoreCubridConfiguration();
			} else {
				this.report.resetData();
				this.testLog.println(TOKEN_FOR_END);
			}
		}

		if (CommonUtils.isEmpty(extendScript) == false) {
			StringBuilder scripts = new StringBuilder();
			scripts.append("source " + CommonUtils.getLinuxStylePath(extendScript)).append("\n");
			scripts.append("typeset -F").append("\n");
			String result = LocalInvoker.exec(scripts.toString(), false, false);
			if (result.indexOf("declare -f verify") != -1) {
				this.extendVerify = true;
			}
		}

		this.cubridBuildsUrl = System.getenv("CUBRID_BUILD_LIST_URL");
		if (CommonUtils.isEmpty(cubridBuildsUrl)) {
			this.cubridBuildsUrl = Constants.COMMON_DAILYQA_CONF.getProperty("cubrid_build_list_url");
		}

		if (enableReport && CommonUtils.isEmpty(cubridBuildsUrl)) {
			throw new Exception("Not found the url where to download CUBRID");
		}

		File stopFile = new File(this.testCaseDir + File.separator + "STOP");
		if (stopFile.exists()) {
			stopFile.delete();
		}
	}

	public void run() throws Exception {
		backupCubridConfiguration();

		loadCubridMeta();
		if (enableReport) {
			startReportThread();
		}

		System.out.println("====> start to test ");
		String extendedFunctions = extendVerify ? "verify" : "";
		System.out.println("Test parameters: ");
		System.out.println("   testcase     :\t" + testCaseDir + File.separator + testCaseName + ".sh");
		System.out.println("   update-build :\t" + keepUpdated);
		System.out.println("   loop         :\t" + withLoop);
		System.out.println("   enable-report:\t" + enableReport);
		System.out.println("   report-cron  :\t" + reportCron);
		System.out.println("   mailto       :\t" + mailTo);
		System.out.println("   mailcc       :\t" + mailCc);
		System.out.println("   issue        :\t" + issueNo);
		System.out.println("   extend-script:\t" + extendScript + (CommonUtils.isEmpty(extendedFunctions) ? "" : "(" + extendedFunctions + ")"));
		System.out.println("   config       :\t" + config);
		System.out.println("   env:HOME     :\t" + homeDir);
		System.out.println("   env:CTP_HOME :\t" + ctpHomeDir);
		System.out.println("   env:CUBRID   :\t" + cubridHomeDir + " (" + cubridBuildId + ", " + cubridBits + "bits, " + cubridOs + ", " + cubridRelType + ")");

		CommonUtils.sleep(1);
		System.out.println("");

		Date startDate = null, endDate = null;
		String resultType = "STOP";
		for (int i = 0; i < (withLoop ? Integer.MAX_VALUE : 1); i++) {
			System.out.println("LOOP: " + (i + 1));
			try {
				if (keepUpdated) {
					upgradeCubrid();
				}
				startDate = new Date();
				runOnce();
				endDate = new Date();
				report.addItem(cubridBuildId, cubridBits, cubridOs, cubridRelType, startDate, endDate, true);
				testLog.println(cubridBuildId + "\t" + cubridBits + "\t" + cubridOs + "\t" + cubridRelType + "\t" + dateToString(startDate, REPORT_DATE_FM) + "\t"
						+ dateToString(endDate, REPORT_DATE_FM) + "\t" + "TRUE");
			} catch (Exception e) {
				if (e instanceof ShellTestException) {
					endDate = new Date();
					report.addItem(cubridBuildId, cubridBits, cubridOs, cubridRelType, startDate, endDate, false);
					testLog.println(cubridBuildId + "\t" + cubridBits + "\t" + cubridOs + "\t" + cubridRelType + "\t" + dateToString(startDate, REPORT_DATE_FM) + "\t"
							+ dateToString(endDate, REPORT_DATE_FM) + "\t" + "FALSE");

					ShellTestException e1 = (ShellTestException) e;
					if (e1.getErrorCode() == ERROR_TEST_NOK) {
						resultType = "QUIT(NOK)";
					} else {
						resultType = "QUIT(FAIL1)";
					}
				} else {
					System.out.println("[ERROR] " + e.getMessage());
					e.printStackTrace();
					resultType = "QUIT(FAIL2)";
				}
				break;
			}

			File stopFile = new File(this.testCaseDir + File.separator + "STOP");
			if (stopFile.exists()) {
				break;
			}
		}

		if (enableReport) {
			scheduler.shutdown();
			sendMailReport(resultType);
		}
	}

	private void startReportThread() throws Exception {
		if (CommonUtils.isEmpty(this.reportCron)) {
			throw new Exception("[ERROR] not set crontab");
		}
		JobDataMap jobmap = new JobDataMap();
		jobmap.put("test", this);
		JobDetail job = JobBuilder.newJob(ManualReportJob.class).withIdentity("run_shell_job", "group").setJobData(jobmap).build();
		CronTrigger trigger = TriggerBuilder.newTrigger().withIdentity("run_shell_trigger", "group").withSchedule(CronScheduleBuilder.cronSchedule(this.reportCron)).build();

		scheduler.scheduleJob(job, trigger);
		scheduler.start();
		System.out.println("[INFO] Reporter thread started");
	}

	private void upgradeCubrid() throws Exception {
		System.out.println("====> start to check whether there is new build. Current is " + cubridBuildId);
		System.out.println("base url: " + cubridBuildsUrl);

		String pkgUrl = findNewerCubridPackageUrl();

		if (pkgUrl == null) {
			System.out.println("[WARN] not found new CUBRID build");
			return;
		}
		System.out.println("package url: " + pkgUrl);

		StringBuilder scripts = new StringBuilder();
		scripts.append("$CTP_HOME/common/script/run_cubrid_install ").append(pkgUrl).append("\n");
		LocalInvoker.exec(scripts.toString(), false, true);

		restoreCubridConfiguration();
		loadCubridMeta();

		sendMailReport("UPGRADE(" + this.cubridBuildId + ")");
	}

	private void backupCubridConfiguration() throws IOException {
		copy(cubridHomeDir + File.separator + "conf" + File.separator + "cubrid.conf", resultDir + File.separator + "cubrid.conf");
		copy(cubridHomeDir + File.separator + "conf" + File.separator + "cubrid_broker.conf", resultDir + File.separator + "cubrid_broker.conf");
		copy(cubridHomeDir + File.separator + "conf" + File.separator + "cubrid_ha.conf", resultDir + File.separator + "cubrid_ha.conf");
		copy(cubridHomeDir + File.separator + "conf" + File.separator + "cm.conf", resultDir + File.separator + "cm.conf");
		copy(cubridHomeDir + File.separator + "conf" + File.separator + "cubrid_locales.txt", resultDir + File.separator + "cubrid_locales.txt");
		copy(cubridHomeDir + File.separator + "databases" + File.separator + "databases.txt", resultDir + File.separator + "databases.txt");
	}

	private void restoreCubridConfiguration() throws IOException {
		copy(resultDir + File.separator + "cubrid.conf", cubridHomeDir + File.separator + "conf" + File.separator + "cubrid.conf");
		copy(resultDir + File.separator + "cubrid_broker.conf", cubridHomeDir + File.separator + "conf" + File.separator + "cubrid_broker.conf");
		copy(resultDir + File.separator + "cubrid_ha.conf", cubridHomeDir + File.separator + "conf" + File.separator + "cubrid_ha.conf");
		copy(resultDir + File.separator + "cm.conf", cubridHomeDir + File.separator + "conf" + File.separator + "cm.conf");
		copy(resultDir + File.separator + "cubrid_locales.txt", cubridHomeDir + File.separator + "conf" + File.separator + "cubrid_locales.txt");
		copy(resultDir + File.separator + "databases.txt", cubridHomeDir + File.separator + "databases" + File.separator + "databases.txt");
	}

	private void loadCubridMeta() throws Exception {
		String result = LocalInvoker.exec("$CUBRID/bin/cubrid_rel 2>/dev/null", false, false);
		if (result.indexOf("CUBRID") == -1) {
			throw new Exception("Not found current $CUBRID.");
		}
		cubridRelCont = result.trim();
		this.cubridBits = result.indexOf("64bit") == -1 ? "32" : "64";
		if (result.toLowerCase().indexOf("linux") != -1) {
			this.cubridOs = "linux";
		} else if (result.toLowerCase().indexOf("windows") != -1) {
			this.cubridOs = "windows";
		} else {
			throw new Exception("Not linux or windows for CUBRID");
		}
		if (result.toLowerCase().indexOf("release") != -1) {
			this.cubridRelType = "release";
		} else if (result.toLowerCase().indexOf("debug") != -1) {
			this.cubridRelType = "debug";
		} else {
			throw new Exception("Not debug or release for CUBRID");
		}
		this.cubridBuildId = result.substring(result.indexOf("(") + 1, result.indexOf(")"));
		if (CommonUtils.isEmpty(cubridBuildId)) {
			throw new Exception("Unknown CUBRID version");
		}
	}

	private void runOnce() throws Exception {
		StringBuilder scripts = new StringBuilder();
		scripts.append("cd " + CommonUtils.getLinuxStylePath(testCaseDir)).append("\n");
		scripts.append("set -x ; sh " + this.testCaseName + ".sh 2>&1").append("\n");
		LocalInvoker.exec(scripts.toString(), false, true);

		if (this.extendVerify) {
			StringBuilder checkScripts = new StringBuilder();
			checkScripts.append("source " + CommonUtils.getLinuxStylePath(extendScript)).append("\n");
			checkScripts.append("verify " + CommonUtils.getLinuxStylePath(this.testCaseDir) + " " + this.testCaseName + ".result").append("\n");
			String result = LocalInvoker.exec(checkScripts.toString(), false, false);
			if (result.indexOf("NOK") != -1) {
				throw new ShellTestException(ERROR_TEST_NOK);
			} else if (result.indexOf("OK") == -1) {
				throw new ShellTestException(ERROR_EMPTY_RESULT);
			}
		} else {
			String result = null;
			try {
				result = CommonUtils.getFileContent(this.testCaseDir + File.separator + this.testCaseName + ".result");
			} catch (Exception e) {
				throw new ShellTestException(ERROR_READ_FILE, e);
			}
			if (CommonUtils.isEmpty(result)) {
				throw new ShellTestException(ERROR_EMPTY_RESULT);
			}

			if (result.indexOf("NOK") != -1) {
				throw new ShellTestException(ERROR_TEST_NOK);
			}
		}
	}

	public synchronized void sendMailReport(String resultType) {
		if (CommonUtils.isEmpty(mailTo)) {
			System.err.println("[ERROR] not found mail receiver when deliver report");
			return;
		}
		MailSender sender = MailSender.getInstance();
		String mailFrom = Constants.MAIL_FROM;
		if (CommonUtils.isEmpty(mailFrom)) {
			System.err.println("[ERROR] not found mail sender when deliver report");
			return;
		}
		String title = "[QA] re-test " + testCaseDir + "/" + testCaseName + ".sh [" + this.cubridBuildId + ", " + report.getTotalTimes() + ", " + userInfo + "] - " + resultType;
		System.out.println("[INFO] begin to send mail report " + new Date().toString() + "\n" + title);
		try {
			sender.sendBatch(mailFrom, mailTo, mailCc, title, report.getMailContent());
		} catch (Exception e) {
			System.err.println("[ERROR] fail to deliver mail report");
			e.printStackTrace();
		}
	}

	public String getRuntimeStatus() {
		return LocalInvoker.exec("echo ===================; df -h; echo ===================; ps -u $USER f", false, false).trim();
	}

	private String findNewerCubridPackageUrl() {
		ArrayList<String> pkgUrlList;
		try {
			pkgUrlList = getCubridPkgListOfNewerBuild(this.cubridBuildId, this.cubridBuildsUrl);
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
		String url = filterMatchedPkgUrl(pkgUrlList, cubridOs, cubridBits, cubridRelType);
		if (CommonUtils.isEmpty(url)) {
			return null;
		} else {
			return url.trim();
		}
	}

	private static ArrayList<String> getCubridPkgListOfNewerBuild(String cubridBuildId, String allBuildsUrl) throws Exception {
		String source = getHtmlSource(allBuildsUrl, true);

		String mainVersion = getMainVersion(cubridBuildId);
		int maxLastNum = getLastNumberInVersion(cubridBuildId);
		int baseLastNum = maxLastNum;
		String currBuildId;
		int currLastNum;
		int stepIndex = -1;
		int start, end;
		String sha = "";
		while (true) {
			stepIndex = source.indexOf(mainVersion, stepIndex + 1);
			if (stepIndex == -1) {
				break;
			}

			end = source.indexOf("'", stepIndex);
			if (end != -1) {
				currBuildId = source.substring(stepIndex, end);
				currLastNum = getLastNumberInVersion(currBuildId);
				if (currLastNum > maxLastNum) {
					int p = currBuildId.indexOf("-");
					if (p == -1) {
						sha = "";
					} else {
						sha = currBuildId.substring(p + 1);
					}
					maxLastNum = currLastNum;
				}
			}
		}

		String buildBaseUrl = null;
		if (maxLastNum == baseLastNum) {
			return null;
		} else {
			String maxBuildId = mainVersion + "." + maxLastNum;
			if (CommonUtils.isEmpty(sha) == false) {
				maxBuildId = maxBuildId + "-" + sha;
			}
			start = source.indexOf(maxBuildId);
			if (start == -1) {
				return null;
			}
			start = source.lastIndexOf("'", start) + 1;
			if (start == -1) {
				return null;
			}
			end = source.indexOf("'", start);
			if (end == -1) {
				return null;
			}
			buildBaseUrl = source.substring(start, end) + "/drop/";

			if (buildBaseUrl != null && buildBaseUrl.startsWith("http:") == false) {
				return null;
			}
			return getCubridPkgUrlList(maxBuildId, buildBaseUrl);
		}
	}

	private static ArrayList<String> getCubridPkgUrlList(String buildId, String buildBaseUrl) throws Exception {
		ArrayList<String> resultList = new ArrayList<String>();

		String source = getHtmlSource(buildBaseUrl, true);

		buildId = buildId.indexOf("-") == -1 ? buildId : buildId.substring(0, buildId.indexOf("-"));

		int stepIndex = -1;
		int start, end;
		String pkg;
		while (true) {
			stepIndex = source.indexOf(buildId, stepIndex + 1);
			if (stepIndex == -1) {
				break;
			}
			start = source.lastIndexOf("'", stepIndex);
			end = source.indexOf("'", stepIndex);
			if (start == -1 || end == -1) {
				break;
			}
			pkg = source.substring(start + 1, end).trim();
			if (pkg.length() > 0 && resultList.contains(pkg) == false) {
				resultList.add(buildBaseUrl + pkg);
			}
		}
		return resultList;
	}

	private static String filterMatchedPkgUrl(ArrayList<String> pkgList, String cubridOs, String cubridBits, String cubridRelType) {
		if (pkgList == null) {
			return null;
		}
		String p;
		for (String url : pkgList) {
			p = url.toLowerCase().trim();
			p = p.substring(url.lastIndexOf("/") + 1);
			if (p.startsWith("cubrid") == false || p.endsWith(".sh") == false) {
				continue;
			}
			if (p.indexOf("-cci") != -1) {
				continue;
			}
			if (p.indexOf(cubridOs) == -1) {
				continue;
			}
			if (cubridBits.equals("64") && p.indexOf("_64") == -1) {
				continue;
			}

			if (cubridBits.equals("32") && p.indexOf("i386") == -1) {
				continue;
			}

			if (cubridRelType.equals("debug") && p.indexOf("debug") == -1) {
				continue;
			}

			if (cubridRelType.equals("release") && p.indexOf("debug") != -1) {
				continue;
			}

			return url;
		}
		return null;
	}

	private static String getHtmlSource(String url, boolean format) throws ParseException, IOException {
		String content = HttpUtil.getHtmlSource(url);
		if (format) {
			StringBuilder contentFormat = new StringBuilder();
			for (int i = 0; i < content.length(); i++) {
				if (content.charAt(i) == '\"' || content.charAt(i) == '=' || content.charAt(i) == '<' || content.charAt(i) == '>') {
					contentFormat.append('\'');
				} else {
					contentFormat.append(content.charAt(i));
				}
			}
			content = contentFormat.toString();
		}
		return content;
	}

	private static String getMainVersion(String buildId) {
		String ver = buildId.substring(0, buildId.lastIndexOf("."));
		return ver;
	}

	private static int getLastNumberInVersion(String buildId) {

		int p = buildId.lastIndexOf("-");
		if (p == -1) {
			return Integer.parseInt(buildId.split("\\.")[3]);
		} else {
			String ver = buildId.substring(0, p);
			return Integer.parseInt(ver.split("\\.")[3]);
		}
	}

	public static void copy(String from, String to) throws IOException {
		int read = 0;
		File fromFile = new File(from);
		if (fromFile.exists() == false) {
			return;
		}
		InputStream is = null;
		FileOutputStream fos = null;
		byte[] buffer = new byte[512];
		try {
			is = new FileInputStream(fromFile);
			fos = new FileOutputStream(to);

			while ((read = is.read(buffer)) != -1) {
				fos.write(buffer, 0, read);
			}
		} finally {
			if (is != null) {
				is.close();
			}

			if (fos != null) {
				fos.close();
			}
		}
	}

	public static String dateToString(Date date, String fm) {
		SimpleDateFormat dateFormat = new SimpleDateFormat(fm);
		return dateFormat.format(date);
	}

	public static Date stringToDate(String value, String fm) throws java.text.ParseException {
		SimpleDateFormat dateFormat = new SimpleDateFormat(fm);
		return dateFormat.parse(value);
	}

	private static boolean continueConfirm() throws IOException {
		int c;
		System.out.println("Found existing tests. Do you hope to continue previous tests? [Y/N]:");
		while (true) {
			c = System.in.read();
			if (c == 'Y' || c == 'N')
				break;
			if (c == '\n')
				System.out.println("Please input [Y/N]:");
		}
		return c == 'Y';
	}

	public static String getNickNameList(String mails) {
		if (CommonUtils.isEmpty(mails))
			return "";
		StringBuilder result = new StringBuilder();
		String[] arr = mails.split(",");
		for (String s : arr) {
			if (CommonUtils.isEmpty(s)) {
				continue;
			}
			result.append(getNickName(s)).append(", ");
		}
		String list = result.toString().trim();
		if (list.endsWith(",")) {
			list = list.substring(0, list.length() - 1);
		}
		return list;
	}

	private static String getNickName(String mail) {
		if (CommonUtils.isEmpty(mail))
			return "";

		if (mail.indexOf("<") == -1) {
			return mail.substring(0, mail.indexOf("@")).trim();
		} else {
			return mail.substring(0, mail.indexOf("<")).trim();
		}
	}

	private static void showHelp(String error, Options options) {
		if (error != null) {
			System.out.println("Error: " + error);
		}
		HelpFormatter formatter = new HelpFormatter();
		formatter.printHelp("run_shell [OPTION]", options);
		System.out.println("\n");
	}

	public static void main(String[] args) throws Exception {
		Options options = new Options();
		options.addOption(null, "update-build", false, "Upgrade CUBRID build when new build comes. The 4th figure in version number will be used to compare.");
		options.addOption(null, "loop", false, "Execute with loop infinitely till there is failure checked.");
		options.addOption(null, "enable-report", false, "Enable to send report for test status");
		options.addOption(null, "report-cron", true, "Define a time to send a mail");
		options.addOption(null, "mailto", true, "The TO who receive mail");
		options.addOption(null, "mailcc", true, "The CC who receive mail");
		options.addOption(null, "issue", true, "Url link to issue CBRD-xxxx. This info will be described in mail text");
		options.addOption(null, "extend-script ", true, "Extended script file to define how to execute test case and verify failure.");
		// options.addOption(null, "config ", true, "provide a configuration
		// file which store parameters used by scenarios.");

		options.addOption("h", "help", false, "List help");

		CommandLineParser parser = null;
		CommandLine cmd = null;

		try {
			parser = new PosixParser();
			cmd = parser.parse(options, args);
		} catch (Exception e) {
			showHelp(e.getMessage(), options);
			return;
		}
		if (cmd.hasOption("h")) {
			showHelp(null, options);
			return;
		}

		String testCaseValue = cmd.getArgs().length == 0 ? "." : cmd.getArgs()[0];
		testCaseValue = CommonUtils.getFixedPath(testCaseValue);
		if (CommonUtils.isEmpty(testCaseValue)) {
			showHelp("Not found test case to execute.", options);
			return;
		}

		File file = new File(testCaseValue).getCanonicalFile();
		if (file.isFile()) {
			file = file.getParentFile();
		}
		if (file.getName().equals("cases") == false) {
			File f1 = new File(file.getAbsolutePath() + File.separator + "cases");
			if (f1.exists()) {
				file = f1;
			} else {
				showHelp("Not found test case to execute.", options);
				return;
			}
		}

		String testCaseDir = file.getAbsolutePath();
		String testCaseName = file.getParentFile().getName();
		boolean keepUpdated = cmd.hasOption("update-build");
		boolean withLoop = cmd.hasOption("loop");
		boolean enableReport = cmd.hasOption("enable-report");
		String reportCron = cmd.getOptionValue("report-cron");
		String mailTo = cmd.getOptionValue("mailto");
		String mailCc = cmd.getOptionValue("mailcc");
		String issueNo = cmd.getOptionValue("issue");
		String extendScript = cmd.getOptionValue("extend-script");
		String config = cmd.getOptionValue("config");
		RunShellMain main = new RunShellMain(testCaseDir, testCaseName, keepUpdated, withLoop, enableReport, reportCron, mailTo, mailCc, issueNo, extendScript, config);

		main.run();
	}
}

class ShellTestException extends Exception {
	private int code;

	public ShellTestException(int code) {
		this.code = code;
	}

	public ShellTestException(int code, Exception e) {
		super(e);
		this.code = code;
	}

	public int getErrorCode() {
		return this.code;
	}
}

class ResultSummary {
	private ArrayList<ResultCategory> list = new ArrayList<ResultCategory>();
	private HashMap<String, ResultCategory> map = new HashMap<String, ResultCategory>();
	private RunShellMain test;

	public ResultSummary(RunShellMain test) {
		this.test = test;
	}

	public void loadData(String filename) throws Exception {
		ArrayList<String> list = CommonUtils.getLineList(filename);
		if (list == null || list.size() == 0) {
			return;
		}
		String[] arr;
		for (String line : list) {
			if (line.startsWith(RunShellMain.TOKEN_FOR_END)) {
				resetData();
				continue;
			}
			arr = line.split("\t");
			if (arr.length != 7) {
				continue;
			}

			addItem(arr[0], arr[1], arr[2], arr[3], RunShellMain.stringToDate(arr[4], RunShellMain.REPORT_DATE_FM), RunShellMain.stringToDate(arr[5], RunShellMain.REPORT_DATE_FM),
					CommonUtils.convertBoolean(arr[6]));
		}
	}

	public void resetData() {
		list.clear();
		map.clear();
	}

	public int getCategorySize() {
		return this.list.size();
	}

	public boolean addItem(String cubridBuildId, String cubridBits, String cubridOs, String cubridRelType, Date startDate, Date endDate, boolean isSucc) {
		if (CommonUtils.isEmpty(cubridBuildId) || CommonUtils.isEmpty(cubridBits) || CommonUtils.isEmpty(cubridOs) || CommonUtils.isEmpty(cubridRelType)) {
			return false;
		}
		cubridBuildId = cubridBuildId.trim();
		cubridBits = cubridBits.trim();
		cubridOs = cubridOs.trim();
		cubridRelType = cubridRelType.trim();
		String key = cubridBuildId + cubridBits + cubridOs + cubridRelType;
		ResultCategory cat = map.get(key);
		if (cat == null) {
			cat = new ResultCategory();
			cat.cubridBuildId = cubridBuildId;
			cat.cubridBits = cubridBits;
			cat.cubridOs = cubridOs;
			cat.cubridRelType = cubridRelType;
			map.put(key, cat);
			list.add(cat);
		}
		if (isSucc) {
			cat.succCount++;
		} else {
			cat.failCount++;
		}
		cat.totalElapse += endDate.getTime() - startDate.getTime();
		return true;
	}

	public String getMailContent() {
		StringBuilder result = new StringBuilder();
		result.append("<pre>\n");
		result.append("Dear ").append(test.getNickNameList(test.mailTo)).append(",\n");
		result.append("\n");
		result.append("(This mail was generated by run_shell.sh automatically)\n");
		result.append("\n");
		if (CommonUtils.isEmpty(test.issueNo) == false) {
			result.append("<b>Issue</b>: <a href=\"" + test.issueNo + "\" target=_blank>").append(test.issueNo).append("</a>\n");
			result.append("\n");
		}
		result.append("<b>Test machine</b>: ").append(test.userInfo).append("\n");
		result.append(test.machineInfo).append("\n");
		result.append("\n");
		result.append("<b>Test case</b>: ").append(test.testCaseDir + "/" + test.testCaseName + ".sh\n");
		result.append("\n");
		result.append("<b>Current</b>: ").append(test.cubridRelCont).append("\n");
		result.append("\n");

		result.append("<b>Test status</b>:\n");
		if (list.size() == 0) {
			result.append("no result.\n\n");
		} else {
			for (ResultCategory cat : list) {
				result.append(cat.cubridBuildId).append("(").append(cat.cubridBits).append("-bit ").append(cat.cubridRelType).append(" build for ").append(cat.cubridOs).append(")\n");
				result.append("Elapse: ").append(cat.totalElapse / 1000.0).append(" secs (avg " + (cat.totalElapse / 1000.0 / (cat.failCount + cat.succCount)) + " secs)\n");
				result.append("Result: ").append(cat.succCount + " succ, ");
				if (cat.failCount == 0) {
					result.append(cat.failCount + " fail ");
				} else {
					result.append("<font color=red>").append(cat.failCount + " fail</font> ");
				}
				result.append("( total ").append(cat.succCount + cat.failCount).append(")\n\n");
			}
		}
		result.append(test.getRuntimeStatus()).append("\n");

		result.append("\nBest Regards,\nCUBRID QA\n");
		result.append("</pre>\n");
		return result.toString();
	}

	public String getSummary() {

		if (list.size() == 0) {
			return null;
		} else {
			StringBuilder result = new StringBuilder();
			result.append("=============================================================\n");
			for (ResultCategory cat : list) {
				result.append(cat.cubridBuildId).append("(").append(cat.cubridBits).append("-bit ").append(cat.cubridRelType).append(" build for ").append(cat.cubridOs).append(")\n");
				result.append("Elapse: ").append(cat.totalElapse / 1000.0).append(" secs (avg " + (cat.totalElapse / 1000.0 / (cat.failCount + cat.succCount)) + " secs)\n");
				result.append("Result: ").append(cat.succCount + " succ, ").append(cat.failCount + " fail ");
				result.append("( total ").append(cat.succCount + cat.failCount).append(")\n\n");
			}
			return result.toString();
		}
	}

	public int getTotalTimes() {
		if (list == null || list.size() == 0) {
			return 0;
		}
		int count = 0;
		for (ResultCategory cat : list) {
			count += cat.succCount + cat.failCount;
		}
		return count;
	}
}

class ResultCategory {
	String cubridBuildId;
	String cubridBits;
	String cubridOs;
	String cubridRelType;
	int succCount = 0, failCount = 0;
	long totalElapse = 0;
}
