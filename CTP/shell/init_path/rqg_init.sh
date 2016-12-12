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
   
    if[ ! "$db_user" ];then
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
    db_name=
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
    then
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

function rqg_cubrid_checkdb()
{
   db_name=$1
   checkdb_options=""
   if [ ! "$db_name" ];then
	db_name="test"
   else
	checkdb_options=$@
   fi

   checkdb_options=$@
   db
   cubrid service stop
   sleep 2

   cubrid checkdb -S $checkdb_options $db_name > _checkdb.log 2>&1
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
}


cur_case_path=`pwd`
export RQG_YY_ZZ_HOME=`search_in_upper_path $cur_case_path files`

