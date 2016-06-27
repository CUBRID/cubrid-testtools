#!/bin/bash
#  run scenario and get result
if [ $# -ne 1 ]
then
  echo "Usage: `basename $0` arg1" >&2
  exit 1
fi
function nocase
{
    echo "################################"
    echo "##There is not a scenario file##"
    echo "################################"
    exit 1
}
scenario=`echo $1|tr -d /`
if [ -d $scenario ]
then
  cd $scenario
  if [ -d cases ]
  then
    cd cases 
    if [ -f $scenario.sh ]
    then 
      sh $scenario.sh
    else
      nocase
    fi
  else
    nocase
  fi
  if grep 'NOK' $scenario.result
  then
    echo "##########################################"
    echo "##This scenario has error, Please check!##"
    echo "##########################################"
    #echo "$scenario.sh has error" >> result-error.txt
  else
    echo "################################"
    echo "####         ok             ####"
    echo "################################"
  fi
else
  echo "sorry $scenario is not exists!, Please check!"
fi

