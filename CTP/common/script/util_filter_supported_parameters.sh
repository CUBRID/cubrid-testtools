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

#set -x

# util_filter_supported_parameters.sh 
# Description: This script receives the config list then only prints the CUBRID supported configs.
# Usage:          
#    <input_str> | sh util_filter_supported_parameters.sh 
# 
# For example:
#    echo "ha_mode = yes" | sh util_filter_supported_parameters.sh 
#    sh ~/CTP/bin/ini.sh -s sql/cubrid.conf  ~/CTP/conf/sql.conf | sh util_filter_supported_parameters.sh 

cubrid_ver=""
input_str=""
all_params=all_params.txt

function create_db()
{
    db_name=$1
    db_options=$2

    cubrid_ver=`cubrid_rel | grep CUBRID | awk -F'(' '{print $2}' |  awk -F')' '{print $1}'`
    cubrid_ver_p1=`echo $cubrid_ver | cut -d . -f 1 | grep -oE "[[:digit:]]{1,}"`
    cubrid_ver_p2=`echo $cubrid_ver | cut -d . -f 2`

    mkdir $db_name
    cd $db_name
    cubrid createdb -r $db_name en_US $db_options 2>&1 >/dev/null || cubrid createdb -r $db_name $db_options 2>&1 >/dev/null
    cd ..
}

function get_all_param()
{
    db_name="db_`date +%Y%m%d%k%M%S`"
    create_db $db_name "--db-volume-size=20M --log-volume-size=20M"
    
    cubrid paramdump -S $db_name
    
    cubrid deletedb $db_name
    rm -rf $db_name
}

#clean 
rm -f $all_params 

#get all parameters
get_all_param | cut -d= -f1 -s > $all_params

#read & check & print
   cat /dev/stdin | grep -w -f $all_params 

#clean
rm -f $all_params



