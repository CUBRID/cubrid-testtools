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
ha_repl_config_template=""
ha_repl_fm_test_conf="${CTP_HOME}/conf/ha_repl_runtime.conf"

coverage_controller_ip=$MKEY_COVERAGE_UPLOAD_IP
coverage_controller_user=$MKEY_COVERAGE_UPLOAD_USER
coverage_controller_pwd=$MKEY_COVERAGE_UPLOAD_PWD
coverage_controller_port=$MKEY_COVERAGE_UPLOAD_PORT


if [  "$BUILD_TYPE" == "coverage" ];then
        role="--role-coverage"
        coverage_controller_target_dir=${MKEY_COVERAGE_UPLOAD_DIR}/${BUILD_ID}/new
    	coverage_collaborate_url=$src_url
fi


function run_ha_repl()
{
   if [ -f ${CTP_HOME}/conf/ha_repl_template_for_${BUILD_SCENARIOS}.conf ]; then
      ha_repl_config_template=${CTP_HOME}/conf/ha_repl_template_for_${BUILD_SCENARIOS}.conf
   elif [ -f ${CTP_HOME}/conf/ha_repl_template.conf ]; then
      ha_repl_config_template=${CTP_HOME}/conf/ha_repl_template.conf
   fi

   cp -f ${ha_repl_config_template} ${ha_repl_fm_test_conf}
   branch=$BUILD_SCENARIO_BRANCH_GIT
   category=$BUILD_SCENARIOS
  
   # Disk checking
   if [ -f $CTP_HOME/conf/${BUILD_SCENARIOS}.act ];then
       runAction ${BUILD_SCENARIOS}.act
   elif [ -f $CTP_HOME/conf/ha_repl.act ]
       runAction ha_repl.act
   else
       echo "Skip Disk Checking!"
   fi
    
   #init and clean log
   tmplog=$CTP_HOME/result/ha_repl/current_runtime_logs/runtime.log
   if [ -d "$CTP_HOME/result/ha_repl/current_runtime_logs" ];then
      rm $CTP_HOME/result/ha_repl/current_runtime_logs/* >/dev/null 2>&1 
   else
      mkdir -p $CTP_HOME/result/ha_repl/current_runtime_logs
   fi
   
   
   cd $CTP_HOME
   #update configuration file
   ini.sh -u "testcase_git_branch=$branch" $ha_repl_fm_test_conf
   ini.sh -u "test_category=$category" $ha_repl_fm_test_conf
   ini.sh -u "cubrid_install_role=$role" $ha_repl_fm_test_conf
   ini.sh -u "cubrid_additional_download_url=$coverage_collaborate_url" $ha_repl_fm_test_conf
   ini.sh -u "coverage_controller_ip=$coverage_controller_ip" $ha_repl_fm_test_conf
   ini.sh -u "coverage_controller_user=$coverage_controller_user" $ha_repl_fm_test_conf 
   ini.sh -u "coverage_controller_pwd=$coverage_controller_pwd" $ha_repl_fm_test_conf 
   ini.sh -u "coverage_controller_port=$coverage_controller_port" $ha_repl_fm_test_conf 
   ini.sh -u "coverage_controller_result=$coverage_controller_target_dir" $ha_repl_fm_test_conf 
   if [ "$BUILD_TYPE" == "coverage" ];then
   		ini.sh -u "feedback_type=file" $ha_repl_fm_test_conf
   fi 
   ini.sh -u "cubrid_download_url=$url" $ha_repl_fm_test_conf
   ini.sh -u "test_continue_yn=false" $ha_repl_fm_test_conf
   
   #Get branch of case
   testcase_path=""
   if [ "$BUILD_SCENARIOS" == "ha_repl_ext" -o "$BUILD_SCENARIOS" == "ha_repl_ext_debug" ];then
		testcase_path=$HOME/cubrid-testcases-private/sql
   elif [ "$BUILD_SCENARIOS" == "ha_repl" -o "$BUILD_SCENARIOS" == "ha_repl_debug" ];then
		testcase_path=$HOME/cubrid-testcases/sql
   fi
		
   if [ "x${testcase_path}" != "x" ];then
	    run_git_update -f $testcase_path  -b $BUILD_SCENARIO_BRANCH_GIT
	    ini.sh -u "scenario=$testcase_path" $ha_repl_fm_test_conf
	    ini.sh -u "testcase_exclude_from_file=${testcase_path}/config/daily_regression_test_exclude_list_ha_repl.conf" $ha_repl_fm_test_conf
   fi

   #execute testing
   ctp.sh ha_repl -c $ha_repl_fm_test_conf 2>&1 | tee $tmplog
   cd -
}

function run_ha_repl_continue()
{
   # Disk checking
   if [ -f $CTP_HOME/conf/${BUILD_SCENARIOS}.act ];then
       runAction ${BUILD_SCENARIOS}.act
   elif [ -f $CTP_HOME/conf/ha_repl.act ]
       runAction ha_repl.act
   else
       echo "Skip Disk Checking!"
   fi
   
   #init and clean log
   tmplog=$CTP_HOME/result/ha_repl/current_runtime_logs/runtime.log
   if [ ! -f $tmplog ];then
       mkdir -p $CTP_HOME/result/ha_repl/current_runtime_logs
   fi
   
   ini.sh -u "test_continue_yn=true" ${ha_repl_fm_test_conf}
   $CTP_HOME/bin/ctp.sh ha_repl -c ${ha_repl_fm_test_conf} 2>&1 | tee -a $tmplog
}


function run_ha_repl_legacy()
{
    category=$BUILD_SCENARIOS
    # Disk checking
    if [ -f $CTP_HOME/conf/${BUILD_SCENARIOS}.act ];then
    	runAction ${BUILD_SCENARIOS}.act
    elif [ -f $CTP_HOME/conf/ha_repl.act ]
    	runAction ha_repl.act
    else
    	echo "Skip Disk Checking!"
    fi
    
    # Update case from svn repository
    run_svn_update -f $HOME/dailyqa/$BUILD_SVN_BRANCH/sql
    run_svn_update -f $HOME/dailyqa/$BUILD_SVN_BRANCH/_24_functional_repl
    run_svn_update -f $HOME/dailyqa/$BUILD_SVN_BRANCH/config
    cd $HOME/ha_repl_test
    sh upgrade.sh
    sh run.sh -Dmain.testing.category=$category -Dmain.testing.role=$role -Dmain.collaborate.url=$coverage_collaborate_url -Dmain.coverage.controller.ip=$coverage_controller_ip -Dmain.coverage.controller.user=$coverage_controller_user -Dmain.coverage.controller.pwd=$coverage_controller_pwd -Dmain.coverage.controller.result=$coverage_controller_target_dir `if [ "$BUILD_TYPE" == "coverage" ];then echo "-Dmain.feedback.type=$feedback_type";fi` $url false
    cd -
}


function run_ha_repl_lagacy_continue()
{
	# Disk checking
    if [ -f $CTP_HOME/conf/${BUILD_SCENARIOS}.act ];then
    	runAction ${BUILD_SCENARIOS}.act
    elif [ -f $CTP_HOME/conf/ha_repl.act ]
    	runAction ha_repl.act
    else
    	echo "Skip Disk Checking!"
    fi
    
    cd $HOME/ha_repl_test
    #execute testing
    sh upgrade.sh
    sh run_continue.sh
    cd - 
}

if [ "$is_continue_mode" == "YES" ];then
   if [ "${BUILD_IS_FROM_GIT}" == "1" ];then
		run_ha_repl_continue
   else
		run_ha_repl_lagacy_continue
   fi
else
   if [ "${BUILD_IS_FROM_GIT}" == "1" ]; then
        run_ha_repl
   else
        run_ha_repl_legacy
   fi 
fi

