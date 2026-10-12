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
#        start_rerun_producer.sh --once       # one pass in the foreground (cron)
#        start_rerun_producer.sh --dry-run --once
# Reads conf/common.conf, conf/producer.conf and the optional conf/rerun.conf of this CTP.

export CTP_HOME=$(cd $(dirname $(readlink -f $0))/../..; pwd)

case " $* " in
    *" --once "*|*" --dry-run "*)
        (cd ${CTP_HOME}/common/sched; "$JAVA_HOME/bin/java" -cp "./lib/cubridqa-scheduler.jar" com.navercorp.cubridqa.scheduler.producer.RerunProducer "$@")
        ;;
    *)
        (
            cd ${CTP_HOME}/common/sched
            "$JAVA_HOME/bin/java" -cp "./lib/cubridqa-scheduler.jar" com.navercorp.cubridqa.scheduler.producer.RerunProducer "$@" &
            echo $! > ${CTP_HOME}/conf/rerun_producer_PID
            wait
        )
        ;;
esac
