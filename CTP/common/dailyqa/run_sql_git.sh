#!/bin/bash

# VARIABLES
tmplog=${CTP_HOME}/tmp.log   
tmptxt=${CTP_HOME}/tmp.txt
git_repo_root="" 
ctp_test_conf=${CTP_HOME}/conf/sql_runtime.conf
ctp_type=""
ctp_scenario=""
ctp_category_alias=""
ctp_data_file=""

function check_core_and_do_backup ()
{
    backupDir=$1
    logfile=$2
    
    if [ ! -d $backupDir ]
    then
        mkdir -p $backupDir
    fi

    # move core files to backup directory
    cat $logfile |grep '^CORE_FILE:'|awk -F ':' '{print $NF}'|tr -d " " | xargs -i mv {} $backupDir

    # copy CUBRID to backup directory
    cp -rf $CUBRID $backupDir    
    
    # tar backup directory
    cd $backupDir
    cd ..
    backupfilename=`basename $backupDir`
    tar -zcf $backupfilename.tar.gz $backupfilename
    if [ -f $backupfilename.tar.gz ]
    then
        rm -rf $backupfilename
    fi
}

function upload_core_info ()
{
    coreInfoDir=$1
    logfile=$2
    coreInfoFile="`basename $coreInfoDir`_corestacks.txt"

    if [ ! -d $coreInfoDir ]
    then
        mkdir -p $coreInfoDir
    fi

    cat $logfile |grep '^CORE_FILE:'|awk -F ':' '{print $NF}'|tr -d " " | xargs -i analyzer.sh {} > $coreInfoDir/$coreInfoFile

    if [ `cat $coreInfoDir/$coreInfoFile | grep "CORE FILE   :" | wc -l` -gt 0 ]
    then
        upload_to_dailysrv $coreInfoDir "/home/fanzq/qaresult_en/web/test_error"
    fi
}

#STEP 1: env clean
runAction sql_medium_git.act 			#clean processes and check disk space
rm -f $tmplog >/dev/null 2>&1			#clean log
rm -f $tmptxt >/dev/null 2>&1 

#STEP 2: install CUBRID
if [ "$BUILD_TYPE" != 'general' -a "$BUILD_TYPE" != 'debug' ]
then
role="--role-${BUILD_TYPE}"
fi
run_cubrid_install $role $url $src_url

# STEP 3: configure CTP
cp conf/sql_local.conf ${ctp_test_conf}
if [ "$BUILD_SCENARIOS" == "medium" -o "$BUILD_SCENARIOS" == "medium_debug" ]
then
	ctp_type="medium"
	git_repo_root=$HOME/cubrid-testcases
	ctp_scenario=$git_repo_root/medium
elif [ "$BUILD_SCENARIOS" == "sql" -o "$BUILD_SCENARIOS" == "sql_debug" ]
then
	ctp_type="sql"
git_repo_root=$HOME/cubrid-testcases
ctp_scenario=$git_repo_root/sql
elif [ "$BUILD_SCENARIOS" == "sql_ext" -o "$BUILD_SCENARIOS" == "sql_ext_debug" ]
then
ctp_type="sql"
git_repo_root=$HOME/cubrid-testcases-private
ctp_scenario=$git_repo_root/sql
else
echo "Unknown scenario type, stop test."
echo "Please check and re-send message."
exit
fi

run_git_update -f $git_repo_root -b $BUILD_SCENARIO_BRANCH_GIT

ini.sh -s sql ${ctp_test_conf} scenario $ctp_scenario
ini.sh -s sql ${ctp_test_conf} data_file $ctp_scenario/files
ini.sh -s sql ${ctp_test_conf} category_alias $BUILD_SCENARIOS

#set supported param
ini.sh -s "sql/cubrid.conf" ${ctp_test_conf} | util_filter_supported_parameters.sh > $tmptxt
ini.sh -s "sql/cubrid.conf" ${ctp_test_conf} --update-from-file=$tmptxt --clear-first
ini.sh -s "sql/cubrid.conf" ${ctp_test_conf} test_mode yes

shard_service=`ini.sh -s "%shard1" $HOME/CUBRID/conf/cubrid_broker.conf SERVICE` 
if [ "$shard_service" -eq "ON" ]
then
	ini.sh -s "%shard1" $CUBRID/conf/cubrid_broker.conf SERVICE OFF	
fi 
ini.sh -s "sql/cubrid_ha.conf" ${ctp_test_conf} | util_filter_supported_parameters.sh > $tmptxt
ini.sh -s "sql/cubrid_ha.conf" ${ctp_test_conf} --update-from-file=$tmptxt --clear-first

# STEP 4: execute test
#export _JAVA_OPTIONS=-Dfile.encoding=utf8
ctp.sh ${ctp_type} -c ${ctp_test_conf} | tee $tmplog

# STEP 5: upload test results to daily server
if [ "$BUILD_TYPE" != "coverage" ]
then
testResultDir=`cat $tmplog|grep "^Test Result Directory"|awk -F ':' '{print $NF}'|tr -d " "`
upload_to_dailysrv "$testResultDir" ./qa_repository/function/y`date +%Y`/m`date +%-m`/${testResultDir/*\//}
else
run_coverage_collect_and_upload -h "$HOME/build" -n "$BUILD_ID" -c "$BUILD_SCENARIOS" -user "$MKEY_COVERAGE_UPLOAD_USER" -pwd "$MKEY_COVERAGE_UPLOAD_PWD" -host "$MKEY_COVERAGE_UPLOAD_IP" -to "${MKEY_COVERAGE_UPLOAD_DIR}/${BUILD_ID}/new" -port $DAILYQA_SSH_PORT_DEFAULT
fi

# STEP 6: check cores and upload to daily server
#timestamp=`echo $testResultFolderName|awk -F '_' '{print $5}'`
#upload_core_info "${backupdir}/${testResultFolderName}/${BUILD_SCENARIOS}_${curr_year}${curr_month2}${timestamp}" "${tmplog}"
#check_core_and_do_backup "$backupdir/$testResultFolderName" "$tmplog"

#STEP 7: final clean
rm -f $tmptxt
rm -r $tmplog
