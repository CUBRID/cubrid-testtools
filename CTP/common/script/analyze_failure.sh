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
core_file_name=
error_msg_keyword=
pkg_file=
fix_backup_dir_name="do_not_delete_core"
json_create_suffix="_CREATE.json"
json_comment_suffix="_COMMENT.json"
export CTP_HOME=$(cd $(dirname $(readlink -f $0))/../..; pwd)
JAVA_CPS=${CTP_HOME}/common/lib/cubridqa-common.jar

function usage
{
    cat <<CCTTPP
Usage: sh analyze_failure.sh [-c|-e] core_file_name|error_msg_keywords [-p] package-file-path-for-failures
       Valid options :
        [-c|-e]      : core file name or error message keywords 
        [-p]	     : package file path for failures
CCTTPP
        exit 1
}

while getopts "c:e:p:h" opt; do
  	case $opt in
                c)
                    core_file_name="$OPTARG"
                    ;;
                e)
                    error_msg_keyword="$OPTARG"
                    ;;
                p)
                    pkg_file="$OPTARG"
                    ;;
		\?)
		    usage
		    ;;
        esac
done

if [ ! "$core_file_name" ] && [ ! "$error_msg_keyword" ];then
	echo "Not get core file name or error msg keyword parameter."
	usage;
fi 

if [ ! "$pkg_file" ] || [ ! -f "$pkg_file" ];then
	echo "Not get the package file path of failure."
        usage;
fi

is_running=`ps -u $USER -o cmd | awk -F '/bash ' '{print$NF}' | grep $0 | grep -v grep|wc -l`
if [ $is_running -gt 2 ];then
	echo "`ps -u $USER -o cmd | awk -F '/bash ' '{print$NF}' | grep $0 | grep -v grep`"
	echo "[Info]: The current script is running!"
	exit 1
fi

pkg_file=$(readlink -f $pkg_file)
pkg_file_name="${pkg_file##*/}"
pkg_file_name_without_ext="${pkg_file_name%.tar*}"

if [ "$core_file_name" ];then
	core_full_name=`echo $core_file_name|sed 's/\.//g'|tr '[a-z]' '[A-Z]'`
	data_issue_create_json_name="${pkg_file_name_without_ext}_${core_full_name}${json_create_suffix}"
	data_issue_comment_json_name="${pkg_file_name_without_ext}_${core_full_name}${json_comment_suffix}"
elif [ "$error_msg_keyword" ];then
	err_keyword=`echo $error_msg_keyword|sed 's/[[:space:]]//g'|tr '[a-z]' '[A-Z]'`
	data_issue_create_json_name="${pkg_file_name_without_ext}_${err_keyword}${json_create_suffix}"
	data_issue_comment_json_name="${pkg_file_name_without_ext}_${err_keyword}${json_comment_suffix}"
fi

JAVA_CPS=${CTP_HOME}/common/lib/cubridqa-common.jar
if [ "$OSTYPE" == "cygwin" ]
then
    JAVA_CPS=`cygpath -wpm $JAVA_CPS`
fi


if [ -d "${HOME}/${fix_backup_dir_name}" ];then
	cd ${HOME}/${fix_backup_dir_name}
else
	cd ${HOME}
	mkdir -p $fix_backup_dir_name
	cd $fix_backup_dir_name
fi 

function export_CUBRID_environment()
{
	curDir=`pwd`

	if [ -d "CUBRID" ];then
		CUBRID=${curDir}/CUBRID
		CUBRID_DATABASES=$CUBRID/databases	
		if [ "x${LD_LIBRARY_PATH}x" = xx ]; then
			LD_LIBRARY_PATH=$CUBRID/lib
		else
			LD_LIBRARY_PATH=$CUBRID/lib:$LD_LIBRARY_PATH
		fi

		SHLIB_PATH=$LD_LIBRARY_PATH
		LIBPATH=$LD_LIBRARY_PATH
		PATH=$CUBRID/bin:$PATH
		export CUBRID
		export CUBRID_DATABASES
		export LD_LIBRARY_PATH
		export SHLIB_PATH
		export LIBPATH
		export PATH
	fi
	cd $curDir
}

function get_affect_version()
{
	ver=$1
	affect_ver=""
	if [ "$ver" ];then
		version_prefix=`echo $ver|awk -F '.' '{print $1"."$2"."$3}'`
		if [ "$version_prefix" == "10.1.0" ];then
			affect_ver="banana pie"		
		fi
	fi
	echo $affect_ver
}

function get_os()
{
	OS=`uname`
	OS_VER=
	OS_INFO=`uname -a|grep -E "[x86_64|x64]"|wc -l`
	if [ $OS_INFO -ne 0 ];then
		OS_VER="$OS 64bit"
	else
		OS_VER="$OS 32bit"
	fi
	echo $OS_VER
}


function gen_data_template()
{
	curDir=`pwd`	
	core_file_path=$1
        if [ ! "$core_file_path" ] || [ ! -f "$core_file_path" ];then
		echo ""
		echo "[Skip]: core file $core_file_path does not exist!"
 		return
	fi	

	analyzer.sh -f $core_file_path > core_full_stack.txt
	build_version=`cat readme.txt |grep "CUBRID VERSION"|grep -v grep|awk -F ':' 'BEGIN{OFS=":"} {$1="";print $0}'|sed 's/^://g'|sed 's/^[ \t]*//g'`
	core_dir="${core_file_path%/*}"
	core_name="${core_file_path##*/}"
	build_id=`echo $build_version|awk -F '(' '{print $2}'|sed 's/)//g'|sed 's/[[:space:]]//g'`
	affect_version=`get_affect_version $build_id`
	cd $core_dir
	core_cmd=`file $core_name`
	cd -

	summary_info=`cat core_full_stack.txt|grep "SUMMARY:"|sed 's/SUMMARY://g'`
	cat core_full_stack.txt|grep -v "SUMMARY:" > core_full_stack.info
	
	#generate issue description 
	echo "TEST_BUILD=$build_version" > issue_create_desc.data
	echo "TEST_OS=`get_os`" >> issue_create_desc.data
	echo "CORE_FILE=$core_cmd" >> issue_create_desc.data
	echo "CALL_STACK_INFO=file:core_full_stack.info" >> issue_create_desc.data
	echo "ISSUE_SUMMARY_INFO=$summary_info" >> issue_create_desc.data

	#generate issue field data
	echo "JSON_TPL_ISSUE_SUMMARY_INFO=" > issue_create.data
	echo "JSON_TPL_AFFECT_VERSION=$affect_version" >> issue_create.data
	echo "JSON_TPL_CALL_STACK_INFO=json_file:issue_create_desc.out" >> issue_create.data

	#generate comment data file content
	user_info=`cat readme.txt |grep TEST_INFO_ENV|grep -v export|awk -F '=' '{print $NF}'`
	related_case=`cat readme.txt |grep "TEST CASE:"|grep -v grep|grep -v freadme|awk -F ':' '{print $NF}'`
	is_only_demodb=`find ./ -name "*_vinf"|grep -v "demodb_vinf"|wc -l`
	if [ $is_only_demodb -eq 0 ];then
		volume_file_path=`find ./ -name "*_vinf"|grep demodb_vinf`
		volume_path="${volume_file_path%/*}"
		db_volume_info="${curDir}/${volume_path}"
	else
		volume_file_path=`find ./ -name "*_vinf"|grep -v demodb_vinf|head -1`
		vlume_path="${volume_file_path%/*}"
		db_volume_info="${curDir}/${vlume_path}"
	fi


	echo "*Test Server:*" > issue_comment_desc.out
	echo "user@IP:$user_info" >> issue_comment_desc.out
	echo "pwd: <please use general password>" >> issue_comment_desc.out
	echo " " >> issue_comment_desc.out
	echo "*All Info*" >> issue_comment_desc.out
	echo "${user_info}:${curDir}" >> issue_comment_desc.out
	echo "pwd: <please use general password>" >> issue_comment_desc.out
	echo "*Core Location:*${curDir}/${core_file_path}" >> issue_comment_desc.out
	echo "*DB-Volume Location:*${db_volume_info}" >> issue_comment_desc.out
	echo "*Error Log Location:*${curDir}/CUBRID/log" >> issue_comment_desc.out
	echo " " >> issue_comment_desc.out
	echo " " >> issue_comment_desc.out
	echo "*Related Case:* $related_case" >> issue_comment_desc.out

	echo "JSON_TPL_COMMENT_BODY=json_file:issue_comment_desc.out" > issue_comment.data

	"$JAVA_HOME/bin/java" -cp "$JAVA_CPS" com.navercorp.cubridqa.common.MergeTemplate -t ${CTP_HOME}/common/tpl/issue_create_desc.tpl -d issue_create_desc.data -o issue_create_desc.out
	"$JAVA_HOME/bin/java" -cp "$JAVA_CPS" com.navercorp.cubridqa.common.MergeTemplate -t ${CTP_HOME}/common/tpl/issue_create.tpl -d issue_create.data -o ${data_issue_create_json_name}
	if [ $? -eq 0 ];then
		echo "CREATE_ISSUE_FIELDS=${curDir}/${data_issue_create_json_name}"
	else
		echo "[ERROR] Please confirm error in ${curDir}/${data_issue_create_json_name}"
	fi

	"$JAVA_HOME/bin/java" -cp "$JAVA_CPS" com.navercorp.cubridqa.common.MergeTemplate -t ${CTP_HOME}/conf/issue_comment.tpl -d issue_comment.data -o ${data_issue_comment_json_name}
	if [ $? -eq 0 ];then
                echo "ADD_ISSUE_COMMENT=${curDir}/${data_issue_comment_json_name}"
        else
                echo "[ERROR] Please confirm error in ${curDir}/${data_issue_comment_json_name}"
        fi

        rm -f *.out *.data core_full_stack.txt core_full_stack.info		
	cd $curDir
}

function analyze_failure_pkg()
{
	curDir=`pwd`
	if [ ! -f "$pkg_file_name" ];then
		cp -f $pkg_file .
	fi
	
	if [ -d "$pkg_file_name_without_ext" ];then
		cd $pkg_file_name_without_ext

	        if [ -f "$data_issue_create_json_name" ] || [ -f "$data_issue_comment_json_name" ];then
          	        echo ""
                	echo "[Info]: Target json file exists - (${curDir}/${pkg_file_name_without_ext}/$data_issue_create_json_name, ${curDir}/${pkg_file_name_without_ext}/$data_issue_comment_json_name)"
                	exit 1
        	fi
	else
		tar zvxf $pkg_file_name
		cd $pkg_file_name_without_ext
	fi	

	if [ "$core_file_name" ];then
		coreFilePath=`find ./ -name "$core_file_name"`
		if [ ! -f "$coreFilePath" ];then
			echo ""
			echo "Core file ${core_file_name} does not exist!"
			return
		fi

		export_CUBRID_environment
		gen_data_template $coreFilePath	
	else
		#TODO for fatal error report
		echo ""
	fi

	cd $curDir   
}

analyze_failure_pkg
