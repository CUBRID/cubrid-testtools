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
import java.io.IOException;
import java.util.ArrayList;
import java.util.Properties;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.PosixParser;
import org.ini4j.InvalidFileFormatException;

import com.navercorp.cubridqa.common.CommonUtils;
import com.navercorp.cubridqa.common.Constants;
import com.navercorp.cubridqa.common.IniData;
import com.navercorp.cubridqa.shell.common.HttpUtil;
import com.navercorp.cubridqa.shell.common.LocalInvoker;
import com.navercorp.cubridqa.shell.dispatch.TestCaseRequest;

public class PrepareMain {

	private TestCaseRequest testcaseReq;
	private String cubridVersion;
	private String cubridBits;
	private String cubridOS;
	private String cubridRelType;
	private String branch;
	private String cubridBuildsUrl;

	private String htmlSourceOfCubridList = null;

	public PrepareMain(String tcFilename, Properties config) {
		this.testcaseReq = new TestCaseRequest(tcFilename, config);
	}

	public PrepareMain(TestCaseRequest testcaseReq) {
		this.testcaseReq = testcaseReq;
	}

	public static void main(String[] args) throws Exception {
		Options options = new Options();
		options.addOption(null, "branch", true, "Provide a branch to update each dependent repositories.");
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

		PrepareMain main = new PrepareMain(getTestCaseFilename(), getTestConfiguration("test.conf"));
		String result = LocalInvoker.exec("cubrid_rel 2>/dev/null", false, false);
		if (result.indexOf("CUBRID") == -1) {
			throw new Exception("Not found current $CUBRID.");
		}
		main.setCubridBits(result.indexOf("64bit") == -1 ? "32" : "64");
		if (result.toLowerCase().indexOf("linux") != -1) {
			main.setCubridOS("linux");
		} else if (result.toLowerCase().indexOf("windows") != -1) {
			main.setCubridOS("windows");
		}
		if (result.toLowerCase().indexOf("release") != -1) {
			main.setCubridRelType("release");
		} else if (result.toLowerCase().indexOf("debug") != -1) {
			main.setCubridRelType("debug");
		}
		main.setCubridVersion(result.substring(result.indexOf("(") + 1, result.indexOf(")")));
		System.out.println("cubrid: " + main.getCubridVersion() + " " + main.getCubridBits() + "bits " + main.getCubridOS() + " " + main.getCubridRelType());

		if (cmd.hasOption("branch")) {
			main.setBranch(cmd.getOptionValue("branch"));
		}

		String cubridBuildsUrl = System.getenv("CUBRID_BUILD_LIST_URL");
		if (CommonUtils.isEmpty(cubridBuildsUrl)) {
			cubridBuildsUrl = Constants.COMMON_DAILYQA_CONF.getProperty("cubrid_build_list_url");
		}
		main.setCubridBuildsUrl(cubridBuildsUrl);
		main.prepare();
	}

	private static void showHelp(String error, Options options) {
		if (error != null) {
			System.out.println("Error: " + error);
		}
		HelpFormatter formatter = new HelpFormatter();
		formatter.printHelp("prepare [OPTION]", options);
		System.out.println("\n");
	}

	private void prepare() throws Exception {
		ArrayList<String> errors = new ArrayList<String>();
		String[] pkgDeps = testcaseReq.getCubridPkgDeps();

		if (pkgDeps != null && pkgDeps.length > 0) {
			if (CommonUtils.isEmpty(this.cubridBuildsUrl)) {
				String err = "[ERROR] Not found the URL of CUBRID build list.";
				System.err.println(err);
				errors.add(err);
			} else {
				for (String p : pkgDeps) {
					try {
						preparePkgDepts(p);
					} catch (Exception e) {
						e.printStackTrace();
						errors.add(e.getMessage());
					}
				}
			}
		}

		String[] repoDeps = testcaseReq.getRepoDeps();
		if (repoDeps != null && repoDeps.length > 0) {
			if (CommonUtils.isEmpty(branch)) {
				String err = "[ERROR] SKIP to update dependent repositories for absence of branch";
				System.err.println(err);
				errors.add(err);
			} else {
				for (String r : repoDeps) {
					try {
						prepareRepoDepts(r, branch);
					} catch (Exception e) {
						e.printStackTrace();
						errors.add(e.getMessage());
					}
				}
			}
		}

		LocalInvoker.exec("if [ -f ~/.cubrid.sh -a -d ~/CUBRID -a ! -f ~/CUBRID/.cubrid.sh ]; then cp -f ~/.cubrid.sh ~/CUBRID; fi", false, false);
		String[] cubridDepts = testcaseReq.getCubridDeps();
		if (cubridDepts != null && cubridDepts.length > 0) {
			if (CommonUtils.isEmpty(this.cubridBuildsUrl)) {
				String err = "[ERROR] Not found the URL of CUBRID build list.";
				System.err.println(err);
				errors.add(err);				
			} else {
				LocalInvoker.exec("if [ -d ~/CUBRID ]; then rm -rf ~/.CUBRID_" + cubridVersion + "; mv -f ~/CUBRID ~/.CUBRID_" + cubridVersion + "; fi", false, false);

				ArrayList<String> exList = new ArrayList<String>();
				for (String c : cubridDepts) {
					try {
						prepareCubridDepts(c.trim());
					} catch (Exception e) {
						e.printStackTrace();
						exList.add(c);
						errors.add(e.getMessage());
					}
				}
				
				LocalInvoker.exec("if [ -d ~/.CUBRID_" + cubridVersion + " ]; then mv -f ~/.CUBRID_" + cubridVersion + " ~/CUBRID; fi", false, false);
				LocalInvoker.exec("if [ -f ~/CUBRID/.cubrid.sh ]; then cp -f ~/CUBRID/.cubrid.sh ~/; fi", false, false);
				
				File srcFile, destFile;
				String[] vers;
				for (String c : cubridDepts) {
					if(exList.contains(c)) {
						continue;
					}
					c = c.trim();
					vers = c.split("\\.");
					c = "CUBRID_" + c;
					srcFile = new File(Constants.ENV_HOME + File.separator + "CUBRID" + File.separator + "conf" + File.separator + "cubrid.conf");
					destFile = new File(Constants.ENV_HOME + File.separator + c + File.separator + "conf" + File.separator + "cubrid.conf");
					syncConfiguration(srcFile, destFile, "common", "cubrid_port_id");
					if (Integer.parseInt(vers[0]) >= 10) {
						syncConfiguration(srcFile, destFile, "common", "inquire_on_exit");	
					}					
					syncConfiguration(srcFile, destFile, "common", "error_log_size");

					srcFile = new File(Constants.ENV_HOME + File.separator + "CUBRID" + File.separator + "conf" + File.separator + "cubrid_broker.conf");
					destFile = new File(Constants.ENV_HOME + File.separator + c + File.separator + "conf" + File.separator + "cubrid_broker.conf");
					syncConfiguration(srcFile, destFile, "broker", "MASTER_SHM_ID");
					syncConfiguration(srcFile, destFile, "%query_editor", "SERVICE");
					syncConfiguration(srcFile, destFile, "%query_editor", "BROKER_PORT");
					syncConfiguration(srcFile, destFile, "%query_editor", "APPL_SERVER_SHM_ID");
					syncConfiguration(srcFile, destFile, "%query_editor", "SQL_LOG");
					syncConfiguration(srcFile, destFile, "%BROKER1", "SERVICE");
					syncConfiguration(srcFile, destFile, "%BROKER1", "BROKER_PORT");
					syncConfiguration(srcFile, destFile, "%BROKER1", "APPL_SERVER_SHM_ID");
					syncConfiguration(srcFile, destFile, "%BROKER1", "SQL_LOG");

					srcFile = new File(Constants.ENV_HOME + File.separator + "CUBRID" + File.separator + "conf" + File.separator + "cm.conf");
					destFile = new File(Constants.ENV_HOME + File.separator + c + File.separator + "conf" + File.separator + "cm.conf");
					syncConfiguration(srcFile, destFile, "cmd", "cm_port");

					srcFile = new File(Constants.ENV_HOME + File.separator + "CUBRID" + File.separator + "conf" + File.separator + "cubrid_ha.conf");
					destFile = new File(Constants.ENV_HOME + File.separator + c + File.separator + "conf" + File.separator + "cubrid_ha.conf");
					syncConfiguration(srcFile, destFile, "common", "ha_port_id");
					syncConfiguration(srcFile, destFile, "common", "ha_node_list");
					syncConfiguration(srcFile, destFile, "common", "ha_db_list");
				}
			}
		}
		
		System.out.println("-----------------------------------");
		if (errors.size() == 0) {
			System.out.println(" RESULT: PASS");
		} else {
			System.out.println(" RESULT: FAIL");
			for (String e : errors) {
				System.out.println(" " + e);
			}
			System.out.println();
		}
	}

	private void preparePkgDepts(String deptPkg) throws Exception {
		if (CommonUtils.isEmpty(deptPkg)) {
			return;
		}
		int p = this.cubridVersion.indexOf("-");
		String simplifiedBuildId = p == -1 ? this.cubridVersion : this.cubridVersion.substring(0, p);
		deptPkg = CommonUtils.replace(deptPkg, "{BUILD_ID_S}", simplifiedBuildId);
		deptPkg = CommonUtils.replace(deptPkg, "{BUILD_ID}", this.cubridVersion);
		deptPkg = deptPkg.trim();

		if (CommonUtils.isEmpty(deptPkg)) {
			return;
		}

		String deptPkgBuildId = CommonUtils.getBuildId(deptPkg);

		String content = getHtmlSourceOfCubridList();

		int hit = content.indexOf(deptPkgBuildId);
		if (hit == -1) {
			throw new Exception("[ERROR] Not found dependent package " + deptPkg + ".");
		}
		int start, end;
		start = content.lastIndexOf("'", hit) + 1;
		end = content.indexOf("'", hit);
		String deptPkgUrl = content.substring(start, end) + "/drop/" + deptPkg.trim();
		if (deptPkgUrl.startsWith("http") == false) {
			throw new Exception("[ERROR] Invalid URL for " + deptPkgUrl);
		}
		LocalInvoker.exec("cd $HOME; rm -rf " + deptPkg.trim() + "; wget -t 3 " + deptPkgUrl, false, false);
		System.out.println("download: " + deptPkgUrl + " DONE");
	}

	private void prepareRepoDepts(String deptRepo, String branch) throws Exception {
		if (CommonUtils.isEmpty(deptRepo) || CommonUtils.isEmpty(branch)) {
			return;
		}

		deptRepo = deptRepo.trim();
		branch = branch.trim();

		String remoteBranch = LocalInvoker.exec("cd ~/" + deptRepo + "; git branch -a | grep '^ *remotes'", false, false);
		remoteBranch = remoteBranch.trim();
		if (remoteBranch.indexOf("/" + branch) == -1) {
			System.err.println("[ERROR] Skip to update repository " + deptRepo + ". Not found expected branch in remote.");
		} else {
			System.out.println("checkout: " + deptRepo + "(" + branch + ")");
			LocalInvoker.exec("cd ~/" + deptRepo + "; $CTP_HOME/common/script/run_git_update -f . -b " + branch, false, true);
		}
	}

	private void prepareCubridDepts(String buildId) throws Exception {
		if (CommonUtils.isEmpty(buildId)) {
			return;
		}
		String content = getHtmlSourceOfCubridList();
		int hit = content.indexOf(buildId);
		if (hit == -1) {
			throw new Exception("[ERROR] Not found " + buildId + " in CUBRID build list.");
		}
		int start, end;
		start = content.lastIndexOf("'", hit);
		end = content.indexOf("'", hit);
		String buildBaseUrl = content.substring(start + 1, end) + "/drop/";
		if (buildBaseUrl.startsWith("http") == false) {
			throw new Exception("[ERROR] Invalid URL for " + buildBaseUrl);
		}

		String pkgName = filterMatchedPkg(getCubridPkgList(buildId, buildBaseUrl));
		if (CommonUtils.isEmpty(pkgName)) {
			throw new Exception("[ERROR] Not found the expected package for " + buildId);
		} else {
			pkgName = pkgName.trim();
			String pkgUrl = buildBaseUrl + pkgName;
			StringBuilder cmd = new StringBuilder();
			System.out.println("install: " + pkgUrl);
			cmd.append("cd ~; $CTP_HOME/common/script/run_cubrid_install " + pkgUrl).append(";");
			cmd.append("cp -f ~/.cubrid.sh ~/CUBRID;");
			cmd.append("sed -i '1d' ~/CUBRID/.cubrid.sh; sed -i \"1i CUBRID=$(cd ~; pwd)/CUBRID_" + buildId + "\" ~/CUBRID/.cubrid.sh;");
			cmd.append("sed -i '2d' ~/CUBRID/.cubrid.sh; sed -i \"2i CUBRID_DATABASES=\\$CUBRID/databases\" ~/CUBRID/.cubrid.sh;");
			cmd.append("rm -rf ~/CUBRID_" + buildId + "; mv -f ~/CUBRID ~/CUBRID_" + buildId).append(";");
			LocalInvoker.exec(cmd.toString(), false, true);
			System.out.println("save as: CUBRID_" + buildId + " DONE");
		}
	}

	private String getHtmlSourceOfCubridList() throws Exception {
		if (CommonUtils.isEmpty(this.cubridBuildsUrl)) {
			return null;
		}
		if (this.htmlSourceOfCubridList == null) {
			this.htmlSourceOfCubridList = HttpUtil.getHtmlSource(this.cubridBuildsUrl);
		}
		return this.htmlSourceOfCubridList;
	}

	private ArrayList<String> getCubridPkgList(String buildId, String url) throws Exception {
		ArrayList<String> resultList = new ArrayList<String>();

		String content = HttpUtil.getHtmlSource(url);
		StringBuilder contentFormat = new StringBuilder();
		for (int i = 0; i < content.length(); i++) {
			if (content.charAt(i) == '\"' || content.charAt(i) == '=' || content.charAt(i) == '<' || content.charAt(i) == '>') {
				contentFormat.append('\'');
			} else {
				contentFormat.append(content.charAt(i));
			}
		}
		content = contentFormat.toString();
		contentFormat = null;

		buildId = buildId.indexOf("-") == -1 ? buildId : buildId.substring(0, buildId.indexOf("-"));

		int stepIndex = -1;
		int start, end;
		String pkg;
		while (true) {
			stepIndex = content.indexOf(buildId, stepIndex + 1);
			if (stepIndex == -1) {
				break;
			}
			start = content.lastIndexOf("'", stepIndex);
			end = content.indexOf("'", stepIndex);
			if (start == -1 || end == -1) {
				break;
			}
			pkg = content.substring(start + 1, end).trim();
			if (pkg.length() > 0 && resultList.contains(pkg) == false) {
				resultList.add(pkg);
			}
		}
		return resultList;
	}

	private String filterMatchedPkg(ArrayList<String> list) {
		String p;
		for (String pkg : list) {
			p = pkg.toLowerCase().trim();
			if (p.startsWith("cubrid") == false || p.endsWith(".sh") == false) {
				continue;
			}
			if (p.indexOf("-cci") != -1) {
				continue;
			}
			if (p.indexOf(this.cubridOS) == -1) {
				continue;
			}
			if (this.getCubridBits().equals("64") && p.indexOf("_64") == -1) {
				continue;
			}

			if (this.getCubridBits().equals("32") && p.indexOf("i386") == -1) {
				continue;
			}

			if (this.cubridRelType.equals("debug") && p.indexOf("debug") == -1) {
				continue;
			}

			if (this.cubridRelType.equals("release") && p.indexOf("debug") != -1) {
				continue;
			}

			return pkg;
		}
		return null;
	}

	private static String getTestCaseFilename() throws Exception {
		File file = new File(".").getCanonicalFile();
		String name;
		if (file.exists() && file.getName().equals("cases")) {
			name = file.getParentFile().getName();
			String tc = file.getAbsolutePath() + File.separator + name + ".sh";
			System.out.println("scenario: " + tc);
			return tc;
		}
		return null;
	}

	private static Properties getTestConfiguration(String configFilename) throws IOException {
		File fname = new File(configFilename);

		ArrayList<String> lineList = CommonUtils.getLineList(fname.getAbsolutePath());
		if (lineList == null) {
			System.err.println("[ERROR] Not found " + fname.getAbsolutePath());
			return null;
		}
		System.out.println("config: " + fname.getCanonicalPath());

		Properties props = new Properties();

		int p;
		String key, value;
		for (String line : lineList) {
			p = line.indexOf(":");
			if (p != -1) {
				key = line.substring(0, p);
				value = line.substring(p + 1);
				System.out.println("prop: " + key + ":" + value);
				props.setProperty(key, value);
			}
		}
		return props;
	}

	private static void syncConfiguration(File srcFile, File destFile, String section, String key) throws InvalidFileFormatException, IOException {
		IniData srcIni = new IniData(srcFile);
		IniData destIni = new IniData(destFile);
		String value = srcIni.get(section, key);
		if (CommonUtils.isEmpty(value) == false) {
			destIni.put(section, key, value, true);
		}
	}

	public String getCubridVersion() {
		return cubridVersion;
	}

	public void setCubridVersion(String cubridVersion) {
		this.cubridVersion = cubridVersion.trim();
	}

	public String getCubridBits() {
		return cubridBits;
	}

	public void setCubridBits(String cubridBits) {
		this.cubridBits = cubridBits;
	}

	public String getCubridOS() {
		return cubridOS;
	}

	public void setCubridOS(String cubridOS) {
		this.cubridOS = cubridOS;
	}

	public String getCubridRelType() {
		return this.cubridRelType;
	}

	public void setCubridRelType(String cubridRelType) {
		this.cubridRelType = cubridRelType;
	}

	public String getBranch() {
		return this.branch;
	}

	public void setBranch(String branch) {
		this.branch = branch.trim();
	}

	public void setCubridBuildsUrl(String url) {
		if (url != null) {
			cubridBuildsUrl = url.trim();
		}
		this.cubridBuildsUrl = url;
	}

	public String getCubridBuildsUrl() {
		return this.cubridBuildsUrl;
	}
}
