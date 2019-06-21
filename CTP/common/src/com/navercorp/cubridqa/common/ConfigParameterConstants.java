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

package com.navercorp.cubridqa.common;

public class ConfigParameterConstants {

	// Prefix and suffix parameters
	public static final String TEST_INSTANCE_PREFIX = "env.";
	public static final String TEST_INSTANCE_HOST_SUFFIX = "ssh.host";
	public static final String TEST_INSTANCE_PORT_SUFFIX = "ssh.port";
	public static final String TEST_INSTANCE_USER_SUFFIX = "ssh.user";
	public static final String TEST_INSTANCE_PASSWORD_SUFFIX = "ssh.pwd";
	public static final String TEST_INSTANCE_RELATED_HOSTS_SUFFIX = "ssh.relatedhosts";

	// Environment variables
	public static final String CTP_SKIP_UPDATE = "CTP_SKIP_UPDATE";
	public static final String CTP_BRANCH_NAME = "CTP_BRANCH_NAME";
	public static final String CTP_DEBUG_ENABLE = "CTP_DEBUG_ENABLE";
	public static final String CTP_PROXY_HOST = "CTP_PROXY_HOST";
	public static final String CTP_PROXY_PORT = "CTP_PROXY_PORT";
	public static final String CTP_PROXY_USER = "CTP_PROXY_USER";
	public static final String CTP_PROXY_PASSWORD = "CTP_PROXY_PASSWORD";
	public static final String CTP_PROXY_PRIORITY = "CTP_PROXY_PRIORITY";

	// CUBRID installation and configuration parameters
	public static final String CUBRID_INSTALL_ROLE = "cubrid_install_role";
	public static final String CUBRID_DOWNLOAD_URL = "cubrid_download_url";
	public static final String CUBRID_ADDITIONAL_DOWNLOAD_URL = "cubrid_additional_download_url";
	public static final String CUBRID_DB_CHARSET = "cubrid_db_charset";
	public static final String CUBRID_TESTDB_NAME = "cubrid_testdb_name";
	public static final String ROLE_ENGINE = "cubrid";
	public static final String ROLE_HA = "ha";
	public static final String ROLE_CM = "cm";
	public static final String ROLE_BROKER_COMMON = "brokercommon";
	public static final String ROLE_BROKER1 = "broker1";
	public static final String ROLE_BROKER2 = "broker2";
	public static final String ROLE_BROKER_AVAILABLE_PORT = "available_port";

	// Test case configuration parameters
	public static final String SCENARIO = "scenario";
	public static final String TESTCASE_EXCLUDE_FROM_FILE = "testcase_exclude_from_file";
	public static final String TESTCASE_EXCLUDE_BY_MACRO = "testcase_exclude_by_macro";
	public static final String TESTCASE_GIT_BRANCH = "testcase_git_branch";
	public static final String TESTCASE_UPDATE_YES_OR_NO = "testcase_update_yn";
	public static final String TESTCASE_WORKSPACE_DIR = "testcase_workspace_dir";
	public static final String TESTCASE_TIMEOUT_IN_SECS = "testcase_timeout_in_secs";
	public static final String TESTCASE_RETRY_NUM = "testcase_retry_num";
	public static final String TESTCASE_ADDITIONAL_ANSWER = "testcase_additional_answer";
	
	// ha_repl
	public static final String HA_SYNC_DETECT_TIMEOUT_IN_SECS = "ha_sync_detect_timeout_in_secs";
	public static final String HA_SYNC_FAILURE_RESOLVE_MODE = "ha_sync_failure_resolve_mode";
	public static final String HA_UPDATE_STATISTICS_ON_CATALOG_CLASSES_YN = "update_statistics_on_catalog_classes_yn";

	// Test tool configuration parameters
	public static final String IGNORE_CORE_BY_KEYWORDS = "ignore_core_by_keywords";
	public static final String LARGE_SPACE_DIR = "large_space_dir";
	
	public static final String ENABLE_CHECK_DISK_SPACE_YES_OR_NO = "enable_check_disk_space_yn";
	public static final String RESERVE_DISK_SPACE_SIZE = "reserve_disk_space_size";
	public static final String RESERVE_DISK_SPACE_SIZE_DEFAULT_VALUE = "2G";  //default value for param reserve_disk_space_size
	
	public static final String ENABLE_STATUS_TRACE_YES_OR_NO = "enable_status_trace_yn";
	public static final String ENABLE_SIKP_MAKE_LOCALE_YES_OR_NO = "enable_skip_make_locale_yn";
	public static final String ENABLE_SAVE_LOG_ONCE_FAIL_YES_OR_NO = "enable_save_log_once_fail_yn";
	public static final String DELETE_TESTCASE_AFTER_EACH_EXECUTION_YES_OR_NO = "delete_testcase_after_each_execution_yn";
	public static final String CLEAN_PROCESS_AFTER_EXECUTION_QUIT_YES_OR_NO = "clean_processes_after_execution_quit_yn";
	public static final String TEST_CONTINUE_YES_OR_NO = "test_continue_yn";
	public static final String TEST_PLATFORM = "test_platform";
	public static final String TEST_CATEGORY = "test_category";
	public static final String GIT_USER = "git_user";
	public static final String GIT_PASSWORD = "git_pwd";
	public static final String GIT_EMAIL = "git_email";
	public static final String SVN_USER = "svn_user";
	public static final String SVN_PASSWORD = "svn_pwd";
	public static final String TEST_OWNER_EMAIL = "owner_email";
	public static final String TEST_CC_EMAIL = "cc_email";
	public static final String AGENT_PROTOCOL = "agent_protocol";
	public static final String AGENT_LOGIN_PORT = "agent_login_port";
	public static final String AGENT_WHITELIST_HOSTS = "agent_whitelist_hosts";
	public static final String AGENT_LOGIN_USER = "agent_login_user";
	public static final String AGENT_LOGIN_PASSWORD = "agent_login_pwd";
	public static final String AGENT_LOGIN_HOME_DIR = "agent_login_home_dir";
	public static final String COVERAGE_CONTROLLER_IP = "coverage_controller_ip";
	public static final String COVERAGE_CONTROLLER_USER = "coverage_controller_user";
	public static final String COVERAGE_CONTROLLER_PASSWORD = "coverage_controller_pwd";
	public static final String COVERAGE_CONTROLLER_PORT = "coverage_controller_port";
	public static final String COVERAGE_CONTROLLER_RESULT = "coverage_controller_result";
	public static final String TEST_REBUILD_ENV_YES_OR_NO = "test_rebuild_env_yn";
	public static final String TEST_BUILD_ID = "build_id";
	public static final String TEST_BUILD_BITS = "build_bits";
	public static final String TEST_INTERFACE_TYPE = "test_interface_type";

	// Test result configuration parameters
	public static final String FEEDBACK_TYPE = "feedback_type";
	public static final String FEEDBACK_DB_HOST = "feedback_db_host";
	public static final String FEEDBACK_DB_PORT = "feedback_db_port";
	public static final String FEEDBACK_DB_NAME = "feedback_db_name";
	public static final String FEEDBACK_DB_USER = "feedback_db_user";
	public static final String FEEDBACK_DB_PASSWORD = "feedback_db_pwd";
	public static final String FEEDBACK_DB_SKIP_SAVE_SUCC_TESTCASE_YES_OR_NO = "feedback_db_skip_save_succ_testcase_yn";
	public static final String FEEDBACK_NOTICE_QAHOME_URL = "feedback_notice_qahome_url";

}
