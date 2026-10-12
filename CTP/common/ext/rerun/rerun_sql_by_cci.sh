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
# Rerun runner of the CTP CCI SQL harness: sql_by_cci (and sql_by_cci_debug / _ext) (CUBRIDQA-1632).
#
# Same flow as rerun_sql.sh (rerun_sql_family cci), with run_sql_by_cci.sh's differences:
#   - template: MKEY_CONFIG, else conf/sql_by_cci_template_for_<cat>.conf, else conf/sql_by_cci_template.conf;
#   - harness: ctp.sh sql_by_cci (ccqt forks execute per case); no exclude filter, no _JAVA_OPTIONS/CORE_BACKUP_DIR;
#   - staging keeps <grp>/answers/<n>.answer and .answer_cci (CCI prefers .answer_cci, compares byte-exact) and the
#     stage path keeps the /cubrid-testcases[-private]/ marker ccqt's getRelativeCasePath needs (and no /scenario/);
#   - verdict per case from the result root's summary.info: "TestCase: <sce_id><TAB>:OK:<ms>" or ":NOK:"; a case
#     missing there is ERROR. ctp.sh's exit code is ignored, nothing is uploaded (no qa_repository/function_cci_for_sql).
#
# Known limits: as rerun_sql.sh - one fresh DB for the batch without the cases that ran before each case in the daily
# run (ccqt runs a directory in readdir order), cores reported per batch, coverage and legacy builds refused.

source "${BASH_SOURCE[0]%/*}/rerun_sql.sh"

function rerun_sql_by_cci() {
    rerun_sql_family cci
}
