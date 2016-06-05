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

driver_ver=''
server_ver=''
dir=`pwd`
url=""
t1st=''
t2nd=''
t3rd=''
s_t1st=''
s_t2nd=''
s_t3rd=''
testver=''
upscenario=''
needrun=''
usetar=''
svnuser=""
svnpassword=""
test_type=''
testver=''
test_case=''
dbIns_ver=''
dbSvr_ver=''
svr_prefix=""
ins_prefix=""
needMake="no"
dbName=""
cubrid_major=""
cubrid_minor=""
cubrid_sub_minor=""
excludelist=''
hlp=''
bkname=""
d_url=""
s_url=""
img_url=""
target_test=""
scenario_home="$HOME/dailyqa"
k_url=""

while [ $# -ne 0 ]; do
    case $1 in
        --help)
        hlp='yes'
        ;;
        -t)
            shift
        test_type=$1
        ;;
        -s)
            shift
        test_case=$1
        ;;
        -v)
            shift
        testver=$1
        ;;  
                -dl)
            shift
        d_url=$1
        ;;
        -sl)
            shift
        s_url=$1
        ;;
        -il)
            shift
        img_url=$1
        ;;
        -catag)
            shift
        excludelist=$1
        ;;
        -MTest)
            shift
        target_test=$1
        ;;
    esac
    shift
done

function usage()
{
  exec_name=$(basename $0)
  cat<<ccitest
    usage: $exec_name options args
        -t      cci,jdbc,dbImg | this option represents which type of compatibility test you will do
        -dl     driver         | this option is the url of test interface driver
        -sl     server         | this option is the url of test server 
        -il     db image url   | this option is the url of db image server 
        -u  update         | this option is in order to update scenario
        -v  test version   | this option is in order to get test version
        -s  case           | this option represents what case you will execute
        -r      run            | this option is in order to run test after setup environment
        -catag  catagory       | this option is in order to list the excluded list
ccitest
}

function parse_build_version()
{
   build_ver=`cubrid_rel|grep "CUBRID"|awk -F '(' '{print $2}'|sed 's/)//g'|tr -d " "`
   cubrid_major=${build_ver%%.*}
   cubrid_minor=`echo $build_ver|awk -F '.' '{print $2}'`
   cubrid_sub_minor=`echo $build_ver|awk -F '.' '{print $3}'`
}


function installScript()
{
        curDir=`pwd`
    url=''
    num=''
    if [ $# -ne 0 ]
    then
        url=$1  
        cub="CUBRID"
        cd ~
        echo ""
        echo "=====install CUBRID ($url)=========="
        echo ""
        
        wget $url
        
        if [ $? -ne 0 ]
        then
            f=${url##*build}
            n=${k_url}${f}
            wget $n
            if [ $? -ne 0 ]
            then
                echo "********* Please make sure your url is correct *********"
                exit 1
            fi
        fi      

        cubrid service stop >/dev/null 2>&1
        #sleep 2
        if [ -d $cub ]
        then
            rm -rf CUBRID
        fi
                
                num=${url##*/}
        sh $num > /dev/null <<EOF
yes


EOF
        . ./.cubrid.sh

    else
        usage
    fi
    rm $num
    cd $curDir  
}

function installDriverBuild()
{
   curDir=`pwd`
   dirver_bk="driver_backup"
   j_driver=$1
   if [ "$j_driver" ]
   then
        goToInstallationDirectory
    installScript $j_driver
    rm -rf CUBRID_${dirver_bk}
    mv CUBRID CUBRID_${dirver_bk}
   else
    usage
   fi
   
   cd $curDir   
}



function installServerBuild()
{
   curDir=`pwd`
   url=$1
   if [ "$url" ]
   then
        goToInstallationDirectory
    installScript $url
   else
    "Please confirm your url is correct!"
   fi

   cd $curDir
}

function revertDriverBackup()
{
    curDir=`pwd`
    goToInstallationDirectory 
    rm -rf CUBRID_${dirver_bk}
    cd $curDir
}

function config_cci_test_environment()
{
    curDir=`pwd`
        dirver_bk="driver_backup"
        url=$1
        filename=${url##*/}
        num=`echo $filename|awk -F '-' '{print $2}'`
        
        #parse driver build
        the1st=${num%.*}
        the2nd=${num%%.*}       


        #config file in lib folder
        cd $CUBRID/lib
        rm libcascci.*
        cp ~/CUBRID_${dirver_bk}/lib/libcascci.* .
        rm libcascci.so libcascci.so.${the2nd} 
        ln -s libcascci.so.${the1st} libcascci.so
        ln -s libcascci.so.${the1st} libcascci.so.${the2nd} 

        #config include file
        cd $CUBRID/include
        rm -f cas_cci.h cas_error.h
        cp ~/CUBRID_${dirver_bk}/include/cas_cci.h .
        cp ~/CUBRID_${dirver_bk}/include/cas_error.h .
    
        #save driver and server info
        echo "CCI_Version=${the1st}" >$CUBRID/qa.conf
        s=$s_url
        sname=${s##*/}
        sno=`echo $sname|awk -F '-' '{print $2}'`
        s_prefix=${sno%.*}        
        echo "Server_Version=${s_prefix}" >>$CUBRID/qa.conf         
        cd $curDir
}

function config_jdbc_test_environment()
{
        curDir=`pwd`
    dirver_bk="driver_backup"
    url=$1
    filename=${url##*/}
        num=`echo $filename|awk -F '-' '{print $2}'`
    
        if [ "$num" ]
    then
        #config file in jdbc folder
        cd $CUBRID/jdbc
        rm -f cubrid_jdbc.jar
    
        #copy test driver and create link
                goToInstallationDirectory
        cp ./CUBRID_${dirver_bk}/jdbc/JDBC-"${num}"-cubrid.jar $CUBRID/jdbc
                if [ $? -ne 0 ]
            then
                cp ./CUBRID_${dirver_bk}/jdbc/JDBC-"${num}".jar $CUBRID/jdbc
                cd $CUBRID/jdbc
                    ln -s JDBC-"${num}".jar cubrid_jdbc.jar
            else
                    cd $CUBRID/jdbc 
                ln -s JDBC-"${num}"-cubrid.jar cubrid_jdbc.jar
            fi
    else
        echo "You are missing driver version!!"
    fi

    cd $curDir

}


function getJDBCByBuild()
{
    jdbc_url=$1
    installDriverBuild $jdbc_url
}

function dosvnup()
{
    dir=`pwd`
    if [ "$test_t" == 'cci' ]
    then
        test_case="shell"
    fi
            
    echo "Start to update scenario from svn..."
    cd $HOME/dailyqa/$testver/scenario/$test_case
    if [ "$test_case" == "site" -o "$test_case" == "shell" ]
    then
        rm -rf kcc_*
        rm -rf neis*
        rm -rf _20_cci
        #svn revert --username=$svnuser --password=$svnpassword  -R -q . --no-auth-cache
        svn up --username $svnuser --password $svnpassword --no-auth-cache 
    else
        rm -rf _*
        #svn revert --username=$svnuser --password=$svnpassword  -R -q . --no-auth-cache
        #if [ "$testver" == "RB-9.2.0" -o "$testver" == "RB-9.2.3" ]
        svn up --username $svnuser --password $svnpassword --no-auth-cache
        #if [ "$testver" == "RB-9.2.0" ]
        #then
        #    svn up -r 42857 --username $svnuser --password $svnpassword --no-auth-cache 
        #else
        #    svn up --username $svnuser --password $svnpassword --no-auth-cache 
        #fi

        #                                               fi  
    fi

    echo "End to update scenario from svn!"

    cd $HOME/dailyqa/$target_test

    echo "Delete test case according to the execluded list"

    prefix=`echo $excludelist|awk -F_ '{print $3}'`
    no3=`echo "$prefix"|awk -F. '{print $3}'`
    if [ ! -n "$no3" ]
    then
        last_element=100
        while [ $last_element -ge 0 ]
        do
            versioninfo=$prefix.$last_element
            version_tmp=`echo $excludelist|sed "s/\$prefix/\$versioninfo/g"`
            file_excludedlist=config/${version_tmp}_excluded_list
            if [ -e "$file_excludedlist" ]
            then
                break
            fi
            let 'last_element -=1'
        done
        excludelist_exclude=$version_tmp

        last_element=100
        while [ $last_element -ge 0 ]
        do
            versioninfo=$prefix.$last_element
            version_tmp=`echo $excludelist|sed "s/\$prefix/\$versioninfo/g"`
            file_patch=config/patch_files/${version_tmp}_patch
            if [ -e "$file_patch" ]
            then
                break
            fi
            let 'last_element -=1'
        done
        excludelist_patch=$version_tmp
    else
        excludelist_exclude=${excludelist}
        excludelist_patch=${excludelist}
        file_excludedlist=config/${excludelist}_excluded_list
        file_patch=config/patch_files/${excludelist}_patch
    fi

    if [ "$file_patch" ] && [ "$test_case" != "shell" ]
    then
        cd $HOME/dailyqa/$target_test/config/patch_files
        svn up --username $svnuser --password $svnpassword --no-auth-cache 
        sed -i 's/^M//g' $HOME/dailyqa/$target_test/config/patch_files/${excludelist_patch}_patch
        rm $HOME/dailyqa/$testver/scenario/$test_case/${excludelist_patch}_patch
        cp $HOME/dailyqa/$target_test/config/patch_files/${excludelist_patch}_patch $HOME/dailyqa/$testver/scenario/$test_case

        cd $HOME/dailyqa/$testver/scenario/$test_case
        patch -p0 -f < ./${excludelist_patch}_patch
        
        #clear patch file for the next time
        rm ./${excludelist_patch}_patch
    fi
   
    cd $HOME/dailyqa/$target_test   
     
    if [ "$file_excludedlist" ]
    then
    cd $HOME/dailyqa/$target_test/config
    #svn up for excluded list
        svn up --username $svnuser --password $svnpassword --no-auth-cache 
  
        cd $HOME/dailyqa/$target_test
        cat $file_excludedlist|grep scenario|awk '{print "rm -rf " $0}' > ./del_list.sh
    sed -i 's/
//g' del_list.sh
    if [ "$target_test" != "$testver" ]
    then
        rm $HOME/dailyqa/$testver/del_list.sh
        mv del_list.sh $HOME/dailyqa/$testver
    fi 

       cd $HOME/dailyqa/$testver
       sh del_list.sh
     fi

     rm del_list.sh 
     cd $dir
}

function prepareScenario()
{
        dir=`pwd`
        bkname="shell_"`date +%s`"_bk"
        cd $HOME/dailyqa/$testver/scenario
        cci="$HOME/dailyqa/$testver/interface/CCI/shell"
        if [ -d shell ]
        then
                mv shell $bkname
                if [ -d $cci ]
                then
                        ln -s $cci shell
                else
                        echo "Please checkout CCI scenario"
                        exit 0
                fi
        else    
                if [ -d $cci ]
                then
                        ln -s $cci shell
                else
                        echo "Please checkout CCI scenario"
                        exit 0
                fi
        fi
        cd $dir
}

function recoveryScenario()
{
        dir=`pwd`
        cd $HOME/dailyqa/$testver/scenario
        rm -rf shell
        if [ -d $bkname ]
        then
                mv "$bkname" shell
        fi
        cd $dir
}

function updatecqt_init()
{
     cur=`pwd`
     dir=$QA_REPOSITORY/lib/shell/common
     if [ -d "$dir" ]
     then
        cd $QA_REPOSITORY/lib/shell/common
        svn up --username $svnuser --password $svnpassword --no-auth-cache 
     fi
     cd $cur
}

function goToInstallationDirectory()
{
    if [ "$CUBRID" ]
    then
        cd $CUBRID
        cd ..
    else
        cd $HOME
    fi
}

function close_shard_service()
{
     hasShard=`cat $CUBRID/conf/cubrid_broker.conf|grep "%shard1" |grep -v grep |wc -l`
     if [ $hasShard -ne 0 ]
     then
    lineOfShard=`cat cubrid_broker.conf |grep -n "%shard1"|awk -F ':' '{print $1}'`
    let "endLine=lineOfShard+2"
    sed -i '${lineOfShard},${endLine}s/ON/OFF/g' cubrid_broker.conf
     fi
}


function config_test_environment()
{
       config_cci_test_environment $1
       config_jdbc_test_environment $1
}


function setup_Compatibility()
{
        #prepare cci driver
        installDriverBuild $d_url ###$driver_ver

        #Install server build
        installServerBuild $s_url   ###$server_ver

    #config jdbc test environment
        config_test_environment $d_url   ###$driver_ver
}

function setup_CCI_Compatibility()
{
        #prepare cci driver
        installDriverBuild $d_url ###$driver_ver

        #Install server build
        installServerBuild $s_url   ###$server_ver

        #config jdbc test environment
        config_cci_test_environment $d_url   ###$driver_ver
}

function setup_JDBC_Compatibility()
{
    #prepare jdbc driver
    getJDBCByBuild $d_url ###$driver_ver
    
    #Install server build
    installServerBuild $s_url   ###$server_ver

        #config jdbc test environment
    config_jdbc_test_environment $d_url   ###$driver_ver
    
}

function parseDBName()
{
       if [ "$test_case" != "" ];then
        if [ "$test_case" == "sql" ]
        then
            dbName="basic"
        elif [ "$test_case" == "medium" ]
        then
            dbName="mdb"
        elif [ "$test_case" == "site" ]
        then
            dbName="kcc,neis05,neis08"
        else
            dbName="basic,mdb,kcc,neis05,neis08"
        fi
       else
        echo "Please enter your test scenario type!"
        exit 1
       fi 

}

function createDB()
{
   dbCharset=$1

   #make locale for test
   make_locale

   if [ "$dbCharset" ]
   then
        arr=(${dbName//,/ })
    for i in ${arr[@]}
    do
        existForDB=`cat $CUBRID/databases/databases.txt|grep $i|wc -l`
        if [ $existForDB -ne 0 ]
        then
            cubrid deletedb $i
        fi

        
            cubrid createdb $i $dbCharset
        di=`pwd`
    done
   else
    arr=(${dbName//,/ })
        for i in ${arr[@]}
        do
        existForDB=`cat $CUBRID/databases/databases.txt|grep $i|wc -l`
        if [ $existForDB -ne 0 ]
                then
                        cubrid deletedb $i
                fi
                cubrid createdb $i
        done
   fi
}


function prepareDBImageInstance()
{
        #create db image under CQT
        curDir=`pwd`
    charset=en_us
    cd $QA_REPOSITORY/temp/
    
    mkdir -p dbImage
    cd dbImage
    rm *
    
    dbSvr_ver=$testver
        parse_build_version
    parseDBName


    if [ $cubrid_major -ge 9 -a $cubrid_minor -gt 1 ] || [ $cubrid_major -ge 10 ]
        then
                 createDB $charset
    else
         createDB 
    fi

    
    #prepare db data
        executeDBDataLoad   
        
    cd $curDir 
}

function backupDBTxT()
{
         cp $CUBRID/databases/databases.txt $QA_REPOSITORY/temp/dbImage/
     echo "#db image compatibility test" > $QA_REPOSITORY/temp/dbImage/dbImage.config
     echo "testdb=$dbName" >> $QA_REPOSITORY/temp/dbImage/dbImage.config
     dbVer=`cubrid_rel|grep "CUBRID"|awk -F '(' '{print $2}'|sed 's/)//g'`
     echo "dbImgInstanceVersion=$dbVer" >> $QA_REPOSITORY/temp/dbImage/dbImage.config
}

function referDBImageInstance()
{
         dbtxt="$QA_REPOSITORY/temp/dbImage/databases.txt"
     if [ -f "$dbtxt" ]
     then
        cp $QA_REPOSITORY/temp/dbImage/databases.txt $CUBRID/databases/
        dbSvrVer=`cubrid_rel|grep "CUBRID"|awk -F '(' '{print $2}'|sed 's/)//g'`
        echo "dbServerVersion=$dbSvrVer" >> $QA_REPOSITORY/temp/dbImage/dbImage.config
     else
        echo "DB Image instance creation fail!"
     fi

     #judge if need make 
     if [ "$needMake" == "yes" ]
     then
        make_locale
        needMake="no"
     fi 
}

function checkEnvironmentVariables()
{
        if [ "$QA_REPOSITORY" = "" ]
        then
                echo "Please config your test environment!"
                exit 1
        fi
}

function executeDBDataLoad()
{ 
        curDir=`pwd`
    if [ "$dbSvr_ver" ]
    then
         arr=(${dbName//,/ })
             for x in ${arr[@]}
             do
            cd ${scenario_home}/${dbSvr_ver}/files/unload/
            if [ "$x" == "mdb" ]
            then
                cp mdb.tar.gz ${QA_REPOSITORY}/temp/dbImage/
                    cd ${QA_REPOSITORY}/temp/dbImage/
                tar zvxf mdb.tar.gz 
                cubrid loaddb -s mdb_schema -i mdb_indexes -d mdb_objects -udba --no-logging $x
                if [ $? -eq 0 ]
                then
                    rm mdb.tar.gz
                    cd -
                else
                    echo "Load DB fail!"
                    rm *.gz
                fi
            elif [ "$x" == "kcc" ]
            then
                cp kcc.tar.gz ${QA_REPOSITORY}/temp/dbImage/
                                cd ${QA_REPOSITORY}/temp/dbImage/
                                tar zvxf kcc.tar.gz
                                cubrid loaddb -s kcc_schema -i kcc_indexes -d kcc_objects -udba --no-logging $x
                                if [ $? -eq 0 ]
                                then
                                        rm kcc.tar.gz
                                        cd -
                                else
                                        echo "Load DB fail!"
                    rm *.gz
                                fi
            elif [ "$x" == "neis05" ]
            then
                cp neis05.tar.gz ${QA_REPOSITORY}/temp/dbImage/
                                cd ${QA_REPOSITORY}/temp/dbImage/
                                tar zvxf neis05.tar.gz
                                cubrid loaddb -s neis05_schema -i neis05_indexes -d neis05_objects -udba --no-logging $x
                                if [ $? -eq 0 ]
                                then
                                        rm neis05.tar.gz
                                        cd -
                                else
                                        echo "Load DB fail!"
                    rm *.gz
                                fi
                modifyDBProperties $x
            elif [ "$x" == "neis08" ]
            then
                cp neis08.tar.gz ${QA_REPOSITORY}/temp/dbImage/
                                cd ${QA_REPOSITORY}/temp/dbImage/
                                tar zvxf neis08.tar.gz
                                cubrid loaddb -s neis08_schema -i neis08_indexes -d neis08_objects -udba --no-logging $x
                                if [ $? -eq 0 ]
                                then
                                        rm neis08.tar.gz
                                        cd -
                                else
                                        echo "Load DB fail!"
                    rm *.gz
                                fi
                modifyDBProperties $x
             fi
        done
    fi
    cd $curDir
                      
}

function make_locale()
{
    curDir=`pwd`
        version=`cubrid_rel | grep CUBRID | awk -F'(' '{print $2}' |  awk -F')' '{print $1}'`
        version_type=`cubrid_rel | grep debug | wc -l`
    is64bit=`cubrid_rel|grep 64bit|grep -v grep|wc -l`
        major=`echo $version | cut -d . -f 1 | grep -oE "[[:digit:]]{1,}"`
        minor=`echo $version | cut -d . -f 2`
        revis=`echo $version | cut -d . -f 3`
    db_bits="64"

        if [ $major -eq 8 ]; then
                # major is equal 8
                if [ $minor -lt 4 ]; then
                        echo 1
                        return
                else
                        #minor is greate equal 4
                        if [ $revis -lt 9 ]; then
                                echo 1
                                return
                        fi
                fi
        fi

        echo "make locale now"
        mv $CUBRID/conf/cubrid_locales.txt $CUBRID/conf/cubrid_locales_bak.txt
        cp $CUBRID/conf/cubrid_locales.all.txt $CUBRID/conf/cubrid_locales.txt
        cd $CUBRID/bin
        needMake="yes"

    if [ $is64bit -ne 0 ]
    then
        db_bits="64bit"
    else
        db_bits="32bit"
    fi

        if [ $version_type -eq 0 ]
        then
                sh make_locale.sh -t $db_bits
        else
                sh make_locale.sh -t $db_bits -m debug
        fi
    cd $curDir
}

function modifyDBProperties()
{
    db=$1
    if [ "$db" != "" ]
    then
         os_name=`uname`
         if [ "$db" == "neis05" ]; then
          if [ "$this_bit" == "32" ]; then
                ## add 32 so
                 csql neis05 -u dba -S -c "alter class dual add file '\$QA_REPOSITORY/function/neis/$os_name/neis5_8/khh_method32.${major}.so'"
                ## drop 64 so
                 csql neis05 -u dba -S -c "alter class dual drop file '\$QA_REPOSITORY/function/neis/$os_name/neis5_8/khh_method64.${major}.so'"    
        else
                ## add 64 so
                 csql neis05 -u dba -S -c "alter class dual add file '\$QA_REPOSITORY/function/neis/$os_name/neis5_8/khh_method64.${major}.so'"
                ## drop 32 so
                 csql neis05 -u dba -S -c "alter class dual drop file '\$QA_REPOSITORY/function/neis/$os_name/neis5_8/khh_method32.${major}.so'"
        fi
        csql neis05 -u dba -S -c "alter class dual drop file '\$QA_REPOSITORY/function/neis/$os_name/neis5_8/khh_method.so'"
         fi
    
             if [ "$db" == "neis08" ]; then
              if [ "$this_bit" == "32" ]; then
                 ## add 32 so
                 csql neis08 -u dba -S -c "alter class dual add file '\$QA_REPOSITORY/function/neis/$os_name/neis8_8/khh_method32.${major}.so'"
                 ## drop 64 so
                 csql neis08 -u dba -S -c "alter class dual drop file '\$QA_REPOSITORY/function/neis/$os_name/neis8_8/khh_method64.${major}.so'"
          else
                 ## add 64 so
                 csql neis08 -u dba -S -c "alter class dual add file '\$QA_REPOSITORY/function/neis/$os_name/neis8_8/khh_method64.${major}.so'"
                 ## drop 32 so
                  csql neis08 -u dba -S -c "alter class dual drop file '\$QA_REPOSITORY/function/neis/$os_name/neis8_8/khh_method32.${major}.so'"                           
           fi
           csql neis08 -u dba -S -c "alter class dual drop file '\$QA_REPOSITORY/function/neis/$os_name/neis8_8/khh_method.so'"
         fi 
    fi
}

function executeUserCommand()
{
        cmd_suffix=$1
    if [ ! "$cmd_suffix" ]
    then
        return 1
    fi

    if [ "$cmd_suffix" == "bf" ]
    then
        cmdName=xxxx_bf.sh
        if [ -f $QA_REPOSITORY/dbImage_script/${cmdName} ]
        then
            sh $QA_REPOSITORY/dbImage_script/${cmdName}
        fi  
    elif [ "$cmd_suffix" == "af" ]
    then
        cmdName=xxxx_af.sh
        if [ -f $QA_REPOSITORY/dbImage_script/${cmdName} ]
        then
                    sh $QA_REPOSITORY/dbImage_script/${cmdName}
        fi
    fi
        

}

function execute_dbImg_Compatibility()
{

    #check test environment variables
        checkEnvironmentVariables
    
        #install db image build
        installServerBuild $img_url
        
        #execute User command
    executeUserCommand "bf"        

        #prepare db image file
    prepareDBImageInstance         
    
    #backup databases.txt
    backupDBTxT

    #install db server build
    installServerBuild $s_url

    executeUserCommand "af"       

    #config db image instance
    referDBImageInstance

} 

function Upper2Lower()
{
    val=''
    str=$1
    if [ "$str" ]
    then
        val=`echo "$str" | tr 'A-Z' 'a-z'`
    else
        "No string parameter is converted!!"
    fi
    echo $val
}



####Main####
if [ "$hlp" == 'yes' ]
then
    usage
    exit 0
fi

test_t=`Upper2Lower $test_type`

if [ "$test_t" == 'cci' ]
then
    setup_CCI_Compatibility
elif [ "$test_t" == 'jdbc' ]
then
    setup_JDBC_Compatibility
elif [ "$test_t" == 'dbimg' ]
then
    execute_dbImg_Compatibility
else
    setup_Compatibility
fi
