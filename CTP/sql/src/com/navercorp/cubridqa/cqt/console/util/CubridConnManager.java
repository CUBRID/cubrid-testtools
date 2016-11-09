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
/**
 * Class Description
 * <pre>
 * Filename        : ConnectionManager.java 
 * Date			   : Aug 20, 2008
 * Creator         : Anson Cheung
 * Revised Description
 * ----------------------------------------------
 * ver	revised date	reviser	revised contents
 * 0.1    Aug 20, 2008        Anson            create
 * ----------------------------------------------
 *</pre>
 */
package com.navercorp.cubridqa.cqt.console.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.dbcp.BasicDataSource;

import com.navercorp.cubridqa.cqt.console.bean.DefTestDB;
import com.navercorp.cubridqa.cqt.console.bean.Test;

public class CubridConnManager {

	final static String driver = "cubrid.jdbc.driver.CUBRIDDriver";
	private static Map<String, Object> DataBaseDefine = new HashMap<String, Object>();
	private static ConfigureUtil configureUtil;

	public static void setConfigureUtil(ConfigureUtil configureUtil) {
		CubridConnManager.configureUtil = configureUtil;
	}

	private static Map<String, Map<String, Map<String, List<CubridConnection>>>> GroupMap = new HashMap<String, Map<String, Map<String, List<CubridConnection>>>>();

	/**
	 * get the detail info of connection group.
	 */
	public static String info() {
		StringBuilder sb = new StringBuilder();
		sb.append("Connection Group INFO :");
		sb.append("\r\n");
		if (GroupMap.isEmpty()) {
			sb.append("Connection Group MAP is NULL ");
			sb.append("\r\n");
		} else {
			int id = 0;
			for (Map<String, Map<String, List<CubridConnection>>> dbmsConnectionMap : GroupMap.values()) {
				id++;
				sb.append("Connection Group [" + id + "]:" + dbmsConnectionMap.size());
				sb.append("\r\n");
				Iterator<Map<String, List<CubridConnection>>> DbmsConnectionIterator = dbmsConnectionMap.values().iterator();
				while (DbmsConnectionIterator.hasNext()) {
					Map<String, List<CubridConnection>> dbmsConnList = DbmsConnectionIterator.next();
					for (List<CubridConnection> l : dbmsConnList.values()) {
						for (CubridConnection conn : l) {
							sb.append("\tCubridConnection :\t" + conn.getConnName() + "[" + conn.getId() + "]    " + (conn.isAutocreate() ? "[AUTO]" : "[MAUL]"));
							sb.append("\r\n");
						}
					}
				}
			}
		}
		return sb.toString();
	}

	/**
	 * destroy all the connection .
	 */
	public static void destroyed() {
		if (GroupMap.isEmpty())
			GroupMap = null;
		else {
			for (Map<String, Map<String, List<CubridConnection>>> dbmsConnectionMap : GroupMap.values()) {
				Iterator<Map<String, List<CubridConnection>>> DbmsConnectionIterator = dbmsConnectionMap.values().iterator();
				while (DbmsConnectionIterator.hasNext()) {
					Map<String, List<CubridConnection>> dbmsConnList = DbmsConnectionIterator.next();
					for (List<CubridConnection> l : dbmsConnList.values()) {
						for (CubridConnection conn : l) {
							conn.destroyed();
						}
					}
				}
				GroupMap = null;
			}
		}
	}

	/**
	 * get the connection through the name .
	 * 
	 * @param connGroup
	 * @param connName
	 * @param seq
	 * @return
	 */
	public static synchronized CubridConnection getDbmsConnection(String connGroup, String connName, int seq, int Type) {
		if (connName == null || connName.trim().length() == 0) {
			connName = "default";
		}

		List<CubridConnection> ConnectionList = getAvlibleDbmsConnectionList(connGroup, connName);// .get(connName);
		for (CubridConnection conn : ConnectionList) {
			if (conn.getId() == seq)
				return conn;
		}

		CubridConnection NewDbmsConnection = new CubridConnection(connName, connGroup, GetDataBaseDefine(connGroup, Type), seq, false);
		ConnectionList.add(NewDbmsConnection);
		return NewDbmsConnection;
	}

	/**
	 * get the connection through connection name .
	 * 
	 * @deprecated
	 * @param connGroup
	 * @param connName
	 * @param seq
	 * @return
	 */
	public static synchronized CubridConnection getAvlibleDbmsConnection(String connGroup, String connName, int Type) {
		int NameSize = 0;
		ArrayList NameList = new ArrayList();
		if (connName == null || connName.trim().length() == 0) {
			connName = "default";
		}
		List<CubridConnection> ConnectionList = getAvlibleDbmsConnectionList(connGroup, connName);
		for (CubridConnection conn : ConnectionList) {
			if (conn.isAvlible())
				return conn;
			NameSize++;
			NameList.add(conn.getId());
		}
		int seq;
		for (seq = 0; seq < NameSize; seq++) {
			if (NameList.contains(seq))
				break;
		}

		CubridConnection NewDbmsConnection = new CubridConnection(connName, connGroup, GetDataBaseDefine(connGroup, Type), seq, true);
		ConnectionList.add(NewDbmsConnection);
		return NewDbmsConnection;
	}

	/**
	 * get the available connection .
	 * 
	 * @param connGroup
	 * @return
	 */
	private static List<CubridConnection> getAvlibleDbmsConnectionList(String connGroup, String connName) {
		Map<String, Map<String, List<CubridConnection>>> ConnMap = new HashMap<String, Map<String, List<CubridConnection>>>();
		Map<String, List<CubridConnection>> ConnListMap = new HashMap<String, List<CubridConnection>>();
		List<CubridConnection> ConnList = new ArrayList<CubridConnection>();
		if (!GroupMap.containsKey(connGroup)) {
			GroupMap.put(connGroup, ConnMap);
		}
		ConnMap = GroupMap.get(connGroup);
		if (!ConnMap.containsKey(connGroup)) {
			ConnMap.put(connGroup, ConnListMap);
		}
		ConnListMap = ConnMap.get(connGroup);
		if (!ConnListMap.containsKey(connName)) {
			ConnListMap.put(connName, ConnList);
		}
		return ConnListMap.get(connName);
	}

	/**
	 * 
	 * @param connGroup
	 * @return
	 */
	private static Object GetDataBaseDefine(String connGroup, int Type) {
		if (DataBaseDefine.isEmpty() || DataBaseDefine.get(connGroup) == null) {
			if (configureUtil == null) {
				System.out.println("Error:Please set the configureUtil Object first!!!");
				return null;
			}
			String dbPath = "";
			if (Type == Test.TYPE_PERFORMANCE) {
				dbPath = configureUtil.getRepositoryPath() + "/configuration/Performance_Db";
			} else {
				dbPath = configureUtil.getRepositoryPath() + "/configuration/Function_Db";
			}
			if (connGroup == null || connGroup.trim().equals("")) {
				return null;
			}
			if (!DataBaseDefine.containsKey(connGroup)) {

				String dbConfFile = dbPath + "/" + connGroup + ".xml";

				System.out.println(" ===== dbConfFile " + dbConfFile);
				DefTestDB dbConf = (DefTestDB) XstreamHelper.fromXml(dbConfFile);
				if ("dbcp".equalsIgnoreCase(dbConf.getConnectionType())) {
					String url = dbConf.getDburl();
					String user = dbConf.getDbuser();
					String password = dbConf.getDbpassword();

					BasicDataSource dataSource = new MyDataSource();
					dataSource.setDriverClassName(driver);
					dataSource.setUsername(user);
					dataSource.setPassword(password);

					// modified by ZhangQiang at 2010-05-24
					if (url.indexOf("charset=") == -1) {
						dataSource.setUrl(url + "?charset=" + dbConf.getCharSet());
					}

					System.out.println(" ===== dbConf.getCharSet() " + dbConf.getCharSet());

					dataSource.setDefaultAutoCommit(false);
					DataBaseDefine.put(connGroup, dataSource);
				} else {
					DataBaseDefine.put(connGroup, dbConf);
				}
			}
		}
		return DataBaseDefine.get(connGroup);
	}

	public static void setGroupMap(Map<String, Map<String, Map<String, List<CubridConnection>>>> groupMap) {
		GroupMap = groupMap;
	}

	public static void setDataBaseDefine(Map<String, Object> dataBaseDefine) {
		DataBaseDefine = dataBaseDefine;
	}

}