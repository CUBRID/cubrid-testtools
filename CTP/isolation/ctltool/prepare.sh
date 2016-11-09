#!/bin/sh
# 
# Copyright (c) 2016, Search Solution Corporation? All rights reserved.
#
# Redistribution and use in source and binary forms, with or without 
# modification, are permitted provided that the following conditions are met:
#
#  * Redistributions of source code must retain the above copyright notice, 
#    this list of conditions and the following disclaimer.
#
#  * Redistributions in binary form must reproduce the above copyright 
#    notice, this list of conditions and the following disclaimer in 
#    the documentation and/or other materials provided with the distribution.
#
#  * Neither the name of the copyright holder nor the names of its contributors may be used to endorse or promote products 
#    derived from this software without specific prior written permission.
#
# THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, 
# INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE 
# DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, 
# SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR 
# SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, 
# WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE 
# USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

function prepare_cubrid
{
  [ $# -lt 1 ] && { echo "ERROR: missing dbname"; return 1; };
  dbname=$1

  charset=$3
  if [ a"$charset" == "a" ]
  then
      charset=en_US
  fi
  cur=`pwd`

  cubrid service stop
  pkill -9 -u `whoami` cub
  cubrid deletedb $dbname
  cd $CUBRID/databases
  rm -rf $dbname
  mkdir $dbname
  cd $dbname
  cubrid createdb $dbname $charset --db-volume-size=50M --log-volume-size=50M
  ######################
  #for memory leak test#
  #cub_master
  #cub_server $dbname &
  #sleep 60
  ######################
  cubrid server start $dbname

  cd $cur

  if [ "a${2}" == "alog" ]
  then
  make clean qactl qacsql

  rm timeout.log
  rm core.log
  touch timeout.log
  touch core.log
  fi
}


function prepare_mysql
{
  echo "TODO"
  make clean qactlm qamysql
}


function prepare_oracle
{
  echo "TODO"
  make clean oracle
}


if [ $# -ge 1 ]; then
  echo "dbtype: $1"
  dbtype=$1; shift
fi

case $dbtype in
  cubrid|qacsql)
    prepare_cubrid $@
    ;;
  mysql|qamycsql)
    prepare_mysql $@
    ;;
  oracle|qaoracle)
    prepare_oracle $@
    ;;
  *)
    echo "ERROR - Usage: $0 <cubrid|mysql|oracle> [arguments]"
    exit 1
    ;;
esac
