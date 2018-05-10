#!/bin/bash -xe
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

# In test mode (if run_mode variable is set 1), this function creates answer files of each test case in one scenario
# on the contrary, in answer mode(if run_mode is set 0), this function writes the test result in result file
# according to result of comparing with a test result and an answer
set -x

function get_host_property {
    local host_key=$1
    local prop_name="\$${host_key}_$2"
    local host_value=`eval echo $prop_name`
    prop_name="\$D_DEFAULT_$2"
    local default_value=`eval echo $prop_name`
    
    if [ -n "${host_value}" ]; then
        echo "${host_value}"
    else
        echo "${default_value}"
    fi
}

function rexec {
    local host_key=$1
    local host_pwd=`get_host_property ${host_key} PWD`
    local host_port=`get_host_property ${host_key} PORT`
    local host_opts="-host \"\$${host_key}_IP\" -user \"\$${host_key}_USER\" -password \"${host_pwd}\" -port ${host_port}" 
    local cmd_opts=""
    local skip=1
    for item in "$@"; do
        if [ $skip -eq 1 ]; then
            skip=0
            continue
        fi
        cmd_opts=$(echo $cmd_opts \'$item\')
    done
    eval $init_path/../../common/script/run_remote_script $host_opts $cmd_opts
}

function r_upload {
    local host_key=$1
    local host_pwd=`get_host_property ${host_key} PWD`
    local host_port=`get_host_property ${host_key} PORT`
    local host_opts="-host \"\$${host_key}_IP\" -user \"\$${host_key}_USER\" -password \"${host_pwd}\" -port ${host_port}" 
    local cmd_opts=""
    local skip=1
    for item in "$@"; do
        if [ $skip -eq 1 ]; then
            skip=0
            continue
        fi
        cmd_opts=$(echo $cmd_opts \'$item\')
    done
    eval $init_path/../../common/script/run_upload $host_opts $cmd_opts
}

function r_download {
    local host_key=$1
    local host_pwd=`get_host_property ${host_key} PWD`
    local host_port=`get_host_property ${host_key} PORT`    
    local host_opts="-host \"\$${host_key}_IP\" -user \"\$${host_key}_USER\" -password \"${host_pwd}\" -port ${host_port}" 
    local cmd_opts=""
    local skip=1
    for item in "$@"; do
        if [ $skip -eq 1 ]; then
            skip=0
            continue
        fi
        cmd_opts=$(echo $cmd_opts \'$item\')
    done
    eval $init_path/../../common/script/run_download $host_opts $cmd_opts
}

function use_cubrid {
    #todo: need improve
    source $HOME/CUBRID_$1/.cubrid.sh
}

function use_cubrid_main {
    #todo: need improve
    source $HOME/CUBRID/.cubrid.sh
}

function check_disk_space_and_notice {
	expected_space="$1"
	home_vol=`df -P $HOME | grep -v Filesystem | awk '{print $1}'`
	mail_addr=`awk -F '=' "/mail_from_address/ {print \\$2}" $init_path/../../conf/common.conf|tr -d '\r'`
	echo "check_disk_space $home_vol $expected_space $mail_addr" > check_temp.act
	$init_path/../../common/script/run_action_files check_temp.act
	rm check_temp.act
}

function get_cubrid_version()
{
        cubrid_rel|grep "CUBRID"|awk -F '(' '{print $2}'|sed 's/)//g'|sed 's/[[:space:]]//g'
}

function get_matched_cubrid_pkg_deps()
{
        file=$1
        build_id=`get_cubrid_version`
        cat $file | grep cubrid_pkg_deps | sed "s/{BUILD_ID}/$build_id/g" |awk -F ": " '{print $2}'
}


function backup_file {
	if [ ! -f "$1.org" ]; then
		cp $1 $1.org
	fi
}

function restore_file {
	if [ -f "$1.org" ]; then
		cp -f $1.org $1
	else
		echo "Not find file: $1.org" >&2
	fi
}

function backup_cubrid_config {
	backup_file ${CUBRID}/conf/cubrid.conf
	backup_file ${CUBRID}/conf/cubrid_broker.conf
	backup_file ${CUBRID}/conf/cubrid_ha.conf
	backup_file ${CUBRID}/conf/cubrid_locales.txt
}

function restore_cubrid_config {
	restore_file ${CUBRID}/conf/cubrid.conf
	restore_file ${CUBRID}/conf/cubrid_broker.conf
	restore_file ${CUBRID}/conf/cubrid_ha.conf
	restore_file ${CUBRID}/conf/cubrid_locales.txt
}

function backup_cubrid_config_on_remote {
	for host in $@ ; do
		rexec $host -c "source \$init_path/init_ext.sh; backup_cubrid_config"
	done
}

function restore_cubrid_config_on_remote {
	for host in $@ ; do
		rexec $host -c "source \$init_path/init_ext.sh; restore_cubrid_config"
	done
}

function sync_cubrid_config_to_remote {
	for host in $@ ; do 
		r_upload $host -from $CUBRID/conf/cubrid.conf -to $CUBRID/conf/
		r_upload $host -from $CUBRID/conf/cubrid_broker.conf -to $CUBRID/conf/
		r_upload $host -from $CUBRID/conf/cubrid_ha.conf -to $CUBRID/conf/
		r_upload $host -from $CUBRID/conf/cubrid_locales.txt -to $CUBRID/conf/
	done
}

ha_hosts=""

#usage example: cubrid_ha_create -s D_HOST1,D_HOST2,... -r D_HOST1,D_HOST2,...
function cubrid_ha_create {
	local slave_hosts=""
	local replica_hosts=""
	local params=""

	while [ $# -ne 0 ]; do
		case $1 in
			-s)
				shift
				slave_hosts=`echo $1 | sed "s/,/ /g"`
				;;
			-r)
				shift
				replica_hosts=`echo $1 | sed "s/,/ /g"`
				;;
			*)
                                params="$params $1"	
				;;
		esac
		shift
	done

	ha_hosts="$slave_hosts $replica_hosts"

	backup_cubrid_config
	backup_cubrid_config_on_remote $ha_hosts
		
	prop_ha_node_list="${USER}@`hostname`"
	for host in $slave_hosts; do
		prop_ha_node_list=$prop_ha_node_list:`rexec $host -c 'hostname'`
	done


	if [ -n "$replica_hosts" ]; then
		prop_ha_replica_list="${USER}"
		for host in $replica_hosts; do
			prop_ha_replica_list=$prop_ha_replica_list:`rexec $host -c 'hostname'`
		done

		prop_ha_replica_list=`echo $prop_ha_replica_list|sed s/:/@/`
	fi

	ini="sh $init_path/../../bin/ini.sh"
	if [ -n "$replica_hosts" ]; then
		$ini -s "common" -u "ha_node_list=${prop_ha_node_list}||ha_db_list=hatestdb||ha_replica_list=${prop_ha_replica_list}" $CUBRID/conf/cubrid_ha.conf
	else
		$ini -s "common" -u "ha_node_list=${prop_ha_node_list}||ha_db_list=hatestdb" $CUBRID/conf/cubrid_ha.conf
	fi

	$ini -s "common" $CUBRID/conf/cubrid.conf ha_mode on

	sed -i 's/ = /=/g' $CUBRID/conf/cubrid.conf
	sed -i 's/ = /=/g' $CUBRID/conf/cubrid_ha.conf

	sync_cubrid_config_to_remote $ha_hosts

	for host in $replica_hosts; do
		rexec $host -c "sh \$init_path/../../bin/ini.sh -s common \$CUBRID/conf/cubrid.conf ha_mode replica"
	done

	cmds="(mkdir -p \$CUBRID/databases/hatestdb; cd \$CUBRID/databases/hatestdb;source \$init_path/init.sh; cubrid_createdb hatestdb $params)"
	eval $cmds
	for node in $ha_hosts; do
		rexec $node -c "$cmds"
	done
 }

function cubrid_ha_destroy {
	cmds="(cubrid service stop; cubrid deletedb hatestdb; rm -rf \${CUBRID}/databases/hatestdb*)"
	for host in $ha_hosts $@ ; do
		rexec $host -c "$cmds"
		restore_cubrid_config_on_remote $host
	done

	eval "$cmds"
	restore_cubrid_config
}

function check_with_loop {
	loops="$1"
	commands="$2"
	expected_text="$3"
	enable_verify="$4"
	for ((i=0;i<$loops;i++)); do 
		if $commands | grep -E "${expected_text}"; then
			return
		fi

		sleep 1
	done

	if [ "$enable_verify" = "true" ]; then
		write_nok "Timeout. Commands: ${commands}. Expect: ${expected_text}"
	fi
}

function cubrid_ha_start {
	cubrid hb start
	check_with_loop 60 "cubrid changemode hatestdb@localhost" "current HA running mode is active|to-be-active" true

	for host in $ha_hosts $@ ; do
		rexec $host -c "cubrid hb start"
	done

	cubrid hb status
}


function cubrid_ha_stop {
	for host in $ha_hosts $@ ; do
		rexec $host -c "cubrid hb stop"
	done

	cubrid hb stop
}

function cubrid_service_stop {
	for host in $ha_hosts $@ ; do
		rexec $host -c "cubrid service stop"
	done

	cubrid service stop
}

function wait_replication_done
{
	csql -udba hatestdb -c "create table wait_for_slave(a int primary key, b varchar(20));insert into wait_for_slave values(999, 'replication finished');"

	if [ $# -eq 0 ]; then
		hosts="$ha_hosts"
	else
		hosts="$@"
	fi

	for host in $hosts; do
		rexec $host -c "csql -udba -c \"select b from wait_for_slave\" hatestdb" -tillcontains "replication finished" -interval 2
	done

	csql -udba hatestdb -c "drop table wait_for_slave"
}

function replace_oid_with_class_name()
{
        [ $# -lt 2 ] && return

        local oid_file=$1
        local db_name=$2
	local server_status=0
	[ -z "$db_name" ] || [ ! -s "$oid_file" ] && return

	rm dialog.data >/dev/null 2>&1
	cubrid server status >server_status.log 2>&1
        if [ `grep "Server $db_name" server_status.log|wc -l` -gt 0 ];then
	    cubrid server stop $db_name
	    server_status=1
	fi
	
        cubrid  diagdb -o dialog.data -d 5 $db_name
	sed -i ':t;N;s/TRAN_INDEX =.*\n//;b t' dialog.data
        oid_list=`grep -onE '([-]*[0-9]+\|[-]*[0-9]+\|[-]*[0-9]+)' $oid_file |awk -F ':' '{print $2}'|sort|uniq`
	for x in $oid_list
	do
	    if [ "$x" == "-1|-1|-1" ];then
		continue
	    fi

	    class_name=`grep -w "$x" dialog.data|awk '{print $2}'|tr -d ' '`
	    if [ -n "$class_name" ];then
	    	sed -i "s/($x)/(?|?|?, $class_name)/g" $oid_file
	    else
		sed -i "s/($x)/(?|?|?)/g" $oid_file
	    fi    
	done

	if [ $server_status -eq 1 ];then
	    cubrid server start $db_name
	fi

	rm dialog.data >/dev/null 2>&1
	rm server_status.log >/dev/null 2>&1
}
