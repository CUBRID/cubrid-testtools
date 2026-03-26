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

package com.navercorp.cubridqa.shell.result;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;

import javax.sql.DataSource;

import org.apache.commons.dbcp.BasicDataSource;

import com.navercorp.cubridqa.common.ConfigParameterConstants;
import com.navercorp.cubridqa.shell.common.CommonUtils;
import com.navercorp.cubridqa.shell.common.Constants;
import com.navercorp.cubridqa.shell.common.HttpUtil;
import com.navercorp.cubridqa.shell.common.Log;
import com.navercorp.cubridqa.shell.main.Context;
import com.navercorp.cubridqa.shell.main.Feedback;

public class FeedbackDB implements Feedback {

	Context context;
	DataSource ds = null;
	final int MAX_CONTENT_SIZE = 128 * 1024;

	int task_id = 0;
	int tbdNum = 0;
	int macroSkippedNum = 0;
	int tempSkippedNum = 0;
	boolean lastPassEligible = false;
	String lastPassVersionId = null;
	String lastPassBuildWid = null;

	public FeedbackDB(Context context) {
		this.context = context;
		this.ds = setupDataSource();
	}

	@Override
	public void onTaskStartEvent(String buildFilename) {
		Connection conn = null;
		PreparedStatement stmt = null;
		String sql;
		ResultSet rs = null;

		Timestamp d = new Timestamp(System.currentTimeMillis());

		String build = context.getTestBuild();

		String category = context.getTestCategory();
		String os = context.getTestPlatform();

		String version = context.getVersion();
		String msgId = context.getMsgId();

		sql = "insert into shell_main(test_build, category, start_time, os, version, msg_id) values(?, ?, ?, ?, ?, ?)";

		try {
			conn = ds.getConnection();
			stmt = conn.prepareStatement(sql);
			stmt.setString(1, build);
			stmt.setString(2, category);
			stmt.setTimestamp(3, d);
			stmt.setString(4, os);
			stmt.setString(5, version);
			stmt.setString(6, msgId);
			stmt.executeUpdate();
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			close(stmt);
		}

		// get task_id
		sql = "select last_insert_id()";
		try {
			stmt = conn.prepareStatement(sql);
			rs = stmt.executeQuery();

			if (rs.next()) {
				task_id = rs.getInt(1);
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			close(rs);
			close(stmt);
			close(conn);
		}

		Log log = new Log(CommonUtils.concatFile(context.getCurrentLogDir(), "current_task_id"), false, false);
		log.println(String.valueOf(task_id));
		context.setTaskId(task_id);
		log.close();
		resolveLastPassBuildContext(build);
	}

	@Override
	public void onTaskContinueEvent() {

		String cont = null;
		try {
			cont = CommonUtils.getFileContent(CommonUtils.concatFile(context.getCurrentLogDir(), "current_task_id"));
			this.task_id = Integer.parseInt(cont.trim());
			context.setTaskId(task_id);
			resolveLastPassBuildContext(context.getTestBuild());
		} catch (Exception e) {
			this.task_id = -1;
			clearLastPassBuildContext();
			e.printStackTrace();
		}
	}

	@Override
	public void onTaskStopEvent() {

		showTestResult();

		shutdownDataSource();

		String noticeUrl = context.getProperty(ConfigParameterConstants.FEEDBACK_NOTICE_QAHOME_URL);
		if (noticeUrl != null && noticeUrl.trim().length() > 0) {
			try {
				noticeUrl = CommonUtils.replace(noticeUrl, "<MAINID>", String.valueOf(task_id));
				String res = HttpUtil.getHtmlSource(noticeUrl);
				System.out.println("The Import Url = " + noticeUrl);
				System.out.println("#Status: " + res);
			} catch (Exception e) {
				System.out.println("Notice QA homepage to load: FAIL. Url=" + noticeUrl);
			}
		}
	}

	private synchronized void updateShellMain(boolean newSucc, boolean newCore) {
		Connection conn = null;

		PreparedStatement stmt = null;
		String sql;
		ResultSet rs = null;

		int executed_num = 0;

		long endTime = System.currentTimeMillis();
		Timestamp d = new Timestamp(endTime);

		// get fail number
		int fail_num = 0;
		int succ_num = 0;
		String testError = null;
		try {
			conn = ds.getConnection();
			sql = "select success_num, fail_num,test_error from shell_main where main_id=?";
			stmt = conn.prepareStatement(sql);
			stmt.setInt(1, task_id);
			rs = stmt.executeQuery();
			rs.next();
			succ_num = rs.getInt(1);
			fail_num = rs.getInt(2);
			testError = rs.getString(3);
			rs.close();
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			close(rs);
			close(stmt);
		}

		if (newSucc) {
			succ_num = succ_num + 1;
		} else {
			fail_num = fail_num + 1;
		}

		if (newCore) {
			testError = "Y";
		} else {
			testError = testError == null || testError.trim().equals("Y") == false ? "N" : "Y";
		}

		executed_num = fail_num + succ_num;

		float execute_rate = tbdNum <= 0 ? 0 : (float) executed_num / (float) tbdNum * 100;

		try {
			float success_rate = tbdNum <= 0 ? 0 : (float) succ_num / (float) executed_num * 100;

			sql = "update shell_main set success_num=?, fail_num=?, end_time=?, test_rate=?, success_rate=?, elapse_time=(?-start_time), test_error=? where main_id=?";
			stmt = conn.prepareStatement(sql);
			stmt.setInt(1, succ_num);
			stmt.setInt(2, fail_num);
			stmt.setTimestamp(3, d);
			stmt.setFloat(4, execute_rate);
			stmt.setFloat(5, success_rate);
			stmt.setTimestamp(6, d);
			stmt.setString(7, testError);
			stmt.setInt(8, task_id);
			stmt.executeUpdate();
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			close(stmt);
			close(conn);
		}
	}

	public void showTestResult() {
		Connection conn = null;

		PreparedStatement stmt = null;
		String sql;
		ResultSet rs = null;

		// get fail number
		int fail_num = 0;
		int succ_num = 0;
		float execute_rate = 0;
		float success_rate = 0;
		try {
			conn = ds.getConnection();
			sql = "select success_num, fail_num,test_rate,success_rate from shell_main where main_id=?";
			stmt = conn.prepareStatement(sql);
			stmt.setInt(1, task_id);
			rs = stmt.executeQuery();
			rs.next();
			succ_num = rs.getInt(1);
			fail_num = rs.getInt(2);
			execute_rate = rs.getFloat(3);
			success_rate = rs.getFloat(4);
			rs.close();
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			close(rs);
			close(stmt);
			close(conn);
		}

		System.out.println("Success num: " + succ_num + ", Fail_num: " + fail_num + ", Skipped(macro): " + macroSkippedNum + ", Skipped(temp): " + tempSkippedNum + ", Total Scenario: "
				+ (tbdNum + macroSkippedNum + tempSkippedNum));
		System.out.println("Test Rate: " + execute_rate + "%");
		System.out.println("Success Rate: " + success_rate + "%");
	}

	@Override
	public void setTotalTestCase(int tbdNum, int macroSkippedNum, int tempSkippedNum) {
		Connection conn = null;
		PreparedStatement stmt = null;
		String sql;
		ResultSet rs = null;

		if (context.isContinueMode() == false) {
			this.tbdNum = tbdNum;
			this.macroSkippedNum = macroSkippedNum;
			this.tempSkippedNum = tempSkippedNum;

			try {
				conn = ds.getConnection();

				sql = "update shell_main set total_scenario=?, executed_scenario=?,skipped_macro=?, skipped_temp=?  where main_id=?";
				stmt = conn.prepareStatement(sql);
				stmt.setInt(1, tbdNum + macroSkippedNum + tempSkippedNum);
				stmt.setInt(2, tbdNum);
				stmt.setInt(3, macroSkippedNum);
				stmt.setInt(4, tempSkippedNum);
				stmt.setInt(5, task_id);
				stmt.executeUpdate();
			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				close(stmt);
				close(conn);
			}
		} else {
			try {
				conn = ds.getConnection();
				sql = "select executed_scenario, skipped_macro, skipped_temp from shell_main where main_id = ?";
				stmt = conn.prepareStatement(sql);
				stmt.setInt(1, task_id);
				rs = stmt.executeQuery();

				if (rs.next()) {
					this.tbdNum = rs.getInt(1);
					this.macroSkippedNum = rs.getInt(2);
					this.tempSkippedNum = rs.getInt(3);
				}
			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				close(rs);
				close(stmt);
				close(conn);
			}
		}
	}

	@Override
	public void onTestCaseStopEvent(String testCase, boolean flag, long elapseTime, String resultCont, String envIdentify, boolean isTimeOut, boolean hasCore, String skippedType, int retryCount) {
		onTestCaseStopEvent(testCase, flag, elapseTime, resultCont, resultCont, envIdentify, isTimeOut, hasCore, skippedType, retryCount);
	}

	@Override
	public void onTestCaseStopEvent(String testCase, boolean flag, long elapseTime, String resultCont, String lastPassResultCont, String envIdentify, boolean isTimeOut, boolean hasCore,
			String skippedType, int retryCount) {
		String category = context.getTestCategory();
		Timestamp d = new Timestamp(System.currentTimeMillis());
		if (lastPassResultCont == null) {
			lastPassResultCont = resultCont;
		}
		String shellItemResultCont = trimResultContent(resultCont);
		boolean isExecutedCase = isExecutedCase(skippedType);

		if (context.isSkipToSaveSuccCase() == false || flag == false) {
			Connection conn = null;

			try {
				conn = ds.getConnection();
				int itemId = insertShellItem(conn, testCase, flag, elapseTime, shellItemResultCont, envIdentify, isTimeOut, hasCore, skippedType, retryCount, d);
				if (isExecutedCase && flag == false && itemId > 0) {
					insertLastPassSnapshot(conn, itemId, category, testCase);
				}
			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				close(conn);
			}
		}

		if (isExecutedCase && flag) {
			refreshLastPass(category, testCase, lastPassResultCont, d);
		}

		if (isExecutedCase) {
			updateShellMain(flag, hasCore);
		}
	}

	@Override
	public void onTestCaseStopEventForRetry(String testCase, boolean flag, long elapseTime, String resultCont, String envIdentify, boolean isTimeOut, boolean hasCore, String skippedType,
			int retryCount) {
		Connection conn = null;

		PreparedStatement stmt = null;
		String sql;

		Timestamp d = new Timestamp(System.currentTimeMillis());
		resultCont = trimResultContent(resultCont);

		sql = "insert into shell_retry_log(main_id, case_file, env_node, elapse_time, test_result, is_timeout, has_core, result_cont, end_time, is_skipped, retry_count) values(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

		try {
			conn = ds.getConnection();

			stmt = conn.prepareStatement(sql);
			stmt.setInt(1, task_id);
			stmt.setString(2, testCase);
			stmt.setString(3, envIdentify);
			stmt.setDouble(4, elapseTime);
			if (skippedType.equals(Constants.SKIP_TYPE_NO)) {
				stmt.setString(5, (flag ? "OK" : "NOK"));
			} else {
				stmt.setString(5, "");
			}
			stmt.setString(6, (isTimeOut ? "Y" : "N"));
			stmt.setString(7, (hasCore ? "Y" : "N"));
			stmt.setString(8, resultCont);
			stmt.setTimestamp(9, d);
			stmt.setString(10, skippedType);
			stmt.setInt(11, retryCount);
			stmt.executeUpdate();

		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			close(stmt);
			close(conn);
		}
	}

	private int insertShellItem(Connection conn, String testCase, boolean flag, long elapseTime, String resultCont, String envIdentify, boolean isTimeOut, boolean hasCore, String skippedType,
			int retryCount, Timestamp endTime) throws Exception {
		PreparedStatement stmt = null;
		String sql = "insert into shell_items(main_id, case_file, env_node, elapse_time, test_result, is_timeout, has_core, result_cont, end_time, is_skipped, retry_count) values(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

		try {
			stmt = conn.prepareStatement(sql);
			stmt.setInt(1, task_id);
			stmt.setString(2, testCase);
			stmt.setString(3, envIdentify);
			stmt.setDouble(4, elapseTime);
			if (isExecutedCase(skippedType)) {
				stmt.setString(5, (flag ? "OK" : "NOK"));
			} else {
				stmt.setString(5, "");
			}
			stmt.setString(6, (isTimeOut ? "Y" : "N"));
			stmt.setString(7, (hasCore ? "Y" : "N"));
			stmt.setString(8, resultCont);
			stmt.setTimestamp(9, endTime);
			stmt.setString(10, skippedType);
			stmt.setInt(11, retryCount);
			stmt.executeUpdate();
		} finally {
			close(stmt);
		}

		if (isExecutedCase(skippedType) && flag == false) {
			return selectLastInsertId(conn);
		}

		return 0;
	}

	private void refreshLastPass(String category, String testCase, String resultCont, Timestamp endTime) {
		if (isLastPassEligible() == false) {
			return;
		}

		Connection conn = null;
		try {
			conn = ds.getConnection();
			upsertLastPass(conn, category, testCase, resultCont, endTime, context.getTestBuild());
		} catch (Exception e) {
			warnLastPass("shell_last_pass refresh", category, testCase, e);
		} finally {
			close(conn);
		}
	}

	private void insertLastPassSnapshot(Connection conn, int itemId, String category, String testCase) {
		if (isLastPassEligible() == false) {
			return;
		}

		PreparedStatement selectStmt = null;
		PreparedStatement insertStmt = null;
		ResultSet rs = null;
		boolean hasLastPass = false;
		Integer lastPassMainId = null;
		String lastPassBuildId = null;
		String lastPassResultCont = null;
		Timestamp lastPassEndTime = null;

		try {
			selectStmt = conn.prepareStatement(
					"select main_id, build_id, result_cont, end_time from shell_last_pass where category=? and version_id=? and case_file=? and build_wid<=? limit 1");
			selectStmt.setString(1, category);
			selectStmt.setString(2, lastPassVersionId);
			selectStmt.setString(3, testCase);
			selectStmt.setString(4, lastPassBuildWid);
			rs = selectStmt.executeQuery();

			if (rs.next()) {
				hasLastPass = true;
				int fetchedLastPassMainId = rs.getInt("main_id");
				if (rs.wasNull() == false) {
					lastPassMainId = Integer.valueOf(fetchedLastPassMainId);
				}
				lastPassBuildId = rs.getString("build_id");
				lastPassResultCont = rs.getString("result_cont");
				lastPassEndTime = rs.getTimestamp("end_time");
			}

			close(rs);
			rs = null;
			close(selectStmt);
			selectStmt = null;

			if (hasLastPass) {
				insertStmt = conn.prepareStatement(
						"insert into shell_last_pass_snapshot(item_id, last_pass_main_id, last_pass_build_id, last_pass_result_cont, last_pass_end_time) values(?, ?, ?, ?, ?)");
				insertStmt.setInt(1, itemId);
				if (lastPassMainId == null) {
					insertStmt.setNull(2, Types.INTEGER);
				} else {
					insertStmt.setInt(2, lastPassMainId);
				}
				insertStmt.setString(3, lastPassBuildId);
				insertStmt.setString(4, lastPassResultCont);
				insertStmt.setTimestamp(5, lastPassEndTime);
				insertStmt.executeUpdate();
			}
		} catch (Exception e) {
			warnLastPass("shell_last_pass_snapshot insert", category, testCase, e);
		} finally {
			close(rs);
			close(selectStmt);
			close(insertStmt);
		}
	}

	private void upsertLastPass(Connection conn, String category, String testCase, String resultCont, Timestamp endTime, String buildId) throws Exception {
		PreparedStatement selectStmt = null;
		PreparedStatement updateStmt = null;
		PreparedStatement insertStmt = null;
		ResultSet rs = null;
		boolean hasStoredLastPass = false;
		String storedBuildId = null;
		String storedBuildWid = null;

		try {
			selectStmt = conn.prepareStatement("select build_id, build_wid from shell_last_pass where category=? and version_id=? and case_file=?");
			selectStmt.setString(1, category);
			selectStmt.setString(2, lastPassVersionId);
			selectStmt.setString(3, testCase);
			rs = selectStmt.executeQuery();

			if (rs.next()) {
				hasStoredLastPass = true;
				storedBuildId = rs.getString("build_id");
				storedBuildWid = rs.getString("build_wid");
			}

			close(rs);
			rs = null;
			close(selectStmt);
			selectStmt = null;

			if (hasStoredLastPass) {
				if (shouldReplaceLastPass(storedBuildId, storedBuildWid, buildId)) {
					updateStmt = conn.prepareStatement(
							"update shell_last_pass set build_id=?, build_wid=?, main_id=?, result_cont=?, end_time=? where category=? and version_id=? and case_file=?");
					updateStmt.setString(1, buildId);
					updateStmt.setString(2, lastPassBuildWid);
					updateStmt.setInt(3, task_id);
					updateStmt.setString(4, resultCont);
					updateStmt.setTimestamp(5, endTime);
					updateStmt.setString(6, category);
					updateStmt.setString(7, lastPassVersionId);
					updateStmt.setString(8, testCase);
					updateStmt.executeUpdate();
				}
			} else {
				insertStmt = conn.prepareStatement(
						"insert into shell_last_pass(category, version_id, case_file, build_id, build_wid, main_id, result_cont, end_time) values(?, ?, ?, ?, ?, ?, ?, ?)");
				insertStmt.setString(1, category);
				insertStmt.setString(2, lastPassVersionId);
				insertStmt.setString(3, testCase);
				insertStmt.setString(4, buildId);
				insertStmt.setString(5, lastPassBuildWid);
				insertStmt.setInt(6, task_id);
				insertStmt.setString(7, resultCont);
				insertStmt.setTimestamp(8, endTime);
				insertStmt.executeUpdate();
			}
		} finally {
			close(rs);
			close(selectStmt);
			close(updateStmt);
			close(insertStmt);
		}
	}

	private boolean shouldReplaceLastPass(String storedBuildId, String storedBuildWid, String buildId) {
		if (storedBuildWid == null || storedBuildWid.trim().length() == 0) {
			return true;
		}

		int compareResult = storedBuildWid.compareTo(lastPassBuildWid);
		if (compareResult < 0) {
			return true;
		}

		return compareResult == 0 && buildId != null && buildId.equals(storedBuildId);
	}

	private int selectLastInsertId(Connection conn) throws Exception {
		PreparedStatement stmt = null;
		ResultSet rs = null;

		try {
			stmt = conn.prepareStatement("select last_insert_id()");
			rs = stmt.executeQuery();
			if (rs.next()) {
				return rs.getInt(1);
			}
		} finally {
			close(rs);
			close(stmt);
		}

		return 0;
	}

	private String trimResultContent(String resultCont) {
		if (resultCont == null) {
			return null;
		}

		int resultContSize = resultCont.length();
		if (resultContSize <= MAX_CONTENT_SIZE) {
			return resultCont;
		}

		String resultContPrefix = resultCont.substring(0, MAX_CONTENT_SIZE / 2);
		String resultContSuffix = resultCont.substring(resultContSize - MAX_CONTENT_SIZE / 2, resultContSize);
		return resultContPrefix + System.getProperty("line.separator") + "********** THE CONTENT LENGTH IS" + resultContSize + "(TRIMMED) **********" + System.getProperty("line.separator")
				+ resultContSuffix;
	}

	private boolean isExecutedCase(String skippedType) {
		return Constants.SKIP_TYPE_NO.equals(skippedType);
	}

	private void resolveLastPassBuildContext(String buildId) {
		Connection conn = null;
		PreparedStatement stmt = null;
		ResultSet rs = null;

		clearLastPassBuildContext();
		if (buildId == null || buildId.trim().length() == 0) {
			return;
		}

		try {
			conn = ds.getConnection();
			stmt = conn.prepareStatement("select version_id, cversion(build_id) as build_wid from cubrid_build where build_id=? and build_type='general' limit 1");
			stmt.setString(1, buildId);
			rs = stmt.executeQuery();
			if (rs.next()) {
				lastPassVersionId = rs.getString("version_id");
				lastPassBuildWid = rs.getString("build_wid");
				lastPassEligible = lastPassVersionId != null && lastPassVersionId.trim().length() > 0 && lastPassBuildWid != null && lastPassBuildWid.trim().length() > 0;
			}
		} catch (Exception e) {
			clearLastPassBuildContext();
			warnLastPassBuild("cubrid_build resolve", buildId, e);
		} finally {
			close(rs);
			close(stmt);
			close(conn);
		}
	}

	private void clearLastPassBuildContext() {
		lastPassEligible = false;
		lastPassVersionId = null;
		lastPassBuildWid = null;
	}

	private boolean isLastPassEligible() {
		return lastPassEligible && lastPassVersionId != null && lastPassBuildWid != null;
	}

	private void warnLastPass(String action, String category, String testCase, Exception e) {
		System.err.println("[WARN] " + action + " failed for " + category + ":" + testCase + " - " + e.getMessage());
	}

	private void warnLastPassBuild(String action, String buildId, Exception e) {
		System.err.println("[WARN] " + action + " failed for build_id=" + buildId + " - " + e.getMessage());
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub
	}

	@Override
	public void onTestCaseStartEvent(String testCase, String envIdentify) {
		// TODO Auto-generated method stub
	}

	@Override
	public void onTestCaseMonitor(String testCase, String action, String envIdentify) {
		System.out.println(action + " " + testCase + " " + envIdentify);

	}

	@Override
	public void onDeployStart(String envIdentify) {
		System.out.println("DEPLAY START: " + envIdentify);

	}

	@Override
	public void onDeployStop(String envIdentify) {
		System.out.println("DEPLAY STOP: " + envIdentify);

	}

	@Override
	public void onSvnUpdateStart(String envIdentify) {
		System.out.println("UPDATE TEST CASES START: " + envIdentify);

	}

	@Override
	public void onSvnUpdateStop(String envIdentify) {
		System.out.println("UPDATE TEST CASES STOP: " + envIdentify);

	}

	private void close(Statement stmt) {
		try {
			if (stmt != null)
				stmt.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void close(ResultSet rs) {
		try {
			if (rs != null)
				rs.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void close(Connection conn) {
		try {
			if (conn != null)
				conn.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private DataSource setupDataSource() {
		String url = context.getFeedbackDbUrl();
		String user = context.getFeedbackDbUser();
		String pwd = context.getFeedbackDbPwd();

		BasicDataSource ds = new BasicDataSource();
		ds.setDriverClassName("cubrid.jdbc.driver.CUBRIDDriver");
		ds.setUsername(user);
		ds.setPassword(pwd);
		ds.setUrl(url);
		return ds;
	}

	private void shutdownDataSource() {
		BasicDataSource bds = (BasicDataSource) ds;
		try {
			bds.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	public int getTestId() {
		return this.task_id;
	}

	@Override
	public void onStopEnvEvent(String envIdentify) {
		// TODO Auto-generated method stub
	}
}
