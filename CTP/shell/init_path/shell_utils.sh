#!/bin/sh
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

function generage_readme {
    test_case_dir=$1
    backup_dir=$2

    freadme=${backup_dir}/readme.txt
    echo 1.TEST CASE: `convert_to_git_url.sh ${test_case_dir}` > $freadme
    echo 2.LOCAL CASE PATH: ${test_case_dir} >> $freadme
    echo 3.CUBRID VERSION: `cubrid_rel | grep CUBRID` >> $freadme
    echo 4.TEST DATE: `date` >> $freadme
    echo 5.ENVIRONMENT: >> $freadme
    set >> $freadme
    echo 6.ALL PROCESSES: >> $freadme
    ps -ef >> $freadme
    echo 7.IPCS >> $freadme
    ipcs >> $freadme
    echo 8.DISK STATUS >> $freadme
    df -h>> $freadme
    echo 9.LOGGED >> $freadme
    who >> $freadme
    echo 10.MEMORY STATUS >> $freadme
    free -m >> $freadme
}

function get_core_analyzer_file {
    local core=$1
    local core_stack_fn=${CUBRID}/`basename $core | sed 's/core/briefstack/g'`
    if [ ! -f ${core_stack_fn} ]; then
       analyzer.sh $core > ${core_stack_fn}
    fi
    echo ${core_stack_fn}
}

function clear_core_analyzer_files {
    rm -rf $CUBRID/*briefstack*
}

function do_check_more_errors {
    test_case_dir=$1
    test_case_dir=${test_case_dir%/cases*}
    case_name=`echo ${test_case_dir##*/}`
    result_file_full_name=${test_case_dir}/cases/${case_name}.result

    local core_brief_stack_fn=""
    clear_core_analyzer_files
    
    if [  -f "$CTP_CORE_EXCLUDE_FILE" ]; then
      find $init_path $CUBRID $test_case_dir -name "core*" -type f > temp_assert_log
      local hit_all_cnt=0
      local hit_server_cnt=0
      local all_core_cnt=0
      local all_server_core_cnt=0

      local curr_is_server=0
      local curr_hit=0
      while read core
      do
         all_core_cnt=`expr $all_core_cnt + 1`

         core_brief_stack_fn=`get_core_analyzer_file $core`

         curr_hit=`cat ${core_brief_stack_fn} | grep -aE -f "$CTP_CORE_EXCLUDE_FILE" |wc -l`
         if [ $curr_hit -gt 0 ]; then
           hit_all_cnt=`expr $hit_all_cnt + 1`
         fi

         curr_is_server=`cat ${core_brief_stack_fn} | grep "PROCESS NAME" | grep "cub_server" |wc -l`
         if [ $curr_is_server -gt 0 ]; then            
            all_server_core_cnt=`expr $all_server_core_cnt + 1`
            if [ $curr_hit -gt 0 ]; then
               hit_server_cnt=`expr $hit_server_cnt + 1`
            fi
         fi
      done < temp_assert_log

      if [ $hit_all_cnt -eq $all_core_cnt ] || [ $all_server_core_cnt -gt 0 -a $all_server_core_cnt -eq $hit_server_cnt ]; then
        while read core
        do
          rm -rf $core
        done < temp_assert_log
      fi
    fi


    find $init_path $CUBRID $test_case_dir -name "core*" -type f>temp_log
    core_dump_cnt=`cat temp_log|wc -l`
    fatal_err_cnt=`grep -r -n "FATAL ERROR" $CUBRID/log/* | wc -l`
    old_fatal_err_cnt=0
    if [ -f $CUBRID/log/qa_fatal_error_count.log ]
    then
	old_fatal_err_cnt=`cat $CUBRID/log/qa_fatal_error_count.log`
    fi

    cub_build_id=`cubrid_rel | grep CUBRID | awk -F ')' '{print $1}' | awk -F '(' '{print $NF}'`
    current_datetime=`date "+%Y%m%d_%H%M%S"`
    backup_name=AUTO_${cub_build_id}_${current_datetime}
    backup_dir=~/ERROR_BACKUP/${backup_name}
	host_ip="${TEST_SSH_HOST}"
	if [ "${host_ip}" = "" ]; then
	    host_ip=`hostname -i`
	fi
	export TEST_INFO_ENV="${USER}@${host_ip}:${TEST_SSH_PORT}"
	export TEST_INFO_BUILD_ID=${cub_build_id}

    if [ $core_dump_cnt -gt 0 ] || [ $fatal_err_cnt -gt $old_fatal_err_cnt -a "$SKIP_CHECK_FATAL_ERROR" != "TRUE" ]; then
        mkdir -p $backup_dir

        generage_readme ${test_case_dir} ${backup_dir}

        host_ip=`hostname -i`
        if [ $core_dump_cnt -gt 0 ]; then
 	    out=" : NOK found core file on host "$host_ip"("$backup_dir")"
            echo $out >> $result_file_full_name
            echo $out

            local has_cub_server_crash=0
            local is_cub_server=0
            while read core
            do
                 core_brief_stack_fn=`get_core_analyzer_file $core`
                 is_cub_server=`cat ${core_brief_stack_fn} | grep "PROCESS NAME:"|grep "cub_server"|wc -l`
                 if [ ${is_cub_server} -gt 0 ]; then
                      has_cub_server_crash=1
                      break
                 fi
            done < temp_log

	    while read core
	    do
                core_brief_stack_fn=`get_core_analyzer_file $core`
		is_cub_cas=`cat ${core_brief_stack_fn} | grep "PROCESS NAME:"|grep "cub_cas"|wc -l`
		if [ ${has_cub_server_crash} -eq 0 -o $is_cub_cas -eq 0 ];then
			  issue_title=`grep SUMMARY ${core_brief_stack_fn} | head -n 1`
		    echo \<!--HTMLESCAPESTART--\>\<a class=SHELLCORE href=\"javascript:reportShellCoreIssue\(\'${core}\', \'${backup_name}\', \'${host_ip}\', \'${TEST_SSH_PORT}\', \'${USER}\', \'${cub_build_id}\', \'${issue_title}\' \) \"\>\<i\>\<font color=red\>REPORT ISSUE FOR BELOW CRASH\</font\>\</i\>\</a\>\<!--HTMLESCAPEEND--\> >> $result_file_full_name
			  cat ${core_brief_stack_fn} >> $result_file_full_name
			  core_full_stack_fn=${CUBRID}/`basename $core | sed 's/core/fullstack/g'`
			  analyzer.sh -f $core > ${core_full_stack_fn}
		else
			  echo "CRASH FROM CUB_CAS:${core}(skip to print call stacks)" >> $result_file_full_name
		fi
	    done < temp_log
            
            clear_core_analyzer_files
        fi
        if [ $fatal_err_cnt -gt 0 ]; then
            out=" : NOK found fatal error on host "$host_ip"("$backup_dir")"
            echo $out >> $result_file_full_name
            echo $out
        fi
        cp -rfp $CUBRID $backup_dir/CUBRID
        cp -rfp $test_case_dir $backup_dir
        cp -rfp $init_path $backup_dir/init_path
	cd ~/ERROR_BACKUP
	tar zcvf AUTO_${cub_build_id}_${current_datetime}.tar.gz AUTO_${cub_build_id}_${current_datetime}
	rm -rf AUTO_${cub_build_id}_${current_datetime}
        cd -

	cat temp_log | xargs -i rm -rf {}
        echo $fatal_err_cnt>$CUBRID/log/qa_fatal_error_count.log
    fi
    rm temp_log -f >/dev/null 2>&1
}

function do_save_normal_error_logs {
    do_save_snapshot_by_type $1 "NORMAL"
}

function should_save_snapshot_for_recovery {
    cnt=`grep -aE -f ${init_path}/recovery_ignore_item.conf cubrid_failure_desc.txt | wc -l`
    if [ $cnt -gt 0 ]; then
        echo 0
    else
        echo 1
    fi
}

function do_save_snapshot_by_type {
    test_case_dir=$1
    kind=$2
    test_case_dir=${test_case_dir%/cases*}
    case_name=`echo ${test_case_dir##*/}`
    result_file_full_name=${test_case_dir}/cases/${case_name}.result
    cub_build_id=`cubrid_rel | grep CUBRID | awk -F ')' '{print $1}' | awk -F '(' '{print $NF}'`
    current_datetime=`date "+%Y%m%d_%H%M%S"`
    
    backup_fname=AUTO_${kind}_${cub_build_id}_${current_datetime}
    backup_dir=~/ERROR_BACKUP/${backup_fname}

    mkdir -p ${backup_dir}
    cp -rfp $CUBRID ${backup_dir}
    cp -rp ${test_case_dir} ${backup_dir}
    cubrid_fail_file=${test_case_dir}/cubrid_failure_desc.txt
    if [ -f ${cubrid_fail_file} ]; then
        mv ${cubrid_fail_file} ${backup_dir}
    fi
    generage_readme ${test_case_dir} ${backup_dir}

    cd ${backup_dir}/..
    tar zcvf ${backup_fname}.tar.gz ${backup_fname} 2>&1 >/dev/null
    rm -rf ${backup_fname}
    cd - 2>&1 >/dev/null
    host_ip=`hostname -i`
    echo " : NOK found ${kind} error on host $host_ip (${backup_dir})" >> ${result_file_full_name}
}
