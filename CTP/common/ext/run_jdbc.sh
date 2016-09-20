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

ctp_jdbc_test_conf=""

function run_jdbc {
    
    if [ -f ${CTP_HOME}/conf/jdbc_template.conf ];then
	ctp_jdbc_test_conf=${CTP_HOME}/conf/jdbc_template.conf
    else
	ctp_jdbc_test_conf=${CTP_HOME}/conf/jdbc.conf
    fi

    if [ "$BUILD_TYPE" != 'general' -a "$BUILD_TYPE" != 'debug' ]; then
	echo ""
        echo "The current CTP does not support $BUILD_TYPE testing!"
	exit 
    elif [ "$BUILD_SCENARIOS" != "jdbc" -a "$BUILD_SCENARIOS" != "jdbc_debug" ]; then
	echo ""
        echo "Unknown scenario type, stop test."
        echo "Please check and re-send message."
        exit
    fi
   
    #VARIABLES
    git_repo_name="cubrid-testcases-private"

    #STEP 1: CLEAN AND INIT
    runAction jdbc_unit_git.act            #clean processes and check disk space
    tmplog=$CTP_HOME/result/jdbc/current_runtime_logs/runtime.log
    if [ -d "$CTP_HOME/result/jdbc/current_runtime_logs" ];then
        rm $CTP_HOME/result/jdbc/current_runtime_logs/* >/dev/null 2>&1
    else
        mkdir -p $CTP_HOME/result/jdbc/current_runtime_logs
    fi

    
    #STEP 2: INSTALL CUBRID
    run_cubrid_install $url

    #STEP 3: UPDATE CASES 
    run_git_update -f ${CTP_HOME}/../${git_repo_name} -b ${BUILD_SCENARIO_BRANCH_GIT}

    #STEP 4: CONFIG CTP
    ini.sh -s common -u "main.testing.category=${BUILD_SCENARIOS}" $ctp_jdbc_test_conf

    #STEP 5: EXECUTE
    ctp.sh jdbc -c $ctp_jdbc_test_conf | tee $tmplog

    #STEP 6: BACKUP LOGS
    cd $CTP_HOME/result/jdbc
    current_id=`cat $CTP_HOME/result/jdbc/current_runtime_logs/current_task_id | tail -n 1`
    timestamp=`date +'%Y.%m.%d_%H.%M.%S'`
    backup_file=`jdbc_result_${BUILD_ID}_${current_id}_${timestamp}`
    mkdir -p ${backup_file} 
    cp -rf current_runtime_logs $backup_file
    cp $ctp_jdbc_test_conf $backup_file
    tar zvcf ${backup_file}.tar.gz $backup_file
    rm -rf $backup_file
}


if [ "${BUILD_IS_FROM_GIT}" == "1" ]; then
    run_jdbc
else
    echo "The current CTP does not support legacy CUBRID build testing!"
fi
