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
    sql_by_cci_config_template=""

    #STEP 1: CLEAN
    runAction sql_medium_site.act           #clean processes and check disk space
    rm -f $tmplog >/dev/null 2>&1           #clean log
    rm -f $tmptxt >/dev/null 2>&1 

    #STEP 2: INSTALL CUBRID
    if [ "$BUILD_TYPE" != 'general' -a "$BUILD_TYPE" != 'debug' ]; then
        role="--role-${BUILD_TYPE}"
    fi
    run_cubrid_install $role $url $src_url
    close_shard_service

    # STEP 3: CONFIGURE CTP
    if [ -f ${CTP_HOME}/conf/sql_by_cci_template_for_${BUILD_SCENARIOS}.conf ]; then
      sql_by_cci_config_template=${CTP_HOME}/conf/sql_by_cci_template_for_${BUILD_SCENARIOS}.conf
    elif [ -f ${CTP_HOME}/conf/sql_by_cci_template.conf ]; then
      sql_by_cci_config_template=${CTP_HOME}/conf/sql_by_cci_template.conf
    fi
    cp $sql_by_cci_config_template ${ctp_test_conf}

    if [ "$BUILD_SCENARIOS" == "medium" -o "$BUILD_SCENARIOS" == "medium_debug" ]; then
        ctp_type="sql_by_cci"
        git_repo_name=cubrid-testcases
        ctp_scenario=medium
    elif [ "$BUILD_SCENARIOS" == "sql_by_cci" -o "$BUILD_SCENARIOS" == "sql_by_cci_debug" ]; then
        ctp_type="sql_by_cci"
        git_repo_name=cubrid-testcases
        ctp_scenario=sql
    elif [ "$BUILD_SCENARIOS" == "sql_by_cci_ext" -o "$BUILD_SCENARIOS" == "sql_by_cci_ext_debug" ]; then
        ctp_type="sql_by_cci"
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
    ini.sh -s sql ${ctp_test_conf} test_category $BUILD_SCENARIOS

    #set supported param
    ini.sh -s "sql/cubrid.conf" ${ctp_test_conf} | util_filter_supported_parameters.sh > $tmptxt
    ini.sh -s "sql/cubrid.conf" ${ctp_test_conf} --update-from-file=$tmptxt --clear-first
    ini.sh -s "sql/cubrid.conf" ${ctp_test_conf} test_mode yes
    ini.sh -s "sql/cubrid_ha.conf" ${ctp_test_conf} | util_filter_supported_parameters.sh > $tmptxt
    ini.sh -s "sql/cubrid_ha.conf" ${ctp_test_conf} --update-from-file=$tmptxt --clear-first

    # STEP 4: execute test
    ctp.sh ${ctp_type} -c ${ctp_test_conf} | tee $tmplog

    # STEP 5: UPLOAD TEST RESULTS TO QA HOMEPAGE
    if [ "$BUILD_TYPE" != "coverage" ]
    then
        testResultPath=`cat $tmplog|grep "^Test Result Directory"|awk -F ':' '{print $NF}'|tr -d " "`
        testResultName="`basename ${testResultPath}`"
        (cd $testResultPath/..; upload_to_dailysrv "./$testResultName" "./qa_repository/function_cci_for_sql/y`date +%Y`/m`date +%-m`/$testResultName")

        if [ `cat $tmplog |grep '^CORE_FILE:' | wc -l` -gt 0 ]; then
            timestamp=`echo $testResultName|awk -F $BUILD_SCENARIOS '{print $2}'|cut -d _ -f 2`
            core_dirname=${BUILD_SCENARIOS}_${timestamp}
            core_path=${core_backup_root}/${testResultName}/${core_dirname}
            mkdir -p ${core_path}
            
            cat $tmplog |grep '^CORE_FILE:'|awk -F ':' '{print $NF}'|tr -d " " | xargs -i analyzer.sh {} > ${core_path}/${core_dirname}_corestacks.txt
            (cd ${core_path}/..; upload_to_dailysrv ${core_dirname} "./qaresult_en/web/test_error/function/${core_dirname}")

            cat $tmplog |grep '^CORE_FILE:'|awk -F ':' '{print $NF}'|tr -d " " | xargs -i mv {} ${core_path}/..
            cp -rf $CUBRID ${core_path}/..
            (cd ${core_backup_root}; tar -czvf ${testResultName}.tar.gz ${testResultName}; if [ "${testResultName}" ];then rm -rf ${testResultName};fi)
        fi
    else
        run_coverage_collect_and_upload -h "${CTP_HOME}/../build" -n "$BUILD_ID" -c "$BUILD_SCENARIOS" -user "$MKEY_COVERAGE_UPLOAD_USER" -pwd "$MKEY_COVERAGE_UPLOAD_PWD" -host "$MKEY_COVERAGE_UPLOAD_IP" -to "${MKEY_COVERAGE_UPLOAD_DIR}/${BUILD_ID}/new" -port "${DAILYQA_SSH_PORT_DEFAULT}"
    fi

    #STEP 6: final clean
    rm -f $tmptxt
    rm -r $tmplog
}

function run_sql_legacy {
    # CONSTANTS
    tmplog=$HOME/ccqt/tmp.log
    if [ "$BUILD_SCENARIOS" ] && [ "${BUILD_SCENARIOS}" == "sql_by_cci" ];then
	BUILD_SCENARIOS="sql"
    fi
    
    #STEP 1: CLEAN
    runAction sql_by_cci.act
    (cd $HOME/ccqt/scripts; sh ./upgrade.sh)
    (cd $HOME/dailyqa/$BUILD_SVN_BRANCH/scenario/$BUILD_SCENARIOS
     svn up
     cd $HOME/dailyqa/$BUILD_SVN_BRANCH
     
     file_excludedlist=config/sql_by_cci_excluded_list
     if [ "$file_excludedlist" ];then
	cd $HOME/dailyqa/$BUILD_SVN_BRANCH/config
	svn up
	cd $HOME/dailyqa/$BUILD_SVN_BRANCH
	cat $file_excludedlist|grep scenario|awk '{print "rm -rf " $0}' > ./del_list.sh
	sh del_list.sh
	rm del_list.sh
     fi     
    )   
 
    #STEP 2: INSTALL CUBRID
    echo "Install Test Build"   
    if [ "$BUILD_TYPE" != 'general' -a "$BUILD_TYPE" != 'debug' ]; then
        role="--role-${BUILD_TYPE}"
    fi
    run_cubrid_install $role $url $src_url
    close_shard_service
    echo "Finish Test Build Installation"

    #STEP 3: EXECUTE TEST
    if [ "$BUILD_TYPE" != "coverage" ]
    then
	sh $HOME/ccqt/ccqt.sh -n $url -v $BUILD_SVN_BRANCH -s $BUILD_SCENARIOS -t $BUILD_BIT -y -q 2>&1 |tee $tmplog
    else 
	sh $HOME/ccqt/ccqt.sh -n $url -c $src_url -v $BUILD_SVN_BRANCH -s $BUILD_SCENARIOS -t $BUILD_BIT -y -q 2>&1 |tee $tmplog
    fi        

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
