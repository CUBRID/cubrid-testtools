#!/bin/bash -xe
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

# In test mode (if run_mode variable is set 1), this function creates answer files of each test case in one scenario
# on the contrary, in answer mode(if run_mode is set 0), this function writes the test result in result file
# according to result of comparing with a test result and an answer
set -x


function rexec {
    host_key=$1
    host_opts="-host \"\$${host_key}_IP\" -user \"\$${host_key}_USER\" -password \"$D_DEFAULT_PWD\" -port $D_DEFAULT_PORT" 
    cmd_opts=""
    skip=1
    for item in "$@"; do
        if [ $skip -eq 1 ]; then
            skip=0
            continue
        fi
        cmd_opts=$(echo $cmd_opts \'$item\')
    done
    eval $init_path/../../common/script/run_remote_script $host_opts $cmd_opts
}


function use_cubrid {
    #todo: need improve
    source $HOME/CUBRID_$1/.cubrid.sh
}

function use_cubrid_main {
    #todo: need improve
    source $HOME/CUBRID/.cubrid.sh
}

function r_upload {
    host_key=$1
    host_opts="-host \"\$${host_key}_IP\" -user \"\$${host_key}_USER\" -password \"$D_DEFAULT_PWD\" -port $D_DEFAULT_PORT" 
    cmd_opts=""
    skip=1
    for item in "$@"; do
        if [ $skip -eq 1 ]; then
            skip=0
            continue
        fi
        cmd_opts=$(echo $cmd_opts \'$item\')
    done
    eval $init_path/../../common/script/run_upload $host_opts $cmd_opts
}

function r_download {
    host_key=$1
    host_opts="-host \"\$${host_key}_IP\" -user \"\$${host_key}_USER\" -password \"$D_DEFAULT_PWD\" -port $D_DEFAULT_PORT" 
    cmd_opts=""
    skip=1
    for item in "$@"; do
        if [ $skip -eq 1 ]; then
            skip=0
            continue
        fi
        cmd_opts=$(echo $cmd_opts \'$item\')
    done
    eval $init_path/../../common/script/run_download $host_opts $cmd_opts
}
