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
import java.net.URL;
import java.net.URLClassLoader;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.apache.commons.dbcp.AbandonedConfig;
import org.apache.commons.dbcp.AbandonedObjectPool;
import org.apache.commons.dbcp.BasicDataSource;
import org.apache.commons.dbcp.DriverConnectionFactory;
import org.apache.commons.dbcp.PoolableConnectionFactory;
import org.apache.commons.dbcp.PoolingDataSource;
import org.apache.commons.dbcp.SQLNestedException;
import org.apache.commons.pool.impl.GenericKeyedObjectPoolFactory;
import org.apache.commons.pool.impl.GenericObjectPool;

@SuppressWarnings("deprecation")
public class MyDataSource extends BasicDataSource {

	private AbandonedConfig abandonedConfig;

	public Connection getConnection() throws SQLException {
		return createDataSource().getConnection();
	}

	protected synchronized DataSource createDataSource() throws SQLException {
		if (dataSource != null) {
			return dataSource;
		}
		if (driverClassName != null) {
			try {
				Class.forName(driverClassName);
			} catch (Throwable t) {
				String message = "Cannot load JDBC driver class '" + driverClassName + "'";
				logWriter.println(message);
				t.printStackTrace(logWriter);
				throw new SQLNestedException(message, t);
			}
		}
		Driver driver = null;
		try {
			SystemModel systemModel = (SystemModel) XstreamHelper.fromXml(PropertiesUtil.getValue("local.path") + "/configuration/System.xml");
			String path = systemModel.getJdbcPath();
			File file = new File(path);
			URL url = file.toURL();
			URLClassLoader classLoader = new URLClassLoader(new URL[] { url });
			Class driverClass = Class.forName(driverClassName, true, classLoader);
			driver = (Driver) driverClass.newInstance();
		} catch (Throwable t) {
			String message = "Cannot create JDBC driver of class '" + (driverClassName == null ? "" : driverClassName) + "' for connect URL '" + url + "'";
			logWriter.println(message);
			t.printStackTrace(logWriter);
			throw new SQLNestedException(message, t);
		}
		if (validationQuery == null) {
			setTestOnBorrow(false);
			setTestOnReturn(false);
			setTestWhileIdle(false);
		}
		if (abandonedConfig != null && abandonedConfig.getRemoveAbandoned()) {
			connectionPool = new AbandonedObjectPool(null, abandonedConfig);
		} else {
			connectionPool = new GenericObjectPool();
		}
		connectionPool.setMaxActive(maxActive);
		connectionPool.setMaxIdle(maxIdle);
		connectionPool.setMinIdle(minIdle);
		connectionPool.setMaxWait(maxWait);
		connectionPool.setTestOnBorrow(testOnBorrow);
		connectionPool.setTestOnReturn(testOnReturn);
		connectionPool.setTimeBetweenEvictionRunsMillis(timeBetweenEvictionRunsMillis);
		connectionPool.setNumTestsPerEvictionRun(numTestsPerEvictionRun);
		connectionPool.setMinEvictableIdleTimeMillis(minEvictableIdleTimeMillis);
		connectionPool.setTestWhileIdle(testWhileIdle);
		GenericKeyedObjectPoolFactory statementPoolFactory = null;
		if (isPoolPreparedStatements()) {
			statementPoolFactory = new GenericKeyedObjectPoolFactory(null, -1, (byte) 0, 0L, 1, maxOpenPreparedStatements);
		}
		if (username != null) {
			connectionProperties.put("user", username);
		} else {
			logWriter.println("DBCP DataSource configured without a 'username'");
		}
		if (password != null) {
			connectionProperties.put("password", password);
		} else {
			logWriter.println("DBCP DataSource configured without a 'password'");
		}
		DriverConnectionFactory driverConnectionFactory = new DriverConnectionFactory(driver, url, connectionProperties);
		PoolableConnectionFactory connectionFactory = null;
		try {
			connectionFactory = new PoolableConnectionFactory(driverConnectionFactory, connectionPool, statementPoolFactory, validationQuery, defaultReadOnly, defaultAutoCommit,
					defaultTransactionIsolation, defaultCatalog, abandonedConfig);
			if (connectionFactory == null) {
				throw new SQLException("Cannot create PoolableConnectionFactory");
			}
			validateConnectionFactory(connectionFactory);
		} catch (RuntimeException e) {
			throw e;
		} catch (Exception e) {
			throw new SQLNestedException("Cannot create PoolableConnectionFactory", e);
		}
		dataSource = new PoolingDataSource(connectionPool);
		((PoolingDataSource) dataSource).setAccessToUnderlyingConnectionAllowed(isAccessToUnderlyingConnectionAllowed());
		dataSource.setLogWriter(logWriter);
		return dataSource;
	}

	private void validateConnectionFactory(PoolableConnectionFactory connectionFactory) throws Exception {
		Connection conn = null;
		try {
			conn = (Connection) connectionFactory.makeObject();
			connectionFactory.validateConnection(conn);
		} finally {
			connectionFactory.destroyObject(conn);
		}
	}

}
