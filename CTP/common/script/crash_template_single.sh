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

export CTP_HOME=$(cd $(dirname $(readlink -f $0))/../..; pwd)

corepath=""
allinfo=""

corelocation=""
dblocation=""
errloglocation=""

build=`cubrid_rel|grep CUBRID|sed "s/).*//g"|sed 's/.*(//g'`
os=""
aos=`uname -a`
if [ `echo $aos| grep Linux|wc -l` -eq 1 ]
then
    os="Linux"
elif [ `echo $aos| grep CYGWIN_NT|wc -l` -eq 1 ]
then
   os="Windows_NT"
fi
if [ `echo $aos| grep _64|wc -l` -eq 1 ]
then
   os=`echo "${os} 64bit"`
else
   os=`echo "${os} 32bit"`
fi

if [ `echo "$os"|grep Linux|wc -l` -eq 1 ]
then
    ip=`ifconfig|grep "inet addr:"|grep -v "127.0.0.1"|awk -F: '{print $2}'|sed 's/ .*//g'`
else
    ip=`ipconfig|grep "IPv4 Address"|awk -F: '{print $2}'`
fi
testserver="`whoami`@$ip"
password="please_set_password"

function usage
{
    exec_name=$(basename $0)
    cat <<CCQQTT
        valid options:
        -t : generate a template file, you could modify this template.txt
        -f : format the previous generate template.txt
CCQQTT
        exit 1
}

if [ $# -eq 0 ]
then
    usage
fi

function gen_template()
{
echo "##################################################################################"
echo "# the information is generate according to the current machine"
echo "# please modify the incorrect information and complement the required information"
echo "##################################################################################"
echo "Test Build: ${build}" 
echo "Test OS: ${os}"
echo ""
echo "Description:"
echo '#please write your description in the next line, if there is not description, please just omit it'

echo ""
echo "Repro Steps:"
echo "1. sh XXX.sh"
echo "2. case address"
echo "3. write the code where the core is thrown if possible"

echo ""
echo "Call Stack Info:"
echo '#please write your core file path here'
echo "core file path: /home/XXX"

echo ""
echo "Test Server:"
echo "user@IP: ${testserver}"
echo "pwd: ${password}"

echo ""
echo "# you only need to add the information to either 'All Info' or 'Other three (Core/DB-Volume/Error-log)'"
echo "All Info:" 
echo "${testserver}:${allinfo}/home/XXX"
echo "pwd: ${password}"

echo ""
echo "Core Location:" 
echo "${testserver}:${allinfo}/home/XXX"
echo "pwd: ${password}"

echo ""
echo "DB-Volume Location:"
echo "${testserver}:${allinfo}/home/XXX"
echo "pwd: ${password}"

echo ""
echo "Error Log Location:"
echo "${testserver}:${allinfo}/home/XXX"
echo "pwd: ${password}"
}

function format()
{
    sed -i "/^#/d" template.txt
    sed -i "1i {panel}" template.txt
    echo "{panel}" >>template.txt
    sed -i -e "s/^Test Build:/*Test Build:*/" -e "s/^Test OS:/*Test OS:*/" -e "s/^Description:/*Description:*/" template.txt
    sed -i -e "s/^Repro Steps:/*Repro Steps:*/" -e "s/^Call Stack Info:/*Call Stack Info:*/" template.txt
    corepath=`grep '^core file path:' template.txt|awk -F: '{print $2}'`
    echo "{noformat}" >tmp
    sh ${CTP_HOME}/common/script/analyzer.sh ${corepath} >>tmp
    sed -i "/CORE ANALYZER/d" tmp
    sed -i "/HOME/d" tmp
    sed -i -e "/STACK DIGEST/,$ d" -e "/^ *$/ d" tmp
    echo "{noformat}" >>tmp
    sed -i '/Call Stack Info/{
    r tmp
    }' template.txt
    #rm tmp
    sed -i "/^core file path/d" template.txt
    sed -i -e "s/^Test Server:/*Test Server:*/" template.txt
    if [ `sed -n "/All Info:/,/pwd:/p" template.txt|grep "/home/XXX"|wc -l` -eq 0 ]
    then
        sed -i "s/^All Info:/*All Info:*/" template.txt
        sed -i "/^Core Location/,$ d" template.txt
        echo "{panel}" >>template.txt
    else
        sed -i -e "s/^Core Location:/*Core Location:*/" -e "s/^DB-Volume Location:/*DB-Volume Location:*/" -e "s/^Error Log Location:/*Error Log Location:*/" template.txt
        sed -i -e "/^All Info:/,/pwd:/d" template.txt
    fi
}

while [ $# -ne 0 ]; do
        case $1 in
                -h)
                    usage
                    ;;
                -t)
                    gen_template >template.txt
                    echo "please edit template.txt in the current path"
                    echo "if the random generate information is not suitable to the real information, please modify it"
                ;;
                -f)
                    format
                     cat template.txt
                ;;
                *)
                    usage
                ;;
        esac
        shift
done
