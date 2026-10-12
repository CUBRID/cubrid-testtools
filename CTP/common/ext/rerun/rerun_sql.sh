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
# Rerun runner of the JDBC SQL harness: sql, sql_debug, medium, medium_debug, sql_ext (CUBRIDQA-1632).
# rerun_sql_by_cci.sh reuses rerun_sql_family below for the CCI harness (sql_by_cci).
#
# How the subset runs (research note rerun-sql-family.md):
#   1. runAction sql_medium_site.act, then run_cubrid_install exactly like run_sql.sh / run_sql_by_cci.sh
#      (role from BUILD_TYPE, shard broker closed) and check that cubrid_rel shows BUILD_ID; the template
#      (MKEY_CONFIG, else conf/sql_local.conf; sql_by_cci: MKEY_CONFIG, else sql_by_cci_template_for_<cat>.conf,
#      else sql_by_cci_template.conf) is copied into RERUN_WORK - conf/sql_runtime.conf is never written;
#   2. the category's repo (../cubrid-testcases or ../cubrid-testcases-private) is updated with run_git_update
#      under a timeout, the branch is checked and the commit recorded;
#   3. only the requested cases are staged - <grp>/cases/<n>.sql (+ <n>.queryPlan) and <grp>/answers/<n>.answer*
#      - under RERUN_WORK/<runner>/stage/<repo>/<sql|medium>/, a path that keeps the /cubrid-testcases[-private]/
#      marker ccqt needs; [sql] scenario points there, data_file stays on the real checkout (medium's
#      mdb.tar.gz), the exclude list is removed, enable_memory_leak is forced off and the cubrid.conf /
#      cubrid_ha.conf parameters are filtered exactly as run_sql.sh does;
#   4. one ctp.sh run for the batch; none of run_sql.sh's STEP 5 uploads (qa_repository/function*,
#      memory_leak, test_error) happens and the SQL harness has no DB feedback and no retry;
#   5. verdicts: summary.xml of the result root (<case> is the sce_id, <result>success|fail</result>, written
#      only for executed cases); sql_by_cci: summary.info ("TestCase: <sce_id><TAB>:OK|NOK:<ms>"). A case
#      missing there is ERROR (not executed or harness aborted); ctp.sh's exit code is ignored.
#
# Known limits:
#   - the batch runs on one fresh database without the cases that ran before it in the daily run, so a
#     failure caused by state an earlier case left (medium mutates mdb) may not reproduce, and a case may
#     fail only because a predecessor it relies on did not run; the order also differs (JDBC sorts, ccqt
#     follows readdir);
#   - MKEY_SPARSECHECKOUT is not applied (it deletes files of the shared checkout; staging does not need it);
#     coverage builds and legacy (non-git) builds are refused;
#   - core files found after the run are stack-analysed into harness/core_stacks.txt and moved to
#     ../corebackup/rerun_<batch>; the note goes into every case message because a core is not tied to a case.

function rerun_sql() {
    rerun_sql_family jdbc
}

function rerun_sql_close_shard_service() {
    local shard_service
    shard_service=$(ini.sh -s "%shard1" "$CUBRID/conf/cubrid_broker.conf" SERVICE)
    if [ "$shard_service" = "ON" ]; then
        ini.sh -s "%shard1" "$CUBRID/conf/cubrid_broker.conf" SERVICE OFF
    fi
}

# rerun_sql_family <jdbc|cci>
function rerun_sql_family() {
    local mode=$1 ctp_type repo scen template conf role commit repo_dir stage_repo stage_root map vfile
    local id sce grp n tmptxt log resdir testlog core_note rc

    if [ "$mode" == "cci" ]; then
        rerun_init sql_by_cci
    else
        rerun_init sql
    fi
    rerun_precheck_message || return 0

    # category -> harness, repo and scenario dir (run_sql.sh / run_sql_by_cci.sh STEP 3)
    case "$mode:$BUILD_SCENARIOS" in
        jdbc:medium|jdbc:medium_debug) ctp_type=medium; repo=cubrid-testcases; scen=medium ;;
        jdbc:sql|jdbc:sql_debug|jdbc:sql-s*) ctp_type=sql; repo=cubrid-testcases; scen=sql ;;
        jdbc:sql_ext|jdbc:sql_ext_debug|jdbc:sql_ext-s*) ctp_type=sql; repo=cubrid-testcases-private; scen=sql ;;
        cci:medium|cci:medium_debug) ctp_type=sql_by_cci; repo=cubrid-testcases; scen=medium ;;
        cci:sql_by_cci|cci:sql_by_cci_debug) ctp_type=sql_by_cci; repo=cubrid-testcases; scen=sql ;;
        cci:sql_by_cci_ext|cci:sql_by_cci_ext_debug) ctp_type=sql_by_cci; repo=cubrid-testcases-private; scen=sql ;;
        *)
            rerun_error_all "category ${BUILD_SCENARIOS} is not handled by the ${RERUN_FAMILY} rerun runner"
            return 0
            ;;
    esac

    if [ "$mode" == "cci" ]; then
        if [ -n "$MKEY_CONFIG" ]; then
            template=${CTP_HOME}/conf/${MKEY_CONFIG}
        elif [ -f "${CTP_HOME}/conf/sql_by_cci_template_for_${BUILD_SCENARIOS}.conf" ]; then
            template=${CTP_HOME}/conf/sql_by_cci_template_for_${BUILD_SCENARIOS}.conf
        else
            template=${CTP_HOME}/conf/sql_by_cci_template.conf
        fi
    else
        template=${CTP_HOME}/conf/${MKEY_CONFIG:-sql_local.conf}
    fi
    if [ ! -f "$template" ]; then
        rerun_error_all "configuration template conf/${template##*/} not found on ${USER}@$(hostname)"
        return 0
    fi

    # STEP 1: clean, install the build (as run_sql.sh STEP 1-2)
    rerun_run_action sql_medium_site.act
    role=""
    if [ "$BUILD_TYPE" != 'general' -a "$BUILD_TYPE" != 'debug' ]; then
        role="--role-${BUILD_TYPE}"
    fi
    rerun_step install "$RERUN_INSTALL_TIMEOUT" run_cubrid_install $role $url $src_url
    rc=$?
    if [ $rc -eq 124 -o $rc -eq 137 ]; then
        rerun_error_all "build install timed out after ${RERUN_INSTALL_TIMEOUT}s"
        return 0
    fi
    if ! rerun_check_installed; then
        rerun_error_all "$RERUN_LAST_ERROR"
        return 0
    fi
    rerun_sql_close_shard_service

    conf=$RERUN_FAM_WORK/${RERUN_FAMILY}_rerun.conf
    cp -f "$template" "$conf"
    rerun_on_exit "rm -f '$conf'"
    rerun_secrets_from_conf "$conf"

    # STEP 2: testcases at the branch head
    repo_dir=$(cd "${CTP_HOME}/../${repo}" 2>/dev/null && pwd)
    if [ -z "$repo_dir" ]; then
        rerun_error_all "testcase checkout ${CTP_HOME}/../${repo} does not exist on ${USER}@$(hostname)"
        return 0
    fi
    if ! rerun_git_update_local "$repo_dir"; then
        rerun_error_all "$RERUN_LAST_ERROR"
        return 0
    fi
    commit=$RERUN_GIT_COMMIT
    rerun_set_tc_commit "$commit"

    # STEP 3: stage the requested cases only
    stage_repo=$RERUN_FAM_WORK/stage/$repo
    stage_root=$stage_repo/$scen
    case "$stage_root/" in
        */scenario/*)
            rerun_error_all "the rerun work dir ${stage_root} contains /scenario/, which breaks the harness case paths"
            return 0
            ;;
    esac
    rerun_on_exit "rm -rf '$RERUN_FAM_WORK/stage'"
    mkdir -p "$stage_root"
    map=$RERUN_FAM_WORK/cases.map
    : > "$map"
    while IFS=$'\t' read -r id sce <&3; do
        sce=${sce%$'\r'}
        grp=${sce%/cases/*}
        n=$(basename "$sce" .sql)
        if rerun_bad_path "$sce" || [[ "$sce" != "$scen"/*/cases/*.sql ]] || [ "$(dirname "$sce")" != "$grp/cases" ]; then
            rerun_verdict "$id" ERROR "" "'${sce}' is not a ${BUILD_SCENARIOS} case path (${scen}/.../cases/<name>.sql)"
            continue
        fi
        if [ ! -f "$repo_dir/$sce" ]; then
            rerun_verdict "$id" ERROR "" "case not found at ${RERUN_BRANCH} HEAD (${commit:0:10})"
            continue
        fi
        if [ "$mode" == "cci" ]; then
            if [ ! -f "$repo_dir/$grp/answers/$n.answer" -a ! -f "$repo_dir/$grp/answers/$n.answer_cci" ]; then
                rerun_verdict "$id" ERROR "" "answer file ${grp}/answers/${n}.answer[_cci] not found at ${RERUN_BRANCH} HEAD (${commit:0:10})"
                continue
            fi
        elif [ ! -f "$repo_dir/$grp/answers/$n.answer" ]; then
            rerun_verdict "$id" ERROR "" "answer file ${grp}/answers/${n}.answer not found at ${RERUN_BRANCH} HEAD (${commit:0:10})"
            continue
        fi
        if [ ! -f "$stage_repo/$sce" ]; then
            mkdir -p "$stage_repo/$grp/cases" "$stage_repo/$grp/answers"
            cp -p "$repo_dir/$sce" "$stage_repo/$grp/cases/"
            if [ -f "$repo_dir/$grp/cases/$n.queryPlan" ]; then
                cp -p "$repo_dir/$grp/cases/$n.queryPlan" "$stage_repo/$grp/cases/"
            fi
            cp -p "$repo_dir/$grp/answers/$n".answer* "$stage_repo/$grp/answers/"
        fi
        printf '%s\t%s\t%s\n' "$id" "$sce" "$sce" >> "$map"
    done 3< "$RERUN_CASES_FILE"
    if [ ! -s "$map" ]; then
        rerun_log "no requested case could be staged; the harness is not started"
        return 0
    fi
    rerun_log "$(wc -l < "$map") case(s) staged in ${stage_root}"

    # STEP 4: rerun conf (as run_sql.sh STEP 3, plus the rerun overrides)
    ini.sh -s sql "$conf" scenario "$stage_root"
    ini.sh -s sql "$conf" data_file '${CTP_HOME}'/../${repo}/${scen}/files
    ini.sh -s sql "$conf" test_category "$BUILD_SCENARIOS"
    ini.sh -s sql "$conf" enable_memory_leak no
    ini.sh -s sql -d testcase_exclude_from_file "$conf"
    tmptxt=$RERUN_FAM_WORK/params.txt
    ini.sh -s "sql/cubrid.conf" "$conf" | util_filter_supported_parameters.sh > "$tmptxt"
    ini.sh -s "sql/cubrid.conf" "$conf" --update-from-file="$tmptxt" --clear-first
    ini.sh -s "sql/cubrid.conf" "$conf" test_mode yes
    ini.sh -s "sql/cubrid_ha.conf" "$conf" | util_filter_supported_parameters.sh > "$tmptxt"
    ini.sh -s "sql/cubrid_ha.conf" "$conf" --update-from-file="$tmptxt" --clear-first
    if [ "$(ini.sh -s sql "$conf" scenario)" != "$stage_root" ] || [ "$(ini.sh -s sql "$conf" enable_memory_leak)" != "no" ] ||
        [ -n "$(ini.sh -s sql "$conf" testcase_exclude_from_file)" ]; then
        rerun_error_all "rerun conf guard failed: scenario / enable_memory_leak / testcase_exclude_from_file not set as required"
        return 0
    fi

    if [ "$mode" == "jdbc" ]; then
        # as run_sql.sh STEP 3-4; cores are moved to this batch's own backup dir below
        export CORE_BACKUP_DIR="${USER}@$(hostname -i)${CTP_HOME}/../corebackup/rerun_${RERUN_TAG}"
        export _JAVA_OPTIONS=-Dfile.encoding=utf8
    fi

    # STEP 5: run the harness once for the batch; nothing is uploaded
    log=$RERUN_FAM_WORK/harness.log
    rerun_run_harness "$ctp_type" "$conf" "$log" "${RERUN_HARNESS_TIMEOUT:-14400}"
    rerun_save_log "$log" harness/harness.log
    testlog=$(grep '^Test Log:' "$log" | tail -1 | cut -d: -f2- | tr -d ' \r')
    [ -n "$testlog" ] && rerun_save_log "$testlog" harness/sql_test.log
    resdir=$(grep '^Test Result Directory' "$log" | tail -1 | awk -F ':' '{print $NF}' | tr -d ' \r')
    if [ -z "$resdir" ]; then
        resdir=$(grep '^Result Root Dir' "$log" | tail -1 | cut -d: -f2- | tr -d ' \r')
    fi
    [ -f "$resdir/summary_info" ] && rerun_save_log "$resdir/summary_info" harness/summary_info
    core_note=$(rerun_backup_cores "$log")

    # STEP 6: verdicts from the harness result files
    vfile=$RERUN_FAM_WORK/verdicts.tsv
    if [ "$mode" == "cci" ]; then
        rerun_sql_cci_verdicts "$resdir/summary.info" "$core_note" > "$vfile"
    else
        rerun_sql_jdbc_verdicts "$resdir/summary.xml" "$core_note" > "$vfile"
    fi
    RERUN_SQL_MODE=$mode
    RERUN_SQL_LOG=$log
    RERUN_SQL_RESDIR=$resdir
    RERUN_SQL_STAGE_REPO=$stage_repo
    if [ -z "$resdir" ]; then
        rerun_report_cases "$map" "$vfile" rerun_sql_case_log "no verdict: the harness printed no result directory (see harness/harness.log)"
    else
        rerun_report_cases "$map" "$vfile" rerun_sql_case_log "no verdict: the case is not in ${resdir##*/}/$([ "$mode" == cci ] && echo summary.info || echo summary.xml) (not executed or harness aborted)"
    fi
}

# rerun_sql_jdbc_verdicts <summary.xml> <note for every case>: "sce<TAB>PASS|FAIL<TAB>note"
function rerun_sql_jdbc_verdicts() {
    [ -f "$1" ] || return 0
    RERUN_AWK_NOTE=$2 awk '
        BEGIN { note = ENVIRON["RERUN_AWK_NOTE"] }
        /<case>/ { c = $0; sub(/.*<case>/, "", c); sub(/<\/case>.*/, "", c) }
        /<result>/ {
            r = $0; sub(/.*<result>/, "", r); sub(/<\/result>.*/, "", r)
            if (r == "success") print c "\tPASS\t" note
            else if (r == "fail") print c "\tFAIL\t" "result differs from the answer" (note == "" ? "" : "; " note)
            else print c "\tERROR\t" "unknown result \"" r "\" in summary.xml" (note == "" ? "" : "; " note)
            c = ""
        }' "$1"
}

# rerun_sql_cci_verdicts <summary.info> <note for every case>: "sce<TAB>PASS|FAIL<TAB>note"
function rerun_sql_cci_verdicts() {
    [ -f "$1" ] || return 0
    RERUN_AWK_NOTE=$2 awk '
        BEGIN { note = ENVIRON["RERUN_AWK_NOTE"] }
        /^TestCase: / {
            line = substr($0, 11); t = index(line, "\t"); if (t == 0) next
            path = substr(line, 1, t - 1); sub(/^[ ]+/, "", path); sub(/[ ]+$/, "", path)
            split(substr(line, t + 1), a, ":")
            if (a[2] == "OK") print path "\tPASS\t" note
            else if (a[2] == "NOK") print path "\tFAIL\t" "result differs from the answer" (note == "" ? "" : "; " note)
            else print path "\tERROR\t" "unknown result \"" a[2] "\" in summary.info" (note == "" ? "" : "; " note)
        }' "$1"
}

# rerun_sql_case_log <rerunId> <sce> <key> <status> <note>: console lines, result, answer, diff, core info.
function rerun_sql_case_log() {
    local sce=$2 status=$4 grp n stage_case result answer
    grp=${sce%/cases/*}
    n=$(basename "$sce" .sql)
    stage_case=$RERUN_SQL_STAGE_REPO/$grp/cases
    grep -F -- "$sce" "$RERUN_SQL_LOG" 2>/dev/null | grep -v 'CONFIG FILE' | rerun_case_log_section "harness console lines"
    result=$stage_case/$n.result
    if [ "$RERUN_SQL_MODE" == "cci" ]; then
        answer=$RERUN_SQL_STAGE_REPO/$grp/answers/$n.answer_cci
        [ -f "$answer" ] || answer=$RERUN_SQL_STAGE_REPO/$grp/answers/$n.answer
    else
        answer=$(awk -v c="$sce" '/<case>/ { x = $0; sub(/.*<case>/, "", x); sub(/<\/case>.*/, "", x); on = (x == c) }
            on && /<answer>/ { a = $0; sub(/.*<answer>/, "", a); sub(/<\/answer>.*/, "", a); print a; exit }' "$RERUN_SQL_RESDIR/summary.xml" 2>/dev/null)
        if [ -n "$answer" ]; then
            answer=$RERUN_SQL_STAGE_REPO/$answer
        else
            answer=$RERUN_SQL_STAGE_REPO/$grp/answers/$n.answer
        fi
    fi
    echo "answer used: ${answer#"$RERUN_SQL_STAGE_REPO"/}" | rerun_case_log_section "answer"
    if [ "$status" == "FAIL" -a -f "$result" -a -f "$answer" ]; then
        diff -u "$answer" "$result" > "$RERUN_FAM_WORK/case.diff" 2>&1
        rerun_case_log_section "diff answer -> result" "$RERUN_FAM_WORK/case.diff"
    fi
    rerun_case_copy "$result" "$n.result"
    rerun_case_copy "$answer" "$(basename "$answer")"
    rerun_case_copy "$RERUN_SQL_STAGE_REPO/$sce" "$n.sql"
    if [ -n "$RERUN_SQL_RESDIR" ] && [ -f "$RERUN_SQL_RESDIR/$grp/$n.err" ]; then
        rerun_case_log_section "core information (.err)" "$RERUN_SQL_RESDIR/$grp/$n.err"
    fi
}
