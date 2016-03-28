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
package com.navercorp.cubridqa.cqt.console.bean;

import java.util.List;


public class Sql {
	public static final int TYPE_STMT = 0;

	public static final int TYPE_PRE_STMT = 1;

	public static final int TYPE_CALL = 2;

	private String script;

	private List<SqlParam> paramList;
	
	//add query plan for single sql statement
	private boolean isQueryplan = false;

	private int type;
	
	private String result = "";

	private boolean isSuccessful = true;

	private long time;

	private String connId = "";

	public Sql(String connId, String src, List<SqlParam> paramList,
			boolean isCall) {
		setConnId(connId);
		setScript(src);
		setParamList(paramList);

		int type = Sql.TYPE_STMT;
		if (isCall) {
			type = Sql.TYPE_CALL;
		} else if (isPrep(src)) {// .indexOf("?") != -1) {
			type = Sql.TYPE_PRE_STMT;
		}
		setType(type);
	}

	private boolean isPrep(String sql) {
		char[] cs = sql.toCharArray();
		boolean lock = true;
		for (char key : cs) {
			if (key == '\'') {
				lock = !lock;
			}
			if (key == '?') {
				if (lock) {
					return true;
				}
			}

		}
		return false;

	}

	public List<SqlParam> getParamList() {
		return paramList;
	}

	public void setParamList(List<SqlParam> paramList) {
		this.paramList = paramList;
	}

	public String getScript() {
		return script;
	}

	public void setScript(String script) {
		this.script = script;
	}

	public String toString() {
		StringBuilder ret = new StringBuilder();
		ret.append(script);
		ret.append(" type:" + type);

		if (paramList != null) {
			ret.append("  values[");
			for (int i = 0; i < paramList.size(); i++) {
				ret.append(((SqlParam) paramList.get(i)).getValue() + ",");
			}
			ret.append("]");
		}

		return ret.toString();
	}

	public boolean isSuccessful() {
		return isSuccessful;
	}

	public void setSuccessful(boolean isSuccessful) {
		this.isSuccessful = isSuccessful;
	}

	public String getResult() {
		return result;
	}

	public void setResult(String result) {
		this.result = result;
	}

	public int getType() {
		return type;
	}

	public void setType(int type) {
		this.type = type;
	}

	public long getTime() {
		return time;
	}

	public void setTime(long time) {
		this.time = time;
	}

	public String getConnId() {
		return connId;
	}

	public void setConnId(String connId) {
		this.connId = connId;
	}

	public boolean isQueryplan() {
		return isQueryplan;
	}

	public void setQueryplan(boolean isQueryplan) {
		this.isQueryplan = isQueryplan;
	}
	
	
}
