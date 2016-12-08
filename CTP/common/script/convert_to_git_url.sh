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

function usage()
{
	cat <<CCTTPP
usage: sh convert_to_git_url.sh <the local path of case>

CCTTPP

}

[ $# -eq 0 ] && usage && exit 1


function convert_to_git_url(){
        case_path=$1
	case_dir=""
	sub_result_path=""
        case_file_suffix=""

        if [ -f "$case_path" ];then
		case_dir="${case_path%/*}"
		case_file_suffix="${case_path##*/}"
		if [ -z "$case_dir" ] || [ "$case_dir" == "$case_file_suffix" ];then
			case_dir="."
		fi
		
        elif [ -d "$case_path" ];then
		case_dir="$case_path"
        else
		echo "please confirm your case path is correct! - $case_path"
                return
	fi

        cd $case_dir
        root_git_url=`git remote -v | grep -Ew "^origin"|awk '{print $2}'|head -n 1|tr -d '[[:space:]]'`
	if [ -z "$root_git_url" ];then
		echo "please confirm if your case path is a git path - $case_path"
		return 
	fi
	
        branch_name=`git rev-parse --abbrev-ref HEAD`
        repo_root_url_prefix=`echo ${root_git_url}|sed 's/\.git//g'`

        while [ ! -d .git ] && [ ! "`pwd`" = "/" ]
      	do 
         	curr_name=$(basename `pwd`)
          	sub_result_path=${curr_name}/${sub_result_path}
          	cd ..
     	done

        full_case_temp_git_path="${repo_root_url_prefix}/blob/${branch_name}/${sub_result_path}${case_file_suffix}"

        echo $full_case_temp_git_path
}

convert_to_git_url $1

