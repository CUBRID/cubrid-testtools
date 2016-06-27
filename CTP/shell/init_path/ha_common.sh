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
