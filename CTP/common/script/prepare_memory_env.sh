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

request_opts="setup"
export CTP_HOME=$(cd $(dirname $(readlink -f $0))/../..; pwd)

function prepare()
{
   request_opts=$@
   case "$request_opts" in
     setup)
	prepare_memory_env ;;
     clean)
	clean_memory_env ;;
     *)
	echo "You do not give options for preparation, so execute the default setup operation"
        prepare_memory_env
	;;
   esac	
}

function check_env()
{
   if [ ! -d  "$VALGRIND_HOME" ];then
	echo "Check VALGRIND_HOME environment variable and make sure the directory of VALGRIND_HOME exists"
	echo "Make sure your valgrind installed is from https://github.com/CUBRID/cubrid-testtools-internal/tree/master/valgrind"
        exit
   fi

   if [ ! -d  "$CUBRID" ];then
        echo "Check CUBRID environment variable and make sure the directory of CUBRID exists"
        exit
   fi
}

function prepare_memory_env()
{
    cur_dir=`pwd`
    echo "Preparing..."
    cd $CUBRID/bin
    if [ ! -f server.exe ];then
    	mv cub_server server.exe
    fi

    if [ ! -f cas.exe ];then	
   	 mv cub_cas cas.exe
    fi

    cp -f $CTP_HOME/sql/memory/cub_cas.c .
    cp -f $CTP_HOME/sql/memory/cub_server.c .
    
    gcc -o cub_server cub_server.c
    gcc -o cub_cas cub_cas.c 
   
    if [ ! -d "$CTP_HOME/result/memory" ];then
        mkdir -p $CTP_HOME/result/memory
    fi

    cd $cur_dir
    echo "End"
}

function clean_memory_env()
{
    echo "Starting to clean..."
    cur_dir=`pwd`
    cd $CUBRID/bin
    if [ -f server.exe ];then
    	mv server.exe cub_server
    fi
    if [ -f cas.exe ];then
    	mv cas.exe cub_cas
    fi
    
    if [ -f "$CUBRID/bin/cub_cas.c" ];then
	rm $CUBRID/bin/cub_cas.c
    fi

    if [ -f "$CUBRID/bin/cub_server.c" ];then
        rm $CUBRID/bin/cub_server.c
    fi

    cd $cur_dir
    echo "End"
}

#main
check_env

prepare $@


