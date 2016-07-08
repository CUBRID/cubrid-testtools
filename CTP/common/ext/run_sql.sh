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
set -x

is_continue_mode=$1

function run_sql {
    # CONSTANTS
    tmplog=${CTP_HOME}/tmp.log   
    tmptxt=${CTP_HOME}/tmp.txt
    core_backup_root=${CTP_HOME}/../corebackup
    ctp_test_conf=${CTP_HOME}/conf/sql_runtime.conf

    # VARIABLES
    git_repo_name="" 
    ctp_type=""
    ctp_scenario=""

    #STEP 1: CLEAN
    runAction sql_medium_git.act            #clean processes and check disk space
    rm -f $tmplog >/dev/null 2>&1           #clean log
    rm -f $tmptxt >/dev/null 2>&1 

    #STEP 2: INSTALL CUBRID
    if [ "$BUILD_TYPE" != 'general' -a "$BUILD_TYPE" != 'debug' ]; then
        role="--role-${BUILD_TYPE}"
    fi
    run_cubrid_install $role $url $src_url
    close_shard_service

    # STEP 3: CONFIGURE CTP
    cp conf/sql_local.conf ${ctp_test_conf}
    if [ "$BUILD_SCENARIOS" == "medium" -o "$BUILD_SCENARIOS" == "medium_debug" ]; then
        ctp_type="medium"
        git_repo_name=cubrid-testcases
        ctp_scenario=medium
    elif [ "$BUILD_SCENARIOS" == "sql" -o "$BUILD_SCENARIOS" == "sql_debug" ]; then
        ctp_type="sql"
        git_repo_name=cubrid-testcases
        ctp_scenario=sql
    elif [ "$BUILD_SCENARIOS" == "sql_ext" -o "$BUILD_SCENARIOS" == "sql_ext_debug" ]; then
        ctp_type="sql"
        git_repo_name=cubrid-testcases-private
        ctp_scenario=sql
    else
        echo "Unknown scenario type, stop test."
        echo "Please check and re-send message."
        exit
    fi

    run_git_update -f ${CTP_HOME}/../${git_repo_name} -b ${BUILD_SCENARIO_BRANCH_GIT}

    ini.sh -s sql ${ctp_test_conf} scenario '${CTP_HOME}'/../${git_repo_name}/$ctp_scenario
    ini.sh -s sql ${ctp_test_conf} data_file '${CTP_HOME}'/../${git_repo_name}/$ctp_scenario/files
    ini.sh -s sql ${ctp_test_conf} category_alias $BUILD_SCENARIOS

    #set supported param
    ini.sh -s "sql/cubrid.conf" ${ctp_test_conf} | util_filter_supported_parameters.sh > $tmptxt
    ini.sh -s "sql/cubrid.conf" ${ctp_test_conf} --update-from-file=$tmptxt --clear-first
    ini.sh -s "sql/cubrid.conf" ${ctp_test_conf} test_mode yes
    ini.sh -s "sql/cubrid_ha.conf" ${ctp_test_conf} | util_filter_supported_parameters.sh > $tmptxt
    ini.sh -s "sql/cubrid_ha.conf" ${ctp_test_conf} --update-from-file=$tmptxt --clear-first

    # STEP 4: execute test
    export _JAVA_OPTIONS=-Dfile.encoding=utf8
    ctp.sh ${ctp_type} -c ${ctp_test_conf} | tee $tmplog

    # STEP 5: UPLOAD TEST RESULTS TO QA HOMEPAGE
    if [ "$BUILD_TYPE" != "coverage" ]
    then
        testResultPath=`cat $tmplog|grep "^Test Result Directory"|awk -F ':' '{print $NF}'|tr -d " "`
        testResultName="`basename ${testResultPath}`"
        (cd $testResultPath/..; upload_to_dailysrv "./$testResultName" "./qa_repository/function/y`date +%Y`/m`date +%-m`/$testResultName")

        if [ `cat $tmplog |grep '^CORE_FILE:' | wc -l` -gt 0 ]; then
            core_dirname=${BUILD_SCENARIOS}_`date '+%Y%m%d%H%M%S'`
            core_path=${core_backup_root}/${testResultName}/${core_dirname}
            mkdir -p ${core_path}
            
            cat $tmplog |grep '^CORE_FILE:'|awk -F ':' '{print $NF}'|tr -d " " | xargs -i analyzer.sh {} > ${core_path}/${core_dirname}_corestacks.txt
            (cd ${core_path}/..; upload_to_dailysrv ${core_dirname} "./qaresult_en/web/test_error/function/${core_dirname}")

            cat $tmplog |grep '^CORE_FILE:'|awk -F ':' '{print $NF}'|tr -d " " | xargs -i mv {} ${core_path}/..
            cp -rf $CUBRID ${core_path}/..
            (cd ${core_backup_root}; tar -czvf ${testResultName}.tar.gz ${testResultName}; rm -rf ${testResultName})
        fi
    else
        run_coverage_collect_and_upload -h "${CTP_HOME}/../build" -n "$BUILD_ID" -c "$BUILD_SCENARIOS" -user "$MKEY_COVERAGE_UPLOAD_USER" -pwd "$MKEY_COVERAGE_UPLOAD_PWD" -host "$MKEY_COVERAGE_UPLOAD_IP" -to "${MKEY_COVERAGE_UPLOAD_DIR}/${BUILD_ID}/new" -port "${DAILYQA_SSH_PORT_DEFAULT}"
    fi

    #STEP 6: final clean
    rm -f $tmptxt
    rm -r $tmplog
}

function run_sql_legacy {

    #STEP 1: CLEAN
    echo "start to update CQT tools"
    runAction sql_medium_site.act

    (cd $QA_REPOSITORY; sh upgrade.sh)

    #STEP 2: INSTALL CUBRID
    echo "Install Test Build"   
    if [ "$BUILD_TYPE" != 'general' -a "$BUILD_TYPE" != 'debug' ]; then
        role="--role-${BUILD_TYPE}"
    fi
    run_cubrid_install $role $url $src_url
    close_shard_service
    broker_port=`ini.sh -s "%BROKER1" $CUBRID/conf/cubrid_broker.conf BROKER_PORT`
    sed -i "s/:[0-9]*:/:$broker_port:/" $QA_REPOSITORY/configuration/Function_Db/*.xml
    if [ "$OS" == "Windows_NT" ]; then
        sed -i "s@<cubridHome>.*</cubridHome>@<cubridHome>${CUBRID/\\/\\\\}</cubridHome>@g" $QA_REPOSITORY/configuration/System.xml
        sed -i "s@<jdbcPath>.*</jdbcPath>@<jdbcPath>${CUBRID/\\/\\\\}\\\\jdbc\\\\cubrid_jdbc.jar</jdbcPath>@g" $QA_REPOSITORY/configuration/System.xml
    else
        sed -i "s@<cubridHome>.*</cubridHome>@<cubridHome>${CUBRID}</cubridHome>@g" $QA_REPOSITORY/configuration/System.xml
        sed -i "s@<jdbcPath>.*</jdbcPath>@<jdbcPath>${CUBRID}/jdbc/cubrid_jdbc.jar</jdbcPath>@g" $QA_REPOSITORY/configuration/System.xml
    fi
    echo "Finish Test Build Installation"

    #STEP 3: EXECUTE TEST
    exec_script_file="sh $QA_REPOSITORY/qatool_bin/console/scripts/cqt.sh"
    if [ "$OS" == "Windows_NT" ]; then
        exec_script_file="sh $QA_REPOSITORY/qatool_bin/console/scripts/runWinTest.sh -config_file test_win.xml "
    fi
    
    echo "Start SQL Test On Linux"   
    export PROPERTIES_PATH=$QA_REPOSITORY/qatool_bin/qamanager
    if [ "$BUILD_SCENARIOS" == "site" ]
    then
        export _JAVA_OPTIONS=-Dfile.encoding=euckr
    else
        export _JAVA_OPTIONS=-Dfile.encoding=utf8
    fi
            
    ${exec_script_file} -h $CUBRID -v ${BUILD_SVN_BRANCH_NEW} -s $BUILD_SCENARIOS -t $BUILD_BIT -x -random_port
        
    echo "Finish SQL Test On Linux"
    if [ "$BUILD_TYPE" != "coverage" ]
    then
        for testResultPath in `cat $HOME/dailyqa/win_cqt_test.log|grep "Result Root Dir"|awk -F 'Dir:' '{print $NF}'|tr -d " "`
        do
            testResultName="`basename ${testResultPath}`"
            (cd $testResultPath/..; upload_to_dailysrv "$testResultName" "./qa_repository/function/y`date +%Y`/m`date +%-m`/$testResultName")
        done
    else
        run_coverage_collect_and_upload -h "${HOME}/build" -n "$BUILD_ID" -c "$BUILD_SCENARIOS" -user "$MKEY_COVERAGE_UPLOAD_USER" -pwd "$MKEY_COVERAGE_UPLOAD_PWD" -host "$MKEY_COVERAGE_UPLOAD_IP" -to "${MKEY_COVERAGE_UPLOAD_DIR}/${BUILD_ID}/new" -port "${DAILYQA_SSH_PORT_DEFAULT}"
    fi        

    #STEP 4: FINAL CLEAN
    rm -rf ${CTP_HOME}/../dailyqa/${BUILD_SVN_BRANCH_NEW}/result/logs/*
}

function close_shard_service {
    shard_service=`ini.sh -s "%shard1" $CUBRID/conf/cubrid_broker.conf SERVICE` 
    if [ "$shard_service" = "ON" ]
    then
        ini.sh -s "%shard1" $CUBRID/conf/cubrid_broker.conf SERVICE OFF 
    fi
}

if [ "${is_continue_mode}" == "YES" ]; then
    echo SQL test does not support CONTINUE mode
else
    if [ "${BUILD_IS_FROM_GIT}" == "1" ]; then
        run_sql
    else
        run_sql_legacy
    fi
fi
