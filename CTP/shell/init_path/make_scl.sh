#!/bin/bash
# 
# Copyright (c) 2016, Cubrid Corporation. All rights reserved.
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

function trim()
{
	# Determine if 'extglob' is currently on.
    local extglobWasOff=1
    shopt extglob >/dev/null && extglobWasOff=0
    (( extglobWasOff )) && shopt -s extglob # Turn 'extglob' on, if currently turned off.
    # Trim leading and trailing whitespace
    local var=$1
    var=${var##+([[:space:]])}
    var=${var%%+([[:space:]])}
    (( extglobWasOff )) && shopt -u extglob # If 'extglob' was off before, turn it back off.
    echo -n "$var"  # Output trimmed string.
}

function read_property()
{
    key=$1
    [ ! "$key" ] && echo  $key && exit 0
    val=`cat $configPath/SCL.properties|grep "$key"`
    val=${val#*=}
    val=$(trim "$val")
    echo $val
}

configPath=${init_path}

TRANSACTION_SERVER_IP=`read_property "TRANSACTION_SERVER_IP"`
TRANSACTION_SERVER_USER=`read_property "TRANSACTION_SERVER_USER"`
TRANSACTION_SERVER_PW=`read_property "TRANSACTION_SERVER_PW"`
TRANSACTION_SERVER_SSH_PORT=`read_property "TRANSACTION_SERVER_SSH_PORT"`

PAGE_SERVER_IP=`read_property "PAGE_SERVER_IP"`
PAGE_SERVER_USER=`read_property "PAGE_SERVER_USER"`
PAGE_SERVER_PW=`read_property "PAGE_SERVER_PW"`
PAGE_SERVER_SSH_PORT=`read_property "PAGE_SERVER_SSH_PORT"`

CUBRID_PORT_ID=`read_property "CUBRID_PORT_ID"`
MASTER_SHM_ID=`read_property "MASTER_SHM_ID"`
BROKER_PORT1=`read_property "BROKER_PORT1"`
APPL_SERVER_SHM_ID1=`read_property "APPL_SERVER_SHM_ID1"`
BROKER_PORT2=`read_property "BROKER_PORT2"`
APPL_SERVER_SHM_ID2=`read_property "APPL_SERVER_SHM_ID2"`
CM_PORT=`read_property "CM_PORT"`

alias run_on_page_server='${init_path}/../../common/script/run_remote_script -host $PAGE_SERVER_IP -port $PAGE_SERVER_SSH_PORT -user $PAGE_SERVER_USER -password "$PAGE_SERVER_PW"'
alias run_upload_on_page_server='${init_path}/../../common/script/run_upload -host $PAGE_SERVER_IP -port $PAGE_SERVER_SSH_PORT -user $PAGE_SERVER_USER -password "$PAGE_SERVER_PW"'
alias run_download_on_page_server='${init_path}/../../common/script/run_download -host $PAGE_SERVER_IP -port $PAGE_SERVER_SSH_PORT -user $PAGE_SERVER_USER -password "$PAGE_SERVER_PW"'

tsHostName="$HOSTNAME"
psHostName=`run_on_page_server -c "hostname"`

#variable definition
dbPath=$CUBRID/databases
dbname=scltestdb
currentPath=`pwd`

#modify cubrid.conf
cubrid_major=""
cubrid_minor=""
cubridcharset=""

export TRANSACTION_SERVER_IP
export TRANSACTION_SERVER_USER
export TRANSACTION_SERVER_PW

export PAGE_SERVER_IP
export PAGE_SERVER_USER
export PAGE_SERVER_PW

export CUBRID_PORT_ID
export MASTER_SHM_ID
export BROKER_PORT1
export APPL_SERVER_SHM_ID1
export BROKER_PORT2
export APPL_SERVER_SHM_ID2
export CM_PORT

export tsHostName
export psHostName

export dbPath
export dbname
export currentPath

function cleanup {
    set -x
    L_DBNAME=$1
    cubrid service stop
    cubrid deletedb ${L_DBNAME}
    #echo > $CUBRID/databases/databases.txt
    rm -rf $CUBRID/databases/${L_DBNAME}*
    rm -rf $CUBRID/log/*
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

#stop slave heartbeat
function stop_page_server()
{
   run_on_page_server -c "cubrid server stop"
}

#start slave heartbeat
function start_page_server()
{
   run_on_page_server -c "cubrid server start"
}

#stop slave service
function stop_page_service()
{
   run_on_page_server -c "cubrid service stop"
}

#execute command on slave
function page_server_cmd()
{
   run_on_page_server -c "$1"
}

#function for SCL, to wait until data replication to slave node finishes
function wait_for_page_server
{
        csql -udba $dbname -c "create table wait_for_page_server(a int primary key, b varchar(20));insert into wait_for_page_server values(999, 'replication finished');" 
        
        interval=2
        if [ ! -z $1 ]
        then
                interval=$1
        fi

        run_on_page_server -c "csql -udba -c \"select b from wait_for_page_server\" $dbname" -tillcontains "replication finished" -interval $interval
        csql -udba $dbname -c "drop table wait_for_page_server"
}


function format_scl_status()
{
    ts_cname=`hostname -f`
    ps_cname=`run_on_page_server -c "hostname -f"`
    sed -i "s/$ts_cname/host1/g" $1
    sed -i "s/$ps_cname/host2/g" $1
    sed -i "s/$tsHostName/host1/g" $1
    sed -i "s/$psHostName/host2/g" $1
    sed -i "s/pid [0-9].*,/pid ,/g" $1
    sed -i "s/master [0-9].*,/master ,/g" $1
    sed -i "s#${CUBRID}#CUBRID#g" $1
}

function parse_build_version()
{
	build_ver=`cubrid_rel|grep "CUBRID"|awk -F '(' '{print $2}'|sed 's/)//g'`
	cubrid_major=${build_ver%%.*}
	cubrid_minor=`echo $build_ver|awk -F '.' '{print $2}'`
}

function modify_cubrid_conf
{
  bak=$1.forsclshell
  if [ -f "$bak" ]
  then
        cp $bak $1
  else
        cp $1 $bak
  fi
  
  sed -i '/cubrid_port_id/d' $1  
  sed -i '/server_type/d' $1
  sed -i '/page_server_hosts/d' $1  

  echo "cubrid_port_id=$CUBRID_PORT_ID" >> $1
  echo "server_type=transaction" >> $1
  echo "page_server_hosts=$psHostName:$CUBRID_PORT_ID" >> $1
  
  run_on_page_server -c "sed -i '/cubrid_port_id/d' $1; sed -i '/server_type/d' $1"
  run_on_page_server -c "echo \"cubrid_port_id=$CUBRID_PORT_ID\" >> $1"
  run_on_page_server -c "echo \"server_type=page\" >> $1"
}

#revert cubrid.conf
function revert_cubrid_conf
{
  bak=$1.forsclshell
  if [ -f "$bak" ]
  then                                                                                                            
        cp $bak $1                                                                                                
  fi
}

#modify cubrid_broker.conf
function modify_cubrid_broker_conf
{
  bak=$1.forsclshell
  if [ -f "$bak" ]
  then
	cp $bak $1
  else
	cp $1 $bak
  fi

  sed -i "/^\[broker]/,/^\[/{s#^MASTER_SHM_ID[[:space:]]*=.*#MASTER_SHM_ID     =     $MASTER_SHM_ID#}" $1
  
  sed -i "/^\[%query_editor]/,/^\[/{s#^BROKER_PORT[[:space:]]*=.*#BROKER_PORT     =     $BROKER_PORT1#}" $1
  sed -i "/^\[%query_editor]/,/^\[/{s#^APPL_SERVER_SHM_ID[[:space:]]*=.*#APPL_SERVER_SHM_ID     =     $APPL_SERVER_SHM_ID1#}" $1
  sed -i "/^\[%BROKER1]/,/^\[/{s#^BROKER_PORT[[:space:]]*=.*#BROKER_PORT     =     $BROKER_PORT2#}" $1
  sed -i "/^\[%BROKER1]/,/^\[/{s#^APPL_SERVER_SHM_ID[[:space:]]*=.*#APPL_SERVER_SHM_ID     =     $APPL_SERVER_SHM_ID2#}" $1
}


#revert cubrid_broker.conf
function revert_cubrid_broker_conf
{
  bak=$1.forsclshell
  if [ -f "$bak" ]
  then
        cp $bak $1
  fi
}

# modify cm.conf
function modify_cm_conf
{
  bak=$1.forsclshell
  if [ -f "$bak" ]
  then
	cp $bak $1
  else
	cp $1 $bak
  fi
  sed -i 's/cm_port/#cm_port/' $1
  echo "cm_port $CM_PORT"  >> $1
}

# revert cm.conf
function revert_cm_conf
{
  bak=$1.forsclshell
  if [ -f "$bak" ]
  then
        cp $bak $1
  fi
}

#cubrid-scl start
function start_scl_page_server
{
   run_on_page_server -c "cubrid server start $dbname;cubrid server status"
}

#cubrid-scl stop
function stop_scl_page_server
{
   run_on_page_server -c "cubrid server stop;cubrid server status"
}


#main function to create scl enviroment
function setup_scl_environment 
{
   cubridcharset=en_US	

   cleanup $dbname
   run_on_page_server -initfile $init_path/make_scl.sh -c "cleanup $dbname"	

   run_on_page_server -c "mkdir -p $CUBRID/databases/$dbname;cd $CUBRID/databases/$dbname; cubrid createdb $dbname $cubridcharset --db-volume-size=20M --log-volume-size=20m"
   run_download_on_page_server -from $dbPath -to $dbPath

   echo "Modifying cubrid.conf........"
   modify_cubrid_conf $CUBRID/conf/cubrid.conf
   
   echo "Modifying cubrid_broker.conf........"
   modify_cubrid_broker_conf $CUBRID/conf/cubrid_broker.conf
   
   echo "Modifying cm.conf........"
   modify_cm_conf $CUBRID/conf/cm.conf

   start_scl_page_server
   cubrid server start $dbname
   cubrid broker start
}

#main function to revert scl enviroment
function revert_scl_environment
{  
   cleanup $dbname
   run_on_page_server -initfile $init_path/make_scl.sh -c "cleanup $dbname"	
   
   echo "Reverting cubrid.conf........"
   revert_cubrid_conf $CUBRID/conf/cubrid.conf

   echo "Reverting cubrid_broker.conf........"
   revert_cubrid_broker_conf $CUBRID/conf/cubrid_broker.conf

   echo "Reverting cm.conf........"
   revert_cm_conf $CUBRID/conf/cm.conf

   run_upload_on_page_server -from $CUBRID/conf/cubrid.conf -to $CUBRID/conf/
   run_upload_on_page_server -from $CUBRID/conf/cubrid_broker.conf -to $CUBRID/conf/
   run_upload_on_page_server -from $CUBRID/conf/cm.conf -to $CUBRID/conf/  
}
