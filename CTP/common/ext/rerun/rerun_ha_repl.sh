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
# Rerun runner of the CTP HA replication harness: ha_repl, ha_repl_debug (CUBRIDQA-1632).
# rerun_cdc_repl.sh reuses rerun_repl_family below for the CDC harness (a near-verbatim fork of ha_repl).
#
# How the subset runs (research note rerun-isolation-ha-cdc.md, sections 3 and 4):
#   1. the template is the one run_<fam>.sh uses (<fam>_template_for_<cat>.conf, else <fam>_template.conf), copied
#      into RERUN_WORK - conf/<fam>_runtime.conf (reused by the continue mode) is never written; runAction <fam>.act
#      when present; the harness deploys the build from cubrid_download_url=$url on the kept env(s), with the
#      template's cubrid_install_role (a coverage role is refused); legacy (non-git) builds are refused;
#   2. the controller's checkout ($HOME/cubrid-testcases/sql, or -private for *_ext) is updated with run_git_update
#      under a timeout - the same command as run_<fam>.sh - the branch is verified and the commit recorded;
#   3. the sce_id is the absolute .test path on the original controller; the part after cubrid-testcases[-private]/
#      maps to <base>.sql in this checkout (the .test is a conversion artefact). Only the requested <base>.sql and
#      their known-diff patches <base>.master.slave1.* are copied into RERUN_WORK/<fam>/stage/<repo>/sql/..., outside
#      the checkout (git clean), and scenario points there: Convert turns only those into .test files;
#   4. up to RERUN_MAX_ENVS env pairs whose every host answers over ssh are kept, the others pruned (each kept env
#      is deployed); feedback_type=file, no notice URL, no feedback DB keys, no exclude list,
#      ha_sync_failure_resolve_mode=continue ('stop' exits the JVM, 'wait' blocks on stdin), test_continue_yn=false;
#      one ctp.sh run for the batch under a timeout (the dirty-DB check and deploy can loop forever);
#   5. verdict per case from the console line "[TESTCASE] <abs .test> <ms>ms <env> [OK]|[NOK]"; no line = ERROR.
#      The case log holds the feedback.log entry; the master/slave dumps and the diff of the case are copied too.
#
# Known limits:
#   - the daily run reuses one HA DB across cases (rebuilt only when dirty), the rerun starts on a freshly built
#     one, so a failure caused by state an earlier case left behind may not reproduce;
#   - the env pair differs from the original one (verify_item does not record it);
#   - a replication sync failure can take up to ha_sync_detect_timeout_in_secs per case.

function rerun_ha_repl() {
    rerun_repl_family ha_repl
}

# rerun_repl_family <ha_repl|cdc_repl>
function rerun_repl_family() {
    local fam=$1 repo template conf testcase_path commit stage_root map ncase max envs id sce rel base dir log vfile p

    rerun_init "$fam"
    rerun_precheck_message || return 0

    case "$BUILD_SCENARIOS" in
        ${fam}|${fam}_debug) repo=cubrid-testcases ;;
        ${fam}_ext|${fam}_ext_debug) repo=cubrid-testcases-private ;;
        *)
            rerun_error_all "category ${BUILD_SCENARIOS} is not handled by the ${fam} rerun runner"
            return 0
            ;;
    esac
    if [ -f "${CTP_HOME}/conf/${fam}_template_for_${BUILD_SCENARIOS}.conf" ]; then
        template=${CTP_HOME}/conf/${fam}_template_for_${BUILD_SCENARIOS}.conf
    elif [ -f "${CTP_HOME}/conf/${fam}_template.conf" ]; then
        template=${CTP_HOME}/conf/${fam}_template.conf
    else
        rerun_error_all "no conf/${fam}_template_for_${BUILD_SCENARIOS}.conf nor conf/${fam}_template.conf on ${USER}@$(hostname)"
        return 0
    fi
    conf=$RERUN_FAM_WORK/${fam}_rerun.conf
    cp -f "$template" "$conf"
    rerun_on_exit "rm -f '$conf'"
    rerun_secrets_from_conf "$conf"
    if ! rerun_conf_role_guard "$conf"; then
        rerun_error_all "$RERUN_LAST_ERROR"
        return 0
    fi

    if [ -f "${CTP_HOME}/conf/${fam}.act" ]; then
        rerun_run_action "${fam}.act"
    fi

    # testcases at the branch head (the controller's checkout, as run_<fam>.sh)
    testcase_path=$HOME/$repo/sql
    if ! rerun_git_update_local "$testcase_path"; then
        rerun_error_all "$RERUN_LAST_ERROR"
        return 0
    fi
    commit=$RERUN_GIT_COMMIT
    rerun_set_tc_commit "$commit"

    # stage <base>.sql + known-diff patches of the requested cases
    stage_root=$RERUN_FAM_WORK/stage
    rerun_on_exit "rm -rf '$stage_root'"
    mkdir -p "$stage_root/$repo/sql"
    # the harness prints java.io.File absolute paths: normalise the prefix the verdict lines are matched on
    stage_root=$(cd "$stage_root" && pwd)
    map=$RERUN_FAM_WORK/cases.map
    : > "$map"
    while IFS=$'\t' read -r id sce <&3; do
        sce=${sce%$'\r'}
        rel=${sce#*"/$repo/"}
        if [ "$rel" == "$sce" ] || rerun_bad_path "$rel" || [[ "$rel" != sql/*.test ]]; then
            rerun_verdict "$id" ERROR "" "'${sce}' is not a ${BUILD_SCENARIOS} case path (.../${repo}/sql/.../<name>.test)"
            continue
        fi
        base=${rel%.test}
        if [ ! -f "$HOME/$repo/$base.sql" ]; then
            rerun_verdict "$id" ERROR "" "case not found at ${RERUN_BRANCH} HEAD (${commit:0:10}): no ${base}.sql"
            continue
        fi
        dir=$(dirname "$base")
        if [ ! -f "$stage_root/$repo/$base.sql" ]; then
            mkdir -p "$stage_root/$repo/$dir"
            cp -p "$HOME/$repo/$base.sql" "$stage_root/$repo/$dir/"
            for p in "$HOME/$repo/$base".master.slave1.*; do
                case "$p" in
                    *.temp|*.norm) ;;
                    *) [ -f "$p" ] && cp -p "$p" "$stage_root/$repo/$dir/" ;;
                esac
            done
        fi
        printf '%s\t%s\t%s\n' "$id" "$sce" "$rel" >> "$map"
    done 3< "$RERUN_CASES_FILE"
    if [ ! -s "$map" ]; then
        return 0
    fi
    ncase=$(cut -f3 "$map" | sort -u | wc -l)
    rerun_log "${ncase} case(s) staged in ${stage_root}/${repo}/sql"

    # env pairs
    max=$RERUN_MAX_ENVS
    [ "$ncase" -lt "$max" ] && max=$ncase
    envs=$(rerun_pick_envs "$conf" ha "$max")
    if [ -z "$envs" ]; then
        rerun_error_all "no ${fam} env whose hosts all answer in ${template##*/} (envs: $(rerun_env_ids "$conf" master | tr '\n' ' '))"
        return 0
    fi
    rerun_log "env(s): $(echo $envs)"

    # rerun conf (as run_<fam>.sh, plus the rerun overrides)
    rerun_conf_keep_envs "$conf" $envs
    rerun_conf_drop_keys "$conf" scenario testcase_exclude_from_file ha_sync_failure_resolve_mode test_category \
        testcase_git_branch cubrid_download_url test_rebuild_env_yn
    ini.sh "$conf" scenario "$stage_root/$repo/sql"
    ini.sh "$conf" ha_sync_failure_resolve_mode continue
    # deploy the message's build: with 'no' the harness would test whatever build the HA nodes already have
    ini.sh "$conf" test_rebuild_env_yn true
    ini.sh "$conf" test_category "$BUILD_SCENARIOS"
    ini.sh "$conf" testcase_git_branch "$RERUN_BRANCH"
    ini.sh "$conf" cubrid_download_url "$url"
    if ! rerun_conf_silence "$conf" || ! rerun_conf_assert "$conf" scenario "$stage_root/$repo/sql" ||
        ! rerun_conf_assert "$conf" testcase_exclude_from_file "" || ! rerun_conf_assert "$conf" ha_sync_failure_resolve_mode continue ||
        ! rerun_conf_assert "$conf" cubrid_download_url "$url" || ! rerun_conf_assert "$conf" test_rebuild_env_yn true; then
        rerun_error_all "$RERUN_LAST_ERROR"
        return 0
    fi

    rerun_clean_runtime_logs "$fam"
    log=$RERUN_FAM_WORK/harness.log
    rerun_run_harness "$fam" "$conf" "$log" "${RERUN_HARNESS_TIMEOUT:-72000}"
    rerun_save_log "$log" harness/harness.log
    rerun_save_runtime_logs "$fam"

    # verdicts: "[TESTCASE] <abs .test> <ms>ms <env> [OK]|[NOK]" on the console; FOUND CORE / TIMEOUT from the
    # case's feedback.log header "[NOK] <abs .test> <ms>ms <env> FOUND CORE TIMEOUT" (console lines interleave)
    vfile=$RERUN_FAM_WORK/verdicts.tsv
    p=${CTP_HOME}/result/$fam/current_runtime_logs/feedback.log
    [ -f "$p" ] || p=/dev/null
    grep '^\[TESTCASE\] ' "$log" > "$RERUN_FAM_WORK/console_verdicts.txt"
    awk -v pre="$stage_root/$repo/" '
        FILENAME == ARGV[1] {
            if ($0 ~ /^\[NOK\] /) { if (index($0, " FOUND CORE") > 0) core[$2] = 1; if ($0 ~ / TIMEOUT[ \t]*$/) tmo[$2] = 1 }
            next
        }
        {
            f = $2
            if (index(f, pre) != 1) next
            k = substr(f, length(pre) + 1)
            env = "env " $(NF - 1)
            if ($NF == "[OK]") { st = "PASS"; note = env }
            else if ($NF == "[NOK]") {
                st = "FAIL"; note = env
                if (f in tmo) note = "timeout; " note
                if (f in core) note = "core or fatal error found; " note
            }
            else { st = "ERROR"; note = "unexpected harness line: " $0 }
            print k "\t" st "\t" note
        }' "$p" "$RERUN_FAM_WORK/console_verdicts.txt" > "$RERUN_FAM_WORK/verdicts_raw.tsv"
    cut -f1-3 "$RERUN_FAM_WORK/verdicts_raw.tsv" > "$vfile"

    RERUN_REPL_FAM=$fam
    RERUN_REPL_STAGE=$stage_root/$repo
    rerun_report_cases "$map" "$vfile" rerun_repl_case_log "no verdict: the harness printed no [TESTCASE] line for this case"
}

# rerun_repl_case_log <rerunId> <sce> <key> <status> <note>: feedback.log entry, dumps and diffs of the case.
function rerun_repl_case_log() {
    local key=$3 base f d=${CTP_HOME}/result/$RERUN_REPL_FAM/current_runtime_logs
    base=${key%.test}
    rerun_extract_block "$d/feedback.log" " $RERUN_REPL_STAGE/$key " '^[[](OK|NOK|SKIP_BY_MACRO|SKIP_BY_BUG|UNKNOWN)[]] |^=============' |
        rerun_case_log_section "feedback.log"
    for f in "$RERUN_REPL_STAGE/$base".*; do
        [ -f "$f" ] || continue
        case "$f" in
            *.dump|*.temp|*.diff_*)
                rerun_case_copy "$f"
                echo "copied: ${f##*/}" >> "$RERUN_CASE_LOG"
                ;;
        esac
    done
}
