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
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map.Entry;

import com.navercorp.cubridqa.common.CommonUtils;
import com.navercorp.cubridqa.common.Log;
import com.navercorp.cubridqa.ha_repl.common.Constants;
import com.navercorp.cubridqa.ha_repl.dispatch.Dispatch;
import com.navercorp.cubridqa.shell.common.GeneralScriptInput;
import com.navercorp.cubridqa.shell.common.SSHConnect;
import com.navercorp.cubridqa.shell.common.SyncException;

public class Test {
	public final static int FAIL_MAX_STAT = 100;
	public final static String SQL_UPDATE_STATS_CLASSES = "update statistics on catalog classes";

	InstanceManager hostManager;
	Log mlog;
	Log masterDumpLog;
	Hashtable<String, Log> logHashTable = new Hashtable<String, Log>();
	int failCount = 0, testCount = 0, checkCount = 0;
	ArrayList<String> fail100List = new ArrayList<String>(FAIL_MAX_STAT);
	CommonReader commonReader;
	long globalFlag = 0;
	Log finishedLog;
	boolean hasCore = false;
	StringBuffer userInfo = null;

	boolean testCompleted;
	String currentTestFile;
	Context context;

	public Test(Context context, String envId) throws Exception {
		this.context = context;
		this.hostManager = new InstanceManager(context, envId);

		this.mlog = new Log(CommonUtils.concatFile(context.getCurrentLogDir(), "test_" + envId + ".log"), false, context.isContinueMode());
		this.finishedLog = new Log(CommonUtils.concatFile(context.getCurrentLogDir(), "dispatch_tc_FIN_" + envId + ".txt"), false, context.isContinueMode());
		this.commonReader = new CommonReader(CommonUtils.concatFile(com.navercorp.cubridqa.common.Constants.ENV_CTP_HOME + "/ha_repl/lib", "common.inc"));
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
				context.getFeedback().onStopEnvEvent(hostManager, mlog);

				if (context.shouldCleanupAfterQuit()) {
					cleanAfterQuit();
				}

				this.testCompleted = true;
				break;
			}

			try {
				haveLeapInCurrTestCase = CommonUtils.containToken(filename, "tz_leap_second_support");
			} catch (Exception e) {
				haveLeapInCurrTestCase = false;
			}

			if (isFinalDatabaseDirty() || haveLeapInCurrTestCase != haveLeapInLastDB) {
				if (haveLeapInCurrTestCase != haveLeapInLastDB) {
					mlog.println("ERROR: found different tz_leap_second_support (db: " + haveLeapInLastDB + ", case: " + haveLeapInCurrTestCase);
				}
				HaReplUtils.rebuildFinalDatabase(context, hostManager, mlog, haveLeapInCurrTestCase ? "tz_leap_second_support=yes" : "");
			} else {
				mlog.println("Needn't rebuild database.");
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
		
		//do clean
		ArrayList<SSHConnect> allNodeList = hostManager.getAllNodeList();
		GeneralScriptInput checkScript;
		for (SSHConnect ssh : allNodeList) {
			try {
				checkScript = new GeneralScriptInput("if [ -d \"${CUBRID}\" ];then find ${CUBRID} -name \"core.*\" -exec rm -rf {} \\; ;fi");
				ssh.execute(checkScript);
				mlog.println("clean core files: " + ssh.toString());
			} catch (Exception e) {
				mlog.println("fail to clean core files: " + e.getMessage());
			}
		}

		context.getFeedback().onTestCaseStartEvent(this.currentTestFile, this.hostManager.getEnvId());

		failCount = 0;
		testCount = 0;
		checkCount = 0;
		fail100List.clear();
		this.hasCore = false;
		this.userInfo = new StringBuffer();

		String logFilename = f.getAbsolutePath();
		logFilename = logFilename.substring(0, logFilename.lastIndexOf("."));

		masterDumpLog = new Log(logFilename + ".master.dump", false);

		ArrayList<SSHConnect> slaveAndReplicaList = hostManager.getAllSlaveAndReplicaList();
		for (SSHConnect ssh : slaveAndReplicaList) {
			Log slave_replicat_log = new Log(logFilename + "." + ssh.getTitle() + ".dump", false);
			logHashTable.put(ssh.getTitle(), slave_replicat_log);
		}
		log("===========================================================================================");
		long startTime = System.currentTimeMillis();

		clearDatabaseLog();
		this.hostManager.resetJdbcConns();

		TestReader tr = null;
		String stmt;
		String actualResult;
		int stmtCount = 0;
		ArrayList<String> checkSQLs;
		StringBuffer msg;
		boolean isSQL, isCMD;
		HoldCasCheck holdCasCheck;
		int holdCasAffected;
		try {
			tr = new TestReader(f, this.commonReader);
			main: while (true) {
				stmt = tr.readOneStatement();
				if (stmt == null)
					break;

				stmtCount++;
				
				//tbd: put into here to avoid to revise existing answer
				holdCasCheck = HoldCasCheck.check(stmt);
				if (holdCasCheck.isHoldCas()) {
					holdCasAffected = hostManager.changeJdbcCasMode(holdCasCheck.isSwitchOn());
					mlog.println("");
					mlog.println("[Line:" + tr.lineNum + "] " + stmt);
					mlog.println(holdCasAffected + " connection(s) affected.");					
					continue;
				}

				msg = new StringBuffer();
				msg.append(Constants.LINE_SEPARATOR).append("----------------------------------- Stmt ").append(stmtCount);
				msg.append("(line: ").append(tr.lineNum).append(") -----------------------------------");
				msg.append(Constants.LINE_SEPARATOR).append("[STMT-").append((tr.isTest() ? "TEST]" : (tr.isCheck() ? "CHECK]" : "")));
				msg.append(("[Line:" + tr.lineNum + "]")).append(stmt);
				log(msg.toString());
				
				if (tr.isTest()) {
					testCount++;
					mlog.println("");
					mlog.println("[Line:" + tr.lineNum + "] " + stmt);
					actualResult = executeOnMaster(tr.isSQL(), tr.isCMD(), stmt, true);
					if (actualResult.equals("")) {
						log("[RESULT] (N/A)");
					} else {
						log("[RESULT] " + Constants.LINE_SEPARATOR + actualResult);
					}
				} else if (tr.isCheck()) {
					boolean checkDML = stmt.equals("$HC_CHECK_FOR_DML");
					if (checkDML) {
						checkSQLs = getCheckSQLForDML();
						isSQL = true;
						isCMD = false;
					} else {
						if (context.isUpdateStatsOnCatalogClasses()) {
							executeSqlOnMaster(SQL_UPDATE_STATS_CLASSES);
						}
						checkSQLs = new ArrayList<String>();
						checkSQLs.add(stmt);
						isSQL = tr.isSQL();
						isCMD = tr.isCMD();
					}

					for (int i = 0; i < checkSQLs.size(); i++) {
						if (checkDML) {
							log(checkSQLs.get(i));
						}
						checkCount++;
						try {
							if (runCheckWithRetry(isSQL, isCMD, checkSQLs.get(i))) {
								log("[OK]" + ": [" + tr.lineNum + "]" + checkSQLs.get(i));								
							} else {
								String info = "[NOK]" + ": [" + tr.lineNum + "]" + checkSQLs.get(i);
								addFail(info);
								log(info);
							}
						} catch (SyncException e) {
							String info = "[NOK]" + ": [" + tr.lineNum + "]" + checkSQLs.get(i) + "(FAIL TO SYNC. BREAK!!!)";
							addFail(info);
							mlog.println("FAIL TO SYNC. BREAK.");
							log(info);
							break main;
						} catch (Exception e) {							
							String info = "[NOK]" + ": [" + tr.lineNum + "]" + checkSQLs.get(i) + "(" + e.getMessage() + ")";
							addFail("[NOK]" + ": [" + tr.lineNum + "]" + checkSQLs.get(i) + "(NOT EQUAL)");
							log(info);
						}
					}
				}
			}
		} catch (Exception e) {
			mlog.println(e.getMessage());
		} finally {			
			tr.close();
			this.hostManager.clearJdbcConns();
		}
		
		int failResolveMode = context.getHaSyncFailureResolveMode();
		
		boolean backupYn = failResolveMode == Constants.HA_SYNC_FAILURE_RESOLVE_MODE_CONTINUE;

		boolean testcasePassed = verifyResults(logFilename, backupYn);  //hasCore has been updated

		if (context.isFailureBackup() && hasCore == false && testcasePassed == false) {
			String backupDir = collectMoreInfoWhenFail();
			this.addFail("More fail information: " + backupDir);
		}
		
		String resultType = testcasePassed ? "OK" : "NOK";

		mlog.println("[" + resultType + "] " + f.getAbsolutePath());
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

		context.getFeedback().onTestCaseStopEvent(currentTestFile, testcasePassed, endTime - startTime, userInfo.toString(), hostManager.getEnvId(), false, hasCore,
				Constants.SKIP_TYPE_NO);
		
		System.out.println("[TESTCASE] " + currentTestFile + " " + (endTime - startTime) + "ms " + hostManager.getEnvId() + " " + "[" + resultType + "]");
		if (hasCore) {
			System.out.println("Found core or fatal error.");
		}
		
		Iterator logHashTableIterator = this.logHashTable.entrySet().iterator();
		while (logHashTableIterator.hasNext()) {
			Entry entry = (Entry) logHashTableIterator.next();
			((Log) entry.getValue()).close();
		}

		masterDumpLog.close();
		
		if (testcasePassed == false) {
			
			if (failResolveMode == Constants.HA_SYNC_FAILURE_RESOLVE_MODE_STOP) {
				String message = "Test failed for " + currentTestFile + " in " + hostManager.getEnvId() +". Quit!\n";
				System.out.println(message);
				mlog.println(message);
				mlog.close();
				System.exit(1);
			} if (failResolveMode == Constants.HA_SYNC_FAILURE_RESOLVE_MODE_WAIT) {				
				acceptConfirm("Test failed for " + currentTestFile + " in " + hostManager.getEnvId() +". Press 'Y' and Enter to continue: ");
				System.out.println("continue");
			} else {
				//continue
			}
		}		
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
				} else {
					mlog.println("Fail. Retry to compare.");
				}
			} catch (SyncException e) {
				throw e;
			} catch (Exception e) {
			}
			RETRY--;
		} while (RETRY > 0);

		masterDumpLog(expectResult);
		ArrayList<SSHConnect> slaveAndReplicaList = hostManager.getAllSlaveAndReplicaList();
		int len = slaveAndReplicaList.size();
		for (int i = 0; i < len; i++) {
			this.logHashTable.get(slaveAndReplicaList.get(i).getTitle()).println(actualResultList.get(i));
		}

		return sameData;
	}

	private boolean verifyResults(String logFilename, boolean backupYn) {
		
		ArrayList<String> failures = checkCoresAndErrors(backupYn);
		if (failures != null && failures.size() > 0) {
			this.hasCore = true;
			for (String f : failures) {
				this.addFail(f);
			}
			return false;
		}

		boolean withPatch = true;
		try {
			Iterator logHashTableIterator = this.logHashTable.entrySet().iterator();
			while (logHashTableIterator.hasNext()) {
				Entry entry = (Entry) logHashTableIterator.next();
				CheckDiff checkDiff = new CheckDiff();
				if (checkDiff.check(logFilename, "master", (String) entry.getKey(), context.getDiffMode()) != 0) {
					return false;
				}
				if (!checkDiff.hasPatch()) {
					withPatch = false;
				}
			}
			
			if(withPatch) {
				return true;
			}
		} catch (Exception e) {
			e.printStackTrace();
			this.addFail("[NOK] " + e.getMessage());
			return false;
		}	
		
		if (this.failCount > 0 || this.fail100List.size() > 0) {
			return false;
		}
		
		return true;
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
		ArrayList<SSHConnect> allNodeList = hostManager.getAllNodeList();
		String cmd = "find ${CUBRID}/log -type f -print | xargs -i sh -c 'cat /dev/null > {}'";
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
		ArrayList<SSHConnect> allNodeList = hostManager.getAllNodeList();

		String resultId = getResultId(context.getFeedback().getTaskId(), null, "FAIL", context.getBuildId());
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

	private ArrayList<String> checkCoresAndErrors(boolean backupYn) {
		ArrayList<SSHConnect> allNodeList = hostManager.getAllNodeList();
		String result;
		String hitHost;
		GeneralScriptInput checkScript;
		String error = null;
		String coreStack = null;
		String cat;
		ArrayList<String> failures = new ArrayList<String>();

		for (SSHConnect ssh : allNodeList) {
			error = null;
			cat = null;
			coreStack = null;
			try {
				hitHost = ssh.getHost().trim();
				checkScript = new GeneralScriptInput("find $CUBRID -name \"core.*\" | wc -l");
				result = ssh.execute(checkScript);
				mlog.println("Check core on " + hitHost + ":     \t\t\t" + result.trim());

				if (!result.trim().equals("0")) {
					cat = "CORE";
					error = "FOUND CORE FILE on host " + ssh.getUser() + "@" + hitHost;
					
					checkScript = new GeneralScriptInput("find $CUBRID -name \"core.*\" -exec analyzer.sh {} \\;");
					coreStack = ssh.execute(checkScript);
				}

				checkScript = new GeneralScriptInput("grep -r \"FATAL ERROR\" $CUBRID/log/* | wc -l");
				result = ssh.execute(checkScript);
				mlog.println("Check fatal error on " + hitHost + ": \t\t\t" + result.trim());
				if (!result.trim().equals("0")) {
					if (cat == null) {
						cat = "FATAL";
						error = "FOUND FATAL ERROR on host " + ssh.getUser() + "@" + hitHost;
					} else {
						error = error + Constants.LINE_SEPARATOR + "FOUND FATAL ERROR on host " + ssh.getUser() + "@" + hitHost;
					}
				}

				if (error == null) {
					continue;
				}

			} catch (Exception e) {
				mlog.println("Fail to check important errors: " + e.getMessage() + " in " + ssh.toString());
				continue;
			}

			if (backupYn) {
				String resultId = getResultId(context.getFeedback().getTaskId(), hitHost, cat, context.getBuildId());
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
				if (coreStack != null && coreStack.trim().equals("") == false) {
					this.userInfo.append(coreStack).append(Constants.LINE_SEPARATOR);
				}
			}

			failures.add("[NOK] " + error);			
		}
		return failures;
	}

	private static GeneralScriptInput getBackupScripts(String resultId, String testCase) {

		GeneralScriptInput script = new GeneralScriptInput("");
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
		String cmd = "grep -s -Ri 'Internal error' ${CUBRID}/log | wc -l";
		ArrayList<SSHConnect> allNodeList = hostManager.getAllNodeList();
		String result;
		for (SSHConnect ssh : allNodeList) {
			try {
				mlog.print("check cubrid error (" + ssh + ") ... ");
				result = executeScript(ssh, false, true, cmd, false);
				result = result.trim();
				if (result.equals("0")) {
					mlog.println(" DONE");
				} else {
					addFail("[NOK]" + ssh + " HIT INTERNAL ERROR");
					mlog.println(" DONE HIT INTERNAL ERROR" + Constants.LINE_SEPARATOR);
				}
			} catch (Exception e) {
				mlog.println("fail to check cubrid error: " + e.getMessage() + " in " + ssh.toString());
			}
		}
	}

	private void addFail(String info) {
		this.failCount ++;
		if (fail100List.size() < FAIL_MAX_STAT)
			this.fail100List.add(info);
	}

	private ArrayList<String> getCheckSQLForDML() throws Exception {
		SSHConnect ssh = hostManager.getHost("master");
		String script = "csql -u dba "
				+ hostManager.getTestDb()
				+ " -c \"select 'FIND'||'_'||'PK_CLASS', class_name, count(*) from db_attribute where class_name in (select distinct class_name from db_index where is_primary_key='YES' and class_name in (select class_name from db_class where is_system_class='NO' and lower(class_name)<>'qa_system_tb_flag')) group by class_name;\" | grep 'FIND_PK_CLASS' ";
		GeneralScriptInput csql = new GeneralScriptInput(script);
		String tablesResult = ssh.execute(csql);
		ArrayList<String[]> tablesToBeVerified = HaReplUtils.extractTableToBeVerified(tablesResult, "FIND_PK_CLASS");
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

	private String executeSQL(String sql, Connection conn) {
		String res = "";
		ResultSet rs = null;
		Statement stmt = null;
	
		try {
			stmt = conn.createStatement();
		} catch (Exception ex) {
			this.addFail("[NOK] DB connection creation fail!");
			if(stmt!=null)
				try {
					stmt.close();
				} catch (SQLException e) {
					e.printStackTrace();
				}
			return "[NOK] DB connection creation fail!";
		}
			
		try {
			String uppperSql = sql.trim().toUpperCase();			
			if (uppperSql.startsWith("SELECT")) {
				rs = stmt.executeQuery(sql);
				res += printdata(rs);
			} else if (uppperSql.startsWith("INSERT") || uppperSql.startsWith("UPDATE") || uppperSql.startsWith("DELETE")) {
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

	private String executeSqlOnMaster(String query) throws Exception {
		String result;
		if (context.getTestmode().equals("jdbc")) {
			result = executeSQL(query, hostManager.getMasterJdbcConn());
		} else {
			SSHConnect ssh = hostManager.getHost("master");
			result = executeScript(ssh, true, false, query, false);
		}
		return result;
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
			Connection conn = hostManager.getMasterJdbcConn();
			result = executeSQL(stmt, conn);
			result += executeSQL(flag_SQL, conn);
		} else {
			result = executeScript(ssh, isSQL, isCMD, stmt, isTest);
		}

		if (isTest && oriStmt != null) {
			String u = CommonUtils.replace(oriStmt, " ", "").toUpperCase();
			boolean shouldExecuteOnSlave = u.indexOf("SETSYSTEMPARAMETERS") != -1 && u.indexOf("TZ_LEAP_SECOND_SUPPORT") == -1;
			
			if (shouldExecuteOnSlave) {
				result = result.trim();
				ArrayList<SSHConnect> slaveAndReplicaList = hostManager.getAllSlaveAndReplicaList();
				for (SSHConnect ssh1 : slaveAndReplicaList) {
					result = executeScript(ssh1, true, false, oriStmt, true);
				}
			}
		}

		if (isTest) {
			log(result);  //should be deleted
		}
		return result;
	}

	private ArrayList<String> executeOnSlaveAndReplica(boolean isSQL, boolean isCMD, String stmt, boolean isTest) throws Exception {
		ArrayList<String> resultList = new ArrayList<String>();
		String result = null;

		SSHConnect masterSsh = hostManager.getHost("master");
		GeneralScriptInput script = new GeneralScriptInput("csql -u dba " + hostManager.getTestDb() + " -c \"select 'EXP' ||'ECT-'|| v from qa_system_tb_flag;\" | grep EXPECT");

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

		ArrayList<SSHConnect> slaveAndReplicaList = hostManager.getAllSlaveAndReplicaList();
		for (SSHConnect ssh : slaveAndReplicaList) {
			if (isSQL && !isTest) {
				waitDataReplicated(ssh, expectedFlagId);
			}
			result = executeScript(ssh, isSQL, isCMD, stmt, isTest);

			resultList.add(result);
		}
		return resultList;
	}

	private String executeScript(SSHConnect ssh, boolean isSQL, boolean isCMD, String stmt, boolean isTest) throws Exception {

		String result = null;
		if (isSQL) {
			String script = "csql -u dba " + hostManager.getTestDb() + " 2>&1 << EOF" + Constants.LINE_SEPARATOR;
			script += ";time off" + Constants.LINE_SEPARATOR;
			script += stmt + Constants.LINE_SEPARATOR;
			script += "EOF" + Constants.LINE_SEPARATOR;

			GeneralScriptInput csql = new GeneralScriptInput(script);
			result = ssh.execute(csql);

		} else if (isCMD) {
			GeneralScriptInput cmd = new GeneralScriptInput(stmt + " 2>&1");
			result = ssh.execute(cmd);
		}

		return result;
	}

	private boolean isFinalDatabaseDirty() {
		boolean result;
		int loop = 1;
		while (true) {
			try {
				mlog.println("Begin to check whether database is dirty ... try " + loop);
				result = __isDatabaseDirty();
				break;
			} catch (Exception e) {
				mlog.println("got error when check whether database is dirty: " + e.getMessage());
			}
			loop ++;
		}
		return result;
	}

	private boolean __isDatabaseDirty() throws Exception {
		ArrayList<SSHConnect> allHosts = hostManager.getAllNodeList();

		StringBuffer s = new StringBuffer();
		s.append("select 'TABLE'||':db_class' check_table, t.* from db_class t where is_system_class='NO' and upper(class_name)<>'QA_SYSTEM_TB_FLAG';");
		s.append("select 'TABLE'||':db_stored_procedure', t.* from db_stored_procedure t; ");
		s.append("select 'TABLE'||':db_trig', t.* from db_trig t; ");
		s.append("select 'TABLE'||':db_partition', t.* from db_partition t; ");
		s.append("select 'TABLE'||':db_meth_file', t.* from db_meth_file t; ");
		s.append("select 'TABLE'||':db_serial', t.* from db_serial t; ");
		s.append("select 'TABLE'||':db_user', t.* from db_user t where name not in ('DBA', 'PUBLIC'); ");

		GeneralScriptInput script = new GeneralScriptInput("csql -u dba " + hostManager.getTestDb() + " -c \"" + s.toString() + "\"");
		GeneralScriptInput scriptMode = new GeneralScriptInput("cubrid changemode " + hostManager.getTestDb());

		String result;
		SSHConnect ssh;
		for (int i = 0; i < allHosts.size(); i++) {
			ssh = allHosts.get(i);
			result = ssh.execute(scriptMode);
			if (i == 0) {
				if (result.indexOf("is active") == -1) {
					mlog.println("ERROR: 'active' status is expected on node " + ssh);
					mlog.println(result);					
					return true;
				}
			} else if (result.indexOf("is standby") == -1) {
				mlog.println("ERROR: 'standby' status is expected on node " + ssh);
				mlog.println(result);
				return true;
			}
			result = ssh.execute(script);
			if (result.indexOf("TABLE:") != -1) {
				mlog.println("ERROR: remained: " + ssh);
				mlog.println(result);
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
		GeneralScriptInput script;

		script = new GeneralScriptInput("csql -u dba " + hostManager.getTestDb() + " -c \"drop table QA_SYSTEM_TB_FLAG;\"");
		masterNode.execute(script);

		this.globalFlag++;
		script = new GeneralScriptInput("csql -u dba " + hostManager.getTestDb() + " -c \"create table QA_SYSTEM_TB_FLAG (v BIGINT primary key);insert into QA_SYSTEM_TB_FLAG values ("
				+ this.globalFlag + ");\"");
		masterNode.execute(script);

		mlog.println("DONE");
	}

	private boolean waitDataReplicated(SSHConnect ssh, long expectedFlagId) throws Exception {
		GeneralScriptInput script = new GeneralScriptInput("csql -u dba " + hostManager.getTestDb() + " -c \"select 'GO'||'OD-'||v FROM QA_SYSTEM_TB_FLAG \" | grep GOOD");
		String result;
		int index = 1;

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
			if ((end_time - start_time) > context.getHaSyncDetectTimeoutInMs()) {
				mlog.println(" SYNC TIMEOUT (" + calcTimeInterval(start_time) + ")");
				throw new SyncException();
			}

			Thread.sleep(100 + index * 10);
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

	private void cleanAfterQuit() {
		StringBuffer s = new StringBuffer();
		s.append("cubrid service stop;");
		s.append("cubrid hb stop;");
		s.append("pkill cub").append(";");
		s.append("ipcs | grep $USER | awk '{print $2}' | xargs -i ipcrm -m {}").append(";");

		ArrayList<SSHConnect> list = hostManager.getAllNodeList();
		for (SSHConnect ssh : list) {
			try {
				executeScript(ssh, false, true, s.toString(), false);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	public InstanceManager getInstanceManager() {
		return this.hostManager;
	}

	private static String calcTimeInterval(long start_time) {
		long end_time = System.currentTimeMillis();
		return ((end_time - start_time) / 1000) + " seconds";
	}
	
	private static void acceptConfirm(String message) {

		try {
			int avail = System.in.available();
			for (int i = 0; i < avail; i++) {
				System.in.read();
			}
		} catch (Exception e) {
		}

		synchronized (Dispatch.getInstance()) {
			int c = 0;
			System.out.print(message);
			while (true) {
				try {
					c = System.in.read();
				} catch (Exception e) {
					e.printStackTrace();
					continue;
				}
				if (c == 'Y') {
					break;
				}
				if (c == '\n') {
					System.out.print(message);
				}
			}
		}
	}
}
