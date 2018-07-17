#!/bin/bash
# 
# Copyright (c) 2016, Search Solution Corporation? All rights reserved.
#
# Redistribution and use in source and binary forms, with or without 
# modification, are permitted provided that the following conditions are met:
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
# THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, 
# INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE 
# DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, 
# SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR 
# SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, 
# WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE 
# USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

export CTP_HOME=$(cd $(dirname $(readlink -f $0))/../..; pwd)
scenario_category=""
config_file_main=""
memory_log="memory.log"

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

function clean_results()
{
   curDir=`pwd`
   if [ ! -d "$CTP_HOME/result" ];then
       cd $CTP_HOME
       mkdir -p result
   else
       cd $CTP_HOME/result
       if [ -d "memory" ];then
          rm ./memory/*
       fi
   fi

   cd $curDir
}


function rename_process()
{
   curDir=`pwd`

   if [ ! -d "$CUBRID/bin" ];then
      echo "Please confirm your build is installed correctly!"
      exit 1
   fi

   cd $CUBRID/bin
   if [ ! -f "server.exe" ];then
      mv cub_server server.exe
   fi
   
   if [ ! -f "cas.exe" ];then
      mv cub_cas cas.exe
   fi
   
   cd $curDir
}


function do_process_mock()
{
   curDir=`pwd`
   cd $CUBRID/bin
   cp $CTP_HOME/sql/memory/cub_server.c .
   cp $CTP_HOME/sql/memory/cub_cas.c .

   gcc -g -o cub_server cub_server.c
   gcc -g -o cub_cas cub_cas.c

   cd $curDir
}


function stop_and_collect_memory_result()
{
   curDir=`pwd`
   cubrid service stop 2>/dev/null
  
   running_db=`cubrid server status|grep -v '@'|grep -v '++'|grep -v grep |awk '{print $2}'|tr -d ' '`
 
   if [ ! -z "$running_db" ];then 
   	cubrid server stop $running_db 2>/dev/null
   fi

   cd $CUBRID/bin
   if [ -f "server.exe" ];then
      mv server.exe cub_server
   fi

   if [ -f "cas.exe" ];then
      mv cas.exe cub_cas
   fi

   if [ -f "cub_server.c" ];then
      rm cub_server.c
   fi

   if [ -f "cub_cas.c" ];then
      rm cub_cas.c
   fi

   cd $curDir
}

function format_results()
{
   run_log=$1
   curDir=`pwd`
   cd $CTP_HOME/result
   if [ "`ls -A ${CTP_HOME}/result/memory`" == "" ];then
      echo "Please check why your testing is not executed correctly!"
      exit 1
   fi

   build_no=`cubrid_rel | grep CUBRID | awk -F'(' '{print $2}' |  awk -F')' '{print $1}'`
   result_folder="memory_${scenario_category}_${build_no}_`date '+%Y%m%d%H%M%S'`"
   mkdir -p $result_folder
   rm ./$result_folder/* 2>/dev/null
   cp $CTP_HOME/result/memory/* $CTP_HOME/result/$result_folder
   cp $run_log $CTP_HOME/result/$result_folder

   testing_result=`cat $run_log|grep 'Test Result Directory:'|grep -v grep|awk -F ':' '{print $2}'|tr -d ' '`

   if [ -n "$testing_result" ];then
   	cp -rf $testing_result/* $CTP_HOME/result/$result_folder
	cd ${CUBRID}
        tar zvcf log.tar.gz ./log
        if [ $? -eq 0 ];then
	   cp ${CUBRID}/log.tar.gz  $CTP_HOME/result/$result_folder
	fi
	cd -
   fi   

   echo "======================="
   echo "Test Build:${build_no}"
   echo "Memory Result:${CTP_HOME}/result/${result_folder}"
   echo "======================="  
   cd $curDir
}

#clean up memory results
clean_results

#rename process of cub
rename_process

#mock process for cub
do_process_mock


sh $CTP_HOME/sql/bin/run.sh -s ${scenario_category} -f $config_file_main 2>&1 > ${CTP_HOME}/result/${memory_log}

# stop services and collect memory results
stop_and_collect_memory_result

#format results
format_results ${CTP_HOME}/result/${memory_log}



