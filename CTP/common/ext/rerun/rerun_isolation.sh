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
# Rerun runner of the CTP isolation harness: isolation, isolation_debug (CUBRIDQA-1632).
#
# How the subset runs (research note rerun-isolation-ha-cdc.md, section 2):
#   1. the template is the one run_isolation.sh uses (isolation_template.conf, else isolation.conf), copied into
#      RERUN_WORK - conf/isolation_runtime.conf (reused by the continue mode) is never written. The harness deploys
#      the build from cubrid_download_url=$url on every kept env, with the template's cubrid_install_role (a
#      coverage role is refused);
#   2. up to RERUN_MAX_ENVS reachable test nodes are kept in template order and the others' env.<id>.* keys pruned
#      (a template without env.* runs locally, as daily);
#   3. cases are discovered and run on the test nodes, so everything happens there: on each kept node the scenario
#      checkout is updated with run_git_update under a timeout, the branch is verified, the commit read (further
#      nodes are pinned to the first node's commit), then the requested cases are COPIED (find -type f skips
#      symlinks) - <n>.ctl, answer/<n>.answer*, optional <n>.sql / <n>.sh - into
#      $HOME/ctp_rerun_stage/<batch>/<repo>/<sce_id>, outside the checkout (run_git_update's git clean would
#      remove them). The prior art staged on the controller, where the nodes cannot see it;
#   4. scenario points at the stage; testcase_update_yn=false, testcase_retry_num=0 (runone -r 1),
#      feedback_type=file, no notice URL, no feedback DB keys, no exclude list, test_continue_yn=false; one
#      ctp.sh run for the batch, under a timeout (the harness loops forever on some deploy/update failures);
#   5. verdict per case from the console line "[TESTCASE] <path> EnvId=<id> [OK]|[NOK]" (one per case, printed
#      after runone's own NOK/OK/core checks); no line = ERROR. The case log holds the feedback.log entry (NOK
#      items and the side-by-side diff), the worker-log section and the node's result/<n>.result and .log.
#
# Known limits:
#   - a case runs on a freshly prepared ctldb (prepare.sh runs when the server is down) instead of the server
#     and DB the earlier cases of the daily run left, so failures caused by an earlier case may not reproduce;
#   - retry is 0 where the daily template retries (up to 3): a PASS means one clean execution, a FAIL may be a
#     flaky case the daily run would have retried;
#   - the node differs from the original one (verify_item does not record it).

function rerun_isolation() {
    local template conf scen_raw map req ncase max envs id c kept="" out f sce n log vfile stage_scen
    local last_err="" found_any=0

    rerun_init isolation
    rerun_precheck_message || return 0

    if [ -f "${CTP_HOME}/conf/isolation_template.conf" ]; then
        template=${CTP_HOME}/conf/isolation_template.conf
    else
        template=${CTP_HOME}/conf/isolation.conf
    fi
    if [ ! -f "$template" ]; then
        rerun_error_all "no conf/isolation_template.conf nor conf/isolation.conf on ${USER}@$(hostname)"
        return 0
    fi
    conf=$RERUN_FAM_WORK/isolation_rerun.conf
    cp -f "$template" "$conf"
    rerun_on_exit "rm -f '$conf'"
    rerun_secrets_from_conf "$conf"
    if ! rerun_conf_role_guard "$conf"; then
        rerun_error_all "$RERUN_LAST_ERROR"
        return 0
    fi
    scen_raw=$(rerun_conf_get "$conf" scenario)
    if [ -z "$scen_raw" ]; then
        rerun_error_all "the template ${template##*/} has no scenario"
        return 0
    fi
    if ! rerun_split_scenario "$scen_raw"; then
        rerun_error_all "$RERUN_LAST_ERROR"
        return 0
    fi

    # sce_id (path in the repo, e.g. isolation/_02_RepeatableRead/catalog/db_class_02.ctl) is the key
    map=$RERUN_FAM_WORK/cases.map
    req=$RERUN_FAM_WORK/requested.txt
    : > "$map"
    while IFS=$'\t' read -r id sce <&3; do
        sce=${sce%$'\r'}
        if rerun_bad_path "$sce" || [[ "$sce" != *.ctl ]] || { [ -n "$RERUN_SCEN_REL" ] && [[ "$sce" != "$RERUN_SCEN_REL"/* ]]; }; then
            rerun_verdict "$id" ERROR "" "'${sce}' is not an isolation case of ${RERUN_REPO_NAME}/${RERUN_SCEN_REL} (<dir>/<name>.ctl)"
            continue
        fi
        printf '%s\t%s\t%s\n' "$id" "$sce" "$sce" >> "$map"
    done 3< "$RERUN_CASES_FILE"
    if [ ! -s "$map" ]; then
        return 0
    fi
    cut -f3 "$map" | sort -u > "$req"
    ncase=$(wc -l < "$req")

    max=$RERUN_MAX_ENVS
    [ "$ncase" -lt "$max" ] && max=$ncase
    envs=$(rerun_pick_envs "$conf" shell "$max")
    if [ -z "$envs" ]; then
        rerun_error_all "no reachable test node in ${template##*/} (envs: $(rerun_env_ids "$conf" | tr '\n' ' '))"
        return 0
    fi
    rerun_log "test node(s): $(echo $envs)"

    # update, pin and stage on every node
    for id in $envs; do
        rerun_on_exit "$(rerun_env_cleanup_cmd "$conf" "$id" "$RERUN_NODE_STAGE/$RERUN_TAG")"
        if ! rerun_env_git_update "$conf" "$id" "$scen_raw" "$RERUN_TC_COMMIT"; then
            last_err=$RERUN_LAST_ERROR
            rerun_log "node ${id} dropped: ${RERUN_LAST_ERROR}"
            continue
        fi
        c=$RERUN_GIT_COMMIT
        [ -z "$RERUN_TC_COMMIT" ] && rerun_set_tc_commit "$c"
        if ! rerun_env_push_file "$conf" "$id" "$req" requested.txt; then
            last_err="cannot copy the case list to node ${id}"
            rerun_log "node ${id} dropped: ${last_err}"
            continue
        fi
        f=$(rerun_script stage)
        echo "d=$RERUN_REPO_RAW" >> "$f"
        echo "S=\$HOME/$RERUN_NODE_STAGE/$RERUN_TAG/$RERUN_REPO_NAME" >> "$f"
        cat >> "$f" <<'EOF'
if [ ! -d "$d" ]; then echo "RERUN_ERROR=testcase checkout $d not found"; exit 0; fi
rm -rf "$S"
mkdir -p "$S"
while IFS= read -r s; do
    [ -n "$s" ] || continue
    if [ ! -f "$d/$s" ]; then echo "RERUN_MISSING $s"; continue; fi
    dir=$(dirname "$s")
    n=$(basename "$s" .ctl)
    mkdir -p "$S/$dir/answer"
    if ! cp -p "$d/$s" "$S/$dir/"; then echo "RERUN_MISSING $s"; continue; fi
    na=0
    for a in "$d/$dir/answer/$n".answer*; do
        if [ -f "$a" ] && cp -p "$a" "$S/$dir/answer/"; then na=$((na + 1)); fi
    done
    for x in sql sh; do
        if [ -f "$d/$dir/$n.$x" ]; then cp -p "$d/$dir/$n.$x" "$S/$dir/"; fi
    done
    if [ $na -eq 0 ] && [ ! -f "$d/$dir/$n.sh" ]; then echo "RERUN_NOANSWER $s"; fi
    echo "RERUN_FOUND $s"
done < "$W/requested.txt"
echo "RERUN_STAGED=$(find "$S" -name "*.ctl" -type f | wc -l)"
EOF
        out=$(rerun_env_exec "$conf" "$id" "" "$RERUN_REMOTE_TIMEOUT" "$f")
        echo "$out" > "$RERUN_FAM_WORK/stage_env_$id.log"
        rerun_save_log "$RERUN_FAM_WORK/stage_env_$id.log" "setup/stage_env_$id.log"
        if echo "$out" | grep -q '^RERUN_ERROR=' || ! echo "$out" | grep -q '^RERUN_STAGED='; then
            last_err="staging failed on node ${id}: $(echo "$out" | grep -m1 -E '^RERUN_ERROR=|^ERROR' | cut -c1-200)"
            rerun_log "node ${id} dropped: ${last_err}"
            continue
        fi
        if [ $found_any -eq 0 ]; then
            echo "$out" | sed -n 's/^RERUN_MISSING //p' > "$RERUN_FAM_WORK/missing.txt"
            echo "$out" | sed -n 's/^RERUN_NOANSWER //p' > "$RERUN_FAM_WORK/noanswer.txt"
            echo "$out" | sed -n 's/^RERUN_FOUND //p' > "$RERUN_FAM_WORK/found.txt"
            found_any=1
        fi
        kept="$kept $id"
    done
    if [ -z "$kept" ]; then
        rerun_error_all "${last_err:-no usable test node}"
        return 0
    fi
    rerun_log "node(s) kept:${kept}; testcase commit ${RERUN_TC_COMMIT}"

    awk -F'\t' 'FILENAME == ARGV[1] { miss[$0] = 1; next } ($3 in miss) { print $1 }' "$RERUN_FAM_WORK/missing.txt" "$map" > "$RERUN_FAM_WORK/missing_ids.txt"
    while IFS= read -r id <&3; do
        rerun_verdict "$id" ERROR "" "case not found at $(rerun_tc_where) (${RERUN_TC_COMMIT:0:10})"
    done 3< "$RERUN_FAM_WORK/missing_ids.txt"
    if [ ! -s "$RERUN_FAM_WORK/found.txt" ]; then
        rerun_log "no requested case exists in the testcases taken; the harness is not started"
        return 0
    fi

    # rerun conf (as run_isolation.sh, plus the rerun overrides)
    stage_scen="\${HOME}/$RERUN_NODE_STAGE/$RERUN_TAG/$RERUN_REPO_NAME${RERUN_SCEN_REL:+/$RERUN_SCEN_REL}"
    if [ "$(echo $kept)" != "local" ]; then
        rerun_conf_keep_envs "$conf" $kept
    fi
    rerun_conf_drop_keys "$conf" scenario testcase_update_yn testcase_exclude_from_file test_category testcase_git_branch cubrid_download_url
    ini.sh "$conf" scenario "$stage_scen"
    ini.sh "$conf" testcase_update_yn false
    ini.sh "$conf" test_category "$BUILD_SCENARIOS"
    ini.sh "$conf" testcase_git_branch "$RERUN_BRANCH"
    ini.sh "$conf" cubrid_download_url "$url"
    if ! rerun_conf_silence "$conf" || ! rerun_conf_assert "$conf" scenario "$stage_scen" ||
        ! rerun_conf_assert "$conf" testcase_update_yn false || ! rerun_conf_assert "$conf" testcase_exclude_from_file "" ||
        ! rerun_conf_assert "$conf" cubrid_download_url "$url"; then
        rerun_error_all "$RERUN_LAST_ERROR"
        return 0
    fi

    rerun_clean_runtime_logs isolation
    log=$RERUN_FAM_WORK/harness.log
    rerun_run_harness isolation "$conf" "$log" "${RERUN_HARNESS_TIMEOUT:-72000}"
    rerun_save_log "$log" harness/harness.log
    rerun_save_runtime_logs isolation

    # verdicts: [TESTCASE] <home-relative path> EnvId=<id> [OK]|[NOK]
    vfile=$RERUN_FAM_WORK/verdicts.tsv
    grep '^\[TESTCASE\] ' "$log" | awk -v repo="$RERUN_REPO_NAME/" -v noans="$RERUN_FAM_WORK/noanswer.txt" '
        BEGIN { while ((getline l < noans) > 0) na[l] = 1; close(noans) }
        {
            p = index($2, repo); if (p == 0) next
            k = substr($2, p + length(repo))
            env = ""; if (match($0, /EnvId=[^ ]+/)) env = substr($0, RSTART, RLENGTH)
            if ($NF == "[OK]") { st = "PASS"; note = env }
            else if ($NF == "[NOK]") { st = "FAIL"; note = ((k in na) ? "no answer file at HEAD; " : "") env }
            else { st = "ERROR"; note = "unexpected harness line: " $0 }
            print k "\t" st "\t" note "\t" $2
        }' > "$RERUN_FAM_WORK/verdicts_raw.tsv"
    cut -f1-3 "$RERUN_FAM_WORK/verdicts_raw.tsv" > "$vfile"
    cut -f1,4 "$RERUN_FAM_WORK/verdicts_raw.tsv" > "$RERUN_FAM_WORK/files.tsv"

    # result/<n>.result and .log of the staged cases, from every node
    for id in $kept; do
        f=$(rerun_script fetch)
        echo "S=\$HOME/$RERUN_NODE_STAGE/$RERUN_TAG" >> "$f"
        cat >> "$f" <<'EOF'
if cd "$S" 2>/dev/null; then
    echo "RERUN_TGZ=$(find . -path '*/result/*' -type f -print | tar czf - -T - 2>/dev/null | base64 -w0)"
fi
EOF
        rerun_env_exec "$conf" "$id" "" "$RERUN_REMOTE_TIMEOUT" "$f" | sed -n 's/^RERUN_TGZ=//p' | base64 -d > "$RERUN_FAM_WORK/results_$id.tgz" 2>/dev/null
        mkdir -p "$RERUN_FAM_WORK/results_$id"
        tar xzf "$RERUN_FAM_WORK/results_$id.tgz" -C "$RERUN_FAM_WORK/results_$id" 2>/dev/null
    done

    RERUN_ISO_FILES=$RERUN_FAM_WORK/files.tsv
    RERUN_ISO_KEPT=$kept
    rerun_report_cases "$map" "$vfile" rerun_isolation_case_log "no verdict: the harness printed no [TESTCASE] line for this case"
}

# rerun_isolation_case_log <rerunId> <sce> <key> <status> <note>
function rerun_isolation_case_log() {
    local sce=$2 fpath id t dir n d=${CTP_HOME}/result/isolation/current_runtime_logs
    fpath=$(awk -F'\t' -v k="$3" '$1 == k { f = $2 } END { print f }' "$RERUN_ISO_FILES")
    if [ -n "$fpath" ]; then
        rerun_extract_block "$d/feedback.log" " $fpath " '^[[](OK|NOK)[]] |^[[]TEST STOP[]]|^=============' |
            rerun_case_log_section "feedback.log (NOK items and diff answer | result)"
        for t in "$d"/test_*.log; do
            [ -f "$t" ] || continue
            grep -qF "[TESTCASE] $fpath" "$t" || continue
            rerun_extract_block "$t" "[TESTCASE] $fpath" '^[[]TESTCASE[]] ' | rerun_case_log_section "worker log ${t##*/} (runone output)"
        done
    fi
    dir=$(dirname "$sce")
    n=$(basename "$sce" .ctl)
    for id in $RERUN_ISO_KEPT; do
        rerun_case_copy "$RERUN_FAM_WORK/results_$id/$RERUN_REPO_NAME/$dir/result/$n.result" "$n.result"
        rerun_case_copy "$RERUN_FAM_WORK/results_$id/$RERUN_REPO_NAME/$dir/result/$n.log" "$n.log"
    done
}
