#!/bin/bash
# 
#Copyright (c) 2016, Search Solution Corporation? All rights reserved.
#
#Redistribution and use in source and binary forms, with or without 
#modification, are permitted provided that the following conditions are met:
#
#  * Redistributions of source code must retain the above copyright notice, 
#    this list of conditions and the following disclaimer.
#
#  * Redistributions in binary form must reproduce the above copyright 
#    notice, this list of conditions and the following disclaimer in 
#    the documentation and/or other materials provided with the distribution.
#
#  * Neither the name of the copyright holder nor the names of its contributors may be used to endorse or promote products 
#    derived from this software without specific prior written permission.
#
#THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, 
#INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE 
#DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, 
#SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR 
#SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, 
#WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE 
#USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

export CTP_HOME=$(cd $(dirname $(readlink -f $0))/../..; pwd)
db_name=""
cubrid_bits=""
cubrid_ver_p1=""
cubrid_ver_p2=""
cubrid_ver_p3=""
cubrid_ver_p4=""
cubrid_ver_prefix=""
version_type=""
config_file=""
log_dir=""
cubrid_ver=""
scenario_category="jdbc"
log_filename=""
scenario=""
jdbc_config_file=""
db_charset=""
CPCLASSES=""
alias ini="sh ${CTP_HOME}/bin/ini.sh"


function usage ()
{
     echo ""
     echo "Usage: $0  <configuration file of jdbc test>"
     echo ""
}

if [ $# -eq 0 ];then
    usage
    exit 1
fi

config_file=$1

function do_init()
{
	curDir=`pwd`
	db_name="jdbcdb"
	db_charset="en_us"
	cd ${CTP_HOME}/conf
    log_dir=${CTP_HOME}/result/jdbc/current_runtime_logs
	if [ "${BUILD_SCENARIOS}" ];then
		scenario_category=${BUILD_SCENARIOS}
	fi
	
	#parse build version
	init_cubrid_version
	
	#init log file
    if [ ! -d $log_dir ];then
       mkdir -p $log_dir
    fi
    
    if [ !"$BUILD_ID" ];then
    	export BUILD_ID=${cubrid_ver}
    	export BUILD_BITS=${cubrid_bits}
    fi
    
    cd $log_dir
    fileName=${scenario_category}_${cubrid_ver}_`get_curr_timestamp`.log
    touch $fileName
    log_filename=${log_dir}/${fileName}
    
    scenario=`ini -s common ${config_file} scenario`
    [ ! -d "$scenario" -a ! -f "$scenario" ] && echo "please make sure your scenario directory" && exit 1
   
    jdbc_config_file=${scenario}/jdbc.properties
    cd $curDir
}

function get_curr_timestamp()
{
    cur=$(date +%s)
    echo $cur
}

function init_cubrid_version()
{
     cubrid_ver=`cubrid_rel | grep CUBRID | awk -F'(' '{print $2}' |  awk -F')' '{print $1}'`
     version_type=`cubrid_rel | grep debug | wc -l`
     cubrid_bits=`cubrid_rel | grep CUBRID|awk -F'(' '{print $3}'|awk  '{print $1}'`
     cubrid_bits="${cubrid_bits}s"
     
     cubrid_ver_p1=`echo $cubrid_ver | cut -d . -f 1 | grep -oE "[[:digit:]]{1,}"`
     cubrid_ver_p2=`echo $cubrid_ver | cut -d . -f 2`
     cubrid_ver_p3=`echo $cubrid_ver | cut -d . -f 3`
     cubrid_ver_p4=`echo $cubrid_ver | cut -d . -f 4`
     cubrid_ver_prefix=`echo ${cubrid_ver%.*}`
}

function stop_db()
{
     echo "stop database $1"
     cnt=`cat $CUBRID/conf/cubrid.conf | grep -v "#" | grep ha_mode | grep -E 'on|yes' | wc -l `
     if [ "$is_support_ha" == "yes" -a $cnt -gt 0 ]
     then
         cubrid hb stop $1 2>&1 > /dev/null
     else
         cubrid server stop $1 2>&1 > /dev/null
     fi

     sleep 2
     cubrid service stop 2>&1 > /dev/null
}

function delete_db()
{
     echo "delete database $1"
     cubrid deletedb $1 2>&1 >> $log_filename
     sleep 2

     #delete db folder 
     cd $CUBRID/databases
     if [ -d "$db_name" ];then
        rm -rf $db_name
     fi

}

function clean_log_cores()
{
     rm -rf "$CUBRID/logs/*" 2>&1 > /dev/null
     find "$CUBRID" "${CTP_HOME}" -type f -name "core*"|xargs -i rm {}
}

function remove_shared_memory()
{
     for x in `ipcs -a|grep $USER|awk '{print $2}'`
     do
         ipcrm -m $x
     done
}

function do_clean()
{
     curDir=`pwd`
     #stop process 
	 stop_db $db_name
	 
	 #delete database
     delete_db $db_name
     
     #clean log files and core
     clean_log_cores
     
     #kill cub for the current user
     pkill cub
     
     #kill share ports
     remove_shared_memory
     
     cd $curDir
}

function do_prepare()
{
     curDir=`pwd`
     cubrid_conf_para=`ini -s "jdbc/cubrid.conf" --separator="||" ${config_file}`
     if [ "$cubrid_conf_para" ];then
         ini -s common -u "${cubrid_conf_para}" $CUBRID/conf/cubrid.conf
     fi
   
     ini -s service -u "service=server,broker" $CUBRID/conf/cubrid.conf
   
     cubrid_broker_shm=`ini -s "jdbc/cubrid_broker.conf/broker" --separator="||" ${config_file}`
     if [ "$cubrid_broker_shm" ];then
         ini -s "broker" -u $cubrid_broker_shm $CUBRID/conf/cubrid_broker.conf
     fi
   
     cubrid_broker_conf_para=`ini -s "sql/cubrid_broker.conf/%BROKER1" --separator="||" ${config_file}`
     cubrid_broker_conf_queryeditor_para=`ini -s "sql/cubrid_broker.conf/%query_editor" --separator="||" ${config_file}`
     is_valid_section_broker1=`cat $CUBRID/conf/cubrid_broker.conf|grep '\[\%BROKER1\]'|grep -v '#'|wc -l`
     is_valid_section_queryeditor=`cat $CUBRID/conf/cubrid_broker.conf|grep '\[\%query_editor\]'|grep -v '#'|wc -l`
   
     if [ "$cubrid_broker_conf_queryeditor_para" ] && [ $is_valid_section_queryeditor -ne 0 ];then
        ini -s "%query_editor" -u "${cubrid_broker_conf_queryeditor_para}" $CUBRID/conf/cubrid_broker.conf
     fi

     if [ "$cubrid_broker_conf_para" ] && [ $is_valid_section_broker1 -ne 0 ];then
        ini -s "%BROKER1" -u "${cubrid_broker_conf_para}" $CUBRID/conf/cubrid_broker.conf
     fi
     
     echo "MAKE $db_name DATABASE (default size)..."
     cd $CUBRID/databases
	 if [ $cubrid_ver_p1 -ge 9 -a $cubrid_ver_p2 -gt 1 ] || [ $cubrid_ver_p1 -ge 10 ]
     then
          echo "cubrid createdb $db_name $db_charset"
          cubrid createdb $db_name ${db_charset} 2>&1 >> $log_filename
     else
          echo "cubrid createdb $db_name"
          cubrid createdb $db_name 2>&1 >> $log_filename
     fi
     
     echo "start database $db_name"
     cnt=`cat $CUBRID/conf/cubrid.conf | grep -v "#" | grep ha_mode | grep -E 'on|yes' | wc -l `
     if [ "$cnt" -gt 0 ]
     then
         cubrid hb start $db_name 2>&1 >> $log_filename
     else
         cubrid server start $db_name 2>&1 >> $log_filename
     fi
     
     echo "restart broker..."        
     cubrid broker restart 2>&1 >> $log_filename
     
     sleep 2
     port=`cubrid broker status -b|grep -vE 'OFF|off'|grep -E 'broker1|query_editor'|awk '{print $4}'`
     jdbc_url="jdbc:cubrid:localhost:${port}:${db_name}:::"
     ini -u "jdbc.url=${jdbc_url}" $jdbc_config_file
     ini -u "jdbc.port=${port}" $jdbc_config_file
     ini -u "jdbc.dbname=${db_name}" $jdbc_config_file
     
     rm ${scenario}/lib/cubrid_jdbc.jar 2>&1 >> $log_filename
     cp $CUBRID/jdbc/cubrid_jdbc.jar ${scenario}/lib/ 2>&1 >> $log_filename
     
     #do compile
     cd $scenario
     
     for clz in $(ls ./lib/*.jar);do
             CPCLASSES=${CPCLASSES}:$clz
     done
     
     echo "CLASSPATH ===> .:${CPCLASSES}" >> $log_filename
     find ${scenario} -type f -name "*.java"|xargs -t javac -cp ".:${CPCLASSES}" >> $log_filename

     cd $curDir
}

function do_test()
{
     curDir=`pwd`
     cd ${scenario}
	 java -cp ".:$CUBRID/jdbc/cubrid_jdbc.jar:./src:${CPCLASSES}:${CTP_HOME}/common/lib/cubridqa-common.jar:${CTP_HOME}/shell/lib/cubridqa-shell.jar:$CLASSPATH" com.navercorp.cubridqa.shell.main.JdbcLocalTest ${config_file} 2>&1 >> $log_filename     
     cd $curDir
}

function print_summary()
{
	  echo ""
      echo "==Test Finished!=="
      echo "log:${log_filename}"
      echo ""
}

#init environment for testing
do_init

#clean environment for testing
do_clean

#prepare for testing
do_prepare

#do testing
do_test

#print summary info
print_summary






