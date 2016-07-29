#!/bin/sh

curdate=`date +%Y.%m.%d_%H.%M.%S`

AUTO_TEST_VERSION=`cat conf/main_snapshot.properties| grep AUTO_TEST_VERSION | awk -F '=' '{ print $2}'`

AUTO_TEST_BITS=`cat conf/main_snapshot.properties| grep AUTO_TEST_BITS | awk -F '=' '{print $2}'`

TASK_ID=`cat conf/current_task_id`

pkg_name=cc_result_${AUTO_TEST_VERSION}_${AUTO_TEST_BITS}_${TASK_ID}_${curdate}.tar.gz

mkdir works >/dev/null 2>&1

tar czf works/${pkg_name} log conf run.log
