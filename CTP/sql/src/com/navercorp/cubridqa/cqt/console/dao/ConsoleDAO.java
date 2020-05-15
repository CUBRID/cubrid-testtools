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
package com.navercorp.cubridqa.cqt.console.dao;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.dbcp.BasicDataSource;

import com.navercorp.cubridqa.cqt.console.Executor;
import com.navercorp.cubridqa.cqt.console.bean.DefTestDB;
import com.navercorp.cubridqa.cqt.console.bean.Sql;
import com.navercorp.cubridqa.cqt.console.bean.SqlParam;
import com.navercorp.cubridqa.cqt.console.bean.SystemModel;
import com.navercorp.cubridqa.cqt.console.bean.Test;
import com.navercorp.cubridqa.cqt.console.util.CommandUtil;
import com.navercorp.cubridqa.cqt.console.util.ConfigureUtil;
import com.navercorp.cubridqa.cqt.console.util.CubridConnManager;
import com.navercorp.cubridqa.cqt.console.util.CubridConnection;
import com.navercorp.cubridqa.cqt.console.util.EnvGetter;
import com.navercorp.cubridqa.cqt.console.util.MyDataSource;
import com.navercorp.cubridqa.cqt.console.util.MyDriverManager;
import com.navercorp.cubridqa.cqt.console.util.StringUtil;
import com.navercorp.cubridqa.cqt.console.util.SystemUtil;
import com.navercorp.cubridqa.cqt.console.util.TestUtil;
import com.navercorp.cubridqa.cqt.console.util.XstreamHelper;

public class ConsoleDAO extends Executor {
	private final static String driver = "cubrid.jdbc.driver.CUBRIDDriver";

	private String url = null;

	private String user = null;

	private String password = null;

	private BasicDataSource dataSource = null;

	private String dbPath;

	private Map<String, Connection> connMap = new Hashtable<String, Connection>();

	private Map<String, DefTestDB> dbMap = new Hashtable<String, DefTestDB>();

	private Map<String, BasicDataSource> dataSourceMap = new Hashtable<String, BasicDataSource>();

	private Test test = null;

	private ConfigureUtil configureUtil;

	private boolean endFlag = false;

	private List<String> connList = new ArrayList<String>();

	private Map<String, Connection> connMapCopy = new Hashtable<String, Connection>();

	private Class oid;

	private URLClassLoader classLoader;

	private Class[] stringType, classType, intType;

	private Object[] objects;

	private String os = SystemUtil.getOS();

	/**
	 * 
	 * @ClassName: ConnThread
	 * @Description: Create connection.
	 * @date 2009-9-8
	 * @version V1.0
	 */
	private static class ConnThread implements Runnable {

		private ConsoleDAO dao;

		ConnThread(ConsoleDAO dao) {
			this.dao = dao;
		}

		public void run() {
			while (!dao.isEndFlag()) {
				List<String> connList = dao.getConnList();
				synchronized (connList) {
					if (connList.size() == 0) {
						try {
							connList.wait(1);
						} catch (InterruptedException e) {
						}
					}
					while (connList.size() > 0) {
						try {
							String key = (String) connList.get(0);
							if (key != null) {
								int position = key.indexOf("/");
								if (position != -1) {
									String db = key.substring(0, position);
									Connection conn = dao.createConnection(db);
									if (conn != null) {
										dao.connMapCopy.put(key, conn);
									}
								}
							}
						} catch (Exception e) {
						}
						connList.remove(0);
					}
					connList.notifyAll();
				}
			}
		}
	}

	/**
	 * init the database configuration
	 * 
	 * @throws ClassNotFoundException
	 */
	public ConsoleDAO(Test test, ConfigureUtil configureUtil) {
		this.test = test;
		this.configureUtil = configureUtil;
		CubridConnManager.setConfigureUtil(configureUtil);
		init();
	}

	/**
	 * 
	 * @Title: init
	 * @Description:Load JDBC driver then begin create connection.
	 * @param
	 * @return void
	 */
	public void init() {
		try {
			// SystemModel systemModel = (SystemModel) XstreamHelper
			// .fromXml(PropertiesUtil.getValue("local.path")
			// + "/configuration/System.xml");
			this.classLoader = MyDriverManager.getURLClassLoader();
			this.oid = classLoader.loadClass("cubrid.sql.CUBRIDOID");
			this.stringType = new Class[] { String.class };
			this.classType = new Class[] {};
			this.intType = new Class[] { int.class };
			this.objects = new Object[] {};
			classLoader.loadClass(driver);
			dbPath = EnvGetter.getenv("CTP_HOME") + File.separator + TestUtil.CONFIG_NAME + File.separator + "Function_Db";
			ConnThread connThread = new ConnThread(this);
			Thread thread = new Thread(connThread);
			thread.start();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * 
	 * @Title: getCubridConnection
	 * @Description:Get CUBRID connection which is being used.
	 * @param @param connGroup
	 * @param @param connName
	 * @param @param type
	 * @param @return
	 * @return CubridConnection
	 * @throws
	 */
	public CubridConnection getCubridConnection(String connGroup, String connName, int type) {

		return getCubridConnection(connGroup, connName, -1, type);
	}

	/**
	 * 
	 * @Title: getCubridConnection
	 * @Description:Get the specified CUBRID connection.
	 * @param @param connGroup
	 * @param @param connName
	 * @param @param seq
	 * @param @param type
	 * @param @return
	 * @return CubridConnection
	 * @throws
	 */
	public CubridConnection getCubridConnection(String connGroup, String connName, int seq, int type) {

		return CubridConnManager.getDbmsConnection(connGroup, connName, seq, type);

	}

	/**
	 * 
	 * @param db
	 */
	@Deprecated
	public Connection getConnection(String db, String id) {
		if (db == null || id == null) {
			return null;
		}

		test.setDbId(db);

		String key = db + "/" + id;
		Connection conn = (Connection) connMap.get(key);
		if (conn == null) {
			addConnToThread(key);
			try {
				conn = createConnection(db);
				connMap.put(key, conn);
				test.setConnId(id);
			} catch (Exception e) {
				e.printStackTrace();
			}
		} else if (!id.equals(test.getConnId())) {
			test.setConnId(id);
		}
		return conn;
	}

	/**
	 * 
	 * @Title: createConnection
	 * @Description:Create CUBRID connection.If need use connection pool then
	 *                     uses DBCP data source.
	 * @param @param db: database name
	 * @param @return
	 * @return Connection
	 * @throws
	 */
	private Connection createConnection(String db) {
		Connection conn = null;
		try {
			DefTestDB dbConf = dbMap.get(db);
			boolean usePool = "dbcp".equalsIgnoreCase(dbConf.getConnectionType());
			if (!usePool) {
				String url = dbConf.getDburl() + "?charset=" + dbConf.getCharSet();
				String user = dbConf.getDbuser();
				String password = dbConf.getDbpassword();
                               	if (Test.urlProperties != null && Test.urlProperties.length() != 0) {
				     url += "&" + Test.urlProperties;
		         	}
				conn = MyDriverManager.giveConnection(driver, url, user, password);
			} else {
				dataSource = dataSourceMap.get(db);
				if (dataSource != null) {
					conn = dataSource.getConnection();
				}
			}

			if (conn != null) {
				conn.setAutoCommit(false);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		if (conn == null) {
			System.out.println("!!!!!!!!!!Can not connect to DB:" + url);
		}
		return conn;
	}

	public void execute(Connection conn, Sql sql, boolean isPrintQueryPlan) {
		this.onMessage("sql.getType: " + sql.getType());
		if (sql.getType() == Sql.TYPE_CALL) {
			executeCall(conn, sql);
		} else if (sql.getType() == Sql.TYPE_PRE_STMT) {
			executePrepareStatement(conn, sql, isPrintQueryPlan);
		} else {
			executeStatement(conn, sql, isPrintQueryPlan);
		}
	}

	/**
	 * 
	 * @Title: addDb
	 * @Description:Init information for DBCP,include database URL,encoding,user
	 *                   name,password,driver name and driver file path.
	 * @param @param db
	 * @return void
	 * @throws
	 */
	public void addDb(String db) {
		if (db == null || db.trim().equals("")) {
			return;
		}
		if (dbMap.containsKey(db)) {
			return;
		}

		String dbConfFile = dbPath + "/" + db + ".xml";
		DefTestDB dbConf = (DefTestDB) XstreamHelper.fromXml(dbConfFile);
		if (dbConf.getScript() != null && !dbConf.getScript().equals("")) {
			if (!commandExecute(dbConf))
				return;
		}

		if (null != dbConf.getVersion() && "32bits".equalsIgnoreCase(dbConf.getVersion())) {
			TestUtil.OTHER_ANSWERS_32 = "answers32";
		} else {
			TestUtil.OTHER_ANSWERS_32 = "answers";
		}

		dbMap.put(db, dbConf);
		if ("dbcp".equalsIgnoreCase(dbConf.getConnectionType())) {
			if (dataSourceMap.containsKey(db)) {
				return;
			}

			url = dbConf.getDburl();
			user = dbConf.getDbuser();
			password = dbConf.getDbpassword();

			BasicDataSource dataSource = new MyDataSource();
			dataSource.setDriverClassName(driver);
			dataSource.setUsername(user);
			dataSource.setPassword(password);
			dataSource.setUrl(url + "?charset=" + dbConf.getCharSet());
			dataSource.setPoolPreparedStatements(true);
			dataSource.setDefaultAutoCommit(false);
			dataSourceMap.put(db, dataSource);
		}
	}

	private boolean commandExecute(DefTestDB dbConf) {
		boolean flag = true;
		try {
			String command = dbConf.getScript();
			String[] commands = new String[2];
			if (os.startsWith("window")) {
				commands[0] = "cd /d " + dbPath;
				commands[1] = command;
				CommandUtil.getExecuteFile(commands, this);
				CommandUtil.execute(commands, this);
			} else {
				System.out.println(command);
				commands[0] = "cd " + dbPath;
				commands[1] = command;
				CommandUtil.execute(commands, this);
			}
		} catch (Exception e) {
			flag = false;
		}
		return flag;
	}

	/**
	 * 
	 * @param db
	 */
	@Deprecated
	public boolean isDbOk() {
		boolean flag = true;
		Iterator<DefTestDB> iter = dbMap.values().iterator();
		while (iter.hasNext()) {
			DefTestDB dbConf = (DefTestDB) iter.next();
			url = dbConf.getDburl()  + "?charset=" + dbConf.getCharSet();
			user = dbConf.getDbuser();
			password = dbConf.getDbpassword();
			if (Test.urlProperties != null && Test.urlProperties.length() != 0) {
				url += "&" + Test.urlProperties;
			}

			Connection conn = null;
			conn = MyDriverManager.giveConnection(driver, url, user, password);
			if (conn == null) {
				flag = false;
				break;
			}
		}
		return flag;
	}

	@Deprecated
	public void commit() {
		Iterator<String> iter = connMap.keySet().iterator();
		String commitSql = "commit";
		while (iter.hasNext()) {
			String connId = (String) iter.next();
			Connection conn = (Connection) connMap.get(connId);
			Sql sql = new Sql(connId, commitSql, null, false);
			try {
				execute(conn, sql, false);
			} catch (Exception e) {
			}
		}
	}

	@Deprecated
	public void clearConnMap() {
		Iterator<Connection> iter = connMap.values().iterator();
		while (iter.hasNext()) {
			Connection conn = (Connection) iter.next();
			try {
				if (conn != null) {
					conn.close();
					conn = null;
				}
			} catch (SQLException e) {
			}
		}

		connMap.clear();

		synchronized (connList) {
			if (connList.size() > 0) {
				try {
					connList.wait();
				} catch (InterruptedException e) {
				}
			}
		}

		this.connMap.putAll(connMapCopy);
		connMapCopy.clear();
	}

	@Deprecated
	public void release() {
		try {
			this.setEndFlag(true);

			clearConnMap();

			Iterator<BasicDataSource> iter = dataSourceMap.values().iterator();
			while (iter.hasNext()) {
				BasicDataSource dataSource = (BasicDataSource) iter.next();
				try {
					dataSource.close();
				} catch (SQLException e) {
					e.printStackTrace();
				}
			}
		} catch (Exception e) {
		}
	}

	/**
	 * 
	 * @Title: executeCall
	 * @Description:Execute script,call JAVA store procedure.
	 * @param @param conn
	 * @param @param sql
	 * @return void
	 * @throws
	 */
	private void executeCall(Connection conn, Sql sql) {
		CallableStatement ps = null;
		try {
			ps = conn.prepareCall(sql.getScript());
			List<SqlParam> paramList = sql.getParamList();
			if (paramList != null) {
				for (int i = 0; i < paramList.size(); i++) {
					SqlParam param = (SqlParam) paramList.get(i);
					Object value = param.getValue();
					int index = param.getIndex();
					int type = param.getType();
					String paramType = param.getParamType();
					if (paramType == null || (paramType.startsWith("IN"))) {
						if (value == null || value.toString().equals("NULL")) {
							ps.setNull(index, type);
						} else {
							ps.setObject(index, value);
						}
					} else if (paramType.indexOf("OUT") != -1) {
						ps.registerOutParameter(index, type);
					}
				}
			}

			boolean isRs = ps.execute();
			getAllResult(ps, isRs, sql);

			if (paramList != null) {
				StringBuilder sb = new StringBuilder();
				for (int i = 0; i < paramList.size(); i++) {
					SqlParam param = (SqlParam) paramList.get(i);
					int index = param.getIndex();
					String paramType = param.getParamType();
					if (paramType == null) {
						continue;
					}
					if (paramType.indexOf("OUT") != -1) {
						Object o = ps.getObject(index);
						sb.append(o + System.getProperty("line.separator"));
					}
				}
				sql.setResult(sql.getResult() + sb.toString());
				sb = null;
			}
		} catch (SQLException e) {
			this.onMessage(e.getMessage());
			sql.setResult(getExceptionMessage(e, test.isEditorExecute()));
		} finally {
			try {
				if (ps != null) {
					ps.close();
					ps = null;

				}
			} catch (Exception e) {
			}
		}
	}

	/**
	 * 
	 * @Title: executePrepareStatement
	 * @Description:Execute SQL sentence use prepared statment.
	 * @param @param conn
	 * @param @param sql
	 * @param @param isPrintQueryPlan
	 * @return void
	 * @throws
	 */
	private void executePrepareStatement(Connection conn, Sql sql, boolean isPrintQueryPlan) {
		PreparedStatement ps = null;
		try {
			ps = conn.prepareStatement(sql.getScript());
			List<SqlParam> paramList = sql.getParamList();
			if (paramList != null) {
				for (int i = 0; i < paramList.size(); i++) {
					SqlParam param = (SqlParam) paramList.get(i);
					Object value = param.getValue();
					int index = param.getIndex();
					int type = param.getType();
					String paramType = param.getParamType();
					if (paramType == null) {
						if (value == null || value.toString().equals("NULL")) {
							ps.setNull(index, type);
						} else {
							switch (type) {
							case Types.CHAR:
								ps.setString(index, (String) value);
								break;
							case Types.VARCHAR:
								ps.setString(index, (String) value);
								break;
							// case Types.NCHAR:
							// ps.setString(index, (String) value);
							// break;
							case Types.NUMERIC:
								ps.setDouble(index, (Double) value);
								break;
							case Types.DECIMAL:
								ps.setDouble(index, (Double) value);
								break;
							case Types.DOUBLE:
								ps.setDouble(index, (Double) value);
								break;
							case Types.INTEGER:
								ps.setInt(index, (Integer) value);
								break;
							case Types.SMALLINT:
								ps.setObject(index, value);
								break;
							case Types.FLOAT:
								ps.setFloat(index, (Float) value);
								break;
							case Types.REAL:
								ps.setFloat(index, (Float) value);
								break;
							case Types.DATE:
								ps.setDate(index, Date.valueOf((String) value));
								break;
							case Types.TIME:
								ps.setTime(index, Time.valueOf((String) value));
								break;
							case Types.TIMESTAMP:
								ps.setTimestamp(index, Timestamp.valueOf((String) value));
								break;
							default:
								ps.setObject(index, value);
								break;
							}
						}
					}
				}
			}
			String script = sql.getScript().trim().toUpperCase();
			if ((isPrintQueryPlan && script.startsWith("SELECT")) || sql.isQueryplan()) {
				Method method2 = ps.getClass().getMethod("setQueryInfo", new Class[] { boolean.class });
				method2.invoke(ps, new Object[] { true });
			}
			boolean isRs = ps.execute();
			getAllResult(ps, isRs, sql);
			if ((isPrintQueryPlan && script.startsWith("SELECT")) || sql.isQueryplan()) {
				Method method = ps.getClass().getMethod("getQueryplan", new Class[] {});
				String queryPlan = (String) method.invoke(ps, new Object[] {});
				queryPlan = queryPlan + System.getProperty("line.separator");
				queryPlan = StringUtil.replaceQureyPlan(queryPlan);
				sql.setResult(sql.getResult() + queryPlan);
				method = null;
				queryPlan = null;
			}
		} catch (SQLException e) {
			this.onMessage(e.getMessage());
			sql.setResult(getExceptionMessage(e, test.isEditorExecute()));
		} catch (Exception e2) {
			this.onMessage(e2.getMessage());
			sql.setResult("Error:-" + 10000 + System.getProperty("line.separator"));
		} finally {
			try {
				if (ps != null) {
					ps.close();
					ps = null;
				}
			} catch (Exception e) {
			}
		}
	}

	/**
	 * 
	 * @Title: executeStatement
	 * @Description:Create statement for executing SQL sentence.
	 * @param @param conn
	 * @param @param sql
	 * @param @param isPrintQueryPlan: Determine whether need to get query plan.
	 * @return void
	 * @throws
	 */
	private void executeStatement(Connection conn, Sql sql, boolean isPrintQueryPlan) {
		Statement st = null;
		try {
			st = conn.createStatement();
			//System.out.println("sql.getScript: " + sql.getScript());
			boolean isRs = st.execute(sql.getScript());
			getAllResult(st, isRs, sql);
			String script = sql.getScript().trim().toUpperCase();
			if ((isPrintQueryPlan && script.startsWith("SELECT")) || sql.isQueryplan()) {
				Method method = st.getClass().getMethod("getQueryplan", stringType);
				String queryPlan = (String) method.invoke(st, new Object[] { sql.getScript() });
				queryPlan = queryPlan + System.getProperty("line.separator");
				queryPlan = StringUtil.replaceQureyPlan(queryPlan);
				sql.setResult(sql.getResult() + queryPlan);
				method = null;
				queryPlan = null;
			}
		} catch (SQLException e) {
			sql.setResult(getExceptionMessage(e, test.isEditorExecute()));
			// e.printStackTrace();
			this.onMessage(e.getMessage());
		} catch (SecurityException e) {
			// e.printStackTrace();
			this.onMessage(e.getMessage());
		} catch (NoSuchMethodException e) {
			// e.printStackTrace();
			this.onMessage(e.getMessage());
		} catch (IllegalArgumentException e) {
			// e.printStackTrace();
			this.onMessage(e.getMessage());
		} catch (IllegalAccessException e) {
			// e.printStackTrace();
			this.onMessage(e.getMessage());
		} catch (InvocationTargetException e) {
			// e.printStackTrace();
			this.onMessage(e.getMessage());
		} finally {
			try {
				if (st != null) {
					st.close();
					st = null;
				}
			} catch (Exception e) {
			}
		}
	}

	/**
	 * 
	 * @Title: getExceptionMessage
	 * @Description:Get exception message.
	 * @param @param e
	 * @param @param editorExecute
	 * @param @return
	 * @return String
	 * @throws
	 */
	private String getExceptionMessage(SQLException e, boolean editorExecute) {
		StringBuilder message = new StringBuilder();
		String errorMessage = StringUtil.replaceExceptionMessage(e.toString());
		if (editorExecute) {
			message.append(e.getErrorCode() + ":" + errorMessage + System.getProperty("line.separator"));
		} else {
			SystemModel systemModel = (SystemModel) XstreamHelper.fromXml(EnvGetter.getenv("CTP_HOME") + File.separator + "sql/configuration/System.xml");
			if (systemModel.isErrorMessage()) {
				message.append(e.getErrorCode() + ":" + errorMessage + System.getProperty("line.separator"));
			} else {
				message.append("Error:" + e.getErrorCode() + System.getProperty("line.separator"));
			}
		}
		return message.toString();
	}

	/**
	 * 
	 * @Title: getAllResult
	 * @Description:Determine whether need to get results.If insert or update
	 *                        then get the number of rows that have been
	 *                        affected,if select sentence then get the results.
	 * @param @param st
	 * @param @param isRs
	 * @param @param sql
	 * @param @throws SQLException
	 * @return void
	 * @throws
	 */
	private void getAllResult(Statement st, boolean isRs, Sql sql) throws SQLException {
		int updateCount = -1;
		while (true) {
			if (isRs) {
				ResultSet rs = st.getResultSet();
				getResult(rs, sql);
			} else {
				updateCount = st.getUpdateCount();
				if (updateCount == -1) {
					break;
				} else {
					sql.setResult(sql.getResult() + updateCount + System.getProperty("line.separator"));
				}
			}

			isRs = st.getMoreResults();
		}
	}

	/**
	 * 
	 * @Title: getResult
	 * @Description:Get results from result set.
	 * @param @param rs
	 * @param @param sql
	 * @return void
	 * @throws
	 */
	private void getResult(ResultSet rs, Sql sql) {
		StringBuilder ret = new StringBuilder();
		try {
			ResultSetMetaData meta = rs.getMetaData();
			int columnCount = meta.getColumnCount();
			for (int i = 0; i < columnCount; i++) {
				int index = i + 1;
				String columnName = meta.getColumnName(index);
				ret.append(columnName + "    ");
			}
			ret.append(System.getProperty("line.separator"));

			while (rs.next()) {
				for (int i = 0; i < columnCount; i++) {
					int index = i + 1;
					String columnTypeName = meta.getColumnTypeName(index);
					int columnType = meta.getColumnType(index);
					Object data = rs.getObject(index);
					String value = getColumnValue(columnType, columnTypeName, data, rs, index);
					ret.append(value + "     ");
				}
				ret.append(System.getProperty("line.separator"));
			}
			this.onMessage("+++++++++++++++++++record begin++++++++++++++++++++");
			this.onMessage(ret.toString());
			this.onMessage("+++++++++++++++++++record end++++++++++++++++++++");
		} catch (Exception e) {
			this.onMessage(e.getMessage());
		} finally {
			try {
				if (rs != null) {
					rs.close();
				}
			} catch (Exception e) {
			}
		}

		String script = sql.getScript().trim().toUpperCase();
		if (script.startsWith("SHOW TRACE")) {
			String res = ret.toString();
			res = res.replaceAll("[0-9]+", "?");
			sql.setResult(sql.getResult() + res + System.getProperty("line.separator"));
		} else {
			sql.setResult(sql.getResult() + ret.toString() + System.getProperty("line.separator"));
		}
	}

	/**
	 * 
	 * @Title: getColumnValue
	 * @Description:Get every column's result.
	 * @param @param colType
	 * @param @param colTypeName
	 * @param @param value
	 * @param @param rs
	 * @param @param index
	 * @param @return
	 * @return String
	 * @throws
	 */
	private String getColumnValue(int colType, String colTypeName, Object value, ResultSet rs, int index) {
		if (value == null) {
			return null;
		}

		StringBuilder sb = new StringBuilder();
		try {
			if (colType == Types.VARBINARY && colTypeName.equalsIgnoreCase("BIT VARYING")) {
				byte[] bytes = (byte[]) value;
				sb.append(StringUtil.toHexString(bytes));
			} else if (colType == Types.BINARY && colTypeName.equalsIgnoreCase("BIT")) {
				byte[] bytes = (byte[]) value;
				sb.append(StringUtil.toHexString(bytes));
			} else if (colType == Types.OTHER) {
				if ("CLASS".equalsIgnoreCase(colTypeName) || "".equalsIgnoreCase(colTypeName)) {
					try {
						Method method = rs.getClass().getMethod("getOID", intType);
						Object oid = method.invoke(rs, index);
						Method method2 = oid.getClass().getMethod("getTableName", classType);
						String tableName = (String) method2.invoke(oid, objects);
						sb.append(tableName);
					} catch (Exception e) {
						sb.append(value.toString());
					}
				} else {
					Method method = rs.getClass().getMethod("getCollection", intType);
					Object[] set = (Object[]) method.invoke(rs, new Object[] { index });
					for (int i = 0; i < set.length; i++) {
						Object o = set[i];
						boolean isInstance = true;
						if (o == null) {
							isInstance = false;
						} else {
							try {
								Object cast = this.oid.cast(o);
								if (cast == null) {
									isInstance = false;
								}
							} catch (Exception e) {
								isInstance = false;
							}
						}
						if (isInstance) {
							Method method2 = o.getClass().getMethod("getTableName", classType);
							String tableName = (String) method2.invoke(o, objects);
							sb.append(tableName + ",");
						} else if (o != null && o instanceof byte[]) {
							byte[] bytes = (byte[]) o;
							sb.append(StringUtil.toHexString(bytes) + ",");
						} else {
							sb.append(o + ",");
						}
					}
				}
			} else {
				sb.append(value.toString());
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return sb.toString();
	}

	public List<String> getConnList() {
		return connList;
	}

	public boolean isEndFlag() {
		return endFlag;
	}

	public void setEndFlag(boolean endFlag) {
		this.endFlag = endFlag;
	}

	private void addConnToThread(String key) {
		synchronized (connList) {
			connList.add(key);
			connList.notifyAll();
		}
	}
}
