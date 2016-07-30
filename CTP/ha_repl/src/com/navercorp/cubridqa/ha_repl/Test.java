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
package com.navercorp.cubridqa.ha_repl;

import java.io.File;


import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Properties;

import com.navercorp.cubridqa.ha_repl.common.CommonUtils;
import com.navercorp.cubridqa.ha_repl.common.Constants;
import com.navercorp.cubridqa.ha_repl.common.DBConnection;
import com.navercorp.cubridqa.common.Log;
import com.navercorp.cubridqa.ha_repl.common.SSHConnect;
import com.navercorp.cubridqa.ha_repl.common.ShellInput;
import com.navercorp.cubridqa.ha_repl.common.SyncException;
import com.navercorp.cubridqa.ha_repl.dispatch.Dispatch;

public class Test {
	public final static int FAIL_MAX_STAT = 100;
	HostManager hostManager;
	String testDb;
	Log mlog;
	Log masterDumpLog;
	Hashtable<String, Log> logHashTable = new Hashtable<String, Log>();
	int failCount = 0, testCount = 0, checkCount = 0;
	ArrayList<String> fail100List = new ArrayList<String>(FAIL_MAX_STAT);
	CommonReader commonReader;
	long globalFlag = 0;
	String envId;
	Log finishedLog;
	boolean isContinueMode;
	Properties props;
	boolean hasCore = false;
	StringBuffer userInfo = null;

	boolean testCompleted;
	String currentTestFile;
	String differMode;
	Context context;
	Connection conn = null;
	String host;
	String usr;
	String passwd;
	String db;
	String port;
	String broker_port;

	public Test(Context context, String envId, String configFilename, boolean isContinueMode, String differMode) throws IOException, Exception {
		this.envId = envId;
		this.props = CommonUtils.getProperties(configFilename);
		this.hostManager = new HostManager(props);
		this.isContinueMode = isContinueMode;
		this.context = context;
		this.differMode = differMode;

		this.testDb = props.getProperty("ha.testdb");
		this.host = props.getProperty("ha.master.ssh.host");
		this.usr = props.getProperty("ha.master.ssh.user");
		this.port = props.getProperty("cubrid.ha.port");
		this.broker_port = props.getProperty("cubrid.ha.broker.port");

		this.mlog = new Log(CommonUtils.concatFile(Constants.DIR_LOG_ROOT, "test_" + envId + ".log"), false, isContinueMode);
		this.finishedLog = new Log(CommonUtils.getFileNameForDispatchFin(envId), true, isContinueMode);
		this.commonReader = new CommonReader(CommonUtils.concatFile(Constants.DIR_CONF, "common.inc"));
	}

	public void runAll() {
		Dispatch dispatch = Dispatch.getInstance();
		this.testCompleted = false;
		boolean haveLeapInCurrTestCase = false;
		boolean haveLeapInLastDB = false;
		String filename;

		while (true) {
			filename = dispatch.nextTestFile();
			this.hostManager.refreshConnection();
			if (filename == null) {
				this.testCompleted = true;

				context.getFeedback().onStopEnvEvent(hostManager, mlog);

				if (context.getProperty("main.testing.cleanup", "true").trim().equalsIgnoreCase("true")) {
					cleanWhenQuit();
				}

				break;
			}

			try {
				haveLeapInCurrTestCase = CommonUtils.containToken(filename, "tz_leap_second_support");
			} catch (Exception e) {
				haveLeapInCurrTestCase = false;
			}

			if (isFinalDatabaseDirty() || haveLeapInCurrTestCase != haveLeapInLastDB) {
				CUBRID_HA_Util.rebuildFinalDatabase(context, hostManager, testDb, mlog, haveLeapInCurrTestCase ? "tz_leap_second_support=yes" : "");
			}

			haveLeapInLastDB = haveLeapInCurrTestCase;
			rebuildFinalFlagTable();

			executeTest(new File(filename));
			this.finishedLog.println(filename);
		}
	}

	public boolean isTestCompleted() {
		return this.testCompleted;
	}

	public void close() {
		this.finishedLog.close();
		this.mlog.close();
		this.hostManager.close();
	}

	private void executeTest(File f) {
		this.currentTestFile = f.getAbsolutePath();

		context.getFeedback().onTestCaseStartEvent(this.currentTestFile, this.envId);

		failCount = 0;
		testCount = 0;
		checkCount = 0;
		fail100List.clear();
		this.hasCore = false;
		this.userInfo = new StringBuffer();

		String logFilename = f.getAbsolutePath();
		logFilename = logFilename.substring(0, logFilename.lastIndexOf("."));

		masterDumpLog = new Log(logFilename + ".master.dump", false);

		ArrayList<SSHConnect> slaveAndReplicaList = CUBRID_HA_Util.getAllSlaveAndReplicaList(hostManager);
		for (SSHConnect ssh : slaveAndReplicaList) {
			Log slave_replicat_log = new Log(logFilename + "." + ssh.getTitle() + ".dump", false);
			logHashTable.put(ssh.getTitle(), slave_replicat_log);
		}
		log("===========================================================================================");
		long startTime = System.currentTimeMillis();
		mlog.println("[TEST-FILE] " + f.getAbsolutePath());
		// log("[TEST-FILE] " + f.getAbsolutePath());

		clearDatabaseLog();
		try {
			conn.close();
		} catch (Exception e) {
			mlog.println(e.getMessage());
		}
		conn = null;

		TestReader tr = null;
		String stmt;
		String expectResult, actualResult;
		int stmtCount = 0;
		boolean isDML;
		ArrayList<String> checkSQLs;
		StringBuffer msg;
		try {
			tr = new TestReader(f, this.commonReader);
			main: while (true) {
				stmt = tr.readOneStatement();
				if (stmt == null)
					break;

				stmtCount++;

				// added by cn15209 for compare master ,slaveAndReplica log
				msg = new StringBuffer();
				msg.append(Constants.LINE_SEPARATOR).append("----------------------------------- Stmt ").append(stmtCount);
				msg.append("(line: ").append(tr.lineNum).append(") -----------------------------------");
				msg.append(Constants.LINE_SEPARATOR).append("[STMT-").append((tr.isTest() ? "TEST]" : (tr.isCheck() ? "CHECK]" : "")));
				msg.append(("[Line:" + tr.lineNum + "]")).append(stmt);
				log(msg.toString());

				if (tr.isTest()) {
					testCount++;
					mlog.println("");
					mlog.println("[Line:" + tr.lineNum + "]" + stmt);
					actualResult = executeOnMaster(tr.isSQL(), tr.isCMD(), stmt, true);
					if (actualResult.equals("")) {
						log("[RESULT] (N/A)");
					} else {
						log("[RESULT] " + Constants.LINE_SEPARATOR + actualResult);
					}
				} else if (tr.isCheck()) {

					// start to verify dml
					isDML = stmt.equals("$HC_CHECK_FOR_DML");
					if (isDML) {
						checkSQLs = getCheckSQLForDML();

						for (int i = 0; i < checkSQLs.size(); i++) {
							log(checkSQLs.get(i));
							checkCount++;
							try {
								if (!runCheckWithRetry(true, false, checkSQLs.get(i))) {
									failCount++;
									String info = "[NOK]" // + f.toString()
											+ ": [" + tr.lineNum + "]" + checkSQLs.get(i);
									addFail(info);
									log(info);
								} else {
									log("[OK]" // + f.toString()
											+ ": [" + tr.lineNum + "]" + checkSQLs.get(i));
								}

							} catch (SyncException e) {
								failCount++;
								String info = "[NOK]" // + f.toString()
										+ ": [" + tr.lineNum + "]" + checkSQLs.get(i) + "(FAIL TO SYNC. BREAK!!!)";
								addFail(info);
								mlog.println("FAIL TO SYNC. BREAK.");
								log(info);
								break main;
							} catch (Exception e) {
								failCount++;
								String info = "[NOK]" // + f.toString()
										+ ": [" + tr.lineNum + "]" + checkSQLs.get(i) + "(" + e.getMessage() + ")";
								addFail("[NOK]" // + f.toString()
										+ ": [" + tr.lineNum + "]" + checkSQLs.get(i) + "(NOT EQUAL)");
								log(info);
							}

						}
					} else {
						checkCount++;
						try {
							if (!runCheckWithRetry(tr.isSQL(), tr.isCMD(), stmt)) {
								failCount++;
								String info = "[NOK]" // + f.toString()
										+ ": [" + tr.lineNum + "]" + stmt;
								addFail(info);
								log(info);
							} else {
								log("[OK]" // + f.toString()
										+ ": [" + tr.lineNum + "]" + stmt);
							}
						} catch (Exception e) {
							failCount++;
							String info = "[NOK]" // + f.toString()
									+ ": [" + tr.lineNum + "]" + stmt + "(" + e.getMessage() + ")";
							addFail("[NOK]" // + f.toString()
									+ ": [" + tr.lineNum + "]" + stmt + "(NOT EQUAL)");
							log(info);
						}
					}
				}
			}

		} catch (Exception e) {
			mlog.println(e.getMessage());
		} finally {
			if (conn != null) {
				try {
					conn.close();
				} catch (SQLException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			tr.close();

		}

		// checkCUBRIDLogFile();
		boolean hasBigError = checkCoresAndErrors();

		String succFlag = verifyResults(hasBigError, failCount, logFilename, differMode);

		if (hasBigError == false && !succFlag.equals("SUCC")) {
			if (context.isFailureBackup()) {
				String backupDir = collectMoreInfoWhenFail();
				this.addFail("More fail information: " + backupDir);
			}
		}

		mlog.println("[" + succFlag + "] " + f.getAbsolutePath());
		log("===========================================================================================");
		log("");
		log("");
		log("Summary: " + failCount + " failure, " + (testCount + checkCount) + " statements, " + checkCount + " tests");
		log("");
		log("");

		int i = 1;
		for (String info : fail100List) {
			log(i + ". " + info);
			i++;
		}

		long endTime = System.currentTimeMillis();

		context.getFeedback().onTestCaseStopEvent(currentTestFile, succFlag != null && succFlag.equals("SUCC"), endTime - startTime, userInfo.toString(), this.envId, false, hasCore,
				Constants.SKIP_TYPE_NO);

		Iterator logHashTableIterator = this.logHashTable.entrySet().iterator();
		while (logHashTableIterator.hasNext()) {
			Entry entry = (Entry) logHashTableIterator.next();
			((Log) entry.getValue()).close();
		}

		masterDumpLog.close();
	}

	private boolean runCheckWithRetry(boolean isSQL, boolean isCMD, String stmt) throws SyncException {
		int RETRY = 5;
		String expectResult = null;
		ArrayList<String> actualResultList = null;
		boolean sameData = false;
		do {
			try {
				expectResult = executeOnMaster(isSQL, isCMD, stmt, false);

				actualResultList = executeOnSlaveAndReplica(isSQL, isCMD, stmt, false);
				if (checkResult(actualResultList, expectResult)) {
					sameData = true;
					break;
				}
			} catch (SyncException e) {
				throw e;
			} catch (Exception e) {
			}
			RETRY--;
		} while (RETRY > 0);

		masterDumpLog(expectResult);
		ArrayList<SSHConnect> slaveAndReplicaList = CUBRID_HA_Util.getAllSlaveAndReplicaList(hostManager);
		int len = slaveAndReplicaList.size();
		for (int i = 0; i < len; i++) {
			this.logHashTable.get(slaveAndReplicaList.get(i).getTitle()).println(actualResultList.get(i));
		}

		return sameData;
	}

	private String verifyResults(boolean hasBigError, int _failCount, String logFilename, String differMode) {

		if (hasBigError) {
			return "FAIL";
		}

		if (_failCount == 0) {
			return "SUCC";
		}
		try {
			Iterator logHashTableIterator = this.logHashTable.entrySet().iterator();
			while (logHashTableIterator.hasNext()) {
				Entry entry = (Entry) logHashTableIterator.next();
				CheckDiff checkDiff = new CheckDiff();
				if (checkDiff.check(logFilename, "master", (String) entry.getKey(), differMode) != 0) {
					return "FAIL";
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			return "UNKNOWN";
		}
		return "SUCC";
	}

	private boolean checkResult(ArrayList<String> actualResultList, String expectResult) {
		for (String result : actualResultList) {
			if (!result.equals(expectResult)) {
				return false;
			}
		}
		return true;
	}

	private void clearDatabaseLog() {
		ArrayList<SSHConnect> allNodeList = CUBRID_HA_Util.getAllNodeList(hostManager);
		String cmd = "find ~/CUBRID/log -type f -print | xargs -i sh -c 'cat /dev/null > {}'";
		for (SSHConnect ssh : allNodeList) {
			try {
				mlog.print("clear database log on " + ssh.toString() + " ... ");
				executeScript(ssh, false, true, cmd, false);
				mlog.println(" DONE.");
			} catch (Exception e) {
				mlog.println("fail to clear database log: " + e.getMessage() + " in " + ssh.toString());
			}
		}
	}

	private String collectMoreInfoWhenFail() {
		ArrayList<SSHConnect> allNodeList = CUBRID_HA_Util.getAllNodeList(hostManager);

		String resultId = getResultId(context.getFeedback().getTaskId(), null, "FAIL", context.getVersionId());
		String result;

		for (SSHConnect ssh : allNodeList) {
			try {
				result = ssh.execute(getBackupScripts(resultId, this.currentTestFile));
				mlog.println("Execute environement backup: FAIL");
				mlog.println(result);
			} catch (Exception e) {
				mlog.println("fail to collect more fail information: " + e.getMessage() + " in " + ssh.toString());
			}
		}

		String dir = Constants.DIR_ERROR_BACKUP + "/" + resultId + ".tar.gz";

		mlog.println("More fail information: " + dir);

		return dir;
	}

	private boolean checkCoresAndErrors() {
		ArrayList<SSHConnect> allNodeList = CUBRID_HA_Util.getAllNodeList(hostManager);
		String result;
		String hitHost;
		ShellInput checkScript;
		String error = null;
		String cat;
		boolean hit = false;

		for (SSHConnect ssh : allNodeList) {
			error = null;
			cat = null;
			try {
				hitHost = ssh.getHost().trim();
				checkScript = new ShellInput("find $CUBRID -name \"core.*\" | wc -l");
				result = ssh.execute(checkScript);
				mlog.println("Check core on " + hitHost + ":     \t\t\t" + result.trim());

				if (!result.trim().equals("0")) {
					cat = "CORE";
					error = "FOUND CORE FILE on host " + hitHost;
					this.hasCore = true;
				}

				checkScript = new ShellInput("grep -r \"FATAL ERROR\" $CUBRID/log/* | wc -l");
				result = ssh.execute(checkScript);
				mlog.println("Check fatal error on " + hitHost + ": \t\t\t" + result.trim());
				if (!result.trim().equals("0")) {
					if (cat == null) {
						cat = "FATAL";
						error = "FOUND FATAL ERROR on host " + hitHost;
					} else {
						error = error + Constants.LINE_SEPARATOR + "FOUND FATAL ERROR on host " + hitHost;
					}
				}

				if (error == null) {
					continue;
				}

			} catch (Exception e) {
				mlog.println("Fail to check important errors: " + e.getMessage() + " in " + ssh.toString());
				continue;
			}

			hit = true;
			String resultId = getResultId(context.getFeedback().getTaskId(), hitHost, cat, context.getVersionId());
			for (SSHConnect ssh1 : allNodeList) {
				try {
					result = ssh1.execute(getBackupScripts(resultId, this.currentTestFile));
					mlog.println("Execute environement backup: " + cat);
					mlog.println(result);
				} catch (Exception e) {
					mlog.println("fail to check error: " + e.getMessage() + " in " + ssh.toString());
				}
			}

			error = error + " (" + Constants.DIR_ERROR_BACKUP + "/" + resultId + ".tar.gz)";
			mlog.println(error);
			this.userInfo.append(error).append(Constants.LINE_SEPARATOR);
			addFail("[NOK] " + error);
			break;
		}

		for (SSHConnect ssh : allNodeList) {
			try {
				checkScript = new ShellInput("find $CUBRID -name \"core.*\" -exec rm -rf {} \\; ");
				result = ssh.execute(checkScript);
				mlog.println("clean core files: " + ssh.toString());
			} catch (Exception e) {
				mlog.println("fail to clean core files: " + e.getMessage());
			}
		}

		return hit;
	}

	private static ShellInput getBackupScripts(String resultId, String testCase) {

		ShellInput script = new ShellInput("");
		script.addCommand("backup_dir_root=" + Constants.DIR_ERROR_BACKUP);
		script.addCommand("mkdir -p ${backup_dir_root}");
		script.addCommand("freadme=${backup_dir_root}/readme.txt ");
		script.addCommand("echo > ${freadme}");
		script.addCommand("echo 1.TEST CASE: " + testCase + " > $freadme");
		script.addCommand("echo 2.CUBRID VERSION: `cubrid_rel | grep CUBRID` >> $freadme");
		script.addCommand("echo 3.TEST DATE: `date` >> $freadme");
		script.addCommand("echo 4.ENVIRONMENT: >> $freadme");
		script.addCommand("set >> $freadme");
		script.addCommand("echo 5.PROCESSES >> $freadme");
		script.addCommand("ps -ef >> $freadme");
		script.addCommand("echo 6.CURRENT USER PROCESSES >> $freadme");
		script.addCommand("ps -u $USER -f >> $freadme");
		script.addCommand("echo 7.IPCS >> $freadme");
		script.addCommand("ipcs >> $freadme");
		script.addCommand("echo 8.DISK STATUS >> $freadme");
		script.addCommand("df >> $freadme");
		script.addCommand("echo 9.LOGGED >> $freadme");
		script.addCommand("who >> $freadme");
		script.addCommand("cd ${backup_dir_root}");
		script.addCommand("tar czvf " + resultId + ".tar.gz ../CUBRID readme.txt");
		return script;
	}

	private static String getResultId(int taskId, String hitHost, String cat, String buildId) {
		return cat.toUpperCase().trim() + "_" + buildId + "_" + CommonUtils.dateToString(new java.util.Date(), Constants.FM_DATE_SNAPSHOT) + "_" + taskId + ((hitHost == null) ? "" : "_" + hitHost);
	}

	private void checkCUBRIDLogFile() {
		String cmd = "grep -s -Ri 'Internal error' ~/CUBRID/log | wc -l";
		ArrayList<SSHConnect> allNodeList = CUBRID_HA_Util.getAllNodeList(hostManager);
		String result;
		for (SSHConnect ssh : allNodeList) {
			try {
				mlog.print("check cubrid error (" + ssh + ") ... ");
				result = executeScript(ssh, false, true, cmd, false);
				result = result.trim();
				if (result.equals("0")) {
					mlog.println(" DONE");
				} else {
					this.failCount++;
					addFail("[NOK]" + ssh + " HIT INTERNAL ERROR");
					mlog.println(" DONE HIT INTERNAL ERROR" + Constants.LINE_SEPARATOR);
				}
			} catch (Exception e) {
				mlog.println("fail to check cubrid error: " + e.getMessage() + " in " + ssh.toString());
			}
		}
	}

	private void addFail(String info) {
		if (fail100List.size() < FAIL_MAX_STAT)
			this.fail100List.add(info);
	}

	private ArrayList<String> getCheckSQLForDML() throws Exception {
		SSHConnect ssh = hostManager.getHost("master");
		String script = "csql -u dba " + testDb
				+ " -c \"select 'FIND'||'_'||'PK_CLASS', class_name, count(*) from db_attribute where class_name in (select distinct class_name from db_index where is_primary_key='YES' and class_name in (select class_name from db_class where is_system_class='NO' and lower(class_name)<>'qa_system_tb_flag')) group by class_name;\" | grep 'FIND_PK_CLASS' ";
		ShellInput csql = new ShellInput(script);
		String tablesResult = ssh.execute(csql);
		ArrayList<String[]> tablesToBeVerified = CommonUtils.extractTableToBeVerified(tablesResult, "FIND_PK_CLASS");
		ArrayList<String> result = new ArrayList<String>();
		if (tablesToBeVerified.size() == 0) {
			return result;
		}

		StringBuffer sql = new StringBuffer();
		int colNMax = 0, index = 1;

		for (String[] item : tablesToBeVerified) {
			sql.append("select '" + item[0] + "' TABLE_NAME, count(*) from ").append(item[0]).append(";");
			sql.append("select '" + item[0] + "' TABLE_NAME, " + item[0] + ".* from ").append(item[0]).append(" order by ");
			colNMax = Integer.parseInt(item[1]) + 1;
			index = 1;
			while (index <= colNMax) {
				sql.append(index);
				if (index < colNMax) {
					sql.append(",");
				}
				index++;
			}
			sql.append(" limit 1000;");
			sql.append("select '" + item[0] + "' TABLE_NAME, " + item[0] + ".* from ").append(item[0]).append(" order by ");
			index = 1;
			while (index <= colNMax) {
				sql.append(index).append(" desc");
				if (index < colNMax) {
					sql.append(",");
				}
				index++;
			}
			sql.append(" limit 1000;");
		}
		result.add(sql.toString());
		return result;

	}

	public static String printdata(ResultSet rs) {
		String result = "";
		String colName = "";
		String data = "";

		try {
			ResultSetMetaData rsmd = null;

			rsmd = rs.getMetaData();
			int numberofColumn = rsmd.getColumnCount();
			for (int c = 1; c <= numberofColumn; c++) {
				colName += rsmd.getColumnTypeName(c) + "	";
			}

			colName += Constants.LINE_SEPARATOR;

			while (rs.next()) {
				for (int j = 1; j <= numberofColumn; j++) {
					int columnType = rsmd.getColumnType(j);
					String columnName = rsmd.getColumnTypeName(j);
					if ("class".equalsIgnoreCase(columnName)) {
						// CUBRIDOID oid=((CUBRIDResultSet)rs).getOID(j);
						// String tableName = oid.getTableName();
						data += rsmd.getColumnName(j);

					} else {
						data += rs.getString(j);
					}
				}
				result = colName + data + Constants.LINE_SEPARATOR;
				System.out.println("");
			}
		} catch (Exception e) {
			System.err.println("SQLException : " + e.getMessage());
		}

		return result;
	}

	private String executeSQL(String sql) {
		String res = "";
		ResultSet rs = null;
		Statement stmt = null;

		try {
			conn = getDBConnection();
			stmt = conn.createStatement();
			if (!("".endsWith(sql)) && sql.length() > 0) {
				if (sql.toUpperCase().trim().startsWith("SELECT")) {
					rs = stmt.executeQuery(sql);
					res += printdata(rs);
				} else if (sql.toUpperCase().trim().startsWith("INSERT") || sql.toUpperCase().trim().startsWith("UPDATE") || sql.toUpperCase().trim().startsWith("DELETE")) {
					int rs_num = stmt.executeUpdate(sql);
					res += rs_num + Constants.LINE_SEPARATOR;
				} else {
					boolean st = stmt.execute(sql);
					if (st) {
						res += "1" + Constants.LINE_SEPARATOR;
					} else {
						res += "0" + Constants.LINE_SEPARATOR;
					}
				}
			}

		} catch (Exception e) {
			// TODO Auto-generated catch block
			// e.printStackTrace();
		} finally {
			if (stmt != null) {
				try {
					stmt.close();
				} catch (SQLException e) {
					// TODO Auto-generated catch block
					// e.printStackTrace();
				}
			}
		}

		return res;

	}

	private Connection getDBConnection() throws SQLException {

		if (conn == null || conn.isClosed()) {
			DBConnection dbCon = new DBConnection();
			conn = dbCon.getDbConnection(this.host, this.testDb, this.broker_port, "dba", "");
		}
		return conn;
	}

	private String executeOnMaster(boolean isSQL, boolean isCMD, String stmt, boolean isTest) throws Exception {
		String oriStmt = stmt;
		SSHConnect ssh = hostManager.getHost("master");
		String flag_SQL = "";

		if (isSQL && isTest) {
			this.globalFlag++;
			if ("jdbc".equals(context.getTestmode())) {
				stmt += ";" + Constants.LINE_SEPARATOR;
				flag_SQL += "UPDATE QA_SYSTEM_TB_FLAG set v=" + this.globalFlag + ";";
			} else {
				stmt += ";" + Constants.LINE_SEPARATOR;
				stmt += "UPDATE QA_SYSTEM_TB_FLAG set v=" + this.globalFlag + ";";
			}
		}
		// String result = executeScript(ssh, isSQL, isCMD, stmt, isTest);

		String result = "";
		if (isTest && "jdbc".equals(context.getTestmode())) {
			result = executeSQL(stmt);
			result += executeSQL(flag_SQL);
		} else {
			result = executeScript(ssh, isSQL, isCMD, stmt, isTest);
		}

		if (isTest && oriStmt != null) {
			String u = CommonUtils.replace(oriStmt, " ", "").toUpperCase();
			boolean shouldExecuteOnSlave = u.indexOf("SETSYSTEMPARAMETERS") != -1 && u.indexOf("TZ_LEAP_SECOND_SUPPORT") == -1;

			if (shouldExecuteOnSlave) {
				ArrayList<SSHConnect> slaveAndReplicaList = CUBRID_HA_Util.getAllSlaveAndReplicaList(hostManager);
				for (SSHConnect ssh1 : slaveAndReplicaList) {
					result = executeScript(ssh1, true, false, oriStmt, true);
				}
			}
		}

		if (isTest) {
			log(result);
		}
		return result;
	}

	private ArrayList<String> executeOnSlaveAndReplica(boolean isSQL, boolean isCMD, String stmt, boolean isTest) throws Exception {
		ArrayList<String> resultList = new ArrayList<String>();
		String result = null;

		SSHConnect masterSsh = hostManager.getHost("master");
		ShellInput script = new ShellInput("csql -u dba " + this.testDb + " -c \"select 'EXP' ||'ECT-'|| v from qa_system_tb_flag;\" | grep EXPECT");

		long expectedFlagId = -1;
		if (isSQL && !isTest) {
			try {
				result = masterSsh.execute(script);
				result = CommonUtils.replace(result, "'", "");
				result = CommonUtils.replace(result, "EXPECT-", "").trim();
				expectedFlagId = Long.parseLong(result);
				mlog.println("  FLAG ID (Master): " + expectedFlagId + ", " + this.globalFlag);
			} catch (Exception e) {
				expectedFlagId = this.globalFlag;
				mlog.println("  FLAG ID: " + expectedFlagId);
			}
		}

		ArrayList<SSHConnect> slaveAndReplicaList = CUBRID_HA_Util.getAllSlaveAndReplicaList(hostManager);
		for (SSHConnect ssh : slaveAndReplicaList) {
			if (isSQL && !isTest) {
				waitDataReplicated(ssh, expectedFlagId);
			}
			result = executeScript(ssh, isSQL, isCMD, stmt, isTest);

			// return String List Result
			resultList.add(result);
		}
		return resultList;
	}

	private String executeScript(SSHConnect ssh, boolean isSQL, boolean isCMD, String stmt, boolean isTest) throws Exception {

		String result = null;
		if (isSQL) {
			String script = "csql -u dba " + testDb + " 2>&1 << EOF" + Constants.LINE_SEPARATOR;
			script += ";time off" + Constants.LINE_SEPARATOR;
			script += stmt + Constants.LINE_SEPARATOR;
			script += "EOF" + Constants.LINE_SEPARATOR;

			ShellInput csql = new ShellInput(script);
			result = ssh.execute(csql);

		} else if (isCMD) {
			ShellInput cmd = new ShellInput(stmt + " 2>&1");
			result = ssh.execute(cmd);
		}

		return result;
	}

	private boolean isFinalDatabaseDirty() {
		boolean result;
		while (true) {
			try {
				result = __isDatabaseDirty();
				break;
			} catch (Exception e) {
				mlog.println("got error whether database is dirty : " + e.getMessage());
			}
		}
		return result;
	}

	private boolean __isDatabaseDirty() throws Exception {
		ArrayList<SSHConnect> allHosts = CUBRID_HA_Util.getAllNodeList(hostManager);

		StringBuffer s = new StringBuffer();
		s.append("select 'FAIL['||sum(c)||']' flag from ( ");
		s.append("    select count(*) c from db_class where is_system_class='NO' and upper(class_name)<>'QA_SYSTEM_TB_FLAG'  union all ");
		s.append("    select count(*) c from db_stored_procedure union all ");
		s.append("    select count(*) c from db_trig union all ");
		s.append("    select count(*) c from db_partition union all ");
		s.append("    select count(*) c from db_meth_file union all ");
		s.append("    select count(*) c from db_serial union all ");
		s.append("    select count(*) c from db_user where name not in ('DBA', 'PUBLIC') ");
		s.append("    ); ");

		ShellInput script = new ShellInput("csql -u dba " + this.testDb + " -c \"" + s.toString() + "\"");
		ShellInput scriptMode = new ShellInput("cubrid changemode " + this.testDb);

		String result;
		SSHConnect ssh;
		for (int i = 0; i < allHosts.size(); i++) {
			ssh = allHosts.get(i);
			result = ssh.execute(scriptMode);
			if (i == 0) {
				if (result.indexOf("is active") == -1) {
					return true;
				}
			} else if (result.indexOf("is standby") == -1) {
				return true;
			}
			result = ssh.execute(script);
			if (result.indexOf("FAIL[0]") == -1) {
				return true;
			}
		}
		return false;
	}

	private void rebuildFinalFlagTable() {
		while (true) {
			try {
				__rebuildFlagTable();
				break;
			} catch (Exception e) {
				mlog.println("error to rebuild flag table: " + e.getMessage());
			}
		}
	}

	private void __rebuildFlagTable() throws Exception {
		mlog.print(Constants.LINE_SEPARATOR + "begin to rebuild flag table ... ");

		SSHConnect masterNode = hostManager.getHost("master");
		ShellInput script;

		script = new ShellInput("csql -u dba " + this.testDb + " -c \"drop table QA_SYSTEM_TB_FLAG;\"");
		masterNode.execute(script);

		this.globalFlag++;
		script = new ShellInput("csql -u dba " + this.testDb + " -c \"create table QA_SYSTEM_TB_FLAG (v BIGINT primary key);insert into QA_SYSTEM_TB_FLAG values (" + this.globalFlag + ");\"");
		masterNode.execute(script);

		mlog.println("DONE");
	}

	private boolean waitDataReplicated(SSHConnect ssh, long expectedFlagId) throws Exception {
		ShellInput script = new ShellInput("csql -u dba " + this.testDb + " -c \"select 'GO'||'OD-'||v FROM QA_SYSTEM_TB_FLAG \" | grep GOOD");
		ShellInput scriptMore = new ShellInput("cubrid applyinfo " + this.testDb + " -L ~/CUBRID/databases/" + this.testDb + "_* -a | grep -E 'count|LSA'");
		String result, halfApplyResult = null, currentApplyResult;
		int MAX = 100;
		int cnt = 0;
		int index = 1;
		boolean useTableReset = false;
		boolean applyInfoNotChanged = false;

		long start_time = System.currentTimeMillis();
		long end_time;

		while (true) {
			result = ssh.execute(script).trim();
			mlog.print("  (" + (index++) + ") Wait data replicated ... ");
			if (result.indexOf("GOOD-" + expectedFlagId) != -1) {
				mlog.println(" GOT (" + calcTimeInterval(start_time) + ", " + expectedFlagId + ", " + result + ")");
				break;
			} else {
				mlog.println(" FAIL (" + calcTimeInterval(start_time) + ", " + expectedFlagId + ", " + result + ")");
			}

			end_time = System.currentTimeMillis();
			if ((end_time - start_time) > 600000) {
				mlog.println(" SYNC TIMEOUT (" + calcTimeInterval(start_time) + ")");
				throw new SyncException();
			}

			// if (cnt == 5) {
			// halfApplyResult = ssh.execute(scriptMore);
			// mlog.println("[Apply Info]");
			// mlog.println(halfApplyResult);
			// }
			// if (cnt > MAX) {
			// CommonUtils.sleep(5);
			// currentApplyResult = ssh.execute(scriptMore);
			// applyInfoNotChanged = halfApplyResult.trim().equals("") == false
			// && halfApplyResult.equals(currentApplyResult);
			// mlog.println("[Apply Info Verify] " + applyInfoNotChanged);
			// mlog.println(currentApplyResult);
			//
			// if (applyInfoNotChanged) {
			// if (useTableReset) {
			// mlog.println(" SYNC FAIL (" + calcTimeInterval(start_time) +
			// ")");
			// throw new SyncException();
			// }
			// rebuildFinalFlagTable();
			// mlog.println(" FAIL (" + calcTimeInterval(start_time) + "):
			// re-create QA_SYSTEM_TB_FLAG table and retry");
			// useTableReset = true;
			// }
			// cnt = -1;
			// }
			Thread.sleep(100 + index * 10);
			// cnt++;
		}

		return true;
	}

	private void masterDumpLog(String info) {
		this.masterDumpLog.println(info);
	}

	private void log(String info) {
		this.masterDumpLog.println(info);
		Iterator logHashTableIterator = this.logHashTable.entrySet().iterator();
		while (logHashTableIterator.hasNext()) {
			Entry entry = (Entry) logHashTableIterator.next();
			((Log) entry.getValue()).println(info);
		}
	}

	public String getTestDb() {
		return this.testDb;
	}

	private void cleanWhenQuit() {
		StringBuffer s = new StringBuffer();
		s.append("cubrid service stop;");
		s.append("cubrid hb stop;");
		s.append("pkill cub").append(";");
		s.append("rm -rf ~/CUBRID").append(";");
		s.append("ipcs | grep $USER | awk '{print $2}' | xargs -i ipcrm -m {}").append(";");

		ArrayList<SSHConnect> list = CUBRID_HA_Util.getAllNodeList(hostManager);
		for (SSHConnect ssh : list) {
			try {
				executeScript(ssh, false, true, s.toString(), false);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	private static String calcTimeInterval(long start_time) {
		long end_time = System.currentTimeMillis();
		return ((end_time - start_time) / 1000) + " seconds";
	}
}
