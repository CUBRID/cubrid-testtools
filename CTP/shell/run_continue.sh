#!/bin/sh

task_all=`cat conf/dispatch_tc_ALL.txt | grep -v "^$" | sort| uniq | wc -l`
task_fin=`cat conf/dispatch_tc_FIN*.txt |grep -v "^$" | sort| uniq | wc -l`
continue_build=`cat conf/current_build |grep -v "^$" | sort| uniq | wc -l`
continue_task_id=`cat conf/current_task_id |grep -v "^$" | sort| uniq | wc -l`

if [ $continue_build -gt 0 ] && [ $continue_task_id -gt 0 ] && [ $task_all -gt $task_fin ] 
then
    exec_j=`cat conf/current_build | sed 's/false$/true/'`
    `$exec_j >> run.log 2>&1`
    sh package_fail.sh
fi
