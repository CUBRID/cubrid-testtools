#!/bin/bash
# 
# Copyright (c) 2016, Search Solution Corporation? All rights reserved.
#
# Redistribution and use in source and binary forms, with or without 
# modification, are permitted provided that the following conditions are met:
#
#  * Redistributions of source code must retain the above copyright notice, 
#    this list of conditions and the following disclaimer.
#
#  * Redistributions in binary form must reproduce the above copyright 
#    notice, this list of conditions and the following disclaimer in 
#    the documentation and/or other materials provided with the distribution.
#
#  * Neither the name of the copyright holder nor the names of its contributors may be used to endorse or promote products 
#    derived from this software without specific prior written permission.
#
# THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, 
# INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE 
# DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, 
# SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR 
# SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, 
# WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE 
# USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

runonearg=""
find_option=""

function usage
{
    cat <<_END
Usage: $0 [options] <scenario dir>
       Valid options :
        [-t]          : enable TAP output mode
        [-r times]    : retry times. default value: 1
        [-N]          : non-recursive subdir
        [-e file]     : excluded case list file
_END
        exit 1
}

echo "[`date +'%F %T'`] Running... $0 $@" > runall.log

while getopts ":ntr:Re:h" opt; do
  case $opt in
    n)
    runonearg="$runonearg -n"
    ;;
    t)
    runonearg="$runonearg -t"
    ;;
    r)  
    runonearg="$runonearg -t -r $OPTARG"
    ;;
    N)  
    find_option="-maxdepth 1"
    ;;
    e)  
    excludefile=$OPTARG
    ;;
    h)
    usage
    ;;

    *)
    usage
    ;;
  esac
done
shift $(($OPTIND - 1))

if [ $# -lt 1 ];
then
  usage
fi

scenariodir=$1

for ctl in `find $scenariodir $find_option -name "*.ctl"`
do
  echo ctl: $ctl
  if [ -n "$excludefile" ];
  then
    grep -q $(echo $ctl | sed -e 's|.*\(_[0-9]\+_[A-Z].*\)|\1|') $excludefile && skip="true" || skip="false"
  else
    skip="false"
  fi

  if [ $skip != "true" ];
  then
    sh -x ./runone.sh $runonearg $ctl 150 qacsql
    echo "[`date +'%F %T'`] $ctl - $(cat ${ctl%.ctl}.result)" >> runall.log
  fi
done

echo "[`date +'%F %T'`] End" >> runall.log
