#!/bin/bash

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

while getopts ":tr:Re:h" opt; do
  case $opt in
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
