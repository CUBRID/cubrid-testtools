#!/bin/sh
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
corepath=$1
command=$2
output=$3

coreloc=`file ${corepath}`

if [ `echo $coreloc|grep "cub_admin"|wc -l` -ge 1 ]
then
    coreloc=cub_admin
elif [ `echo $coreloc|grep "cub_server"|wc -l` -ge 1 ]
then
    coreloc=cub_server
elif [ `echo $coreloc|grep "cub_cas"|wc -l` -ge 1 ]
then
    coreloc=cub_cas
elif [ `echo $coreloc|grep "cub_master"|wc -l` -ge 1 ]
then
    coreloc=cub_master
elif [ `echo $coreloc|grep "csql"|wc -l` -ge 1 ]
then
    coreloc=csql
else
    echo "analyze file command failed"
    exit
fi

if [ `echo $coreloc|grep "csql"|wc -l` -ge 1 -o `echo $coreloc|grep "cub_cas"|wc -l` -ge 1 ]
then
    # if csql,cub_cas, then print error Message with 'p *er_Msg'
    sed -i "/^quit/i\p \*er_Msg" ${command}
    gdb -c "$corepath" "$coreloc" -x $command |tee $output
else
    gdb -c "$corepath" "$coreloc" -x $command |tee $output
fi
