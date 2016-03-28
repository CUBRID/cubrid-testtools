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
package com.navercorp.cubridqa.cqt.console.util;

import java.sql.Connection;
import java.sql.SQLException;

import com.navercorp.cubridqa.cqt.console.util.MyDriverManager;

public class CubridDBCenter {
	private String ip;
	private String port;
	private String dbname;
	private String userid;
	private String userpwd;
	private Connection connection;

	/**
	 * @param ip
	 * @param port
	 * @param dbname
	 * @param userid
	 * @param userpwd
	 */
	public CubridDBCenter(String ip, String port, String dbname, String userid,
			String userpwd) {
		this.ip = ip;
		this.port = port;
		this.dbname = dbname;
		this.userid = userid;
		this.userpwd = userpwd;
	}


	/**
	 * 
	 * @return
	 */
	public Connection getConnection() {

		try {

			this.connection = CubridDBCenter.getConnection(ip, port, dbname,
					userid, userpwd);

			return this.connection;

		} catch (SQLException e) {

			e.printStackTrace();
		}
		return null;
	}

	/**
	 * 
	 * @param ip
	 * @param port
	 * @param dbname
	 * @param userid
	 * @param userpwd
	 * @return
	 * @throws SQLException
	 */
	public static Connection getConnection(String ip, String port,
			String dbname, String userid, String userpwd) throws SQLException {

		String url = "jdbc:cubrid:" + ip + ":" + port + ":" + dbname + ":::";
		Connection conn = MyDriverManager.giveConnection(
				"cubrid.jdbc.driver.CUBRIDDriver", url, userid, userpwd);

		return conn;

	}

	/**
	 * 
	 * @return
	 */
	public static String getDatabaseVersion() {
		return MyDriverManager.getDatabaseVersion();
	}

	/**
	 * 
	 */
	public void closeConnect() {
		try {
			if (this.connection != null) {
				this.connection.close();
			}
		} catch (SQLException e) {

			e.printStackTrace();
		}
	}

	/**
	 * 
	 */
	public void commit() {
		try {
			this.connection.commit();
		} catch (SQLException e) {

			e.printStackTrace();
		}
	}

	/**
	 * 
	 * @param conn
	 */
	public static void commit(Connection conn) {
		try {
			conn.commit();
		} catch (SQLException e) {

			e.printStackTrace();
		}
	}

	/**
	 * 
	 * @param conn
	 */
	public static void rollback(Connection conn) {
		try {

			conn.rollback();
		} catch (SQLException e) {

			e.printStackTrace();
		}
	}

}
