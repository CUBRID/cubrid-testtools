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

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Properties;

public class CoreBO {

	Properties props;

	public CoreBO() throws IOException, ClassNotFoundException {
		String coreHome = System.getenv("COMMON_HOME");
		props = CommonUtil.getProperties(CommonUtil.concatFile(((coreHome == null) ? "" : coreHome + "/") + "conf", "db.conf"));

		Class.forName(props.getProperty("jdbc.driver", "cubrid.jdbc.driver.CUBRIDDriver"));
	}

	public void insertCoreIssue(IssueBean bean) throws SQLException {
		String sql = "insert into core_issue ( process_name , detail_stack, digest_stack, issue_key, issue_status, create_time, update_time) values (?, ?, ?, ?, ?, ?, ?)";
		Connection conn = null;
		PreparedStatement pstmt = null;
		try {
			conn = getConnection();
			pstmt = conn.prepareStatement(sql);
			pstmt.setString(1, bean.getProcessName());
			pstmt.setString(2, bean.getDetailStack());
			pstmt.setString(3, bean.getDigestStack());
			pstmt.setString(4, bean.getIssueKey());
			pstmt.setString(5, bean.getIssueStatus());
			pstmt.setTimestamp(6, bean.getCreateTime());
			pstmt.setTimestamp(7, bean.getUpdateTime());
			pstmt.executeUpdate();
		} finally {
			close(pstmt);
			close(conn);
		}
	}

	public ArrayList<IssueBean> selectIssueByCore(String digestStack) throws SQLException {
		String sql = "select * from core_issue where digest_stack = ? order by id desc";
		Connection conn = null;
		PreparedStatement pstmt = null;
		ResultSet rs = null;
		ArrayList<IssueBean> beanList = new ArrayList<IssueBean>();
		IssueBean bean = null;

		try {
			conn = getConnection();
			pstmt = conn.prepareStatement(sql);
			pstmt.setString(1, digestStack);
			rs = pstmt.executeQuery();
			while (rs.next()) {
				bean = new IssueBean();
				bean.setId(rs.getInt("id"));
				bean.setProcessName(rs.getString("process_name"));
				bean.setDetailStack(rs.getString("detail_stack"));
				bean.setDigestStack(rs.getString("digest_stack"));
				bean.setIssueKey(rs.getString("issue_key"));
				bean.setIssueStatus(rs.getString("issue_status"));
				bean.setCreateTime(rs.getTimestamp("create_time"));
				bean.setUpdateTime(rs.getTimestamp("update_time"));
				beanList.add(bean);
			}
		} finally {
			close(rs);
			close(pstmt);
			close(conn);
		}
		return beanList;
	}

	public ArrayList<IssueBean> selectAllRows() throws SQLException {
		String sql = "select * from core_issue order by id asc";
		Connection conn = null;
		PreparedStatement pstmt = null;
		ResultSet rs = null;
		ArrayList<IssueBean> beanList = new ArrayList<IssueBean>();
		IssueBean bean = null;

		try {
			conn = getConnection();
			pstmt = conn.prepareStatement(sql);
			rs = pstmt.executeQuery();
			while (rs.next()) {
				bean = new IssueBean();
				bean.setId(rs.getInt("id"));
				bean.setProcessName(rs.getString("process_name"));
				bean.setDetailStack(rs.getString("detail_stack"));
				bean.setDigestStack(rs.getString("digest_stack"));
				bean.setIssueKey(rs.getString("issue_key"));
				bean.setIssueStatus(rs.getString("issue_status"));
				bean.setCreateTime(rs.getTimestamp("create_time"));
				bean.setUpdateTime(rs.getTimestamp("update_time"));
				beanList.add(bean);
			}
		} finally {
			close(rs);
			close(pstmt);
			close(conn);
		}
		return beanList;
	}

	public int changeIssueStatus(String issueKey, String status) throws SQLException {
		String sql = "update core_issue set issue_status=?, update_time=now() where issue_key=?";
		Connection conn = null;
		PreparedStatement pstmt = null;
		int cnt = 0;
		try {
			conn = getConnection();
			pstmt = conn.prepareStatement(sql);
			pstmt.setString(1, status);
			pstmt.setString(2, issueKey);
			cnt = pstmt.executeUpdate();
		} finally {
			close(pstmt);
			close(conn);
		}
		return cnt;
	}

	public int updateStack(String id, String detailStack, String digestStack) throws SQLException {
		String sql = "update core_issue set detail_stack=?, digest_stack=?, update_time=now() where id=?";
		Connection conn = null;
		PreparedStatement pstmt = null;
		int cnt = 0;
		try {
			conn = getConnection();
			pstmt = conn.prepareStatement(sql);
			pstmt.setString(1, detailStack);
			pstmt.setString(2, digestStack);
			pstmt.setString(3, id);
			cnt = pstmt.executeUpdate();
		} finally {
			close(pstmt);
			close(conn);
		}
		return cnt;
	}

	public ArrayList<IssueBean> showIssueStatus(String issueKey) throws SQLException {
		String sql = "select * from core_issue where issue_key=? order by update_time desc";
		Connection conn = null;
		PreparedStatement pstmt = null;
		ResultSet rs = null;
		ArrayList<IssueBean> list = new ArrayList<IssueBean>();
		IssueBean bean;

		try {
			conn = getConnection();
			pstmt = conn.prepareStatement(sql);
			pstmt.setString(1, issueKey);
			rs = pstmt.executeQuery();
			while (rs.next()) {
				bean = new IssueBean();
				bean.setId(rs.getInt("id"));
				bean.setProcessName(rs.getString("process_name"));
				bean.setDetailStack(rs.getString("detail_stack"));
				bean.setDigestStack(rs.getString("digest_stack"));
				bean.setIssueKey(rs.getString("issue_key"));
				bean.setIssueStatus(rs.getString("issue_status"));
				bean.setCreateTime(rs.getTimestamp("create_time"));
				bean.setUpdateTime(rs.getTimestamp("update_time"));
				list.add(bean);
			}
		} finally {
			close(rs);
			close(pstmt);
			close(conn);
		}

		return list;
	}

	private Connection getConnection() throws SQLException {
		String jdbcUrl = props.getProperty("url");
		String jdbcUser = props.getProperty("user");
		String jdbcPwd = props.getProperty("pwd");
		Connection conn = DriverManager.getConnection(jdbcUrl, jdbcUser, jdbcPwd);
		return conn;
	}

	private void close(Statement stmt) {
		if (stmt != null) {
			try {
				stmt.close();
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
	}

	private void close(Connection conn) {
		if (conn != null) {
			try {
				conn.close();
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
	}

	private void close(ResultSet rs) {
		if (rs != null) {
			try {
				rs.close();
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
	}
}
