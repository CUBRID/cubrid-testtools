#!/bin/bash
pdir="/home/cuiman/dailyqa/trunk/scenario/cc_basic"
cd $pdir
function vicase()
{
    cdir=`cat ${pdir}/curdir`
    cname=`cat ${pdir}/curfile`
    cname=`echo $cname|sed 's/\..*//g'`
    vi ${cdir}/${cname}.ctl
}

function see()
{
    cdir=`cat ${pdir}/curdir`
    cname=`cat ${pdir}/curfile`
    cname=`echo $cname|sed 's/\..*//g'`
    cat ${cdir}/${cname}.ctl
    echo ${cdir}/${cname}.ctl
}

function result()
{
    cdir=`cat ${pdir}/curdir`
    cname=`cat ${pdir}/curfile`
    cname=`echo $cname|sed 's/\..*//g'`
    cat ${cdir}/result/${cname}.result
    echo "${cdir}/result/${cname}.result"
}

function answer()
{
    cdir=`cat ${pdir}/curdir`
    cname=`cat ${pdir}/curfile`
    cname=`echo $cname|sed 's/\..*//g'`
    cat ${cdir}/answer/${cname}.answer
    echo "${cdir}/answer/${cname}.answer"
}

function log()
{
    cdir=`cat ${pdir}/curdir`
    cname=`cat ${pdir}/curfile`
    cname=`echo $cname|sed 's/\..*//g'`
    cat ${cdir}/result/${cname}.log
    echo "${cdir}/result/${cname}.log"
}

function run()
{
    cdir=`cat ${pdir}/curdir`
    cname=`cat ${pdir}/curfile`
    cname=`echo $cname|sed 's/\..*//g'`
    
    if [ -f ${cdir}/result/${cname}.result ]
    then
        cp ${cdir}/result/${cname}.result ${cdir}/result/${cname}.result_v1
    fi  
    
    if [ -f ${cdir}/result/${cname}.log ]
    then
        cp ${cdir}/result/${cname}.log ${cdir}/result/${cname}.log_v1
    fi

    cd $ctlpath
    echo "run ${cdir}/${cname}.ctl"
    sh runone.sh ${cdir}/${cname}.ctl 60 qacsql
    
    echo "--------------------------------------------------------------------------------------------"
    if diff ${cdir}/result/${cname}.log ${cdir}/result/${cname}.log_v1 >/dev/null
    then
        echo success!!!
    else
        diff ${cdir}/result/${cname}.log ${cdir}/result/${cname}.log_v1 -y
    fi
    echo "--------------------------------------------------------------------------------------------"

    cd $cdir
}

function runmysql()
{
    cdir=`cat ${pdir}/curdir`
    cname=`cat ${pdir}/curfile`
    cname=`echo $cname|sed 's/\..*//g'`
    #dir=`echo $dir|sed 's/mysql/cubrid/g'|sed 's;/result/.*$;;g'`
    #dir=${pdir}/${dir}
    if [ -f ${cdir}/result/${cname}.result ]
    then
        cp ${cdir}/result/${cname}.result ${cdir}/result/${cname}.result_v1
    fi

    if [ -f ${cdir}/result/${cname}.log ]
    then
        cp ${cdir}/result/${cname}.log ${cdir}/result/${cname}.log_v1
    fi

    cd $ctlpath
    sh runone.sh ${cdir}/${cname}.ctl 60 qamysql

    echo "--------------------------------------------------------------------------------------------"
    sed -i "/Q U E R Y   R E S U L T S/,/rows selected/ s/'//g" ${cdir}/result/${cname}.log_v1
    if diff ${cdir}/result/${cname}.log ${cdir}/result/${cname}.log_v1 -b
    then
        echo success!!!
    else
        diff ${cdir}/result/${cname}.log ${cdir}/result/${cname}.log_v1 -y -b
    fi
    echo "--------------------------------------------------------------------------------------------"
}

function runorcl()
{
    cdir=`cat ${pdir}/curdir`
    cname=`cat ${pdir}/curfile`
    cname=`echo $cname|sed 's/\..*//g'`
    
    if [ -f ${cdir}/result/${cname}.result ]
    then
        cp ${cdir}/result/${cname}.result ${cdir}/result/${cname}.result_v1
    fi

    if [ -f ${cdir}/result/${cname}.log ]
    then
        cp ${cdir}/result/${cname}.log ${cdir}/result/${cname}.log_v1
    fi

    cd $ctlpath
    sh runone.sh ${cdir}/${cname}.ctl 60 qaoracle
   
    echo "--------------------------------------------------------------------------------------------"
    sed -i "/Q U E R Y   R E S U L T S/,/rows selected/ s/'//g" ${cdir}/result/${cname}.log_v1
    if diff ${cdir}/result/${cname}.log ${cdir}/result/${cname}.log_v1 -b
    then
        echo success!!!
    else
        diff ${cdir}/result/${cname}.log ${cdir}/result/${cname}.log_v1 -y -b
    fi
    echo "--------------------------------------------------------------------------------------------"
    cd $cdir
}

function curdir()
{
    echo $1 >${pdir}/curdir
    #export $curdir
}

function reg()
{
 if [ `echo $1|grep '.ctl'|wc -l` -eq 1 ]
 then
     p=`cat ${pdir}/curdir`
     if [ ! -d $p ]
     then
         echo "current dir is not right"
         return
     fi
     echo $1 >${pdir}/curfile
 fi
 echo "${p}/$1"
}

function add()
{
    cdir=`cat ${pdir}/curdir`
    cname=`cat ${pdir}/curfile`
    cname=`echo $cname|sed 's/\..*//g'`
    if [ ! -f ${cdir}/result/${cname}.log ]
    then
        echo "log file does not exist"
        return
    fi
    cd $cdir
    svn up
    if [ ! -d ${cdir}/answer ]
    then
        mkdir ${cdir}/answer
        svn add answer
    fi
    cp ${cdir}/result/${cname}.log ${cdir}/answer/${cname}.answer
    cd $cdir/answer
    pwd
    svn add ${cname}.answer
    svn st|grep "${cname}.answer"
    cd $cdir
}

case "$1" in
        'add')
             echo "add"
             add
             ;;
        'vicase')
             echo "vicase"  
             vicase
             ;;
        'case')
             echo "case"  
             see
             ;;
        'run')
             echo "run"  
             run
             ;;
        'runmysql')
             echo "runmysql"  
             runmysql
             ;;
        'runorcl')
             echo "runorcl"  
             runorcl
             ;;
        'result')
             echo "result"  
             result
             ;;
        'answer')
             echo "answer"  
             answer
             ;;
        'log')
             echo "log"  
             log
             ;;
        'reg')
             echo "reg"  
             reg $2
             ;;
        'curdir')
             echo "curdir"  
             curdir $2
             ;;
        'help')
    echo "sh shotcut.sh [ add | case | run ]"
    echo "sh shotcut.sh reg casepath"
    ;;
esac

