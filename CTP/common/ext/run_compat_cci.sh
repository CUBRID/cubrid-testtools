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
#constants
TEST_RUNTIME_CONF="${CTP_HOME}/conf/shell_runtime.conf"
TEST_RESULT_DIR=$CTP_HOME/result/shell/current_runtime_logs

#todo remove this file
exclude_file=""

is_continue_mode=$1

function clean() {
   # Disk checking
   if [ -f $CTP_HOME/conf/cci_comapt.act ];then
      runAction cci_compat.act
   else
      echo "Skip Disk Checking!"
   fi
}

function install_cubrid() {
    #installation
    if [ "${COMPAT_TEST_CATAGORY##*_}" == "S64"  ]; then
        run_cubrid_install -d $url $com_url
    elif [ "${COMPAT_TEST_CATAGORY##*_}" == "D64" ]; then
        run_cubrid_install -s $url $com_url
    fi
}

function run_shell() {

    clean
    install_cubrid

    test_config_template=${CTP_HOME}/conf/shell_template_for_${BUILD_SCENARIOS}.conf
    if [ ! -f ${test_config_template} ]; then
        test_config_template=${CTP_HOME}/conf/shell_template.conf
    fi
    if [ ! -f ${test_config_template} ]; then
        echo ERROR: shell configuration file does not exist. Please check it.
        exit
    fi
    cp -f ${test_config_template} ${TEST_RUNTIME_CONF}

    #init and clean log
    if [ -d "${TEST_RESULT_DIR}" ];then
        rm ${TEST_RESULT_DIR}/*
    else
        mkdir -p ${TEST_RESULT_DIR}
    fi
   
    #get branch and path of test cases and exclude file
    if [ "${COMPAT_TEST_CATAGORY##*_}" == "S64" ]; then
        branch=$COMPAT_BUILD_SCENARIO_BRANCH_GIT
        if [ "$BUILD_IS_FROM_GIT" == "1" ];then
           exclude_branch=$BUILD_SCENARIO_BRANCH_GIT
           exclude_file_dir=$HOME/cubrid-testcases-private/interface/CCI/shell/config/daily_regression_test_exclude_list_compatibility
           run_git_update -f $HOME/cubrid-testcases-private -b $exclude_branch
           get_best_version_for_exclude_file "${exclude_file_dir}" "$COMPAT_TEST_CATAGORY"
           fileName=${exclude_file##*/}
           cp -f $exclude_file ${TEST_RESULT_DIR}/$fileName
           exclude_file=${TEST_RESULT_DIR}/$fileName
        elif [ "$BUILD_IS_FROM_GIT" == "0" ];then
           exclude_branch=$BUILD_SVN_BRANCH_NEW
           exclude_file_dir=$HOME/dailyqa/$BUILD_SVN_BRANCH_NEW/config
           run_svn_update -f $exclude_file_dir
           get_best_version_for_exclude_file "${exclude_file_dir}" "$COMPAT_TEST_CATAGORY"
        fi
    elif [ "${COMPAT_TEST_CATAGORY##*_}" == "D64" ]; then
        branch=$BUILD_SCENARIO_BRANCH_GIT
        exclude_file_dir=$HOME/cubrid-testcases-private/interface/CCI/shell/config/daily_regression_test_exclude_list_compatibility
        get_best_version_for_exclude_file "${exclude_file_dir}" "$COMPAT_TEST_CATAGORY"
    fi

    #update configuration file
    ini.sh -u "cubrid_download_url=" $TEST_RUNTIME_CONF
    ini.sh -u "scenario=$HOME/cubrid-testcases-private/interface/CCI/shell/_20_cci" $TEST_RUNTIME_CONF
    ini.sh -u "testcase_git_branch=$branch" $TEST_RUNTIME_CONF
    ini.sh -u "test_category=$COMPAT_TEST_CATAGORY" $TEST_RUNTIME_CONF
    ini.sh -u "test_continue_yn=false" $TEST_RUNTIME_CONF 
    if [ -e $exclude_file ];then
        ini.sh -u "testcase_exclude_from_file=$exclude_file" $TEST_RUNTIME_CONF
    fi

    #execute testing
    ctp.sh shell -c $TEST_RUNTIME_CONF 2>&1 | tee ${TEST_RESULT_DIR}/runtime.log
}

function run_shell_continue() {
    clean
    install_cubrid

    ini.sh -u "test_continue_yn=true" ${TEST_RUNTIME_CONF}
    ctp.sh shell -c ${TEST_RUNTIME_CONF} 2>&1 | tee -a ${result_dir}/runtime.log
}

function run_shell_legacy() {
    clean
    install_cubrid

    curDir=`pwd`
    category=$COMPAT_TEST_CATAGORY
    (cd $QA_REPOSITORY; sh upgrade.sh)
    
    if [ ! -d $QA_REPOSITORY/temp ]
    then
        mkdir $QA_REPOSITORY/temp
    fi
    tmplog=$QA_REPOSITORY/temp/tmp.log
    if [ -f $tmplog ];then
        rm -f $tmplog
    fi

    #close shard
    shard_service=`ini.sh -s "%shard1" $CUBRID/conf/cubrid_broker.conf SERVICE`
    if [ "$shard_service" = "ON" ]
    then
        ini.sh -s "%shard1" $CUBRID/conf/cubrid_broker.conf SERVICE OFF
    fi

    #get branch and path of test cases and exclude file
    if [ "${COMPAT_TEST_CATAGORY##*_}" == "S64" ]; then
        branch=$COMPAT_BUILD_SCENARIO_BRANCH_GIT
        if [ "$BUILD_IS_FROM_GIT" == "1" ];then
           exclude_branch=$BUILD_SCENARIO_BRANCH_GIT
           exclude_file_dir=$HOME/cubrid-testcases-private/interface/CCI/shell/config/daily_regression_test_exclude_list_compatibility
           run_git_update -f $HOME/cubrid-testcases-private -b $exclude_branch
        elif [ "$BUILD_IS_FROM_GIT" == "0" ];then
           exclude_file_dir=$HOME/dailyqa/$BUILD_SVN_BRANCH_NEW/config
           run_svn_update -f $exclude_file_dir
        fi
    elif [ "${COMPAT_TEST_CATAGORY##*_}" == "D64" ]; then
        branch=$BUILD_SVN_BRANCH_NEW
        exclude_file_dir=$HOME/dailyqa/$branch/config
        run_svn_update -f $exclude_file_dir
    fi
    get_best_version_for_exclude_file "${exclude_file_dir}" "$COMPAT_TEST_CATAGORY"

    #update cases
    cd $HOME/dailyqa
    run_git_update -f $branch -b $branch
    if [ ! -d $branch/scenario ]
    then
        mkdir $branch/scenario
    fi

    #mv scenario/interface/CCI/shell to scenario/shell structure
    cd $HOME/dailyqa/$branch/scenario
    bkname="shell_"`date +%s`"_bk"
    cci="$HOME/dailyqa/$branch/interface/CCI/shell"
    if [ -L shell ]
    then
         rm -f shell
    else
         mv shell $bkname
    fi
    ln -s $cci shell

    #exclude cases
    cat $exclude_file|grep "^shell"|xargs -i rm -rf {}

    #runCCI
    exec_script_file="sh $QA_REPOSITORY/qatool_bin/console/scripts/cqt.sh"
    ${exec_script_file} -h $CUBRID -v $branch -s shell -q -no-i18n -x |tee -a $tmplog

    #upload test results
    logPath=`cat $tmplog|grep RESULT_DIR|awk -F ':' '{print $NF}'|tr -d " "`
    testResultPath=`cat $logPath|grep "Result Root Dir:"|awk -F ':' '{print $NF}'|tr -d " "`
    testResultName="`basename ${testResultPath}`"
    cd $testResultPath
    typ=`echo $testResultName|awk -F '_' '{print $3}'`
    sed -i "s/<catPath>${typ}</<catPath>$category</g" summary.info
    cd ..
    if [ "${COMPAT_TEST_CATAGORY##*_}" == "S64" ]; then
        prefix=`echo $testResultName|awk -F '_' '{print $1"_"$2"_"$3"_"$4"_"$5}'`
        newName="${prefix}"_"${BUILD_ID}"
        name=`echo $newName|tr -d " "`
        rm -rf $name
        mv $testResultName $name
    elif [ "${COMPAT_TEST_CATAGORY##*_}" == "D64" ]; then
        name=$testResultName
    fi
    upload_to_dailysrv "$name" "./qa_repository/function/y`date +%Y`/m`date +%-m`/$name"
    cd $curDir
}

#todo simplify this file
function get_best_version_for_exclude_file() {
   excludeFileDir=$1
   initVer=$2   
   prefix=`echo $initVer|awk -F_ '{print $3}'`
   no3=`echo "$prefix"|awk -F. '{print $3}'`
   if [ ! -n "$no3" ]
   then
        last_element=10
        while [ $last_element -ge 0 ]
        do
            versioninfo=${prefix}.$last_element
            lastVer=`echo ${initVer}|sed "s/\$prefix/\$versioninfo/g"`
            exclude_file=${excludeFileDir}/${lastVer}_excluded_list
            if [ -e "$exclude_file" ]
            then
                break
            fi
            let 'last_element -=1'
        done
   else
        exclude_file=${excludeFileDir}/${initVer}_excluded_list
   fi
}

function get_server_version() {
    if [ "${COMPAT_TEST_CATAGORY##*_}" == "S64" ]; then
        echo $COMPAT_BUILD_ID
    elif [ "${COMPAT_TEST_CATAGORY##*_}" == "D64" ]; then
        echo $BUILD_ID
    fi
}

function is_server_ge_10_0 () {
    fst_num=`get_server_version | awk -F '.' '{print $1}'`
    if [ $fst_num -ge 10 ];then
        echo YES
    else
        echo NO
    fi
}

if [ "$is_continue_mode" == "YES" ];then
    if [ `is_server_ge_10_0` = "YES" ];then
        run_shell_continue
    else
        echo WARN: Legacy test does not support CONTINUE mode
    fi
else
   if [ `is_server_ge_10_0` = "YES" ];then
        run_shell
   else
        run_shell_legacy
   fi
fi
