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

package common;

import java.io.File;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Document;
import org.xml.sax.SAXException;

/**
 * 
 * for commonforjdbc.jar
 *
 */
public class TestDbInfo {
	public String dbname;
	public String ip;
	public String port;
	public String user;
	public String password;
	public String charset;

	public TestDbInfo() {
		dbname = "testdb";
		ip = "localhost";
		port = "33000";
		user = "dba";
		password = "";
		charset = "UTF-8";
	}

	public static void main(String[] args) {
		TestDbInfo testDbInfo = TestDbInfo.call("shell_config.xml");
		System.out.println("ip="  + testDbInfo.ip);
		System.out.println("broker port="  + testDbInfo.port);
	}
	
	public static TestDbInfo call(){
		return call("shell_config.xml");
	}

	public static TestDbInfo call(String shellConfig) {
		TestDbInfo testDbInfo = new TestDbInfo();
		try {
			String cqtPath = System.getenv("REAL_INIT_PATH");
			String configName = cqtPath + File.separator + shellConfig;
			File file = new File(configName);
			DocumentBuilder builder = DocumentBuilderFactory.newInstance()
					.newDocumentBuilder();
			Document document = builder.parse(file);
			XPath xpath = XPathFactory.newInstance().newXPath();
			testDbInfo.ip = xpath.evaluate("ShellConfig/ip", document);
			testDbInfo.port = xpath.evaluate("ShellConfig/port", document);
		} catch (XPathExpressionException e) {
			e.printStackTrace();
		} catch (ParserConfigurationException e) {
			e.printStackTrace();
		} catch (SAXException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

		return testDbInfo;
	}
	
	public Connection getConnection() {
		Connection conn = null;
		try {
			Class.forName("cubrid.jdbc.driver.CUBRIDDriver");
			conn = DriverManager.getConnection("jdbc:cubrid:" + ip
					+ ":" + port + ":" + dbname + ":::"
					+ "?charset=" + charset, user,
					password);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return conn;
	}
	
	public String getUrl() {   
        return "jdbc:cubrid:" + ip + ":" + port + ":"
				+ dbname + ":::" + "?charset=" + charset;
	}	

	public void closeConnection(Connection conn) {
		if (conn == null) {
			return;
		}

		try {
			conn.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	public void closeStatment(Statement stmt) {
		if (stmt == null) {
			return;
		}

		try {
			stmt.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	public void closeResultSet(ResultSet rst) {
		if (rst == null) {
			return;
		}

		try {
			rst.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
}