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

export CTP_HOME=$(cd $(dirname $(readlink -f $0))/..; pwd)

JAVA_CPS=$CTP_HOME/common/lib/cubridqa-common.jar

if [ "$OSTYPE" == "cygwin" ]
then
	JAVA_CPS=`cygpath -wp $JAVA_CPS`
fi
key=`date '+%Y%m%d%H%M%s'`
file_output=${CTP_HOME}/.output_${key}.log
file_script=${CTP_HOME}/.script_cont_${key}.sh
[ ! "${JAVA_HOME}" ] && echo "Please confirm JAVA_HOME is configured!" && exit 1

debug_opts=""
if [ ! "${CTP_DEBUG_ENABLE}" = "" -a `echo "Y,YES,TRUE,T,1" | grep -i "${CTP_DEBUG_ENABLE}" | wc -l` -gt 0 ] ; then
    debug_opts="-server -Xdebug -Xnoagent -Djava.compiler=NONE -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=8000"
fi

"$JAVA_HOME/bin/java" ${debug_opts} -cp "$JAVA_CPS" com.navercorp.cubridqa.ctp.CTP "$@" 2>&1 | tee ${file_output}
cat ${file_output} | grep SCRIPTCONT > ${file_script} 
sh ${file_script} 
rm -rf ${file_output} ${file_script} >/dev/null 2>&1
