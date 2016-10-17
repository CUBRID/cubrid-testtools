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
package com.navercorp.cubridqa.common;

import java.io.File;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Properties;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.PosixParser;

public class CommitConfigFileIntoDB {

	java.sql.Connection dbConn;
	Properties props;

	public CommitConfigFileIntoDB() {
		props = Constants.COMMON_DAILYQA_CONF;
		String driver = props.getProperty("qahome_db_driver");
		try {
			Class.forName(driver);
		} catch (Exception e) {
			System.out.println("[ERROR] fail to load JDBC Driver, please refer to qahome_db_driver parameter in conf/dailyqa.conf file.");
		}
	}

	public static void main(String[] args) throws IOException {
		Options options = new Options();
		options.addOption("id", true, "msg id in source main table");
		options.addOption("host", true, "local host ip, user don't need give it, it will be parsed from local");
		options.addOption("user", true, "local user, user don't need give it, it will be parsed from local");
		options.addOption("table", true, "source main table");
		options.addOption("title", true, "log title");
		options.addOption("configfile", true, "file content");
		options.addOption("content", true, "file content of configuration file");
		options.addOption("help", false, "List help");

		CommandLineParser parser = null;
		CommandLine cmd = null;

		try {
			parser = new PosixParser();
			cmd = parser.parse(options, args);
		} catch (Exception e) {
			showHelp(e.getMessage(), options);
			return;
		}

		if (args.length == 0 || cmd.hasOption("help")) {
			showHelp(null, options);
			return;
		}

		if (!cmd.hasOption("host") || !cmd.hasOption("user") || !cmd.hasOption("title") || !cmd.hasOption("id") || !cmd.hasOption("table")) {
			showHelp("Please input remote <host>, <user>, <table>, <id>, <title>.", options);
			return;
		}

		if (!cmd.hasOption("configfile") && !cmd.hasOption("content")) {
			showHelp("Please give configuration files or content of configuration file.", options);
			return;
		}

		String localHost = cmd.getOptionValue("host");
		String localUser = cmd.getOptionValue("user");
		String sourceMainTable = cmd.getOptionValue("table");
		String title = cmd.getOptionValue("title");
		String contentMessage = cmd.getOptionValue("content");
		String sourceMainId = cmd.getOptionValue("id");
		String content = (contentMessage == null || contentMessage.length() == 0) ? parseFileIntoString(cmd.getOptionValue("configfile")) : contentMessage;
		CommitConfigFileIntoDB initJdbc = new CommitConfigFileIntoDB();
		initJdbc.insertConfigurationToDB(sourceMainId, sourceMainTable, title, content, localHost, localUser);

	}

	private void insertConfigurationToDB(String sourceMainId, String sourceMainTable, String title, String content, String host, String user) {
		String sql = "insert into general_test_log(src_type, src_id, host_ip, host_user, log_title, log_content, create_time) values (?, ?, ?, ?, ?, ?, now())";
		PreparedStatement stmt = null;
		try {
			stmt = getConnection().prepareStatement(sql);
			stmt.setString(1, sourceMainTable);
			stmt.setString(2, sourceMainId);
			stmt.setString(3, host);
			stmt.setString(4, user);
			stmt.setString(5, title);
			stmt.setString(6, content);
			stmt.executeUpdate();
		} catch (SQLException e) {
			e.printStackTrace();
		} finally {
			if (stmt != null) {
				try {
					stmt.close();
				} catch (SQLException e) {
					e.printStackTrace();
				}
			}

			close();
		}

	}

	private java.sql.Connection createConnection() throws SQLException, ClassNotFoundException {
		String url = props.getProperty("qahome_db_url");
		String user = props.getProperty("qahome_db_user");
		String pwd = props.getProperty("qahome_db_pwd");
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

	public static String parseFileIntoString(String filename) throws IOException {
		File file = new File(filename);
		if (!file.exists()) {
			return null;
		}
		StringBuffer result = new StringBuffer();
		FileInputStream fis = new FileInputStream(file);
		InputStreamReader reader = new InputStreamReader(fis, "UTF-8");

		LineNumberReader lineReader = new LineNumberReader(reader);
		String line;

		while ((line = lineReader.readLine()) != null) {
			result.append(line.trim()).append(Constants.LINE_SEPARATOR);
		}
		lineReader.close();
		reader.close();
		fis.close();
		return result.toString();
	}

	private static void showHelp(String error, Options options) {
		if (error != null)
			System.out.println("Error: " + error);
		HelpFormatter formatter = new HelpFormatter();
		formatter.printHelp("commit_config_file: upload configuration files to general_test_log", options);
		System.out.println();
	}
}
