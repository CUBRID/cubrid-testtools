#!/bin/bash
#script to setup cubrid ha environment
#To start ha environment establishment, please call the main function which is 'setup_ha_enviroment'
#Before you call the function, make sure you do the settings below:
#(1) set the environment variables in 'user configuration' area, and no other parameters are needed when calling the function.
#(2) include '$init_path/make_ha.sh' and '$init_path/init.sh' script file in your script file which calls the main function here.
#(3) the current script file 'make_ha.sh' and other related expect files 
#    including 'hostname.exp', 'rm_db_info.exp', 'scp.exp' and 'start_cubrid_ha.exp' must be put in $init_path directory.

#===========================================================user configuration begin==========================================================================

#MASTER_SERVER_IP=59.108.93.74
#MASTER_SERVER_USER=xdbms
#MASTER_SERVER_PW=xdbms_qa!#%

#SLAVE_SERVER_IP=59.108.93.82
#SLAVE_SERVER_USER=xdbms
#SLAVE_SERVER_PW=xdbms_qa!#%

#set port numbers according to different users
#CUBRID_PORT_ID=1523
#ha_port_id=11539
#MASTER_SHM_ID=30001
#BROKER_PORT1=30000
#APPL_SERVER_SHM_ID1=30000
#BROKER_PORT2=33000
#APPL_SERVER_SHM_ID2=33000
#cm_port=8001

#masterHostName=$HOSTNAME
#slaveHostName=""
#===========================================================user configuration end==============================================================================

#set -x


#variable definition
dbPath=$CUBRID/databases
dbname=hatestdb
currentPath=`pwd`

#=================================================================function definition begin========================================================================
#modify cubrid.conf
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
  sed -i '/ha_server_state/'d $1
  sed -i '/ha_port_id/'d $1
  sed -i '/ha_node_list/'d $1
  sed -i 's/cubrid_port_id/#cubrid_port_id/' $1

  echo "ha_mode=yes" >> $1
  echo "ha_node_list=$MASTER_SERVER_USER@$masterHostName:$slaveHostName" >> $1
  echo "ha_port_id=$ha_port_id" >> $1
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

#modify cubrid-ha
function modify_cubrid_ha
{
  bak=$1.forhashell
  if [ -f "$bak" ]
  then
	cp $bak $1
  else
	cp $1 $bak
  fi
  echo $masterHostName
  sed -i '1,40 s/^CUBRID_USER.*$/CUBRID_USER='$MASTER_SERVER_USER'/' $1
  sed -i '1,40 s/^DB_LIST.*$/DB_LIST='\'$dbname\''/' $1
  #sed -i '1,40 s/^NODE_LIST.*$/NODE_LIST='\'$masterHostName $slaveHostName\''/' $1
  sed -i '1,40 s/^NODE_LIST.*$/NODE_LIST='\'$masterHostName@@@abcdefg1234567@@@$slaveHostName\''/' $1
  sed -i '1,40 s/@@@abcdefg1234567@@@/ /' $1

}

#revert cubrid-ha
function revert_cubrid_ha
{
  bak=$1.forhashell
  if [ -f "$bak" ]
  then
        cp $bak $1
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
  echo "cm_port $cm_port"  >> $1
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
  echo "$dbname    /home/$MASTER_SERVER_USER/CUBRID/databases/$dbname   $masterHostName:$slaveHostName   home/$MASTER_SERVER_USER/CUBRID/databases/$dbname"  >> $1
}

#cubrid-ha stop
function stop_ha_master
{
   expect stop_cubrid_ha.exp $MASTER_SERVER_USER@$MASTER_SERVER_IP $MASTER_SERVER_PW
}


#=================================================================function definition end========================================================================


#main function to create ha enviroment
function setup_ha_environment 
{

   cd $init_path

   #file to save master server info
   masterInfoFile=masterInfo.txt
   #file to save slaver server info
   slaveInfoFile=slaveInfo.txt
   
   #get cubrid install path from slave server
   expect hostname.exp $SLAVE_SERVER_USER@$SLAVE_SERVER_IP $SLAVE_SERVER_PW slaveInfo.txt
   
   expect scp.exp $SLAVE_SERVER_USER@$SLAVE_SERVER_IP:~/$slaveInfoFile $init_path $SLAVE_SERVER_PW 
   
   #master server OS
   masterOS=`uname`
   #slave server OS
   slaveOS=`sed -n '1p' $slaveInfoFile`
   
   #check OS info of master and slave, if OS is not Linux, then skip testing
   if [ $masterOS = "Linux" ]
   then
   	echo "Linux! test start......"
   else
   	echo "no need to test!!"
	exit
   fi
   
   
   #hostname of master server
   masterHostName="$HOSTNAME"
   #hostname of slave server
   slaveHostName=`sed -n '2p' $slaveInfoFile`
   
   
   #cubrid install path of master server
   masterCubridPath=$CUBRID
   #cubrid databases directory of master server
   masterDatabasesPath=$masterCubridPath/databases
   #cubrid install path of slave server
   slaveCubridPath=`sed -n '3p' $slaveInfoFile`
   #cubrid databases directory of slave server
   slaveDatabasesPath=$slaveCubridPath/databases
    
  
   #delete exited db info from master and slave
   cubrid service stop
   cubrid-ha stop
   cd $CUBRID/databases/
   rm -rf $dbname*
   rm -rf $CUBRID/log/*
   sed -i '/'$dbname'/d' $dbPath/databases.txt

   cd $init_path
   
   expect rm_db_info.exp $SLAVE_SERVER_USER@$SLAVE_SERVER_IP $SLAVE_SERVER_PW $dbname $SLAVE_SERVER_USER
   
   #create db. if db already exists, delete it
   cd $dbPath
   mkdir $dbname
   cd $dbname
   echo "creating db..."
   
   if [ -z $1 ]
   then
        cubrid createdb $dbname
   elif [ -z $2 ]
   then
        cubrid createdb $dbname -p $1
   elif [ -z $3 ]
   then
        cubrid createdb $dbname -p $1 -l $2
   else
        mkdir $3
        cubrid createdb $dbname -p $1 -l $2 -L ./$3
   fi

   cd $init_path
   
   #copy db to master and slave server
   expect scp.exp $dbPath/$dbname $SLAVE_SERVER_USER@$SLAVE_SERVER_IP:$slaveDatabasesPath $SLAVE_SERVER_PW
   expect scp.exp $dbPath/databases.txt $SLAVE_SERVER_USER@$SLAVE_SERVER_IP:$slaveDatabasesPath $SLAVE_SERVER_PW
   
   
   echo "Modifying cubrid.conf........"
   modify_cubrid_conf $CUBRID/conf/cubrid.conf
   
   echo "Modifying cubrid-ha.........."
   modify_cubrid_ha $CUBRID/share/init.d/cubrid-ha
   
   echo "Modifying cubrid_broker.conf........"
   modify_cubrid_broker_conf $CUBRID/conf/cubrid_broker.conf
   
   echo "Modifying cm.conf........"
   modify_cm_conf $CUBRID/conf/cm.conf
   
   #copy config files to master and slave
   expect scp.exp $CUBRID/conf/cubrid.conf $SLAVE_SERVER_USER@$SLAVE_SERVER_IP:$slaveCubridPath/conf/ $SLAVE_SERVER_PW 
   expect scp.exp $CUBRID/conf/cubrid_broker.conf $SLAVE_SERVER_USER@$SLAVE_SERVER_IP:$slaveCubridPath/conf/ $SLAVE_SERVER_PW 
   expect scp.exp $CUBRID/conf/cm.conf $SLAVE_SERVER_USER@$SLAVE_SERVER_IP:$slaveCubridPath/conf/ $SLAVE_SERVER_PW    
   expect scp.exp $CUBRID/share/init.d/cubrid-ha $SLAVE_SERVER_USER@$SLAVE_SERVER_IP:$slaveCubridPath/bin/ $SLAVE_SERVER_PW 
   cp -f $CUBRID/share/init.d/cubrid-ha $CUBRID/bin/
  

   #start cubrid service and cubrid-ha on master and slave
   cubrid service start
   sleep 5
   cubrid-ha start
   sleep 10 
   cubrid-ha status
   expect start_cubrid_ha.exp $SLAVE_SERVER_USER@$SLAVE_SERVER_IP $SLAVE_SERVER_PW

   cd $currentPath 
   
   #rm *.txt hfaq*

   sleep 30
   
}

#main function to revert ha enviroment
function revert_ha_environment
{
   cd $init_path

   #file to save master server info
   masterInfoFile=masterInfo.txt
   #file to save slaver server info
   slaveInfoFile=slaveInfo.txt

   #get cubrid install path from slave server
   expect hostname.exp $SLAVE_SERVER_USER@$SLAVE_SERVER_IP $SLAVE_SERVER_PW slaveInfo.txt

   expect scp.exp $SLAVE_SERVER_USER@$SLAVE_SERVER_IP:~/$slaveInfoFile $init_path $SLAVE_SERVER_PW

   #master server OS
   masterOS=`uname`
   #slave server OS
   slaveOS=`sed -n '1p' $slaveInfoFile`



   #cubrid install path of slave server
   slaveCubridPath=`sed -n '3p' $slaveInfoFile`

   #delete exited db info from master and slave
   cubrid service stop
   cubrid-ha stop
   cd $CUBRID/databases/
   rm -rf $dbname*
   rm -rf $CUBRID/log/*
   sed -i '/'$dbname'/d' $dbPath/databases.txt

   cd $init_path

   expect rm_db_info.exp $SLAVE_SERVER_USER@$SLAVE_SERVER_IP $SLAVE_SERVER_PW $dbname $SLAVE_SERVER_USER

   echo "Reverting cubrid.conf........"
   revert_cubrid_conf $CUBRID/conf/cubrid.conf

   echo "Reverting cubrid-ha.........."
   revert_cubrid_ha $CUBRID/share/init.d/cubrid-ha

   echo "Reverting cubrid_broker.conf........"
   revert_cubrid_broker_conf $CUBRID/conf/cubrid_broker.conf

   echo "Reverting cm.conf........"
   revert_cm_conf $CUBRID/conf/cm.conf

   #copy config files to master and slave
   expect scp.exp $CUBRID/conf/cubrid.conf $SLAVE_SERVER_USER@$SLAVE_SERVER_IP:$slaveCubridPath/conf/ $SLAVE_SERVER_PW
   expect scp.exp $CUBRID/conf/cubrid_broker.conf $SLAVE_SERVER_USER@$SLAVE_SERVER_IP:$slaveCubridPath/conf/ $SLAVE_SERVER_PW
   expect scp.exp $CUBRID/conf/cm.conf $SLAVE_SERVER_USER@$SLAVE_SERVER_IP:$slaveCubridPath/conf/ $SLAVE_SERVER_PW
   
   #rm *.txt hfaq*
}

