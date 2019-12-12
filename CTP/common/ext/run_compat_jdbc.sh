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
TEST_RUNTIME_CONF="${CTP_HOME}/conf/sql_runtime.conf"

#init
tmpdir=""
tmplog=""
tmptxt=""

#todo remove this file
exclude_file=""
patch_file=""

is_continue_mode=$1

function clean() {
   # Disk checking
   if [ -f $CTP_HOME/conf/jdbc_comapt.act ];then
      runAction jdbc_compat.act
   else
      echo "Skip Disk Checking!"
   fi
}

function install_cubrid() {
    #installation
    if [ "${COMPAT_TEST_CATAGORY##*_}" == "S64"  ]; then
        run_cubrid_install -d $url $com_url
    elif [ "${COMPAT_TEST_CATAGORY##*_}" == "D" ]; then
        run_cubrid_install -s $url $com_url
    fi
}

function run_sql() {

    curdir=`pwd`
    category=$COMPAT_TEST_CATAGORY

    # init
    tmplog=${CTP_HOME}/tmp.log
    tmptxt=${CTP_HOME}/tmp.txt

    # VARIABLES
    git_repo_name=""
    ctp_type=""
    ctp_scenario=""
    
    clean
    install_cubrid

    test_config_template=${CTP_HOME}/conf/sql_template_for_${BUILD_SCENARIOS}.conf
    if [ ! -f ${test_config_template} ]; then
        test_config_template=${CTP_HOME}/conf/sql_template.conf
    fi
    if [ ! -f ${test_config_template} ]; then
        echo ERROR: shell configuration file does not exist. Please check it.
        exit
    fi
    cp -f ${test_config_template} ${TEST_RUNTIME_CONF}
 
    if [ "$COMPAT_BUILD_SCENARIOS" == "medium" ];then
        ctp_type="medium"
        git_repo_name=cubrid-testcases
        ctp_scenario=medium
    elif [ "$COMPAT_BUILD_SCENARIOS" == "sql" ];then
        ctp_type="sql"
        git_repo_name=cubrid-testcases
        ctp_scenario=sql
    elif [ "$COMPAT_BUILD_SCENARIOS" == "sql_ext" ];then
        ctp_type="sql"
        git_repo_name=cubrid-testcases-private
        ctp_scenario=sql
    else
        echo "Unknown scenario type, stop test."
        echo "Please check and re-send message."
        exit
    fi

    #init and clean log
    temp=`date +'%Y%m%d%s'` 
    tmpdir=${CTP_HOME}/patch_files_${temp}
    mkdir -p ${tmpdir}
   
    #get branch and path of test cases and exclude file
    if [ "${COMPAT_TEST_CATAGORY##*_}" == "S64" ]; then
        branch=$COMPAT_BUILD_SCENARIO_BRANCH_GIT
        if [ "$BUILD_IS_FROM_GIT" == "1" ];then
           exclude_branch=$BUILD_SCENARIO_BRANCH_GIT
           exclude_file_dir=$HOME/${git_repo_name}/${ctp_scenario}/config/daily_regression_test_exclude_list_compatibility
           run_git_update -f $HOME/${git_repo_name} -b $exclude_branch
        elif [ "$BUILD_IS_FROM_GIT" == "0" ];then
           exclude_branch=$BUILD_SVN_BRANCH_NEW
           exclude_file_dir=$HOME/dailyqa/$BUILD_SVN_BRANCH_NEW/config
           run_svn_update -f $exclude_file_dir    
        fi
    elif [ "${COMPAT_TEST_CATAGORY##*_}" == "D" ]; then
        branch=$BUILD_SCENARIO_BRANCH_GIT
        exclude_file_dir=$HOME/${git_repo_name}/${ctp_scenario}/config/daily_regression_test_exclude_list_compatibility
    fi
    get_best_version_for_exclude_patch_file "${exclude_file_dir}" "$COMPAT_TEST_CATAGORY"

    #exclude cases and do patch for some case
    cd ${CTP_HOME}/../${git_repo_name}
    run_git_update -f . -b $branch
    git status .
   
    fileName=${exclude_file##*/}
    fileName2=${patch_file##*/}
    cp -f $exclude_file $tmpdir/$fileName
    cp -f $patch_file $tmpdir/$fileName2
    exclude_file=$tmpdir/$fileName
    patch_file=$tmpdir/$fileName2
    if [ ` cat ${exclude_file}|grep "^scenario"|wc -l` -ge 1 ];then
         sed -i 's/scenario\///g' $exclude_file
    fi

    cd ${ctp_scenario} 
    if [ -s $patch_file ] 
    then
        patch -p0 -f < $patch_file
        patch_re=`echo $?`
    else
        patch_re=0 
    fi
    cd ..
    if [ -s $exclude_file ] ;then
       cat $exclude_file|grep "^${ctp_scenario}"|xargs -i rm -rf {}
    fi
    git status .
    cd ${CTP_HOME}/..

    ini.sh -s sql ${TEST_RUNTIME_CONF} scenario '${CTP_HOME}'/../${git_repo_name}/$ctp_scenario
    ini.sh -s sql ${TEST_RUNTIME_CONF} data_file '${CTP_HOME}'/../${git_repo_name}/$ctp_scenario/files
    ini.sh -s sql ${TEST_RUNTIME_CONF} test_category $COMPAT_TEST_CATAGORY
    #set supported param
    ini.sh -s "sql/cubrid.conf" ${TEST_RUNTIME_CONF} | util_filter_supported_parameters.sh > $tmptxt
    ini.sh -s "sql/cubrid.conf" ${TEST_RUNTIME_CONF} --update-from-file=$tmptxt --clear-first
    ini.sh -s "sql/cubrid.conf" ${TEST_RUNTIME_CONF} test_mode yes
    ini.sh -s "sql/cubrid_ha.conf" ${TEST_RUNTIME_CONF} | util_filter_supported_parameters.sh > $tmptxt
    ini.sh -s "sql/cubrid_ha.conf" ${TEST_RUNTIME_CONF} --update-from-file=$tmptxt --clear-first

    #execute testing
    export _JAVA_OPTIONS=-Dfile.encoding=utf8
    ctp.sh ${ctp_type} -c ${TEST_RUNTIME_CONF} | tee $tmplog
    
    #upload test results
    testResultPath=`cat $tmplog|grep "^Test Result Directory"|awk -F ':' '{print $NF}'|tr -d " "`
    testResultName="`basename ${testResultPath}`"
    cd $testResultPath/..
    #if it is driver test,rename the test result name 
    if [ "${COMPAT_TEST_CATAGORY##*_}" == "S64" ]; then
        prefix=`echo $testResultName|awk -F '_' '{print $1"_"$2"_"$3"_"$4"_"$5}'`
        newName="${prefix}"_"${BUILD_ID}"
        name=`echo $newName|tr -d " "`
        rm -rf $name
        mv $testResultName $name
    elif [ "${COMPAT_TEST_CATAGORY##*_}" == "D" ]; then
        name=$testResultName
    fi
    upload_to_dailysrv "$name" "./qa_repository/function/y`date +%Y`/m`date +%-m`/$name"
    if [ $patch_re -eq 0 -a -d $tmpdir ]
    then
       cd ${CTP_HOME}
       rm -rf $tmpdir
    fi
    cd $curdir
}

function run_sql_legacy() {
    curDir=`pwd`
    tmplog=$QA_REPOSITORY/temp/tmp.log
    tmpdir=$QA_REPOSITORY/temp
    category=$COMPAT_TEST_CATAGORY

    # VARIABLES
    cqt_scenario=""
    exclude_git_repo_name=""

    clean
    install_cubrid

    if [ "$COMPAT_BUILD_SCENARIOS" == "medium" ];then
        cqt_scenario=medium
        exclude_git_repo_name=cubrid-testcases
    elif [ "$COMPAT_BUILD_SCENARIOS" == "sql" ];then
        cqt_scenario=sql
        exclude_git_repo_name=cubrid-testcases
    elif [ "$COMPAT_BUILD_SCENARIOS" == "sql_ext" ];then
        cqt_scenario=sql
        exclude_git_repo_name=cubrid-testcases-private
    elif [ "$COMPAT_BUILD_SCENARIOS" == "site" ];then
        cqt_scenario=site
    else
        echo "Unknown scenario type, stop test."
        echo "Please check and re-send message."
        exit
    fi

    (cd $QA_REPOSITORY; sh upgrade.sh)
    if [ -f $tmplog ];then
        rm -f $tmplog
    fi

    if [ ! -d "$QA_REPOSITORY/temp" ];then
       mkdir -p $QA_REPOSITORY/temp
    fi
   

    #close shard 
    shard_service=`ini.sh -s "%shard1" $CUBRID/conf/cubrid_broker.conf SERVICE`
    if [ "$shard_service" = "ON" ]
    then
        ini.sh -s "%shard1" $CUBRID/conf/cubrid_broker.conf SERVICE OFF
    fi
    sed -i "s@<cubridHome>.*</cubridHome>@<cubridHome>${CUBRID/\\/\\\\}</cubridHome>@g" $QA_REPOSITORY/configuration/System.xml
    sed -i "s@<jdbcPath>.*</jdbcPath>@<jdbcPath>${CUBRID/\\/\\\\}\\\\jdbc\\\\cubrid_jdbc.jar</jdbcPath>@g" $QA_REPOSITORY/configuration/System.xml
  
    #get branch and path of test cases and exclude file
    if [ "${COMPAT_TEST_CATAGORY##*_}" == "S64" ]; then
        branch=$COMPAT_BUILD_SVN_BRANCH
        if [ "$BUILD_IS_FROM_GIT" == "1" ];then
           exclude_branch=$BUILD_SCENARIO_BRANCH_GIT
           exclude_file_dir=$HOME/${exclude_git_repo_name}/${cqt_scenario}/config/daily_regression_test_exclude_list_compatibility
           run_git_update -f $HOME/${exclude_git_repo_name} -b $exclude_branch
        elif [ "$BUILD_IS_FROM_GIT" == "0" ];then
           exclude_file_dir=$HOME/dailyqa/$BUILD_SVN_BRANCH_NEW/config
           run_svn_update -f $exclude_file_dir
        fi
    elif [ "${COMPAT_TEST_CATAGORY##*_}" == "D" ]; then
        branch=$BUILD_SVN_BRANCH_NEW
        exclude_file_dir=$HOME/dailyqa/$branch/config
        run_svn_update -f $exclude_file_dir
    fi
    get_best_version_for_exclude_patch_file "${exclude_file_dir}" "$COMPAT_TEST_CATAGORY"
    fileName=${exclude_file##*/}
    fileName2=${patch_file##*/}
    cp -f $exclude_file $tmpdir/$fileName
    cp -f $patch_file $tmpdir/$fileName2
    exclude_file=$tmpdir/$fileName
    patch_file=$tmpdir/$fileName2
    if [ ` cat ${exclude_file}|grep "^scenario"|wc -l` -ge 1 ];then
         sed -i 's/scenario\///g' $exclude_file
    fi

    #update cases
    cd $HOME/dailyqa/$branch/scenario 
    run_svn_update -f .

    #exclude cases and do patch for some case
    cd $HOME/dailyqa/$branch/scenario
    if [ -f $exclude_file ];then
       cat $exclude_file|grep "^${cqt_scenario}"|xargs -i rm -rf {}
    fi
    cd $cqt_scenario
    if [ -f $patch_file ];then
       patch -p0 -f < $patch_file
    fi 
    cd ..

    #edited by cn15209, because CUBRIDSUS-17766 issue
    #2015.11.19
    if [ "$COMPAT_BUILD_SCENARIOS" == "site" ];then
         export _JAVA_OPTIONS=-Dfile.encoding=euckr
    else
         export _JAVA_OPTIONS=-Dfile.encoding=utf8
    fi

    #runJDBC
    exec_script_file="sh $QA_REPOSITORY/qatool_bin/console/scripts/cqt.sh"    
    ${exec_script_file} -h $CUBRID -v $branch -s $COMPAT_BUILD_SCENARIOS -t $COMPAT_BUILD_BIT -x -q -random_port|tee $tmplog

    #upload test results
    logPath=`cat $tmplog|grep RESULT_DIR|awk -F ':' '{print $NF}'|tr -d " "`
    logDir=`dirname $logPath`
    cat_cmd="cat $logDir/*.log"
    
    for testResultPath in `${cat_cmd}|grep "Result Root Dir:"|awk -F ':' '{print $NF}'|tr -d " "`
    do
        testResultName="`basename ${testResultPath}`"
        #get the value of typ
        if [ $COMPAT_BUILD_SCENARIOS == "sql_ext" ];then
           typ="sql_ext"
        else
           typ=`echo $testResultName|awk -F '_' '{print $3}'`
        fi
        
        cd $testResultPath    
        if [ "$typ" == "site" ];then
           if [ `cat summary.info|grep kcc|grep -v grep|wc -l` -ne 0 ];then
              site_type="kcc"
           elif [ `cat summary.info|grep neis05|grep -v grep|wc -l` -ne 0 ];then
              site_type="neis05"
           elif [ `cat summary.info|grep neis08|grep -v grep|wc -l` -ne 0 ];then
              site_type="neis08"
           fi
        fi
   
        #instead of typ in summary.info by category value
        if [ -n "$site_type" ];then
           category=`echo ${COMPAT_TEST_CATAGORY}|sed "s#site#$site_type#g"`
        fi
        sed -i "s/<catPath>${typ}</<catPath>$category</g" summary.info
        cd ..
        #if it is driver test,rename the test result name 
        if [ "${COMPAT_TEST_CATAGORY##*_}" == "S64" ]; then    
            prefix=`echo $testResultName|awk -F '_' '{print $1"_"$2"_"$3"_"$4"_"$5}'` 
            newName="${prefix}"_"${BUILD_ID}"
            name=`echo $newName|tr -d " "`
            rm -rf $name
            mv $testResultName $name
        elif [ "${COMPAT_TEST_CATAGORY##*_}" == "D" ]; then
            name=$testResultName
        fi
        upload_to_dailysrv "$name" "./qa_repository/function/y`date +%Y`/m`date +%-m`/$name" 
    done
    cd $curDir
}

#todo simplify this file
function get_best_version_for_exclude_patch_file() {
   excludeFileDir=$1
   initVer=$2 
   if [ `echo $initVer|grep ext |wc -l` -eq 1 ]; then
       prefix=`echo $initVer|awk -F_ '{print $4}'`
   else
       prefix=`echo $initVer|awk -F_ '{print $3}'`
   fi  
   no3=`echo "$prefix"|awk -F. '{print $3}'`
   
   #get the value of exclude_file
   if [ ! -n "$no3" ];then
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

   #get the value of patch_file
   last_element=50
   if [ ! -n "$no3" ];then
      while [ $last_element -ge 0 ]
      do
        versioninfo=${prefix}.$last_element
        lastVer=`echo ${initVer}|sed "s/\$prefix/\$versioninfo/g"`
        patch_file=${excludeFileDir}/patch_files/${lastVer}_patch
        if [ -e "$patch_file" ];then
            break
        fi
        let 'last_element -=1'
      done
   else
      patch_file=${excludeFileDir}/patch_files/${initVer}_patch 
   fi
}

function get_server_version() {
    if [ "${COMPAT_TEST_CATAGORY##*_}" == "S64" ]; then
        echo $COMPAT_BUILD_ID
    elif [ "${COMPAT_TEST_CATAGORY##*_}" == "D" ]; then
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
   echo WARN: Legacy test does not support CONTINUE mode
else
   if [ `is_server_ge_10_0` = "YES" ];then
        run_sql
   else
        run_sql_legacy
   fi
fi
