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

# In test mode (if run_mode variable is set 1), this function creates answer files of each test case in one scenario
# on the contrary, in answer mode(if run_mode is set 0), this function writes the test result in result file
# according to result of comparing with a test result and an answer 
set -x

## the begin time for case
begin_time=0
## the end time for case
end_time=0
#0: not count time  1: count time
need_count_time=0
cubrid_major=""
cubrid_minor=""
IGNORE_TEST_PERFORMANCE=""
#CUBRID_CHARSET=en_US.utf8
#CUBRID_CHARSET=ko_KR.euckr
#CUBRID_CHARSET=en_US
#echo $CUBRID_CHARSET
if [ ! -n "$CUBRID_CHARSET" ]
then
   CUBRID_CHARSET=en_US
fi

# get broker port from shell_config.xml
function get_broker_port_from_shell_config
{
  port=`awk '/<port>/' $init_path/shell_config.xml`
  port=${port#*>}
  port=${port%<*}
  echo $port
}

#get another available port 
function generate_port {
        generated_port=$1
        while true; do
                ((generated_port=${generated_port} + 1 ))
                is_use=`netstat -ant | awk '{print $4}' | grep -e "\:${generated_port}$"`

                if [ -z "$is_use" ]; then
                        break
                fi
        done
        echo ${generated_port}
}

function get_curr_second
{
	str=`date +%s`
	echo $(($str))
}

function get_curr_seconds
{
        str=`date +%s`
        str=`echo $str|sed 's/^0*//'`
        echo $(($str))
}



function get_curr_min
{
        str=`date +%M`
        str=`echo $str|sed 's/^0*//'`
        echo $(($str))
}


function get_curr_hour
{
        str=`date +%H`
        str=`echo $str|sed 's/^0*//'`
        echo $(($str))
}


function make_answer_or_compare_result
{
  if [ $run_mode -eq 0 ] 
  then
     answer_file="$case_name-$answer_no.answer"
     echo $temp_result > $answer_file
     let "answer_no = answer_no + 1"
  else
     echo $temp_result > $case_name-$answer_no.result
     if diff $case_name-$answer_no.answer $case_name-$answer_no.result
     then
        write_ok
     else 
        write_nok
     fi
     let "answer_no = answer_no + 1"
  fi
}

#pick_test_compat_version "optional_fils" "$drv_ver" 
#pick_test_compat_version "optional_fils" "$srv_ver" 
function pick_test_compat_version
{
   param_file=$1
   param_version=$2
   best_v1=0
   best_v2=0
   best_v3=0
   best_num=0

   if [ "$param_version" == "" ]
   then
      echo ""
      return 1 
   fi

   param_v1=`echo "${param_version}"|awk -F. '{print $1}'`
   param_v2=`echo "${param_version}"|awk -F. '{print $2}'`
   param_v3=`echo "${param_version}"|awk -F. '{print $3}'`
   ((param_num=${param_v1}\*100\*100 + ${param_v2}\*100 + ${param_v3}))
   while read line
   do
     v1=`echo "$line"|awk -F. '{print $1}'`
     v2=`echo "$line"|awk -F. '{print $2}'`
     v3=`echo "$line"|awk -F. '{print $3}'`
     ((tmp_num=$v1\*100\*100 + $v2\*100 + $v3))
     ((best_num=${best_v1}\*100\*100 + ${best_v2}\*100 + ${best_v3}))
     if [ $tmp_num -gt $param_num  ] || [ $tmp_num  -lt $best_num ] ;then continue; fi 
     best_v1=$v1; best_v2=$v2; best_v3=$v3
   done < ${param_file}
   
   if [ $best_v1 -ne 0 ]
   then  
        echo $best_v1.$best_v2.$best_v3
   else 
        echo ""
   fi 
}

function version_convert_to_number 
{
  param_version=$1
  if [ "$param_version" == "" ]
  then
     echo 0
  else
     param_v1=`echo "${param_version}"|awk -F. '{print $1}'`
     param_v2=`echo "${param_version}"|awk -F. '{print $2}'`
     param_v3=`echo "${param_version}"|awk -F. '{print $3}'`
    ((param_num=${param_v1}\*100\*100 + ${param_v2}\*100 + ${param_v3}))
     echo $param_num
  fi
}

#get_best_compat_file file srv_ver drv_ver
function get_best_compat_file
{
   suffix_os=""
   best_driver="" 
   best_server=""
   fileName=$1
   current_server=$2
   cci_driver=$3   
   newfilename=""
   
   if [ "$OS" = "Windows_NT" ]
   then
      suffix_os=_WIN
   elif [ "$OS" = "AIX" ]
   then
      suffix_os=_AIX
   fi

   #get best driver version based on the current server version 
   ls -l ${fileName}*_D* |grep -E "_D[0-9\.]+" | awk -F "_D" '{print $NF}' >listdrv
   if [ `cat listdrv |wc -l` -eq 0 ] 
   then
       if [ -f ${fileName}${suffix_os} ]
       then
           newfilename=${fileName}${suffix_os}
       elif [  -f ${fileName} ]
       then
           newfilename=${fileName}
       fi   
       
       echo "$newfilename"
       return 1
   fi  

   best_driver=`pick_test_compat_version "listdrv" $cci_driver `

   best_driver_num=`version_convert_to_number $best_driver`
   cci_driver_num=`version_convert_to_number $cci_driver`
   current_server_num=`version_convert_to_number  $current_server`
   
   if [ $cci_driver_num -gt $current_server_num ]
   then
     if [ $best_driver_num -lt $current_server_num ]
     then
        best_driver=""
     fi
   fi
   
   if [ "$best_driver" == "" ]
   then    
       if [ -f ${fileName}${suffix_os} ]
       then
           newfilename=${fileName}${suffix_os}
       elif [  -f ${fileName} ]
       then
           newfilename=${fileName}
       fi   
   else
       suffix2=_S${current_server}_D${best_driver} 
       suffix3=_D${best_driver}
       suffix4=""
       
       if [ -f ${fileName}${suffix_os}${suffix2} ]
       then
           newfilename=${fileName}${suffix_os}${suffix2}
       elif [ -f ${fileName}${suffix2} ]
       then
            newfilename=${fileName}${suffix2}
       elif [ -f ${fileName}${suffix_os}${suffix3} ]
       then
            newfilename=${fileName}${suffix_os}${suffix3}
       elif [ -f ${fileName}${suffix3} ]
       then    
            newfilename=${fileName}${suffix3}
       fi
       
       if [ "$newfilename" == "" ]
       then
           ls -l ${fileName}*|grep -E "_S[0-9\.]+_D${best_driver}" |awk -F '_S' '{print $2}'| awk -F '_D' '{print $1}' >listsrv
           if [ `cat listsrv |wc -l` -ne 0 ]
           then    
              best_server=`pick_test_compat_version "listsrv" $current_server `
              suffix4=_S${best_server}_D${best_driver}
           fi   
      
           if [ -f ${fileName}${suffix_os}${suffix4} ]
           then
           newfilename=${fileName}${suffix_os}${suffix4}
           elif [ -f ${fileName}${suffix4} ]
           then
           newfilename=${fileName}${suffix4}
           elif [ -f ${fileName}${suffix_os} ]
           then
           newfilename=${fileName}${suffix_os} 
           elif [ -f ${fileName} ]
           then
           newfilename=${fileName} 
           fi
       fi
   fi 
   echo  "$newfilename"

}


# After comparing two files, This function write the result int result files.
# Usage:
#        compare_result_between_files file1 file2 [error]

function compare_result_between_files
{

  cci_driver=""
  server=""

  if [ $# -lt 2 ]
  then
     write_nok "Please input two files to compare"
     return
  fi

  if [ -f $CUBRID/qa.conf ]
  then
     cci_driver=`grep 'CCI_Version' $CUBRID/qa.conf|awk -F= '{print $2}'`
     server=`grep 'Server_Version' $CUBRID/qa.conf|awk -F= '{print $2}'`
  fi
   
  left=`get_best_compat_file $1 $server $cci_driver`
  right=`get_best_compat_file $2 $server $cci_driver`

  if [ "$left" == "" ]
  then
     write_nok "Cannot find the proper file for $1 to compare"
     return
  fi

  if [ "$right" == "" ]
  then
     write_nok "Cannot find the proper file for $2 to compare"
     return
  fi

  dos2unix $left
  dos2unix $right

  echo "start to compare files: diff $left $right"  
  if [ "$3" = "error" ]
  then
        if diff $left $right -b
        then
                write_nok
                echo "diff $left $right failed" >> $result_file
                #diff $left $right -y >> $result_file
		diff $left $right -y |tee -a $result_file
        else
                write_ok
        fi
        let "answer_no = answer_no + 1"
  else
        if diff $left $right -b
        then
                write_ok
        else
                write_nok
                echo "diff $left $right failed" >> $result_file
                #diff $left $right -y >> $result_file
		diff $left $right -y |tee -a $result_file
        fi
        let "answer_no = answer_no + 1"
  fi
}

# Check DB status(start/stop)
# Usage:
#       db_status dbname

function db_status
{
  sqlx_mode=''
  isdbstart=`cub_commdb -P | grep "Server $1" | wc -l `
  if [ $isdbstart -eq 1 ]
  then
        sqlx_mode="-C"
  else
        sqlx_mode="-S"
  fi
}

# Execute SQL
# Usage: 
#        exec_sql dbname "insert into xoo values(1)" [options]

function exec_sql
{
  rtn=""
  db_status $1
  if [ -n "$3" ]
  then 
        if [ -n "$2" ]
        then 
                rtn=`csql $1 $sqlx_mode -c "$2" $3`
        else
                rtn=`csql $1 $sqlx_mode $3`
        fi
  else
        rtn=`csql $1 $sqlx_mode -c "$2" `
  fi
  
  if [ -n "$rtn" ]
  then
    echo $rtn
  fi
}

# Test execute SQL
# Usage:
#       test_exec_sql dbname "create class xoo" [options] [error]

function test_exec_sql
{
  rtn=""
  db_status $1

  if [ "$3" = "error" ]
  then 
        if rtn=`csql $1 $sqlx_mode -c "$2"` 
        then
                write_nok
        else
                write_ok
        fi
        echo "$rtn"
  elif [ "$3" != "error" -a -z "$4" ]
  then
        if [ -n "$2" ]
        then 
                if rtn=`csql $1 $sqlx_mode -c "$2" $3`
                then
                        write_ok
                else
                        write_nok
                fi
                echo "$rtn"
        else
                if rtn=`csql $1 $sqlx_mode $3`
                then
                        write_ok
                else
                        write_nok
                fi
                echo "$rtn"
        fi
  elif [ "$3" != "error" -a "$4" = "error" ]
  then
        if [ -n "$2" ]
        then 
                if rtn=`csql $1 $sqlx_mode -c "$2" $3`
                then
                        write_nok
                else
                        write_ok
                fi
                echo "$rtn"
        else 
                if rtn=`csql $1 $sqlx_mode $3`
                then
                        write_nok
                else
                        write_ok
                fi
                echo "$rtn"
        fi
  else
        if rtn=`csql $1 $sqlx_mode -c "$2"`
        then
                write_ok
        else
                write_nok
        fi
        echo "$rtn"
  fi
}

# Test execute command
# Usage:
#       test_exec_command "backupdb testdb" [error]

function test_exec_command
{
  if [ "$2" = "error" ]
  then 
        if $1
        then 
                write_nok
        else
                write_ok
        fi
  else
        if $1
        then 
                write_ok
        else
                write_nok
        fi
  fi
}

# This function writes the result for one case of a scenario in the result file when a test case is failed.
# Usage:
#       write_ok [description]

function write_ok 
{
  if [ -z "$1" ]
  then 
        echo "----------------- $case_no : OK"
        echo "$case_name-$case_no : OK" >>  ${cur_path}/$result_file
        let "case_no = case_no + 1"
  else 
        echo "----------------- $case_no : OK " $1
        echo "$case_name-$case_no : OK " $1 >>  ${cur_path}/$result_file
        let "case_no = case_no + 1"
  fi
}

# This function writes the result for one case of a scenario in the result file when a test case is success.
# Usage:
#       write_nok [description|filename]

function write_nok
{
  if [ -z "$1" ]
  then 
        echo "----------------- $case_no : NOK"
        echo "$case_name-$case_no : NOK" >> ${cur_path}/$result_file
        internal_err=`grep "Internal Error" $CUBRID/log/server/*.err | wc -l`
  	if [ $internal_err -gt 0 ]
 	then
  	    grep "Internal Error" $CUBRID/log/server/*.err >> ${cur_path}/$result_file
 	fi 
        let "case_no = case_no + 1"
  elif [ -f "$1" ]; 
  then
	echo "$case_name-$case_no : NOK"  >> ${cur_path}/$result_file
	cat $1 >> ${cur_path}/$result_file
	let "case_no = case_no + 1"
  else
        echo "----------------- $case_no : NOK" $1
        echo "$case_name-$case_no : NOK" $1 >> ${cur_path}/$result_file
        let "case_no = case_no + 1"
  fi
}

# This function removes temporary & log files

function release_broker_sharedmemory
{
    #broker_port=`get_broker_port_from_shell_config`
    #broker_sid=`ipcs | grep $broker_port | awk '{print $2}'`
    if [ "$OS" == "Linux" -o "$OS" == "AIX" ]; then
        broker_sid=`ipcs | grep $USER | awk '{print $2}'`
        if [ -n "$broker_sid" ];then
            arr_sid=($broker_sid)
            for sid in ${arr_sid[@]}
            do
                ipcrm -m $sid
            done
        fi
    fi
}

function count_time
{
  end_time=`get_curr_second`
  duration=$(($end_time-$begin_time))
  date_str=`date +"%Y-%m-%d"`
  time_str=`date +%H:%M:%S`
  cur_pwd=`pwd`
  echo $time_str----$cur_pwd---'time'=$duration >> ~/shell_cases_log/$date_str-time.log	
  if [ $duration -gt 7200 ]; then # 2hours
  	echo $time_str----$cur_pwd---$duration >> ~/shell_cases_log/$date_str-gt_2hours.log
  elif [ $duration -gt 3600 ]; then # 1hours
  	echo $time_str----$cur_pwd---$duration >> ~/shell_cases_log/$date_str-gt_1hours.log  
  elif [ $duration -gt 1800 ]; then # 30minutes
  	echo $time_str----$cur_pwd---$duration >> ~/shell_cases_log/$date_str-gt_30minutes.log  
  elif [ $duration -gt 600 ]; then # 10minutes
  	echo $time_str----$cur_pwd---$duration >> ~/shell_cases_log/$date_str-gt_10minutes.log
  fi
}

function get_codeset()
{
	case_path=$1
    tmp_code=${case_path##*/i18n.}
    cub_code=`echo $tmp_code|awk -F '/' '{print $1}'`
    echo $cub_code
}

function get_language() 
{
	case_path=$1
    default=`echo $CUBRID_CHARSET|awk -F '.' '{print $1}'`
    tmp_lang=${case_path##*/scenario/}
    cub_lang=`echo $tmp_lang|awk -F '/' '{print $2}'`
    tmp_coll=`echo $cub_lang|awk -F '_' '{print $3}'`
    if [ -n "$tmp_coll" ] ; then
        cub_lang=${cub_lang%%_$tmp_coll*}
    fi        
    if [ "$cub_lang" == "common" ] ; then
                cub_lang=$default
    fi
    echo $cub_lang
}

# This function initialize the variables that are related with test script.
# case_no: serial number for a test case in one scenario.
# answer_no: serial number for an answer file in one scenario.
# case_name: scenario name
# result_file: result file name for an scenario

function init 
{
  echo "[INFO] TEST START (`date`)"
  if [ $need_count_time -eq 1 ]; then
  	begin_time=`get_curr_second`
  	date_str=`date +"%Y-%m-%d"`
  	time_str=`date +%H:%M:%S`
  	echo $time_str----`pwd` >> ~/shell_cases_log/$date_str-time.log	
  fi
  
  cur_path=`pwd`
  cd $cur_path
  case_no=1
  full_name=$0
  answer_no=1 
  mode=$1
  OS=`uname`
  if [ `echo $OS| grep CYGWIN_NT|wc -l` -eq 1 ]
  then
      OS="Windows_NT"
  fi
  
  if [ "$OS" = "Windows_NT" ]; then
  	export init_path=`cygpath "${init_path}"`
    export REAL_INIT_PATH=`cygpath -w "${init_path}"`
  	export MINGW_PATH=`cygpath "${MINGW_PATH}"`
  else 
    export REAL_INIT_PATH=${init_path}
  fi
    
  is_cubrid_32bits=`cubrid_rel | grep 32bit | wc -l`
  if [ "${is_cubrid_32bits}" == "1" ]; then
    CUBRID_BITS="32"
  else
    CUBRID_BITS="64"
  fi
  export CUBRID_BITS
  
  rm $CUBRID/log/* > /dev/null 2>&1

  if [ $OS = "AIX" ];then
  	cp $init_path/commonforjdbc_aix.jar $init_path/commonforjdbc.jar
  	cp $init_path/commonforc/lib/libcommfun_aix.so $init_path/commonforc/lib/libcommfun.so
  	cp $init_path/commonforc/lib32/libcommfun_aix.so $init_path/commonforc/lib32/libcommfun.so
  fi

  objext="_objects"
  schext="_schema"
  idxext="_indexes"
  trgext="_trigger"

  PATH=${init_path}/../../bin:${init_path}/../../common/script:$PATH
   
  if [ $OS = "Windows_NT" ]
  then
    PATH=${MINGW_PATH}/bin:${MINGW_PATH}/lib:`cygpath "${JAVA_HOME}"`/bin:$PATH
    if [ "${CUBRID_BITS}" == "32" ]; then
      PATH=${MINGW_PATH}/mingw32/lib:${MINGW_PATH}/libexec/gcc/mingw32/4.6.2:$PATH
      LIBRARY_PATH=`cygpath -w "$MINGW_PATH\bin"`\;`cygpath -w "$MINGW_PATH\lib"`\;`cygpath -w "$MINGW_PATH\mingw32\lib"`\;`cygpath -w "$MINGW_PATH\libexec\gcc\mingw32\4.6.2"`\;.
    else
      PATH=${MINGW_PATH}/x86_64-w64-mingw32/lib:${MINGW_PATH}/libexec/gcc/x86_64-w64-mingw32/4.7.3:$PATH
      LIBRARY_PATH=`cygpath -w "$MINGW_PATH\bin"`\;`cygpath -w "$MINGW_PATH\lib"`\;`cygpath -w "$MINGW_PATH\x86_64-w64-mingw32\lib"`\;`cygpath -w "$MINGW_PATH\libexec\gcc\x86_64-w64-mingw32\4.7.3"`\;.
    fi
    
    CLASSPATH=`cygpath -w "$CUBRID/jdbc/cubrid_jdbc.jar"`\;`cygpath -w "${init_path}/commonforjdbc.jar"`\;.
    LD_LIBRARY_PATH=`cygpath -w $init_path/commonforc/lib`:$LD_LIBRARY_PATH
    cubrid service stop
    taskkill /F /FI "imagename eq cub*"
    rm $CUBRID/log/server/*.err > /dev/null 2>&1
    cubrid service stop
    wmic PROCESS WHERE \( name = \'java.exe\' AND NOT CommandLine LIKE \'%service.Server%\' \) DELETE
  else
    CLASSPATH=$CUBRID/jdbc/cubrid_jdbc.jar:$init_path/commonforjdbc.jar:.
    LD_LIBRARY_PATH=$init_path/commonforc/lib:$LD_LIBRARY_PATH
    rm $CUBRID/log/server/*.err > /dev/null 2>&1
    cubrid service stop
    pkill cub >/dev/null 2>&1
    
    chmod u+x ${init_path}/cubrid >/dev/null 2>&1
	PATH=${init_path}:${JAVA_HOME}/bin:$PATH
  fi

  export PATH
  export CLASSPATH
  export LD_LIBRARY_PATH
  export LIBRARY_PATH
  
  isAvailableServiceForBroker1=`ini.sh -s '%BROKER1' $CUBRID/conf/cubrid_broker.conf SERVICE |grep -iw ON|grep -v grep |wc -l`
  if [ $isAvailableServiceForBroker1 -ne 0 ];then
  	 broker_port=`ini.sh -s "%BROKER1" $CUBRID/conf/cubrid_broker.conf BROKER_PORT`
  else
 	 broker_port=`ini.sh -s "%query_editor" $CUBRID/conf/cubrid_broker.conf BROKER_PORT`
  fi
  
  sed -i "s@<port>[0-9]*</port>@<port>${broker_port}</port>@g" ${init_path}/shell_config.xml 
  
  if [ "$mode" = "test" ]
  then
     run_mode=1
  elif [ "$mode" = "answer" ]
  then
     run_mode=0
  else 
     echo " Usage: Input the correct parameter for init function in your script "
     exit
  fi

  #case_name=`echo $full_name | cut -d. -f1`
  case_name=`echo ${full_name%%.sh*}`
  result_file="$case_name.result"
  
  if [ -f $case_name.tar ]
  then
     tar xvfz $case_name.tar
  fi

  if [ $run_mode -eq 1 ]
  then
    if [ -f $result_file ]
    then
       rm $result_file
       touch $result_file
    else
       touch $result_file
    fi
  fi
  
  export OS
}

# All test script file shoule have description for test scenario.
# This function extracts description information of test script file.

function get_comment {
  line_no=1
  start_prog=0

  cat $full_name | \
  while read line
  do
    first_char=`echo $line | cut -c1-1`

    if [ "$line" = "" ]
    then
      let "line_no = line_no + 1"
      continue
    fi
  
    if [ "$first_char" != "#" ]
    then
       start_prog=1
    fi
  
    if [ $line_no -ne 1 ] && [ "$first_char" = "#" ] && [ $start_prog -eq 0 ]
    then
      echo `echo $line | cut -c2-` >> $result_file
    fi
  
    let "line_no = line_no + 1"
  done
}

function change_parameter
{
    filename=$1
    parameter=$2

    key=${parameter%%=*}
    value=${paramter##*=}
    key=`echo $key|sed 's/^ *//g'`
    key=`echo $key|sed 's/ *$//g'`

    if [ -f "$filename" ] && [ "$key" != "" ]
    then
        cat "${filename}" | grep -v "^${key} *=" > "${filename}.bak"
        echo $parameter >> "${filename}.bak"
        cat "${filename}.bak" > "${filename}"
        rm "${filename}.bak"
    fi
}


# Change DB parameter in the .ini file
# Usage:
#       change_db_parameter "ORACLE_STYLE_EMPTY_STRING = 0"

function change_db_parameter
{
  if [ $OS = 'Windows_NT' ]
  then
        if [ -f "$CUBRID/conf/cubrid.conf.org" ]
        then 
                change_parameter  "$CUBRID/conf/cubrid.conf" "$1"
                #echo "$1" >> $CUBRID/conf/cubrid.conf
        else
                cp $CUBRID/conf/cubrid.conf $CUBRID/conf/cubrid.conf.org
                change_parameter  "$CUBRID/conf/cubrid.conf" "$1"
                #echo "$1" >> $CUBRID/conf/cubrid.conf
        fi
  else
        if [ -f "$CUBRID/conf/cubrid.conf.org" ]
        then 
                change_parameter  "$CUBRID/conf/cubrid.conf" "$1"
                #echo "$1" >> $CUBRID/conf/cubrid.conf
        else
                cp $CUBRID/conf/cubrid.conf $CUBRID/conf/cubrid.conf.org
                change_parameter  "$CUBRID/conf/cubrid.conf" "$1"
                #echo "$1" >> $CUBRID/conf/cubrid.conf
        fi
  fi
}

# Restore DB .ini file from source file

function delete_ini
{
  if [ $OS = 'Windows_NT' ]
  then
        cp $CUBRID/conf/cubrid.conf.org $CUBRID/conf/cubrid.conf
  else
        cp $CUBRID/conf/cubrid.conf.org $CUBRID/conf/cubrid.conf
  fi
}

# Change DB Broker parameter in the cubrid_broker.conf
# Use only in the broker1  
# Usage:
#       change_broker_parameter "JAVA_CACHE	ON"

function change_broker_parameter
{
  name=`echo $1|awk -F= '{print $1}'`
  if [ -f "$CUBRID/conf/cubrid_broker.conf.org" ]
  then
        sed -i "/$name/d" $CUBRID/conf/cubrid_broker.conf
  	sed -i "/\[%.*\]/a${1}" $CUBRID/conf/cubrid_broker.conf
  else
        cp $CUBRID/conf/cubrid_broker.conf $CUBRID/conf/cubrid_broker.conf.org
        sed -i "/$name/d" $CUBRID/conf/cubrid_broker.conf
  	sed -i "/\[%.*\]/a${1}" $CUBRID/conf/cubrid_broker.conf
  fi
}

function change_ha_parameter
{ 
    name=`echo $1|awk -F= '{print $1}'`
    if [ -f "$CUBRID/conf/cubrid_ha.conf.org" ]
    then  
        sed -i "/$name/d" $CUBRID/conf/cubrid_ha.conf
        echo "$1" >> $CUBRID/conf/cubrid_ha.conf
    else
        cp $CUBRID/conf/cubrid_ha.conf $CUBRID/conf/cubrid_ha.conf.org
        sed -i "/$name/d" $CUBRID/conf/cubrid_ha.conf
        echo "$1" >> $CUBRID/conf/cubrid_ha.conf
    fi  
} 

# Restore cubrid_broker.conf file from source file

function restore_broker_conf
{
    if [ -f "$CUBRID/conf/cubrid_broker.conf.org" ]
    then
        cp $CUBRID/conf/cubrid_broker.conf.org $CUBRID/conf/cubrid_broker.conf
    fi
}

function restore_ha_conf
{    
    if [ -f "$CUBRID/conf/cubrid_ha.conf.org" ]
    then
        cp $CUBRID/conf/cubrid_ha.conf.org $CUBRID/conf/cubrid_ha.conf
    fi
}       
     
function restore_db_conf
{    
    if [ -f "$CUBRID/conf/cubrid.conf.org" ]
    then
        cp $CUBRID/conf/cubrid.conf.org $CUBRID/conf/cubrid.conf
    fi
}
     
function restore_all_conf
{    
    restore_db_conf
    restore_broker_conf
    restore_ha_conf
}

#Get 32 or 64 bit version for cubrid
#32 bit echo 1,64 bit echo 0
function is32bit
{
    is32bit=`cubrid_rel|grep "32bit" | wc -l`
    echo $is32bit
}

function format_csql_output
{
    if [ -n "$1" ];then
        #sed -e '/SQL statement execution time/d' $1 > outputtmp.txt
        #sed  "s/(.* sec)//g" $1 > outputtmp.txt
        sed -i '/SQL statement execution time/d' $1
	sed -i '/(.* sec)/d' $1 
	sed -i '/Committed./d' $1 
    else
        echo "Please input an file name"
    fi
}
function format_query_plan
{
    sed -i '/Join graph segments/,/Query stmt:/s/[0-9]/?/g' $1
    sed -i 's/\([0-9]\.[0-9][0-9][0-9][0-9][0-9][0-9] sec\)/?/g' $1
    sed -i 's/time: [0-9]*/time:?/g' $1
    sed -i 's/fetch: [0-9]*/fetch:?/g' $1
    sed -i 's/ioread: [0-9]*/ioread:?/g' $1
    sed -i 's/"time": [0-9]*/"time":?/g' $1
    sed -i 's/"fetch": [0-9]*/"fetch":?/g' $1
}

function format_path_output
{
	if [ -n "$1" ];then
		for f in $@;do
			sed -i -e 's#[^ ][[:alnum:][:punct:]]*[/\\]cases#/PATH/TO/cases#g' -e 's#\\#/#g' $f
			sed -i "s#${CUBRID}#/PATH/TO/CUBRID#g" $f
		done
	else
		echo "Please input at least one file"
	fi
}

function remove_space_character
{
    sed -i 's/ /\t/g' $1
}

# example: xkill cub
# it equals to: pkill cub
function xkill
{
   strkill=$1

   if [ "$strkill" = "" ]
   then
       return
   fi
   
   if [ "$OS" == Windows_NT ]
   then
       win_svr_pid=`get_win_service_pid`
       for pid in `ps -W | grep "${strkill}" | awk '{print $1}'`
       do
          is_in_white_list=0
          for svr_id in ${win_svr_pid}
          do
	          if [ "${pid}" == "${svr_id}" ]; then
	          	is_in_white_list=1
	          fi
          done
          if [ "${is_in_white_list}" == "0" ]; then
             /bin/kill -9 -f ${pid}
          fi
       done
   else
       for pid in `ps -u $USER -o pid,comm | grep $1 | grep -v grep | awk '{print $1}'`
       do
           kill -9 $pid
       done
   fi
}


function xkill_pid {
    if [ $OS == Windows_NT ]
    then
       /bin/kill -9 -f $@
    else
       kill -9 $@
    fi 
}

##this funtion only for windows

function xkill_java_windows {
    if [ $OS == Windows_NT ]
    then
	xkill java
    fi
}



function get_win_service_pid {
    wmic PROCESS WHERE \( Name != \'wmic.exe\' and CommandLine LIKE \'%service.Server%\' \)  get processid | grep -v ProcessId | sed 's/ //g'
}

#format number like 0|123|456
function format_number_output
{
    sed -i 's/\: [0-9]*|[0-9]*|[0-9]*/\: ?/g' $1
}

#format object type instance oid
function format_instance_oid_output()
{
    sed -i 's/[[:space:]]\+[0-9]*|[[:space:]]\+[0-9]*|[[:space:]]\+[0-9]*//g' $1
}

#format tran index number of lockdb output
function format_tran_index_lockdb_output()
{
    sed -i 's/Tran_index =.*Granted_mode/Tran_index =    , Granted_mode/g' $1
}

function set_CUBRID_CHARSET
{
    parse_build_version
    if [ $cubrid_major -ge 9 -a $cubrid_minor -gt 1 ] || [ $cubrid_major -ge 10 ]
    then
	  CUBRID_CHARSET=$1
    else
	  export CUBRID_CHARSET=$1
	  if [ "$OS" == "Windows_NT" ];then
           cygw=`cygpath -w $init_path/resetCubridLangOnRegedit.bat`

          cmd.exe /C $cygw "$1"
          fi
    fi
}

# $1 parameter name,$2 parameter value,eg: $1 CUBRID_DATE_LANG $2 en_US
function setENVParam
{
    export $1=$2
    if [ "$OS" == "Windows_NT" ];then
       cygw=`cygpath -w $init_path/setCubridParamOnRegedit.bat`

        cmd.exe /C $cygw "$1" "$2"
    fi
}

# $1 parameter name,$2 parameter value,eg: $1 CUBRID_DATE_LANG $2 en_US
function recoverENVParam
{
    export $1=$2
    if [ "$OS" == "Windows_NT" ]
    then
        if [ "$1" == "CUBRID_CHARSET" ]
        then
            setENVParam $1 $2
        else
            cygw=`cygpath -w $init_path/dropCubridParamOnRegedit.bat`
            cmd.exe /C $cygw "$1"
        fi
    fi
}

function unsetENVParam
{
    unset $1
    if [ "$OS" == "Windows_NT" ]
    then
       cygw=`cygpath -w $init_path/dropCubridParamOnRegedit.bat`
       cmd.exe /C $cygw "$1"
    fi
}

function do_make_locale
{
parameter=""
force=0
nocheck=0

while [ $# -ne 0 ]
do
   case $1 in
      debug)
        if [ "$OS" == "Windows_NT" ]
        then
                parameter=`echo $parameter " /debug"`
        else
                parameter=`echo $parameter " -m debug"`
        fi
        shift
        ;;
     release)
        if [ "$OS" == "Windows_NT" ]
        then
                parameter=`echo $parameter " /release"`
        else
                parameter=`echo $parameter " -m release"`
        fi
        shift
        ;;
     force)
        force=1
        shift
        ;;
     nocheck)
        nocheck=1
        shift
        ;;
     *)
       parameter=`echo $parameter " $1"`
       shift
       ;;
   esac
done
echo $parameter

#force=0,if have done make locale,do not do it
if [ $force -eq 0 ]
then
    if [ -f $CUBRID/lib/libcubrid_all_locales.so ] || [ -f $CUBRID/lib/libcubrid_all_locales.dll ]
    then
        if diff $CUBRID/conf/cubrid_locales.txt $CUBRID/conf/cubrid_locales.all.txt >/dev/null
        then
            return 0
        fi
    fi
    cp $CUBRID/conf/cubrid_locales.all.txt $CUBRID/conf/cubrid_locales.txt
fi

if [ "$OS" == "Windows_NT" ]
then
    old_cubrid=`echo $CUBRID`
    new_lang=`echo ${old_cubrid}|sed 's:/:\\\\:g'`
    export CUBRID=${new_lang}
    make_locale.bat $parameter > make_locale.log 2>&1
    export CUBRID=${old_cubrid}
fi

if [ "$OS" == "Linux" -o "$OS" == "AIX" ]
    then
    m64=`file $CUBRID/bin/cubrid | grep 64-bit | wc -l`
    if [ $m64 -eq 1 ]
    then
        make_locale.sh -t 64 $parameter > make_locale.log 2>&1
    else 
        make_locale.sh -t 32 $parameter > make_locale.log 2>&1
    fi
fi
cat make_locale.log

if [ $nocheck -eq 1 ]
then
   return 0
fi

if [ "$OS" == "Linux" -a `grep Done make_locale.log|wc -l` -ge 3 ] || [ "$OS" == "Windows_NT" -a `grep Done make_locale.log|wc -l` -ge 4 ] || [ "$OS" == "AIX" -a `grep Done make_locale.log|wc -l` -ge 3 ]
then
    write_ok
    return 0
else
    write_nok "make_locale failed"
    return -1
fi
return -2
}

function delete_make_locale
{
    echo "" >$CUBRID/conf/cubrid_locales.txt
    do_make_locale force
}


function backup_tz
{
        if [ ! -d $CUBRID/qa_tzbk ]
        then
          mkdir $CUBRID/qa_tzbk
	  cp -rf $CUBRID/timezones/tzdata $CUBRID/qa_tzbk/
	  cp -f $CUBRID/lib/libcubrid_timezones* $CUBRID/qa_tzbk/

	  if [ -d $CUBRID/databases/demodb ]
	  then
	     cp -rf $CUBRID/databases/demodb $CUBRID/qa_tzbk/
          fi
        fi
}

# execute make_tz tool
# usage: do_make_tz [debug|release] [new|update|extend database_name] [nocheck]
function do_make_tz
{
        parameter=""
        nocheck=0

        while [ $# -ne 0 ]
        do
           case $1 in
              debug|release)
                if [ "$OS" == "Windows_NT" ]
                then
                        parameter=`echo $parameter " /$1"`
                else
                        parameter=`echo $parameter " -m $1"`
                fi
                shift
                ;;
        new|extend)
        if [ "$OS" == "Windows_NT" ]
        then
            parameter=`echo $parameter " /$1"`
        else
            parameter=`echo $parameter " -g $1"`
        fi
        shift
        ;;
        # do not check error
             nocheck)
                nocheck=1
                shift
                ;;
             *)
                parameter=`echo $parameter " $1"`
                shift
                ;;
          esac
        done
        echo $parameter

        if [ ! -d $CUBRID/qa_tzbk ]
        then
            echo "Warning: please backup timezone related data before make_tz"
        fi

        if [ "$OS" == "Windows_NT" ]
        then
          #old_cubrid=`echo $CUBRID`
          #new_lang=`echo ${old_cubrid}|sed 's:/:\\\\:g'`
          #export CUBRID=${new_lang}
          #make_tz.bat $parameter > make_tz.log 2>&1
          #export CUBRID=${old_cubrid}
          for i in {1..10}
          do
            (export CUBRID=`cygpath -w $CUBRID`; make_tz.bat $parameter > make_tz.log 2>&1)
            cnt=`grep "0 file" make_tz.log | wc -l`
            if [ $cnt -eq 0 ]; then
               break
            fi
          done
        else
          if [ `is32bit` -eq 1 ]
          then
            bit=32
          else
            bit=64
          fi
          make_tz.sh -t $bit $parameter > make_tz.log 2>&1
        fi

        if [ $nocheck -eq 1 ]
        then
          return 0
        else
          succ_cnt=`grep "The timezone library has been created at" make_tz.log | wc -l`
          fail_cnt=`grep "fail\|0 file" make_tz.log | wc -l`
          if [ "$succ_cnt" -ge 1 -a "$fail_cnt" -eq 0 ]
          then
            write_ok
            return 0
          else
            echo tail -n 60 make_tz.log
            tail -n 60 make_tz.log
            write_nok "make_tz failed!!!"
            return 1
          fi
          return 2
        fi
}

function revert_tz
{
	if [ -d $CUBRID/qa_tzbk ]
	then
		rm -rf $CUBRID/timezones/tzdata
		cp -rf $CUBRID/qa_tzbk/tzdata $CUBRID/timezones/
		rm -f $CUBRID/lib/libcubrid_timezones*
		cp -f $CUBRID/qa_tzbk/libcubrid_timezones* $CUBRID/lib/

		if [ -d $CUBRID/qa_tzbk/demodb ]
		then
			rm -rf $CUBRID/databases/demodb
			cp -rf $CUBRID/qa_tzbk/demodb $CUBRID/databases/
		fi
	fi
}

#get the OS version
function get_os(){
osname=`uname`
case "$osname" in
	"Linux")
		echo "Linux";;
	"AIX")
		echo "AIX";;
	*)
		echo "Windows_NT";;
esac
}

function get_bit_ver(){
is32bit=`file ${CUBRID}/bin/cubrid | grep "32-bit" | wc -l`
if [ "$is32bit" -eq 1 ] 
then
	BIT_VERSION=32
else
	BIT_VERSION=64
fi
echo $BIT_VERSION
}

#replace gcc to cross platforms
function xgcc(){
OS=`get_os`
BIT_VERSION=`get_bit_ver`

#recive parameters and delete options
gcc_option=$@
gcc_option=`echo $gcc_option|sed "s#-m32##g"`
gcc_option=`echo $gcc_option|sed "s#-DWINDOWS##g"`
gcc_option=`echo $gcc_option|sed "s#-L.*\$CUBRID/bin/##g"`
gcc_option=`echo $gcc_option|sed "s#-L.*\$CUBRID/bin##g"`
gcc_option=`echo $gcc_option|sed "s#-L.*\$CUBRID/lib/##g"`
gcc_option=`echo $gcc_option|sed "s#-L.*\$CUBRID/lib##g"`
gcc_option=`echo $gcc_option|sed "s#-I.*\$CUBRID/include/##g"`
gcc_option=`echo $gcc_option|sed "s#-I.*\$CUBRID/include##g"`
gcc_option=`echo $gcc_option|sed "s#-ggdb##g"`
gcc_option=`echo $gcc_option|sed "s#-g##g"`
gcc_option=`echo $gcc_option|sed "s#-lcascci##g"`
gcc_option=`echo $gcc_option|sed "s#-lpthread##g"`


#get common compile option
gcc_option="-g -I$CUBRID/include -L$CUBRID/lib -lcascci $gcc_option"

#get bit version of cubrid
if [ "$BIT_VERSION" -eq 32 ]
then
    case "$OS" in
    	"AIX")
    		gcc_option="$gcc_option -maix32 -lpthread -Wl,-G ";;
    	"Linux")
    		gcc_option="$gcc_option -m32 -lpthread ";;
    	"Windows_NT")
    		gcc_option="-m32 -DWINDOWS -L$CUBRID/bin ${gcc_option} -lpthread ";;
    esac
else
    case "$OS" in
    	"AIX")
    		gcc_option="$gcc_option -maix64 -lpthread -Wl,-G ";;
    	"Linux")
    		gcc_option="$gcc_option -lpthread ";;
    	"Windows_NT")
    		gcc_option="-DWINDOWS -L$CUBRID/bin ${gcc_option} -lpthread ";;
    esac
fi
#compile c program using gcc_option
gcc $gcc_option

} 


# replace cas info for compare result
function cas_info_replace
{
  argc=$#
  if [ $argc -eq 2 ];then
     caspwd=`pwd`
     #sed "s/\[CAS INFO - [0-9]\{1,\}\.[0-9]\{1,\}\.[0-9]\{1,\}\.[0-9]\{1,\}\:[0-9]\{1,\}\, .*[0-9]\{1,\}\, [0-9]\{1,\}\]\.//g"  $caspwd/$1 > $caspwd/$2
     sed  "s/\[CAS INFO.*\]\.//g"  $caspwd/$1 > $caspwd/$2
  elif [ $argc -eq 1 ];then
	sed -i "s/\[CAS INFO.*\]\.//g"  $1
  else
	echo "Please confirm the number of your argurments"
  fi

}


# function to get csql execution time, 1 param, the file name
function get_csql_execution_time
{
	argc=$#
	if [ ! $argc -eq 1 ]; then
		echo "Usage: get_csql_execution_time {file_name}"
	else
		time=`grep -o -E '\(.*sec\)' $1 | awk '{print substr($1, 2)}'`
		echo "$time"
	fi
}

function parse_build_version()
{
        build_ver=`cubrid_rel|grep "CUBRID"|awk -F '(' '{print $2}'|sed 's/)//g'`
        cubrid_major=${build_ver%%.*}
        cubrid_minor=`echo $build_ver|awk -F '.' '{print $2}'`
        isDebug=`cubrid_rel | grep "debug" | wc -l`
        if [ $isDebug -eq 1 ]
        then
            IGNORE_TEST_PERFORMANCE="TRUE"
        else
            IGNORE_TEST_PERFORMANCE="FALSE"
        fi

}

function format_cubrid_version()
{
	parse_build_version
        cubrid_ver=$cubrid_major.$cubrid_minor
	sed -i "/$cubrid_ver/d" $1
}

function cubrid_createdb()
{
    ##parse build version
    parse_build_version
    if [ $cubrid_major -ge 9 -a $cubrid_minor -gt 1 ] || [ $cubrid_major -ge 10 ]
    then
	cubrid createdb $* $CUBRID_CHARSET
    else
	cubrid createdb $*
    fi 
}

function search_in_upper_path {
   curr_path=$1
   dest_file=$2
   if [ -f ${curr_path}/${dest_file} ]; then
       echo $(cd ${curr_path}; pwd)/${dest_file}
   else
       if [ "$(cd ${curr_path}/..; pwd)" == "/" ]; then
           return 
       else
           search_in_upper_path ${curr_path}/.. ${dest_file}
       fi
   fi
}

# create ccidb
function create_ccidb
{
    ##parse build version
    parse_build_version

    cubrid server stop ccidb
    cubrid deletedb ccidb
    db_size=`du -s $CUBRID/databases/ccidbbak | awk '{print $1}'`
    db_size_min=40000
    if [ -d $CUBRID/databases/ccidbbak -a "$db_size" -ge "$db_size_min" ]
    then
        rm -rf $CUBRID/databases/ccidb
        cp -r $CUBRID/databases/ccidbbak $CUBRID/databases/ccidb
        cat $CUBRID/databases/ccidb.txt >>$CUBRID/databases/databases.txt
    else
        cur=`pwd`
        cd $CUBRID/databases
        mkdir ccidb
        cd ccidb
	if [ $cubrid_major -ge 9 -a $cubrid_minor -gt 1 ] || [ $cubrid_major -ge 10 ]
	then
        	cubrid createdb ccidb --db-volume-size=20m --log-volume-size=20m $CUBRID_CHARSET
	else
		cubrid createdb ccidb --db-volume-size=20m --log-volume-size=20m
	fi
	    init_sql_file_ccidb=${init_path}/ccidb.sql
	    if [ ! -f ${init_sql_file_ccidb} ]; then
	    	init_sql_file_ccidb=`search_in_upper_path "${cur_path}" files/ccidb.sql`
	    fi	
	      
	    if [ -f "${init_sql_file_ccidb}" ]; then
        	csql ccidb -S -i ${init_sql_file_ccidb}
        fi
        
        rm -rf $CUBRID/databases/ccidbbak
        cp -r $CUBRID/databases/ccidb $CUBRID/databases/ccidbbak
        grep ccidb $CUBRID/databases/databases.txt >$CUBRID/databases/ccidb.txt
        cd $cur
    fi
}

function finish {
#  rm -f *.err *.log >/dev/null 2>&1
  rm -f userver.err.* >/dev/null 2>&1
  rm -f uclient.err.* >/dev/null 2>&1
  rm -f client.err.* >/dev/null 2>&1
  rm -rf ./lob
  cubrid service stop
  pkill cub >/dev/null 2>&1
  if [ $need_count_time -eq 1 ]; then
  	count_time
  fi
  release_broker_sharedmemory
  delete_ini
  restore_all_conf
  echo "[INFO] TEST STOP (`date`)"
}

function WINDOWS_NOT_SUPPORTED {
    return
}

function LINUX_NOT_SUPPORTED {
    return
}

function AIX_NOT_SUPPORTED {
    return
}


