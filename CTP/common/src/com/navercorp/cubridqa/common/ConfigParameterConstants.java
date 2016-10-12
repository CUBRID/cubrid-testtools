package com.navercorp.cubridqa.common;

public class ConfigParameterConstants {
	
	//Prefix and suffix parameters
	public static final String TEST_INSTANCE_PREFIX = "env.";
	public static final String TEST_INSTANCE_HOST_SUFFIX = "ssh.host";
	public static final String TEST_INSTANCE_PORT_SUFFIX = "ssh.port";
	public static final String TEST_INSTANCE_USER_SUFFIX = "ssh.user";
	public static final String TEST_INSTANCE_PASSWORD_SUFFIX = "ssh.pwd";
	public static final String TEST_INSTANCE_HA_SLAVE_SUFFIX = "ssh.relatedhosts";
	public static final String DEFAULT_CUBRID_PROPERTY_PREFIX = "default.cubrid";
	public static final String DEFAULT_CUBRID_BROKER1_PROPERTY_PREFIX = "default.broker1";
	public static final String DEFAULT_CUBRID_BROKER2_PROPERTY_PREFIX = "default.broker2";
	
	//Environment variables
	public static final String CTP_SKIP_UPGRADE = "CTP_SKIP_UPGRADE";
	public static final String CTP_BRANCH_NAME = "CTP_BRANCH_NAME";
	
	//CUBRID installation and configuration parameters
	public static final String CUBRID_INSTALL_ROLE = "cubrid_install_role";
	public static final String CUBRID_DOWNLOAD_URL = "cubrid_download_url";
	public static final String CUBRID_ADDITIONAL_DOWNLOAD_URL = "cubrid_additional_download_url";
	public static final String CUBRID_DB_CHARSET = "cubrid_db_charset";
	public static final String CUBRID_CUBRID_PORT_ID = "cubrid.cubrid_port_id";
	public static final String CUBRID_HA_PORT_ID = "ha.ha_port_id";
	public static final String CUBRID_CM_PORT = "cm.cm_port";
	public static final String CUBRID_BROKER1_BROKER_PORT = "broker1.BROKER_PORT";
	public static final String CUBRID_BROKER2_BROKER_PORT = "broker2.BROKER_PORT";
	
	//Test case configuration parameters
	public static final String SCENARIO = "scenario";
	public static final String TESTCASE_EXCLUDE_FROM_FILE = "testcase_exclude_from_file";
	public static final String TESTCASE_EXCLUDE_BY_MACRO = "testcase_exclude_by_macro";
	public static final String TESTCASE_GIT_BRANCH = "testcase_git_branch";
	public static final String TESTCASE_UPDATE_YES_OR_NO = "testcase_update_yn";
	public static final String TESTCASE_WORKSPACE_DIR = "testcase_workspace_dir";
	public static final String TESTCASE_TIMEOUT_IN_SECS = "testcase_timeout_in_secs";
	public static final String TESTCASE_RETRY_NUM = "testcase_retry_num";
	public static final String TESTCASE_ADDITIONAL_ANSWER = "testcase_additional_answer";
	
	//Test tool configuration parameters
	public static final String IGNORE_CORE_BY_KEYWORDS = "ignore_core_by_keywords";
	public static final String LARGE_SPACE_DIR = "large_space_dir";
	public static final String ENABLE_CHECK_DISK_SPACE_YES_OR_NO = "enable_check_disk_space_yn";
	public static final String ENABLE_SAVE_LOG_ONCE_FAIL_YES_OR_NO = "enable_save_log_once_fail_yn";
	public static final String ENABLE_SAVE_CORE_FILE_YES_OR_NO = "enable_save_core_file_yn";
	public static final String SKIP_SAVE_SUCC_TESTCASE_YES_OR_NO = "skip_save_succ_testcase_yn";
	public static final String ENABLE_STATUS_TRACE_YES_OR_NO = "enable_status_trace_yn";
	public static final String DELETE_TESTCASE_AFTER_EACH_EXECUTION_YES_OR_NO = "delete_testcase_after_each_execution_yn";
	public static final String TEST_CONTINUE_YES_OR_NO = "test_continue_yn";
	public static final String TEST_PLATFORM = "test_platform";
	public static final String TEST_CATEGORY = "test_category";
	public static final String SVN_USER = "svn_user";
	public static final String SVN_PASSWORD = "svn_pwd";
	public static final String GIT_USER = "git_user";
	public static final String GIT_PASSWORD = "git_pwd";
	public static final String GIT_EMAIL = "git_email";
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
	public static final String TEST_FAILURE_BACKUP_YES_OR_NO = "test_failure_backup_yn";
	public static final String TEST_BUILD_ID = "build_id";
	public static final String TEST_BUILD_BITS = "build_bits";
	
	
	//Test result configuration parameters
	public static final String FEEDBACK_TYPE = "feedback_type";
	public static final String FEEDBACK_DB_HOST = "feedback_db_host";
	public static final String FEEDBACK_DB_PORT = "feedback_db_port";
	public static final String FEEDBACK_DB_NAME = "feedback_db_name";
	public static final String FEEDBACK_DB_USER = "feedback_db_user";
	public static final String FEEDBACK_DB_PASSWORD = "feedback_db_pwd";
	public static final String FEEDBACK_NOTICE_QAHOME_URL = "feedback_notice_qahome_url";

}
