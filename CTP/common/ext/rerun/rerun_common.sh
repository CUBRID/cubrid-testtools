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
# Shared helpers of the failed-case rerun runners (CUBRIDQA-1632).
#
# run_rerun.sh sources this file and then common/ext/rerun/rerun_<family>.sh in a subshell whose CWD
# is ${CTP_HOME}, and calls rerun_<family> without argument. The contract (see run_rerun.sh):
#   in : RERUN_CASES_FILE (lines rerunId<TAB>sceId), RERUN_WORK (scratch dir of the batch), RERUN_LOG_ROOT
#        (its contents are uploaded and linked from QAHome), the cloned daily message (BUILD_*, MKEY_*,
#        url, src_url) and the functions exported by common/sched/init.sh;
#   out: exactly one rerun_result per case and the testcase commit in RERUN_TC_COMMIT_FILE.
#
# What every runner keeps to:
#   - install the same build the regular run_<family>.sh installs, but never call run_<family>.sh and never
#     write conf/*_runtime.conf (the daily continue mode reuses them): rerun confs live in RERUN_WORK;
#   - update the testcase checkout of BUILD_SCENARIO_BRANCH_GIT under a timeout with run_git_update, which takes
#     the testcases the original run took: a develop build's are pinned to its engine commit time
#     (BUILD_COMMIT_TIME, CUBRIDQA-1667), other builds take the branch head. A pull request rerun
#     (MKEY_RERUN_TC_REF) then checks out the pull request's head instead. Verify the branch before trusting
#     the commit; run_git_update gives up after 3 failed fetches or checkouts with an "ERROR: <reason>" line;
#   - no DB feedback, no QAHome notice/import, no upload to qa_repository or any other normal result path,
#     no in-harness retry, no memory-leak mode;
#   - verdicts come from the harness result files, never from the ctp.sh exit code;
#   - nothing that may hold a password reaches RERUN_LOG_ROOT: ctp.sh prints the whole configuration file
#     (ssh and feedback DB passwords included) and run_remote_script takes the ssh password as argument,
#     so every harness/tool output goes to RERUN_WORK first and only a scrubbed copy (rerun_save_log) is
#     published; configuration files, main_snapshot.properties and *_result_*.tar.gz are never copied.
#
# Tunables (consumer environment, all optional): RERUN_GIT_TIMEOUT (1800 s), RERUN_INSTALL_TIMEOUT (3600 s),
# RERUN_REMOTE_TIMEOUT (600 s), RERUN_HARNESS_TIMEOUT (family default), RERUN_MAX_ENVS (4 test envs at most),
# RERUN_LOG_MAX_BYTES (20000000 bytes per copied log).
#
# Must stay valid for bash started as 'sh' (POSIX mode): no process substitution.

RERUN_GIT_TIMEOUT=${RERUN_GIT_TIMEOUT:-1800}
RERUN_INSTALL_TIMEOUT=${RERUN_INSTALL_TIMEOUT:-3600}
RERUN_REMOTE_TIMEOUT=${RERUN_REMOTE_TIMEOUT:-600}
RERUN_MAX_ENVS=${RERUN_MAX_ENVS:-4}
RERUN_LOG_MAX_BYTES=${RERUN_LOG_MAX_BYTES:-20000000}

# Directory used on test nodes (and on the consumer for local runs), relative to the node user's $HOME.
RERUN_NODE_DIR=ctp_rerun_work
RERUN_NODE_STAGE=ctp_rerun_stage
# Local branch a pull request rerun leaves its testcase checkout on (run_git_update moves it back next time).
RERUN_PR_BRANCH=rerun_tc_pr

#######################################################################################################
# Life cycle
#######################################################################################################

# rerun_init <family>: per-run variables, work dir, secret list and the cleanup trap.
function rerun_init() {
    { set +x; } 2>/dev/null
    if [ -z "$RERUN_WORK" -o -z "$1" ]; then
        # run_rerun.sh always sets RERUN_WORK; without it the rm -rf below would aim at /<family>.
        echo "[RERUN] rerun_init needs RERUN_WORK and a family; refusing to run"
        exit 1
    fi
    RERUN_FAMILY=$1
    RERUN_BRANCH=$BUILD_SCENARIO_BRANCH_GIT
    RERUN_LOCAL_BRANCH=${RERUN_BRANCH//\//_}
    # A pull request rerun runs the cases from the pull request's head: the build's testcase pin (and a failed
    # lookup of it) does not apply, and the checkout ends on RERUN_PR_BRANCH.
    RERUN_TC_REF=$MKEY_RERUN_TC_REF
    RERUN_TC_SHA=$MKEY_RERUN_TC_SHA
    if [ -n "$RERUN_TC_REF" ]; then
        unset BUILD_COMMIT_TIME
        RERUN_CHECKOUT_BRANCH=$RERUN_PR_BRANCH
    else
        RERUN_CHECKOUT_BRANCH=$RERUN_LOCAL_BRANCH
    fi
    RERUN_TC_COMMIT=""
    RERUN_TAG=$(basename "$RERUN_WORK")
    RERUN_FAM_WORK=$RERUN_WORK/$RERUN_FAMILY
    RERUN_SECRETS=$RERUN_FAM_WORK/.secrets
    RERUN_CLEANUP_CMDS=""
    RERUN_LAST_ERROR=""
    RERUN_HARNESS_NOTE=""
    rm -rf "$RERUN_FAM_WORK"
    mkdir -p "$RERUN_FAM_WORK" "$RERUN_LOG_ROOT/setup" "$RERUN_LOG_ROOT/harness"
    # run_rerun.sh scrubs runner.log with it after the runner ends, then removes the whole family work dir
    (umask 077; : > "$RERUN_SECRETS")
    trap 'rerun_cleanup' EXIT
    rerun_secrets_init
    rerun_log "category ${BUILD_SCENARIOS}, build ${BUILD_ID}, testcase branch ${RERUN_BRANCH} ($(rerun_tc_source)), $(rerun_case_count) case(s), runner ${RERUN_FAMILY}"
}

# rerun_tc_where: where the testcases come from, in "case not found at ..." messages.
function rerun_tc_where() {
    if [ -n "$RERUN_TC_REF" ]; then
        echo "pull request #$(echo "$RERUN_TC_REF" | cut -d/ -f3)"
    elif [[ "$BUILD_COMMIT_TIME" =~ ^[0-9]+$ ]]; then
        echo "${RERUN_BRANCH} as of the build"
    else
        echo "${RERUN_BRANCH} HEAD"
    fi
}

# rerun_tc_source: which testcases the rerun takes, for the logs.
function rerun_tc_source() {
    if [ -n "$RERUN_TC_REF" ]; then
        echo "pull request ${RERUN_TC_REF}"
    elif [[ "$BUILD_COMMIT_TIME" =~ ^[0-9]+$ ]]; then
        echo "as of the build's engine commit time $(TZ=Asia/Seoul date -d @"$BUILD_COMMIT_TIME" '+%F %T %Z')"
    else
        echo "branch head"
    fi
}

function rerun_log() {
    echo "[RERUN] $(date '+%Y-%m-%d %H:%M:%S') $*"
}

# rerun_on_exit <command string>: run it when the runner subshell exits (staging and node cleanup).
function rerun_on_exit() {
    RERUN_CLEANUP_CMDS="$1
$RERUN_CLEANUP_CMDS"
}

function rerun_cleanup() {
    local cmd
    { set +x; } 2>/dev/null
    while IFS= read -r cmd; do
        [ -n "$cmd" ] && eval "$cmd" >/dev/null 2>&1
    done <<EOF
$RERUN_CLEANUP_CMDS
EOF
    RERUN_CLEANUP_CMDS=""
}

#######################################################################################################
# Cases and verdicts
#######################################################################################################

function rerun_case_count() {
    awk 'END { print NR }' "$RERUN_CASES_FILE"
}

# rerun_reported <rerunId>: true when this runner already gave the case a verdict.
function rerun_reported() {
    awk -F'\t' -v id="$1" '$1 == id { f = 1 } END { exit f ? 0 : 1 }' "$RERUN_RESULTS_FILE"
}

# rerun_verdict <rerunId> <PASS|FAIL|ERROR> <log file relative to RERUN_LOG_ROOT or ""> <message>
# The single place that calls run_rerun.sh's rerun_result, so a case is never reported twice.
function rerun_verdict() {
    rerun_reported "$1" && return 0
    rerun_result "$1" "$2" "$3" "$4"
    rerun_log "case $1: $2${4:+ - $4}"
}

# rerun_error_all <message>: ERROR for every case that has no verdict yet.
function rerun_error_all() {
    local id sce
    while IFS=$'\t' read -r id sce <&3; do
        rerun_verdict "$id" ERROR "" "$1"
    done 3< "$RERUN_CASES_FILE"
}

# rerun_verdict_lookup <verdict file> <key>: the last "key<TAB>STATUS<TAB>note" line for the key, as "STATUS<TAB>note".
function rerun_verdict_lookup() {
    awk -F'\t' -v k="$2" '$1 == k { l = $2 "\t" $3 } END { if (l != "") print l }' "$1"
}

# rerun_report_cases <case map> <verdict file> <case log callback> <message when a case has no verdict>
# Case map lines: rerunId<TAB>sceId<TAB>key, written by the runner for the cases it handed to the harness.
# Verdict lines: key<TAB>PASS|FAIL|ERROR<TAB>note, parsed from the harness result files. The callback is
# called as '<callback> <rerunId> <sceId> <key> <status> <note>' after rerun_case_log_begin, and appends the
# case's evidence to RERUN_CASE_LOG / RERUN_CASE_DIR.
function rerun_report_cases() {
    local map=$1 vfile=$2 callback=$3 missing=$4 id sce key line status note msg
    while IFS=$'\t' read -r id sce key <&3; do
        rerun_reported "$id" && continue
        line=$(rerun_verdict_lookup "$vfile" "$key")
        if [ -n "$line" ]; then
            status=${line%%$'\t'*}
            note=${line#*$'\t'}
        else
            status=ERROR
            note=$missing
        fi
        case "$status" in PASS|FAIL|ERROR) ;; *) note="unexpected harness status '$status'${note:+; $note}"; status=ERROR ;; esac
        msg=$note
        [ -n "$RERUN_HARNESS_NOTE" -a "$status" == ERROR ] && msg="${msg:+$msg; }$RERUN_HARNESS_NOTE"
        rerun_case_log_begin "$id" "$sce" "$status" "$msg"
        "$callback" "$id" "$sce" "$key" "$status" "$note"
        rerun_verdict "$id" "$status" "$id/case.log" "$msg"
    done 3< "$map"
}

# rerun_case_log_begin <rerunId> <sceId> <status> <message>: create <rerunId>/case.log (RERUN_CASE_LOG).
function rerun_case_log_begin() {
    RERUN_CASE_DIR=$RERUN_LOG_ROOT/$1
    RERUN_CASE_LOG=$RERUN_CASE_DIR/case.log
    mkdir -p "$RERUN_CASE_DIR"
    {
        echo "Rerun case      : $2"
        echo "Verdict         : $3${4:+ ($4)}"
        echo "Category        : ${BUILD_SCENARIOS}"
        echo "Build           : ${BUILD_ID}"
        echo "Testcase branch : ${RERUN_BRANCH}"
        echo "Testcase commit : ${RERUN_TC_COMMIT}"
        echo "Testcases from  : $(rerun_tc_source)"
        echo "Consumer        : ${USER}@$(hostname)"
        echo "Batch logs      : ../runner.log, ../setup/, ../harness/"
        echo
    } | rerun_scrub_stream > "$RERUN_CASE_LOG"
}

# rerun_case_log_section <title> [file]: append a section from a file (or stdin), scrubbed and size-capped.
function rerun_case_log_section() {
    echo "==================== $1 ====================" >> "$RERUN_CASE_LOG"
    if [ $# -ge 2 ]; then
        if [ -s "$2" ]; then
            tail -c 2000000 "$2" | rerun_scrub_stream >> "$RERUN_CASE_LOG"
        else
            echo "(none)" >> "$RERUN_CASE_LOG"
        fi
    else
        tail -c 2000000 | rerun_scrub_stream >> "$RERUN_CASE_LOG"
    fi
    echo >> "$RERUN_CASE_LOG"
}

# rerun_case_copy <file> [name]: copy one text evidence file into the case dir (scrubbed).
function rerun_case_copy() {
    [ -f "$1" ] || return 1
    tail -c "$RERUN_LOG_MAX_BYTES" "$1" | rerun_scrub_stream > "$RERUN_CASE_DIR/${2:-$(basename "$1")}"
}

# rerun_extract_block <file> <needle> <stop ERE>: from the first line containing <needle> (fixed string)
# up to, not including, the next line matching <stop ERE>.
function rerun_extract_block() {
    [ -f "$1" ] || return 0
    RERUN_AWK_NEEDLE=$2 RERUN_AWK_STOP=$3 awk '
        BEGIN { needle = ENVIRON["RERUN_AWK_NEEDLE"]; stop = ENVIRON["RERUN_AWK_STOP"] }
        on && $0 ~ stop { exit }
        !on && index($0, needle) > 0 { on = 1; print; next }
        on { print }' "$1"
}

#######################################################################################################
# Secrets and published logs
#######################################################################################################

function rerun_secret_add() {
    local v
    for v in "$@"; do
        v=${v%$'\r'}
        if [ ${#v} -ge 4 ]; then
            printf '%s\n' "$v" >> "$RERUN_SECRETS"
        fi
    done
}

# rerun_secrets_from_conf <file>: every value of a *pwd* / *passw* / *secret* / *token* key.
function rerun_secrets_from_conf() {
    [ -f "$1" ] || return 0
    awk '{
            line = $0; sub(/^[ \t]+/, "", line)
            if (line ~ /^[#;!]/) next
            p = index(line, "="); if (p == 0) next
            k = tolower(substr(line, 1, p - 1)); v = substr(line, p + 1)
            sub(/^[ \t]+/, "", v); sub(/[ \t\r]+$/, "", v)
            if (k ~ /(pwd|passw|secret|token)/ && length(v) >= 4) print v
        }' "$1" >> "$RERUN_SECRETS"
}

function rerun_secrets_init() {
    rerun_secret_add "$DAILYQA_DAILYSRV_PWD" "$DAILYQA_GIT_PWD" "$DAILYQA_SSH_PWD_DEFAULT" "$MKEY_COVERAGE_UPLOAD_PWD" \
        "$KEY_SSH_PWD_DEFAULT" "$KEY_GIT_PWD_DEFAULT" "$RERUN_TOKEN" "$MKEY_RERUN_TOKEN"
    rerun_secrets_from_conf "${CTP_HOME}/conf/common.conf"
}

# rerun_scrub_stream [drop ERE]: stdin -> stdout with known secrets, URL credentials, '-password x' and
# '<..pwd..>=x' values masked, and ctp.sh's configuration dump removed. Lines matching <drop ERE> are dropped.
function rerun_scrub_stream() {
    RERUN_AWK_DROP=$1 awk -v sf="$RERUN_SECRETS" '
        BEGIN {
            drop = ENVIRON["RERUN_AWK_DROP"]; n = 0
            while ((getline s < sf) > 0) if (length(s) >= 4 && !(s in seen)) { seen[s] = 1; sec[++n] = s }
            close(sf)
            # longest first: a short secret inside a longer one must not break the longer match
            for (i = 2; i <= n; i++) { v = sec[i]; for (j = i - 1; j >= 1 && length(sec[j]) < length(v); j--) sec[j + 1] = sec[j]; sec[j + 1] = v }
        }
        incfg { if ($0 ~ /^----------END OF FILE----------/) incfg = 0; next }
        /\] CONFIG FILE: / { print; print "[RERUN] (configuration dump removed: it holds passwords)"; incfg = 1; next }
        drop != "" && $0 ~ drop { next }
        {
            line = $0
            for (i = 1; i <= n; i++) {
                out = ""
                while ((p = index(line, sec[i])) > 0) {
                    out = out substr(line, 1, p - 1) "****"
                    line = substr(line, p + length(sec[i]))
                }
                line = out line
            }
            print line
        }' | sed -E \
        -e 's#(://)[^/@:[:space:]]+:[^/[:space:]]*@#\1****:****@#g' \
        -e 's#(-password|-pwd|--password)([[:space:]]+)[^[:space:]]+#\1\2****#g' \
        -e 's#([A-Za-z0-9_.]*([Pp][Ww][Dd]|[Pp][Aa][Ss][Ss][Ww][Oo][Rr][Dd]|[Pp][Aa][Ss][Ss][Ww][Dd])[A-Za-z0-9_.]*[[:space:]]*[=:][[:space:]]*)[^[:space:]]+#\1****#g'
}

# rerun_save_log <file> <path relative to RERUN_LOG_ROOT> [drop ERE]: publish a scrubbed, size-capped copy.
function rerun_save_log() {
    local src=$1 dest=$RERUN_LOG_ROOT/$2
    [ -f "$src" ] || return 1
    mkdir -p "$(dirname "$dest")"
    if [ "$(wc -c < "$src")" -gt "$RERUN_LOG_MAX_BYTES" ]; then
        { echo "[RERUN] (log truncated to its last $RERUN_LOG_MAX_BYTES bytes)"; tail -c "$RERUN_LOG_MAX_BYTES" "$src"; } | rerun_scrub_stream "$3" > "$dest"
    else
        rerun_scrub_stream "$3" < "$src" > "$dest"
    fi
}

#######################################################################################################
# Build, message and configuration
#######################################################################################################

# rerun_precheck_message: ERROR every case and return 1 when the message cannot be rerun at all.
function rerun_precheck_message() {
    if [ "$BUILD_IS_FROM_GIT" != "1" ]; then
        rerun_error_all "rerun supports git builds only (BUILD_IS_FROM_GIT=${BUILD_IS_FROM_GIT})"
        return 1
    fi
    if [ "$BUILD_TYPE" == "coverage" ]; then
        rerun_error_all "rerun does not support coverage builds"
        return 1
    fi
    if [ -z "$url" ]; then
        rerun_error_all "the original message has no build url (BUILD_URLS)"
        return 1
    fi
    if [ -z "$RERUN_BRANCH" ]; then
        rerun_error_all "the original message has no BUILD_SCENARIO_BRANCH_GIT"
        return 1
    fi
    if [ -n "$RERUN_TC_REF" ] && ! [[ "$RERUN_TC_REF" =~ ^refs/pull/[1-9][0-9]*/head$ ]]; then
        rerun_error_all "unsupported testcase ref ${RERUN_TC_REF} (refs/pull/<number>/head only)"
        return 1
    fi
    if [ -n "$RERUN_TC_REF" ] && ! [[ "$RERUN_TC_SHA" =~ ^[0-9a-f]{40}$ ]]; then
        # without it a pull request of the same number in another testcase repository would run unnoticed
        rerun_error_all "a pull request rerun needs the commit QAHome knows on it (MKEY_RERUN_TC_SHA)"
        return 1
    fi
    # init.sh could not read the engine commit time on GitHub: run_git_update would refuse to update the
    # testcases, so the original run's testcases cannot be taken now
    if [ "$BUILD_COMMIT_TIME" == "lookup_failed" ]; then
        rerun_error_all "could not read the engine commit time of ${BUILD_ID} on GitHub, so the testcases of the original run cannot be taken; request the rerun again later"
        return 1
    fi
    return 0
}

# rerun_timeout <timeout(1) arguments...>: timeout(1) with its process group on record. timeout makes itself the
# leader of a new process group, out of reach of a cancel that stops the runner's group, so run_rerun.sh also
# stops every group listed in RERUN_PGID_FILE. Use it for every local command that runs under a timeout.
function rerun_timeout() {
    local pid rc
    timeout "$@" &
    pid=$!
    echo "$pid" >> "$RERUN_PGID_FILE"
    wait $pid
    rc=$?
    sed -i "/^${pid}\$/d" "$RERUN_PGID_FILE" 2>/dev/null
    return $rc
}

# rerun_step <name> <timeout secs> <command...>: run a script (not a shell function) with its output in
# RERUN_FAM_WORK/<name>.log and a scrubbed copy in setup/<name>.log; returns the command's status.
function rerun_step() {
    local name=$1 secs=$2 rc
    shift 2
    rerun_log "step ${name}: start"
    rerun_timeout -k 60 "$secs" "$@" > "$RERUN_FAM_WORK/$name.log" 2>&1 < /dev/null
    rc=$?
    rerun_save_log "$RERUN_FAM_WORK/$name.log" "setup/$name.log"
    if [ $rc -eq 124 -o $rc -eq 137 ]; then
        rerun_log "step ${name}: timed out after ${secs}s"
    else
        rerun_log "step ${name}: exit status ${rc}"
    fi
    return $rc
}

# rerun_run_action <file.act>: init.sh's runAction (a shell function, so no timeout), output in setup/.
function rerun_run_action() {
    if [ -f "${CTP_HOME}/conf/$1" ]; then
        rerun_log "runAction $1"
        # runAction is exported by sched/init.sh; under a timeout so a hung cleanup cannot hold the consumer
        rerun_timeout 1800 bash -c 'runAction "$1"' rerun_action "$1" > "$RERUN_FAM_WORK/action_$1.log" 2>&1 < /dev/null
        rerun_save_log "$RERUN_FAM_WORK/action_$1.log" "setup/action_$1.log"
    else
        rerun_log "conf/$1 not found, skip it (as the regular runner does)"
    fi
}

# rerun_check_installed: after a local install, cubrid_rel must answer with BUILD_ID (same parsing as
# sql/bin/run.sh init_cubrid_version), so a failed install never reruns cases on the previous build.
function rerun_check_installed() {
    local rel ver
    RERUN_LAST_ERROR=""
    rel=$(rerun_timeout 120 cubrid_rel 2>&1 < /dev/null | grep CUBRID | head -1)
    if [ -z "$rel" ]; then
        RERUN_LAST_ERROR="build install failed: cubrid_rel does not answer after installing ${url##*/} (see setup/install.log)"
        return 1
    fi
    ver=$(echo "$rel" | awk -F'(' '{print $2}' | awk -F')' '{print $1}')
    if [ -n "$BUILD_ID" -a "$ver" != "$BUILD_ID" ]; then
        RERUN_LAST_ERROR="build install failed: the installed build is ${ver}, not ${BUILD_ID} (see setup/install.log)"
        return 1
    fi
    rerun_log "installed build: ${rel}"
    return 0
}

# rerun_set_tc_commit <sha>
function rerun_set_tc_commit() {
    RERUN_TC_COMMIT=$1
    echo "$1" > "$RERUN_TC_COMMIT_FILE"
    rerun_log "testcase commit ${RERUN_BRANCH} = $1"
}

# rerun_tc_checkout_script <file>: append the shell lines that finish a testcase update in "$d" after
# run_git_update: they check run_git_update's own report (its "ERROR:" line, and the "TC pin time:" line when
# the build's testcases are pinned), then for a pull request rerun fetch RERUN_TC_REF from the testcase
# repository (a temporary remote made the way run_git_update makes auto_update_url: the checkout's origin
# with conf/common.conf's git_user / git_pwd; removed again, and the URL never printed) and check out its head. Every failure prints RERUN_ERROR=<reason> and exits 0. Expects $W, $d and $rc (run_git_update's
# status, its output in $W/git_update.log); used locally and on the test nodes.
function rerun_tc_checkout_script() {
    cat >> "$1" <<RERUN_EOF
if [ \$rc -eq 124 ] || [ \$rc -eq 137 ]; then echo "RERUN_ERROR=testcase update timed out after ${RERUN_GIT_TIMEOUT}s"; exit 0; fi
if [ \$rc -ne 0 ]; then
    e=\$(grep -m1 '^ERROR: ' "\$W/git_update.log" | cut -c8-240)
    echo "RERUN_ERROR=testcase update failed: \${e:-run_git_update exit status \$rc}"; exit 0
fi
if [ -n "${BUILD_COMMIT_TIME}" ] && ! grep '^TC pin time: ' "\$W/git_update.log" | grep -qv '^TC pin time: none'; then
    echo "RERUN_ERROR=run_git_update did not pin the testcases to the build's engine commit time: the CTP there predates CUBRIDQA-1667"; exit 0
fi
RERUN_EOF
    [ -n "$RERUN_TC_REF" ] || return 0
    cat >> "$1" <<RERUN_EOF
cd "\$d" || exit 0
origin_url=\$(git config --get remote.origin.url)
gu=\$(sh "\${CTP_HOME}/bin/ini.sh" "\${CTP_HOME}/conf/common.conf" git_user | tr -d '\r')
gp=\$(sh "\${CTP_HOME}/bin/ini.sh" "\${CTP_HOME}/conf/common.conf" git_pwd | tr -d '\r')
git remote rm rerun_tc_url 2>/dev/null
git remote add rerun_tc_url "https://\${gu}:\${gp}@github.com\${origin_url#*github.com}"
timeout -k 60 600 git fetch -q rerun_tc_url "+${RERUN_TC_REF}:refs/rerun/${RERUN_PR_BRANCH}" < /dev/null > "\$W/pr_fetch.log" 2>&1
fetch_rc=\$?
git remote rm rerun_tc_url 2>/dev/null
if [ \$fetch_rc -ne 0 ]; then
    echo "RERUN_ERROR=cannot fetch ${RERUN_TC_REF} from \${origin_url#*github.com/}: \$(grep -m1 -iE 'fatal|error' "\$W/pr_fetch.log" | sed 's#https://[^ ]*#<url>#g' | cut -c1-160)"; exit 0
fi
tip=\$(git rev-parse "refs/rerun/${RERUN_PR_BRANCH}")
sha="${RERUN_TC_SHA}"
if [ -n "\$sha" ] && ! git merge-base --is-ancestor "\$sha" "\$tip" 2>/dev/null; then
    echo "RERUN_ERROR=${RERUN_TC_REF} no longer contains commit \$sha (its head is \$tip)"; exit 0
fi
if ! git checkout -q -f -B ${RERUN_PR_BRANCH} "\$tip" 2> "\$W/pr_checkout.log"; then
    echo "RERUN_ERROR=cannot check out \$tip of ${RERUN_TC_REF}: \$(head -1 "\$W/pr_checkout.log" | cut -c1-200)"; exit 0
fi
echo "Testcases from ${RERUN_TC_REF} at \$tip"
RERUN_EOF
}

# rerun_git_update_local <dir>: update a local checkout of BUILD_SCENARIO_BRANCH_GIT with the daily command
# (run_git_update, pinned like the original run) under a timeout, or to a pull request's head, verify the
# branch and set RERUN_GIT_COMMIT. On failure: status 1 and the reason in RERUN_LAST_ERROR. Call it directly,
# not in $(...), or the reason is lost.
function rerun_git_update_local() {
    local dir=$1 rc f out branch head
    RERUN_LAST_ERROR=""
    RERUN_GIT_COMMIT=""
    if [ ! -d "$dir" ]; then
        RERUN_LAST_ERROR="testcase checkout $dir does not exist on ${USER}@$(hostname)"
        return 1
    fi
    rerun_step git_update "$RERUN_GIT_TIMEOUT" run_git_update -f "$dir" -b "$RERUN_BRANCH" >&2
    rc=$?
    f=$RERUN_FAM_WORK/tc_checkout.sh
    { echo "W=$RERUN_FAM_WORK"; echo "d=$dir"; echo "rc=$rc"; } > "$f"
    rerun_tc_checkout_script "$f"
    out=$(rerun_timeout -k 60 900 bash "$f" 2>&1 < /dev/null)
    if echo "$out" | grep -q '^RERUN_ERROR='; then
        RERUN_LAST_ERROR="$(echo "$out" | sed -n 's/^RERUN_ERROR=//p' | head -1) (see setup/git_update.log)"
        return 1
    fi
    [ -n "$out" ] && rerun_log "$out"
    branch=$(cd "$dir" && git rev-parse --abbrev-ref HEAD 2>/dev/null)
    head=$(cd "$dir" && git rev-parse HEAD 2>/dev/null)
    if [ "$branch" != "$RERUN_CHECKOUT_BRANCH" ]; then
        RERUN_LAST_ERROR="testcase update failed: $dir is on '${branch}' instead of ${RERUN_CHECKOUT_BRANCH} (see setup/git_update.log)"
        return 1
    fi
    if ! [[ "$head" =~ ^[0-9a-f]{40}$ ]]; then
        RERUN_LAST_ERROR="cannot read the testcase commit of $dir"
        return 1
    fi
    RERUN_GIT_COMMIT=$head
}

# rerun_conf_get <conf> <key>: raw value (no ${HOME} expansion, last definition wins) of a key in a
# properties-like file, whatever section it is in.
function rerun_conf_get() {
    awk -v k="$2" '{
            line = $0; sub(/^[ \t]+/, "", line)
            if (line ~ /^[#;!]/) next
            p = index(line, "="); if (p == 0) next
            key = substr(line, 1, p - 1); sub(/[ \t]+$/, "", key)
            if (key != k) next
            v = substr(line, p + 1); sub(/^[ \t]+/, "", v); sub(/[ \t\r]+$/, "", v)
            val = v; found = 1
        } END { if (found) print val }' "$1"
}

# rerun_conf_values <conf> <key>: every raw value of a key, one per line (to assert a key is set once).
function rerun_conf_values() {
    awk -v k="$2" '{
            line = $0; sub(/^[ \t]+/, "", line)
            if (line ~ /^[#;!]/) next
            p = index(line, "="); if (p == 0) next
            key = substr(line, 1, p - 1); sub(/[ \t]+$/, "", key)
            if (key != k) next
            v = substr(line, p + 1); sub(/^[ \t]+/, "", v); sub(/[ \t\r]+$/, "", v); print v
        }' "$1"
}

# rerun_conf_drop_keys <conf> <key>...: delete every definition of these keys in any section.
# (ini.sh -u "key=" is silently ignored and ini.sh -d only works on one section.)
function rerun_conf_drop_keys() {
    local conf=$1
    shift
    awk -v keys=" $* " '{
            line = $0; sub(/^[ \t]+/, "", line)
            p = index(line, "=")
            if (p > 0 && line !~ /^[#;!]/) {
                key = substr(line, 1, p - 1); sub(/[ \t]+$/, "", key)
                if (index(keys, " " key " ") > 0) next
            }
            print
        }' "$conf" > "$conf.tmp" && mv -f "$conf.tmp" "$conf"
}

# rerun_conf_assert <conf> <key> <expected>: every definition equals <expected> ("" = the key is absent).
function rerun_conf_assert() {
    local values
    values=$(rerun_conf_values "$1" "$2" | sort -u)
    if [ "$values" != "$3" ]; then
        RERUN_LAST_ERROR="rerun conf guard failed: $2 is '$(echo $values)' instead of '$3'"
        return 1
    fi
    return 0
}

# rerun_conf_silence <conf> [ini section]: feedback to files only, no QAHome notice, no feedback DB,
# no in-harness retry and no continue mode; asserted afterwards (fails closed).
function rerun_conf_silence() {
    local conf=$1 section=$2 sopt=""
    [ -n "$section" ] && sopt="-s $section"
    rerun_conf_drop_keys "$conf" feedback_type feedback_notice_qahome_url feedback_db_host feedback_db_port \
        feedback_db_name feedback_db_user feedback_db_pwd testcase_retry_num test_continue_yn
    ini.sh $sopt "$conf" feedback_type file
    ini.sh $sopt "$conf" test_continue_yn false
    ini.sh $sopt "$conf" testcase_retry_num 0
    rerun_conf_assert "$conf" feedback_type file &&
        rerun_conf_assert "$conf" feedback_notice_qahome_url "" &&
        rerun_conf_assert "$conf" feedback_db_host "" &&
        rerun_conf_assert "$conf" test_continue_yn false &&
        rerun_conf_assert "$conf" testcase_retry_num 0
}

# rerun_conf_role_guard <conf>: the template must not install a coverage role (it would collect and upload coverage).
function rerun_conf_role_guard() {
    case "$(rerun_conf_get "$1" cubrid_install_role)" in
        *coverage*)
            RERUN_LAST_ERROR="the template installs a coverage role; rerun does not support it"
            return 1
            ;;
    esac
    return 0
}

# rerun_split_scenario <raw scenario>: sets RERUN_REPO_RAW (prefix up to the cubrid-testcases* directory,
# unexpanded), RERUN_REPO_NAME and RERUN_SCEN_REL (scenario path inside the repo, may be empty).
function rerun_split_scenario() {
    local s=${1%/}
    RERUN_REPO_RAW=$(echo "$s" | sed -nE 's#^((.*/)?cubrid-testcases[^/]*)(/.*)?$#\1#p')
    if [ -z "$RERUN_REPO_RAW" ]; then
        RERUN_LAST_ERROR="the template scenario '$1' is not inside a cubrid-testcases* checkout"
        return 1
    fi
    RERUN_REPO_NAME=${RERUN_REPO_RAW##*/}
    RERUN_SCEN_REL=${s#"$RERUN_REPO_RAW"}
    RERUN_SCEN_REL=${RERUN_SCEN_REL#/}
    return 0
}

# rerun_bad_path <path>: true for an absolute path, a '..' component, an empty path or a control character.
function rerun_bad_path() {
    case "/$1/" in
        //*|*/../*|*/./*) return 0 ;;
    esac
    [ -z "$1" ] && return 0
    [[ "$1" == *[[:cntrl:]]* ]] && return 0
    return 1
}

#######################################################################################################
# Harness run
#######################################################################################################

# rerun_run_harness <ctp type> <conf> <log> <timeout secs>: ctp.sh under a timeout (it kills the whole
# process group), from ${CTP_HOME}. Sets RERUN_HARNESS_NOTE when the run ended abnormally.
function rerun_run_harness() {
    local type=$1 conf=$2 log=$3 secs=$4 rc reason
    rerun_log "harness: ctp.sh ${type} (timeout ${secs}s)"
    (cd "${CTP_HOME}" && rerun_timeout -k 120 "$secs" "${CTP_HOME}/bin/ctp.sh" "$type" -c "$conf" > "$log" 2>&1 < /dev/null)
    rc=$?
    RERUN_HARNESS_NOTE=""
    if [ $rc -eq 124 -o $rc -eq 137 ]; then
        RERUN_HARNESS_NOTE="harness timed out after ${secs}s"
    else
        reason=$(grep -E '^\[ERROR\]|^QUIT$|NO TEST CASE TO TEST|compile fail|Not found any|^Error: ' "$log" 2>/dev/null | tail -1 | cut -c1-300 | rerun_scrub_stream "")
        [ -n "$reason" ] && RERUN_HARNESS_NOTE="harness said: ${reason}"
    fi
    rerun_log "harness: exit status ${rc}${RERUN_HARNESS_NOTE:+ (${RERUN_HARNESS_NOTE})}"
    return 0
}

# rerun_clean_runtime_logs <family>: same reset as the regular runner (result/<family>/current_runtime_logs).
function rerun_clean_runtime_logs() {
    local d=${CTP_HOME}/result/$1/current_runtime_logs
    if [ -d "$d" ]; then
        rm -f "$d"/* > /dev/null 2>&1
    else
        mkdir -p "$d"
    fi
}

# rerun_save_runtime_logs <family> [drop ERE]: publish the harness logs of current_runtime_logs, scrubbed.
# Never main_snapshot.properties (it dumps every password of the conf), never the conf or a tarball.
function rerun_save_runtime_logs() {
    local d=${CTP_HOME}/result/$1/current_runtime_logs f
    for f in "$d"/*.log "$d"/*.txt "$d"/test_status.data; do
        [ -f "$f" ] || continue
        rerun_save_log "$f" "harness/$(basename "$f")" "$2"
    done
}

# rerun_junit_verdicts <junit xml>: "file<TAB>PASS|FAIL|SKIP|ERROR" per <testcase> of a CTP FeedbackFile
# test-<category>.xml (one child element: none = pass, failure, skipped or error). CDATA is skipped and
# quoted attribute values may hold '>' (JDBC ids look like 'X.java => m()').
function rerun_junit_verdicts() {
    [ -f "$1" ] || return 0
    awk '
        function unesc(v) {
            gsub(/&quot;/, "\"", v); gsub(/&apos;/, "\047", v); gsub(/&lt;/, "<", v); gsub(/&gt;/, ">", v); gsub(/&amp;/, "\\&", v)
            return v
        }
        function attr(s, name,   p, rest, q) {
            p = index(s, " " name "=\""); if (p == 0) return ""
            rest = substr(s, p + length(name) + 3); q = index(rest, "\"")
            return unesc(substr(rest, 1, q - 1))
        }
        # end of the start tag beginning at s: index of its closing ">" (quotes respected), 0 if not on this line
        function tag_end(s,   i, c, inq) {
            inq = 0
            for (i = 1; i <= length(s); i++) {
                c = substr(s, i, 1)
                if (c == "\"") inq = !inq
                else if (c == ">" && !inq) return i
            }
            return 0
        }
        function emit() { if (cur != "") print cur "\t" st; cur = "" }
        function handle(h,   p, e) {
            p = index(h, "<testcase ")
            if (p > 0) {
                emit()
                h = substr(h, p); cur = attr(h, "file"); st = "PASS"
                e = tag_end(h)
                if (e > 0) {
                    if (substr(h, e - 1, 1) == "/") { emit(); return }
                    h = substr(h, e + 1)
                } else {
                    return
                }
            }
            if (cur == "") return
            if (st == "PASS") {
                if (index(h, "<failure") > 0) st = "FAIL"
                else if (index(h, "<skipped") > 0) st = "SKIP"
                else if (index(h, "<error") > 0) st = "ERROR"
            }
            if (index(h, "</testcase>") > 0) emit()
        }
        {
            s = $0
            while (s != "") {
                if (incdata) {
                    p = index(s, "]]>"); if (p == 0) { s = ""; break }
                    s = substr(s, p + 3); incdata = 0; continue
                }
                p = index(s, "<![CDATA[")
                if (p > 0) { head = substr(s, 1, p - 1); s = substr(s, p + 9); incdata = 1 } else { head = s; s = "" }
                handle(head)
            }
        }' "$1"
}

# rerun_junit_block <junit xml> <file attribute>: the raw <testcase> element of one case (evidence).
function rerun_junit_block() {
    [ -f "$1" ] || return 0
    awk -v want="$2" '
        function unesc(v) {
            gsub(/&quot;/, "\"", v); gsub(/&apos;/, "\047", v); gsub(/&lt;/, "<", v); gsub(/&gt;/, ">", v); gsub(/&amp;/, "\\&", v)
            return v
        }
        !on && index($0, "<testcase ") > 0 {
            s = substr($0, index($0, " file=\"") + 7); f = unesc(substr(s, 1, index(s, "\"") - 1))
            if (f == want) { on = 1; print; if (index($0, "</testcase>") > 0 || $0 ~ /\/>[ \t]*$/) exit; next }
        }
        on { print; if ($0 ~ /^[ \t]*<\/testcase>/) exit }' "$1"
}

#######################################################################################################
# Test nodes (shell, isolation, ha_repl, cdc_repl)
#######################################################################################################

# rerun_env_ids <conf> [role]: env ids of env.<id>[.<role>].ssh.host keys with a value, in file order.
function rerun_env_ids() {
    local sfx=".ssh.host"
    [ -n "$2" ] && sfx=".$2.ssh.host"
    awk -v sfx="$sfx" '{
            line = $0; sub(/^[ \t]+/, "", line)
            if (line !~ /^env\./) next
            p = index(line, "="); if (p == 0) next
            key = substr(line, 1, p - 1); sub(/[ \t]+$/, "", key)
            v = substr(line, p + 1); gsub(/[ \t\r]/, "", v); if (v == "") next
            ls = length(key) - length(sfx)
            if (ls <= 4 || substr(key, ls + 1) != sfx) next
            id = substr(key, 5, ls - 4)
            if (!(id in seen)) { seen[id] = 1; print id }
        }' "$1"
}

# rerun_env_roles <conf> <envId>: roles of an HA/CDC env (env.<id>.<role>.ssh.host), e.g. master slave.
function rerun_env_roles() {
    awk -v pre="env.$2." '{
            line = $0; sub(/^[ \t]+/, "", line)
            if (index(line, pre) != 1) next
            p = index(line, "="); if (p == 0) next
            key = substr(line, 1, p - 1); sub(/[ \t]+$/, "", key)
            v = substr(line, p + 1); gsub(/[ \t\r]/, "", v); if (v == "") next
            rest = substr(key, length(pre) + 1)
            if (rest ~ /^[^.]+\.ssh\.host$/) { split(rest, a, "."); if (!(a[1] in seen)) { seen[a[1]] = 1; print a[1] } }
        }' "$1"
}

# rerun_env_prop <conf> <envId> <role or ""> <key>: env.<id>[.<role>].<key>, else default.<role>.<key>,
# else default.<key> (the harness lookup order).
function rerun_env_prop() {
    local conf=$1 id=$2 role=$3 key=$4 v
    if [ -n "$role" ]; then
        v=$(rerun_conf_get "$conf" "env.$id.$role.$key")
        [ -z "$v" ] && v=$(rerun_conf_get "$conf" "default.$role.$key")
    else
        v=$(rerun_conf_get "$conf" "env.$id.$key")
    fi
    [ -z "$v" ] && v=$(rerun_conf_get "$conf" "default.$key")
    echo "$v"
}

# rerun_conf_keep_envs <conf> <envId>...: drop the env.<id>.* keys of every other env.
function rerun_conf_keep_envs() {
    local conf=$1
    shift
    awk -v keep="$*" '{
            line = $0; sub(/^[ \t]+/, "", line)
            if (line !~ /^[#;!]/ && line ~ /^env\./) {
                n = split(keep, ids, " "); ok = 0
                for (i = 1; i <= n; i++) if (index(line, "env." ids[i] ".") == 1) ok = 1
                if (!ok) next
            }
            print
        }' "$conf" > "$conf.tmp" && mv -f "$conf.tmp" "$conf"
}

# rerun_script <name>: path of a new script file in RERUN_FAM_WORK, starting with the node prologue
# (W = the batch dir on the node, not created here; CTP scripts on PATH). Append the body with
# cat >> "$f" <<EOF; a script that writes into $W creates it (cleanup is registered for those nodes only).
function rerun_script() {
    local f=$RERUN_FAM_WORK/node_$1.sh
    cat > "$f" <<EOF
cd
W=\$HOME/$RERUN_NODE_DIR/$RERUN_TAG
if [ -n "\$CTP_HOME" ] && [ -d "\$CTP_HOME/common/script" ]; then export PATH=\$CTP_HOME/bin:\$CTP_HOME/common/script:\$PATH; fi
EOF
    echo "$f"
}

# rerun_ssh_exec <host|local> <port> <user> <password> <timeout secs> <script file>
# Runs a bash script on a node from its $HOME ("local" = this consumer) and prints its output. The script
# travels base64-encoded, so run_remote_script's "\n" rewriting and a CTP proxy's single quotes cannot
# mangle it. Tracing is off: the password is an argument.
function rerun_ssh_exec() {
    local host=$1 port=$2 user=$3 pwd=$4 secs=$5 script=$6 b64 rfile
    { set +x; } 2>/dev/null
    if [ "$host" == "local" ]; then
        (cd "$HOME" && rerun_timeout -k 30 "$secs" bash "$script" 2>&1 < /dev/null)
        return 0
    fi
    b64=$(base64 < "$script" | tr -d '\n')
    if [ ${#b64} -gt 100000 ]; then
        echo "RERUN_ERROR=node script too large for run_remote_script"
        return 1
    fi
    rfile="\$HOME/.ctp_rerun_${RERUN_TAG}_\$\$.sh"
    rerun_timeout -k 30 "$secs" run_remote_script -host "$host" -port "${port:-22}" -user "$user" -password "$pwd" \
        -c "echo $b64 | base64 -d > $rfile; bash $rfile 2>&1; rm -f $rfile" < /dev/null 2>&1
}

# rerun_env_exec <conf> <envId|local> <role or ""> <timeout secs> <script file>: rerun_ssh_exec on the env's host.
function rerun_env_exec() {
    local conf=$1 id=$2 role=$3
    if [ "$id" == "local" ]; then
        rerun_ssh_exec local "" "" "" "$4" "$5"
        return
    fi
    rerun_ssh_exec "$(rerun_env_prop "$conf" "$id" "$role" ssh.host)" "$(rerun_env_prop "$conf" "$id" "$role" ssh.port)" \
        "$(rerun_env_prop "$conf" "$id" "$role" ssh.user)" "$(rerun_env_prop "$conf" "$id" "$role" ssh.pwd)" "$4" "$5"
}

# rerun_env_title <conf> <envId> [role]: user@host:port of an env (for messages).
function rerun_env_title() {
    if [ "$2" == "local" ]; then
        echo "local(${USER}@$(hostname))"
        return
    fi
    local port
    port=$(rerun_env_prop "$1" "$2" "$3" ssh.port)
    echo "$(rerun_env_prop "$1" "$2" "$3" ssh.user)@$(rerun_env_prop "$1" "$2" "$3" ssh.host):${port:-22}"
}

# rerun_env_alive <conf> <envId> <shell|ha>: every host of the env answers over ssh (shell: main host and
# ssh.relatedhosts with the same account; ha: every role host). The harness QUITs the whole run otherwise.
function rerun_env_alive() {
    local conf=$1 id=$2 mode=$3 f role h out
    f=$(rerun_script alive)
    echo 'echo RERUN_ALIVE' >> "$f"
    if [ "$mode" == "ha" ]; then
        for role in $(rerun_env_roles "$conf" "$id"); do
            out=$(rerun_env_exec "$conf" "$id" "$role" 120 "$f")
            echo "$out" | grep -q RERUN_ALIVE || return 1
        done
        return 0
    fi
    out=$(rerun_env_exec "$conf" "$id" "" 120 "$f")
    echo "$out" | grep -q RERUN_ALIVE || return 1
    for h in $(rerun_conf_get "$conf" "env.$id.ssh.relatedhosts" | tr ',' ' '); do
        out=$(rerun_ssh_exec "$h" "$(rerun_env_prop "$conf" "$id" "" ssh.port)" "$(rerun_env_prop "$conf" "$id" "" ssh.user)" \
            "$(rerun_env_prop "$conf" "$id" "" ssh.pwd)" 120 "$f")
        echo "$out" | grep -q RERUN_ALIVE || return 1
    done
    return 0
}

# rerun_pick_envs <conf> <shell|ha> <max>: up to <max> reachable env ids in template order, one per line;
# "local" when the template defines no env (the harness then runs on this consumer).
function rerun_pick_envs() {
    local conf=$1 mode=$2 max=$3 ids id n=0
    if [ "$mode" == "ha" ]; then
        ids=$(rerun_env_ids "$conf" master)
    else
        ids=$(rerun_env_ids "$conf")
    fi
    if [ -z "$ids" ]; then
        [ "$mode" == "ha" ] || echo local
        return 0
    fi
    for id in $ids; do
        [ $n -ge "$max" ] && break
        if rerun_env_alive "$conf" "$id" "$mode"; then
            echo "$id"
            n=$((n + 1))
        else
            rerun_log "env ${id} ($(rerun_env_title "$conf" "$id" "$([ "$mode" == ha ] && echo master)")) is not reachable, skip it" >&2
        fi
    done
}

# rerun_env_push_file <conf> <envId> <local file> <file name in the node batch dir>: copy a small text
# file to the node (in chunks: run_remote_script takes the script as one command-line argument).
function rerun_env_push_file() {
    local conf=$1 id=$2 src=$3 name=$4 f part out
    rm -f "$RERUN_FAM_WORK"/push_part_*
    : > "$RERUN_FAM_WORK/push_empty"
    if [ -s "$src" ]; then
        split -l 200 "$src" "$RERUN_FAM_WORK/push_part_"
    else
        cp "$RERUN_FAM_WORK/push_empty" "$RERUN_FAM_WORK/push_part_aa"
    fi
    f=$(rerun_script push)
    echo "mkdir -p \"\$W\" && : > \"\$W/$name\"" >> "$f"
    out=$(rerun_env_exec "$conf" "$id" "" "$RERUN_REMOTE_TIMEOUT" "$f")
    for part in "$RERUN_FAM_WORK"/push_part_*; do
        f=$(rerun_script push)
        {
            echo "base64 -d >> \"\$W/$name\" <<'RERUN_B64_EOF'"
            base64 < "$part"
            echo "RERUN_B64_EOF"
            echo "echo RERUN_PUSHED"
        } >> "$f"
        out=$(rerun_env_exec "$conf" "$id" "" "$RERUN_REMOTE_TIMEOUT" "$f")
        echo "$out" | grep -q RERUN_PUSHED || return 1
    done
    rm -f "$RERUN_FAM_WORK"/push_part_*
    return 0
}

# rerun_env_git_update <conf> <envId> <scenario dir, raw> <pin commit or "">: run_git_update on the node
# (timeout on both sides) with the build's BUILD_COMMIT_TIME as in the original run, then the pull request's
# head for a pull request rerun, optionally pin the checkout to the commit another env got, verify the branch
# and set RERUN_GIT_COMMIT. Status 1 and RERUN_LAST_ERROR on failure (call it directly, not in $(...)).
# The node records the update's process group in $W/git.pgid, so the env cleanup can stop a running update.
function rerun_env_git_update() {
    local conf=$1 id=$2 dir=$3 pin=$4 f out branch head
    RERUN_LAST_ERROR=""
    RERUN_GIT_COMMIT=""
    f=$(rerun_script git_update)
    cat >> "$f" <<EOF
mkdir -p "\$W"
d=$dir
if [ ! -d "\$d" ]; then echo "RERUN_ERROR=testcase checkout \$d does not exist"; exit 0; fi
$( [[ "$BUILD_COMMIT_TIME" =~ ^[0-9]+$ ]] && echo "export BUILD_COMMIT_TIME=$BUILD_COMMIT_TIME" || echo "unset BUILD_COMMIT_TIME" )
timeout -k 60 $RERUN_GIT_TIMEOUT run_git_update -f "\$d" -b "$RERUN_BRANCH" > "\$W/git_update.log" 2>&1 < /dev/null &
echo \$! > "\$W/git.pgid"
wait \$!
rc=\$?
rm -f "\$W/git.pgid"
tail -n 40 "\$W/git_update.log"
EOF
    rerun_tc_checkout_script "$f"
    cat >> "$f" <<EOF
pin="$pin"
if [ -n "\$pin" ]; then (cd "\$d" && git cat-file -e "\$pin^{commit}" 2>/dev/null && git reset -q --hard "\$pin"); fi
cd "\$d" && echo "RERUN_BRANCH=\$(git rev-parse --abbrev-ref HEAD)" && echo "RERUN_HEAD=\$(git rev-parse HEAD)"
EOF
    out=$(rerun_env_exec "$conf" "$id" "" $((RERUN_GIT_TIMEOUT + 300)) "$f")
    echo "$out" > "$RERUN_FAM_WORK/git_update_env_$id.log"
    rerun_save_log "$RERUN_FAM_WORK/git_update_env_$id.log" "setup/git_update_env_$id.log"
    branch=$(echo "$out" | sed -n 's/^RERUN_BRANCH=//p' | tail -1)
    head=$(echo "$out" | sed -n 's/^RERUN_HEAD=//p' | tail -1)
    if echo "$out" | grep -q '^RERUN_ERROR='; then
        RERUN_LAST_ERROR=$(echo "$out" | sed -n 's/^RERUN_ERROR=//p' | head -1)
        return 1
    fi
    if [ -z "$branch" ]; then
        RERUN_LAST_ERROR="testcase update on env $id gave no answer: $(echo "$out" | grep -m1 '^ERROR' | cut -c1-200)"
        return 1
    fi
    if [ "$branch" != "$RERUN_CHECKOUT_BRANCH" ]; then
        RERUN_LAST_ERROR="testcase update failed on env $id: checkout is on '${branch}' instead of ${RERUN_CHECKOUT_BRANCH}"
        return 1
    fi
    if ! [[ "$head" =~ ^[0-9a-f]{40}$ ]]; then
        RERUN_LAST_ERROR="cannot read the testcase commit on env $id"
        return 1
    fi
    if [ -n "$pin" -a "$head" != "$pin" ]; then
        RERUN_LAST_ERROR="env $id cannot be pinned to testcase commit $pin (it is at $head)"
        return 1
    fi
    RERUN_GIT_COMMIT=$head
}

# rerun_env_cleanup_cmd <conf> <envId> [extra node dir, relative to $HOME]: a command string for
# rerun_on_exit that removes the batch dirs on the node.
function rerun_env_cleanup_cmd() {
    local f frozen
    f=$(rerun_script cleanup_$2)
    # a testcase update still running there (cancel): stop its process group first
    echo "[ -s \"\$W/git.pgid\" ] && kill -TERM -- -\$(cat \"\$W/git.pgid\") 2>/dev/null" >> "$f"
    echo "rm -rf \"\$W\"${3:+ \"\$HOME/$3\"}" >> "$f"
    echo "rmdir \"\$HOME/$RERUN_NODE_DIR\" \"\$HOME/$RERUN_NODE_STAGE\" 2>/dev/null" >> "$f"
    # The runner later prunes dropped envs from the conf; keep a copy so this env can still be reached.
    frozen=$RERUN_FAM_WORK/.cleanup_env_$2.conf
    (umask 077; cp -f "$1" "$frozen")
    echo "rerun_env_exec '$frozen' '$2' '' 300 '$f'"
}

#######################################################################################################
# Log capture for the AI analysis (shell family)
#######################################################################################################
#
# A daily run keeps almost nothing of what a case script writes: the case removes its own output files after
# its last check (rm -f *.log, as the rulebook asks) and finish removes the client error logs. A rerun
# therefore runs each requested case script with one more command in front of its first line (the line count
# does not change): it sources $W/capture.sh, which shadows rm with a shell function that first copies each
# text file of the script's own cases/ directory it is about to remove, then runs the real rm with the same
# arguments and status, and on exit copies the untracked or modified text files the case left in that
# directory. Only files directly in the cases/ directory: not its subdirectories, not $CUBRID/log (whoever
# needs those looks on the node). Each file is cut to its last RERUN_CAPTURE_FILE_MAX bytes, at most
# RERUN_CAPTURE_CASE_MAX bytes per case. The scripts are put back right after the harness (and on exit), the
# copies come back to the consumer and each case's are published under <rerunId>/capture/ with an INDEX.tsv.
# Not seen: files removed by another program (find -delete, xargs rm, a child script), and the exit copy of a
# case killed by the harness timeout or that sets its own EXIT trap.

RERUN_CAPTURE_FILE_MAX=${RERUN_CAPTURE_FILE_MAX:-2000000}
RERUN_CAPTURE_CASE_MAX=${RERUN_CAPTURE_CASE_MAX:-30000000}

# rerun_capture_script: the capture.sh a case script sources (stdout).
function rerun_capture_script() {
    cat <<EOF
# Sourced by the first line of a case script during a QAHome rerun (CUBRIDQA-1630): copies of the text files
# of the script's cases/ directory that the case removes with rm, and of those it left when it exits.
RERUN_CAP_CASES=\$(pwd)
RERUN_CAP_DIR="\$HOME/$RERUN_NODE_DIR/$RERUN_TAG/capture/\$(pwd | tr '/' '_')"
RERUN_CAP_SELF="\${0##*/}"
RERUN_CAP_FILE_MAX=$RERUN_CAPTURE_FILE_MAX
RERUN_CAP_CASE_MAX=$RERUN_CAPTURE_CASE_MAX
EOF
    cat <<'EOF'
mkdir -p "$RERUN_CAP_DIR/files" 2>/dev/null
[ -f "$RERUN_CAP_DIR/case_dir" ] || pwd > "$RERUN_CAP_DIR/case_dir"
# rerun_cap_one <file> <how>: one copy and one index line (how, time, size, copy or -, file name) when the file is
# a text file directly in the cases/ directory. init.sh turns on set -x, so the callers turn tracing off: these
# lines must not flood the case's console output.
rerun_cap_one() {
    local f=$1 how=$2 name size dest n used keep
    case "$f" in /*) ;; *) f="$(pwd)/$f" ;; esac
    case "$f" in "$RERUN_CAP_CASES"/*/*) return 0 ;; "$RERUN_CAP_CASES"/*) ;; *) return 0 ;; esac
    name=${f##*/}
    [ "$name" = "$RERUN_CAP_SELF" ] && return 0
    [ -f "$f" ] && [ ! -L "$f" ] || return 0
    size=$(stat -c %s "$f" 2>/dev/null) || return 0
    [ "${size:-0}" -gt 0 ] || return 0
    grep -Iq . "$f" 2>/dev/null || return 0
    keep=$size
    [ "$keep" -gt "$RERUN_CAP_FILE_MAX" ] && keep=$RERUN_CAP_FILE_MAX
    used=0
    [ -f "$RERUN_CAP_DIR/bytes" ] && read -r used < "$RERUN_CAP_DIR/bytes"
    if [ $((${used:-0} + keep)) -gt "$RERUN_CAP_CASE_MAX" ]; then
        n=0
        [ -f "$RERUN_CAP_DIR/skipped" ] && read -r n < "$RERUN_CAP_DIR/skipped"
        echo $((${n:-0} + 1)) > "$RERUN_CAP_DIR/skipped"
        [ "${n:-0}" -lt 100 ] && printf '%s\t%s\t%s\t-\t%s\n' "$how" "$(date +%H:%M:%S)" "$size" "$name" >> "$RERUN_CAP_DIR/index"
        return 0
    fi
    dest=$RERUN_CAP_DIR/files/$name
    n=1
    while [ -e "$dest" ]; do dest=$RERUN_CAP_DIR/files/$name.$n; n=$((n + 1)); done
    if [ "$size" -gt "$keep" ]; then tail -c "$keep" "$f" > "$dest" 2>/dev/null; else cp -p "$f" "$dest" 2>/dev/null; fi
    echo $((${used:-0} + keep)) > "$RERUN_CAP_DIR/bytes"
    printf '%s\t%s\t%s\t%s\t%s\n' "$how" "$(date +%H:%M:%S)" "$size" "${dest##*/}" "$name" >> "$RERUN_CAP_DIR/index"
    return 0
}
rm() {
    { rerun_cap_x=$-; set +x; } 2>/dev/null
    local a opts=1
    for a in "$@"; do
        if [ $opts -eq 1 ]; then
            case "$a" in --) opts=0; continue ;; -*) continue ;; esac
        fi
        rerun_cap_one "$a" rm
    done
    case "$rerun_cap_x" in *x*) set -x ;; esac
    command rm "$@"
}
rerun_cap_exit() {
    { set +x; } 2>/dev/null
    (cd "$RERUN_CAP_CASES" 2>/dev/null && git ls-files --others --modified . 2>/dev/null) | sort -u | while IFS= read -r rerun_cap_f; do
        rerun_cap_one "$RERUN_CAP_CASES/$rerun_cap_f" left
    done
    if [ -f "$RERUN_CAP_DIR/skipped" ]; then
        printf 'budget spent\t%s\t%s\t-\t(files not kept)\n' "$(date +%H:%M:%S)" "$(cat "$RERUN_CAP_DIR/skipped")" >> "$RERUN_CAP_DIR/index"
    fi
}
trap rerun_cap_exit EXIT
EOF
}

# rerun_capture_install <conf> <envId>: write $W/capture.sh on the node and put the source line in front of the
# first line of each requested case script ($W/requested_full.txt of the prepare step); the originals are
# kept in $W/capture_orig and put back by rerun_capture_collect, or on exit. Failure only logs: the cases
# then run without capture.
function rerun_capture_install() {
    local conf=$1 id=$2 f out
    f=$(rerun_script capture_install)
    {
        echo "mkdir -p \"\$W/capture\" \"\$W/capture_orig\""
        echo "cat > \"\$W/capture.sh\" <<'RERUN_CAPTURE_EOF'"
        rerun_capture_script
        echo "RERUN_CAPTURE_EOF"
        echo "line=\"[ -f \\\"\\\$HOME/$RERUN_NODE_DIR/$RERUN_TAG/capture.sh\\\" ] && . \\\"\\\$HOME/$RERUN_NODE_DIR/$RERUN_TAG/capture.sh\\\"; \""
        cat <<'EOF'
n=0
touch "$W/capture_patched.txt"
while IFS= read -r p; do
    [ -f "$p" ] || continue
    # left patched by a batch that could not put it back: take the committed script first
    if head -1 "$p" | grep -qF "ctp_rerun_work/"; then
        (cd "$(dirname "$p")" && git checkout -q -- "$(basename "$p")") || continue
    fi
    n=$((n + 1))
    cp -p "$p" "$W/capture_orig/$n" || continue
    printf '%s\t%s\n' "$n" "$p" >> "$W/capture_patched.txt"
    # a new file moved into place: a shell still reading the old one is not disturbed
    awk -v l="$line" 'NR == 1 { print l $0; next } { print }' "$W/capture_orig/$n" > "$p.rerun_tmp" &&
        chmod --reference="$W/capture_orig/$n" "$p.rerun_tmp" && mv -f "$p.rerun_tmp" "$p"
done < "$W/requested_full.txt"
echo "RERUN_CAPTURE_PATCHED=$n"
EOF
    } >> "$f"
    # registered first: a run stopped while the scripts are being patched still puts them back
    rerun_on_exit "$(rerun_capture_restore_cmd "$conf" "$id")"
    out=$(rerun_env_exec "$conf" "$id" "" "$RERUN_REMOTE_TIMEOUT" "$f")
    if ! echo "$out" | grep -q '^RERUN_CAPTURE_PATCHED='; then
        rerun_log "env ${id}: log capture not set up: $(echo "$out" | grep -m1 -iE 'error|denied|no such' | cut -c1-200)"
        return 1
    fi
    rerun_log "env ${id}: log capture on for $(echo "$out" | sed -n 's/^RERUN_CAPTURE_PATCHED=//p') case script(s)"
}

# rerun_capture_restore_lines: node script lines that put the patched case scripts back (idempotent).
function rerun_capture_restore_lines() {
    cat <<'EOF'
if [ -f "$W/capture_patched.txt" ]; then
    : > "$W/capture_patched.left"
    while IFS=$'\t' read -r n p; do
        if [ -f "$W/capture_orig/$n" ] && cp -p "$W/capture_orig/$n" "$p.rerun_tmp" && mv -f "$p.rerun_tmp" "$p"; then
            continue
        fi
        printf '%s\t%s\n' "$n" "$p" >> "$W/capture_patched.left"
    done < "$W/capture_patched.txt"
    mv -f "$W/capture_patched.left" "$W/capture_patched.txt"
    [ -s "$W/capture_patched.txt" ] || rm -f "$W/capture_patched.txt"
fi
EOF
}

# rerun_capture_restore_cmd <conf> <envId>: a command string for rerun_on_exit that puts the scripts back.
function rerun_capture_restore_cmd() {
    local f frozen
    f=$(rerun_script capture_restore_$2)
    rerun_capture_restore_lines >> "$f"
    frozen=$RERUN_FAM_WORK/.capture_env_$2.conf
    (umask 077; cp -f "$1" "$frozen")
    echo "rerun_env_exec '$frozen' '$2' '' 300 '$f'"
}

# rerun_ssh_download <host|local> <port> <user> <password> <file relative to the node user's $HOME> <local dir>
function rerun_ssh_download() {
    local host=$1 port=$2 user=$3 pwd=$4 from=$5 to=$6
    { set +x; } 2>/dev/null
    mkdir -p "$to"
    if [ "$host" == "local" ]; then
        cp -f "$HOME/$from" "$to/" 2>/dev/null
        return
    fi
    rerun_timeout -k 30 "$RERUN_REMOTE_TIMEOUT" run_download -host "$host" -port "${port:-22}" -user "$user" -password "$pwd" \
        -from "$from" -to "$to" < /dev/null > /dev/null 2>&1
}

# rerun_capture_collect <conf> <envId>: put the scripts back and bring the copies to RERUN_FAM_WORK/capture_env_<id>/.
function rerun_capture_collect() {
    local conf=$1 id=$2 f out dir=$RERUN_FAM_WORK/capture_env_$2
    f=$(rerun_script capture_collect)
    rerun_capture_restore_lines >> "$f"
    cat >> "$f" <<'EOF'
[ -d "$W/capture" ] || { echo "RERUN_CAPTURE_NONE"; exit 0; }
(cd "$W" && tar czf capture.tgz capture) && echo "RERUN_CAPTURE_TGZ=$(stat -c %s "$W/capture.tgz")"
EOF
    out=$(rerun_env_exec "$conf" "$id" "" "$RERUN_REMOTE_TIMEOUT" "$f")
    echo "$out" | grep -q '^RERUN_CAPTURE_TGZ=' || return 0
    if [ "$id" == "local" ]; then
        rerun_ssh_download local "" "" "" "$RERUN_NODE_DIR/$RERUN_TAG/capture.tgz" "$dir"
    else
        rerun_ssh_download "$(rerun_env_prop "$conf" "$id" "" ssh.host)" "$(rerun_env_prop "$conf" "$id" "" ssh.port)" \
            "$(rerun_env_prop "$conf" "$id" "" ssh.user)" "$(rerun_env_prop "$conf" "$id" "" ssh.pwd)" \
            "$RERUN_NODE_DIR/$RERUN_TAG/capture.tgz" "$dir"
    fi
    if [ -s "$dir/capture.tgz" ]; then
        # what a case wrote comes back: no links, nothing outside the dir (rerun_capture_publish checks paths too)
        tar xzf "$dir/capture.tgz" -C "$dir" --no-same-owner && rm -f "$dir/capture.tgz"
        find "$dir" -type l -delete 2>/dev/null
    else
        rerun_log "env ${id}: the captured logs could not be copied back"
    fi
}

# rerun_capture_publish <rerunId> <case dir: relative to the node user's $HOME, or absolute>: publish the copies of
# one case under <rerunId>/capture/ (scrubbed like every published log) with INDEX.tsv (how, time, size,
# published file or "-" when the case budget was spent, file name), and print a short listing for the case
# log. Prints nothing when no copy of this case came back.
function rerun_capture_publish() {
    local rid=$1 casedir=${2#./} d cdir="" how t size path name n=0 skipped=0
    for d in "$RERUN_FAM_WORK"/capture_env_*/capture/*/; do
        [ -f "$d/case_dir" ] || continue
        case "$(cat "$d/case_dir")" in
            "$casedir" | */"$casedir") cdir=${d%/}; break ;;
        esac
    done
    [ -n "$cdir" ] || return 0
    mkdir -p "$RERUN_LOG_ROOT/$rid/capture"
    : > "$RERUN_LOG_ROOT/$rid/capture/INDEX.tsv"
    while IFS=$'\t' read -r how t size path name; do
        # the index comes from the node and the case could write to it: a kept file is a plain name under files/
        case "$path" in
            -) ;;
            "" | */* | .*) path=- ;;
        esac
        if [ "$path" != "-" ] && [ -f "$cdir/files/$path" ] && [ ! -L "$cdir/files/$path" ] &&
            rerun_save_log "$cdir/files/$path" "$rid/capture/$path"; then
            n=$((n + 1))
        else
            path=-
            skipped=$((skipped + 1))
        fi
        printf '%s\t%s\t%s\t%s\t%s\n' "$how" "$t" "$size" "$path" "$name" | rerun_scrub_stream >> "$RERUN_LOG_ROOT/$rid/capture/INDEX.tsv"
    done < "$cdir/index"
    echo "${n} file(s) of the cases directory under capture/ (list: capture/INDEX.tsv)$([ $skipped -gt 0 ] && echo ", ${skipped} not kept: the ${RERUN_CAPTURE_CASE_MAX}-byte case budget was spent")"
    awk -F'\t' '{ printf "  %-8s %-12s %10s  %s\n", $2, $1, $3, ($4 == "-" ? "(not kept) " $5 : $4) }' "$RERUN_LOG_ROOT/$rid/capture/INDEX.tsv"
}

#######################################################################################################
# Cores of local runs (sql, sql_by_cci)
#######################################################################################################

# rerun_backup_cores <harness log>: CORE_FILE: lines -> stacks in harness/core_stacks.txt (analyzer.sh),
# core files moved to ../corebackup/rerun_<batch> on this node (never uploaded). Echoes a note when cores exist.
function rerun_backup_cores() {
    local log=$1 backup core n=0 out=$RERUN_FAM_WORK/core_stacks.txt
    grep '^CORE_FILE:' "$log" 2>/dev/null | awk -F ':' '{ print $NF }' | tr -d ' ' > "$RERUN_FAM_WORK/cores.lst"
    [ -s "$RERUN_FAM_WORK/cores.lst" ] || return 0
    mkdir -p "${CTP_HOME}/../corebackup/rerun_${RERUN_TAG}"
    backup=$(cd "${CTP_HOME}/../corebackup/rerun_${RERUN_TAG}" && pwd)
    : > "$out"
    while IFS= read -r core <&3; do
        [ -f "$core" ] || continue
        n=$((n + 1))
        if [ $n -le 5 ]; then
            echo "==================== $core ====================" >> "$out"
            rerun_timeout 600 analyzer.sh "$core" >> "$out" 2>&1 < /dev/null
        fi
        mv -f "$core" "$backup/" 2>/dev/null
    done 3< "$RERUN_FAM_WORK/cores.lst"
    {
        echo "core files moved to ${backup} on ${USER}@$(hostname):"
        sed 's/^/  /' "$RERUN_FAM_WORK/cores.lst"
    } >> "$out"
    rerun_save_log "$out" "harness/core_stacks.txt"
    echo "${n} core file(s) during the rerun, see harness/core_stacks.txt"
}
