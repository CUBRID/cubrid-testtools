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
# --- what this machine gave the run ----------------------------------------
#
# A run's own log says what it used and never what it was allowed: gha-ci prints
# the cgroup's memory.peak when the suite finishes, and nothing prints the limit
# that peak is a fraction of, or the size of the machine underneath. So the one
# question an operator has -- can this host take another one of these -- cannot
# be answered from the logs it already keeps.
#
# All four are readable unprivileged, from inside the container. MemTotal is the
# node's, not the container's, which is the point: the limit is the share and
# MemTotal is what the shares are cut from.
_ctp_res() {
	local lim cur cpu d sub
	# In a container /sys/fs/cgroup is the container's own cgroup, so the files are
	# at the root; on a host they are one subtree down, where /proc/self/cgroup says.
	sub=$(awk -F: '$1 == "0" { print $3 }' /proc/self/cgroup 2>/dev/null)
	d=/sys/fs/cgroup
	[ -r "$d/memory.max" ] || [ -z "$sub" ] || d="/sys/fs/cgroup$sub"
	if [ -r "$d/memory.max" ]; then
		lim=$(cat "$d/memory.max" 2>/dev/null)
		cur=$(cat "$d/memory.current" 2>/dev/null)
		cpu=$(cat "$d/cpu.max" 2>/dev/null)
	elif [ -r /sys/fs/cgroup/memory/memory.limit_in_bytes ]; then
		lim=$(cat /sys/fs/cgroup/memory/memory.limit_in_bytes 2>/dev/null)
		cur=$(cat /sys/fs/cgroup/memory/memory.usage_in_bytes 2>/dev/null)
		cpu=$(cat /sys/fs/cgroup/cpu/cpu.cfs_quota_us 2>/dev/null)/$(cat /sys/fs/cgroup/cpu/cpu.cfs_period_us 2>/dev/null)
	fi
	echo "[RES] cgroup memory.max=${lim:-?} current=${cur:-?} cpu=${cpu:-?} nproc=$(nproc 2>/dev/null)"
	awk '/^MemTotal:|^MemAvailable:/ {printf "[RES] node %s %.1f GiB\n", $1, $2/1048576}' /proc/meminfo 2>/dev/null
}
_ctp_res

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

# --- what the run cost, per case, and what it left behind -------------------
#
# feedback.log is the only per-case duration a run produces, and it does not
# leave the node: collect reads it to update the split's timing file and drops
# the per-run copy, so afterwards two runs can be compared by their medians and
# by nothing finer. Printed here it is in the job log, which survives, and a
# diff of two runs then says which cases moved rather than which median did.
_ctp_fb="$CTP_HOME/result/shell/current_runtime_logs/feedback.log"
if [ -r "$_ctp_fb" ]; then
	_ctp_dur=/tmp/.ctp_dur_$$
	# Same scan as collect: the number before EnvId= is the case's milliseconds.
	awk '/^\[(OK|NOK)\]/ {
		ms = ""; p = ""
		for (i = NF; i > 1; i--) if ($i ~ /^EnvId=/) { ms = $(i-1); p = $(i-2); break }
		if (ms ~ /^[0-9]+$/ && p != "") printf "%.1f\t%s\n", ms / 1000, p
	}' "$_ctp_fb" > "$_ctp_dur"
	if [ -s "$_ctp_dur" ]; then
		echo "[SLOW] $(wc -l < "$_ctp_dur" | tr -d ' ') cases, $(awk -F'\t' '{s+=$1} END {printf "%.0f", s}' "$_ctp_dur")s inside CTP"
		sort -rn "$_ctp_dur" | awk -F'\t' '{printf "[DUR] %8.1f  %s\n", $1, $2}'
	else
		echo "[SLOW] feedback.log carried no durations"
	fi
	rm -f "$_ctp_dur"
fi

# A failure snapshot is the whole $CUBRID install plus the case directory,
# tarred, and it is written inside the measurement -- the wall clock carries it
# and nothing in the run's output says so. It is taken only for a core file or a
# new FATAL ERROR, so a run of answer mismatches should take none. That is a
# prediction; this is the line that checks it, and it also says which filesystem
# would have paid, since a tmpfs is charged to the pod's memory.
_ctp_bk="${CTP_ERROR_BACKUP_DIR:-$HOME/ERROR_BACKUP}"
if [ -d "$_ctp_bk" ]; then
	echo "[BACKUP] $_ctp_bk: $(find "$_ctp_bk" -mindepth 1 -maxdepth 1 2>/dev/null | wc -l | tr -d ' ') entries, $(du -sm "$_ctp_bk" 2>/dev/null | cut -f1)MB, fs $(stat -f -c %T "$_ctp_bk" 2>/dev/null)"
else
	echo "[BACKUP] $_ctp_bk: none taken, fs $(stat -f -c %T "$(dirname "$_ctp_bk")" 2>/dev/null)"
fi

exit $java_exit_code
