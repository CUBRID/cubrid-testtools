#!/bin/bash
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

function help()
{
   echo "
======================================  Welcome to Interactive Mode ======================================  
Usage: 
    help         print the usage of interactive
    run <arg>    the path of case file to run
    quit         quit interactive mode "
   echo "
For example:
    run .                                                 #run the current directory cases
    run ./_001_primary_key/_002_uniq                      #run the cases which are based on the relative path
    run test.sql                                          #run the test.sql file
    run /home/user1/dailyqa/trunk/scenario/sql/_02_object #run the cases which are based on the absolute path"
   echo ""
}

function run()
{
   case_file=`readlink -f $1`
   client_charset=$2
   
   if [ !"$client_charset" ];then
	client_charset=$client_charset_in_interactive
   fi
  
   javaArgs="$case_file?db=${db_name_in_interactive}_qa"
  
   "$JAVA_HOME/bin/java" -Xms1024m -Xmx2048m -XX:MaxPermSize=512m -XX:+UseParallelGC -classpath "$CLASSPATH" com.navercorp.cubridqa.cqt.console.ConsoleAgent runCQT $sceanrio_type_in_interactive $scenario_alias_in_interactive $bits_in_interactive $client_charset $javaArgs 2>&1 | tee $log_file_in_interactive 

   print_summary $log_file_in_interactive
}


function print_summary()
{
   log_filename=$1
   
   resultDirTemp=`cat ${log_filename}|grep "^Result Root Dir"|head -n 1`
   resultDir=${resultDirTemp#*:}
   resultSummaryInfoFile=${resultDir}/main.info
   [ ! -f $resultSummaryInfoFile ] && echo "No Results!! please confirm your scenario path include valid case script(log:$log_filename)" && return

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
   echo "Test Result Directory:$resultDir"
   echo "Test Log:$log_filename"
   echo "-----------------------"
   echo ""
   echo "-----------------------"
   echo "Testing End!"
   echo "-----------------------"
   echo ""
}

function quit()
{
   echo "quit interactive mode!"
   echo "Bye!"
   exit
}

export -f run
export -f quit
export -f help
export -f print_summary
