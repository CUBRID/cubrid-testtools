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

set -x
export CTP_HOME=$(cd $(dirname $(readlink -f $0))/../..; pwd)
export CURRENT_TOOL_HOME=${CTP_HOME}/common/sched
export PATH=$CTP_HOME/bin:${CTP_HOME}/common/script:$PATH
chmod u+x ${CTP_HOME}/bin/*
chmod u+x ${CTP_HOME}/common/script*
mkdir -p ${CTP_HOME}/common/sched/result/ >/dev/null 2>&1
mkdir -p ${CTP_HOME}/common/sched/status/ >/dev/null 2>&1
cd ${CURRENT_TOOL_HOME}

##variable for script
separator=":"
clsep=
os=`uname`
queueName=""
isDebug=""
fileName=consumerMsg.info
build_no=""
ser_site="china"
hasBuild="false"
branch=""
bit=""
url=""
s_exec=""
q_exec=""
scenario=""
store_branch=""
script_path=""
queue=""
qlist=""
isWin=""
onlyMax=""
withoutSync=0
clspath="$CLASSPATH"
msgId=""
statFile="${CTP_HOME}/common/sched/status/STATUS.TXT"
branchName=master

while [ $# -ne 0 ];do
	case $1 in
		-q)
			shift
		queue=$1
		;;
		-exec)
			shift
		s_exec=$1
		;;
		--debug)
		isDebug='--debug'
		;;
		--only-max)
		onlyMax='--only-max'
		;;
                -s)
                        shift
                ser_site=$1
                ;;
                --without-sync)
                        shift
                withoutSync=1
                ;;
	esac
	shift
done

if [ "$os" == "Linux" -o "$os" = "AIX" ]
then
        separator=":"
else
        separator=";"
fi

if [ "$DEFAULT_BRANCH_NAME" ];then
   branchName=$DEFAULT_BRANCH_NAME
fi

function usage()
{
	exec_name=$(basename $0)
		cat<<EOF
		Usage:$exec_name <-q queue_name> <-exec script> [--debug] [--only-max] [-s china|kor] [--without-sync]
		-q <queue name>      | this option represents queue name what you want to consume
		-exec <script.sh>    | script name you will run
                -s                   | this option represents what build site you will use, such as 'china', 'kor'
		--debug              | this option represents you just want to debug test environement,so message will be dequeued
		--only-max           | this option represents you only need max version in this queue, old ones will be deleted
                --without-sync       | do not update codes
	    
EOF

}

function getTimeStamp()
{
   timeStamp=`date +"%Y-%m-%d %H:%M:%S %Z"`
   echo $timeStamp
}

function consumerTimer()
{
     msgId=$1
     typeName=$2
     timeStamp=`getTimeStamp`
     echo "++++++++ Message-${msgId} $typeName at $timeStamp ++++++++"
     (cd $CURRENT_TOOL_HOME; "$JAVA_HOME/bin/java" -cp "./lib/cubridqa-scheduler.jar" com.navercorp.cubridqa.scheduler.consumer.ConsumerTimer $msgId $typeName)
}


function updateCodes()
{ 
	curDir=`pwd`
    branchName=$1

    changedCount=`cd ${CTP_HOME}; run_grepo_fetch -r cubrid-testtools -b "$branchName" -p "CTP" -e "conf" --check-only . | grep "fetch" | grep CHANGED | wc -l`
        
    if [ "$changedCount" -gt "0" ]
    then
		echo "-------------------------- Begin to update codes -----------------------------"
		default_lc_all=`echo $LC_ALL`
		export LC_ALL=en_us
		echo "Update status: CHANGED " `date`
		commands=`ps -u $USER -o cmd | grep start_consumer.sh | grep -v grep|head -1`
	    echo "#!/bin/bash " > $HOME/.autoUpdate.sh
	    echo "if [ -f ~/.bash_profile ]; " >> $HOME/.autoUpdate.sh
	    echo "then " >> $HOME/.autoUpdate.sh
	    echo "	  . ~/.bash_profile " >> $HOME/.autoUpdate.sh
	    echo "fi " >> $HOME/.autoUpdate.sh
	    echo "cd ${CURRENT_TOOL_HOME}/../script ">> $HOME/.autoUpdate.sh
       	echo "sh stop_consumer.sh" >>$HOME/.autoUpdate.sh
        echo "sh upgrade.sh" >>$HOME/.autoUpdate.sh
        echo "nohup ${commands} 2>&1 >> nohup.out &">>$HOME/.autoUpdate.sh
        echo "cd -" >>$HOME/.autoUpdate.sh
		at -f $HOME/.autoUpdate.sh now+1 minutes 2>&1 | xargs -i echo \#{} >> $HOME/.autoUpdate.sh
		export LC_ALL=$default_lc_all
		echo "----------------------------Done ----------------------------------------------"
		exit
	fi

	cd $curDir
}

function startAgent()
{	
	rm -rf ${CTP_HOME}/common/sched/result/*
	queueName=$1
	(cd ${CURRENT_TOOL_HOME}; "$JAVA_HOME/bin/java" -cp "./lib/cubridqa-scheduler.jar" com.navercorp.cubridqa.scheduler.consumer.ConsumerAgent $queueName $isDebug $onlyMax > ${CTP_HOME}/common/sched/result/$fileName)
    msgId=`cat ${CTP_HOME}/common/sched/result/$fileName |grep MSG_ID|awk -F ':' '{print $2}'`
}

function runagent()
{
	clpth=$1

}

#add "bak" for this functiong becasue it's same as below function
function analyzeMessageInfo_bak()
{
	result=$1
	build_no=`cat $result|awk '/BUILD_ID/'|cut -d ":" -f2`
	branch=`cat $result|awk '/BUILD_SVN_BRANCH/'|cut -d ":" -f2|tr -d " "`		
	bit=`cat $result|awk '/BUILD_BIT/'|cut -d ":" -f2|tr -d " "`		
	scenario=`cat $result|awk '/BUILD_SCENARIOS/'|cut -d ":" -f2|tr -d " "`
	url_cn=`cat $result|grep "BUILD_URLS:"`
	url_kr=`cat $result|grep "BUILD_URLS_KR:"`
	url_cn_1=${url_cn#*:}		
	url_kr_1=${url_kr#*:}		
	store_branch=`cat $result|awk '/BUILD_STORE_ID/'|cut -d ":" -f2|tr -d " "`	

        if [ $ser_site == 'china' ]
        then
            url=$url_cn_1
        else
            url=$url_kr_1
        fi

	fn=${url##*/}
	fexe=${fn##*.}
	if [ "$fexe" == 'zip' ]
	then
		isWin='yes'
	fi			
}


function splitString()
{
	separator=$1
	str=$2
	OLD_IFS="$IFS"
	IFS="$separator"
	arr=($str)
	IFS="$OLD_IFS" 
	echo $arr

}

function acceptParameters()
{
	q=$1
	if [ "$q" ]
	then
        	OLD_IFS="$IFS"
        	IFS=","
        	qlist=($q)
        	IFS="$OLD_IFS"
	else
		usage
		exit 0
	fi	
	
	qexe=$2
	if [ "$qexe" ]
	then
		OLD_IFS="$IFS"
                IFS=","
                q_exec=($qexe)
                IFS="$OLD_IFS"
	else
		usage
		exit 0
	fi	
	if [ ${#qlist[*]} -ne ${#q_exec[*]} ]
	then
		echo "Queue list does not match with command list"
		exit 0
	fi

}

function checkConsumerStarted()

{
    ps -u $USER -o cmd >~/.tmp
    consumerList=`cat ~/.tmp | grep start_consumer.sh | grep "exec" | grep -v grep|wc -l`
    if [ ${consumerList} -ge 2 ];then
	echo
        echo ERROR: duplicated starting. There is an active process.
   	echo
        exit
    fi

    historyAtQId=`cat $HOME/.autoUpdate.sh|grep '#job'|awk '{print $2}'`
    if [ -z "$historyAtQId" ];then
        return
    fi
    atqListCount=`atq|awk '{print $1}'|grep -w ${historyAtQId}|grep -v grep|wc -l`
    if [ ${atqListCount} -ne 0 ]
    then
	 echo
	 echo "ERROR: duplicated starting. There is a job in 'at' queue."
	 echo
	 exit
    fi
}

function analyzeMessageInfo()
{
        result=$1
        build_no=`cat $result|awk '/BUILD_ID/'|cut -d ":" -f2`
        branch=`cat $result|awk '/BUILD_SVN_BRANCH/'|cut -d ":" -f2|tr -d " "`
        bit=`cat $result|awk '/BUILD_BIT/'|cut -d ":" -f2|tr -d " "`
        scenario=`cat $result|awk '/BUILD_SCENARIOS/'|cut -d ":" -f2|tr -d " "`
        url_cn=`cat $result|grep "BUILD_URLS:"`
        url_kr=`cat $result|grep "BUILD_URLS_KR:"`
        url_cn_1=${url_cn#*:}
        url_kr_1=${url_kr#*:}
        store_branch=`cat $result|awk '/BUILD_STORE_ID/'|cut -d ":" -f2|tr -d " "`

        if [ $ser_site == 'china' ]
        then
            url=$url_cn_1
        else
            url=$url_kr_1
        fi

        fn=${url##*/}
        fexe=${fn##*.}
        if [ "$fexe" == 'zip' ]
        then
                isWin='yes'
        fi
}

function hasTestBuild()
{
	analyzeMessageInfo ${CTP_HOME}/common/sched/result/$fileName
  	if [ "$build_no" ] && [ "$scenario" ] && [ "$branch" ] 
	then
		hasBuild="true"
	else
		hasBuild="false"
	fi	
}

##Consumer MAIN##
#get consumer script 
echo "-------------------------- Start Test -----------------------------"
cd ${CURRENT_TOOL_HOME}
acceptParameters $queue $s_exec
checkConsumerStarted

#loop to consume task messages
while [ 1 ]
do
	cd $script_path
        count=0
	#loop queue list
	for x in ${qlist[@]}
	do
		#update client
        if [ $withoutSync -ne 1 ]
		then
 			updateCodes $branchName
		fi
	
		existsMsgId=`cat ${CTP_HOME}/common/sched/status/${x} 2>&1 /dev/null | grep MSG_ID|awk -F ':' '{print $2}'`
		isStartByData=`echo $existsMsgId|grep "[^0-9]"|wc -l`
		if [ "$existsMsgId" -a  ${isStartByData} -gt 0 ]
		then
			consumerTimer ${existsMsgId} "interrupted"
		fi
		
		if [ -f ${CTP_HOME}/common/ext/${q_exec[$count]}_continue.sh ]
		then
			if [ "$existsMsgId" -a ${isStartByData} -gt 0 ];then
				echo "Action: $x , ${q_exec[$count]}_continue.sh, CONTINUE"
			
				(cd ${CTP_HOME}; source ${CTP_HOME}/common/sched/init.sh $ser_site; sh common/ext/${q_exec[$count]}_continue.sh )
			
				echo
				echo "End continue mode test!"
				consumerTimer ${existsMsgId} "stop"
				contimeENDTIME=`getTimeStamp`
				echo "END_CONTINUE_TIME:${contimeENDTIME}"
				echo '' > ${CTP_HOME}/common/sched/status/${x}
				echo
			fi
		elif [ -n "$(type -t ${q_exec[$count]}_continue)" ] && [ "$(type -t ${q_exec[$count]}_continue)" = "function" ];then
			if [ "$existsMsgId" -a ${isStartByData} -gt 0 ];then
				echo "Action: $x, ${q_exec[$count]}_continue(), CONTINUE"
				(cd ${CTP_HOME}; source ${CTP_HOME}/common/sched/init.sh $ser_site; ${q_exec[$count]}_continue)
				
				echo
                                echo "End continue mode test!"
				consumerTimer ${existsMsgId} "stop"
				contimeENDTIME=`getTimeStamp`
				echo "END_CONTINUE_TIME:${contimeENDTIME}"
				echo '' > ${CTP_HOME}/common/sched/status/${x}
				echo
			fi
		fi

		startAgent $x 
		hasTestBuild
		if [ "$isDebug" == "--debug" ]
		then
			echo "-------------------------- Debug Message Information -----------------------------"
			cat ${CTP_HOME}/common/sched/result/$fileName
			echo "----------------------------------------------------------------------------------"
			exit 0
		elif [ "$hasBuild" == "true" ]
		then
			if [ -f ${CTP_HOME}/common/ext/${q_exec[$count]}.sh ] 
			then
				echo "Action: $x , ${q_exec[$count]}.sh, GENERAL"
			
				if [ -f $statFile ]
				then
					rm -f $statFile
				else
					mkdir -p ${CTP_HOME}/common/sched/status
                		fi
					
				touch $statFile
				TestTime=`getTimeStamp`
				echo "QUEUE:${x}" > $statFile
				echo "START_TIME:${TestTime}" >> $statFile
			
				echo
				echo "Log msg id into queue file!"
				echo "MSG_ID:$msgId" > ${CTP_HOME}/common/sched/status/$x
				echo "START_TIME:${TestTime}" >> ${CTP_HOME}/common/sched/status/$x
				echo

				consumerTimer $msgId "start"
			
				(cd ${CTP_HOME}; source ${CTP_HOME}/common/sched/init.sh $ser_site; sh common/ext/${q_exec[$count]}.sh)
			
				consumerTimer $msgId "stop"

				ENDTIME=`getTimeStamp`
				echo 
				echo "Clean msg id from queue file"
				echo '' > ${CTP_HOME}/common/sched/status/$x
				echo "END_TIME:${ENDTIME}"
				echo
			elif [ -n "$(type -t ${q_exec[$count]})" ] && [ "$(type -t ${q_exec[$count]})" = "function" ];then
				echo "Action: $x , ${q_exec[$count]}() function, GENERAL"

                                if [ -f $statFile ]
                                then
                                        rm -f $statFile
                                else
                                        mkdir -p ${CTP_HOME}/common/sched/status
                                fi
	
				
                                touch $statFile
                                TestTime=`getTimeStamp`
                                echo "QUEUE:${x}" > $statFile
                                echo "EXEC FUNCTION:${q_exec[$count]}()" > $statFile
                                echo "START_TIME:${TestTime}" >> $statFile

                                echo
                                echo "Log msg id into queue file!"
                                echo "MSG_ID:$msgId" > ${CTP_HOME}/common/sched/status/$x
                                echo "START_TIME:${TestTime}" >> ${CTP_HOME}/common/sched/status/$x
                                echo

                                consumerTimer $msgId "start"

                                (cd ${CTP_HOME}; source ${CTP_HOME}/common/sched/init.sh $ser_site; ${q_exec[$count]})

                                consumerTimer $msgId "stop"

                                ENDTIME=`getTimeStamp`
                                echo 
                                echo "Clean msg id from queue file"
                                echo '' > ${CTP_HOME}/common/sched/status/$x
                                echo "END_TIME:${ENDTIME}"
                                echo
				
			else
				echo "Please check if your script exists!"
			fi
		else
			echo "No build"
		fi	
 	        let "count=count+1"	
		
		if [ -f $statFile ]
		then
			rm -f $statFile
		fi
	done
	sleep 5 
done

echo "-------------------------- End Test -----------------------------"
