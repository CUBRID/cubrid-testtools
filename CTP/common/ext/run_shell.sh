#!/bin/bash
# 
# Copyright (c) 2016, Search Solution Corporation. All rights reserved.
# 
# Redistribution and use in source and binary forms, with or without 
# modification, are permitted provided that the following conditions are met:
# 
#   * Redistributions of source code must retain the above copyright notice, 
#     this list of conditions and the following disclaimer.
# 
#   * Redistributions in binary form must reproduce the above copyright 
#     notice, this list of conditions and the following disclaimer in 
#     the documentation and/or other materials provided with the distribution.
# 
#   * Neither the name of the copyright holder nor the names of its contributors may be used to endorse or promote products 
#     derived from this software without specific prior written permission.
# 
# THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, 
# INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE 
# DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, 
# SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR 
# SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
# WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE 
# USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE. 
#

#init conf for shell
branch=""
category=""
db_charset=""
testcase_root=""
testcase_timeout=""
excluded_list=""
fail_case_retry_count=""
feedback_type="database"
tmplog=""
is_continue_mode=$1

svn_user=""
svn_pwd=""
cubrid_common_url=""

coverage_controller_ip=""
coverage_controller_user=""
coverage_controller_pwd=""
coverage_controller_port=""
coverage_controller_target_dir=""
coverage_collaborate_url=""

ctp_test_conf=${CTP_HOME}/conf/shell_runtime.conf
ctp_common_conf=${CTP_HOME}/conf/dailyqa.conf

svn_user=`ini.sh $ctp_common_conf "svn.user"`
svn_pwd=`ini.sh $ctp_common_conf "svn.pwd"`
cubrid_common_url=`ini.sh $ctp_common_conf "cubrid.commom.url"`

coverage_controller_ip=$MKEY_COVERAGE_UPLOAD_IP
coverage_controller_user=$MKEY_COVERAGE_UPLOAD_USER
coverage_controller_pwd=$MKEY_COVERAGE_UPLOAD_PWD
coverage_controller_port=$MKEY_COVERAGE_UPLOAD_PORT
testcase_timeout=`getMsgValue $MKEY_TESTCASE_TIMEOUT 7200`
fail_case_retry_count=`getMsgValue $MKEY_MAX_RETRY_COUNT 0`
db_charset=`getMsgValue $MKEY_TESTING_DEFAULT_CHARSET en_US`

if [  "$BUILD_TYPE" == "coverage" ];then
        role="--role-coverage"
        coverage_controller_target_dir=${MKEY_COVERAGE_UPLOAD_DIR}/${BUILD_ID}/new
        feedback_type="file"
	coverage_collaborate_url=$src_url
fi


if [ $BUILD_IS_FROM_GIT -eq 1 ];then
	branch=$BUILD_SCENARIO_BRANCH_GIT
	category=$BUILD_SCENARIOS
	testcase_root=cubrid-testcases-private-ex/shell	
	excluded_list=cubrid-testcases-private-ex/shell/config/daily_regression_test_excluded_list_linux.conf	
	cd $CTP_HOME

	#init and clean log
	tmplog=$CTP_HOME/runtime.log
	rm $tmplog >/dev/null 2>&1 

	cp conf/shell.conf $ctp_test_conf
	
	#update configuration file
	ini.sh -u "main.testcase.root=$testcase_root" $ctp_test_conf
	ini.sh -u "main.testcase.branch_git=$branch" $ctp_test_conf
	ini.sh -u "main.testcase.excluded=$excluded_list" $ctp_test_conf
	ini.sh -u "main.testing.category=$category" $ctp_test_conf
	ini.sh -u "main.testing.role=$role" $ctp_test_conf
	ini.sh -u "main.collaborate.url=$coverage_collaborate_url" $ctp_test_conf
	ini.sh -u "main.coverage.controller.ip=$coverage_controller_ip" $ctp_test_conf
	ini.sh -u "main.coverage.controller.user=$coverage_controller_user" $ctp_test_conf 
	ini.sh -u "main.coverage.controller.pwd=$coverage_controller_pwd" $ctp_test_conf 
	ini.sh -u "main.coverage.controller.port=$coverage_controller_port" $ctp_test_conf 
	ini.sh -u "main.coverage.controller.result=$coverage_controller_target_dir" $ctp_test_conf 
	ini.sh -u "main.feedback.type=$feedback_type" $ctp_test_conf 
	ini.sh -u "main.testcase.timeout=$testcase_timeout" $ctp_test_conf 
	ini.sh -u "max.retry.count=$fail_case_retry_count" $ctp_test_conf 
	ini.sh -u "main.testing.default_charset=$db_charset" $ctp_test_conf 
	ini.sh -u "main.testbuild.url=$url" $ctp_test_conf
	if [ "$is_continue_mode" == "YES" ];then 
		ini.sh -u "main.mode.continue=true" $ctp_test_conf
	fi	

	#execute testing
	ctp.sh shell -c $ctp_test_conf | tee $tmplog
else
	testcase_root=dailyqa/$BUILD_SVN_BRANCH_NEW/scenario/shell
	excluded_list=dailyqa/$BUILD_SVN_BRANCH_NEW/config/linux_shell_excluded_list
	category=$BUILD_SCENARIOS

	#init and clean log
	tmplog=$HOME/cubrid_shell_fm/runtime.log
	rm $tmplog >/dev/null 2>&
	cd $HOME/cubrid_shell_fm
	continue_mode=false
	if [ "$is_continue_mode" == "YES" ];then	
		continue_mode=true
	fi

	#execute testing
	svnup upgrade.sh
	sh upgrade.sh
	sh run.sh -Dmain.testcase.root=$testcase_root -Dmain.testcase.branch_git="" -Dmain.testcase.excluded=$excluded_list -Dmain.testing.category=$category -Dmain.testing.role=$role -Dmain.mode.continue=$continue_mode -Dmain.collaborate.url=$coverage_collaborate_url -Dmain.coverage.controller.ip=$coverage_controller_ip -Dmain.coverage.controller.user=$coverage_controller_user -Dmain.coverage.controller.pwd=$coverage_controller_pwd -Dmain.coverage.controller.result=$coverage_controller_target_dir -Dmain.feedback.type=$feedback_type -Dmain.testcase.timeout=$testcase_timeout -Dmax.retry.count=$fail_case_retry_count -Dmain.testing.default_charset=$db_charset -Dmain.svn.user=$svn_user -Dmain.svn.pwd=$svn_pwd -Dcubrid.common.url=$cubrid_common_url $url false | tee $tmplog	
fi






