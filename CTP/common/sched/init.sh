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

analyzeMessageInfo $1
export -f getMsgValue
export -f runAction
