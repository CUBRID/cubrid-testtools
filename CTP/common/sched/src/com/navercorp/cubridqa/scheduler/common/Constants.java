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
package com.navercorp.cubridqa.scheduler.common;

import com.navercorp.cubridqa.common.CommonUtils;

public class Constants {
	public static final String LINE_SEPARATOR = System.getProperty("line.separator");

	public final static String CTP_HOME = CommonUtils.getEnvInFile("CTP_HOME");

	public static final String MSG_ID_DATE_FM = "yyMMdd-HHmmss-SSS";

	public static final String QUEUE_CUBRID_QA_SQL_LINUX = "QUEUE_CUBRID_QA_SQL_LINUX";
	public static final String QUEUE_CUBRID_QA_SQL_PERF_LINUX = "QUEUE_CUBRID_QA_SQL_PERF_LINUX";
	public static final String QUEUE_CUBRID_QA_SQL_WIN32 = "QUEUE_CUBRID_QA_SQL_WIN32";
	public static final String QUEUE_CUBRID_QA_SQL_WIN64 = "QUEUE_CUBRID_QA_SQL_WIN64";

	public static final String QUEUE_CUBRID_QA_SQL_CCI_LINUX = "QUEUE_CUBRID_QA_SQL_CCI_LINUX";

	public static final String QUEUE_CUBRID_QA_SHELL_LINUX = "QUEUE_CUBRID_QA_SHELL_LINUX";
	public static final String QUEUE_CUBRID_QA_SHELL_WIN32 = "QUEUE_CUBRID_QA_SHELL_WIN32";
	public static final String QUEUE_CUBRID_QA_SHELL_WIN64 = "QUEUE_CUBRID_QA_SHELL_WIN64";

	public static final String QUEUE_CUBRID_QA_CCI_LINUX = "QUEUE_CUBRID_QA_CCI_LINUX";
	public static final String QUEUE_CUBRID_QA_SHELL_HA_LINUX = "QUEUE_CUBRID_QA_SHELL_HA_LINUX";
	public static final String QUEUE_CUBRID_QA_JDBC_UNITTEST_LINUX = "QUEUE_CUBRID_QA_JDBC_UNITTEST_LINUX";

	public static final String QUEUE_CUBRID_QA_YCSB_LINUX = "QUEUE_CUBRID_QA_YCSB_LINUX";
	public static final String QUEUE_CUBRID_QA_SYSBENCH_LINUX = "QUEUE_CUBRID_QA_SYSBENCH_LINUX";
	public static final String QUEUE_CUBRID_QA_DOTS_LINUX = "QUEUE_CUBRID_QA_DOTS_LINUX";
	public static final String QUEUE_CUBRID_QA_TPCC_LINUX = "QUEUE_CUBRID_QA_TPCC_LINUX";
	public static final String QUEUE_CUBRID_QA_TPCW_LINUX = "QUEUE_CUBRID_QA_TPCW_LINUX";
	public static final String QUEUE_CUBRID_QA_HA_REPL_LINUX = "QUEUE_CUBRID_QA_HA_REPL_LINUX";
	public static final String QUEUE_CUBRID_QA_HA_REPL_PERF_LINUX = "QUEUE_CUBRID_QA_HA_REPL_PERF_LINUX";

	public static final String QUEUE_CUBRID_QA_MEMORY_LEAK_LINUX = "QUEUE_CUBRID_QA_MEMORY_LEAK_LINUX";

	public static final String QUEUE_CUBRID_QA_NBD_LINUX = "QUEUE_CUBRID_QA_NBD_LINUX";
	public static final String QUEUE_CUBRID_QA_NBD_WIN32 = "QUEUE_CUBRID_QA_NBD_WIN32";
	public static final String QUEUE_CUBRID_QA_NBD_WIN64 = "QUEUE_CUBRID_QA_NBD_WIN64";

	public static final String QUEUE_CUBRID_QA_BASIC_PERF_LINUX = "QUEUE_CUBRID_QA_BASIC_PERF_LINUX";

	public static final String QUEUE_CUBRID_QA_SQL_AIX_64 = "QUEUE_CUBRID_QA_SQL_AIX_64";
	public static final String QUEUE_CUBRID_QA_SHELL_AIX_64 = "QUEUE_CUBRID_QA_SHELL_AIX_64";
	public static final String QUEUE_CUBRID_QA_NBD_AIX_64 = "QUEUE_CUBRID_QA_NBD_AIX_64";
	public static final String QUEUE_CUBRID_QA_CCI_AIX_64 = "QUEUE_CUBRID_QA_CCI_AIX_64";

	public static final String QUEUE_CUBRID_QA_COMPAT_JDBC_SQL_DRIVER = "QUEUE_CUBRID_QA_COMPAT_JDBC_SQL_DRIVER";
	public static final String QUEUE_CUBRID_QA_COMPAT_JDBC_SQL_SERVER_64 = "QUEUE_CUBRID_QA_COMPAT_JDBC_SQL_SERVER_64";
	public static final String QUEUE_CUBRID_QA_COMPAT_CCI_SHELL_DRIVER_64 = "QUEUE_CUBRID_QA_COMPAT_CCI_SHELL_DRIVER_64";
	public static final String QUEUE_CUBRID_QA_COMPAT_CCI_SHELL_SERVER_64 = "QUEUE_CUBRID_QA_COMPAT_CCI_SHELL_SERVER_64";

	public static final String QUEUE_CUBRID_QA_COMPAT_DBIMG_INS = "QUEUE_CUBRID_QA_COMPAT_DBIMG_INS";
	public static final String QUEUE_CUBRID_QA_COMPAT_DBIMG_SRV = "QUEUE_CUBRID_QA_COMPAT_DBIMG_SRV";

	public static final String QUEUE_CUBRID_QA_CAS4MYSQL_NTEST = "QUEUE_CUBRID_QA_CAS4MYSQL_NTEST";
	public static final String QUEUE_CUBRID_QA_CAS4ORACLE_NTEST = "QUEUE_CUBRID_QA_CAS4ORACLE_NTEST";
	public static final String QUEUE_CUBRID_QA_CAS4MYSQL_UNITTEST = "QUEUE_CUBRID_QA_CAS4MYSQL_UNITTEST";
	public static final String QUEUE_CUBRID_QA_CAS4ORACLE_UNITTEST = "QUEUE_CUBRID_QA_CAS4ORACLE_UNITTEST";

	public static final String QUEUE_CUBRID_QA_SHARD_LINUX = "QUEUE_CUBRID_QA_SHARD_LINUX";

	public static final String QUEUE_CUBRID_QA_I18N_LINUX_64 = "QUEUE_CUBRID_QA_I18N_LINUX_64";

	public static final String MSG_MSGID = "MSG_ID";

	public static final String MSG_BUILD_URLS = "BUILD_URLS";
	public static final String MSG_BUILD_URLS_KR = "BUILD_URLS_KR";
	public static final String MSG_BUILD_URLS_KR_REPO1 = "BUILD_URLS_KR_REPO1";

	public static final String MSG_BUILD_URLS_CNT = "BUILD_URLS_CNT";
	public static final String MSG_BUILD_BIT = "BUILD_BIT";
	public static final String MSG_BUILD_CREATE_TIME = "BUILD_CREATE_TIME";
	public static final String MSG_BUILD_SEND_TIME = "BUILD_SEND_TIME";
	public static final String MSG_BUILD_SEND_DELAY = "BUILD_SEND_DELAY";
	public static final String MSG_BUILD_ID = "BUILD_ID";
	public static final String MSG_BUILD_STORE_ID = "BUILD_STORE_ID";
	public static final String MSG_BUILD_SCENARIOS = "BUILD_SCENARIOS";
	public static final String MSG_BUILD_SVN_BRANCH = "BUILD_SVN_BRANCH";
	public static final String MSG_BUILD_SVN_BRANCH_NEW = "BUILD_SVN_BRANCH_NEW";
	public static final String MSG_BUILD_TYPE = "BUILD_TYPE";
	public static final String MSG_BUILD_ABSOLUTE_PATH = "BUILD_ABSOLUTE_PATH";
	public static final String MSG_BUILD_IS_FROM_GIT = "BUILD_IS_FROM_GIT";
	public static final String MSG_BUILD_SCENARIO_BRANCH_GIT = "BUILD_SCENARIO_BRANCH_GIT";

	public static final String MSG_BUILD_PACKAGE_PATTERN = "BUILD_PACKAGE_PATTERN";
	public static final String MSG_BUILD_GENERATE_MSG_WAY = "BUILD_GENERATE_MSG_WAY";

	public static final String MSG_COMPAT_BUILD_ID = "COMPAT_BUILD_ID";
	public static final String MSG_COMPAT_BUILD_URLS = "COMPAT_BUILD_URLS";
	public static final String MSG_COMPAT_BUILD_URLS_KR = "COMPAT_BUILD_URLS_KR";
	public static final String MSG_COMPAT_TEST_CATAGORY = "COMPAT_TEST_CATAGORY";
	public static final String MSG_I18N_TEST_CATAGORY = "I18N_TEST_CATAGORY";
	public static final String MSG_MSG_FILEID = "MSG_FILEID";

	public static final String MSG_DBIMG_BUILD_ID = "DBIMG_BUILD_ID";
	public static final String MSG_DBIMG_BUILD_URLS = "DBIMG_BUILD_URLS";
	public static final String MSG_DBIMG_BUILD_URLS_KR = "DBIMG_BUILD_URLS_KR";
	public static final String MSG_DBIMG_TEST_CATAGORY = "DBIMG_TEST_CATAGORY";

	public static final String MSG_DB_CHARSET = "DB_CHARSET";
	public static final String MSG_RESET_CONFIG_FILE = "RESET_CONFIG_FILE";

	public static final int BUILD_TYPE_SERVER_SH_LINUX_X86_64 = 1;
	public static final int BUILD_TYPE_SERVER_SH_LINUX_X86_64_DEBUG = 2;
	public static final int BUILD_TYPE_SERVER_SH_LINUX_I386 = 3;
	public static final int BUILD_TYPE_SERVER_ZIP_WINDOWS_X64 = 4;
	public static final int BUILD_TYPE_SERVER_ZIP_WINDOWS_X86 = 5;
	public static final int BUILD_TYPE_SERVER_SH_AIX_64 = 6;
	public static final int BUILD_TYPE_JDBC_JAR = 7;
	public static final int BUILD_TYPE_SERVER_SH_LINUX_X86_64_AND_JDBC = 8;

}
