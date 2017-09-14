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


function rexec {
    host_key=$1
    host_opts="-host \"\$${host_key}_IP\" -user \"\$${host_key}_USER\" -password \"$D_DEFAULT_PWD\" -port $D_DEFAULT_PORT" 
    cmd_opts=""
    skip=1
    for item in "$@"; do
        if [ $skip -eq 1 ]; then
            skip=0
            continue
        fi
        cmd_opts=$(echo $cmd_opts \'$item\')
    done
    eval $init_path/../../common/script/run_remote_script $host_opts $cmd_opts
}


function use_cubrid {
    #todo: need improve
    source $HOME/CUBRID_$1/.cubrid.sh
}

function use_cubrid_main {
    #todo: need improve
    source $HOME/CUBRID/.cubrid.sh
}

function r_upload {
    host_key=$1
    host_opts="-host \"\$${host_key}_IP\" -user \"\$${host_key}_USER\" -password \"$D_DEFAULT_PWD\" -port $D_DEFAULT_PORT" 
    cmd_opts=""
    skip=1
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
    host_key=$1
    host_opts="-host \"\$${host_key}_IP\" -user \"\$${host_key}_USER\" -password \"$D_DEFAULT_PWD\" -port $D_DEFAULT_PORT" 
    cmd_opts=""
    skip=1
    for item in "$@"; do
        if [ $skip -eq 1 ]; then
            skip=0
            continue
        fi
        cmd_opts=$(echo $cmd_opts \'$item\')
    done
    eval $init_path/../../common/script/run_download $host_opts $cmd_opts
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


function sync_cubrid_config 
{
	host_key=$1
	r_upload $host_key -from $CUBRID/conf/cubrid.conf -to CUBRID/conf/
	r_upload $host_key -from $CUBRID/conf/cubrid_broker.conf -to CUBRID/conf/
	r_upload $host_key -from $CUBRID/conf/cubrid_ha.conf -to CUBRID/conf/
	r_upload $host_key -from $CUBRID/conf/cubrid_locales.txt -to CUBRID/conf/
}


function badckup_one_config 
{
	if [ ! -f "$1.origin" ]; then
		cp $1 $1.origin
	fi
}

function restore_one_config {
	if [ -f "$1.origin" ]; then
		cp $1.origin $1
	else
		echo "The backup file cannot be found"
	fi
}

function badckup_cubrid_config_local {
    badckup_one_config ${CUBRID}/conf/cubrid.conf
    badckup_one_config ${CUBRID}/conf/cubrid_broker.conf
    badckup_one_config ${CUBRID}/conf/cubrid_ha.conf
    badckup_one_config ${CUBRID}/conf/cubrid_locales.txt
}

function restore_cubrid_config_local {
    restore_one_config ${CUBRID}/conf/cubrid.conf
    restore_one_config ${CUBRID}/conf/cubrid_broker.conf
    restore_one_config ${CUBRID}/conf/cubrid_ha.conf
    restore_one_config ${CUBRID}/conf/cubrid_locales.txt
}


function backup_cubrid_config {
    badckup_cubrid_config_local
    for host in "$@"
    do
        rexec $host -c "source $init_path/init_ext.sh; badckup_cubrid_config_local"
    done
}

function restore_cubrid_config {
    restore_cubrid_config_local
    for host in "$@"
    do
        rexec $host -c "source $init_path/init_ext.sh; restore_cubrid_config_local"
    done
}


function modify_conf_for_ha { 

	sed -i '/ha_node_list/'d ${CUBRID}/conf/cubrid_ha.conf 
	sed -i '/ha_db_list/'d ${CUBRID}/conf/cubrid_ha.conf   

	echo "$ha_node_list" >> ${CUBRID}/conf/cubrid_ha.conf 
	echo "ha_db_list=hatestdb" >> ${CUBRID}/conf/cubrid_ha.conf 

	if [ $replica_cnt -gt 0 ]
	then
		echo "$ha_replica_list" >> ${CUBRID}/conf/cubrid_ha.conf 
	fi
	
	sed -i '/ha_mode/'d ${CUBRID}/conf/cubrid.conf
	echo 'ha_mode=on' >> ${CUBRID}/conf/cubrid.conf

	for node in $all_nodes
	do
		sync_cubrid_config $node
	done
  
	for node in ${replica_list[@]}
	do
		rexec $node -c "sed -i '/ha_mode/'d \${CUBRID}/conf/cubrid.conf"
		rexec $node -c "echo 'ha_mode=replica' >> \${CUBRID}/conf/cubrid.conf"
	done

}


function usage()
{
        exec_name=$(basename $0)
                cat<<EOF
                Usage:$exec_name <-s slave_list> <-r replica_list>
                -s <slave_list>      | slave node list, seperated by ','
                -r <replica_list>    | replica node list, seperated by ','
EOF

}

#main function to create ha enviroment
function setup_ha_environment_new 
{
	slaves=""
	replicas=""
	all_nodes=""

	while [ $# -ne 0 ]
	do
		case $1 in
			-s)
				shift
				slaves=$1
				;;
			-r)
				shift
				replicas=$1
				;;
			*)
				;;
		esac
			shift
        done

       
        if [ -n "$slaves" ]
        then
                OLD_IFS="$IFS"
                IFS=","
                slave_list=($slaves)
                IFS="$OLD_IFS"
        else
                usage
                exit 1
        fi    
    
        
        if [ -n "$replicas" ]
        then
                OLD_IFS="$IFS"
                IFS=","
                replica_list=($replicas)
                IFS="$OLD_IFS"
        else
                usage
                exit 1
        fi  
	
	host_name=`hostname`
	ha_node_list="ha_node_list=${D_HOST1_USER}@${host_name}"	
	slave_cnt=${#slave_list[*]}

	for ((i=0;i<$slave_cnt;i++))
	do
		t_hostname=`rexec ${slave_list[$i]} -c "hostname"`
		ha_node_list="${ha_node_list}:${t_hostname}"
		all_nodes="${all_nodes}${slave_list[$i]} "
	done


  
	ha_replica_list="ha_replica_list=${D_HOST1_USER}@"
	replica_cnt=${#replica_list[*]}

	if [ $replica_cnt -gt 0 ]
	then
		t_hostname=`rexec ${replica_list[0]} -c "hostname"`
		ha_replica_list="${ha_replica_list}${t_hostname}"
		all_nodes="${all_nodes}${replica_list[0]} "
	fi

	for ((i=1;i<$replica_cnt;i++))
	do
		t_hostname=`rexec ${replica_list[$i]} -c "hostname"`
		ha_replica_list="${ha_replica_list}:${t_hostname}"		
		all_nodes="${all_nodes}${replica_list[$i]} "
	done

	backup_cubrid_config $all_nodes
	modify_conf_for_ha

	cdir=`pwd`
	cubrid service stop
	cubrid deletedb hatestdb
	rm -rf ${CUBRID}/databases/hatestdb*
	mkdir -p $CUBRID/databases/hatestdb
	cd $CUBRID/databases/hatestdb 
	cubrid createdb hatestdb en_US

	cd $cdir

	for node in $all_nodes
	do
		rexec $node -c "cubrid service stop; cubrid deletedb hatestdb; rm -rf ${CUBRID}/databases/hatestdb*; mkdir -p ${CUBRID}/databases/hatestdb; cd ${CUBRID}/databases/hatestdb; cubrid createdb hatestdb en_US"
	done	
 }

function revert_ha_environment_new 
{
	cubrid service stop
	cubrid deletedb hatestdb
	rm -rf ${CUBRID}/databases/hatestdb*

	for node in $all_nodes
	do
		rexec $node -c "cubrid service stop; cubrid deletedb hatestdb; rm -rf ${CUBRID}/databases/hatestdb"
	done
	
	restore_cubrid_config $all_nodes
}