#!/bin/sh
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

current_user_dir="`pwd`"
dest_tool_dir="$(cd $(dirname $(readlink -f $0))/../..; pwd)"

cd ${dest_tool_dir}

branchName=master
skipUpgrade=1
if [ "${CTP_BRANCH_NAME}" ];then
   branchName=${CTP_BRANCH_NAME}
fi
if [ "${CTP_SKIP_UPDATE}" ]; then
   skipUpgrade=${CTP_SKIP_UPDATE}
fi
export CTP_SKIP_UPDATE=${skipUpgrade}

rm -rf ../.ctp/* >/dev/null 2>&1
mkdir -p ../.ctp

cp -rf common/lib/* ../.ctp/

export `grep -E "^grepo_service_url" conf/common.conf | tr -d '\r'`
"$JAVA_HOME/bin/java" -cp ../.ctp/cubridqa-common.jar com.navercorp.cubridqa.common.grepo.UpgradeMain -r cubrid-testtools -b "$branchName" -p "CTP" -e "conf" . | tee ./.upgrade.log
if grep -q "fetch done: CHANGED" ./.upgrade.log ; then 
	echo "[INFO] begin to build CTP ..."
	ant dist
	echo "[INFO] CTP build done"
fi
(chmod u+x ./bin/*; chmod u+x ./common/script/*; chmod u+x ./common/ext/*; chmod u+x sql/bin/*; rm -rf ../.ctp >/dev/null 3>&1); echo DONE; cd "${current_user_dir}"; exit
