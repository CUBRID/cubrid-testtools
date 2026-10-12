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
# Failed-case rerun runner (CUBRIDQA-1632).
#
# start_consumer.sh calls this for a message taken from RERUN_<queue>. The message is a clone of
# the original run's message plus MKEY_RERUN_BATCH_ID / MKEY_RERUN_TOKEN / MKEY_RERUN_FEEDBACK_URL /
# MKEY_RERUN_LOG_DIR. This script asks QAHome (rerun.nhn) for the cases, runs only those with the
# family runner in common/ext/rerun/rerun_<family>.sh, uploads the logs, and reports one verdict
# per case. It never uses the normal result paths: no DB feedback, no QAHome import, no upload to
# qa_repository/function - a normal import would hide the original run's fail list.
#
# Usage: run_rerun.sh <exec of the regular queue, e.g. run_shell>

regular_exec=$1

RERUN_BATCH_ID=$MKEY_RERUN_BATCH_ID
RERUN_FEEDBACK_URL=$MKEY_RERUN_FEEDBACK_URL
RERUN_LOG_DIR=${MKEY_RERUN_LOG_DIR:-./qaresult_en/web/test_error/rerun}
# Keep the batch token out of the environment: harnesses paste MKEY_* values into scripts they ship
# to worker nodes, and the consumer runs with 'set -x'.
RERUN_TOKEN=$MKEY_RERUN_TOKEN
unset MKEY_RERUN_TOKEN

export RERUN_WORK=${CTP_HOME}/result/rerun/batch_${RERUN_BATCH_ID}
export RERUN_LOG_ROOT=${RERUN_WORK}/logs
export RERUN_CASES_FILE=${RERUN_WORK}/cases.tsv
export RERUN_RESULTS_FILE=${RERUN_WORK}/results.tsv
export RERUN_TC_COMMIT_FILE=${RERUN_WORK}/tc_commit
RERUN_FINISHED=0

# rerun_result <rerunId> <PASS|FAIL|ERROR> <log file relative to RERUN_LOG_ROOT, may be empty> <message>
function rerun_result() {
    printf '%s\t%s\t%s\t%s\n' "$1" "$2" "$3" "$(echo "$4" | tr '\t\n' '  ')" >> "$RERUN_RESULTS_FILE"
}
export -f rerun_result

# rerun_call <method> [curl --data-urlencode args...]; tracing is off so the token never reaches a log.
function rerun_call() {
    local method=$1
    shift
    { set +x; } 2>/dev/null
    curl -s -S --max-time 120 --retry 3 -X POST "$RERUN_FEEDBACK_URL" \
        --data-urlencode "m=$method" --data-urlencode "batchId=$RERUN_BATCH_ID" \
        --data-urlencode "token=$RERUN_TOKEN" "$@"
}

function rerun_finish() {
    if [ "$RERUN_FINISHED" -eq 1 ]; then
        return
    fi
    RERUN_FINISHED=1
    echo "[RERUN] finish batch ${RERUN_BATCH_ID}: $(rerun_call rerunFinish --data-urlencode "message=$1")"
}

# category -> family runner (common/ext/rerun/rerun_<family>.sh)
function rerun_family_of() {
    case "$1" in
        sql|sql_debug|medium|medium_debug|sql_ext) echo sql ;;
        sql_by_cci) echo sql_by_cci ;;
        shell|shell_debug|shell_heavy|shell_long|ha_shell|cci|cci_debug) echo shell ;;
        jdbc) echo jdbc ;;
        isolation|isolation_debug) echo isolation ;;
        ha_repl|ha_repl_debug) echo ha_repl ;;
        cdc_repl) echo cdc_repl ;;
        *) echo "" ;;
    esac
}

if [ -z "$RERUN_BATCH_ID" -o -z "$RERUN_TOKEN" -o -z "$RERUN_FEEDBACK_URL" ]; then
    echo "[RERUN] The message has no MKEY_RERUN_BATCH_ID/TOKEN/FEEDBACK_URL; nothing to rerun."
    exit 0
fi
trap 'rerun_finish "rerun runner on ${USER}@$(hostname) exited before reporting every case"' EXIT

rm -rf "$RERUN_WORK"
mkdir -p "$RERUN_LOG_ROOT"
: > "$RERUN_RESULTS_FILE"

rerun_call rerunCases --data-urlencode "node=${USER}@$(hostname)" > "$RERUN_WORK/cases.out" 2>&1
first_line=$(head -1 "$RERUN_WORK/cases.out")
case "$first_line" in
    OK*) ;;
    *)
        echo "[RERUN] QAHome did not hand out batch ${RERUN_BATCH_ID}: ${first_line}"
        RERUN_FINISHED=1
        exit 0
        ;;
esac
tail -n +2 "$RERUN_WORK/cases.out" | awk -F'\t' '$1 ~ /^[0-9]+$/ && NF >= 2' > "$RERUN_CASES_FILE"
if [ ! -s "$RERUN_CASES_FILE" ]; then
    rerun_finish "no cases to rerun"
    exit 0
fi
echo "[RERUN] batch ${RERUN_BATCH_ID}: $(wc -l < "$RERUN_CASES_FILE") case(s) of ${BUILD_SCENARIOS} on build ${BUILD_ID} (queue exec ${regular_exec})"

family=$(rerun_family_of "$BUILD_SCENARIOS")
runner=${CTP_HOME}/common/ext/rerun/rerun_${family}.sh
if [ -n "$family" -a -f "$runner" ]; then
    # A subshell, so a runner that exits early cannot skip the reporting below.
    (
        cd ${CTP_HOME}
        if [ -f ${CTP_HOME}/common/ext/rerun/rerun_common.sh ]; then
            source ${CTP_HOME}/common/ext/rerun/rerun_common.sh
        fi
        source "$runner"
        rerun_${family}
    ) 2>&1 | tee "$RERUN_LOG_ROOT/runner.log"
else
    while IFS=$'\t' read -r rerun_id sce_id; do
        rerun_result "$rerun_id" ERROR "" "rerun is not supported for category ${BUILD_SCENARIOS}"
    done < "$RERUN_CASES_FILE"
fi

# Every requested case gets a verdict: the first one a runner wrote wins, a missing one is ERROR.
while IFS=$'\t' read -r rerun_id sce_id; do
    if ! awk -F'\t' -v id="$rerun_id" '$1 == id { found = 1 } END { exit found ? 0 : 1 }' "$RERUN_RESULTS_FILE"; then
        rerun_result "$rerun_id" ERROR "" "the ${family:-unknown} runner gave no verdict for this case"
    fi
done < "$RERUN_CASES_FILE"

if [ -n "$(ls -A "$RERUN_LOG_ROOT" 2>/dev/null)" ]; then
    upload_to_dailysrv "$RERUN_LOG_ROOT" "${RERUN_LOG_DIR}/${RERUN_BATCH_ID}" || echo "[RERUN] log upload failed"
fi

tc_commit=$(cat "$RERUN_TC_COMMIT_FILE" 2>/dev/null)
reported=""
# Split with cut, not 'IFS=<tab> read': tab is IFS whitespace, so read would collapse an empty log field.
while IFS= read -r line; do
    rerun_id=$(printf '%s' "$line" | cut -f1)
    result=$(printf '%s' "$line" | cut -f2)
    log_file=$(printf '%s' "$line" | cut -f3)
    message=$(printf '%s' "$line" | cut -f4-)
    if [ -z "$rerun_id" ]; then
        continue
    fi
    case " $reported " in *" $rerun_id "*) continue ;; esac
    reported="$reported $rerun_id"
    answer=$(rerun_call rerunReport --data-urlencode "rerunId=$rerun_id" --data-urlencode "result=$result" \
        --data-urlencode "logFile=$log_file" --data-urlencode "message=$message" --data-urlencode "tcCommit=$tc_commit")
    echo "[RERUN] case ${rerun_id}: ${result} -> ${answer}"
done < "$RERUN_RESULTS_FILE"

rerun_finish ""
