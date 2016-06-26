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

function clean_for_ha_repl()
{
    cd ~
    rm csql.err* CUBRID-* core.*
    find ~/build ~/ERROR_BACKUP -mtime +15 | xargs rm -rf
}

function mail_send {
    PATH=$HOME/CTP/common/script:$HOME/cubrid_common:$PATH
    run_mail_send "$@"
}

function get_current_ip {
    (ifconfig || /sbin/ifconfig) 2>/dev/null| awk '/inet addr/{print substr($2,6)}'|grep -v "127.0.0.1" | grep -v "192.168."
}


########## check disk space ###########
function check_disk_space {
    if [ $# -ne 3 ]
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

    expect=`echo $expectOriginal | tr [a-z] [A-Z]`
    unit=""
    
    unit=${expect//[0-9|.]/}
    expect=${expect%$unit}

    if [ -z $unit ]
    then
        expect=`echo $expect/1024|bc`
    else
        if [ $unit == "M" ]
        then
            expect=`echo $expect*1024|bc`
        elif [ $unit == "G" ]
        then
            expect=`echo $expect*1024*1024|bc`
        elif [ $unit == "T" ]
        then
            expect=`echo $expect*1024*1024*1024|bc`
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

   for (( i=1; ; i=i+1 ))
   do
       actual=`df -P | grep "$diskName" | awk '{print $4}'`
       
       if [ $actual -lt $expect ]
       then
           if [ $i -eq 1 ]
           then
               noteInfo="$@"
               mail_send -to "$mailto" -cc "${KEY_MAIL_FROM_NICKNAME}<${KEY_MAIL_FROM_ADDRESS}>" -title "[CUBRID QA] Please Check Disk Space on $USER@`get_current_ip` Right Now" -content "Dear #TO#,<br>$USER@`get_current_ip` dose not have enough available disk space. Please handle with it right now. <br><br> (note: $noteInfo) <br><br>Best Regards,<br>CUBRID QA" 
           else
               sleep 15
           fi
       else
           break;
       fi
   done

   endTime=`date +%s`
   elapse=`expr \( $endTime - $startTime \)`
   echo "CHECK DISK SPACE: ${expectOriginal} expected, actual is ${actual} K. (elapse: $elapse seconds)"

}

function upgrade_ctp {
    (cd $HOME/CTP/scheduler && sh upgrade.sh)
}
