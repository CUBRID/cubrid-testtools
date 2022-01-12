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


role=""
tmplog=""
coverage_controller_target_dir=""
coverage_collaborate_url=""
is_continue_mode=$1
cdc_repl_config_template=""
cdc_repl_fm_test_conf="${CTP_HOME}/conf/cdc_repl_runtime.conf"

coverage_controller_ip=$MKEY_COVERAGE_UPLOAD_IP
coverage_controller_user=$MKEY_COVERAGE_UPLOAD_USER
coverage_controller_pwd=$MKEY_COVERAGE_UPLOAD_PWD
coverage_controller_port=$MKEY_COVERAGE_UPLOAD_PORT


if [  "$BUILD_TYPE" == "coverage" ];then
        role="--role-coverage"
        coverage_controller_target_dir=${MKEY_COVERAGE_UPLOAD_DIR}/${BUILD_ID}/new
    	coverage_collaborate_url=$src_url
fi


function run_cdc_repl()
{
   if [ -f ${CTP_HOME}/conf/cdc_repl_template_for_${BUILD_SCENARIOS}.conf ]; then
      cdc_repl_config_template=${CTP_HOME}/conf/cdc_repl_template_for_${BUILD_SCENARIOS}.conf
   elif [ -f ${CTP_HOME}/conf/cdc_repl_template.conf ]; then
      cdc_repl_config_template=${CTP_HOME}/conf/cdc_repl_template.conf
   fi

   cp -f ${cdc_repl_config_template} ${cdc_repl_fm_test_conf}
   branch=$BUILD_SCENARIO_BRANCH_GIT
   category=$BUILD_SCENARIOS
  
   # Disk checking
   if [ -f $CTP_HOME/conf/cdc_repl.act ];then
       runAction cdc_repl.act
   else
       echo "Skip Disk Checking!"
   fi
    
   #init and clean log
   tmplog=$CTP_HOME/result/cdc_repl/current_runtime_logs/runtime.log
   if [ -d "$CTP_HOME/result/cdc_repl/current_runtime_logs" ];then
      rm $CTP_HOME/result/cdc_repl/current_runtime_logs/* >/dev/null 2>&1 
   else
      mkdir -p $CTP_HOME/result/cdc_repl/current_runtime_logs
   fi
   
   
   cd $CTP_HOME
   #update configuration file
   ini.sh -u "testcase_git_branch=$branch" $cdc_repl_fm_test_conf
   ini.sh -u "test_category=$category" $cdc_repl_fm_test_conf
   ini.sh -u "cubrid_install_role=$role" $cdc_repl_fm_test_conf
   ini.sh -u "cubrid_additional_download_url=$coverage_collaborate_url" $cdc_repl_fm_test_conf
   ini.sh -u "coverage_controller_ip=$coverage_controller_ip" $cdc_repl_fm_test_conf
   ini.sh -u "coverage_controller_user=$coverage_controller_user" $cdc_repl_fm_test_conf 
   ini.sh -u "coverage_controller_pwd=$coverage_controller_pwd" $cdc_repl_fm_test_conf 
   ini.sh -u "coverage_controller_port=$coverage_controller_port" $cdc_repl_fm_test_conf 
   ini.sh -u "coverage_controller_result=$coverage_controller_target_dir" $cdc_repl_fm_test_conf 
   if [ "$BUILD_TYPE" == "coverage" ];then
   		ini.sh -u "feedback_type=file" $cdc_repl_fm_test_conf
   fi 
   ini.sh -u "cubrid_download_url=$url" $cdc_repl_fm_test_conf
   ini.sh -u "test_continue_yn=false" $cdc_repl_fm_test_conf
   
   #Get branch of case
   testcase_path=""
   if [ "$BUILD_SCENARIOS" == "cdc_repl_ext" -o "$BUILD_SCENARIOS" == "cdc_repl_ext_debug" ];then
	testcase_path=$HOME/cubrid-testcases-private/sql
   elif [ "$BUILD_SCENARIOS" == "cdc_repl" -o "$BUILD_SCENARIOS" == "cdc_repl_debug" ];then
	testcase_path=$HOME/cubrid-testcases/sql
   fi
		
   if [ "x${testcase_path}" != "x" ];then
	#run_git_update -f $testcase_path  -b $BUILD_SCENARIO_BRANCH_GIT
	#ini.sh -u "scenario=$testcase_path" $cdc_repl_fm_test_conf
	ini.sh -u "testcase_exclude_from_file=${testcase_path}/config/daily_regression_test_exclude_list_cdc_repl.conf" $cdc_repl_fm_test_conf
   fi

   #execute testing
   ctp.sh cdc_repl -c $cdc_repl_fm_test_conf 2>&1 | tee $tmplog
   cd -
}

function run_cdc_repl_continue()
{
   # Disk checking
   if [ -f $CTP_HOME/conf/cdc_repl.act ];then
       runAction cdc_repl.act
   else
       echo "Skip Disk Checking!"
   fi
   
   #init and clean log
   tmplog=$CTP_HOME/result/cdc_repl/current_runtime_logs/runtime.log
   if [ ! -f $tmplog ];then
       mkdir -p $CTP_HOME/result/cdc_repl/current_runtime_logs
   fi
   
   ini.sh -u "test_continue_yn=true" ${cdc_repl_fm_test_conf}
   $CTP_HOME/bin/ctp.sh cdc_repl -c ${cdc_repl_fm_test_conf} 2>&1 | tee -a $tmplog
}


function run_cdc_repl_legacy()
{
    category=$BUILD_SCENARIOS
    db_charset=`getMsgValue $MKEY_TESTING_DB_CHARSET en_US`
    test_case_root=`getMsgValue $MKEY_TESTCASE_ROOT $HOME/dailyqa/$BUILD_SVN_BRANCH/`
    prefetch_mode=`getMsgValue $MKEY_TESTING_PREFETCH false`  
    # Disk checking
    if [ -f $CTP_HOME/conf/cdc_repl_legacy.act ];then
    	runAction cdc_repl_legacy.act
    else
    	echo "Skip Disk Checking!"
    fi
    
    # Update case from svn repository
    run_svn_update -f $HOME/dailyqa/$BUILD_SVN_BRANCH/sql
    run_svn_update -f $HOME/dailyqa/$BUILD_SVN_BRANCH/_24_functional_repl
    run_svn_update -f $HOME/dailyqa/$BUILD_SVN_BRANCH/config
    cd $HOME/cdc_repl_test
    sh upgrade.sh
    sh run.sh -Dmain.db.charset=${db_charset} -Dmain.testbuild.url=$url -Dmain.testcase.root=${test_case_root}  -Dmain.testcase.excluded=$HOME/dailyqa/$BUILD_SVN_BRANCH/config/cdc_replication_excluded_list -Dmain.testing.category=$category -Dmain.testing.prefetch=${prefetch_mode} -Dmain.testing.role=$role -Dmain.collaborate.url=$coverage_collaborate_url -Dmain.coverage.controller.ip=$coverage_controller_ip -Dmain.coverage.controller.user=$coverage_controller_user -Dmain.coverage.controller.pwd=$coverage_controller_pwd -Dmain.coverage.controller.result=$coverage_controller_target_dir `if [ "$BUILD_TYPE" == "coverage" ];then echo "-Dmain.feedback.type=file";fi`
    cd -
}


function run_cdc_repl_lagacy_continue()
{
    # Disk checking
    if [ -f $CTP_HOME/conf/cdc_repl_legacy.act ];then
    	runAction cdc_repl_legacy.act
    else
    	echo "Skip Disk Checking!"
    fi
    
    cd $HOME/cdc_repl_test
    #execute testing
    sh upgrade.sh
    sh run_continue.sh
    cd - 
}

if [ "$is_continue_mode" == "YES" ];then
   if [ "${BUILD_IS_FROM_GIT}" == "1" ];then
	run_cdc_repl_continue
   else
	run_cdc_repl_lagacy_continue
   fi
else
   if [ "${BUILD_IS_FROM_GIT}" == "1" ]; then
        run_cdc_repl
   else
        run_cdc_repl_legacy
   fi 
fi

