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
# Rerun runner of the CTP shell harness: shell, shell_debug, shell_heavy, shell_long, ha_shell, cci, cci_debug
# (CUBRIDQA-1632).
#
# How the subset runs (research note rerun-shell-family.md):
#   1. the template is the one run_shell.sh uses (shell_template_for_<cat>.conf, else shell_template.conf); it is
#      copied into RERUN_WORK - conf/shell_runtime.conf is never written nor reused, and there is no fallback to
#      conf/shell.conf (wrong repo). The build is deployed by the harness from cubrid_download_url=$url with the
#      template's cubrid_install_role, as in run_shell.sh (a coverage role is refused);
#   2. up to RERUN_MAX_ENVS reachable test envs are kept, in template order (main host and ssh.relatedhosts
#      checked over ssh first: one dead env makes the harness QUIT); the others' env.<id>.* keys are pruned. A
#      template without env.* runs locally (cci), as the daily run does;
#   3. on each kept env (cases are discovered and run there, not on the consumer) the scenario checkout is
#      updated with run_git_update under a timeout, the branch is verified and the commit read; every further
#      env is pinned to the first env's commit (git reset --hard), so the batch runs one commit;
#   4. cases run in place (they source ../../common files and init_path helpers): one harness run with the
#      template's scenario and testcase_exclude_from_file = every discovered case except the requested ones
#      (same find as Dispatch, run on the env; the substring match cannot hit a requested case), written to
#      $HOME/ctp_rerun_work/<batch>/exclude.txt on every kept env. testcase_update_yn=false (already updated),
#      testcase_workspace_dir removed (it would copy the cases elsewhere), feedback_type=file, no notice URL, no
#      feedback DB keys, testcase_retry_num=0, test_continue_yn=false, delete_testcase_after_each_execution_yn=false;
#   5. verdict per case from result/shell/current_runtime_logs/test-<cat>.xml (<testcase file=...>: no child =
#      PASS, <failure> = FAIL, <skipped> = ERROR "skipped at HEAD", <error> = ERROR); when the XML has no element
#      (harness killed before it closed the file) the console line "[TESTCASE] <file> EnvId=<id> [OK|NOK]" is used;
#      neither = ERROR. The case log holds the XML element (result items, console output), the worker-log section
#      and the feedback.log entry.
#
# Known limits:
#   - a case runs on a freshly deployed env without the cases that ran before it on that env in the daily run;
#   - the env differs from the original one (verify_item does not record it); cases use the template's
#     testcase_timeout_in_secs (shell_long has none: hours per case) and testcase_exclude_by_macro;
#   - the harness rewrites result/shell/current_runtime_logs (the daily run's live logs) like any shell run;
#   - each run discovers the whole scenario tree, so feedback.log / harness log list thousands of
#     [SKIP_BY_BUG] / "Skipped File(Temp)" entries; they are filtered out of the published copies.

function rerun_shell() {
    local template conf scen_raw map req ncase max envs id c kept="" first_rel="" first_total="" out f
    local sce under base name log xml vfile raw files key st note fattr blk extra last_err=""

    rerun_init shell
    rerun_precheck_message || return 0

    if [ -f "${CTP_HOME}/conf/shell_template_for_${BUILD_SCENARIOS}.conf" ]; then
        template=${CTP_HOME}/conf/shell_template_for_${BUILD_SCENARIOS}.conf
    elif [ -f "${CTP_HOME}/conf/shell_template.conf" ]; then
        template=${CTP_HOME}/conf/shell_template.conf
    else
        rerun_error_all "no conf/shell_template_for_${BUILD_SCENARIOS}.conf nor conf/shell_template.conf on ${USER}@$(hostname)"
        return 0
    fi
    conf=$RERUN_FAM_WORK/shell_rerun.conf
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

    # sce_id -> path under the scenario; the harness case shape is <name>/cases/<name>.sh
    map=$RERUN_FAM_WORK/cases.map
    req=$RERUN_FAM_WORK/requested.txt
    : > "$map"
    while IFS=$'\t' read -r id sce <&3; do
        sce=${sce%$'\r'}
        if [ -n "$RERUN_SCEN_REL" ]; then
            under=${sce#"$RERUN_SCEN_REL"/}
        else
            under=$sce
        fi
        base=$(basename "$sce")
        name=$(basename "$(dirname "$(dirname "$sce")")")
        if rerun_bad_path "$sce" || [ "$under" == "$sce" -a -n "$RERUN_SCEN_REL" ] || [[ "$sce" != */cases/*.sh ]] ||
            [ "$(basename "$(dirname "$sce")")" != "cases" ] || [ "$name.sh" != "$base" ]; then
            rerun_verdict "$id" ERROR "" "'${sce}' is not a ${BUILD_SCENARIOS} case of ${RERUN_REPO_NAME}/${RERUN_SCEN_REL} (<name>/cases/<name>.sh)"
            continue
        fi
        printf '%s\t%s\t%s\n' "$id" "$sce" "$under" >> "$map"
    done 3< "$RERUN_CASES_FILE"
    if [ ! -s "$map" ]; then
        return 0
    fi
    cut -f3 "$map" | sort -u > "$req"
    ncase=$(wc -l < "$req")

    # test envs
    max=$RERUN_MAX_ENVS
    [ "$ncase" -lt "$max" ] && max=$ncase
    envs=$(rerun_pick_envs "$conf" shell "$max")
    if [ -z "$envs" ]; then
        rerun_error_all "no reachable test env in ${template##*/} (envs: $(rerun_env_ids "$conf" | tr '\n' ' '))"
        return 0
    fi
    rerun_log "test env(s): $(echo $envs)"

    # update, pin, and build the complement exclude list on every env
    for id in $envs; do
        rerun_on_exit "$(rerun_env_cleanup_cmd "$conf" "$id")"
        if ! rerun_env_git_update "$conf" "$id" "$scen_raw" "$RERUN_TC_COMMIT"; then
            last_err=$RERUN_LAST_ERROR
            rerun_log "env ${id} dropped: ${RERUN_LAST_ERROR}"
            continue
        fi
        c=$RERUN_GIT_COMMIT
        [ -z "$RERUN_TC_COMMIT" ] && rerun_set_tc_commit "$c"
        if ! rerun_env_push_file "$conf" "$id" "$req" requested.txt; then
            last_err="cannot copy the case list to env ${id}"
            rerun_log "env ${id} dropped: ${last_err}"
            continue
        fi
        f=$(rerun_script prepare)
        echo "d=$scen_raw" >> "$f"
        cat >> "$f" <<'EOF'
home=$(cd "$HOME"; pwd)
sd=$(cd "$d" 2>/dev/null && pwd)
if [ -z "$sd" ]; then echo "RERUN_ERROR=scenario $d not found"; exit 0; fi
case "$sd" in
    "$home") rel=. ;;
    "$home"/*) rel=${sd#"$home"/} ;;
    *) rel=$sd ;;
esac
echo "RERUN_REL=$rel"
cd
find "$rel" -name "*.sh" -type f -print | xargs -i echo {} | awk -F "/" '{ if( $(NF-2)".sh"== $NF) print }' > "$W/all.txt"
awk -v rel="$rel" 'NF > 0 { print rel "/" $0 }' "$W/requested.txt" > "$W/requested_full.txt"
awk 'FILENAME == ARGV[1] { want[$0] = 1; next } !($0 in want)' "$W/requested_full.txt" "$W/all.txt" > "$W/exclude.txt"
awk -v rel="$rel" 'FILENAME == ARGV[1] { have[$0] = 1; next } { print (($0 in have) ? "RERUN_FOUND " : "RERUN_MISSING ") substr($0, length(rel) + 2) }' "$W/all.txt" "$W/requested_full.txt"
echo "RERUN_TOTAL=$(wc -l < "$W/all.txt")"
echo "RERUN_EXCLUDED=$(wc -l < "$W/exclude.txt")"
EOF
        out=$(rerun_env_exec "$conf" "$id" "" "$RERUN_REMOTE_TIMEOUT" "$f")
        echo "$out" > "$RERUN_FAM_WORK/prepare_env_$id.log"
        rerun_save_log "$RERUN_FAM_WORK/prepare_env_$id.log" "setup/prepare_env_$id.log"
        if echo "$out" | grep -q '^RERUN_ERROR=' || ! echo "$out" | grep -q '^RERUN_TOTAL='; then
            last_err="case discovery failed on env ${id}: $(echo "$out" | grep -m1 -E '^RERUN_ERROR=|^ERROR' | cut -c1-200)"
            rerun_log "env ${id} dropped: ${last_err}"
            continue
        fi
        if [ -z "$first_total" ]; then
            first_total=$(echo "$out" | sed -n 's/^RERUN_TOTAL=//p')
            first_rel=$(echo "$out" | sed -n 's/^RERUN_REL=//p')
            echo "$out" | sed -n 's/^RERUN_FOUND //p' > "$RERUN_FAM_WORK/found.txt"
            echo "$out" | sed -n 's/^RERUN_MISSING //p' > "$RERUN_FAM_WORK/missing.txt"
        elif [ "$(echo "$out" | sed -n 's/^RERUN_TOTAL=//p')" != "$first_total" ]; then
            last_err="env ${id} sees a different case tree than the first env"
            rerun_log "env ${id} dropped: ${last_err}"
            continue
        fi
        kept="$kept $id"
    done
    if [ -z "$kept" ]; then
        rerun_error_all "${last_err:-no usable test env}"
        return 0
    fi
    rerun_log "env(s) kept:${kept}; ${first_total} case(s) under ${first_rel}, testcase commit ${RERUN_TC_COMMIT}"

    # requested cases missing at the branch head
    awk -F'\t' 'FILENAME == ARGV[1] { miss[$0] = 1; next } ($3 in miss) { print $1 }' "$RERUN_FAM_WORK/missing.txt" "$map" > "$RERUN_FAM_WORK/missing_ids.txt"
    while IFS= read -r id <&3; do
        rerun_verdict "$id" ERROR "" "case not found at ${RERUN_BRANCH} HEAD (${RERUN_TC_COMMIT:0:10})"
    done 3< "$RERUN_FAM_WORK/missing_ids.txt"
    if [ ! -s "$RERUN_FAM_WORK/found.txt" ]; then
        rerun_log "no requested case exists at the branch head; the harness is not started"
        return 0
    fi

    # rerun conf (as run_shell.sh, plus the rerun overrides)
    if [ "$(echo $kept)" != "local" ]; then
        rerun_conf_keep_envs "$conf" $kept
    fi
    rerun_conf_drop_keys "$conf" testcase_update_yn testcase_exclude_from_file testcase_workspace_dir \
        delete_testcase_after_each_execution_yn test_category testcase_git_branch cubrid_download_url
    ini.sh "$conf" testcase_update_yn false
    ini.sh "$conf" testcase_exclude_from_file '${HOME}'/${RERUN_NODE_DIR}/${RERUN_TAG}/exclude.txt
    ini.sh "$conf" delete_testcase_after_each_execution_yn false
    ini.sh "$conf" test_category "$BUILD_SCENARIOS"
    ini.sh "$conf" testcase_git_branch "$RERUN_BRANCH"
    ini.sh "$conf" cubrid_download_url "$url"
    if ! rerun_conf_silence "$conf" || ! rerun_conf_assert "$conf" testcase_update_yn false ||
        ! rerun_conf_assert "$conf" testcase_exclude_from_file "\${HOME}/${RERUN_NODE_DIR}/${RERUN_TAG}/exclude.txt" ||
        ! rerun_conf_assert "$conf" testcase_workspace_dir "" || ! rerun_conf_assert "$conf" scenario "$scen_raw" ||
        ! rerun_conf_assert "$conf" cubrid_download_url "$url"; then
        rerun_error_all "$RERUN_LAST_ERROR"
        return 0
    fi

    # run once for the batch
    if [ -f "${CTP_HOME}/conf/shell.act" ]; then
        rerun_run_action shell.act
    fi
    rerun_clean_runtime_logs shell
    log=$RERUN_FAM_WORK/harness.log
    rerun_run_harness shell "$conf" "$log" "${RERUN_HARNESS_TIMEOUT:-72000}"
    rerun_save_log "$log" harness/harness.log 'Skipped File[(]Temp[)]: '
    rerun_save_runtime_logs shell '^[[]SKIP_BY_BUG[]] '

    # verdicts: test-<cat>.xml first, the console line as fallback
    xml=${CTP_HOME}/result/shell/current_runtime_logs/test-${BUILD_SCENARIOS}.xml
    raw=$RERUN_FAM_WORK/verdicts_raw.tsv
    vfile=$RERUN_FAM_WORK/verdicts.tsv
    files=$RERUN_FAM_WORK/files.tsv
    grep '^\[TESTCASE\] ' "$log" > "$RERUN_FAM_WORK/console_verdicts.txt"
    rerun_junit_verdicts "$xml" > "$RERUN_FAM_WORK/junit.tsv"
    awk -F'\t' -v under_of="$first_rel/" -v repo_scen="${RERUN_REPO_NAME}/${RERUN_SCEN_REL:+$RERUN_SCEN_REL/}" -v xmlname="${xml##*/}" '
        # case path as discovered on env[0] -> path under the scenario (the case map key)
        function keyof(f,   p) {
            if (index(f, under_of) == 1) return substr(f, length(under_of) + 1)
            p = index(f, repo_scen); return (p > 0) ? substr(f, p + length(repo_scen)) : ""
        }
        FILENAME == ARGV[1] {
            split($0, w, " "); k = keyof(w[2]); if (k == "") next
            env[k] = ""; if (match($0, /EnvId=[^ ]+/)) env[k] = substr($0, RSTART, RLENGTH)
            cst[k] = (index($0, "[NOK]") > 0) ? "FAIL" : ((index($0, "[OK]") > 0) ? "PASS" : "ERROR"); cf[k] = w[2]
            next
        }
        {
            k = keyof($1); if (k == "") next
            jst[k] = $2; jf[k] = $1
        }
        END {
            for (k in cst) if (!(k in jst)) print k "\t" cst[k] "\tverdict from the harness console (" xmlname " has none)" (env[k] == "" ? "" : "; " env[k]) "\t" cf[k]
            for (k in jst) {
                st = jst[k]; note = (k in env) ? env[k] : ""
                if (st == "SKIP") { st = "ERROR"; note = "skipped by the harness at the branch head (testcase_exclude_by_macro)" }
                else if (st == "ERROR") note = "the harness reported an unknown status"
                print k "\t" st "\t" note "\t" jf[k]
            }
        }' "$RERUN_FAM_WORK/console_verdicts.txt" "$RERUN_FAM_WORK/junit.tsv" > "$raw"
    cut -f1-3 "$raw" > "$vfile"
    cut -f1,4 "$raw" > "$files"
    while IFS=$'\t' read -r key st note fattr <&3; do
        [ "$st" == "FAIL" ] || continue
        blk=$(rerun_junit_block "$xml" "$fattr")
        extra=""
        echo "$blk" | grep -q 'NOK timeout' && extra="timeout"
        echo "$blk" | grep -qE 'NOK found core file|NOK found fatal error' && extra="${extra:+$extra, }core file or fatal error found"
        if [ -n "$extra" ]; then
            printf '%s\tFAIL\t%s\n' "$key" "${extra}${note:+; $note}" >> "$vfile"
        fi
    done 3< "$raw"

    RERUN_SHELL_XML=$xml
    RERUN_SHELL_FILES=$files
    RERUN_SHELL_REL=$first_rel
    rerun_report_cases "$map" "$vfile" rerun_shell_case_log "no verdict: neither ${xml##*/} nor the harness console has this case"
}

# rerun_shell_case_log <rerunId> <sce> <key> <status> <note>
function rerun_shell_case_log() {
    local key=$3 fattr t d=${CTP_HOME}/result/shell/current_runtime_logs
    fattr=$(awk -F'\t' -v k="$key" '$1 == k { f = $2 } END { print f }' "$RERUN_SHELL_FILES")
    [ -z "$fattr" ] && fattr=$RERUN_SHELL_REL/$key
    rerun_junit_block "$RERUN_SHELL_XML" "$fattr" | rerun_case_log_section "${RERUN_SHELL_XML##*/} (result items; console output on failure)"
    for t in "$d"/test_*.log; do
        [ -f "$t" ] || continue
        grep -qF "[TESTCASE] $fattr" "$t" || continue
        rerun_extract_block "$t" "[TESTCASE] $fattr" '^[[]TESTCASE[]] ' | rerun_case_log_section "worker log ${t##*/}"
    done
    rerun_extract_block "$d/feedback.log" " $fattr " '^[[](OK|NOK|SKIP_BY_MACRO|SKIP_BY_BUG|UNKNOWN)[]]|^[[]TEST STOP[]]|^=============' |
        rerun_case_log_section "feedback.log"
}
