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
old_dir=`pwd`
script_path=""
platform=""
url=""
srcUrl=""

while [ $# -ne 0 ];do
	case $1 in
		-p)
			shift
		platform=$1
                ;;
		-l)
			shift
		url=$1
		;;
		-s)
			shift
		srcUrl=$1
		;;
	esac
	shift
done

function getDir()
{
	cd $(dirname ${0})
	script_path=`pwd`
}

function usage()
{
	exec_name=$(basename $0)
		cat<<installbuild
		Usage:$exec_name <-p> [linux|win|aix|solaris|hp-ux] <-l>
		- <p> | please set platform what you want to install on it
		- <l> | build url where is saved
		- <s> | source code url where is saved
	    
installbuild

}

function checkEnvironmentVariable()
{
     curDir=`pwd`
     isGCOVPREFIX_RELATED=`env|grep GCOV_PREFIX|wc -l`
     if [ $isGCOVPREFIX_RELATED -ne 2 ]
     then
	  sed -i '/GCOV_PREFIX/d' $HOME/.bash_profile
	  echo "export GCOV_PREFIX=$HOME" >> $HOME/.bash_profile
	  echo "export GCOV_PREFIX_STRIP=2" >> $HOME/.bash_profile
	  cd $HOME
	  source .bash_profile
     fi

     cd $curDir
}

function installBuildOnLinux()
{
	build_url=$1
	source_url=$2
	buildFile=${build_url##*/}
	cub="CUBRID"
	if [ "$source_url" ]
	then
	 	cd ~
                echo ""
                echo "=====install CUBRID $buildFile =========="
                echo ""
		srcFolder=`echo $buildFile|awk -F '-' '{print $2}'` 
		rm $buildFile >/dev/null 2>&1
		wget $build_url
		if [ -d $cub ]
                then
                        rm -rf ~/CUBRID
                fi

		tar zvxf $buildFile
		
		mkdir -p build
		cd build
		sourceFile=${source_url##*/}
		rm -rf "cubrid-${srcFolder}" >/dev/null 2>&1
		rm $sourceFile >/dev/null 2>&1
		wget $source_url
		tar zvxf $sourceFile
                rm src
		ln -s "cubrid-${srcFolder}/src"

		rm $sourceFile
		
		cd ~
		
		rm $buildFile
		checkEnvironmentVariable
		cd $script_path			
	else
		cd ~
        	echo ""
        	echo "=====install CUBRID $buildFile =========="
        	echo ""

        	wget $build_url
        	cubrid service stop >/dev/null 2>&1
        	#sleep 2
        	if [ -d $cub ]
        	then
                	rm -rf ~/CUBRID
        	fi

        	sh $buildFile > /dev/null <<EOF
yes


EOF
        	. ~/.cubrid.sh

        	rm $buildFile
        	cd $script_path
	fi
}


function installBuildOnWin()
{
	build_url=$1
        buildFile=${url##*/}
        cub="CUBRID"
        cd ~
        echo ""
        echo "=====install CUBRID=========="
        echo ""
        cubrid service stop >/dev/null 2>&1
	sleep 1
	taskkill /FI "imagename eq cub*" /F 
	cd $CUBRID
	rm -rf *
	cd ..
	rm -rf CUBRID-Windows*
	wget $build_url
	unzip $buildFile -d ./CUBRID
	echo "=====finish CUBRID Installation====="
}


##Main function
getDir

if [ "$platform" ] && [ "$url" ]
then
	if [ "$platform" == "linux" ]
	then
		if [ "$srcUrl" ]
		then
			installBuildOnLinux $url $srcUrl
		else
			installBuildOnLinux $url
		fi
	elif [ "$platform" == "win" ]
	then
		installBuildOnWin $url
	elif [ "$platform" == "aix" ]
	then
		echo "TODO AIX"
	fi
else
	usage
fi

cd $old_dir


