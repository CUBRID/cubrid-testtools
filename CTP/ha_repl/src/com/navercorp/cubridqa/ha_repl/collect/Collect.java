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
package com.navercorp.cubridqa.ha_repl.collect;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.navercorp.cubridqa.ha_repl.common.CommonUtils;
import com.navercorp.cubridqa.ha_repl.common.Constants;

/*

csql> ;sc ha_repl_task

=== <Help: Schema of a Class> ===


 <Class Name> 

     ha_repl_task

 <Attributes> 

     main_id              INTEGER AUTO_INCREMENT  NOT NULL
     start_time           TIMESTAMP
     end_time             TIMESTAMP
     test_build           CHARACTER VARYING(20)
     test_bits            CHARACTER VARYING(5)
     test_package         CHARACTER VARYING(300)
     status               CHARACTER VARYING(20)
     num_total            INTEGER
     run_mode             CHARACTER VARYING(10)

 <Constraints> 

     PRIMARY KEY pk_ha_repl_task_main_id ON ha_repl_task (main_id)


Current transaction has been committed.
csql> 

csql> ;sc ha_repl_item

=== <Help: Schema of a Class> ===


 <Class Name> 

     ha_repl_item

 <Attributes> 

     item_id              INTEGER AUTO_INCREMENT  NOT NULL
     main_id              INTEGER
     test_filename        CHARACTER VARYING(300)
     start_time           TIMESTAMP
     end_time             TIMESTAMP
     succ_flag            CHARACTER VARYING(10)

 <Constraints> 

     PRIMARY KEY pk_ha_repl_item_item_id ON ha_repl_item (item_id)


Current transaction has been committed.
csql> 

 */
@Deprecated
public class Collect {
	private String DRIVER = "cubrid.jdbc.driver.CUBRIDDriver";
	private String URL = null;// "jdbc:cubrid:10.34.63.29:33000:basic:::";
	private String USER = null;// "dba";
	private String PASS = null;
	private Statement Stmt = null;
	private String RUN_MODE = null;
	private Connection conn = null;
	private String ha_repl_task = "ha_repl_task";
	private String ha_repl_item = "ha_repl_item";

	private Statement getCurrentStmt() throws SQLException, InstantiationException, IllegalAccessException, ClassNotFoundException {
		if (conn == null || Stmt == null || conn.isClosed()) {
			Class.forName(DRIVER).newInstance();
			conn = DriverManager.getConnection(URL, USER, PASS);
			Stmt = conn.createStatement(ResultSet.TYPE_SCROLL_SENSITIVE, ResultSet.CONCUR_UPDATABLE);
		}
		return Stmt;
	}

	public Collect() throws Exception {
		Properties props = CommonUtils.getProperties(CommonUtils.concatFile(Constants.DIR_CONF, "main.properties"));
		URL = props.getProperty("main.logdb.url");
		USER = props.getProperty("main.logdb.user");
		PASS = props.getProperty("main.logdb.passwd");
		RUN_MODE = props.getProperty("main.collect_mode");
		if (URL == null || USER == null || PASS == null || RUN_MODE == null) {
			throw new Exception("Collect get logdb or run_mode info failed!");
		}

	}

	public void close() {
		try {
			if (Stmt != null)
				Stmt.close();
		} catch (Exception e) {
			e.printStackTrace();
		}

		try {
			if (conn != null)
				conn.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private String getTime(String strTime) {
		if (strTime == null || strTime.trim().length() == 0) {
			return "";
		}
		Pattern p = Pattern.compile("^[1-9][0-9]{3}-[0-9]{2}-[0-9]{2} \\d\\d:\\d\\d:\\d\\d");
		Matcher m = p.matcher(strTime);
		if (m.find()) {
			return m.group();
		}
		return "";
	}

	private String getVersion(String cubridPackageUrl) {
		if (cubridPackageUrl == null) {
			return "";
		}
		Pattern p = Pattern.compile("\\d.\\d.\\d.\\d{4}");
		Matcher m = p.matcher(cubridPackageUrl);
		if (m.find()) {
			return m.group();
		} else {
			return "";
		}

	}

	private String getBits(String cubridPackageUrl) {
		if (cubridPackageUrl == null) {
			return "";
		}
		Pattern p = Pattern.compile("x86_\\d\\d");
		Matcher m = p.matcher(cubridPackageUrl);
		if (m.find()) {
			return "64bit";
		}
		p = Pattern.compile("i386");
		m = p.matcher(cubridPackageUrl);
		if (m.find()) {
			return "32bit";
		}
		return "";

	}

	public synchronized int createTask(String cubridPackageUrl, int totalTestFile) throws Exception {

		if (cubridPackageUrl == null) {
			return -1;
		}
		int taskId = -1;
		try {

			String currtentTime = getTime(new Timestamp(System.currentTimeMillis()).toString());
			String sql = String.format(
					"insert into " + ha_repl_task + " (" + "start_time, " + "test_build, " + "test_bits, " + "test_package, " + "status, " + "num_total,  "
							+ "run_mode) values (TO_TIMESTAMP('%s'),'%s','%s','%s','%s',%d,'%s')",
					currtentTime, getVersion(cubridPackageUrl), getBits(cubridPackageUrl), cubridPackageUrl, "INPROGRESS", totalTestFile, RUN_MODE);
			getCurrentStmt().executeUpdate(sql);
			sql = String.format("select current_val from db_serial where name='ha_repl_task_ai_main_id'");
			ResultSet resultSet = getCurrentStmt().executeQuery(sql);
			if (resultSet != null && resultSet.next()) {
				taskId = resultSet.getInt(1);
			} else {
				return -1;
			}

		} catch (Exception e) {
			e.printStackTrace();
			return -1;
		}
		return taskId; // ha_repl_task.main_id as taskId
	}

	public synchronized void createItem(int taskId, String testFile, Timestamp startTime, Timestamp endTime, String succFlag) throws Exception {
		if (testFile == null || startTime == null || endTime == null || succFlag == null || testFile.trim().length() == 0 || succFlag.trim().length() == 0) {
			throw new Exception("CreateItem the input param null or length==0.");
		}
		String sql = String.format(
				"insert into " + ha_repl_item + " (" + "main_id, " + "test_filename, " + "start_time, " + "end_time, " + "succ_flag)" + "values (%d,'%s',TO_TIMESTAMP('%s'),TO_TIMESTAMP('%s'),'%s')",
				taskId, testFile, getTime(startTime.toString()), getTime(endTime.toString()), succFlag);
		getCurrentStmt().executeUpdate(sql);

	}

	public synchronized void finishTask(int taskId) throws SQLException, InstantiationException, IllegalAccessException, ClassNotFoundException {
		String currtentTime = getTime(new Timestamp(System.currentTimeMillis()).toString());
		String sql = String.format("update " + ha_repl_task + " set end_time=TO_TIMESTAMP('%s') , status='FIN' where main_id=%d", currtentTime, taskId);
		getCurrentStmt().executeUpdate(sql);
	}

	public static void main(String[] args) throws Exception {
		// 1 thread ,1 log ,1 taskId
		try {
			Collect collect = new Collect();
			int taskId = collect.createTask("http://10.34.64.209/daily_build/8.4.9.0380/drop/CUBRID-8.4.9.0380-linux.x86_64.sh", 2);
			if (taskId == -1)
				return;
			Thread.sleep(2000);
			collect.createItem(taskId, "/public/1.test", new Timestamp(System.currentTimeMillis()), new Timestamp(System.currentTimeMillis() + 2000), "SUCC");
			Thread.sleep(2000);
			collect.finishTask(taskId);
		} catch (Exception e) {
			e.printStackTrace();
		}

		// 6 threads ,6 logs , 1 taskId
		try {
			ArrayList<String> envList = new ArrayList<String>();
			envList.add("a");
			envList.add("b");
			envList.add("c");
			envList.add("d");
			envList.add("e");
			envList.add("f");

			ExecutorService pool;
			Collect collect = new Collect();
			final int taskId = collect.createTask("http://10.34.64.209/daily_build/8.4.9.0380/drop/CUBRID-8.4.9.0380-linux.x86_64.sh", 2);
			if (taskId == -1)
				return;
			pool = Executors.newFixedThreadPool(envList.size());

			for (final String envId : envList) {
				pool.execute(new Runnable() {

					@Override
					public void run() {
						try {
							Collect collect = new Collect();
							for (int i = 0; i < 100; i++) {
								collect.createItem(taskId, "/public/" + envId + "_" + i + ".test", new Timestamp(System.currentTimeMillis()), new Timestamp(System.currentTimeMillis() + 2000), "SUCC");
							}
							for (int i = 0; i < 100; i++) {
								collect.createItem(taskId, "/public/" + envId + "_" + i + ".test", new Timestamp(System.currentTimeMillis()), new Timestamp(System.currentTimeMillis() + 2000), "FAIL");
							}
							collect.finishTask(taskId);
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
				});
			}
			pool.shutdown();
			pool.awaitTermination(Integer.MAX_VALUE, TimeUnit.DAYS);

			pool = null;
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
}
