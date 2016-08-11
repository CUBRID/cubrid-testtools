#!/bin/bash
set -x
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
scenario_category=""
scenario_full_name=""
scenario_update_yn=""
result_copy_yn=""
config_file_ext=""
config_file_main=""
log_dir=""
log_filename=""
cubrid_root_dir=""
scenario_repo_root=""
cubrid_ver_p1=""
cubrid_ver_p2=""
cubrid_ver_p3=""
cubrid_ver_p4=""
cubrid_ver_prefix=""
cubrid_ver=""
db_charset=""
os_type=""
ha_mode_yn=""
is_support_ha=""
scenario_alias=""
need_make_locale=""
test_data_file=""
interface_type=""
alias ini="sh ${CTP_HOME}/bin/ini.sh"


function usage ()
{
     echo ""
     echo "Usage: $0 -s <type of scenario>
           -f <test configuration file"
     echo ""
}

while getopts "s:f:h:" opt; do
     case $opt in
     s)
         scenario_category="$OPTARG"
         ;;
     f)
         config_file_main="$OPTARG"
         ;;
     \?)
         usage
         exit 1
         ;;
     esac
done

if [ $# -eq 0 ];then
    usage
    exit 1
fi


function get_curr_timestamp()
{
    cur=$(date +%s)
    echo $cur
}

function do_init()
{
    curDir=`pwd`
    db_name=basic
    cubrid_bits=64
    scenario_update_yn=no
    result_copy_yn=no
    config_file_ext="test_default.xml"
    log_dir=${CTP_HOME}/result/${scenario_category}/current_runtime_log
    result_dir=${CTP_HOME}/sql/result
    log_filename=cqt.log
    cubrid_root_dir=$CUBRID
    scenario_repo_root=$HOME/dailyqa
    cubrid_ver_p1=""
    cubrid_ver_p2=""
    cubrid_ver_p3=""
    cubrid_ver_p4=""
    cubrid_ver_prefix=""
    cubrid_ver=""
    db_charset=""
    ha_mode_yn="no"
    is_support_ha="no"

    #check CUBRID environment
    [ ! -d "$CUBRID" ] && echo "please make sure your build is installed" && exit 1
    [ ! -f "$config_file_main" ] && echo "please confirm your conf file path!"  && exit 1

    # set os
    os_str=`uname`
    tmp=`echo $os_str | grep 'CYGWIN' | wc -l`
    if [ $tmp -eq 1 ];then
       os_type='Windows'
    else
       os_type=$os_str
    fi
  
    #unit full name
    uniteName ${scenario_category}
 
    #parse build version
    init_cubrid_version

    #init log file
    if [ ! -d $log_dir ];then
       mkdir $log_dir
    fi

    #init result folder
    if [ ! -d $result_dir ];then
       mkdir $result_dir
    fi

    cd $log_dir
    fileName=${scenario_category}_${cubrid_ver}_`get_curr_timestamp`.log
    touch $fileName
    log_filename=${log_dir}/${fileName}

    scenario_repo_root=`ini -s sql ${config_file_main} scenario`
    [ ! -d "$scenario_repo_root" -a ! -f "$scenario_repo_root" ] && echo "please make sure your scenario directory" && exit 1

    scenario_alias=`ini -s sql ${config_file_main} category_alias`
    if [ -z "$scenario_alias" ]
    then
	scenario_alias=${scenario_category}
    fi

    need_make_locale=`ini -s sql ${config_file_main} need_make_locale`
    if [ -z "$need_make_locale" ];then
         need_make_locale="yes"
    fi
  
    test_data_file=`ini -s sql ${config_file_main} data_file` 

    is_support_ha_mode=`cubrid|grep heartbeat|grep -v grep|wc -l`
    if [ $is_support_ha_mode -ne 0 ]
    then
	 is_support_ha="yes"
    fi

    if [ "${sql_interface_type}" ];then
         interface_type=${sql_interface_type}	
    fi

    cd $curDir
}

function uniteName()
{
    scenarioType=$1
    if [ "$scenarioType" == "site" ]||[ "$scenarioType" == "kcc" ]||[ "$scenarioType" == "neis05" ]||[ "$scenarioType" == "neis08" ]
    then
         scenario_full_name="site"
    else
         scenario_full_name="$scenarioType"
    fi
}

function clean_log_cores()
{
     rm -rf "$CUBRID/logs/*" 2>&1 > /dev/null
     find "$CUBRID" "${CTP_HOME}" -type f -name "core*"|xargs -i rm {}
}

function do_clean()
{
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

     #reset cubrid conf files
     reset_cubrid_files 
}

function init_cubrid_version()
{
     cubrid_ver=`cubrid_rel | grep CUBRID | awk -F'(' '{print $2}' |  awk -F')' '{print $1}'`
     version_type=`cubrid_rel | grep debug | wc -l`
     cubrid_bits=`cubrid_rel | grep CUBRID|awk -F'(' '{print $3}'|awk  '{print $1}'`
     cubrid_ver_p1=`echo $cubrid_ver | cut -d . -f 1 | grep -oE "[[:digit:]]{1,}"`
     cubrid_ver_p2=`echo $cubrid_ver | cut -d . -f 2`
     cubrid_ver_p3=`echo $cubrid_ver | cut -d . -f 3`
     cubrid_ver_p4=`echo $cubrid_ver | cut -d . -f 4`
     cubrid_ver_prefix=`echo ${cubrid_ver%.*}`
     cubrid_bits=${cubrid_bits%bit}
  
     if [ "$scenario_full_name" == "medium" ];then
          db_name="mdb"
     elif [ "$scenario_full_name" == "site" ];then
          db_name="${scenario_category}"
     else
          db_name="basic"
     fi
}

function remove_shared_memory()
{
     for x in `ipcs -a|grep $USER|awk '{print $2}'`
     do
         ipcrm -m $x
     done
}

function remove_all_ipc_segments()
{               
     if [ -e $current_dir/segments ]; then
           keys=`cat $current_dir/segments`
           for key in ${keys}
           do
                   if [ "x${key}" != "x" ]; then
                           exist=`ipcs | grep ${key}`
                           if [ "x${exist}" != "x" ]; then
                                   echo "remove key 0x00"${key}
                                   ipcrm -M 0x00${key}
                           fi
                   fi
           done;

           rm $current_dir/segments
     fi
}

function reset_cubrid_files()
{
     curDir=`pwd`
     name="forFun"
     retscript=$1
     cd $CUBRID/conf
     if [ -f "cubrid_broker.conf.$name" ];then
     	cp cubrid.conf.$name cubrid.conf
     else
   	cp cubrid.conf cubrid.conf.$name
     fi
     
     if [ -f "cubrid_broker.conf.$name" ];then
     	cp cubrid_broker.conf.$name cubrid_broker.conf
     else
   	cp cubrid_broker.conf cubrid_broker.conf.$name 
     fi
   
     if [ -f "cubrid_ha.conf.$name" ]; then
           cp cubrid_ha.conf.$name cubrid_ha.conf
     else
   	cp cubrid_ha.conf cubrid_ha.conf.$name
     fi

     cd $curDir

}

function config_cubrid_without_ha()
{
     build_ver_type=""
     cubrid_conf_para=`ini -s "sql/cubrid.conf" --separator="||" ${config_file_main}`
     if [ "$cubrid_conf_para" ];then
     	ini -s common -u "${cubrid_conf_para}" $CUBRID/conf/cubrid.conf
     fi
     ini -s service -u "service=server,broker" $CUBRID/conf/cubrid.conf
     cubrid_broker_shm=`ini -s "sql/cubrid_broker.conf/broker" --separator="||" ${config_file_main}` 
     if [ "$cubrid_broker_shm" ];then
     	ini -s "broker" -u $cubrid_broker_shm $CUBRID/conf/cubrid_broker.conf
     fi

     cubrid_broker_conf_para=`ini -s "sql/cubrid_broker.conf/%BROKER1" --separator="||" ${config_file_main}`
     cubrid_broker_conf_queryeditor_para=`ini -s "sql/cubrid_broker.conf/%query_editor" --separator="||" ${config_file_main}`
     is_valid_section_broker1=`cat $CUBRID/conf/cubrid_broker.conf|grep '\[\%BROKER1\]'|grep -v '#'|wc -l`
     is_valid_section_queryeditor=`cat $CUBRID/conf/cubrid_broker.conf|grep '\[\%query_editor\]'|grep -v '#'|wc -l`
     
     if [ "$cubrid_broker_conf_queryeditor_para" ] && [ $is_valid_section_queryeditor -ne 0 ];then
     	ini -s "%query_editor" -u "${cubrid_broker_conf_queryeditor_para}" $CUBRID/conf/cubrid_broker.conf
     fi
 
     if [ "$cubrid_broker_conf_para" ] && [ $is_valid_section_broker1 -ne 0 ];then	
     	ini -s "%BROKER1" -u "${cubrid_broker_conf_para}" $CUBRID/conf/cubrid_broker.conf
     fi

}

function config_qa_tool()
{
     curDir=`pwd`
     if [ "$interface_type" == "cci" ];then
	
	cd $CTP_HOME/sql_by_cci
        echo ""> interface_verify.h 
	sh compile.sh

     else
     	build_ver_type=""
     	qa_db_xml_path=${CTP_HOME}/sql/configuration/Function_Db/${db_name}_qa.xml
     	avaliable_broker_port=`awk '/SERVICE[[:space:]]*=[[:space:]]*ON/, /BROKER_PORT/' $CUBRID/conf/cubrid_broker.conf|grep BROKER_PORT|grep -v '#'|awk -F '=' '{print $2}'|head -1| tr -d ' '`
     	db_url="<dburl>jdbc:cubrid:localhost:${avaliable_broker_port}:${db_name}:::</dburl>"
     	sed -i "s#<dburl>.*</dburl>#$db_url#g" $qa_db_xml_path
     	if [ "$cubrid_bits" == "32" ];then
        	  build_ver_type="32bits"
     	else
        	  build_ver_type="Main"
     	fi
     	sed -i "s#<version>.*</version>#<version>$build_ver_type</version>#g" $qa_db_xml_path
     fi

     cd $curDir
}


function config_cubrid_ha()
{
     echo "start config ha"
     if [ "$is_support_ha" != "yes" ];then
        return
     fi

     cnt=`cat $CUBRID/conf/cubrid.conf | grep -v "#" | grep ha_mode | grep -E 'on|yes' | wc -l `
     hasConfigHA=`ini -s "sql/cubrid_ha.conf" ${config_file_main} ha_mode`
     if [ "$hasConfigHA" == "yes" ] || [ "$hasConfigHA" == "on" ]
     then
  	if [ "$cnt" -gt 0 ]
  	then	
  	     cubrid_ha_para=`ini -s "sql/cubrid_ha.conf" ${config_file_main} --separator="||"`
  	     ini -s common -u "ha_db_list=$db_name||$cubrid_ha_para" $CUBRID/conf/cubrid_ha.conf
  	     ha_mode_yn="yes"
  	fi
     fi
}

function get_random_number {
     random_number=$((RANDOM%($2-$1+1)+$1))
     valid_port=0
  
     while [ $valid_port -eq 0 ]; do
           is_use=`netstat -ant | awk '{print $4}' | grep -e "\:${random_number}$"`
           if [ -z "$is_use" ]; then
               valid_port=$random_number
           else
               random_number=$((RANDOM%($2-$1+1)+$1))
           fi
     done
     echo $random_number
}


function make_locale()
{
     curDir=`pwd`
     if [ "$need_make_locale" == "no" ];then
        echo "Don't need  make locale since you configure need_make_locale=no !"
        return
     fi

     version_type=`cubrid_rel | grep debug | wc -l`
 
     if [ $cubrid_ver_p1 -eq 8 ]; then
             # cubrid_ver_p1 is equal 8
             if [ $cubrid_ver_p2 -lt 4 ]; then
                     echo 1
                     return
             else
                     if [ $cubrid_ver_p3 -lt 9 ]; then
                             echo 1
                             return
                     fi
             fi
     fi
 
     if [ "$os_type" == "Windows" ]
     then
	  if [ "$scenario_full_name" != "medium" -a "$scenario_full_name" != "site" ];then
               echo "make locale now"
               mv $CUBRID/conf/cubrid_locales.txt $CUBRID/conf/cubrid_locales_bak.txt
               cp $CUBRID/conf/cubrid_locales.all.txt $CUBRID/conf/cubrid_locales.txt
               cd $CUBRID/bin

               batPath=`cygpath $CUBRID`
               if [ $version_type -eq 0 ];then
                    $batPath/bin/make_locale.bat /release
               else
                    $batPath/bin/make_locale.bat /debug
               fi
          fi
                       
     else
          echo "make locale now"
          mv $CUBRID/conf/cubrid_locales.txt $CUBRID/conf/cubrid_locales_bak.txt
          cp $CUBRID/conf/cubrid_locales.all.txt $CUBRID/conf/cubrid_locales.txt
          cd $CUBRID/bin

          if [ $version_type -eq 0 ]
 	  then
 	       sh make_locale.sh -t $cubrid_bits
 	  else
 	       sh make_locale.sh -t $cubrid_bits -m debug
 	  fi
     fi

     cd $curDir
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

function do_create_db()
{
     echo "MAKE $db_name DATABASE (default size)..."
     curDir=`pwd`
     mkdir -p ${cubrid_root_dir}/databases
     cd $cubrid_root_dir/databases
     if [ ! -d $db_name ]
     then
  	mkdir -p $db_name
  	cd $db_name
     else
  	rm -rf $db_name/* 2>&1 > /dev/null
          cd $db_name
     fi 
 
   
     if [ $cubrid_ver_p1 -ge 9 -a $cubrid_ver_p2 -gt 1 ] || [ $cubrid_ver_p1 -ge 10 ]
     then
          echo "cubrid createdb $db_name $db_charset"
          cubrid createdb $db_name $db_charset 2>&1 >> $log_filename
     else
          echo "cubrid createdb $db_name"
          cubrid createdb $db_name 2>&1 >> $log_filename
     fi
  
     if [ "${scenario_full_name}" == "medium" ];then
          make_db_data mdb
     elif [ "${scenario_full_name}" == "site" ];then
          make_db_data ${scenario_category} 
     else
	  make_sql_db_data
     fi
    
     cd $curDir
}

function optimize_db()
{
     echo "optimizedb $1..."
     cubrid optimizedb $1 2>&1 >> $log_filename
     sleep 1
}

function start_db()
{
     echo "start database $1"
     cubrid service stop
     sleep 1
     cubrid service start
     sleep 1
     echo "start database $1"
     cnt=`cat $CUBRID/conf/cubrid.conf | grep -v "#" | grep ha_mode | grep -E 'on|yes' | wc -l `
     if [ "$cnt" -gt 0 ]
     then
         cubrid hb start $1 2>&1 >> $log_filename
     else
         cubrid server start $1 2>&1 >> $log_filename
     fi
     sleep 2
}

function delete_db()
{
     echo "delete database $1"
     cubrid deletedb $1 2>&1 >> $log_filename
     sleep 2
   
     #delete db folder 
     cd $cubrid_root_dir/databases
     if [ -d "$db_name" ];then
	rm -rf $db_name
     fi
      
}

function restart_broker()
{
     echo "restart broker..."        
     cubrid broker restart 2>&1 >> $log_filename
     sleep 2
}

function make_sql_db_data()
{
     curDir=`pwd`
     echo "Load Java Stored Procedure Classes"
     cd ${CTP_HOME}/sql/function/stored_procedure/src
     rm *.class 2>&1 > /dev/null
     "$JAVA_HOME/bin/javac" -cp $CUBRID/jdbc/cubrid_jdbc.jar *.java
     
     for clz in $(ls *.class);do
 	echo "Load ${clz}..."
 	loadjava $db_name $clz 2>&1 >> $log_filename
     done
 
     rm *.class 2>&1 >/dev/null
     cd $curDir
  
}

function make_site_db_data()
{
     echo "todo"
}

function check_status()
{
    db_name=$1
    if [ "$ha_mode_yn" == "yes" ];then
        res=`cubrid changemode $db_name 2>&1`
        res=`echo $res | grep -e "active" -e "not configured for HA"`
        m=0
        while [ -z "$res" ]; do
             if [ $m -gt 50 ]; then
                     break
             else    
                     sleep 2
                     echo "waiting for $db_name become active..."
                     res=`cubrid changemode $db_name`
                     res=`echo $res | grep "active"`
                     m=`expr $m + 1`
             fi
        done
    else
   	   server_status=`cubrid server status|grep $db_name|grep -v grep|wc -l`
   	   broker_status=`cubrid broker status|grep PID|wc -l`
   	   m=0
   	   while [ $server_status -ne 1 -o $broker_status -eq 0 ]; do
   	          if [ $m -gt 50 ]; then
   	                  break
   	          else
   	                  sleep 2
   	                  echo "waiting for server and broker become active..."
   	  		server_status=`cubrid server status|grep $db_name|grep -v grep|wc -l`
   	  		broker_status=`cubrid broker status|grep PID|wc -l`
   	                  m=`expr $m + 1`
   	          fi
   	   done
     fi
}


function make_db_data()
{
     curDir=`pwd`
     dataFileName=$1
     echo "Load Initial Data..."
     if [ -z "$test_data_file" ];then
        echo "Not found data file(the current is $test_data_file) to load,
              please set the correct value for data_file in $config_file_main"
        exit 1
     fi
     data_file=""
     if [ -d $test_data_file ]
     then
         data_file=${test_data_file}/${dataFileName}.tar.gz
         if [ ! -f $data_file ];then
             echo "please confirm your data file exist(the current directory $test_data_file) does not include tar! "
             exit 1
         fi
	     
     elif [ -f $test_data_file ]
     then
         data_file=$test_data_file
     else
         echo "Not found data file(the current is $test_data_file) to load, 
               please set the correct value for data_file in $config_file_main"
         exit 1
     fi
         
     cd $cubrid_root_dir/databases/$db_name
     cp $data_file .
 
     tar -zxvf mdb.tar.gz
     cubrid loaddb -s ${db_name}_schema -i ${db_name}_indexes -d ${db_name}_objects -u dba ${db_name} >> $log_filename
     optimize_db $db_name
 
     rm *.gz 2>&1 >/dev/null
     cd $curDir
    
}

function do_configure()
{
     curDir=`pwd`
     #get charset config
     db_charset=`ini -s sql ${config_file_main} db_charset`
     if [ -z "$db_charset" ];then
     	if [ $cubrid_ver_p1 -ge 10 ] && [ "$scenario_category" == "site" ]
     	then
  		db_charset="ko_KR.euckr"
     	else
  		db_charset="en_US.iso88591"
          fi
     fi 
     
     LD_LIBRARY_PATH=$LD_LIBRARY_PATH
  
     java_version=`file $JAVA_HOME/bin/java|grep 64-bit|wc -l`
     if [ $java_version -ne 0 ];then
  	LD_LIBRARY_PATH=$JAVA_HOME/jre/lib/amd64:$JAVA_HOME/jre/lib/amd64/server:$LD_LIBRARY_PATH
     else
  	LD_LIBRARY_PATH=$JAVA_HOME/jre/lib/i386/:$JAVA_HOME/jre/lib/i386/client:$LD_LIBRARY_PATH
     fi
  
     export LD_LIBRARY_PATH
     export LC_ALL=en_US.UTF-8
     export CUBRID_CHARSET=$db_charset
     export CUBRID_LANG=en_US
  
     #set cubrid conf
     config_cubrid_without_ha
  
     #config HA env, to check if need ha mode, if need it, config it
     config_cubrid_ha

     #config qa tool
     config_qa_tool
     
     #make locale
     make_locale
  
     #config QA tool
     if [ "$cubrid_ver_p4" -a "$cubrid_ver_prefix" ]
     then
  	ini -u "dbbuildnumber=$cubrid_ver_p4||dbversion=$cubrid_ver_prefix" ${CTP_HOME}/sql/configuration/local.properties 
     fi
  
     #get config file
     config_file=`ini -s sql ${config_file_main} config_file`
     if [ -n "$config_file" ];then
  	config_file_ext=$config_file
     fi
  
     cd $curDir
  
}


function do_test()
{
     curDir=`pwd`
     #start  server
     start_db $db_name
 
     #start broker
     restart_broker
 
     #check status
     check_status $db_name
 
     CPLIB=$CTP_HOME/sql/lib
     CPCLASSES=""
     separator=":"
    (
     if [ "$os_type" == "Linux" -o "$os_type" = "AIX" ]
     then
         separator=":"
     else
         separator=";"
         CPLIB=`cygpath -wp $CPLIB`
         export CTP_HOME=`cygpath -w $CTP_HOME`
     fi
     cd $CPLIB 
     for clz in $(ls *.jar);do
 	     CPCLASSES=${CPCLASSES}${separator}$CPLIB/$clz
     done 
   
     if [ ! -f ${scenario_repo_root} ];then
         lastChar=${scenario_repo_root:${#scenario_repo_root}-1:1}
         if [ "$lastChar" != "/" -a "$lastChar" != "\\" ] && [ "$sql_interactive" != "cci" ];then
            scenario_repo_root=${scenario_repo_root}/
         fi
     fi

     javaArgs="${scenario_repo_root}?db=${db_name}_qa"
     
     if [ "$sql_interactive" == "yes" ];then
         (export CLASSPATH=${CLASSPATH}${separator}${CPCLASSES}
          export log_file_in_interactive=$log_filename
          export sceanrio_type_in_interactive=${scenario_category}
          export scenario_alias_in_interactive=${scenario_alias}
          export bits_in_interactive=${cubrid_bits}
          export db_name_in_interactive=$db_name
          export client_charset_in_interactive=$config_file_ext
          export PS1="sql> ";cd ${scenario_repo_root}; source ${CTP_HOME}/sql/bin/interactive.sh; help; bash --posix)
	
          #do clean for interactive mode
          do_clean
     elif [ "$interface_type" == "cci" ];then
	  port=`ini -s "%BROKER1"  $CUBRID/conf/cubrid_broker.conf BROKER_PORT`
	  $CTP_HOME/sql_by_cci/ccqt $port $db_name ${scenario_alias} ${cubrid_bits} ${scenario_repo_root} $CTP_HOME 2>&1 >> $log_filename 
	  cd $curDir
	  
     else   
     	  "$JAVA_HOME/bin/java" -Xms1024m -XX:+UseParallelGC -classpath "${CLASSPATH}${separator}${CPCLASSES}" com.navercorp.cubridqa.cqt.console.ConsoleAgent runCQT ${scenario_category} ${scenario_alias} ${cubrid_bits} $config_file_ext $javaArgs 2>&1 >> $log_filename 
          cd $curDir
     fi
    )
}

function generate_summary_info()
{
    summaryFolder=$1
    if [ "x${summaryFolder}" != "x" -a -d "$summaryFolder" ];then
	summaryFile=${summaryFolder}/summary.info
        failCount=`cat $summaryFile|grep "NOK"|wc -l`
	totalCount=`cat ${log_filename}|grep "TOTAL_COUNT"|awk -F ':' '{print $2}'|tr -d ' '`
	totalElapseTime=`cat ${log_filename}|grep "TOTAL_ELAPSE_TIME"|awk -F ':' '{print $2}'|tr -d ' '`
	let "succCount=totalCount-failCount" 
	echo "cubrid_build_id=$cubrid_ver" >> ${summaryFolder}/summary_info
	echo "execute_date=`date +"%Y-%m-%d %H:%M:%S"`" >> ${summaryFolder}/summary_info
	echo "Num_total=$totalCount" >> ${summaryFolder}/summary_info
	echo "Num_test=$totalCount" >> ${summaryFolder}/summary_info
	echo "Num_success=$succCount" >> ${summaryFolder}/summary_info
	echo "Num_fail=$failCount" >> ${summaryFolder}/summary_info
	echo "Test_cat=$scenario_alias" >> ${summaryFolder}/summary_info
	echo "Test_upcat=function" >> ${summaryFolder}/summary_info
	echo "OS=`uname`" >> ${summaryFolder}/summary_info
	echo "Bit=$cubrid_bits" >> ${summaryFolder}/summary_info
	echo "Elapse_time=$totalElapseTime" >> ${summaryFolder}/summary_info

        echo ""
	echo "-----------------------"
        echo "Fail:$failCount"
        echo "Success:$succCount"
        echo "Total:$totalCount"
        echo "Elapse Time:$totalElapseTime"
        echo "Test Log:$log_filename"
        echo "Test Result Directory:$summaryFolder"
        echo "-----------------------"
        echo ""
    fi

}


function do_summary_and_clean()
{
     #get summary info
     [ "$sql_interactive" == "yes" ] && return

     coreCount=0
     resultDirTemp=`cat ${log_filename}|grep "^Result Root Dir"|head -n 1`
     resultDir=${resultDirTemp#*:}
     if [ "$interface_type" == "cci" ];then
	 generate_summary_info $resultDir	          		
     else
     	resultSummaryInfoFile=${resultDir}/main.info
     	[ ! -f $resultSummaryInfoFile ] && echo "No Results!! please confirm your scenario path include valid case script(the current scenairo path:$scenario_repo_root)" && exit 1
     	failNum=`cat $resultSummaryInfoFile|grep 'fail:'|awk -F ':' '{print $2}'`
     	succNum=`cat $resultSummaryInfoFile|grep 'success:'|awk -F ':' '{print $2}'`
     	totalNum=`cat $resultSummaryInfoFile|grep 'total:'|awk -F ':' '{print $2}'`
     	elapseTime=`cat $resultSummaryInfoFile|grep 'totalTime:'|awk -F ':' '{print $2}'`
     	
     	echo ""
     	echo "-----------------------"
     	echo "Fail:$failNum"
     	echo "Success:$succNum"
     	echo "Total:$totalNum"
     	echo "Elapse Time:$elapseTime"
     	echo "Test Log:$log_filename"
     	echo "Test Result Directory:$resultDir"
     	echo "-----------------------"
     	echo ""
     fi

     #check core
     coreFiles=$(find "$CUBRID" "${CTP_HOME}" -type f -name "core*")
     while read -r file;
     do
	 isCore=`file "$file"|grep 'core file'|grep -v grep|wc -l`
         if [ $isCore -ne 0 ];then
             echo "CORE_FILE:$file"
             let "coreCount=coreCount+1"
         fi
     done <<EOF
$coreFiles
EOF
     
     #print core flag
     if [ $coreCount -ne 0 ];then
          echo "test_error=Y" >> ${resultDir}/summary_info
     else
	  do_clean
     fi

     echo "-----------------------"
     echo "Testing End!"
     echo "-----------------------"
}


#init environment for testing
do_init

#clean environment for testing
do_clean

#config envrionment
do_configure

#prepare db
do_create_db

#run test
do_test

#print summary info
do_summary_and_clean

