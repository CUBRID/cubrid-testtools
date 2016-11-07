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

import java.sql.Types;

public class CubridUtil {

	/**
	 * get the cubrid working directory.
	 * 
	 * @return
	 */
	public static String getCubridPath() {
		String ret = null;
		try {
			ret = EnvGetter.getenv("CUBRID");
			ret = StringUtil.replaceSlash(ret);
		} catch (Exception e) {
		}
		return ret;
	}

	/**
	 * get the cubrid broker path .
	 * 
	 * @return
	 */
	public static String getCubridBrokerPath() {
		return CubridUtil.getCubridPath() + "";
	}

	/**
	 * get the cubrid manager path .
	 * 
	 * @return
	 */
	public static String getCubridManagerPath() {
		return CubridUtil.getCubridPath() + "/cubridmanager";
	}

	/**
	 * get the cubrid database path .
	 * 
	 * @return
	 */
	public static String getCubridDatabasesPath() {
		return CubridUtil.getCubridPath() + "/databases";
	}

	/**
	 * get the cubrid configuration path.
	 * 
	 * @return
	 */
	public static String getDefaultSqlXConfFile() {
		String ret = CubridUtil.getCubridPath() + "/conf/cubrid.conf";
		return ret;
	}

	/**
	 * get the cubrid broker configuration path.
	 * 
	 * @return
	 */
	public static String getCubridBrokerConfFile() {
		return CubridUtil.getCubridPath() + "/conf/cubrid_broker.conf";
	}

	/**
	 * get the cubrid jdbc jar file path .
	 * 
	 * @return
	 */
	public static String getCubridJdbcFile() {
		String ret = CubridUtil.getCubridPath() + "/java/cubrid_jdbc.jar";
		return ret;
	}

	/**
	 * get the java.sql type map to cubrid data type.
	 * 
	 * @param cubridType
	 * @return
	 */
	public static int getSqlType(String cubridType) {
		if (cubridType == null || cubridType.trim().equals("")) {
			return Types.VARCHAR;
		}

		cubridType = cubridType.toUpperCase();
		if (cubridType.equals("CHAR")) {
			return Types.CHAR;
		} else if (cubridType.equals("VARCHAR")) {
			return Types.VARCHAR;
		} else if (cubridType.equals("STRING")) {
			return Types.VARCHAR;
		} else if (cubridType.equals("NCHAR")) {
			return Types.CHAR;
		} else if (cubridType.equals("BIT")) {
			return Types.BIT;
		} else if (cubridType.equals("BIT VARYING")) {
			return Types.BIT;
		} else if (cubridType.equals("NUMERIC") || cubridType.equals("DECIMAL")) {
			return Types.NUMERIC;
		} else if (cubridType.equals("INTEGER") || cubridType.equals("INT")) {
			return Types.INTEGER;
		} else if (cubridType.equals("SMALLINT")) {
			return Types.SMALLINT;
		} else if (cubridType.equals("MONETARY")) {
			return Types.VARCHAR;
		} else if (cubridType.equals("FLOAT") || cubridType.equals("REAL")) {
			return Types.FLOAT;
		} else if (cubridType.equals("DOUBLE PRECISION") || cubridType.equals("DOUBLE")) {
			return Types.DOUBLE;
		} else if (cubridType.equals("DATE")) {
			return Types.DATE;
		} else if (cubridType.equals("TIME")) {
			return Types.TIME;
		} else if (cubridType.equals("TIMESTAMP")) {
			return Types.TIMESTAMP;
		} else if (cubridType.equals("NULL")) {
			return Types.NULL;
		} else {
			return Types.OTHER;
		}
	}

	/**
	 * get the value through different object .
	 * 
	 * @param sqlType
	 * @param value
	 * @return
	 */
	public static Object getObject(int sqlType, String value) {
		if (value == null || value.equalsIgnoreCase("null")) {
			return null;
		}

		if (sqlType == Types.CHAR) {
			return value;
		} else if (sqlType == Types.VARCHAR) {
			return value;
		} else if (sqlType == Types.BIT) {
			return value;
		} else if (sqlType == Types.INTEGER) {
			return new Integer(value);
		} else if (sqlType == Types.NUMERIC) {
			return new Double(value);
		} else if (sqlType == Types.FLOAT) {
			return new Float(value);
		} else if (sqlType == Types.DOUBLE) {
			return new Double(value);
		} else if (sqlType == Types.SMALLINT) {
			return new Short(value);
		} else if (sqlType == Types.DATE) {
			return value;
		} else if (sqlType == Types.TIME) {
			return value;
		} else if (sqlType == Types.TIMESTAMP) {
			return value;
		} else if (sqlType == Types.ARRAY) {
			return value;
		} else if (sqlType == Types.NULL) {
			return null;
		} else {
			return value;
		}
	}
}
