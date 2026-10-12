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
# Producer side of the QAHome failed-case rerun (CUBRIDQA-1631): sends the rerun requests QAHome
# writes to qaresu.verify_rerun_batch as messages on RERUN_<queue> (see RerunProducer).
#
# Usage: start_rerun_producer.sh              # loop in the background, PID in conf/rerun_producer_PID
#        start_rerun_producer.sh --once       # one pass in the foreground (cron); exit status 1 if it failed
#        start_rerun_producer.sh --dry-run --once
#        start_rerun_producer.sh --stop       # stop the background loop
# Reads conf/common.conf, conf/producer.conf and the optional conf/rerun.conf of this CTP.

export CTP_HOME=$(cd $(dirname $(readlink -f $0))/../..; pwd)
pidFile=${CTP_HOME}/conf/rerun_producer_PID

for arg in "$@"; do
    case "$arg" in
        --once|--dry-run|--stop) ;;
        *)
            echo "Usage: $(basename $0) [--once] [--dry-run] | --stop"
            exit 1
            ;;
    esac
done

# PID of the running background loop, if any
function runningPid() {
    local pid=$(cat "$pidFile" 2>/dev/null)
    if [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null && ps -o args= -p "$pid" | grep -q RerunProducer; then
        echo "$pid"
    fi
}

case " $* " in
    *" --stop "*)
        pid=$(runningPid)
        if [ -z "$pid" ]; then
            echo "RerunProducer is not running"
            rm -f "$pidFile"
            exit 0
        fi
        kill "$pid"
        for i in $(seq 30); do
            kill -0 "$pid" 2>/dev/null || break
            sleep 1
        done
        kill -9 "$pid" 2>/dev/null
        rm -f "$pidFile"
        echo "RerunProducer stopped (PID $pid)"
        ;;
    *" --once "*|*" --dry-run "*)
        cd ${CTP_HOME}/common/sched && "$JAVA_HOME/bin/java" -cp "./lib/cubridqa-scheduler.jar" com.navercorp.cubridqa.scheduler.producer.RerunProducer "$@"
        exit $?
        ;;
    *)
        pid=$(runningPid)
        if [ -n "$pid" ]; then
            echo "RerunProducer is already running (PID $pid); stop it first with --stop"
            exit 1
        fi
        (
            cd ${CTP_HOME}/common/sched
            "$JAVA_HOME/bin/java" -cp "./lib/cubridqa-scheduler.jar" com.navercorp.cubridqa.scheduler.producer.RerunProducer "$@" &
            echo $! > "$pidFile"
            wait
            rm -f "$pidFile"
        )
        ;;
esac
