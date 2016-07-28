#!/bin/bash

function init {
	return
}

function list {
    find $CUBRID/util -name unittests* | grep -v CMake
}

function execute {
	unittestlog=".unittest.log"
	
    $1 2>&1 | tee ${unittestlog}
    
    if [ `cat ${unittestlog} | grep -i 'fail\|Unit tests failed' | wc -l ` -eq 0 -a `cat ${unittestlog} | grep -i 'OK' | wc -l ` -ne 0 ]; then
    	IS_SUCC=true
    else
    	IS_SUCC=false
    fi
    rm -rf ${unittestlog}
}

function finish {
	return
}
