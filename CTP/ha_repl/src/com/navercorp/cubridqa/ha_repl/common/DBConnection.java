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
package com.navercorp.cubridqa.ha_repl.common;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import javax.sql.DataSource;

import org.apache.commons.dbcp.BasicDataSource;

import com.navercorp.cubridqa.ha_repl.Context;

public class DBConnection {

	Context context;
	DataSource ds;

	int task_id = 0;
	int total_scenario;

	public DBConnection() {
	}

	public void shutDownDataSource() {
		shutdownDataSource();
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

	protected void println(String... conts) {
		for (String cont : conts) {
			System.out.println(cont);
		}
	}

	public String getDBUrl(String host, String db, String port) {
		String url = "";
		if ("".equals(host) || "".equals(db) || "".equals(port)) {
			System.out.println("Generate DB url error!");

		} else {
			url = "jdbc:cubrid:" + host + ":" + port + ":" + db + ":::";
		}
		return url;

	}

	public Connection getDbConnection(String host, String db, String port, String dbuser, String passwd) {
		Connection conn = null;
		String url = getDBUrl(host, db, port);
		try {
			Class.forName("cubrid.jdbc.driver.CUBRIDDriver").newInstance();
			conn = DriverManager.getConnection(url, dbuser, passwd);
		} catch (InstantiationException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} catch (IllegalAccessException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} catch (ClassNotFoundException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} catch (SQLException e) {
			e.printStackTrace();
		}

		// BasicDataSource ds = new BasicDataSource();
		// ds.setDriverClassName("cubrid.jdbc.driver.CUBRIDDriver");
		// ds.setUsername(dbuser);
		// ds.setPassword(passwd);
		// ds.setUrl(url);

		// try {
		// conn = createConnection();
		//
		// } catch (SQLException e) {
		// // TODO Auto-generated catch block
		// e.printStackTrace();
		// } catch (InterruptedException e) {
		// // TODO Auto-generated catch block
		// e.printStackTrace();
		// }

		return conn;
	}

	private DataSource setupDataSource() {
		String url = context.getDBUrl();
		String user = context.getDbUser();
		String pwd = context.getDbPwd();

		BasicDataSource ds = new BasicDataSource();
		ds.setDriverClassName("cubrid.jdbc.driver.CUBRIDDriver");
		ds.setUsername(user);
		ds.setPassword(pwd);
		ds.setUrl(url);
		// ds.setValidationQuery("select 1 from db_root");
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

	private Connection createConnection() throws SQLException, InterruptedException {
		Connection conn = null;
		while (true) {
			conn = ds.getConnection();
			if (conn != null)
				break;
			Thread.sleep(1000 * 3);
		}
		return conn;
	}
}
