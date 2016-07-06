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

import javax.sql.DataSource;

import org.apache.commons.dbcp.BasicDataSource;

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

		String category = context.getProperty("main.testing.category");
		String os = context.getProperty("main.testing.os");

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
	}

	@Override
	public void onTaskContinueEvent() {

		String cont = null;
		try {
			cont = CommonUtils.getFileContent(CommonUtils.concatFile(context.getToolHome() + "/" + Constants.CURRENT_LOG_DIR, "current_task_id"));
			this.task_id = Integer.parseInt(cont.trim());
			context.setTaskId(task_id);
		} catch (Exception e) {
			this.task_id = -1;
			e.printStackTrace();
		}
	}

	@Override
	public void onTaskStopEvent() {

		refreshShellMain(true);

		shutdownDataSource();

		String noticeUrl = context.getProperty("feedback.qahome.notice_load");
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

	public void notifyUpdateMain() {
		Connection conn = null;
		PreparedStatement stmt = null;
		String sql;
		try {
			conn = ds.getConnection();
			sql = "update shell_main set test_error='Y' where main_id=? and (test_error='N' or test_error is null)";
			stmt = conn.prepareStatement(sql);
			stmt.setInt(1, task_id);
			stmt.executeUpdate();
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			close(stmt);
			close(conn);
		}
	}

	public void refreshShellMain(boolean show) {
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
		try {
			conn = ds.getConnection();
			sql = "select count(*) from shell_items where main_id=? and test_result=?";
			stmt = conn.prepareStatement(sql);
			stmt.setInt(1, task_id);
			stmt.setString(2, "OK");
			rs = stmt.executeQuery();
			if (rs.next()) {
				succ_num = rs.getInt(1);
			}
			rs.close();

			stmt.setInt(1, task_id);
			stmt.setString(2, "NOK");
			rs = stmt.executeQuery();
			if (rs.next()) {
				fail_num = rs.getInt(1);
			}
			rs.close();
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			close(rs);
			close(stmt);
		}

		executed_num = fail_num + succ_num;

		float execute_rate = tbdNum <= 0 ? 0 : (float) executed_num / (float) tbdNum * 100;

		try {
			float success_rate = tbdNum <= 0 ? 0 : (float) succ_num / (float) executed_num * 100;

			if (show) {
				System.out.println("Success num: " + succ_num + ", Fail_num: " + fail_num + ", Skipped(macro): " + macroSkippedNum + ", Skipped(temp): " + tempSkippedNum + ", Total Scenario: "
						+ (tbdNum + macroSkippedNum));
				System.out.println("Test Rate: " + execute_rate + "%");
				System.out.println("Success Rate: " + success_rate + "%");
			}
			sql = "update shell_main set end_time=?, success_num=?, fail_num=?, test_rate=?, success_rate=? where main_id=?";
			stmt = conn.prepareStatement(sql);
			stmt.setTimestamp(1, d);
			stmt.setInt(2, succ_num);
			stmt.setInt(3, fail_num);
			stmt.setFloat(4, execute_rate);
			stmt.setFloat(5, success_rate);
			stmt.setInt(6, task_id);
			stmt.executeUpdate();
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			close(stmt);
		}

		try {
			sql = "update shell_main set elapse_time=(end_time-start_time) where main_id=?";
			stmt = conn.prepareStatement(sql);
			stmt.setInt(1, task_id);
			stmt.executeUpdate();
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			close(stmt);
			close(conn);
		}
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
		Connection conn = null;
		int resultContSize = 0;

		PreparedStatement stmt = null;
		String sql;

		long caseStopTime = System.currentTimeMillis();
		Timestamp d = new Timestamp(caseStopTime);
        if(resultCont!=null){
        	resultContSize = resultCont.length();
        	if(resultContSize > MAX_CONTENT_SIZE){
        		String resultContPrefix = resultCont.substring(0, MAX_CONTENT_SIZE/2);
        		String resultContSuffer = resultCont.substring(resultContSize - MAX_CONTENT_SIZE/2, resultContSize);
        		resultCont = resultContPrefix + System.getProperty("line.separator") + "********** THE CONTENT LENGTH IS" + resultContSize + "(TRIMMED) **********" + System.getProperty("line.separator") + resultContSuffer;
        	}
        }
        
		sql = "insert into shell_items(main_id, case_file, env_node, elapse_time, test_result, is_timeout, has_core, result_cont, end_time, is_skipped, retry_count) values(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

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

		if (skippedType.equals(Constants.SKIP_TYPE_NO)) {
			refreshShellMain(false);
			if (hasCore) {
				notifyUpdateMain();
			}
		}
	}

	@Override
	public void onTestCaseStopEventForRetry(String testCase, boolean flag, long elapseTime, String resultCont, String envIdentify, boolean isTimeOut, boolean hasCore, String skippedType, int retryCount) {
		Connection conn = null;
		int resultContSize = 0;

		PreparedStatement stmt = null;
		String sql;

		long caseStopTime = System.currentTimeMillis();
		Timestamp d = new Timestamp(caseStopTime);
		if(resultCont!=null){
        	resultContSize = resultCont.length();
        	if(resultContSize > MAX_CONTENT_SIZE){
        		String resultContPrefix = resultCont.substring(0, MAX_CONTENT_SIZE/2);
        		String resultContSuffer = resultCont.substring(resultContSize - MAX_CONTENT_SIZE/2, resultContSize);
        		resultCont = resultContPrefix + System.getProperty("line.separator") + "********** THE CONTENT LENGTH IS" + resultContSize + "(TRIMMED) **********" + System.getProperty("line.separator") + resultContSuffer;
        	}
        }

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

		if (skippedType.equals(Constants.SKIP_TYPE_NO)) {
			refreshShellMain(false);
			if (hasCore) {
				notifyUpdateMain();
			}
		}

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
		System.out.println("SVN UPDATE START: " + envIdentify);

	}

	@Override
	public void onSvnUpdateStop(String envIdentify) {
		System.out.println("SVN UPDATE STOP: " + envIdentify);

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
