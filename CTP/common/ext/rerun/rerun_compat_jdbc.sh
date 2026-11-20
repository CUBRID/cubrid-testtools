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
# Rerun runner of the JDBC compat runs: jdbc_sql_<ver>_D|S64, jdbc_medium_<ver>_D|S64 and the sql_ext ones
# (CUBRIDQA-1753). A compat message pairs this build (BUILD_*) with another one (COMPAT_BUILD_*), and the suffix of
# its category (COMPAT_TEST_CATAGORY) says which one is the server: _D runs this build's server with the other
# build's JDBC driver, _S64 the other build's server with this build's driver. (The SERVER_64 queue's
# compat_jdbc_S64 messages carry the _D categories, the DRIVER queue's compat_jdbc_D ones the _S64.)
#
# It does what run_compat_jdbc.sh run_sql does, then the sql family's staged run (rerun_sql_run_staged):
#   1. runAction jdbc_compat.act only when conf/jdbc_comapt.act exists (the name the regular runner checks);
#   2. run_cubrid_install -s url com_url (_D) or -d url com_url (_S64); cubrid_rel must show the server build;
#   3. the template: conf/sql_template_for_<BUILD_SCENARIOS>.conf, else conf/sql_template.conf, or MKEY_CONFIG
#      under the regular runner's rules (by scenario, suffix and server version);
#   4. the category's exclude list and patch (get_best_version_for_exclude_patch_file's choice) from
#      cubrid-testcases/<scenario>/config/daily_regression_test_exclude_list_compatibility on
#      BUILD_SCENARIO_BRANCH_GIT, as of the build like the regular run;
#   5. the testcases: _D on BUILD_SCENARIO_BRANCH_GIT as of the build, _S64 on COMPAT_BUILD_SCENARIO_BRANCH_GIT (the
#      server's release branch) at its head, as the regular run; the patch is applied to that checkout as the
#      regular run does (the next run_git_update resets it), and a case of the exclude list is an ERROR;
#   6. the requested cases only, with test_category = COMPAT_TEST_CATAGORY (rerun_sql.sh's steps 3-6).
# A server older than 10.0 ran the legacy harness (cqt, run_sql_legacy): it is not rerun.

source "${BASH_SOURCE[0]%/*}/rerun_sql.sh"

function rerun_compat_jdbc() {
    local cat=$COMPAT_TEST_CATAGORY kind ctp_type repo scen server driver engine_version template conf role rc
    local cfg_dir repo_dir commit list_dir name server_pkg driver_pkg patch_rc=0

    RERUN_CATEGORY=$cat
    rerun_init compat_jdbc
    rerun_precheck_message || return 0

    # what the regular runner would run (run_compat_jdbc.sh run_sql)
    kind=${cat##*_}
    if ! [[ "$cat" =~ ^jdbc_[a-z_]+_[0-9][0-9.]*_(D|S64)$ ]]; then
        rerun_error_all "COMPAT_TEST_CATAGORY '${cat}' is not a JDBC compat category (jdbc_<scenario>_<version>_D or _S64)"
        return 0
    fi
    case "$COMPAT_BUILD_SCENARIOS" in
        medium) ctp_type=medium; repo=cubrid-testcases; scen=medium ;;
        sql) ctp_type=sql; repo=cubrid-testcases; scen=sql ;;
        sql_ext) ctp_type=sql; repo=cubrid-testcases-private; scen=sql ;;
        *)
            rerun_error_all "COMPAT_BUILD_SCENARIOS '${COMPAT_BUILD_SCENARIOS}' is not one the compat runner runs (sql, medium, sql_ext)"
            return 0
            ;;
    esac
    if [ -z "$com_url" ]; then
        rerun_error_all "the original message has no build url of the other build (COMPAT_BUILD_URLS)"
        return 0
    fi
    if [ "$kind" == "D" ]; then
        role=-s; server=$BUILD_ID; driver=$COMPAT_BUILD_ID; server_pkg=${url##*/}; driver_pkg=${com_url##*/}
    else
        role=-d; server=$COMPAT_BUILD_ID; driver=$BUILD_ID; server_pkg=${com_url##*/}; driver_pkg=${url##*/}
        if [ -z "$COMPAT_BUILD_SCENARIO_BRANCH_GIT" ]; then
            rerun_error_all "the original message has no COMPAT_BUILD_SCENARIO_BRANCH_GIT, the testcase branch of the ${server} server"
            return 0
        fi
    fi
    engine_version=$(echo "$server" | awk -F '.' '{print $1 $2}')
    if ! [[ "$engine_version" =~ ^[0-9]+$ ]]; then
        rerun_error_all "cannot read the server version of '${server}'"
        return 0
    fi
    if [ "$engine_version" -lt 100 ]; then
        rerun_error_all "the server build ${server} is older than 10.0: its compat run used the legacy harness (cqt), which rerun does not support"
        return 0
    fi
    template=$(rerun_compat_jdbc_template "$engine_version")
    if [ ! -f "$template" ]; then
        rerun_error_all "configuration template conf/${template##*/} not found on ${USER}@$(hostname)"
        return 0
    fi

    # STEP 1: clean, install the pair (as run_compat_jdbc.sh clean and install_cubrid)
    if [ -f "${CTP_HOME}/conf/jdbc_comapt.act" ]; then
        rerun_run_action jdbc_compat.act
    fi
    rerun_step install "$RERUN_INSTALL_TIMEOUT" run_cubrid_install $role "$url" "$com_url"
    rc=$?
    if [ $rc -eq 124 -o $rc -eq 137 ]; then
        rerun_error_all "build install timed out after ${RERUN_INSTALL_TIMEOUT}s"
        return 0
    fi
    if ! rerun_check_installed "$server" "$server_pkg"; then
        rerun_error_all "$RERUN_LAST_ERROR"
        return 0
    fi
    # util_compat_test.sh links the driver build's jar here; without it every case would fail for no visible reason
    if [ ! -s "$CUBRID/jdbc/cubrid_jdbc.jar" ]; then
        rerun_error_all "driver install failed: ${CUBRID}/jdbc/cubrid_jdbc.jar is missing or empty after installing ${driver_pkg} (see setup/install.log)"
        return 0
    fi

    conf=$RERUN_FAM_WORK/${RERUN_FAMILY}_rerun.conf
    cp -f "$template" "$conf"
    rerun_on_exit "rm -f '$conf'"
    rerun_secrets_from_conf "$conf"

    # STEP 2: the exclude list and patch, from cubrid-testcases on this build's branch as of the build
    cfg_dir=$(cd "$HOME/cubrid-testcases" 2>/dev/null && pwd)
    if [ -z "$cfg_dir" ]; then
        rerun_error_all "testcase checkout $HOME/cubrid-testcases (the compat exclude lists) does not exist on ${USER}@$(hostname)"
        return 0
    fi
    if ! rerun_git_update_local "$cfg_dir"; then
        rerun_error_all "$RERUN_LAST_ERROR"
        return 0
    fi
    commit=$RERUN_GIT_COMMIT
    list_dir=$cfg_dir/$scen/config/daily_regression_test_exclude_list_compatibility
    rerun_compat_jdbc_lists "$list_dir" "$cat"
    RERUN_SQL_EXCLUDED=$RERUN_FAM_WORK/excluded.lst
    : > "$RERUN_SQL_EXCLUDED"
    RERUN_SQL_EXCLUDED_NAME=${RERUN_COMPAT_EXCLUDE##*/}
    if [ -s "$RERUN_COMPAT_EXCLUDE" ]; then
        sed 's/scenario\///g' "$RERUN_COMPAT_EXCLUDE" | grep "^${scen}" > "$RERUN_SQL_EXCLUDED"
        rerun_save_log "$RERUN_COMPAT_EXCLUDE" "setup/${RERUN_SQL_EXCLUDED_NAME}.txt"
        rerun_log "exclude list ${RERUN_SQL_EXCLUDED_NAME}: $(grep -c . "$RERUN_SQL_EXCLUDED") path(s) of ${scen}"
    else
        rerun_log "no exclude list for ${cat} in ${list_dir#$cfg_dir/}"
    fi
    if [ -s "$RERUN_COMPAT_PATCH" ]; then
        cp -f "$RERUN_COMPAT_PATCH" "$RERUN_FAM_WORK/compat.patch"
        rerun_save_log "$RERUN_COMPAT_PATCH" "setup/${RERUN_COMPAT_PATCH##*/}.txt"
    else
        rerun_log "no patch for ${cat} in ${list_dir#$cfg_dir/}/patch_files"
    fi

    # STEP 3: the testcases (_S64: the server's release branch at its head, as the regular run)
    repo_dir=$(cd "${CTP_HOME}/../${repo}" 2>/dev/null && pwd)
    if [ -z "$repo_dir" ]; then
        rerun_error_all "testcase checkout ${CTP_HOME}/../${repo} does not exist on ${USER}@$(hostname)"
        return 0
    fi
    if [ "$kind" == "S64" ]; then
        unset BUILD_COMMIT_TIME
        RERUN_BRANCH=$COMPAT_BUILD_SCENARIO_BRANCH_GIT
        RERUN_LOCAL_BRANCH=${RERUN_BRANCH//\//_}
        RERUN_CHECKOUT_BRANCH=$RERUN_LOCAL_BRANCH
    fi
    if [ "$repo_dir" != "$cfg_dir" ] || [ "$kind" == "S64" ]; then
        if ! rerun_git_update_local "$repo_dir"; then
            rerun_error_all "$RERUN_LAST_ERROR"
            return 0
        fi
        commit=$RERUN_GIT_COMMIT
    fi
    rerun_set_tc_commit "$commit"
    if [ -s "$RERUN_FAM_WORK/compat.patch" ]; then
        # as the regular run: a hunk that does not apply leaves the rest applied and the run goes on
        (cd "$repo_dir/$scen" && patch -p0 -f < "$RERUN_FAM_WORK/compat.patch") > "$RERUN_FAM_WORK/patch.log" 2>&1
        patch_rc=$?
        echo "patch exit status: ${patch_rc}" >> "$RERUN_FAM_WORK/patch.log"
        rerun_save_log "$RERUN_FAM_WORK/patch.log" setup/patch.log
        rerun_log "patch ${RERUN_COMPAT_PATCH##*/} on ${repo}/${scen}: exit status ${patch_rc} (see setup/patch.log)"
    fi

    name=${RERUN_COMPAT_PATCH##*/}
    RERUN_SQL_HOW_NOTE="Compat run ${cat}: server ${server}, JDBC driver of ${driver}, testcases of ${RERUN_BRANCH}"
    if [ -s "$RERUN_FAM_WORK/compat.patch" ] && [ $patch_rc -eq 0 ]; then
        RERUN_SQL_HOW_NOTE+=" with ${name} applied"
    elif [ -s "$RERUN_FAM_WORK/compat.patch" ]; then
        RERUN_SQL_HOW_NOTE+=" with ${name} (did not apply cleanly: exit status ${patch_rc}, see setup/patch.log)"
    fi
    RERUN_SQL_HOW_NOTE+=", as the regular compat run takes them."

    # STEP 4-6: stage the requested cases, run the harness once, report (rerun_sql.sh)
    rerun_sql_run_staged jdbc "$ctp_type" "$repo" "$scen" "$repo_dir" "$commit" "$conf" "$cat"
}

# rerun_compat_jdbc_template <server version digits>: the template run_compat_jdbc.sh run_sql copies.
function rerun_compat_jdbc_template() {
    local engine_version=$1 template arr
    template=${CTP_HOME}/conf/sql_template_for_${BUILD_SCENARIOS}.conf
    [ -f "$template" ] || template=${CTP_HOME}/conf/sql_template.conf
    [ -z "$MKEY_CONFIG" ] && { echo "$template"; return 0; }
    arr=($(echo "$COMPAT_TEST_CATAGORY" | sed 's/ext_//g' | sed 's/_/\n/g'))
    if [ "$COMPAT_BUILD_SCENARIOS" == "medium" ]; then
        [ "$engine_version" -ge 110 ] && template=${CTP_HOME}/conf/${MKEY_CONFIG}
    elif [ "${arr[3]}" == "S64" ]; then
        # jdbc_sql_10.1_S64, jdbc_sql_ext_10.1_S64 and jdbc_sql_10.2_S64 only
        [ "${arr[2]}" == "10.1" -o "${arr[2]}" == "10.2" ] && template=${CTP_HOME}/conf/${MKEY_CONFIG}
    elif [ "$engine_version" -ge 101 -a "$engine_version" -lt 110 ]; then
        template=${CTP_HOME}/conf/${MKEY_CONFIG}
    fi
    echo "$template"
}

# rerun_compat_jdbc_lists <dir> <category>: RERUN_COMPAT_EXCLUDE and RERUN_COMPAT_PATCH, the files
# run_compat_jdbc.sh get_best_version_for_exclude_patch_file picks (the newest patch level of the category's
# version that exists; the path of the .0 / .0 level when none does).
function rerun_compat_jdbc_lists() {
    local dir=$1 cat=$2 prefix n
    if echo "$cat" | grep -q ext; then
        prefix=$(echo "$cat" | awk -F_ '{print $4}')
    else
        prefix=$(echo "$cat" | awk -F_ '{print $3}')
    fi
    if [ -n "$(echo "$prefix" | awk -F. '{print $3}')" ]; then
        RERUN_COMPAT_EXCLUDE=$dir/${cat}_excluded_list
        RERUN_COMPAT_PATCH=$dir/patch_files/${cat}_patch
        return 0
    fi
    for n in $(seq 10 -1 0); do
        RERUN_COMPAT_EXCLUDE=$dir/$(echo "$cat" | sed "s/$prefix/$prefix.$n/g")_excluded_list
        [ -e "$RERUN_COMPAT_EXCLUDE" ] && break
    done
    for n in $(seq 50 -1 0); do
        RERUN_COMPAT_PATCH=$dir/patch_files/$(echo "$cat" | sed "s/$prefix/$prefix.$n/g")_patch
        [ -e "$RERUN_COMPAT_PATCH" ] && break
    done
}
