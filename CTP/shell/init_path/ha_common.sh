#!/bin/sh
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

function cleanup {
    set -x
    L_DBNAME=$1
    cubrid heartbeat stop
    cubrid service stop
    cubrid deletedb ${L_DBNAME}
    #echo > $CUBRID/databases/databases.txt
    rm -rf $CUBRID/databases/${L_DBNAME}*
    rm -rf $CUBRID/log/*
}



#Upload files to remote host and execute scripts on it
#Usage:
#      run_upload_exec_on_slave from to scripts
#
#   Parameters Description:
#   1."from": Files to upload
#   2."to"  : Remote directory
#   3."scripts": execute scripts. e.g.,"script1;script2;script3" 

function run_upload_exec_on_slave(){

   #upload files to remote host
   run_upload_on_slave -from $1 -to $2 

   #execute scripts on remote host
   run_on_slave -c "$3"
}

#Execute scripts on remote host and download files from it
#Usage:
#      run_download_exec_on_slave scripts from to
#
#   Parameters Description:
#   1."scripts": execute scripts. e.g.,"script1;script2;script3" 
#   2."from": Files to download
#   3."to"  : Local directory

function run_download_exec_on_slave(){

   #execute scripts on remote host
   run_on_slave -c "$1"

   #download files to from host
   run_download_on_slave -from $2 -to $3

}

function wait_for_active(){
   if [ ! -z $1 ]
   then
      dbname=$1
   fi
   count=120
   while [ $count -ne 0 ]
   do
       if cubrid changemode $dbname@localhost|grep "current HA running mode is active"
       then
           break
       else
           let count=$count-1
           sleep 1
       fi
   done

   if [ $count -eq 0 ]
   then
      echo "NOK: db server is not active after long time"
   fi
}

#wait_for_slave_active is used to wait for the server changed to active status in slave node
function wait_for_slave_active(){
   if [ ! -z $1 ]
   then
      dbname=$1
   fi
   count=120
   while [ $count -ne 0 ]
   do
       if cubrid changemode ${dbname}@${slaveHostName}|grep "current HA running mode is active"
       then
           break
       else
           let count=$count-1
           sleep 1
       fi
   done

   if [ $count -eq 0 ]
   then
      echo "NOK: db server is not active after long time"
   fi
}

#wait_for_active_hb can be used in the case that will only be tested in the build after CUBRID 9.0. Refer to CUBRIDSUS-5212.
#If the case is also belongs to CUBRID 8.2, 8.4, 8.4, or any other version before 9.0, please use wait_for_active instead.
function wait_for_active_hb(){
   count=120
   while [ $count -ne 0 ]
   do
       if cubrid hb status|grep "state registered_and_active"
       then
           break
       else
           let count=$count-1
           sleep 1
       fi
   done

   if [ $count -eq 0 ]
   then
      echo "NOK: db server is not active after long time"
   fi
}

function skip_core_issue(){
   coreIssue=$1
   coreList=`ls core.*`
   for coreFile in $coreList
   do
      if analyzer.sh $coreFile | grep "DUPLICATE WITH $coreIssue(OPEN)" 
      then
         rm $coreFile
      fi
   done

}
