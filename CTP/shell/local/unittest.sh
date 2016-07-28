#!/bin/bash

function init {
	return
}

function list {
    find $CUBRID -name unittests*
}

function execute {
	unittestlog=".unittest.log"
	
    sh $1 2>&1 | tee ${unittestlog}
    
    if [ `cat ${unittestlog} | grep -i 'fail' | wc -l ` -eq 0 ]; then
    	IS_SUCC=true
    else
    	IS_SUCC=false
    fi
    rm -rf ${unittestlog}
}

function finish {
	return
}
