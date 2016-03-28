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
set -x

# usage: set_file_param filename $filename $key $value
#        set_file_param filename $filename $key $value $startLineNumber
#        set_file_param filename $filename $key $value $startLineNumber $endLineNumber
# example: set_file_param filename $CUBRID/conf/cubrid_broker.conf SQL_LOG OFF 1 20
function set_file_param_pos_number()
{
    filename=$1
    key=$2
    keyvalue="$2=$3"
    start=$4
    end=$5
    
    startPos=1
    endPos=`cat ${filename}|wc -l`
    
    # if start is a number, then assign value to it
    if [ ! -z "${start##*[!0-9]*}" ] 
    then
        startPos=${start}
    fi
    if [ ! -z "${end##*[!0-9]*}" ]
    then
        endPos=${end}
    fi 
    
    if [ `sed -n "${startPos},${endPos} {/^ *${key} *=/p}" $filename|wc -l` -eq 0 ]
    then
        #it doesn't exist, add it
        sed -i "${endPos}i${keyvalue}" $filename
    else
        # it already exists, modify it
        sed -i "${startPos},${endPos} {s/^ *${key} *=.*$/${keyvalue}/g}" $filename
    fi    
}

# Usage get_line_num_by_word <words> <filename>
# return the linenumber of $findString in $filename
function get_line_num_by_word()
{
    if [ $# -ne 2 ]
    then
        echo -2
        return
    fi
    str=$1
    fname=$2
    if  [ "$str" != "" ]
    then
        pos=`grep -n "$str" $fname|head -1|awk -F: '{print $1}'`
        if [ "$pos" != "" ]
        then
            echo $pos
            return
        fi
    fi
    echo -1
}

# usage: set_file_param filename $filename $key $value
#        set_file_param filename $filename $key $value $startString
#        set_file_param filename $filename $key $value $startString $endString
# example: set_file_param filename $CUBRID/conf/cubrid_broker.conf SQL_LOG OFF "query_editor" "broker1"
function set_file_param()
{
    filename=$1
    key=$2
    keyvalue="$2=$3"
    start="$4"
    end="$5"
    
    startPos=1
    endPos=`cat $filename|wc -l`
    
    # assign startPos and endPos by $startString and $endString
    spos=`get_line_num_by_word $start $filename`
    if  [ $spos -gt 0 ]
    then
        startPos=${spos}
        # if there is correct start, then get the end
        sed -n "$startPos,$ p" $filename >tmp
        epos=`get_line_num_by_word "$end" tmp`
        if [ $epos -gt 0 ]
        then
            endPos=`expr ${epos} + ${startPos} - 1`
        fi
    fi 
    
    if [ `sed -n "${startPos},${endPos} {/^ *${key} *=/p}" $filename|wc -l` -eq 0 ]
    then
        #it doesn't exist, add it
        sed -i "${endPos}i${keyvalue}" $filename
    else
        # it already exists, modify it
        sed -i "${startPos},${endPos} {s/^ *${key} *=.*$/${keyvalue}/g}" $filename
    fi
}

# function: it will get environment parameter which looks like "$PREFIX_$KEY = $VALUE|$START|$END",
#           and then add/modify them in $changedFileName
# usage: autoConfigByMKEY $prefix $changedFileName
#        autoConfigByMKEY "MEY_BROKER" $CUBRID/conf/cubrid.conf
function autoConfigByMKEY()
{
    prefix=$1
    filename=$2
    
    for line in `set|grep "^ *${prefix}"`
    do
        key=`echo $line|awk -F= '{print $1}'|sed "s/${prefix}//g"`
        # get string OFF|query_editor|^$ from string MKEY_BROKER_SQL_LOG='OFF|query_editor|^$'
        valueStr=${line##*=}
        # erase blackspace and single quote at the begin and the end
        valueStr=`echo $valueStr|sed "s/^ *'*//g"|sed "s/'* *//g"`
        
        # get value from OFF|query_editor|^$
        value=${valueStr%%|*}
        start=`echo $valueStr|awk -F'|' '{print $2}'`
        end=`echo $valueStr|awk -F'|' '{print $3}'`
        set_file_param $filename $key $value $start $end
    done 
}

# usage: MKEY_BROKER_$KEY = $VALUE|$START|$END
#        MKEY_BROKER_SQL_LOG="OFF|query_editor|^$"
#        MKEY_BROKER_SQL_LOG=OFF
function autoConfigBrokerByMKEY()
{
    autoConfigByMKEY "MKEY_BROKER_" "$CUBRID/conf/cubrid_broker.conf"
}

function autoConfigServerByMKEY()
{
    autoConfigByMKEY "MKEY_SERVER_" "$CUBRID/conf/cubrid.conf" 
}

function autoConfigCUBRIDByMKEY()
{
    autoConfigBrokerByMKEY
    autoConfigServerByMKEY
}
