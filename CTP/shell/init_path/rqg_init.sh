#!/bin/bash -xe
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

function get_dsn_url_with_autocommit_on()
{
    db_name=$1
    port=$2
    echo "dbi:cubrid:database=${db_name};host=localhost;port=${port};autocommit=on"
}


function get_dsn_url_with_autocommit_off()
{
   db_name=$1
   port=$2
   echo "dbi:cubrid:database=${db_name};host=localhost;port=${port};autocommit=off"
}

function get_all_table_names()
{
   db_name=$1
   csql -u dba $from -c "select class_name from db_class where is_system_class='NO' and class_name not in(select partition_class_name from db_partition) and owner_name='PUBLIC';" >temp.log
   tables=`grep "^ *'" temp.log |sed "s/'//g"|sed "s/ //g"`   
   echo "$tables"
}

function run_gendata()
{
   param_options=$*
   perl $RQG_HOME/gendata.pl $param_options
   if [ $? -ne 0 ];then
	write_nok "generate data fail, please check your parameter $param_options"
   fi
}

function run_gengrammar()
{
   param_options=$*
   perl $RQG_HOME/gengrammar.pl $param_options
   if [ $? -ne 0 ];then
        write_nok "generate Grammar fail, please check your parameter $param_options"
   fi
	
}

function run_gensql()
{
   param_options=$*
   perl $RQG_HOME/gensql.pl $param_options
   if [ $? -ne 0 ];then
        write_nok "generate sql fail, please check your parameter $param_options"
   fi

}

function run_gentest()
{
   param_options=$*
   perl $RQG_HOME/gentest.pl $param_options > gentest.log 2<&1
   if [ $? -ne 0 ];then
        write_nok "generate test fail, please check your parameter $param_options"
   else
        if [ `grep 'Test completed successfully' gentest.log|wc -l` -eq 1 ];then
                rm gentest.log >/dev/null
                write_ok
        else
                write_nok "generate test is not completed successfully, please confirm gentest.log"
        fi
   fi

}


function run_genall()
{
   param_options=$*
   perl $RQG_HOME/runall.pl $param_options
   if [ $? -ne 0 ];then
        write_nok "run all test fail, please check your parameter $param_options"
   fi

}


function rqg_do_backup_db()
{
    pram_count=$#
    ori_db_name=$1
    to_db_name=$2
    curDir=`pwd`
    if [ $pram_count -ne 1 ];then
    	cd $CUBRID/databases
    	cubrid server stop $to_db_name
    	cubrid deletedb $to_db_name
    	[ -d "$to_db_name" ] && rm -rf $to_db_name
    	mkdir -p $to_db_name

    	cd $to_db_name
    	cubrid unloaddb $ori_db_name
    	rqg_cubrid_createdb $to_db_name
    	cubrid loaddb -i ${ori_db_name}_indexes -d ${ori_db_name}_objects -s ${ori_db_name}_schema -udba $to_db_name
    else
	cd $CUBRID/databases/$ori_db_name
        cubrid backupdb $ori_db_name
    fi

    cd $curDir 
}


function rqg_check_db_data()
{
    curDir=`pwd`
    ori_db_name=$1
    target_db_name=$2
    table_name_list=$3
    cd $cur_path

    if [ ! "$table_name_list" ];then
	table_name_list=`get_all_table_names $ori_db_name`
    fi

    for tbl in $table_name_list
    do
        table_name=$tbl
        csql -u dba $from_db_name -c "select count(*) from $table_name order by pk" > before.log
        csql -S -u dba $target_db_name -c "select count(*) from $table_name order by pk" >after.log

        # compare row number, if it is equal, then compare data
        sed -i "/row selected/d" before.log
        sed -i "/row selected/d" after.log

        if diff before.log after.log >/dev/null
        then
            write_ok
        else
            write_nok "data is different between before.log and after.log"
            diff before.log after.log
            break
        fi
    done

    cd $curDir
}


function rqg_check_restoredb_consistency()
{
    curDir=`pwd`
    db_name=$1
    compare_log_name=$2
    if [ -z "$compare_log_name" ];then
         compare_log_name="compare.log"        
    fi

    table_name_list=`get_all_table_names $db_name`
    cd $CUBRID/databases
    for t in "$table_name_list"
    do
	 csql -u dba $db_name -c "select * from $t order by pk" >before_${t}.log
	 sed -i "/row[s] selected/d" before_${t}.log
    done

    rm ${db_name}/${db_name}
    cubrid service stop 
    cubrid restoredb $db_name
    cubrid server start $db_name
   
    for t in "$table_name_list"
    do
         csql -u dba $db_name -c "select * from $t order by pk" >after_${t}.log
	 sed -i "/row[s] selected/d" after_${t}.log
    done

    for t in "$table_name_list"
    do
         if diff before_${t}.log after_${t}.log >/dev/null
         then
		write_ok
         else
             	write_nok "data ddis different between before_${t}.log and after_${t}.log"
             break
         fi
    done

    cd $curDir
}


function rqg_check_constraint_unique()
{
    name=$1
    db_name=$2
    if [ ! "$db_name" ];then
        db_name="test"
    fi

    csql -udba $db_name > temp 2>&1 <<CCCSQL
;sc $name

CCCSQL

    grep 'PRIMARY KEY' temp|sed 's/.*(//g'|sed 's/)\s*//g' > columns.log
    grep '^\s*UNIQUE' temp|sed 's/.*(//g'|sed 's/)\s*//g' >>column.log
    while read value
    do
	hit_cnt=`csql -u dba ${db_name} -c "select 'NOK' from ${name} group by ${value} having count(*) >1" | wc -l`
        if [ $hit_cnt -gt 0 ];then
	      write_nok "Violate unique constraint on ${name}.${value}: $hit_cnt"
        else  
	      write_ok "${name}.${value}"	
        fi
    done < column.log

    rm -f temp column.log
}


function rqg_check_constraint_notnull()
{
    name=$1
    db_name=$2
    if [ ! "$db_name" ];then
        db_name="test"
    fi

    csql -udba $db_name > temp 2>&1 <<CCCSQL
;sc $name

CCCSQL

    columns=`cat temp|grep 'NOT NULL'|awk '{print $1}'`
    for value in ${columns[*]}
    do
    	cnt=`csql -u dba $db_name -c "select 'CNT:'||count(*) from $name where $value IS NULL" | grep "CNT:0" | wc -l`
	if [ $cnt -eq 0 ];then
	     write_ok "${name}.${value}"
        else
	     write_nok "${name}.${value} violate not null constraint"
        fi

    done
    
    rm -f temp
}


function rqg_check_constraint_fk()
{
    name=$1
    db_name=$2
    if [ ! "$db_name" ];then
        db_name="test"
    fi
    
    csql -udba $db_name > temp 2>&1 <<CCCSQL
;sc $name

CCCSQL

    if [ `grep 'FOREIGN KEY' temp|wc -l` -eq 0 ]
    then
        return
    fi
    grep 'FOREIGN KEY' temp>temp1
    while read line
    do
        columns=`echo $line|sed 's/.*(//g'|sed 's/).*$//g'`
        fname=`echo $line|sed 's/.*REFERENCES //g'|sed 's/ ON.*$//g'`
        
        csql -udba $db_name > temp 2>&1 <<CCCSQL
;sc $fname

CCCSQL
        fcolumns=`grep 'PRIMARY KEY' temp|sed 's/.*(//g'|sed 's/)\s*//g'`
        csql -u dba $db_name -c "select 'RAW', ${columns} from $name" | grep RAW >child.log
        csql -u dba $db_name -c "select 'RAW', ${fcolumns} from $fname" | grep RAW >father.log
	cnt=`cat child.log | grep -v -f father.log | wc -l`
        if [ $cnt -gt 0 ];then
	    write_nok "$line violate fk constraint"
	else
	    write_ok
        fi
    done <temp1
   
    rm -f temp temp1 child.log father.log 
}

function rqg_check_constraint_all()
{
    db_name=$1
    db_user=$2
    if [ ! "$db_name" ];then
        db_name="test"
    fi
   
    if [ ! "$db_user" ];then
	db_user="public"
    fi

    csql -u dba $db_name -c "select class_name from db_class where owner_name='${db_user}' and is_system_class='NO' and class_type='CLASS'" >tables.log
    sed -n '/=============/,$p' tables.log|grep "^\s*'"|sed -e "s/^\s*'//g" -e "s/'\s*$//g" >tables
    while read tname
    do
       rqg_check_constraint_unique $tname $db_name
       rqg_check_constraint_notnull $tname $db_name
       rqg_check_constraint_fk $tname $db_name
    done <tables

    rm -f tables tables.log    
}

function rqg_cubrid_createdb()
{
    db_name=$1
    param_count=$#
    if [ $param_count -eq 0 ];then
       db_name="test"
    fi

    if [ "$db_name" ];then
       cubrid_createdb $db_name
    else
       cubrid_createdb $*
    fi
}

function rqg_cubrid_cleandb()
{
    db_name=$1
    param_count=$#
    if [ $param_count -eq 0 ];then
       db_name="test"
    fi

    cubrid server stop $db_name
    cubrid deletedb $db_name
}

function rqg_kill_all_cub_process()
{
    ps -u $USER -o pid,comm|grep -v grep|grep cub_server|awk '{print $1}'|xargs kill -9 {} >/dev/null 2>&1
    ps -u $USER -o pid,comm|grep -v grep|grep cub_broker|awk '{print $1}'|xargs kill -9 {} >/dev/null 2>&1
    ps -u $USER -o pid,comm|grep -v grep|grep cub_cas|awk '{print $1}'|xargs kill -9 {} >/dev/null 2>&1
    ps -u $USER -o pid,comm|grep -v grep|grep cub_master|awk '{print $1}'|xargs kill -9 {} >/dev/null 2>&1
}


function recovery_test_begin()
{
    db_name=$1
    if [ ! "$db_name" ];then
	db_name="test"
    fi

    cubrid server stop $db_name
    cubrid broker stop

    change_db_parameter "fault_injection_test=recovery"
    change_db_parameter "error_log_level=notification"
    change_db_parameter "error_log_size=2147483647"
    change_db_parameter "call_stack_dump_activation_list=-588"
    
    rqg_check_start_server $db_name
    cubrid broker start    
}

function recovery_test_end()
{
    db_name=$1
    if [ ! "$db_name" ];then
        db_name="test"
    fi
    cubrid service stop
    sed -i "/fault_injection_test/d" $CUBRID/conf/cubrid.conf
    sed -i "/error_log_level/d" $CUBRID/conf/cubrid.conf
    sed -i "/error_log_size/d" $CUBRID/conf/cubrid.conf
    sed -i "/call_stack_dump_activation_list/d" $CUBRID/conf/cubrid.conf

    rqg_check_start_server $db_name
    

}


function rqg_kill_cub_server()
{
    ps -u $USER -o pid,comm|grep -v grep|grep cub_server|awk '{print $1}'|xargs kill -9 {}
    sleep 5
}

function rqg_cubrid_start_server()
{
    db_name=$1
    if [ ! "$db_name" ];then
        db_name="test"
    fi

    retry_count=$2
    if [ ! "$retry_count" ];then
	retry_count=1
    fi 

    isRunning=`ps -u $USER -o pid,comm|grep -v grep|grep cub_server|wc -l`
    if [ $isRunning -ne 0 ];then
	return
    fi
    
    for((r=0;r<${retry_count};r++))
    do
       cubrid server start $db_name 1>start_status.log 2>&1
       if [ $? -eq 0 ]
       then
	   break
       else
	   sleep 2
       fi
    done
}

function rqg_check_start_server()
{
    db_name=$1
    rqg_cubrid_start_server $db_name

    if [ -f start_status.log ];then
        if grep 'cubrid server start: success' start_status.log
        then
             write_ok
             rm start_status.log >/dev/null
        else
            isRunning=`ps -u $USER -o pid,comm|grep -v grep|grep cub_server|wc -l`
            if [ $isRunning -ne 0 ];then
                write_ok
            else
                write_nok
            fi
        fi
    fi
}

function rqg_cubrid_start_broker()
{
   cubrid broker start
}

function rqg_cubrid_checkdb()
{
   checkdb_options="$1"
   if [ ! "$checkdb_options" ];then
	checkdb_options="test"
   else
	checkdb_options=$@
   fi

   cubrid service stop
   sleep 2

   cubrid checkdb -S $checkdb_options > _checkdb.log 2>&1
   if [ $? -ne 0 ];then
        sed -i 'a\Fail to execute checkdb utility with the standalone mode!\n' _checkdb.log
	write_nok _checkdb.log
   else
	write_ok 
   fi 

}

function rqg_cubrid_vacuumdb()
{
   db_name=$1
   if [ ! "$db_name" ];then
	db_name="test"
   fi
   cubrid service stop
   sleep 2
   
   cubrid vacuumdb -S $db_name > _vacuumdb.log 2>&1
   if [ $? -ne 0 ];then
	sed -i '1 a\Fail to execute vacuumdb utility with the standalone mode! \n'  _vacuumdb.log
	write_nok _vacuumdb.log
   else
	write_ok
   fi
}

function rqg_cubrid_checkcore_and_stoptest()
{
    case_path=$1
    if [ -n "$case_path" ];then
	do_check_more_errors $case_path
	exit -1
    fi
}

function rqg_check_space()
{
   #TODO 
   echo "TODO"
}


cur_case_path=`pwd`
export TESTCASE_HOME=`search_in_upper_path $cur_case_path config`/..

