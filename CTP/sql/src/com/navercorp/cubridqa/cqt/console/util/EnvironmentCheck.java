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

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.UnknownHostException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Enumeration;

public class EnvironmentCheck {
	private static int flag = 0;

	public static int getFlag() {
		return flag;
	}

	public static void setFlag(int flag) {
		EnvironmentCheck.flag = flag;
	}

	public static synchronized void addFlag() {
		EnvironmentCheck.flag++;
	}

	/**
	 * check file is exist
	 * 
	 * @param filename
	 * @return
	 */
	public static boolean fileExist(String filename) {
		File file = new File(filename);
		return file.exists();
	}

	/**
	 * check ip port can use
	 * 
	 * @param ip
	 * @param port
	 * @return
	 */
	public static boolean isIpCanConnected(String ip, String port) {
		try {
			Socket s = new Socket(ip, Integer.parseInt(port));
			return true;
		} catch (UnknownHostException e) {
			System.out.println("Cannot connect " + ip + ":" + port + ", or the port is occupied. ");
		} catch (IOException e) {
			System.out.println("Cannot connect " + ip + ":" + port + ", or the port is occupied.");
		}
		return false;
	}

	/**
	 * check ip can connect
	 * 
	 * @param ip
	 * @return
	 */
	public static boolean isIpCanConnected(String ip) {

		return true;
	}

	/**
	 * check db can connect
	 * 
	 * @param ip
	 * @param port
	 * @param dbname
	 * @param userid
	 * @param pwd
	 * @return
	 */
	public static boolean isDBCanConnect(String ip, String port, String dbname, String userid, String pwd) {
		Connection conn = null;
		try {
			conn = CubridDBCenter.getConnection(ip, port, dbname, userid, pwd);
		} catch (SQLException e1) {
			e1.printStackTrace();
		}
		if (conn == null)
			return false;
		else
			try {
				conn.close();
			} catch (SQLException e) {
				e.printStackTrace();
			}
		return true;
	}

	/**
	 * check the default port can connected
	 * 
	 * @param ip
	 * @return
	 */
	public static String isDBDefaultPortCanConnect(String ip) {
		if (EnvironmentCheck.isIpCanConnected(ip, "30000")) {
			return "30000";
		}
		if (EnvironmentCheck.isIpCanConnected(ip, "33000")) {
			return "33000";
		}
		return null;
	}

	/**
	 * check the ip is local
	 * 
	 * @param ip
	 * @return
	 */
	public static boolean isLocalHost(String ip) {
		if (ip.equals(getLocalAddress()))
			return true;
		else
			return false;
	}

	/**
	 * 
	 * @param ip
	 * @param port
	 * @param user
	 * @param dbname
	 * @param password
	 * @param flag
	 * @throws Exception
	 */
	public static void setFinishedFlag(String ip, String port, String user, String dbname, String password, int flag) throws Exception {
		String sql = "update flag set flag = " + flag;
		Connection conn = CubridDBCenter.getConnection(ip, port, dbname, user, password);

		System.out.println("====:::===" + ip);

		Statement stmt = null;
		try {
			conn.setAutoCommit(false);
			stmt = conn.createStatement();
			stmt.executeUpdate(sql);
			conn.commit();

		} catch (Exception ex) {
			ex.printStackTrace();
			conn.rollback();
			throw ex;
		} finally {
			stmt.close();
			conn.close();
		}
	}

	/**
	 * 
	 * @param ip
	 * @param port
	 * @param user
	 * @param dbname
	 * @param password
	 * @return
	 * @throws Exception
	 */
	public static int getFinishedFlag(String ip, String port, String user, String dbname, String password) throws Exception {
		String sql = "select flag from flag";
		Connection conn = CubridDBCenter.getConnection(ip, port, dbname, user, password);
		PreparedStatement stmt = null;
		ResultSet rs = null;
		try {
			stmt = conn.prepareStatement(sql);
			rs = stmt.executeQuery();
			if (rs == null)
				throw new Exception("Get Flag error");
			rs.next();
			int flag = rs.getInt("flag");

			return flag;

		} catch (Exception e) {
			e.printStackTrace();
			throw e;
		} finally {
			if (rs != null)
				rs.close();
			if (stmt != null)
				stmt.close();
			if (conn != null)
				conn.close();
		}

	}

	/**
	 * get local ip
	 * 
	 * @return
	 */
	private static String getLocalAddress() {

		Enumeration interfaces;
		try {
			interfaces = NetworkInterface.getNetworkInterfaces();
			while (interfaces.hasMoreElements()) {
				NetworkInterface interfaceN = (NetworkInterface) interfaces.nextElement();
				Enumeration ienum = interfaceN.getInetAddresses();
				while (ienum.hasMoreElements()) {
					InetAddress ia = (InetAddress) ienum.nextElement();
					if (ia.getHostAddress().toString().startsWith("127")) {
						continue;
					} else {

						return ia.getLocalHost().getHostAddress();
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

}