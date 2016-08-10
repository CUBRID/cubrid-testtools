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


#=============================================================== function area ===============================================================================
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
    val=`cat $configPath/HA.properties|grep "$key"`
    val=${val#*=}
    val=$(trim "$val")
    echo $val
}

#===========================================================user configuration begin==========================================================================
configPath=${init_path}

MASTER_SERVER_IP=`read_property "MASTER_SERVER_IP"`
MASTER_SERVER_USER=`read_property "MASTER_SERVER_USER"`
MASTER_SERVER_PW=`read_property "MASTER_SERVER_PW"`
MASTER_SERVER_SSH_PORT=`read_property "MASTER_SERVER_SSH_PORT"`

SLAVE_SERVER_IP=`read_property "SLAVE_SERVER_IP"`
SLAVE_SERVER_USER=`read_property "SLAVE_SERVER_USER"`
SLAVE_SERVER_PW=`read_property "SLAVE_SERVER_PW"`
SLAVE_SERVER_SSH_PORT=`read_property "SLAVE_SERVER_SSH_PORT"`

CUBRID_PORT_ID=`read_property "CUBRID_PORT_ID"`
HA_PORT_ID=`read_property "HA_PORT_ID"`
MASTER_SHM_ID=`read_property "MASTER_SHM_ID"`
BROKER_PORT1=`read_property "BROKER_PORT1"`
APPL_SERVER_SHM_ID1=`read_property "APPL_SERVER_SHM_ID1"`
BROKER_PORT2=`read_property "BROKER_PORT2"`
APPL_SERVER_SHM_ID2=`read_property "APPL_SERVER_SHM_ID2"`
CM_PORT=`read_property "CM_PORT"`

masterHostName=$HOSTNAME
slaveHostName=""

#=========================================================

alias run_on_slave='${init_path}/../../common/script/run_remote_script -host $SLAVE_SERVER_IP -port $SLAVE_SERVER_SSH_PORT -user $SLAVE_SERVER_USER -password "$SLAVE_SERVER_PW"'
alias run_upload_on_slave='${init_path}/../../common/script/run_upload -host $SLAVE_SERVER_IP -port $SLAVE_SERVER_SSH_PORT -user $SLAVE_SERVER_USER -password "$SLAVE_SERVER_PW"'
alias run_download_on_slave='${init_path}/../../common/script/run_download -host $SLAVE_SERVER_IP -port $SLAVE_SERVER_SSH_PORT -user $SLAVE_SERVER_USER -password "$SLAVE_SERVER_PW"'

#===========================================================user configuration end==============================================================================
#set -x

#variable definition
dbPath=$CUBRID/databases
dbname=hatestdb
currentPath=`pwd`
build_id=`cubrid_rel | grep CUBRID | awk -F '(' '{print $2}' | awk -F ")" '{print $1}'`
p1=`echo $build_id | awk -F "." '{print $1}'`
p2=`echo $build_id | awk -F "." '{print $2}'`
if [ $p1 -le 8 -a $p2 -lt 4 ] ; then
	is_R40=0
else
    is_R40=1
fi
#=================================================================function definition begin========================================================================

export MASTER_SERVER_IP
export MASTER_SERVER_USER
export MASTER_SERVER_PW

export SLAVE_SERVER_IP
export SLAVE_SERVER_USER
export SLAVE_SERVER_PW

export CUBRID_PORT_ID
export HA_PORT_ID
export MASTER_SHM_ID
export BROKER_PORT1
export APPL_SERVER_SHM_ID1
export BROKER_PORT2
export APPL_SERVER_SHM_ID2
export CM_PORT
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
