#!/bin/sh
function do_check_more_errors
{
    test_case_dir=$1
    test_case_dir=${test_case_dir%/cases*}
    case_name=`echo ${test_case_dir##*/}`
    result_file=${test_case_dir}/cases/${case_name}.result

    if [  $EXCLUDED_CORES_BY_ASSERT_LINE ]; then
      find $init_path $CUBRID $test_case_dir -name "core*" -type f > temp_assert_log
      exclude_assert=0
      all_core=`cat temp_assert_log|wc -l`
      while read core
        do
         analyzer.sh $core  > analyzer.log 2>&1
         count_analyzer=`cat analyzer.log | grep -E "$EXCLUDED_CORES_BY_ASSERT_LINE" |wc -l`
         if [ $count_analyzer -eq 0 ]
         then
           break
         else
           exclude_assert=`expr $exclude_assert + 1`
         fi
        done < temp_assert_log

      if [ $exclude_assert -eq $all_core ]
      then
        while read core
        do
          rm -rf $core
        done < temp_assert_log
      fi
    fi


    find $init_path $CUBRID $test_case_dir -name "core*" -type f>temp_log
    core_dump_cnt=`cat temp_log|wc -l`
    fatal_err_cnt=`grep -r -n "FATAL ERROR" $CUBRID/log/* | wc -l`
    if [ -f $CUBRID/log/qa_fatal_error_count.log ]
    then
	old_fatal_err_cnt=`cat $CUBRID/log/qa_fatal_error_count.log`
    fi

    cub_build_id=`cubrid_rel | grep CUBRID | awk -F ')' '{print $1}' | awk -F '(' '{print $NF}'`
    current_datetime=`date "+%Y%m%d_%H%M%S"`
    backup_dir=~/ERROR_BACKUP/AUTO_${cub_build_id}_${current_datetime}

    if [ $core_dump_cnt -gt 0 ] || [ $fatal_err_cnt -gt $old_fatal_err_cnt -a "$SKIP_CHECK_FATAL_ERROR" != "TRUE" ]; then
        mkdir -p $backup_dir

        #generate readme
        freadme=$backup_dir/readme.txt
        echo 1.TEST CASE: $test_case_dir > $freadme
        echo 2.CUBRID VERSION: `cubrid_rel | grep CUBRID` >> $freadme
        echo 3.TEST DATE: `date` >> $freadme
        echo 4.ENVIRONMENT: >> $freadme
        set >> $freadme
        echo 5.PROCESS >> $freadme
        ps -ef >> $freadme
        echo 6.IPCS >> $freadme
        ipcs >> $freadme
        echo 7.DISK STATUS >> $freadme
        df >> $freadme
        echo 8.LOGGED >> $freadme
        who >> $freadme

        host_ip=`hostname -i`
        if [ $core_dump_cnt -gt 0 ]; then
 	    out=" : NOK found core file on host "$host_ip"("$backup_dir")"
            echo $out >> $result_file
            echo $out
	    while read core
	    do
		analyzer.sh $core  > analyzer.log 2>&1
		is_cub_cas=`cat analyzer.log | grep "PROCESS NAME:"|grep "cub_cas"|wc -l`
		if [ $is_cub_cas -eq 0 ];then
			cat analyzer.log >> $result_file	
		else
			echo "CRASH FROM CUB_CAS:${core}(skip to print call stacks)" >> $result_file
		fi
	    done < temp_log
        fi
        if [ $fatal_err_cnt -gt 0 ]; then
            out=" : NOK found fatal error file on host "$host_ip"("$backup_dir")"
            echo $out >> $result_file
            echo $out
        fi
        cp -rf $CUBRID $backup_dir/CUBRID
        cp -rf $test_case_dir $backup_dir
        cp -rf $init_path $backup_dir/init_path
	cd ~/ERROR_BACKUP
	tar zcvf AUTO_${cub_build_id}_${current_datetime}.tar.gz AUTO_${cub_build_id}_${current_datetime}
	rm -rf AUTO_${cub_build_id}_${current_datetime}
        cd -

	cat temp_log | xargs -i rm -rf {}
        echo $fatal_err_cnt>$CUBRID/log/qa_fatal_error_count.log

    fi
    rm temp_log -f >/dev/null 2>&1
}

function do_save_normal_error_logs
{
    test_case_dir=$1
    test_case_dir=${test_case_dir%/cases*}
    case_name=`echo ${test_case_dir##*/}`
    result_file=${test_case_dir}/cases/${case_name}.result
    cub_build_id=`cubrid_rel | grep CUBRID | awk -F ')' '{print $1}' | awk -F '(' '{print $NF}'`
    current_datetime=`date "+%Y%m%d_%H%M%S"`
    
    backup_dir=~/ERROR_BACKUP/AUTO_NORMAL_${cub_build_id}_${current_datetime}
    mkdir -p $backup_dir
    cp -rf $CUBRID/log $backup_dir
    cd ~/ERROR_BACKUP
    tar zcvf AUTO_NORMAL_${cub_build_id}_${current_datetime}.tar.gz AUTO_NORMAL_${cub_build_id}_${current_datetime} 2>&1 >/dev/null
    rm -rf AUTO_NORMAL_${cub_build_id}_${current_datetime}
    cd - 2>&1 >/dev/null
    echo $backup_dir >> $result_file
    echo $backup_dir
}

