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
# to worker nodes, and the consumer runs with 'set -x'. It never goes on a command line either (rerun_call).
RERUN_TOKEN=$MKEY_RERUN_TOKEN
unset MKEY_RERUN_TOKEN

if [ -z "$RERUN_BATCH_ID" -o -z "$RERUN_TOKEN" -o -z "$RERUN_FEEDBACK_URL" ]; then
    echo "[RERUN] The message has no MKEY_RERUN_BATCH_ID/TOKEN/FEEDBACK_URL; nothing to rerun."
    exit 0
fi
# Both end up in paths (rm -rf here, the upload destination on the QAHome host): accept plain values only.
if ! [[ "$RERUN_BATCH_ID" =~ ^[0-9]+$ ]] || ! [[ "$RERUN_LOG_DIR" =~ ^[A-Za-z0-9._/-]+$ ]] || [[ "$RERUN_LOG_DIR" == *..* ]] ||
    ! [[ "$RERUN_FEEDBACK_URL" =~ ^https?://[A-Za-z0-9._:/-]+$ ]]; then
    echo "[RERUN] The message has an invalid MKEY_RERUN_BATCH_ID, MKEY_RERUN_LOG_DIR or MKEY_RERUN_FEEDBACK_URL; nothing to rerun."
    exit 0
fi

export RERUN_WORK=${CTP_HOME}/result/rerun/batch_${RERUN_BATCH_ID}
export RERUN_LOG_ROOT=${RERUN_WORK}/logs
export RERUN_CASES_FILE=${RERUN_WORK}/cases.tsv
export RERUN_RESULTS_FILE=${RERUN_WORK}/results.tsv
export RERUN_TC_COMMIT_FILE=${RERUN_WORK}/tc_commit
# Process groups of the steps the runner runs under timeout(1) (rerun_timeout); a cancel stops them too.
export RERUN_PGID_FILE=${RERUN_WORK}/pgids
RERUN_FINISHED=0
runner_pid=""
family=""
# How often a running rerun asks QAHome whether it was cancelled (rerun.nhn?m=rerunPoll).
RERUN_POLL_SECONDS=${RERUN_POLL_SECONDS:-60}
# Batches whose work dir (case list, verdicts, scrubbed logs) is kept on this node.
RERUN_KEEP_BATCHES=${RERUN_KEEP_BATCHES:-20}

# rerun_result <rerunId> <PASS|FAIL|ERROR> <log file relative to RERUN_LOG_ROOT, may be empty> <message>
function rerun_result() {
    printf '%s\t%s\t%s\t%s\n' "$1" "$2" "$3" "$(echo "$4" | tr '\t\n' '  ')" >> "$RERUN_RESULTS_FILE"
}
export -f rerun_result

# rerun_call <method> [curl --data-urlencode args...]: the QAHome answer on stdout, status 1 when QAHome could
# not be reached. Transport failures (no connection, timeout, HTTP error, empty or unexpected answer) are
# retried with a backoff, so a QAHome restart does not lose a rerun. The token goes on stdin, not on the
# command line where ps would show it; tracing is off.
function rerun_call() {
    local method=$1 answer attempt delay=10
    shift
    { set +x; } 2>/dev/null
    for attempt in 1 2 3 4 5 6; do
        answer=$(printf '%s' "$RERUN_TOKEN" | curl -s -S -f --connect-timeout 20 --max-time 120 -X POST "$RERUN_FEEDBACK_URL" \
            --data-urlencode "m=$method" --data-urlencode "batchId=$RERUN_BATCH_ID" --data-urlencode "token@-" "$@" 2>&1)
        case "$answer" in
            OK*|ERROR*|CANCEL*)
                printf '%s\n' "$answer"
                return 0
                ;;
        esac
        echo "[RERUN] $method: QAHome did not answer (attempt $attempt): $(echo "$answer" | head -1 | cut -c1-200)" >&2
        [ $attempt -lt 6 ] && sleep $delay
        delay=$((delay * 2))
    done
    return 1
}

# Process groups of the runner and everything under it, collected while the tree is still whole: timeout(1)
# and the harnesses start new groups (also nested ones rerun_timeout cannot record).
function rerun_tree_groups() {
    local own=$(ps -o pgid= -p $$ | tr -d ' ')
    ps -u "$USER" -o pid=,ppid=,pgid= | awk -v root="$1" -v own="$own" '
        { ppid[$1] = $2; pgid[$1] = $3; pids[NR] = $1 }
        END {
            tree[root] = 1; more = 1
            while (more) { more = 0; for (i = 1; i <= NR; i++) { p = pids[i]; if (!(p in tree) && (ppid[p] in tree)) { tree[p] = 1; more = 1 } } }
            for (p in tree) if (pgid[p] != "" && pgid[p] != own && !(pgid[p] in seen)) { seen[pgid[p]] = 1; print pgid[p] }
        }'
}

# Stop the runner and every process group under it: TERM, then KILL after 30 s.
function rerun_stop_runner() {
    local groups g i alive
    [ -n "$runner_pid" ] || return 0
    groups="$(rerun_tree_groups $runner_pid) $(cat "$RERUN_PGID_FILE" 2>/dev/null) $runner_pid"
    for g in $groups; do
        kill -TERM -- -$g 2>/dev/null
    done
    for i in $(seq 30); do
        alive=0
        for g in $groups; do
            kill -0 -- -$g 2>/dev/null && alive=1
        done
        [ $alive -eq 0 ] && break
        sleep 1
    done
    for g in $groups $(cat "$RERUN_PGID_FILE" 2>/dev/null); do
        kill -KILL -- -$g 2>/dev/null
    done
    wait $runner_pid 2>/dev/null
    runner_pid=""
}

function rerun_finish() {
    if [ "$RERUN_FINISHED" -eq 1 ]; then
        return
    fi
    RERUN_FINISHED=1
    echo "[RERUN] finish batch ${RERUN_BATCH_ID}: $(rerun_call rerunFinish --data-urlencode "message=$1" || echo "QAHome unreachable")"
}

# Publish the runner's console output with the secrets the runner collected masked.
function rerun_publish_runner_log() {
    local secrets=${RERUN_WORK}/${family}/.secrets
    [ -f "$RERUN_WORK/runner.raw.log" ] || return 0
    if [ -n "$family" -a -f "$secrets" ] && type rerun_scrub_stream >/dev/null 2>&1; then
        RERUN_SECRETS=$secrets rerun_scrub_stream "" < "$RERUN_WORK/runner.raw.log" > "$RERUN_LOG_ROOT/runner.log"
    else
        # nothing to scrub with: publish only the [RERUN] progress lines
        grep '^\[RERUN' "$RERUN_WORK/runner.raw.log" > "$RERUN_LOG_ROOT/runner.log"
    fi
}

# The family work dir holds raw harness logs, conf copies with passwords and the secrets list: drop it, and
# keep only the newest RERUN_KEEP_BATCHES batch dirs.
function rerun_tidy() {
    [ -n "$family" ] && rm -rf "${RERUN_WORK:?}/${family}"
    rm -f "$RERUN_WORK/runner.raw.log" "$RERUN_PGID_FILE"
    ls -dt "${CTP_HOME}"/result/rerun/batch_* 2>/dev/null | tail -n +$((RERUN_KEEP_BATCHES + 1)) | while read -r old; do
        rm -rf "$old"
    done
}

function rerun_main_exit() {
    if [ -n "$runner_pid" ]; then
        # killed before the runner ended (TERM/INT/HUP, or an error): stop the harness before closing the batch
        rerun_stop_runner
    fi
    rerun_finish "rerun runner on ${USER}@$(hostname) exited before reporting every case"
    rerun_tidy
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

trap 'rerun_main_exit' EXIT
trap 'exit 143' TERM INT HUP

rm -rf "$RERUN_WORK"
mkdir -p "$RERUN_LOG_ROOT"
: > "$RERUN_RESULTS_FILE"

if ! rerun_call rerunCases --data-urlencode "node=${USER}@$(hostname)" > "$RERUN_WORK/cases.out"; then
    # The message is off the queue, but QAHome is unreachable: the batch times out in QAHome.
    echo "[RERUN] QAHome is unreachable; batch ${RERUN_BATCH_ID} is not run"
    RERUN_FINISHED=1
    exit 0
fi
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
    # for rerun_scrub_stream (runner.log) and the shared helpers
    source ${CTP_HOME}/common/ext/rerun/rerun_common.sh
    # A subshell, so a runner that exits early cannot skip the reporting below; in its own process group
    # (set -m), so a cancel from QAHome can stop the whole harness under it.
    set -m
    (
        # Back to no job control inside, so background jobs a harness starts stay in this process group.
        set +m
        cd ${CTP_HOME}
        source "$runner"
        rerun_${family}
    ) > "$RERUN_WORK/runner.raw.log" 2>&1 &
    runner_pid=$!
    set +m
    waited=0
    while kill -0 $runner_pid 2>/dev/null; do
        sleep 5
        waited=$((waited + 5))
        if [ $((waited % RERUN_POLL_SECONDS)) -ne 0 ]; then
            continue
        fi
        poll=$(rerun_call rerunPoll | head -1)
        case "$poll" in
            CANCEL*)
                echo "[RERUN] batch ${RERUN_BATCH_ID}: ${poll}; stopping the harness"
                rerun_stop_runner
                rerun_publish_runner_log
                cat "$RERUN_LOG_ROOT/runner.log"
                # The cancel (or timeout) already closed the batch and its cases in QAHome: nothing to report.
                RERUN_FINISHED=1
                exit 0
                ;;
        esac
    done
    wait $runner_pid 2>/dev/null
    runner_pid=""
    rerun_publish_runner_log
    cat "$RERUN_LOG_ROOT/runner.log"
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

logs_uploaded=1
if [ -n "$(ls -A "$RERUN_LOG_ROOT" 2>/dev/null)" ]; then
    # under a timeout: the upload tool retries for minutes and has no ssh timeout of its own
    upload_out=$(timeout -k 30 1800 bash -c 'upload_to_dailysrv "$1" "$2"' rerun_upload "$RERUN_LOG_ROOT" "${RERUN_LOG_DIR}/${RERUN_BATCH_ID}" 2>&1)
    if ! echo "$upload_out" | grep -q '\[INFO\] upload done'; then
        logs_uploaded=0
        echo "[RERUN] log upload failed: $(echo "$upload_out" | grep -m1 -iE 'error|fail|exception' | cut -c1-200)"
    fi
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
    # without the upload the link would point at nothing
    [ $logs_uploaded -eq 1 ] || log_file=""
    if ! answer=$(rerun_call rerunReport --data-urlencode "rerunId=$rerun_id" --data-urlencode "result=$result" \
        --data-urlencode "logFile=$log_file" --data-urlencode "message=$message" --data-urlencode "tcCommit=$tc_commit"); then
        # QAHome is unreachable: the remaining verdicts stay in results.tsv; QAHome times the batch out.
        echo "[RERUN] QAHome is unreachable; stopped reporting at case ${rerun_id} (verdicts kept in ${RERUN_RESULTS_FILE})"
        RERUN_FINISHED=1
        exit 0
    fi
    echo "[RERUN] case ${rerun_id}: ${result} -> ${answer}"
done < "$RERUN_RESULTS_FILE"

rerun_finish ""
