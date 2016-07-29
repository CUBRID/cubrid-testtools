#!/bin/bash
set -x
casename=""
casedir=""
curanswer=""

function write_ok()
{
    echo "flag: OK"
    #rm $casedir/result/${casename}.result $casedir/result/${casename}.log
    echo ${casename}":OK." > ${casedir}/${casename}.result
}

function write_nok()
{
    echo "flag: NOK $1"
    echo ${casename}":NOK $1" > ${casedir}/result/${casename}.result
    if [ -f "${curanswer}" ] && [ -f "${casedir}/result/${casename}.log" ]
    then
        diff ${curanswer} ${casedir}/result/${casename}.log -y >>${casedir}/${casename}.result
    fi
}

function format_ctl_result()
{
    if [ -n "$1" ];then
        sed -i '/Success to commit the transaction/d' $1
        sed -i '/Success to rollback the transaction/d' $1
        sed -i '/Transaction index/d' $1
        sed -i '/^QACTL/d' $1
        sed -i '/shutting down/d' $1
        sed -i '/set transaction/d' $1
        sed -i '/^| Ope_no =/,+1d' $1
        sed -i '/;$/d' $1
        sed -i '/^INFO/d' $1
        sed -i '/restart master client/d' $1
        sed -i '/^$/d' $1
        sed -i "s/(.*) has been unilaterally aborted by the system/has been unilaterally aborted by the system/g" $1
	sed -i 's/STATUS: forcefully killing pid [0-9]*/STATUS: forcefully killing pid ?/g' $1
	sed -i 's/key: [0-9]*(OID:/key: ?(OID:/g' $1
    else
        echo "Please input an file name"
    fi
}

function format_ctl_result_oracle()
{
    if [ -n "$1" ];then
        sed -i '/| 0 row affected/d' $1
    else
        echo "Please input an file name"
    fi
}

#format number like 0|123|456
function format_number_output
{
    sed -i 's/\: [0-9]*|[0-9]*|[0-9]*/\: ?/g' $1
}

function usage
{
    cat <<CCQQTT
Usage: sh runone.sh [-t] [-r times] parameter1 parameter2 [parameter3]
       Valid options :
        [-t]          : enable TAP output mode
        [-r times]    : retry times. default value: 1
        parameter1    : case path
        parameter2    : timeout time
	parameter3    : client program [qacsql | qamysql | qaoracle], default qacsql
CCQQTT
        exit 1
}

retry=1
while getopts ":thr:" opt; do
  case $opt in
                t)
                    tap_output="true"
                    ;;
		r)  
		    retry=$OPTARG
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

db=ctldb
casepath=$1
timeout=$2

if [ "$casepath" = "" ];
then
	echo Not found case path parameter. 
	usage;
fi

if [ "$timeout" = "" ];
then
	echo Not found timeout parameter. 
	usage;
fi


if [ $retry -le 0 ];
then
        echo Retry parameter must be greater than 0.
        usage;
fi

if [ "$3" = "" ] || [ "$3" = "qacsql" ];
then
    client=qacsql
    controller=qactl
else
	if [ "$3" = "qamysql" ]
	then 
		client=$3
		controller=qactlm
	else
		if [ "$3" = "qaoracle" ]
		then 
			client=$3
			controller=qactlo
		else
			echo "the client program should be qacsql or qamysql or qaoracle or empty."
			exit
		fi			
	fi			
fi

casedir=`dirname $casepath`
casename=`basename $casepath|sed 's/.ctl//g'`
if [ a`basename $casepath|awk -F. '{print $2}'` != "actl" ]
then
    echo "case name should be *.ctl"
    exit
fi

if [ a`echo $ctlpath` = "a" ]
then
    ctlpath=$(dirname $(readlink -f $0))
fi

function removeCoreAndLog
{
	find $ctlpath $CUBRID $casedir -name "core.*" -type f | grep -v core.log | xargs -i rm -rf {}
	find ~/CUBRID/log -type f -print | xargs -i sh -c 'echo>{}'
}

function checkCoreAndFatalError
{
	find $ctlpath $CUBRID $casedir -name "core.*" -type f | grep -v core.log>temp_log
	core_dump_cnt=`cat temp_log|wc -l`       
        fatal_err_cnt=`grep -r "FATAL ERROR" $CUBRID/log/* | wc -l`
        # 4. check core
        if [ $core_dump_cnt -gt 0 ] ||
           [ $fatal_err_cnt -gt 0 ]
        then
            version=`cubrid_rel|grep CUBRID|sed 's/).*$//g'|sed 's/^.*(//g'`
            errorfile="error_${version}_"`date "+%Y%m%d%H%M%S"`
            backup_dir=~/error_backup/${errorfile}
            mkdir -p $backup_dir
            while read line 
            do
                mv $line ${backup_dir}
            done<temp_log

            # stop service before copy CUBRID
            cubrid service stop
            pkill -u $(whoami) cub
            cp -r ${CUBRID} ${backup_dir}
            
            freadme=$backup_dir/readme.txt
            echo 1.TEST CASE: ${casedir}/${casename}.ctl > $freadme
            echo 2.CUBRID VERSION: `cubrid_rel | grep CUBRID` >> $freadme
            echo 3.TEST DATE: `date` >> $freadme
            echo 4.ENVIRONMENT: >> $freadme
            set >> $freadme
            echo 5.PROCESS >> $freadme
            ps -ef >> $freadme
            echo 6.IPCS >> $freadme
            ipcs >> $freadme
            echo 7.DISK STATUS >> $freadme
            df >> $freadme
            echo 8.LOGGED >> $freadme
            who >> $freadme
            cp ${casedir}/${casename}.ctl $backup_dir
	    cd ~/error_backup
	    tar zcvf ${errorfile}.tar.gz ${errorfile}
	    rm -rf ${errorfile}
	    cd -
	    host_ip=`hostname -i`
	    if [ $core_dump_cnt -gt 0 ]; then
	        out="found core file on host "$host_ip"("$backup_dir")"
	        	        fi
	    if [ $fatal_err_cnt -gt 0 ]; then
	        out="found fatal error file on host "$host_ip"("$backup_dir")"
	    fi
            write_nok "${out}"
            sh $ctlpath/prepare.sh ${client} $db "nolog"
        fi
}

function run_test_case
{
        # 0. backup core generate by others
        removeCoreAndLog

	# 1. setup
	if [ ${client} = "qacsql" ] && [ `cubrid server status|grep $db|wc -l` -eq 0 ]
	then
	    sh $ctlpath/prepare.sh ${client} $db "log"
	fi

	if [ -f $casedir/${casename}.sql ]
	then
	    csql -u public $db -i $casedir/${casename}.sql >/dev/null 
	fi

	#echo `date +"%Y-%m-%d %H:%M:%S"`

	# 2. execute ctl cases
	if [ ! -d $casedir/result ]
	then
	    mkdir $casedir/result
	fi
	START=`date +%s%N`
	$ctlpath/timeout3.sh -t $timeout $ctlpath/$controller $db $casepath $ctlpath/$client 1>$casedir/result/${casename}.result 2>&1
	if [ $? -eq 7 -o $? -eq 143 ]; then
	  # timeout error
	  date >>timeout.log
	  echo "$ctlpath/$controller $db $casepath $ctlpath/$client" >>timeout.log
	  timed_out="yes"
	fi
	END=`date +%s%N`

	elapse=`expr \( $END - $START \) / 1000000`
	echo "elapse: "$elapse
	echo $casepath" "$elapse >> runone.log

	if [ -f $casedir/result/${casename}.result ]
	then
	   cp $casedir/result/${casename}.result $casedir/result/${casename}.log
	   format_ctl_result $casedir/result/${casename}.log
		if [ ${client} = "qaoracle" ]; then
		format_ctl_result_oracle $casedir/result/${casename}.log
		fi 
	   format_number_output $casedir/result/${casename}.log
	fi

	# 3. check
	currentdir=`pwd`
	if [ -f $casedir/${casename}.sh ]
	then
	    cd $casedir
	    sh ${casename}.sh $casedir/result/${casename}.result
	    cd $currentdir
	else
	    # may be there are many answer, so use for loop to do it
	    flag=false
	    curanswer=""
	    for x in `ls $casedir/answer/${casename}.answer*`
	    do
		curanswer=${x}
		if diff ${curanswer} $casedir/result/${casename}.log
		then
		    flag=true
		    break
		fi
	    done

	    if [ $flag = "true" ]
	    then
		if [ "$tap_output" = "true" ]; then
		  echo "1..1" > $casedir/${casename}.t
		  echo "ok 1 - $casedir/${casename}" >> $casedir/${casename}.t
		  echo "  ---" >> $casedir/${casename}.t
		  echo "  duration_ms: $( echo "scale=3; $elapse / 1000" | bc )" >> $casedir/${casename}.t
		  echo "  ..." >> $casedir/${casename}.t
		fi
		write_ok
	    else
		if [ "$tap_output" = "true" ]; then
		  if [ $(wc -l < ${curanswer}) -eq 1 ] || [ $(head -n 1 ${curanswer} | grep -e '^CUBRIDSUS-[0-9]\+') ];  then
		    fline=$(head -n 1 ${curanswer})
		    [ $(expr match "$fline" 'CUBRIDSUS-[0-9]\+') -gt 0 ] && fline="# SKIP $fline"
		    tap_directives=$(expr match "$fline" '\(^#\s*\(SKIP\|TODO\).*\)')
		    error_tag=$(echo "$tap_directives" | tr -d '#')
		  else
		    error_tag="ERROR"
		  fi
		  # make result
		  echo "<html><head><title>CTL case and Result</title></head>" > $casedir/case_result.html
		  echo "<body bgcolor='#ffffff' text='#000000'><table border='1' width='100%'>" >> $casedir/case_result.html
		  echo "<tr><th width='50%'>$casedir/${casename}.ctl</th>" >> $casedir/case_result.html
		  echo "<th width='50%'>$casedir/result/${casename}.result</th></tr>" >> $casedir/case_result.html
		  echo "<tr><td valign='top' width='50%'><div style='overflow:auto; height:500px'><pre>" >> $casedir/case_result.html
		  cat -n $casepath >> $casedir/case_result.html
		  echo "</pre></div></td><td valign='top' width='50%'><div style='overflow:auto; height:500px'><pre>" >> $casedir/case_result.html
		  if [ $(wc -c < $casedir/result/${casename}.result) -gt 1000000 ]; then
		    head -n 100 $casedir/result/${casename}.result >> $casedir/case_result.html
		    echo ".......... Below lines are omitted." >> $casedir/case_result.html
		    echo "Result file is too big(over 1M). Please see the result file in archive" >> $casedir/case_result.html
		  else
		    cat $casedir/result/${casename}.result >> $casedir/case_result.html
		  fi
		  echo "</pre></div></td></tr></table></body></html>" >> $casedir/case_result.html

		  # make diff html
		  if [ $(wc -c < $casedir/result/${casename}.log) -gt 1000000 ]; then
		    resultlog="$casedir/result/${casename}.log2"
		    head -n 100 $casedir/result/${casename}.log > $resultlog
		    echo ".......... Below lines are omitted." >> $resultlog
		    echo "Result file is too big(over 1M). Please see the result file in archive" >> $resultlog
		  else
		    resultlog="$casedir/result/${casename}.log"
		  fi
		  vimdiff -e -s -N -n -c 'colorscheme zellner' -c 'let g:html_no_progress=1' -c 'let g:html_ignore_folding=1' -c 'let g:html_use_css=0' -c TOhtml -c 'w!'"$casedir/${casename}_diff.html" -c 'qa!' ${curanswer} $resultlog > /dev/null
		  echo "1..1" > $casedir/${casename}.t
		  echo "not ok 1 - $casedir/${casename} $tap_directives" >> $casedir/${casename}.t
		  echo "  ---" >> $casedir/${casename}.t
		  echo "  duration_ms: $(echo "scale=3; $elapse / 1000" | bc )" >> $casedir/${casename}.t
		  if [ "$timed_out" = "yes" ]; then
		  echo "  reason: $error_tag - Timeout ($timeout)">> $casedir/${casename}.t
		  else
		  echo "  reason: $error_tag - Not expected answer">> $casedir/${casename}.t
		  fi
		  echo "  extentions:" >> $casedir/${casename}.t
		  echo "   Files:" >> $casedir/${casename}.t
		  echo "    ${casename}_result.html:" >> $casedir/${casename}.t
		  echo "     File-Description: Case and Result" >> $casedir/${casename}.t
		  echo "     File-Name: ${casename}_result.html" >> $casedir/${casename}.t
		  echo "     File-Size: $(wc -c < $casedir/case_result.html)" >> $casedir/${casename}.t
		  echo "     File-Content: $(base64 -w0 $casedir/case_result.html)" >> $casedir/${casename}.t
		  echo "     File-Type: text/html" >> $casedir/${casename}.t
		  echo "    ${casename}_diff.html:" >> $casedir/${casename}.t
		  echo "     File-Description: Side by side Diff" >> $casedir/${casename}.t
		  echo "     File-Name: ${casename}_diff.html" >> $casedir/${casename}.t
		  echo "     File-Size: $(wc -c < $casedir/${casename}_diff.html)" >> $casedir/${casename}.t
		  echo "     File-Content: $(base64 -w0 $casedir/${casename}_diff.html)" >> $casedir/${casename}.t
		  echo "     File-Type: text/html" >> $casedir/${casename}.t
		  echo "  ..." >> $casedir/${casename}.t
		fi
		write_nok 
	    fi
	fi
        
        # 4. check core
        checkCoreAndFatalError

	# 5. cleanup
	pkill -u $(whoami) -9 sleep >/dev/null 2>&1
	sh $ctlpath/clean.sh ${client} $db
}

# main (with retry)
i=0
while ((i < $retry))
do
	#echo retry $i
        echo "Testing $casepath (retry count: ${i})" | tee .test.log
        run_test_case 2>&1 >> .test.log  
        if grep 'flag: OK' .test.log ;
        then
                break;
        fi
        cat .test.log
        let ++i
done

