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