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
import com.navercorp.cubridqa.common.Log;
import com.navercorp.cubridqa.ha_repl.common.Constants;
import com.navercorp.cubridqa.shell.common.SSHConnect;
import com.navercorp.cubridqa.shell.common.GeneralShellInput;

public class HAUtils {

	private static int MAX_TRY_WAIT_STATUS = 180;

	public static void rebuildFinalDatabase(Context context, HostManager hostManager, String dbName, Log log, String... params) {
		int maxTry = 5;
		while (maxTry-- > 0) {
			try {
				__rebuildDatabase(context, hostManager, dbName, log, params);
				break;
			} catch (Exception e) {
				log.println("Rebuild DB Error: " + e.getMessage());
			}
		}
	}

	private static void __rebuildDatabase(Context context, HostManager hostManager, String dbName, Log log, String... params) throws Exception {
		log.println("DATABASE IS DIRTY. REBUILD...");

		ArrayList<SSHConnect> allHosts = getAllNodeList(hostManager);

		StringBuffer s = new StringBuffer();
		s.append("pkill -u $USER cub;ps -u $USER | grep cub | awk '{print $1}' | grep -v PID | xargs -i  kill -9 {}; ipcs | grep $USER | awk '{print $2}' | xargs -i ipcrm -m {};cubrid deletedb "
				+ dbName).append(";");
		s.append("cd ~/CUBRID/databases/").append(";");
		s.append("rm -rf ").append(dbName.trim() + "*").append(";");
		s.append("cd ~;");

		GeneralShellInput script = new GeneralShellInput(s.toString());
		for (SSHConnect ssh : allHosts) {
			ssh.execute(script);
		}

		s = new StringBuffer();
		s.append("cd ~/CUBRID/databases").append(";");
		s.append("mkdir ").append(dbName).append(";");
		s.append("cd ").append(dbName).append(";");

		if (params != null) {
			for (String p : params) {
				if (p != null && p.trim().equals("") == false) {
					s.append("echo " + p + " >> $CUBRID/conf/cubrid.conf; ");
				}
			}
		}

		if (haveCharsetToCreateDB(context.getBuildId())) {
			// use different DBcharset to run test
			String dbCharset = context.getProperty("main.db.charset", "").trim();
			if (dbCharset.equals("")) {
				dbCharset = "en_US";
			}
			s.append("cubrid createdb ").append(dbName).append(" --db-volume-size=50M --log-volume-size=50M " + dbCharset + ";");
		} else {
			s.append("cubrid createdb ").append(dbName).append(" --db-volume-size=50M --log-volume-size=50M;");
		}

		s.append("cubrid hb start ").append(";");
		s.append("cubrid broker start").append(";");
		s.append("cd ~;");

		script = new GeneralShellInput(s.toString());

		SSHConnect master = hostManager.getHost("master");
		log.println("------------ MASTER : CREATE DATABASE -----------------");

		String result = master.execute(script);
		if (result.indexOf("fail") != -1) {
			throw new Exception("fail to create on master.");
		}
		boolean succ = waitDatabaseReady(master, dbName, "to-be-active", log, MAX_TRY_WAIT_STATUS);
		if (!succ)
			throw new Exception("timeout when wait to-be-active in master");

		ArrayList<SSHConnect> slaveAndReplicaList = getAllSlaveAndReplicaList(hostManager);
		for (SSHConnect ssh : slaveAndReplicaList) {
			log.println("------------ SLAVE/REPLICA : CREATE DATABASE -----------------");
			result = ssh.execute(script);
			if (result.indexOf("fail") != -1) {
				throw new Exception("fail to create on slave/replica.");
			}
			succ = waitDatabaseReady(ssh, dbName, "is standby", log, MAX_TRY_WAIT_STATUS);
			if (!succ)
				throw new Exception("timeout when wait standby in slave/replica");
		}
		log.println("------------ MASTER : WAIT ACTIVE -----------------");
		succ = waitDatabaseReady(master, dbName, "is active", log, MAX_TRY_WAIT_STATUS);
		if (!succ)
			throw new Exception("timout when wait active in master");

		CommonUtils.sleep(1);
		log.println("REBUILD DONE");
	}

	private static boolean waitDatabaseReady(SSHConnect ssh, String dbName, String expectedStatus, Log log, int maxTry) throws Exception {
		GeneralShellInput script = new GeneralShellInput("cubrid changemode " + dbName);
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

	public static ArrayList<SSHConnect> getAllNodeList(HostManager hostManager) {
		ArrayList<SSHConnect> allList = new ArrayList<SSHConnect>();
		allList.add(hostManager.getHost("master"));
		allList.addAll(getAllSlaveAndReplicaList(hostManager));
		return allList;
	}

	public static ArrayList<SSHConnect> getAllSlaveAndReplicaList(HostManager hostManager) {
		ArrayList<SSHConnect> slaveAndReplicaList = new ArrayList<SSHConnect>();
		slaveAndReplicaList.addAll(hostManager.getAllHost("slave"));
		slaveAndReplicaList.addAll(hostManager.getAllHost("replica"));
		return slaveAndReplicaList;
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

	public static int greaterThanVersion(String v1, String v2) {
		String[] a1 = v1.split("\\.");
		String[] a2 = v2.split("\\.");
		int p1, p2;
		for (int i = 0; i < 4; i++) {
			p1 = Integer.parseInt(a1[i]);
			p2 = Integer.parseInt(a2[i]);
			if (p1 == p2)
				continue;
			return (p1 > p2) ? 1 : -1;
		}
		return 0;
	}

	public static int getVersionNum(String versionId, int pos) {
		String[] arr = versionId.split("\\.");
		return Integer.parseInt(arr[pos - 1]);
	}

	public static boolean haveCharsetToCreateDB(String versionId) {
		if (greaterThanVersion(versionId, Constants.HAVE_CHARSET_10) >= 0) {
			return true;
		} else if (getVersionNum(versionId, 1) == 9 && greaterThanVersion(versionId, Constants.HAVE_CHARSET_9) >= 0) {
			return true;
		} else {
			return false;
		}
	}
}
