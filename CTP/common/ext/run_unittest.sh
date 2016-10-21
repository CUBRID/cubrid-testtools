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

role=""
tmplog=""
unittest_config_template=""
unittest_test_conf="${CTP_HOME}/conf/unittest_runtime.conf"


if [ "$BUILD_SCENARIOS" == "unittest_debug" ];then
		role="--role-unittest-debug"
elif [ "$BUILD_SCENARIOS" == "unittest" ];then
		role="--role-unittest-release"
fi

function installBuild()
{
	run_cubrid_install $role $BUILD_URLS
}


function run_unittest()
{
   if [ -f ${CTP_HOME}/conf/unittest_template_for_${BUILD_SCENARIOS}.conf ]; then
      unittest_config_template=${CTP_HOME}/conf/unittest_template_for_${BUILD_SCENARIOS}.conf
   elif [ -f ${CTP_HOME}/conf/unittest_template.conf ]; then
      unittest_config_template=${CTP_HOME}/conf/unittest_template.conf
   fi

   cp -f ${unittest_config_template} ${unittest_test_conf}
   build_id=$BUILD_ID
   category=$BUILD_SCENARIOS
  
   #init and clean log
   tmplog=$CTP_HOME/result/${category}/current_runtime_logs/runtime.log
   if [ -d "$CTP_HOME/result/${category}/current_runtime_logs" ];then
      rm $CTP_HOME/result/${category}/current_runtime_logs/* >/dev/null 2>&1 
   else
      mkdir -p $CTP_HOME/result/${category}/current_runtime_logs
   fi
   
   
   cd $CTP_HOME
   #update configuration file
   ini.sh -u "test_category=$category" $unittest_test_conf
   ini.sh -u "build_id=${build_id}" $unittest_test_conf

   #execute testing
   ctp.sh unittest -c $unittest_test_conf 2>&1 | tee $tmplog
   cd -
}


installBuild

run_unittest

