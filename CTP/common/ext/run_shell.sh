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
shell_config_template=""
shell_fm_test_conf="${CTP_HOME}/conf/shell_runtime.conf"

coverage_controller_ip=$MKEY_COVERAGE_UPLOAD_IP
coverage_controller_user=$MKEY_COVERAGE_UPLOAD_USER
coverage_controller_pwd=$MKEY_COVERAGE_UPLOAD_PWD
coverage_controller_port=$MKEY_COVERAGE_UPLOAD_PORT


if [  "$BUILD_TYPE" == "coverage" ];then
        role="--role-coverage"
        coverage_controller_target_dir=${MKEY_COVERAGE_UPLOAD_DIR}/${BUILD_ID}/new
    	coverage_collaborate_url=$src_url
elif [ "$BUILD_SCENARIOS" == "unittest_debug" ];then
		role="--role-unittest-debug"
elif [ "$BUILD_SCENARIOS" == "unittest" ];then
		role="--role-unittest-release"
fi


function run_shell()
{
   if [ -f ${CTP_HOME}/conf/shell_template_for_${BUILD_SCENARIOS}.conf ]; then
      shell_config_template=${CTP_HOME}/conf/shell_template_for_${BUILD_SCENARIOS}.conf
   elif [ -f ${CTP_HOME}/conf/shell_template.conf ]; then
      shell_config_template=${CTP_HOME}/conf/shell_template.conf
   fi

   cp -f ${shell_config_template} ${shell_fm_test_conf}
   branch=$BUILD_SCENARIO_BRANCH_GIT
   category=$BUILD_SCENARIOS
  
   #init and clean log
   tmplog=$CTP_HOME/result/shell/current_runtime_logs/runtime.log
   if [ -d "$CTP_HOME/result/shell/current_runtime_logs" ];then
      rm $CTP_HOME/result/shell/current_runtime_logs/* >/dev/null 2>&1 
   else
      mkdir -p $CTP_HOME/result/shell/current_runtime_logs
   fi
   
   
   cd $CTP_HOME
   #update configuration file
   ini.sh -u "testcase_git_branch=$branch" $shell_fm_test_conf
   ini.sh -u "test_category=$category" $shell_fm_test_conf
   ini.sh -u "cubrid_install_role=$role" $shell_fm_test_conf
   ini.sh -u "cubrid_additional_download_url=$coverage_collaborate_url" $shell_fm_test_conf
   ini.sh -u "coverage_controller_ip=$coverage_controller_ip" $shell_fm_test_conf
   ini.sh -u "coverage_controller_user=$coverage_controller_user" $shell_fm_test_conf 
   ini.sh -u "coverage_controller_pwd=$coverage_controller_pwd" $shell_fm_test_conf 
   ini.sh -u "coverage_controller_port=$coverage_controller_port" $shell_fm_test_conf 
   ini.sh -u "coverage_controller_result=$coverage_controller_target_dir" $shell_fm_test_conf 
   if [ "$BUILD_TYPE" == "coverage" ];then
   		ini.sh -u "feedback_type=file" $shell_fm_test_conf
   fi 
   ini.sh -u "cubrid_download_url=$url" $shell_fm_test_conf
   ini.sh -u "test_continue_yn=false" $shell_fm_test_conf

   #execute testing
   ctp.sh shell -c $shell_fm_test_conf 2>&1 | tee $tmplog
   cd -
}

function run_shell_continue()
{
   #init and clean log
   tmplog=$CTP_HOME/result/shell/current_runtime_logs/runtime.log
   if [ ! -f $tmplog ];then
       mkdir -p $CTP_HOME/result/shell/current_runtime_logs
   fi
   
   ini.sh -u "test_continue_yn=true" ${shell_fm_test_conf}
   $CTP_HOME/bin/ctp.sh shell -c ${shell_fm_test_conf} 2>&1 | tee -a $tmplog
}


function run_shell_legacy()
{
    category=$BUILD_SCENARIOS
    scenario=`ini.sh $HOME/cubrid_shell_fm/conf/main.properties main.testcase.root`
    testcase_excluded_list=`ini.sh $HOME/cubrid_shell_fm/conf/main.properties main.testcase.excluded`
    eval scenario=$scenario
    eval testcase_excluded_list=$testcase_excluded_list
    cd $HOME/cubrid_shell_fm
    sh upgrade.sh
    sh run.sh -Dmain.testcase.root=${scenario} -Dmain.testcase.excluded=${testcase_excluded_list} -Dmain.testing.category=$category -Dmain.testing.role=$role -Dmain.collaborate.url=$coverage_collaborate_url -Dmain.coverage.controller.ip=$coverage_controller_ip -Dmain.coverage.controller.user=$coverage_controller_user -Dmain.coverage.controller.pwd=$coverage_controller_pwd -Dmain.coverage.controller.result=$coverage_controller_target_dir `if [ "$BUILD_TYPE" == "coverage" ];then echo "-Dmain.feedback.type=$feedback_type";fi` $url false
    cd -
}


function run_shell_legacy_continue()
{
    cd $HOME/cubrid_shell_fm
    #execute testing
    sh upgrade.sh
    sh run_continue.sh
    cd - 
}

if [ "$is_continue_mode" == "YES" ];then
   if [ "${BUILD_IS_FROM_GIT}" == "1" ];then
	run_shell_continue
   else
	run_shell_legacy_continue
   fi
else
   if [ "${BUILD_IS_FROM_GIT}" == "1" ]; then
        run_shell
   else
        run_shell_legacy
   fi 

fi

