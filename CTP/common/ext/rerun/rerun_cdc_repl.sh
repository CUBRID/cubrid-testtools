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
# Rerun runner of the CTP CDC replication harness: cdc_repl (CUBRIDQA-1632).
#
# The cdc_repl harness is a near-verbatim fork of ha_repl (Main, Dispatch, Convert, CheckDiff and FeedbackFile
# differ only in names), so this runner is rerun_repl_family of rerun_ha_repl.sh with the cdc_repl names:
# template cdc_repl_template_for_<cat>.conf / cdc_repl_template.conf (copied into RERUN_WORK; cdc_repl_runtime.conf
# is never written), runAction cdc_repl.act, update of $HOME/cubrid-testcases/sql, staging of the requested
# <base>.sql (+ <base>.master.slave1.* patches) whose path follows cubrid-testcases/ in the sce_id (an absolute .test
# path on the original controller), scenario set explicitly (run_cdc_repl.sh leaves the template's), up to
# RERUN_MAX_ENVS master/target pairs kept, feedback_type=file without notice URL or feedback DB, no exclude list,
# one ctp.sh cdc_repl run under a timeout, verdict from "[TESTCASE] <abs .test> <ms>ms <env> [OK]|[NOK]".
#
# Known limits: as rerun_ha_repl.sh - the rerun builds the master/target DBs and cdc_test_helper afresh, while the
# daily run reuses them across cases, so a failure caused by an earlier case may not reproduce; the env pair differs
# from the original one.

source "${BASH_SOURCE[0]%/*}/rerun_ha_repl.sh"

function rerun_cdc_repl() {
    rerun_repl_family cdc_repl
}
