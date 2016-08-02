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

package com.navercorp.cubridqa.isolation;

import java.util.HashMap;

public class Constants {
	public static final String LINE_SEPARATOR = System.getProperty("line.separator");;

	public static final String TC_RESULT_OK = "OK";
	public static final String TC_RESULT_NOK = "NOK";

	public static final String SKIP_TYPE_NO = "0";
	public static final String SKIP_TYPE_BY_MACRO = "1"; 
	public static final String SKIP_TYPE_BY_TEMP = "2";

	public static final HashMap<String, String> DB_TEST_MAP;
	static {
		DB_TEST_MAP = new HashMap<String, String>();
		DB_TEST_MAP.put("cubrid", "qacsql");
		DB_TEST_MAP.put("mysql", "qamysql");
	}

	public static final String WIN_KILL_PROCESS = createWinKillScripts();
	public static final String LIN_KILL_PROCESS = createLinKillScripts();

	private static String createWinKillScripts() {
		StringBuffer scripts = new StringBuffer();
		scripts.append("$CUBRID/bin/cubrid.exe service stop ").append(LINE_SEPARATOR);
		scripts.append("tasklist |  grep cubridservice | awk '{print $2}' | xargs -i taskkill '/T' '/F' '/PID' {} ").append(LINE_SEPARATOR);
		scripts.append("tasklist |  grep cub_master | awk '{print $2}' | xargs -i taskkill '/T' '/F' '/PID' {} ").append(LINE_SEPARATOR);
		scripts.append("tasklist |  grep cub_server | awk '{print $2}' | xargs -i taskkill '/T' '/F' '/PID' {} ").append(LINE_SEPARATOR);
		scripts.append("tasklist |  grep cub_broker | awk '{print $2}' | xargs -i taskkill '/T' '/F' '/PID' {} ").append(LINE_SEPARATOR);
		scripts.append("tasklist |  grep cub | awk '{print $2}' | xargs -i taskkill '/T' '/F' '/PID' {} ").append(LINE_SEPARATOR);
		scripts.append("tasklist |  grep cub | awk '{print $2}' | xargs -i taskkill '/T' '/F' '/PID' {} ").append(LINE_SEPARATOR);
		scripts.append("tasklist |  grep broker | awk '{print $2}' | xargs -i taskkill '/T' '/F' '/PID' {} ").append(LINE_SEPARATOR);
		scripts.append("tasklist |  grep shard | awk '{print $2}' | xargs -i taskkill '/T' '/F' '/PID' {} ").append(LINE_SEPARATOR);
		scripts.append("tasklist |  grep convert_password | awk '{print $2}' | xargs -i taskkill '/T' '/F' '/PID' {} ").append(LINE_SEPARATOR);
		scripts.append("tasklist |  grep csql | awk '{print $2}' | xargs -i taskkill '/T' '/F' '/PID' {} ").append(LINE_SEPARATOR);
		scripts.append("tasklist |  grep ctrlservice | awk '{print $2}' | xargs -i taskkill '/T' '/F' '/PID' {} ").append(LINE_SEPARATOR);
		scripts.append("tasklist |  grep -i CUBRID | awk '{print $2}' | xargs -i taskkill '/T' '/F' '/PID' {} ").append(LINE_SEPARATOR);
		scripts.append("tasklist |  grep loadjava | awk '{print $2}' | xargs -i taskkill '/T' '/F' '/PID' {} ").append(LINE_SEPARATOR);
		scripts.append("tasklist |  grep migrate | awk '{print $2}' | xargs -i taskkill '/T' '/F' '/PID' {} ").append(LINE_SEPARATOR);
		scripts.append("tasklist |  grep setupmanage | awk '{print $2}' | xargs -i taskkill '/T' '/F' '/PID' {} ").append(LINE_SEPARATOR);
		scripts.append("tasklist |  grep make_locale | awk '{print $2}' | xargs -i taskkill '/T' '/F' '/PID' {} ").append(LINE_SEPARATOR);
		scripts.append("tasklist |  grep java | awk '{print $2}' | xargs -i taskkill '/T' '/F' '/PID' {} ").append(LINE_SEPARATOR);

		return scripts.toString();
	}

	private static String createLinKillScripts() {
		StringBuffer scripts = new StringBuffer();
		scripts.append("cubrid service stop").append(LINE_SEPARATOR);
		scripts.append(bothKill("ps -u $USER -f| grep -v grep | grep cub_admin | awk '{print $2}'")).append(LINE_SEPARATOR);
		scripts.append(bothKill("ps -u $USER -f| grep -v grep | grep cub_master | awk '{print $2}'")).append(LINE_SEPARATOR);
		scripts.append(bothKill("ps -u $USER -f| grep -v grep | grep cub_server | awk '{print $2}'")).append(LINE_SEPARATOR);
		return scripts.toString();
	}

	private static String bothKill(String k) {
		return k + " | xargs -i kill -9 {} " + LINE_SEPARATOR + "kill -9 `" + k + "`" + LINE_SEPARATOR;
	}

	public static void main(String args[]) {
		System.out.println(LIN_KILL_PROCESS);
	}
}
