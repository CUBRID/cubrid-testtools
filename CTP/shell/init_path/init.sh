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
need_count_time=1
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

export OS=`get_os`

if [ "$OS" = "Windows_NT" ]; then
	alias diff="diff -a -b"
fi

# get broker port from shell_config.xml
function get_broker_port_from_shell_config
{
  port=`awk '/<port>/' $init_path/shell_config.xml`
  port=${port#*>}
  port=${port%<*}
  echo $port
}

function get_cubrid_port_id(){
    local port_id=`ini.sh -s "common" $CUBRID/conf/cubrid.conf cubrid_port_id`
    if [ "${port_id}" = "" ]
    then
        echo "1523"
    else
        echo "${port_id}"
    fi
}

# This function is not recommended.
# It is used to get another available port. 
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

function diff_ignore_lineno
{
   local f1=$1
   local f2=$2
   local op=$3
   local tmp1="${f1}_temp_diff"
   local tmp2="${f2}_temp_diff"
   cp -rf ${f1} ${tmp1}
   cp -rf ${f2} ${tmp2}

   local reg="s/In[\t ]*line[\t 0-9]*,[\t ]*column[\t 0-9]*/In      line    ?,      column  ?/g"
   sed -i "$reg" ${tmp1}
   sed -i "$reg" ${tmp2}

   reg="s/In the command from line[ 0-9]*/In the command from line ?/g"
   sed -i "$reg" ${tmp1}
   sed -i "$reg" ${tmp2}

   reg="s/Commit transaction at line[ 0-9]*/Commit transaction at line ?/g"
   sed -i "$reg" ${tmp1}
   sed -i "$reg" ${tmp2}

   reg="s/In schema[0-9]* line [0-9]*/In schema? line ?/g"
   sed -i "$reg" ${tmp1}
   sed -i "$reg" ${tmp2}

   reg="s/In indexes[0-9]* line [0-9]*/In indexes? line ?/g"
   sed -i "$reg" ${tmp1}
   sed -i "$reg" ${tmp2}

   diff ${tmp1} ${tmp2} ${op}
}

# After comparing two files, This function write the result int result files.
# Usage:
#        compare_result_between_files file1 file2 [error|sort]

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
  if [ "$3" = "error" ] && [ "$4" = "sort" ] || [ "$3" = "sort" ] && [ "$4" = "error" ]
  then
        sorted_left="${left}_sorted"
        sorted_right="${right}_sorted"
        sort $left > $sorted_left
        sort $right > $sorted_right

        if diff_ignore_lineno $sorted_left $sorted_right -b
        then
                write_nok
                echo "diff $sorted_left $sorted_right failed" >> ${cur_path}/$result_file
                diff_ignore_lineno $sorted_left $sorted_right -y |tee -a ${cur_path}/$result_file
        else
                write_ok
        fi

        rm -f $sorted_left $sorted_right
  elif [ "$3" = "error" ]
  then
        if diff_ignore_lineno $left $right -b
        then
                write_nok
                echo "diff $left $right failed" >> ${cur_path}/$result_file
                diff_ignore_lineno $left $right -y |tee -a ${cur_path}/$result_file
        else
                write_ok
        fi
        let "answer_no = answer_no + 1"
  elif [ "$3" = "sort" ]
  then
        sorted_left="${left}_sorted"
        sorted_right="${right}_sorted"
        sort $left > $sorted_left
        sort $right > $sorted_right

        if diff_ignore_lineno $sorted_left $sorted_right -b
        then
                write_ok
        else
                write_nok
                echo "diff $sorted_left $sorted_right failed" >> ${cur_path}/$result_file
                diff_ignore_lineno $sorted_left $sorted_right -y |tee -a ${cur_path}/$result_file
        fi

        rm -f $sorted_left $sorted_right
  else
        if diff_ignore_lineno $left $right -b
        then
                write_ok
        else
                write_nok
                echo "diff $left $right failed" >> ${cur_path}/$result_file
                diff_ignore_lineno $left $right -y |tee -a ${cur_path}/$result_file
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
	echo "----------------- $case_no : NOK"
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
  echo $time_str----$cur_path--- time="$duration" >> ${cur_path}/$result_file
  if [ $duration -gt 7200 ]; then # 2hours
  	echo $time_str----$cur_path--- over 2hour time="$duration" >> ${cur_path}/$result_file
  elif [ $duration -gt 3600 ]; then # 1hours
  	echo $time_str----$cur_path--- over 1hour time="$duration" >> ${cur_path}/$result_file
  elif [ $duration -gt 1800 ]; then # 30minutes
  	echo $time_str----$cur_path--- over 30minutes time="$duration" >> ${cur_path}/$result_file
  elif [ $duration -gt 600 ]; then # 10minutes
  	echo $time_str----$cur_path--- over 10minutes time="$duration" >> ${cur_path}/$result_file
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
  
  cur_path=`pwd`
  cd $cur_path
  case_no=1
  full_name=$0
  answer_no=1 
  mode=$1

  if [ $need_count_time -eq 1 ]; then
  	begin_time=`get_curr_second`
  	date_str=`date +"%Y-%m-%d"`
  	time_str=`date +%H:%M:%S`
  	echo $time_str----$cur_path---- test start >> ${cur_path}/$result_file	
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
  
  rm -rf $CUBRID/log/* > /dev/null 2>&1

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
      PATH=${MINGW_PATH}/x86_64-w64-mingw32/lib:${MINGW_PATH}/libexec/gcc/x86_64-w64-mingw32/8.1.0:$PATH
      LIBRARY_PATH=`cygpath -w "$MINGW_PATH\x86_64-w64-mingw32\lib"`\;`cygpath -w "$MINGW_PATH\libexec\gcc\x86_64-w64-mingw32\8.1.0"`\;`cygpath -w "$MINGW_PATH\bin"`\;`cygpath -w "$MINGW_PATH\lib"`\;.
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
      echo `echo $line | cut -c2-` >> ${cur_path}/$result_file
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

# Usage:
#       change_db_section_parameter common "ORACLE_STYLE_EMPTY_STRING = 0" 
function change_db_section_parameter
{
  local sec=$1  
  local prm=$2  

  if [ ! -f "$CUBRID/conf/cubrid.conf.org" ]
  then
       cp $CUBRID/conf/cubrid.conf $CUBRID/conf/cubrid.conf.org
  fi

  change_config_section_parameter $sec "$prm" $CUBRID/conf/cubrid.conf
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

# Usage:
#       change_config_section_parameter common "ORACLE_COMPAT_NUMBER_BEHAVIOR = 0" $CUBRID/conf/cubrid.conf
function change_config_section_parameter
{
  local sec=$1
  local prm=$2
  local file=$3

  local key=${prm%%=*}
  local val=${prm#*=}

  sec=`echo $sec|sed "s@\/@\\\\\/@g"`
  key=`echo $key|sed "s@\/@\\\\\/@g"`
  key=`echo $key|sed 's/^ *//g'`
  key=`echo $key|sed 's/ *$//g'`
  val=`echo $val|sed "s@\/@\\\\\/@g"`

  sed -i "/^\[$sec\]/,/^\[/{s/^$key[[:space:]]*=.*/$key = $val/}" $file
  awk "/\[$sec\]/{flag=1;next}/\[.*\]/{flag=0}flag && NF" $file \
  | grep "$key = $val" > /dev/null || sed -i  "/\[$sec\]/a\\$key = $val" $file
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

# Usage:
#       change_broker_section_parameter %BROKER1 "MIN_NUM_APPL_SERVER = 4000"
function change_broker_section_parameter
{
    local sec=$1
    local prm=$2

    if [ ! -f "$CUBRID/conf/cubrid_broker.conf.org" ]
    then
        cp $CUBRID/conf/cubrid_broker.conf $CUBRID/conf/cubrid_broker.conf.org
    fi

    change_config_section_parameter $sec "$prm" $CUBRID/conf/cubrid_broker.conf
}

# Usage:
#       change_gateway_section_parameter %BROKER1 "MIN_NUM_APPL_SERVER = 4000"
function change_gateway_section_parameter
{
    local sec=$1
    local prm=$2

    if [ ! -f "$CUBRID/conf/cubrid_gateway.conf.org" ]
    then
        cp $CUBRID/conf/cubrid_gateway.conf $CUBRID/conf/cubrid_gateway.conf.org
    fi

    change_config_section_parameter $sec "$prm" $CUBRID/conf/cubrid_gateway.conf
}

# Usage:
#       change_ha_section_parameter common "ha_port_id = 59901"
function change_ha_section_parameter
{
    local sec=$1
    local prm=$2
    
    if [ ! -f "$CUBRID/conf/cubrid_ha.conf.org" ]
    then
        cp $CUBRID/conf/cubrid_ha.conf $CUBRID/conf/cubrid_ha.conf.org
    fi
    
    change_config_section_parameter $sec "$prm" $CUBRID/conf/cubrid_ha.conf
}


# Restore cubrid_broker.conf file from source file

function restore_broker_conf
{
    if [ -f "$CUBRID/conf/cubrid_broker.conf.org" ]
    then
        cp $CUBRID/conf/cubrid_broker.conf.org $CUBRID/conf/cubrid_broker.conf
    fi
}

function restore_gateway_conf
{
    if [ -f "$CUBRID/conf/cubrid_gateway.conf.org" ]
    then
        cp $CUBRID/conf/cubrid_gateway.conf.org $CUBRID/conf/cubrid_gateway.conf
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
    restore_gateway_conf
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
    sed -i 's/hit: [0-9]*/hit:?/g' $1
    sed -i 's/miss: [0-9]*/miss:?/g' $1
    sed -i 's/size: [0-9]*/size:?/g' $1
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
   strkill=""
   fullCommand=0

   if [ $# -eq 1 ]
   then
       strkill=$1
   elif [ $# -eq 2 ] && [ "$1" == "-f" ]
   then
	strkill=$2
	fullCommand=1
   else
	echo "Usage: xkill [-f] \"KeyWord\""
	return
   fi
   
   if [ "$OS" == Windows_NT ]
   then
	win_svr_pid=$(get_win_service_pid | tr '\n' '|')
    	ps -W | grep "${strkill}" | awk -v win_svr_pid="${win_svr_pid}" '{ if (index(win_svr_pid, $1) == 0) print $1 }' | xargs -I {} /bin/kill -9 -f {}
   else
       if [ $fullCommand -eq 1 ]
       then
		pids=`ps -u $USER -o pid,command | grep "$strkill" | grep -v grep | awk '{print $1}'`
       else
		pids=`ps -u $USER -o pid,comm | grep "$strkill" | grep -v grep | awk '{print $1}'`
       fi 

       for pid in $pids
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


# This function returns whitelist.
# Whitelist includes pids for critical process that can occure system crash when it is killed.
# Also includes process for running regression test.
function get_win_service_pid {
    wmic PROCESS WHERE "(
        Name = 'System Idle Process' or 
        Name = 'System' or 
        Name = 'smss.exe' or 
        Name = 'csrss.exe' or 
        Name = 'wininit.exe' or 
        Name = 'winlogon.exe' or 
        Name = 'services.exe' or 
        Name = 'lsass.exe' or 
        Name = 'svchost.exe' or 
        Name = 'conhost.exe' or 
        Name = 'WmiPrvSE.exe' or
        Name = 'taskhostex.exe' or 
        Name = 'dwm.exe' or 
        Name = 'rdpclip.exe' or
        Name = 'explorer.exe' or
        Name = 'wmic.exe'
    ) or (
	CommandLine LIKE '%service.Server%' or 
	CommandLine LIKE '%RMIService%' or 
	CommandLine LIKE '%start_service%'
    )" get processid | grep -v ProcessId | sed 's/ //g'
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

# A database that has been built once can be copied instead of built again.
#
# Creating one costs about 6 seconds of which half a second is CPU -- the rest is
# waiting -- and 94% of the shell corpus creates one, so it is most of what a
# short case costs. Copying a prepared database into place costs about 0.2.
#
# The key is what decides whether this is safe, and it is the parameters
# themselves: the same name, the same volume-shaping options, the same charset
# and the same engine binary produce the same database, so a hit cannot be the
# wrong database. Options the key does not understand are not guessed at -- they
# make it fall through to a real createdb.
#
# Parameters a case sets in cubrid.conf before calling this are deliberately not
# in the key. supplemental_log, unicode_input_normalization, dont_reuse_heap_file,
# isolation_level and lock_timeout_in_secs were each measured and none of them
# changes what createdb writes; the server reads them when it starts, which is
# after the copy, so the case's edit still takes effect. db_page_size and
# db_volume_size do change it, and they arrive as options.
#
# Off unless CTP_DB_TEMPLATE_CACHE=1. It is a change to what every case sees.
_ctp_template_dir()
{
    echo "${CTP_DB_TEMPLATE_DIR:-$HOME/.ctp_db_templates}"
}

_ctp_template_key()
{
    local name="" opts="" a
    for a in "$@"
    do
        case "$a" in
            -r) ;;
            -*) opts="$opts $(echo $a | tr 'A-Z' 'a-z')" ;;
            *)  if [ -z "$name" ]; then name=$a; else opts="$opts $(echo $a | tr 'A-Z' 'a-z')"; fi ;;
        esac
    done
    [ -n "$name" ] || return 1
    opts=`echo $opts | tr ' ' '\n' | sort | tr '\n' ' '`

    # The volume sizes decide how big the database is, and only 67% of the cases
    # that create one say --db-volume-size while 40% say --log-volume-size: for
    # the rest the size comes from cubrid.conf, so it belongs in the key.  It was
    # not there, and the difference is not small -- a log volume of 512M against
    # 20M is 707 MB on disk against 215.  Without this a run that lowers the
    # defaults restores templates the old ones built, at the old size.
    local vols
    vols=`awk -F= '/^[[:space:]]*(db|log)_volume_size[[:space:]]*=/ {
              k=$1; v=$2; gsub(/[[:space:]]/,"",k); gsub(/[[:space:]\r]/,"",v)
              print k "=" v }' "$CUBRID/conf/cubrid.conf" 2>/dev/null | sort | tr '\n' ','`

    # The name is in the key so that a hit needs no rename -- but that is what
    # holds the hit rate down. Measured over the corpus: 1,695 distinct database
    # names in 2,440 literal `db=` assignments, and 1,487 of them are used once,
    # so each is a guaranteed miss however ordinary its options are. Only 39% of
    # creations land on a name that repeats at all.
    #
    # CTP_DB_TEMPLATE_RENAME=1 takes the name out and pays `cubrid renamedb` on
    # the hit instead: about 3 s against createdb's 6, so a name that never
    # repeats goes from a 6 s miss to a 3.2 s hit while a repeated one gives up
    # 3 s. Off by default, because which way that trade lands is a measurement
    # and not an argument.
    if [ "$CTP_DB_TEMPLATE_RENAME" = "1" ]; then name=""; fi

    # createdb does not depend only on its arguments and the server binary. It
    # reads the locale library and the timezone library, and a case is entitled
    # to change either before creating a database -- some exist to do exactly
    # that. Without them in the key the cache answers for a createdb that would
    # not have happened:
    #
    #   _24_apricot/_08_I18N/_02_msg_lang/_01_createdb_01 runs `do_make_locale
    #   force` and then creates; a template from before the rebuild carries the
    #   old locale data.
    #
    #   _30_banana_qa/issue_14183_make_tz/issues/bug_bts_15940 moves
    #   libcubrid_timezones.so aside and its own comment says "should fail
    #   because of timezone lib error" -- and a cache hit makes it succeed,
    #   because no createdb runs to fail.
    #
    # Both showed up as cases that fail only with the cache on. A missing file
    # stats to nothing, which is itself a different key, so removing a library
    # is as distinguishing as replacing one.
    local libs
    libs=`stat -c %n%s%Y "$CUBRID/lib/libcubrid_all_locales.so" \
                         "$CUBRID/lib/libcubrid_timezones.so" 2>/dev/null | tr '\n' ','`
    echo "$name|$opts|${CUBRID_CHARSET}|$vols|`stat -c %s%Y $CUBRID/bin/cub_server 2>/dev/null`|$libs" \
        | sha1sum | cut -c1-16
}

# The database name is part of the key, so nothing has to be renamed -- renaming
# costs 3 seconds and would take back half of what this saves. What a copy does
# have to fix is the two ASCII info files, which hold absolute paths, and the
# databases.txt entry, which a file copy does not create.
_ctp_template_restore()
{
    local key=$1 db=$2 dir origin
    dir=`_ctp_template_dir`/$key
    [ -f "$dir/.origin" ] || return 1

    # A database of this name is already here, and copying over it is not what
    # createdb does.  `createdb --replace` deletes the old one first, and that
    # takes the backup volumes with it -- so a template restored on top leaves
    # <db>_bkvinf and <db>_bk0v000 behind, and the case's next level-0 backup
    # finds one already there and asks whether to overwrite it.  Under CTP the
    # case's standard input is a pipe nobody writes to, so it waits for an
    # answer that never comes: _15_backupdb/itrack_10002 sat on that question
    # for eighteen minutes before this check existed.  Without --replace the
    # divergence is the other way round and just as wrong -- createdb refuses a
    # database that exists, and the cache would quietly succeed.
    #
    # Reproducing the deletion here would mean reimplementing deletedb from a
    # guess at which files belong to the database, and a wrong guess deletes a
    # case's own data.  Standing aside costs one real createdb in the one
    # situation where the two are not the same thing.
    [ -e "${db}_vinf" ] && return 1
    # Slots share this store, so the copy has to be protected from the eviction
    # that would otherwise delete its source halfway through.  A shared lock:
    # any number of slots may restore the same template at once, and save and
    # eviction take the same lock exclusively.  Everything that reads $dir is
    # inside it, because a template read outside the lock may already be a
    # different template.
    #
    # A missing flock fails the subshell, which falls through to a real
    # createdb.  Slower, never wrong, and no special case to write.
    # The name the template's files carry, which is the requested one unless the
    # key left the name out -- see CTP_DB_TEMPLATE_RENAME in _ctp_template_key.
    local tname
    tname=`cat "$dir/.dbname" 2>/dev/null`
    [ -n "$tname" ] || tname=$db
    # Renaming into a name something else already occupies is not what createdb
    # does, and the check above only looked at the requested name.
    [ "$tname" = "$db" ] || [ ! -e "${tname}_vinf" ] || return 1

    ( flock -s 9 || exit 1
      origin=`cat "$dir/.origin" 2>/dev/null` || exit 1
      [ -n "$origin" ] || exit 1
      cp -a --sparse=always "$dir/$tname" "$dir/$tname"_* "$dir/lob" . 2>/dev/null || exit 1
      sed -i "s#$origin#$PWD#g" "${tname}_vinf" "${tname}_lginf" 2>/dev/null || exit 1
    ) 9>"`_ctp_template_dir`/.lk.$key" || return 1

    # A template built under another name has to become this one. renamedb wants
    # the database in databases.txt first, and it costs about 3 s -- which is the
    # whole trade this mode exists to measure. A failure here leaves files behind
    # that are not this case's database, so they go before falling back.
    if [ "$tname" != "$db" ]; then
        grep -v "^$tname[[:space:]]" $CUBRID_DATABASES/databases.txt > $CUBRID_DATABASES/.ctp_dbt 2>/dev/null
        mv $CUBRID_DATABASES/.ctp_dbt $CUBRID_DATABASES/databases.txt
        echo "$tname	$PWD	$PWD	$CUBRID_DATABASES/databases.txt" >> $CUBRID_DATABASES/databases.txt
        if ! $CUBRID/bin/cubrid renamedb "$tname" "$db" >/dev/null 2>&1; then
            rm -f "$tname" "$tname"_* 2>/dev/null
            grep -v "^$tname[[:space:]]" $CUBRID_DATABASES/databases.txt > $CUBRID_DATABASES/.ctp_dbt 2>/dev/null
            mv $CUBRID_DATABASES/.ctp_dbt $CUBRID_DATABASES/databases.txt
            return 1
        fi
    fi

    # One more case has found this template worth having.  Recorded as an append
    # to this process's own tally rather than a read-modify-write of a shared
    # counter: N slots restoring at once lose increments that way, and the
    # counter is only ever read when the store is over its cap.  Eviction folds
    # the tallies in when it needs them.
    echo "$key" >> "`_ctp_template_dir`/.used.$$" 2>/dev/null
    grep -v "^$db[[:space:]]" $CUBRID_DATABASES/databases.txt > $CUBRID_DATABASES/.ctp_dbt 2>/dev/null
    mv $CUBRID_DATABASES/.ctp_dbt $CUBRID_DATABASES/databases.txt
    printf '%s\t\t%s\tlocalhost\t%s\tfile:%s/lob\n' "$db" "$PWD" "$PWD" "$PWD" \
        >> $CUBRID_DATABASES/databases.txt
    return 0
}

# The store has a ceiling, and it is not optional.  Measured over a 25-case
# sample the templates reached 6.7 GB across 16 keys; the corpus has 129 argument
# shapes, so an uncapped store fills the disk of a machine whose whole job is to
# tell you whether something failed.  A run that dies because the disk is full
# reports the same way a real failure does, which is the worst kind of wrong
# answer a test harness can give.
#
# Eviction is by reference count: how many cases have used a template is the only
# evidence available about which one is worth keeping, and the corpus is heavily
# skewed -- three argument shapes cover 88% of the calls.  A template counts as
# used once when it is created, so a new one is not the first thing thrown away.
_ctp_template_cap_mb()
{
    echo "${CTP_DB_TEMPLATE_MAX_MB:-10240}"
}

_ctp_template_store_mb()
{
    du -sm "`_ctp_template_dir`" 2>/dev/null | awk '{print $1+0}'
}

_ctp_template_free_mb()
{
    df -Pm "`_ctp_template_dir`" 2>/dev/null | awk 'NR==2 {print $4+0}'
}

# _ctp_template_evict -- bring the store back under the cap.
#
# It takes no size, because the candidate is already inside the store by the time
# this runs and the store's own measurement therefore includes it. Passing a size
# as well counted it twice, which is how an 800 MB cap held one 356 MB template.

# The plan is what a previous run recorded: the keys the cases will ask for, in
# the order they will ask.  With it, eviction stops guessing.
#
# Cache replacement has a known optimum when the reference order is known --
# discard the entry whose next use is furthest away -- and it is normally
# unusable because the future is not known.  Here the ranked queue *is* the
# future, so the two ways a reference count is wrong both disappear: a template
# used ten times early and never again scores infinity and goes first, and one
# about to be used, which a count would rate lowest because it was just built,
# is protected.
#
# Without a plan this falls back to the reference count, which is what the first
# run of a corpus has and all a legacy CTP has.  A stale plan costs a miss and a
# real createdb; it cannot cost correctness.
_ctp_template_plan() { echo "${CTP_DB_TEMPLATE_PLAN:-`_ctp_template_dir`/.plan}"; }

# Fold each process's tally into the shared counts.  Only eviction reads them,
# so this is the only place that has to pay for it.  A tally older than the plan
# belongs to a previous run: it counts towards .refs, which is cumulative, but
# not towards this run's position in the plan.
_ctp_template_fold_tallies()
{
    local store plan t k n r
    store=`_ctp_template_dir`
    plan=`_ctp_template_plan`
    for t in "$store"/.used.*; do
        [ -f "$t" ] || continue
        [ -s "$t" ] || { rm -f "$t"; continue; }
        # A tally this run is still writing is the plan's cursor -- how far
        # through it each key has got -- so it is left alone.  Folding it would
        # also double-count it the next time eviction runs.  Everything else is
        # a previous run's, and belongs in the cumulative count that the
        # fallback uses.
        if [ -f "$plan" ] && [ ! "$t" -ot "$plan" ]; then continue; fi
        awk '{c[$0]++} END {for (k in c) print k, c[k]}' "$t" |
        while read -r k n; do
            [ -d "$store/$k" ] || continue
            r=`cat "$store/$k/.refs" 2>/dev/null`; [ -n "$r" ] || r=0
            echo $((r + n)) > "$store/$k/.refs" 2>/dev/null
        done
        rm -f "$t"
    done
}

# Print the template to give up next, or nothing.  $1 is a space-delimited list
# of keys another slot is using.
_ctp_template_worst()
{
    local store plan busy=$1
    store=`_ctp_template_dir`
    plan=`_ctp_template_plan`

    if [ ! -f "$plan" ]; then
        for x in "$store"/*/; do
            [ -d "$x" ] || continue
            case "$busy" in *" `basename "$x"` "*) continue ;; esac
            local r; r=`cat "$x/.refs" 2>/dev/null`; [ -n "$r" ] || r=0
            echo "$r $x"
        done | sort -n | head -1 | cut -d' ' -f2-
        return
    fi

    # used[k] is how many times this run has already taken k, so the next use is
    # the (used+1)-th time the plan mentions it.  A key the plan does not mention
    # again scores infinity and is given up first.
    local live=""
    for t in "$store"/.used.*; do
        [ -f "$t" ] && [ ! "$t" -ot "$plan" ] && live="$live $t"
    done
    { [ -n "$live" ] && cat $live 2>/dev/null | sed 's/^/U /'
      sed 's/^/P /' "$plan"
      for x in "$store"/*/; do
          [ -d "$x" ] || continue
          case "$busy" in *" `basename "$x"` "*) continue ;; esac
          echo "C `basename "$x"` $x"
      done
    } | awk '
        $1=="U" { used[$2]++; next }
        $1=="P" { pos[$2 "|" (seen[$2]++)] = ++n; next }
        $1=="C" { cand[$2] = $3 }
        END {
            best=""; bestscore=-1
            for (k in cand) {
                p = pos[k "|" (used[k]+0)]
                score = (p ? p : 1e18)
                if (score > bestscore) { bestscore = score; best = cand[k] }
            }
            if (best != "") print best
        }'
}

_ctp_template_evict()
{
    local store cap used d refs k
    store=`_ctp_template_dir`
    cap=`_ctp_template_cap_mb`
    used=`_ctp_template_store_mb`
    [ -n "$used" ] || used=0

    _ctp_template_fold_tallies

    # Templates another slot is restoring from are skipped rather than waited
    # for: the lock is what keeps a copy from losing its source, and blocking
    # here would stall the case that wanted the space.  A skipped template stays
    # a candidate for the next run, and if every one of them is in use this
    # gives up rather than spinning.
    local busy=" "
    while [ $used -gt $cap ]
    do
        d=`_ctp_template_worst "$busy"`
        [ -n "$d" ] || return 1          # nothing left to give up
        k=`basename "$d"`
        if ( flock -n -x 9 || exit 1; rm -rf "$d" ) 9>"$store/.lk.$k"; then
            used=`_ctp_template_store_mb`
            [ -n "$used" ] || used=0
        else
            busy="$busy$k "
        fi
    done
    return 0
}

_ctp_template_save()
{
    local key=$1 db=$2 dir tmp size
    dir=`_ctp_template_dir`/$key
    mkdir -p "`_ctp_template_dir`" 2>/dev/null || return 1

    # Copy first, then measure what was actually stored.  A template is kept
    # sparse and is half the size of the database it came from, so measuring the
    # source and comparing against the store is how the first two versions of
    # this got the arithmetic wrong in both directions.
    # Dot-prefixed so the eviction loop's glob does not see it as a candidate and
    # throw away the template it is in the middle of building.
    tmp=`_ctp_template_dir`/.tmp.$$
    rm -rf "$tmp"
    mkdir -p "$tmp" 2>/dev/null || return 1
    cp -a --sparse=always "$db" "$db"_* lob "$tmp/" 2>/dev/null || { rm -rf "$tmp"; return 1; }
    size=`du -sm "$tmp" 2>/dev/null | awk '{print $1+0}'`
    [ -n "$size" ] || size=0

    # Never take the last of the disk, whatever the cap says.  The cap is a
    # policy; this is the machine saying no.
    local free
    free=`_ctp_template_free_mb`
    if [ -n "$free" ] && [ "$free" -lt "$size" ]; then rm -rf "$tmp"; return 1; fi

    echo "$PWD" > "$tmp/.origin"
    # Which name these files carry. Without the name in the key a template is
    # restored under some other name, and renamedb needs to know what to rename.
    echo "$db" > "$tmp/.dbname"
    echo 1 > "$tmp/.refs"
    # Moved into place, so a reader never sees half a template and two runs
    # sharing the store cannot interleave. Eviction comes after the move, when
    # the new template is a candidate like any other and its single reference is
    # what keeps it -- briefly -- ahead of one nothing has used.
    ( flock -x 9 || exit 1
      rm -rf "$dir"
      mv "$tmp" "$dir" 2>/dev/null || exit 1
    ) 9>"`_ctp_template_dir`/.lk.$key" || { rm -rf "$tmp"; return 1; }
    _ctp_template_evict
    return 0
}

function cubrid_createdb()
{
    ##parse build version
    parse_build_version

    local _ctp_key="" _ctp_db=""
    if [ "$CTP_DB_TEMPLATE_CACHE" = "1" ]
    then
        _ctp_key=`_ctp_template_key "$@"`
        for _ctp_db in "$@"; do case "$_ctp_db" in -*) ;; *) break ;; esac; done
        if [ -n "$_ctp_key" ] && _ctp_template_restore "$_ctp_key" "$_ctp_db"
        then
            return 0
        fi
    fi

    local _ctp_rc
    if [ $cubrid_major -ge 9 -a $cubrid_minor -gt 1 ] || [ $cubrid_major -ge 10 ]
    then
	cubrid createdb $* $CUBRID_CHARSET
    else
	cubrid createdb $*
    fi
    _ctp_rc=$?

    # The status is captured before anything else runs, and returned at the end.
    #
    # Without this the function's status is the *save* block's, and a shell `if`
    # whose condition is false exits 0 -- so with the cache off, where _ctp_key
    # is empty, cubrid_createdb returned success whatever createdb did. Upstream
    # ends the function with `cubrid createdb $*` and therefore returns its
    # status; adding a statement after it silently took that away.
    #
    # Measured: _06_createdb/itrack_10005 creates a database under a
    # CUBRID_DATABASES that does not exist and requires it to fail. The engine
    # fails it correctly -- "Could not obtain write access to database file" --
    # and the wrapper reported success, so the case wrote "DB exist!" and failed.
    # Four _06_createdb cases and others behaved the same way.
    #
    # And the save is gated on success too: a template built from a database
    # createdb did not finish is a template that poisons every later restore of
    # that key.
    if [ -n "$_ctp_key" ] && [ -n "$_ctp_db" ] && [ "$_ctp_rc" -eq 0 ]
    then
        _ctp_template_save "$_ctp_key" "$_ctp_db"
    fi
    return $_ctp_rc
}

function search_in_upper_path {
   curr_path=$1
   dest_name=$2
   if [ -f ${curr_path}/${dest_name} ]; then
       echo $(cd ${curr_path}; pwd)/${dest_name}
   elif [ -d ${curr_path}/${dest_name} ];then
       echo $(cd ${curr_path}/${dest_name}; pwd)
   else
       if [ "$(cd ${curr_path}/..; pwd)" == "/" ]; then
           return 
       else
           search_in_upper_path ${curr_path}/.. ${dest_name}
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

source $init_path/shell_utils.sh
