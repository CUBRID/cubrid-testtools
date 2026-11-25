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
# Rerun runner of the CCI compat runs: cci_shell_<ver>_D64 and cci_shell_<ver>_S64 (CUBRIDQA-1753). A compat message
# pairs this build (BUILD_*) with another one (COMPAT_BUILD_*): _D64 runs this build's server with the other build's
# CCI driver, _S64 the other build's server with this build's driver. (The SERVER_64 queue's compat_cci_S64 messages
# carry the _D64 categories, the DRIVER_64 queue's compat_cci_D64 ones the _S64.)
#
# It does what run_compat_cci.sh run_shell does, then the shell runner's run (rerun_shell_run) on this host:
#   1. runAction cci_compat.act only when conf/cci_comapt.act exists (the name the regular runner checks);
#   2. run_cubrid_install -s url com_url (_D64) or -d url com_url (_S64) on this host; cubrid_rel must show the server
#      build, and lib/libcascci.so must point at the driver copy util_compat_test.sh puts under cci/driver;
#   3. the category's exclude list (get_best_version_for_exclude_file's choice) from cubrid-testcases-private/
#      interface/CCI/shell/config/daily_regression_test_exclude_list_compatibility on BUILD_SCENARIO_BRANCH_GIT, as
#      of the build like the regular run; a requested case it names is an ERROR (the harness's own matching rule);
#   4. the testcases of $HOME/cubrid-testcases-private/interface/CCI/shell/_20_cci on BUILD_SCENARIO_BRANCH_GIT as of
#      the build (_D64), or on COMPAT_BUILD_SCENARIO_BRANCH_GIT at its head (_S64), as the regular run;
#   5. the shell template with test_category = COMPAT_TEST_CATAGORY and cubrid_download_url empty (the pair is already
#      installed), the cases run locally: a template with test envs is refused.
# A server older than 10.0 ran the legacy harness (run_shell_legacy): it is not rerun, nor a pull request rerun.

source "${BASH_SOURCE[0]%/*}/rerun_shell.sh"

function rerun_compat_cci() {
    local cat=$COMPAT_TEST_CATAGORY kind role server driver server_pkg driver_pkg major rc cfg_dir list_dir drv

    RERUN_CATEGORY=$cat
    rerun_init compat_cci
    rerun_precheck_message || return 0
    if [ -n "$RERUN_TC_REF" ]; then
        rerun_error_all "a pull request rerun of a compat category is not supported"
        return 0
    fi
    if ! [[ "$cat" =~ ^cci_shell_[0-9][0-9.]*_(D64|S64)$ ]]; then
        rerun_error_all "COMPAT_TEST_CATAGORY '${cat}' is not a CCI compat category (cci_shell_<version>_D64 or _S64)"
        return 0
    fi
    if [ -z "$com_url" ]; then
        rerun_error_all "the original message has no build url of the other build (COMPAT_BUILD_URLS)"
        return 0
    fi
    kind=${cat##*_}
    if [ "$kind" == "D64" ]; then
        role=-s; server=$BUILD_ID; driver=$COMPAT_BUILD_ID; server_pkg=${url##*/}; driver_pkg=${com_url##*/}
    else
        role=-d; server=$COMPAT_BUILD_ID; driver=$BUILD_ID; server_pkg=${com_url##*/}; driver_pkg=${url##*/}
        if [ -z "$COMPAT_BUILD_SCENARIO_BRANCH_GIT" ]; then
            rerun_error_all "the original message has no COMPAT_BUILD_SCENARIO_BRANCH_GIT, the testcase branch of the ${server} server"
            return 0
        fi
    fi
    # run_compat_cci.sh is_server_ge_10_0: the first number of the server's version
    major=${server%%.*}
    if ! [[ "$major" =~ ^[0-9]+$ ]]; then
        rerun_error_all "cannot read the server version of '${server}'"
        return 0
    fi
    if [ "$major" -lt 10 ]; then
        rerun_error_all "the server build ${server} is older than 10.0: its compat run used the legacy harness, which rerun does not support"
        return 0
    fi

    # STEP 1: clean, install the pair on this host (as run_compat_cci.sh clean and install_cubrid)
    if [ -f "${CTP_HOME}/conf/cci_comapt.act" ]; then
        rerun_run_action cci_compat.act
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
    drv=$(readlink -f "$CUBRID/lib/libcascci.so" 2>/dev/null)
    case "$drv" in
        "$(readlink -f "$CUBRID")"/cci/driver/*) [ -s "$drv" ] || drv="" ;;
        *) drv="" ;;
    esac
    if [ -z "$drv" ]; then
        rerun_error_all "driver install failed: ${CUBRID}/lib/libcascci.so does not point at the driver of ${driver_pkg} under ${CUBRID}/cci/driver (see setup/install.log)"
        return 0
    fi

    # STEP 2: the exclude list, from cubrid-testcases-private on this build's branch as of the build
    cfg_dir=$(cd "$HOME/cubrid-testcases-private" 2>/dev/null && pwd)
    if [ -z "$cfg_dir" ]; then
        rerun_error_all "testcase checkout $HOME/cubrid-testcases-private does not exist on ${USER}@$(hostname)"
        return 0
    fi
    if ! rerun_git_update_local "$cfg_dir"; then
        rerun_error_all "$RERUN_LAST_ERROR"
        return 0
    fi
    list_dir=$cfg_dir/interface/CCI/shell/config/daily_regression_test_exclude_list_compatibility
    rerun_compat_cci_list "$list_dir" "$cat"
    RERUN_SHELL_EXCLUDED=$RERUN_FAM_WORK/excluded.lst
    RERUN_SHELL_EXCLUDED_NAME=${RERUN_COMPAT_EXCLUDE##*/}
    : > "$RERUN_SHELL_EXCLUDED"
    if [ -s "$RERUN_COMPAT_EXCLUDE" ]; then
        cp -f "$RERUN_COMPAT_EXCLUDE" "$RERUN_SHELL_EXCLUDED"
        rerun_save_log "$RERUN_COMPAT_EXCLUDE" "setup/${RERUN_SHELL_EXCLUDED_NAME}.txt"
        rerun_log "exclude list ${RERUN_SHELL_EXCLUDED_NAME}"
    else
        rerun_log "no exclude list for ${cat} in ${list_dir#$cfg_dir/}"
    fi

    # STEP 3: the testcases (_S64: the server's release branch at its head, as the regular run)
    if [ "$kind" == "S64" ]; then
        unset BUILD_COMMIT_TIME
        RERUN_BRANCH=$COMPAT_BUILD_SCENARIO_BRANCH_GIT
        RERUN_LOCAL_BRANCH=${RERUN_BRANCH//\//_}
        RERUN_CHECKOUT_BRANCH=$RERUN_LOCAL_BRANCH
        rerun_log "testcases of ${RERUN_BRANCH} at its head (the ${server} server's branch, as the regular compat run)"
    fi
    RERUN_SHELL_SCENARIO=$HOME/cubrid-testcases-private/interface/CCI/shell/_20_cci
    RERUN_SHELL_LOCAL_INSTALL=Y
    RERUN_SHELL_HOW_NOTE="Compat run ${cat}: server ${server}, CCI driver of ${driver}, testcases of ${RERUN_BRANCH}, as the regular compat run takes them."

    # STEP 4: the shell runner's run on this host
    rerun_shell_run
}

# rerun_compat_cci_list <dir> <category>: RERUN_COMPAT_EXCLUDE, the file run_compat_cci.sh
# get_best_version_for_exclude_file picks (the newest patch level of the category's version that exists; the path of
# the .0 level when none does).
function rerun_compat_cci_list() {
    local dir=$1 cat=$2 prefix n
    prefix=$(echo "$cat" | awk -F_ '{print $3}')
    if [ -n "$(echo "$prefix" | awk -F. '{print $3}')" ]; then
        RERUN_COMPAT_EXCLUDE=$dir/${cat}_excluded_list
        return 0
    fi
    for n in $(seq 10 -1 0); do
        RERUN_COMPAT_EXCLUDE=$dir/$(echo "$cat" | sed "s/$prefix/$prefix.$n/g")_excluded_list
        [ -e "$RERUN_COMPAT_EXCLUDE" ] && break
    done
}
