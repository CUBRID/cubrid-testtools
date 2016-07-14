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

#script to setup cubrid ha environment
#To start ha environment establishment, please call the main function which is 'setup_ha_enviroment'
#Before you call the function, make sure you do the settings below:
#(1) set the environment variables in 'user configuration' area, and no other parameters are needed when calling the function.
#(2) include '$init_path/make_ha.sh' and '$init_path/init.sh' script file in your script file which calls the main function here.
#(3) the current script file 'make_ha.sh' and other related expect files 
#    including 'hostname.exp', 'rm_db_info.exp', 'scp.exp' and 'start_cubrid_ha.exp' must be put in $init_path directory.

#===========================================================user configuration begin==========================================================================
configPath=$QA_REPOSITORY/qatool_bin/qamanager/properties
master=`grep MASTER_SERVER_IP $configPath/HA.properties`
master_user=`grep MASTER_SERVER_USER $configPath/HA.properties`
master_pw=`grep MASTER_SERVER_PW $configPath/HA.properties`

slave=`grep SLAVE_SERVER_IP $configPath/HA.properties`
slave_user=`grep SLAVE_SERVER_USER $configPath/HA.properties`
slave_pw=`grep SLAVE_SERVER_PW $configPath/HA.properties`


#set port numbers according to different users

CUBRID_PORT_ID=`grep CUBRID_PORT_ID $configPath/HA.properties`
ha_port_id=`grep ha_port_id $configPath/HA.properties`
MASTER_SHM_ID=`grep MASTER_SHM_ID $configPath/HA.properties`
BROKER_PORT1=`grep BROKER_PORT1 $configPath/HA.properties`
APPL_SERVER_SHM_ID1=`grep APPL_SERVER_SHM_ID1 $configPath/HA.properties`
BROKER_PORT2=`grep BROKER_PORT2 $configPath/HA.properties`
APPL_SERVER_SHM_ID2=`grep APPL_SERVER_SHM_ID2 $configPath/HA.properties`
cm_port=`grep cm_port $configPath/HA.properties`

BROKER_PORT3=`grep BROKER_PORT3 $configPath/HA.properties`
APPL_SERVER_SHM_ID3=`grep APPL_SERVER_SHM_ID3 $configPath/HA.properties`
SHARD_PROXY_SHM_ID=`grep SHARD_PROXY_SHM_ID $configPath/HA.properties`

MASTER_SERVER_IP=${master#*=}
MASTER_SERVER_USER=${master_user#*=}
MASTER_SERVER_PW=${master_pw#*=}

SLAVE_SERVER_IP=${slave#*=}
SLAVE_SERVER_USER=${slave_user#*=}
SLAVE_SERVER_PW=${slave_pw#*=}


CUBRID_PORT_ID=${CUBRID_PORT_ID#*=}
ha_port_id=${ha_port_id#*=}
MASTER_SHM_ID=${MASTER_SHM_ID#*=}
BROKER_PORT1=${BROKER_PORT1#*=}
APPL_SERVER_SHM_ID1=${APPL_SERVER_SHM_ID1#*=}
BROKER_PORT2=${BROKER_PORT2#*=}
APPL_SERVER_SHM_ID2=${APPL_SERVER_SHM_ID2#*=}
cm_port=${cm_port#*=}
BROKER_PORT3=${BROKER_PORT3#*=}
APPL_SERVER_SHM_ID3=${APPL_SERVER_SHM_ID3#*=}
SHARD_PROXY_SHM_ID=${SHARD_PROXY_SHM_ID#*=}

masterHostName=$HOSTNAME
slaveHostName=""

#=========================================================

alias run_on_slave='$CTP_HOME/common/script/run_remote_script -host $SLAVE_SERVER_IP -user $MASTER_SERVER_USER -password "$MASTER_SERVER_PW"'
alias run_upload_on_slave='$CTP_HOME/common/script/run_upload -host $SLAVE_SERVER_IP -user $MASTER_SERVER_USER -password "$MASTER_SERVER_PW"'
alias run_download_on_slave='$CTP_HOME/common/script/run_download -host $SLAVE_SERVER_IP -user $MASTER_SERVER_USER -password "$MASTER_SERVER_PW"'

#===========================================================user configuration end==============================================================================

#set -x


#variable definition
dbPath=$CUBRID/databases
dbname=hatestdb
currentPath=`pwd`
version_str=`$CUBRID/bin/cubrid_rel`
is_R40=`echo $version_str | grep -E '8.4|9.|10.' | wc -l`

#=================================================================function definition begin========================================================================

export MASTER_SERVER_IP
export MASTER_SERVER_USER
export MASTER_SERVER_PW

export SLAVE_SERVER_IP
export SLAVE_SERVER_USER
export SLAVE_SERVER_PW

export CUBRID_PORT_ID
export ha_port_id
export MASTER_SHM_ID
export BROKER_PORT1
export APPL_SERVER_SHM_ID1
export BROKER_PORT2
export APPL_SERVER_SHM_ID2
export cm_port
export BROKER_PORT3
export APPL_SERVER_SHM_ID3
export SHARD_PROXY_SHM_ID

export masterHostName
export slaveHostName

export dbPath
export dbname
export currentPath
export is_R40

if [ $is_R40 -eq 1 ]; then
	. $init_path/make_ha_upper.sh
else
	. $init_path/make_ha_lower.sh
fi
