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

function init()
{
    . $init_path/init.sh
    init test

    source $init_path/shell_utils.sh  
    export RQG_YY_ZZ_HOME=`search_in_upper_path $cur_path files`
}


function search_directory_in_upper_path {
   curr_path=$1
   dest_folder_name=$2
   if [ -d ${curr_path}/${dest_folder_name} ]; then
       echo $(cd ${curr_path}/${dest_folder_name}; pwd)
   else
       if [ "$(cd ${curr_path}/..; pwd)" == "/" ]; then
           return
       else
           search_in_upper_path ${curr_path}/.. ${dest_folder_name}
       fi
   fi
}

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

function delete_file()
{
    file_name=$@
    for x in $file_name
    do
    	if [ -f "$x" ];then
             rm -f $x
        fi
    done
}

function rqg_pk_check()
{
    name=$1
    db_name=$2
    if [ ! "$db_name" ];then
	db_name="test"
    fi

    csql -udba $db_name > temp 2>&1 <<CCCSQL
;sc $name

CCCSQL

    columns=`grep 'PRIMARY KEY' temp|sed 's/.*(//g'|sed 's/)\s*//g'`
    if [ "a"${columns} == "a" ]
    then
        return
    fi
    csql -u dba $db_name -c "select ${columns} from $name" >temp
    sed -i '1,/===========/d' temp
    sort temp|uniq >temp1
    before=`cat temp|wc -l`
    after=`cat temp1|wc -l`
    if [ $before -ne $after ]
    then
        write_nok "violate unique constraint on pk"
        return
    fi

    delete_file temp temp1
}

function rqg_unique_check()
{
    name=$1
    db_name=$2
    if [ ! "$db_name" ];then
        db_name="test"
    fi

    sh csql.sh $db_name ";sc $name" >temp
    grep '^\s*UNIQUE' temp|sed 's/.*(//g'|sed 's/)\s*//g' >column.log
    while read value
    do
        csql -u dba $db_name -c "select ${value} from $name" >temp
        sed -i '1,/===========/d' temp
        sort temp|uniq >temp1
        before=`cat temp|wc -l`
        after=`cat temp1|wc -l`
        if [ $before -ne $after ]
        then
            write_nok "violate unique constraint on pk"
            return
        fi
    done < column.log

    delete_file temp column.log temp1
}


function rqg_isNotNull_check()
{
    name=$1
    db_name=$2
    if [ ! "$db_name" ];then
        db_name="test"
    fi

    sh csql.sh $db_name ";sc $name" >temp
    columns=`cat temp|grep 'NOT NULL'|awk '{print $1}'`
    for value in ${columns[*]}
    do
       csql -u dba $db_name -c "select * from $name where $value IS NULL" >temp
       if [ `grep '0 row selected' temp|wc -l` -ne 1 ]
       then
           write_nok "$value violate not null constraint"
           cat temp
           return
       fi
    done
    
    delete_file temp
}


function rqg_fk_check()
{
    name=$1
    db_name=$2
    if [ ! "$db_name" ];then
        db_name="test"
    fi
    sh csql.sh $db_name ";sc $name" >temp
    if [ `grep 'FOREIGN KEY' temp|wc -l` -eq 0 ]
    then
        return
    fi
    grep 'FOREIGN KEY' temp>temp1
    while read line
    do
        columns=`echo $line|sed 's/.*(//g'|sed 's/).*$//g'`
        fname=`echo $line|sed 's/.*REFERENCES //g'|sed 's/ ON.*$//g'`
        sh csql.sh $db_name ";sc $fname" >temp
        fcolumns=`grep 'PRIMARY KEY' temp|sed 's/.*(//g'|sed 's/)\s*//g'`
        csql -u dba $db_name -c "select ${columns} from $name" >child
        if grep 'There are no results' child >/dev/null
        then
            return
        fi
        csql -u dba $db_name -c "select ${fcolumns} from $fname" >father
        sed -i '1,/===========/d' child
        sed -i '/row[s]* selected/,$ d' child
        while read line
        do
            if [ `grep "$line" father|wc -l` -eq 0 ]
            then
               write_nok "$line violate fk constraint"
               break
            fi
        done <child
    done <temp1
   
    delete_file temp temp1 child father 
}

function rqg_table_column_attribute_check()
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
       rqg_pk_check $tname $db_name
       rqg_isNotNull_check $tname $db_name
       rqg_unique_check $tname $db_name
       rqg_fk_check $tname $db_name
    done <tables

    delete_file tables tables.log    
}

function rqg_createdb()
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

function rqg_check_db()
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

function rqg_do_standAlone_vacuum()
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

function rqg_check_space()
{
   #TODO 
}



