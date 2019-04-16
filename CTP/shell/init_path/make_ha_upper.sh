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

# script to setup cubrid ha environment
# To start ha environment establishment, please call the main function which is 'setup_ha_enviroment'
# Before you call the function, make sure you do the settings below:
# (1) set the environment variables in 'user configuration' area, and no other parameters are needed when calling the function.
# (2) include '$init_path/make_ha.sh' and '$init_path/init.sh' script file in your script file which calls the main function here.
# (3) the current script file 'make_ha.sh' and other related expect files 
#     including 'hostname.exp', 'rm_db_info.exp', 'scp.exp' and 'start_cubrid_ha.exp' must be put in $init_path directory.

#set -x

source $init_path/ha_common.sh

#=================================================================function definition begin========================================================================
#modify cubrid.conf
cubrid_major=""
cubrid_minor=""
cubridcharset=""

#stop slave heartbeat
function stop_slave_hb()
{
   #expect $init_path/slave_cmd.exp "$SLAVE_SERVER_IP" "$SLAVE_SERVER_PW" "cubrid hb stop"
   run_on_slave -c "cubrid hb stop"
}

#start slave heartbeat
function start_slave_hb()
{
   #expect $init_path/slave_cmd.exp "$SLAVE_SERVER_IP" "$SLAVE_SERVER_PW" "cubrid hb start"
   run_on_slave -c "cubrid hb start"
}

#stop slave service
function stop_slave_service()
{
   #expect $init_path/slave_cmd.exp "$SLAVE_SERVER_IP" "$SLAVE_SERVER_PW" "cubrid service stop"
   run_on_slave -c "cubrid service stop"
}

#execute command on slave
function slave_cmd()
{
   #expect $init_path/slave_cmd.exp "$SLAVE_SERVER_IP" "$SLAVE_SERVER_PW" "$1"
   run_on_slave -c "$1"
}

#function for HA, to wait until data replication to slave node finishes
function wait_for_slave
{
        csql -udba $dbname -c "create table wait_for_slave(a int primary key, b varchar(20));insert into wait_for_slave values(999, 'replication finished');" 
        
        interval=2
        if [ ! -z $1 ]
        then
                interval=$1
        fi

        run_on_slave -c "csql -udba -c \"select b from wait_for_slave\" $dbname" -tillcontains "replication finished" -interval $interval
        csql -udba $dbname -c "drop table wait_for_slave"
}


#function for HA, to wait until data replication to slave(previous master) node finishes after failover
function wait_for_slave_failover
{
        csql -udba ${dbname}@${slaveHostName} -c "create table wait_for_slave(a int primary key, b varchar(20));insert into wait_for_slave values(999, 'replication finished'); "
        
        interval=2
        if [ ! -z $1 ]
        then
                interval=$1
        fi
        
        for (( i=0; i<60; i=i+1 ))
        do
            csql -udba -c "select b from wait_for_slave" $dbname > temp.out
            if grep "replication finished" temp.out
            then
                break
            else
                sleep $interval
            fi
        done

        if [ $i -eq 60 ]
        then
            echo "The data is not replicated after a long time"
        fi
        
        csql -udba ${dbname}@${slaveHostName} -c "drop table wait_for_slave"
}

function format_hb_status()
{
    sed -i "s/$masterHostName/host1/g" $1
    sed -i "s/$slaveHostName/host2/g" $1
    sed -i "s/pid [0-9].*,/pid ,/g" $1
    sed -i "s/master [0-9].*,/master ,/g" $1
    sed -i "s#${CUBRID}#CUBRID#g" $1
}

function add_ha_db()
{
    curPath=`pwd`    
    run_on_slave -c "cubrid service stop"
    cubrid service stop
    while [ $# -ne 0 ]
    do
    	db=$1
	cd $dbPath
        cubrid deletedb $db
        rm -rf $db* 
        mkdir $db
        cd $db
        echo "creating db $db"
        cubrid createdb $db $cubridcharset --db-volume-size=20M --log-volume-size=20M
	sed -i "s#\(ha_db_list.*\)#\1,$1#g" $CUBRID/conf/cubrid_ha.conf
        run_on_slave -c "cd $CUBRID/databases;cubrid deletedb $db;rm -rf $db ${db}_${masterHostName};mkdir ${db};cd ${db};cubrid createdb $db $cubridcharset"        
        shift
    done
    
    cd $curPath
    run_upload_on_slave -from $CUBRID/conf/cubrid_ha.conf -to $CUBRID/conf/
    cubrid hb start
    cubrid broker start
    sleep 5
    run_on_slave -c "cubrid hb start"    
}

function parse_build_version()
{
        build_ver=`cubrid_rel|grep "CUBRID"|awk -F '(' '{print $2}'|sed 's/)//g'`
        cubrid_major=${build_ver%%.*}
        cubrid_minor=`echo $build_ver|awk -F '.' '{print $2}'`
}

function modify_cubrid_conf
{
  bak=$1.forhashell
  if [ -f "$bak" ]
  then
        cp $bak $1
  else
        cp $1 $bak
  fi
  
  sed -i '/ha_mode/'d $1
  sed -i '/log_max_archives/'d $1
  sed -i 's/cubrid_port_id/#cubrid_port_id/' $1   


  echo "ha_mode=on" >> $1
 # echo "log_max_archives=100" >> $1
  echo "cubrid_port_id=$CUBRID_PORT_ID" >> $1
}

#revert cubrid.conf
function revert_cubrid_conf
{
  bak=$1.forhashell
  if [ -f "$bak" ]
  then                                                                                                            
        cp $bak $1                                                                                                
  fi
  
  
}

#modify cubrid_ha.conf
function modify_cubrid_ha
{
  ha_bak=$1.forhashell
  if [ -f "$ha_bak" ]
  then
        cp $ha_bak $1
  else
        cp $1 $ha_bak
  fi
  
  sed -i '/ha_mode/'d $1
  sed -i '/ha_port_id/'d $1
  sed -i '/ha_node_list/'d $1
  sed -i '/ha_db_list/'d $1    

  echo "ha_port_id=$HA_PORT_ID" >> $1
  echo "ha_node_list=$MASTER_SERVER_USER@$masterHostName:$slaveHostName" >> $1  
  echo "ha_db_list=$dbname" >> $1
}

#revert cubrid_ha
function revert_cubrid_ha
{
  ha_bak=$1.forhashell
  if [ -f "$ha_bak" ]
  then                                                                                                            
        cp $ha_bak $1                                                                                                
  fi
}

#modify cubrid_broker.conf
function modify_cubrid_broker_conf
{
  bak=$1.forhashell
  if [ -f "$bak" ]
  then
	cp $bak $1
  else
	cp $1 $bak
  fi

  sed -i '1,34 s/^BROKER_PORT.*$/BROKER_PORT             ='$BROKER_PORT1'/' $1
  sed -i '34,80 s/^BROKER_PORT.*$/BROKER_PORT             ='$BROKER_PORT2'/' $1
  sed -i 's/^MASTER_SHM_ID.*$/MASTER_SHM_ID           ='$MASTER_SHM_ID'/' $1
  sed -i '1,34 s/^APPL_SERVER_SHM_ID.*$/APPL_SERVER_SHM_ID      ='$APPL_SERVER_SHM_ID1'/' $1
  sed -i '34,80 s/^APPL_SERVER_SHM_ID.*$/APPL_SERVER_SHM_ID      ='$APPL_SERVER_SHM_ID2'/' $1
  #sed -i '/\[%shard1\]/,/BROKER_PORT/ s/BROKER_PORT.*$/BROKER_PORT             ='$BROKER_PORT3'/' $1
  #sed -i '/\[%shard1\]/,/APPL_SERVER_SHM_ID/ s/^APPL_SERVER_SHM_ID.*$/APPL_SERVER_SHM_ID      ='$APPL_SERVER_SHM_ID3'/' $1
  #sed -i '/\[%shard1\]/,/SHARD_PROXY_SHM_ID/ s/^SHARD_PROXY_SHM_ID .*$/SHARD_PROXY_SHM_ID       ='$SHARD_PROXY_SHM_ID'/' $1
}


#revert cubrid_broker.conf
function revert_cubrid_broker_conf
{
  bak=$1.forhashell
  if [ -f "$bak" ]
  then
        cp $bak $1
  fi
}

# modify cm.conf
function modify_cm_conf
{
  bak=$1.forhashell
  if [ -f "$bak" ]
  then
	cp $bak $1
  else
	cp $1 $bak
  fi
  sed -i 's/cm_port/#cm_port/' $1
  #echo "cm_port 8401" >> $1
  echo "cm_port $CM_PORT"  >> $1
}

# revert cm.conf
function revert_cm_conf
{
  bak=$1.forhashell
  if [ -f "$bak" ]
  then
        cp $bak $1
  fi
}

#modify local database.txt
function modify_local_cubrid_database
{
  sed -i "/$dbname/d" $1
  echo "$dbname    $CUBRID/databases/$dbname   $masterHostName:$slaveHostName   $CUBRID/databases/$dbname"  >> $1
}

#cubrid-ha stop
function stop_ha_master
{
   #expect stop_cubrid_ha_upper.exp $MASTER_SERVER_USER@$MASTER_SERVER_IP $MASTER_SERVER_PW
   run_on_slave -c "cubrid hb stop;cubrid service stop;cubrid hb status"
}


#=================================================================function definition end========================================================================


#main function to create ha enviroment
function setup_ha_environment 
{
   masterHostName=`hostname -f`
   slaveHostName=`run_on_slave -c "hostname -f"`
   
   #parse build version
   parse_build_version
   if [ $cubrid_major -ge 9 -a $cubrid_minor -gt 1 ] || [ $cubrid_major -ge 10 ]
   then
	cubridcharset=en_US	
   fi

   cleanup $dbname
   run_on_slave -initfile $init_path/ha_common.sh -c "cleanup $dbname"
   
   create_option=""
   page_flag=false
   logPath=""

   while [ $# -ne 0 ] 
   do  
       if echo $1|grep ^[0-9].*
       then
           if [ $page_flag = "false" ]
           then
               create_option="$create_option -p $1"
               page_flag=true
           else
               create_option="$create_option -l $1"
           fi  
       elif [ $1 = "log" ]
       then
           logPath="log"
           create_option="$create_option -L ./$1"
       else
           create_option="$create_option $1"
       fi  

       shift
    done

    mkdir -p $CUBRID/databases/$dbname
    cd $CUBRID/databases/$dbname 
    run_on_slave -c "mkdir -p $CUBRID/databases/$dbname"
    if [ -z "$create_option" ]    
    then
        echo "cubrid createdb $dbname $cubridcharset --db-volume-size=20M --log-volume-size=20m"
        cubrid createdb $dbname $cubridcharset --db-volume-size=20M --log-volume-size=20m 
	run_on_slave -c "cd $CUBRID/databases/$dbname; cubrid createdb $dbname $cubridcharset --db-volume-size=20M --log-volume-size=20m"
    else
        echo "cubrid createdb $dbname $cubridcharset $create_option"
        if [ -n $logPath ]
        then
            mkdir log
            run_on_slave -c "mkdir -p $CUBRID/databases/$dbname/log"
        fi

        cubrid createdb $dbname $cubridcharset $create_option -r
	run_on_slave -c "cd $CUBRID/databases/$dbname; cubrid createdb $dbname $cubridcharset $create_option -r"
    fi
   cd -

   echo "Modifying cubrid.conf........"
   modify_cubrid_conf $CUBRID/conf/cubrid.conf
   
   echo "Modifying cubrid_ha.conf.........."
   modify_cubrid_ha $CUBRID/conf/cubrid_ha.conf
   
   echo "Modifying cubrid_broker.conf........"
   modify_cubrid_broker_conf $CUBRID/conf/cubrid_broker.conf
   
   echo "Modifying cm.conf........"
   modify_cm_conf $CUBRID/conf/cm.conf
   
   run_upload_on_slave -from $CUBRID/conf/cubrid.conf -to $CUBRID/conf/
   run_upload_on_slave -from $CUBRID/conf/cubrid_broker.conf -to $CUBRID/conf/
   run_upload_on_slave -from $CUBRID/conf/cm.conf -to $CUBRID/conf/
   run_upload_on_slave -from $CUBRID/conf/cubrid_ha.conf -to $CUBRID/conf/

   cubrid heartbeat start
   cubrid broker start
   cubrid heartbeat status
   sleep 3 
   run_on_slave -c "cubrid hb start;cubrid hb status"

   cd $currentPath 
    
   setup_count=150
   while [ $setup_count -ne 0 ]
   do 
       if cubrid changemode $dbname@localhost|grep "current HA running mode is active"
       then
           break
       else
           let setup_count=$setup_count-1
           sleep 1 
       fi  
   done
   
   if [ $setup_count -eq 0 ]
   then
      echo "NOK: setup_ha_environment failed"
   fi
}

#main function to revert ha enviroment
function revert_ha_environment
{  
   cleanup $dbname
   run_on_slave -initfile $init_path/ha_common.sh -c "cleanup $dbname"
   
   echo "Reverting cubrid.conf........"
   revert_cubrid_conf $CUBRID/conf/cubrid.conf

   echo "Reverting cubrid_ha.conf.........."
   revert_cubrid_ha $CUBRID/conf/cubrid_ha.conf

   echo "Reverting cubrid_broker.conf........"
   revert_cubrid_broker_conf $CUBRID/conf/cubrid_broker.conf

   echo "Reverting cm.conf........"
   revert_cm_conf $CUBRID/conf/cm.conf

   run_upload_on_slave -from $CUBRID/conf/cubrid.conf -to $CUBRID/conf/
   run_upload_on_slave -from $CUBRID/conf/cubrid_broker.conf -to $CUBRID/conf/
   run_upload_on_slave -from $CUBRID/conf/cm.conf -to $CUBRID/conf/
   run_upload_on_slave -from $CUBRID/conf/cubrid_ha.conf -to $CUBRID/conf/   
}

