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
 * Filename        : DbmsConnection.java 
 * Date			   : Aug 20, 2008
 * Creator         : Anson Cheung
 * Revised Description
 * ----------------------------------------------
 * ver	revised date	reviser	revised contents
 * 0.1    Aug 20, 2008        Anson            create
 * ----------------------------------------------
 * 
 *</pre>
 */
package com.navercorp.cubridqa.cqt.console.util;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Date;

import org.apache.commons.dbcp.BasicDataSource;

import com.navercorp.cubridqa.cqt.console.bean.DefTestDB;
import com.navercorp.cubridqa.cqt.console.bean.Test;

public class CubridConnection {

	private int id;
	private boolean autocreate;
	private String connName;
	private String connGroup;
	private Connection Conn;
	private long create;
	private boolean using;
	private Object _obj;

	/**
	 * determine if connection is auto created .
	 * 
	 * @return
	 */
	public boolean isAutocreate() {
		return autocreate;
	}

	/**
	 * 
	 * @param connName
	 * @param connGroup
	 * @param _obj
	 * @param seq
	 */
	public CubridConnection(String connName, String connGroup, Object _obj, int seq) {
		if (connName == null || connName.trim().length() == 0) {
			connName = "default";
		}
		this.create = (new Date()).getTime();
		this.connGroup = connGroup;
		this.connName = connName;
		this.id = seq;
		this._obj = _obj;
	}

	/**
	 * 
	 * @param connName
	 * @param connGroup
	 * @param _obj
	 * @param seq
	 * @param autocreate
	 */

	public CubridConnection(String connName, String connGroup, Object _obj, int seq, boolean autocreate) {

		if (connName == null || connName.trim().length() == 0) {
			connName = "default";
		}
		this.create = (new Date()).getTime();
		this.connGroup = connGroup;
		this.connName = connName;
		this.id = seq;
		this.autocreate = autocreate;
		this._obj = _obj;
	}

	public String getConnName() {
		return connName;
	}

	public String getConnGroup() {
		return connGroup;
	}

	/**
	 * determine if the connection is available or not .
	 */
	public boolean isAvlible() {
		try {
			if (this.Conn == null || this.Conn.isClosed()) {
				ReUseConn();
				try {
					this.Conn.setAutoCommit(false);
				} catch (SQLException e) {
					e.printStackTrace();
				}
				return true;
			} else if (using) {
				return false;
			} else {
				return true;
			}
		} catch (SQLException e) {
			e.printStackTrace();
			return false;
		}
	}

	/**
	 * create connection or get from pool to reuse the connection.
	 */
	private void ReUseConn() {
		DefTestDB dbConf = null;
		BasicDataSource dataSource = null;
		boolean usePool = false;
		if (_obj instanceof DefTestDB) {
			dbConf = (DefTestDB) _obj;
			usePool = false;
		} else {
			dataSource = (MyDataSource) _obj;
			usePool = true;
		}
		if (!usePool) {
			String url = dbConf.getDburl() + "?charset=" + dbConf.getCharSet();
			String user = dbConf.getDbuser();
			String password = dbConf.getDbpassword();
			if (Test.urlProperties != null && Test.urlProperties.length() != 0) {
				url += "&" + Test.urlProperties;
			}
			Conn = MyDriverManager.giveConnection(CubridConnManager.driver, url, user, password);
		} else {
			if (dataSource != null) {
				try {
					Conn = dataSource.getConnection();
				} catch (SQLException e) {
					System.out.println("Error:Create Connection with  dataSource Failed !!!  ConnName :" + this.connName);

				}
			}
		}

	}

	public Connection getConn() {
		return Conn;
	}

	public void free() {
		try {
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			using = false;
		}
	}

	public long getCreate() {
		return create;
	}

	public int getId() {
		return id;
	}

	/**
	 * close the connection .
	 */
	public void destroyed() {
		if (this.Conn != null)
			try {
				this.Conn.close();
			} catch (SQLException e) {
				e.printStackTrace();
			}
		this._obj = null;

	}
}
