#!/bin/sh

para_app=""
para_vm=""

cnt=0
for i in $*
do
    cnt=`echo $i| grep "\-D" | wc -l`
    if [ $cnt -eq 1 ]
    then
      para_vm="$para_vm $i"
    else
      para_app="$para_app $i"
    fi
done

exec_j="java -cp lib/commons-dbcp-1.4.jar:lib/commons-logging-1.1.jar:lib/commons-logging-api-1.0.4.jar:lib/commons-pool-1.6.jar:lib/httpclient-4.2.5.jar:lib/httpcore-4.2.4.jar:lib/JDBC-8.1.5.1039.jar:lib/jsch-0.1.51.jar:bin $para_vm com.nhncorp.cubrid.cc.Main $para_app"

echo $exec_j > conf/current_build

`$exec_j >run.log 2>&1`

sh package_fail.sh

