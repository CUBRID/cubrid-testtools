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
package com.navercorp.cubridqa.scheduler.consumer;

import java.io.IOException;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

public class ConsumerTimer {

	Properties props;
	java.sql.Connection dbConn;

	public ConsumerTimer() throws IOException {
		Configure conf = new Configure();
		this.props = conf.props;
	}

	/**
	 * @param args
	 * @throws IOException
	 * @throws SQLException
	 */
	public static void main(String[] args) throws IOException {

		if (args == null || args.length < 2) {
			showHelp();
			return;
		}

		String msgId = args[0];
		String typeOfTimer = args[1];

		ConsumerTimer cTimer = new ConsumerTimer();
		cTimer.updateTimeToDatabase(msgId, typeOfTimer);

	}

	private void updateTimeToDatabase(String msgId, String typeOfTimer) {
		String sql = "";
		if ("interrupted".equalsIgnoreCase(typeOfTimer.trim())) {
			sql = "update msg_sended set is_interrupted = 'Y', consume_flag = 'FINISHED', consume_stop_time = CURRENT_TIMESTAMP()  where msg_id ='"
					+ msgId.trim() + "';";
		} else {
			if("start".equalsIgnoreCase(typeOfTimer.trim())){
				sql = "update msg_sended set consume_start_time = CURRENT_TIMESTAMP() , consume_flag = 'IN PROGRESS' where msg_id ='"
						+ msgId.trim() + "';";
			}else if ("stop".equalsIgnoreCase(typeOfTimer.trim()))
			sql = "update msg_sended set consume_stop_time = CURRENT_TIMESTAMP() , consume_flag = 'FINISHED' where msg_id ='"
					+ msgId.trim() + "';";
		}
		

		Statement stat = null;
		try {
			stat = getConnection().createStatement();
			stat.execute(sql);
		} catch (SQLException e) {
			e.printStackTrace();
		} finally {
			if (stat != null) {
				try {
					stat.close();
				} catch (SQLException e) {
					e.printStackTrace();
				}
			}

			close();

		}

	}

	private java.sql.Connection createConnection() throws SQLException,
			ClassNotFoundException {
		String url = props.getProperty("dailydb.url");
		String user = props.getProperty("dailydb.user");
		String pwd = props.getProperty("dailydb.pwd");
		return DriverManager.getConnection(url, user, pwd);
	}

	private java.sql.Connection getConnection() {
		if (dbConn != null)
			return dbConn;
		try {
			dbConn = createConnection();
		} catch (Exception e) {
			e.printStackTrace();
			dbConn = null;
		}

		return dbConn;
	}

	private void close() {
		if (this.dbConn != null) {
			try {
				this.dbConn.close();
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
	}

	private static void showHelp() {
		System.out
				.println("Usage: java com.navercorp.cubridqa.scheduler.consumer.ConsumerTimer <start> <stop> <interrupted> MSGID");
		System.out.println();
	}

}
