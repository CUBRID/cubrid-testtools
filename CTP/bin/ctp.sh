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

# --- the cubrid.conf this run will read, stated once ------------------------
#
# The engine's parameters reach a case through $CUBRID/conf/cubrid.conf, and
# every step between the repository that ships that file and the process that
# reads it -- the build's install, the published artifact, an overlay mount,
# CTP's own `ini.sh -s common -u` at deploy -- is a place a value can be lost.
# Tracing those steps is not the same as seeing the file, and a run whose
# environment is the variable under test has to say what its environment was.
#
# Sections are printed with the values because a [@db] section overrides
# [common], so the last one above a value is what decides it.
_ctp_conf="$CUBRID/conf/cubrid.conf"
echo "[CONF] CUBRID=${CUBRID:-(unset)}"
if [ -r "$_ctp_conf" ]; then
	echo "[CONF] $_ctp_conf ($(wc -c < "$_ctp_conf" | tr -d ' ') bytes, mtime $(date -r "$_ctp_conf" -u '+%Y-%m-%dT%H:%M:%SZ' 2>/dev/null))"
	grep -nE '^[[:space:]]*(\[|db_volume_size|log_volume_size|db_page_size|log_page_size|data_buffer_size|log_buffer_size)[[:space:]]*' "$_ctp_conf" \
		| sed 's/^/[CONF]   /'
else
	echo "[CONF] $_ctp_conf is not readable"
fi

"$JAVA_HOME/bin/java" -cp "$JAVA_CPS" com.navercorp.cubridqa.ctp.CTP "$@" 2>&1 | tee ${file_output}
java_exit_code=${PIPESTATUS[0]}
cat ${file_output} | grep SCRIPTCONT > ${file_script} 
sh ${file_script} 
rm -rf ${file_output} ${file_script} >/dev/null 2>&1
exit $java_exit_code
