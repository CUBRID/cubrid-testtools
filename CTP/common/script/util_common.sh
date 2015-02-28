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
#

function cleanCUBRID()
{
    $CUBRID/bin/cubrid service stop 
    pkill cub
}

function releaseSharedmemory()
{
    ipcs | grep $USER | awk '{print $2}'  | xargs -i ipcrm -m {}
}

function kill_process {
   all_pids=$(calc_pids "$$")
   kill -9 `ps -u $USER -o pid | grep -v "PID" | grep -E -v "$all_pids" | grep -vw $$`
}

function find_ppid {
    ppid=`ps -u $USER -o pid,ppid | grep -w "$1" | awk '{print $2}' | grep -v "$1"`
                                                      
    if [ -z $ppid ]
    then
        echo "-1"
    else
        echo $ppid
    fi
}

function calc_pids {
    if [ -z $1 ]
    then
        echo "need one parameter";
        exit 1;
    fi 

    if [ "$1" = "-1" ]
    then
        echo ${excluded_pids} | sed 's/|$//'
    else
        excluded_pids=$1"|"${excluded_pids}
        pid=$(find_ppid "$1")
        calc_pids $pid
    fi
}

########## clean process ##########
function clean_processes {
  echo "==============Clean_CUBRID Process=============="
  cleanCUBRID
  echo "==============Release shared Memory=============="
  releaseSharedmemory
  echo "==============Stop monitor======================="
  if [ -d ${MONITOR_HOME} ];then
     sh $MONITOR_HOME/monitor.sh stop
  fi
  echo "==============Clean Processes======================="
  kill_process
}

function get_current_ip {
    current_ip=`(ifconfig || /sbin/ifconfig) 2>/dev/null| awk '/inet addr/{print substr($2,6)}'|grep -v "127.0.0.1" | grep -v "192.168."`
    if [ "$current_ip" == "" ]; then
        current_ip=`hostname -i | awk '{print $NF}'`
    fi
    echo $current_ip
}


########## check disk space ###########
function check_disk_space {
    if [ $# -lt 3 ]
    then
        echo "Usage: check_disk_space /dev/sda3 20G \"Nickname<your@mail.address>\""
        exit 1
    fi

    diskName=""
    expect="20G"
    
    echo "==========check disk space==========="
    diskName="$1"
    expectOriginal=$2
    mailto="$3"
    mailcc="$4"

    expect=`echo $expectOriginal | tr [a-z] [A-Z]`
    unit=""
    
    unit=${expect//[0-9|.]/}
    expect=${expect%$unit}

    if [ -z $unit ]
    then
        expect=$(($expect/1024))
    else
        if [ $unit == "M" ]
        then
            expect=$(($expect*1024))
        elif [ $unit == "G" ]
        then
            expect=$(($expect*1024*1024))
        elif [ $unit == "T" ]
        then
            expect=$(($expect*1024*1024*1024))
        else 
            if [ $unit != "K" ]
            then
                echo "Please input a valid unit: k, K, m, M, g, G, t, T"
                exit 1
            fi
        fi 
    fi
   
   #convert expect vaule to int 
   expect=${expect%%\.*}
   
   startTime=`date +%s`
   sended_flag=0

   current_user="$USER"
   if [ "$current_user" == "" ]; then
      current_user="<user>"
   fi
   current_ip="`get_current_ip`"

   for (( i=1; ; i=i+1 ))
   do
       actual=`df -P | grep "$diskName" | awk '{print $4}'`
       
       if [ $actual -lt $expect ]
       then
           if [ $i -eq 1 ]
           then
               noteInfo="$@"
               echo No enough available disk space. Send mail to notice:
               echo "MAIL TO: " $mailto
               echo "MAIL CC: " $mailcc
               run_mail_send -to "$mailto" -cc "$mailcc" -title "[CUBRID QA] Please Check Disk Space on ${current_user}@${current_ip} Right Now" -content "Dear #TO#,<br><br>${current_user}@${current_ip} does not have enough available disk space. Please handle with it right now. <br><b>We hope to  get your feedback!</b><br>==========<pre>`df -h`</pre>----------<pre>`ls -lh $HOME`<pre> <br>(<b>Note</b>: $noteInfo) <br><br>Best Regards,<br>CUBRID QA" 
               sended_flag=1
           else
               echo actual: $actual, expect: $expect
               sleep 15
           fi
       else
           break;
       fi
   done

   endTime=`date +%s`
   elapse=`expr \( $endTime - $startTime \)`
   echo "CHECK DISK SPACE: ${expectOriginal} expected, actual is ${actual} K. (elapse: $elapse seconds)"
   if [ "$sended_flag" == "1" ]; then
      run_mail_send -to "$mailto" -cc "$mailcc" -title "Re: [CUBRID QA] Please Check Disk Space on ${current_user}@${current_ip} Right Now" -content "Dear #TO#, <br><br>Thank you for your fixing on ${current_user}@${current_ip}. <br> Elapse time: $elapse second(s).<br> <br>==========<pre>`df -h`</pre><br>----------<pre>`ls -lh $HOME`</pre> <br><br> Best Regards, <BR> CUBRID QA"
   fi
}
