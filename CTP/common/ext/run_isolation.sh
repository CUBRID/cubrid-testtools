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
isolation_config_template=""
isolation_fm_test_conf="${CTP_HOME}/conf/isolation_runtime.conf"

coverage_controller_ip=$MKEY_COVERAGE_UPLOAD_IP
coverage_controller_user=$MKEY_COVERAGE_UPLOAD_USER
coverage_controller_pwd=$MKEY_COVERAGE_UPLOAD_PWD
coverage_controller_port=$MKEY_COVERAGE_UPLOAD_PORT


if [  "$BUILD_TYPE" == "coverage" ];then
        role="--role-coverage"
        coverage_controller_target_dir=${MKEY_COVERAGE_UPLOAD_DIR}/${BUILD_ID}/new
    	coverage_collaborate_url=$src_url
fi


function run_isolation()
{
   if [ -f ${CTP_HOME}/conf/isolation_template.conf ]; then
      isolation_config_template=${CTP_HOME}/conf/isolation_template.conf
   else
   	  isolation_config_template=${CTP_HOME}/conf/isolation.conf
   fi


   cp -f ${isolation_config_template} ${isolation_fm_test_conf}
   branch=$BUILD_SCENARIO_BRANCH_GIT
   category=$BUILD_SCENARIOS
  
   #init and clean log
   tmplog=$CTP_HOME/result/isolation/current_runtime_logs/runtime.log
   if [ -d "$CTP_HOME/result/isolation/current_runtime_logs" ];then
      rm $CTP_HOME/result/isolation/current_runtime_logs/* >/dev/null 2>&1 
   else
      mkdir -p $CTP_HOME/result/isolation/current_runtime_logs
   fi
   
   
   cd $CTP_HOME
   #update configuration file
   ini.sh -u "testcase_git_branch=$branch" $isolation_fm_test_conf
   ini.sh -u "test_category=$category" $isolation_fm_test_conf
   ini.sh -u "cubrid_install_role=$role" $isolation_fm_test_conf
   ini.sh -u "cubrid_additional_download_url=$coverage_collaborate_url" $isolation_fm_test_conf
   ini.sh -u "coverage_controller_ip=$coverage_controller_ip" $isolation_fm_test_conf
   ini.sh -u "coverage_controller_user=$coverage_controller_user" $isolation_fm_test_conf 
   ini.sh -u "coverage_controller_pwd=$coverage_controller_pwd" $isolation_fm_test_conf 
   ini.sh -u "coverage_controller_port=$coverage_controller_port" $isolation_fm_test_conf 
   ini.sh -u "coverage_controller_result=$coverage_controller_target_dir" $isolation_fm_test_conf 
   if [ "$BUILD_TYPE" == "coverage" ];then
   		ini.sh -u "feedback_type=file" $isolation_fm_test_conf
   fi 
   ini.sh -u "cubrid_download_url=$url" $isolation_fm_test_conf
   ini.sh -u "test_continue_yn=false" $isolation_fm_test_conf

   #execute testing
   ctp.sh isolation -c $isolation_fm_test_conf 2>&1 | tee $tmplog
   cd -
}

function run_isolation_continue()
{
   #init and clean log
   tmplog=$CTP_HOME/result/isolation/current_runtime_logs/runtime.log
   if [ ! -f $tmplog ];then
       mkdir -p $CTP_HOME/result/isolation/current_runtime_logs
   fi
   
   ini.sh -u "test_continue_yn=true" ${isolation_fm_test_conf}
   $CTP_HOME/bin/ctp.sh isolation -c ${isolation_fm_test_conf} 2>&1 | tee -a $tmplog
}


if [ "$is_continue_mode" == "YES" ];then
	run_isolation_continue
else
    run_isolation
fi

