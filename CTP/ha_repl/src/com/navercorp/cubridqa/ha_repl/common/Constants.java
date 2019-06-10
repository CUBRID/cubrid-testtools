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
package com.navercorp.cubridqa.ha_repl.common;

public class Constants {
	public static final String LINE_SEPARATOR = System.getProperty("line.separator");;

	public static final int TYPE_MASTER = 1;
	public static final int TYPE_SLAVE = 2;
	public static final int TYPE_REPLICA = 3;

	public static String FM_DATE_SNAPSHOT = "yyyyMMdd_hhmmss";

	public static final String SKIP_TYPE_NO = "0";
	public static final String SKIP_TYPE_BY_MACRO = "1"; // Not implement.
															// Please ignore. By
															// Fan
	public static final String SKIP_TYPE_BY_TEMP = "2";

	public static final String DIR_ERROR_BACKUP = "~/ERROR_BACKUP";

	public static final String GET_BROKER_PORT_CMD = "cat $CUBRID/conf/cubrid_broker.conf| grep '^SERVICE\\|^BROKER_PORT' |grep -A1 'ON' |grep 'BROKER_PORT'|tail -n 1|awk -F '=' '{print $NF}'|tr -d '[[:space:]]'";
	
	public static final int HA_SYNC_DETECT_TIMEOUT_IN_MS_DEFAULT = 600 * 1000;
	
	public static final int HA_SYNC_FAILURE_RESOLVE_MODE_STOP = 1;
	public static final int HA_SYNC_FAILURE_RESOLVE_MODE_CONTINUE = 2;
	public static final int HA_SYNC_FAILURE_RESOLVE_MODE_WAIT = 3;
}
