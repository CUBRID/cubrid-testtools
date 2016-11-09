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

SVR_SITE="china"
result="${CTP_HOME}/common/sched/result/consumerMsg.info"
statusFile="${CTP_HOME}/common/sched/status/STATUS.TXT"

function analyzeMessageInfo() {
	SVR_SITE=$1
	`cat $result |grep ":" |grep -v "Max Version of" | sed "s/:/=/" | sed "s/^/export /"`
	 if [ "$BUILD_SVN_BRANCH_NEW" ]
	 then 
		BUILD_SVN_BRANCH=$BUILD_SVN_BRANCH_NEW
		export BUILD_SVN_BRANCH
	 fi
 
    if [ "$SVR_SITE" == 'china' ]
    then
        url=$BUILD_URLS
	    com_url=$COMPAT_BUILD_URLS
	    com_img_url=$DBIMG_BUILD_URLS
	    src_url=$BUILD_URLS_1
    else
	    if [ "$BUILD_TYPE" == "coverage" ];then
	   	    url=$BUILD_URLS_KR_REPO1
		    src_url=$BUILD_URLS_KR_REPO1_1	
	    else
           	url=$BUILD_URLS_KR
           	com_url=$COMPAT_BUILD_URLS_KR
           	com_img_url=$DBIMG_BUILD_URLS_KR
	    	src_url=$BUILD_URLS_KR_1
	    fi
    fi

    export url com_url com_img_url src_url
    fn=${url##*/}
    fexe=${fn##*.}
    if [ "$fexe" == 'zip' ]
    then
        isWin='yes'
    fi
	
	if [ -f $statusFile ]		
 	then		
 		echo "TEST_TYPE:$BUILD_SCENARIOS" >> $statusFile		
 		echo "TEST_BUILD:$BUILD_ID" >> $statusFile		
 	fi 
}

function getMsgValue() {
    echo $1
}

function runAction() {
    if [ $# -eq 0 ]
    then
        echo "Usage: runAction [config file name]"
        echo "eg: runAction test1.act test2.act"
        exit 1      
    else
        (cd ${CTP_HOME}/conf/;run_action_files $@)
    fi
}

function upload_to_dailysrv () {
    from=$1
    to=$2
    run_upload -host "$DAILYQA_DAILYSRV_HOST" -user "$DAILYQA_DAILYSRV_USER" -password "$DAILYQA_DAILYSRV_PWD" -port "$DAILYQA_DAILYSRV_PORT" -from "$from" -to "$to"
}

function check_local_disk_space () {
    (source ${CTP_HOME}/common/script/util_common.sh
     cc=`ini.sh ${CTP_HOME}/conf/common.conf mail_from_address`
     check_disk_space `df -P $HOME | grep -v Filesystem | awk '{print $1}'` 2G "$1" "$cc"
    )
}

export DAILYQA_DAILYSRV_HOST=`ini.sh conf/common.conf qahome_server_host`
export DAILYQA_DAILYSRV_USER=`ini.sh conf/common.conf qahome_server_user`
export DAILYQA_DAILYSRV_PWD=`ini.sh conf/common.conf qahome_server_pwd`
export DAILYQA_DAILYSRV_PORT=`ini.sh conf/common.conf qahome_server_port`
export DAILYQA_GIT_USER=`ini.sh conf/common.conf git_user`
export DAILYQA_GIT_PWD=`ini.sh conf/common.conf git_pwd`
export DAILYQA_GIT_EMAIL=`ini.sh conf/common.conf git_email`
export DAILYQA_SSH_PWD_DEFAULT=`ini.sh conf/common.conf default_ssh_pwd`
export DAILYQA_SSH_PORT_DEFAULT=`ini.sh conf/common.conf default_ssh_port`

analyzeMessageInfo $1
export -f getMsgValue
export -f runAction
export -f upload_to_dailysrv
export -f check_local_disk_space