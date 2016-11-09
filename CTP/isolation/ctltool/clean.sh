#!/bin/bash
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

temp_file_for_clean=.isolation_clean_temp

function clean_cubrid
{
  [ $# -lt 1 ] && { echo "ERROR: missing dbname"; return 1; };
  dbname=$1
  # clean running proccess
  pkill -u $(whoami) -9 qactl >/dev/null 2>&1
  pkill -u $(whoami) -9 qacsql >/dev/null 2>&1
  cubrid tranlist -u dba $dbname|grep $HOSTNAME|awk '{print $4}'|xargs -i kill -9 {} >/dev/null 2>&1

  # delete user
  csql -u dba $dbname -c "select name from db_user where name !='DBA' and name !='PUBLIC'" > ${temp_file_for_clean} 
  cat ${temp_file_for_clean}  |grep "^ *[\']"|sed "s/'//g"|xargs -i echo "drop user " {} ";" >deleteuser.sql
  csql -u dba $dbname -i deleteuser.sql >/dev/null
  rm ${temp_file_for_clean}  deleteuser.sql

  # delete foreign key table
  csql -u dba $dbname -c "select class_name from db_index where is_foreign_key='YES';" > ${temp_file_for_clean}  
  cat ${temp_file_for_clean}  |grep "^ *[\']"|sed "s/'//g"|xargs -i echo "drop table " {} ";" >deletefk.sql
  csql -u dba $dbname -i deletefk.sql >/dev/null
  rm ${temp_file_for_clean}  deletefk.sql

  # delete trigger
  csql -u dba $dbname -c "select name from db_trigger;" >${temp_file_for_clean} 
  cat ${temp_file_for_clean}  |grep "^ *[\']"|sed "s/'//g"|xargs -i echo "drop trigger " {} ";" >deletetrigger.sql
  csql -u dba $dbname -i deletetrigger.sql >/dev/null
  rm ${temp_file_for_clean}  deletetrigger.sql

  # delete serial
  csql -u dba $dbname -c "select name from db_serial where att_name is null;" >${temp_file_for_clean} 
  cat ${temp_file_for_clean}  |grep "^ *[\']"|sed "s/'//g"|xargs -i echo "drop serial " {} ";" >deleteserial.sql
  csql -u dba $dbname -i deleteserial.sql >/dev/null
  rm ${temp_file_for_clean}  deleteserial.sql

  # delete function
  csql -u dba $dbname -c "SELECT sp_name FROM db_stored_procedure where sp_type='FUNCTION' and sp_name !='sleep' and sp_name !='sleep1' and sp_name !='sleep2'" >${temp_file_for_clean} 
  cat ${temp_file_for_clean}  |grep "^ *[\']"|sed "s/'//g"|xargs -i echo "drop function " {} ";" >deletefun.sql
  csql -u dba $dbname -i deletefun.sql >/dev/null
  rm ${temp_file_for_clean}  deletefun.sql

  # delete procedure
  csql -u dba $dbname -c "SELECT sp_name FROM db_stored_procedure where sp_type='PROCEDURE';" >${temp_file_for_clean} 
  cat ${temp_file_for_clean}  |grep "^ *[\']"|sed "s/'//g"|xargs -i echo "drop procedure " {} ";" >deletefun.sql
  csql -u dba $dbname -i deletefun.sql >/dev/null
  rm ${temp_file_for_clean}  deletefun.sql


  # delete table
  csql -u dba $dbname -c "select class_name from db_class where is_system_class='NO' and class_type='VCLASS';" >${temp_file_for_clean} 
  cat ${temp_file_for_clean}  |grep "^ *[\']"|sed "s/'//g"|xargs -i echo "drop view " {} ";" >deletetable.sql
  csql -u dba $dbname -i deletetable.sql >/dev/null
  rm ${temp_file_for_clean}  deletetable.sql

  # delete table
  csql -u dba $dbname -c "select class_name from db_class where is_system_class='NO' and class_type='CLASS';" >${temp_file_for_clean} 
  cat ${temp_file_for_clean}  |grep "^ *[\']"|sed "s/'//g"|xargs -i echo "drop table " {} ";" >deletetable.sql
  csql -u dba $dbname -i deletetable.sql >/dev/null
  rm ${temp_file_for_clean}  deletetable.sql
}



function clean_mysql
{
  [ $# -lt 1 ] && { echo "ERROR: missing dbname"; return 1; };
  dbname=$1
  mysql -u root <<EOF >/dev/null 2>&1 
drop database $dbname; 
create database $dbname; 
use $dbname; 
create function sleep2(sec INT) returns INT return (sleep(sec));
create function sleep1(sec INT,col VARCHAR(100)) returns INT return (sleep(sec)+sec); 
create table db_class(id int); 
insert into db_class values(1); 
insert into db_class select * from db_class; 
insert into db_class select * from db_class;
insert into db_class select * from db_class;
insert into db_class select * from db_class;
insert into db_class select * from db_class;
insert into db_class select * from db_class limit 10;
insert into db_class values(1);
EOF
}


function clean_oracle
{
  sqlplus /nolog <<EOF >/dev/null 2>&1
connect test/test1234
SPOOL ./clean_oracle_2.lst
begin
for user_tables in (select table_name from user_tables) loop
execute immediate 'drop table '||user_tables.table_name;
end loop;
end;
/
begin
for user_idx in (select index_name from user_indexes) loop
execute immediate 'drop index '||user_idx.index_name;
end loop;
end;
/
create table db_class(id int);
insert into db_class values(1);
insert into db_class select * from db_class;
insert into db_class select * from db_class;
insert into db_class select * from db_class;
insert into db_class select * from db_class;
insert into db_class select * from db_class where rownum <= 8;
SPOOL OFF
EXIT;
EOF
}


if [ $# -ge 1 ]; then
  echo "dbtype: $1"
  dbtype=$1; shift
fi

case $dbtype in
  cubrid|qacsql)
    clean_cubrid $@
    ;;
  mysql|qamycsql)
    clean_mysql $@
    ;;
  oracle|qaoracle)
    clean_oracle $@
    ;;
  *)
    echo "ERROR - Usage: $0 <cubrid|mysql|oracle> [arguments]"
    exit 1
    ;;
esac
