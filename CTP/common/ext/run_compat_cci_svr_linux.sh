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

tmplog=""
exclude_file=""
is_continue_mode=$1
shell_config_template=""
shell_fm_test_conf="${CTP_HOME}/conf/shell_runtime.conf"

#start to install
run_cubrid_install -s $url $com_url

function close_shard_service {
    shard_service=`ini.sh -s "%shard1" $CUBRID/conf/cubrid_broker.conf SERVICE`
    if [ "$shard_service" = "ON" ]
    then
        ini.sh -s "%shard1" $CUBRID/conf/cubrid_broker.conf SERVICE OFF
    fi
}

function get_best_version_for_exclude_file()
{
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


function run_shell()
{
   if [ -f ${CTP_HOME}/conf/shell_template_for_${BUILD_SCENARIOS}.conf ]; then
      shell_config_template=${CTP_HOME}/conf/shell_template_for_${BUILD_SCENARIOS}.conf
   elif [ -f ${CTP_HOME}/conf/shell_template.conf ]; then
      shell_config_template=${CTP_HOME}/conf/shell_template.conf
   fi

   cp -f ${shell_config_template} ${shell_fm_test_conf}
   branch=$BUILD_SCENARIO_BRANCH_GIT
   category=$COMPAT_TEST_CATAGORY
   exclude_file_dir=$HOME/cubrid-testcases-private/interface/CCI/shell/config/daily_regression_test_exclude_list_compatibility
   get_best_version_for_exclude_file "${exclude_file_dir}" "$category"  
 
   # Disk checking
   if [ -f $CTP_HOME/conf/cci_comapt.act ];then
      runAction cci_compat.act
   else
      echo "Skip Disk Checking!"
   fi
    
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
   ini.sh -u "test_continue_yn=false" $shell_fm_test_conf

   #if the test has exclude file,config it.
   if [ -e $exclude_file ];then 
      ini.sh -u "testcase_exclude_from_file=$exclude_file" $shell_fm_test_conf 
   fi

   #execute testing
   ctp.sh shell -c $shell_fm_test_conf 2>&1 | tee $tmplog
   cd -
}

function run_shell_continue()
{
   # Disk checking
   if [ -f $CTP_HOME/conf/cci_compat.act ];then
      runAction cci_compat.act
   else
      echo "Skip Disk Checking!"
   fi
   
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
    curDir=`pwd`
    category=$COMPAT_TEST_CATAGORY
    branch=$BUILD_SVN_BRANCH_NEW 
    tmplog=$QA_REPOSITORY/temp/tmp.log
    (cd $QA_REPOSITORY; sh upgrade.sh)
    if [ -f $tmplog ];then
        rm -f $tmplog
    fi
     
    # Disk checking
    if [ -f $CTP_HOME/conf/cci_compat.act ];then
      runAction cci_compat.act
    else
      echo "Skip Disk Checking!"
    fi

    #close shard service
    close_shard_service
   
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
    
    #update cases
    svnuser="cubridci"
    svnpassword="1722335e0ae88110364fa7f91773d2255c209b2a"
    cd $HOME/dailyqa/$branch/scenario/shell 
    svnParams="--username $svnuser --password $svnpassword --non-interactive --no-auth-cache"
    default_exclude="grep -v '?'|grep -v '!'"
    tbd_cmd="svn st $svnParams |$default_exclude|awk '{print \$NF}'" 
    tbd=`eval ${tbd_cmd}`
    svn cleanup $svnParams
    if [ "$tbd" != "" ]
    then
      svn revert -R $svnParams $tbd
    fi
    svn up $svnParams
    svn revert $svnParams -R *

    #exclude cases
    exclude_file_dir=$HOME/dailyqa/$branch/config
    get_best_version_for_exclude_file "${exclude_file_dir}" "$category"  
    cd $HOME/dailyqa/$branch/scenario
    cat $exclude_file|grep "^shell"|awk '{print "rm -rf " $0}' >delete.sh
    sh delete.sh
    
 
    #runCCI
    exec_script_file="sh $QA_REPOSITORY/qatool_bin/console/scripts/cqt.sh"    
    ${exec_script_file} -h $CUBRID -v $branch -s shell -q -no-i18n -x |tee -a $tmplog

    #upload test results
    logPath=`cat $tmplog|grep RESULT_DIR|awk -F ':' '{print $NF}'|tr -d " "`
    testResultPath=`cat $logPath|grep "Result Root Dir:"|awk -F ':' '{print $NF}'|tr -d " "`
    testResultName="`basename ${testResultPath}`"
    #testResultName=`cat $logPath|grep "Test Id:"|awk -F ':' '{print $NF}'|tr -d " "`
    cd $testResultPath
    typ=`echo $testResultName|awk -F '_' '{print $3}'`
    sed -i "s/<catPath>${typ}</<catPath>$category</g" summary.info    
    cd ..
    upload_to_dailysrv "$testResultName" "./qa_repository/function/y`date +%Y`/m`date +%-m`/$testResultName" 
    cd $curDir
}

if [ "$is_continue_mode" == "YES" ];then
   if [ "${BUILD_IS_FROM_GIT}" == "1" ];then
	run_shell_continue
   else
        run_shell_legacy
   fi
else
   if [ "${BUILD_IS_FROM_GIT}" == "1" ]; then
        run_shell
   else
        run_shell_legacy
   fi 

fi

