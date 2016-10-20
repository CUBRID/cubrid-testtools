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
package com.navercorp.cubridqa.ha_repl;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.navercorp.cubridqa.common.CommonUtils;
import com.navercorp.cubridqa.common.ConfigParameterConstants;
import com.navercorp.cubridqa.common.Log;
import com.navercorp.cubridqa.shell.common.GeneralScriptInput;
import com.navercorp.cubridqa.shell.common.SSHConnect;

public class HaReplUtils {

	private static int MAX_TRY_WAIT_STATUS = 180;

	public static void rebuildFinalDatabase(Context context, InstanceManager hostManager, Log log, String... params) {
		int maxTry = 5;
		while (maxTry-- > 0) {
			try {
				__rebuildDatabase(context, hostManager, log, params);
				break;
			} catch (Exception e) {
				log.println("Rebuild DB Error: " + e.getMessage());
			}
		}
	}

	private static void __rebuildDatabase(Context context, InstanceManager hostManager, Log log, String... params) throws Exception {
		log.println("DATABASE IS DIRTY. REBUILD...");

		ArrayList<SSHConnect> allHosts = hostManager.getAllNodeList();

		StringBuffer s = new StringBuffer();
		s.append("pkill -u $USER cub;ps -u $USER | grep cub | awk '{print $1}' | grep -v PID | xargs -i  kill -9 {}; ipcs | grep $USER | awk '{print $2}' | xargs -i ipcrm -m {};cubrid deletedb "
				+ hostManager.getTestDb()).append(";");
		s.append("cd ~/CUBRID/databases/").append(";");
		if(CommonUtils.isEmpty(hostManager.getTestDb()) == false) {
			s.append("rm -rf ").append(hostManager.getTestDb().trim() + "*").append(";");
		}
		s.append("cd ~;");

		GeneralScriptInput script = new GeneralScriptInput(s.toString());
		for (SSHConnect ssh : allHosts) {
			ssh.execute(script);
		}

		s = new StringBuffer();
		s.append("cd ~/CUBRID/databases").append(";");
		s.append("mkdir ").append(hostManager.getTestDb()).append(";");
		s.append("cd ").append(hostManager.getTestDb()).append(";");

		if (params != null) {
			for (String p : params) {
				if (p != null && p.trim().equals("") == false) {
					s.append("echo " + p + " >> $CUBRID/conf/cubrid.conf; ");
				}
			}
		}

		if (CommonUtils.haveCharsetToCreateDB(context.getBuildId())) {
			// use different DBcharset to run test
			String dbCharset = context.getProperty(ConfigParameterConstants.CUBRID_DB_CHARSET, "").trim();
			if (dbCharset.equals("")) {
				dbCharset = "en_US";
			}
			s.append("cubrid createdb ").append(hostManager.getTestDb()).append(" --db-volume-size=50M --log-volume-size=50M " + dbCharset + ";");
		} else {
			s.append("cubrid createdb ").append(hostManager.getTestDb()).append(" --db-volume-size=50M --log-volume-size=50M;");
		}

		s.append("cubrid hb start ").append(";");
		s.append("cubrid broker start").append(";");
		s.append("cd ~;");

		script = new GeneralScriptInput(s.toString());

		SSHConnect master = hostManager.getHost("master");
		log.println("------------ MASTER : CREATE DATABASE -----------------");

		String result = master.execute(script);
		if (result.indexOf("fail") != -1) {
			throw new Exception("fail to create on master.");
		}
		boolean succ = waitDatabaseReady(master, hostManager.getTestDb(), "to-be-active", log, MAX_TRY_WAIT_STATUS);
		if (!succ)
			throw new Exception("timeout when wait to-be-active in master");

		ArrayList<SSHConnect> slaveAndReplicaList = hostManager.getAllSlaveAndReplicaList();
		for (SSHConnect ssh : slaveAndReplicaList) {
			log.println("------------ SLAVE/REPLICA : CREATE DATABASE -----------------");
			result = ssh.execute(script);
			if (result.indexOf("fail") != -1) {
				throw new Exception("fail to create on slave/replica.");
			}
			succ = waitDatabaseReady(ssh, hostManager.getTestDb(), "is standby", log, MAX_TRY_WAIT_STATUS);
			if (!succ)
				throw new Exception("timeout when wait standby in slave/replica");
		}
		log.println("------------ MASTER : WAIT ACTIVE -----------------");
		succ = waitDatabaseReady(master, hostManager.getTestDb(), "is active", log, MAX_TRY_WAIT_STATUS);
		if (!succ)
			throw new Exception("timout when wait active in master");

		CommonUtils.sleep(1);
		log.println("REBUILD DONE");
	}

	private static boolean waitDatabaseReady(SSHConnect ssh, String dbName, String expectedStatus, Log log, int maxTry) throws Exception {
		GeneralScriptInput script = new GeneralScriptInput("cubrid changemode " + dbName);
		String result;
		while (maxTry-- > 0) {
			result = ssh.execute(script);
			log.println(result);
			if (result.indexOf(expectedStatus) != -1) {
				return true;
			}
			CommonUtils.sleep(1);
		}
		return false;
	}


	
	public static ArrayList<String[]> extractTableToBeVerified(String input, String flag) {

		ArrayList<String[]> list = new ArrayList<String[]>();
		if (input == null)
			return list;

		Pattern pattern = Pattern.compile("'" + flag + "'\\s*'(.*?)'\\s*([0-9]*)");
		Matcher matcher = pattern.matcher(input);

		String[] item;

		while (matcher.find()) {
			item = new String[2];
			item[0] = matcher.group(1);
			item[1] = matcher.group(2);

			list.add(item);
		}
		return list;

	}
}
