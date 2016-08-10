#!/bin/sh

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
