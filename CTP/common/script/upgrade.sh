#!/bin/bash

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
export SKIP_UPGRADE=${skipUpgrade}

rm -rf ../.ctp/* >/dev/null 2>&1
mkdir -p ../.ctp

cp -rf common/lib/* ../.ctp/

export `grep -E "^grepo_service_url" conf/common.conf | tr -d '\r'`
"$JAVA_HOME/bin/java" -cp ../.ctp/cubridqa-common.jar com.navercorp.cubridqa.common.grepo.UpgradeMain -r cubrid-testtools -b "$branchName" -p "CTP" -e "conf" .; (chmod u+x ./bin/*; chmod u+x ./common/script/*; chmod u+x ./common/ext/*; chmod u+x sql/bin/*; rm -rf ../.ctp >/dev/null 3>&1); echo DONE; cd "${current_user_dir}"; exit
