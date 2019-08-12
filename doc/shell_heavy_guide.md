# 1. Test Objective
Shel_heavy test is a part of the shell test. The test cases that require big disk size or big memory or with heavy workload are categorized as shell_heavy.
# 2. Deploy Regression Test Environment
## 2.1 Test Machines

No. | role | user | ip | hostname | Tools to deploy
--|--|--|--|--|--
0 | Controller node | rqgcontroller | 192.168.1.99 | func24 | CTP
1 | Test node | shell_heavy | 192.168.1.99 | func24 | CTP, cubrid-testcases-private-ex
2 | Test node | shell_heavy | 192.168.1.100 | func25 | CTP, cubrid-testcases-private-ex
3 | Test node | shell_heavy | 192.168.1.101 | func26 | CTP, cubrid-testcases-private-ex
4 | Test node | shell_heavy | 192.168.1.102 | func27 | CTP, cubrid-testcases-private-ex
5 | Test node | shell_heavy | 192.168.1.103 | func28 | CTP, cubrid-testcases-private-ex

**Controller node** listens to test messages and starts a test when there is a test message. It will distribute test cases to each test node for execution.  
**Test nodes** execute test cases in parallel.

## 2.2 On Controller Node
### 2.2.1 Install CTP
1. Checkout CTP 
    ```bash
    cd ~
    git clone https://github.com/CUBRID/cubrid-testtools.git
    cd ~/cubrid-testtools 
    git checkout develop
    cp -rf CTP ~/
    ```
2. Create and config `CTP/conf/common.conf`   
    ```
    git_user=cubridqa
    git_pwd=PASSWORD
    git_email=dl_cubridqa_bj_internal@navercorp.com
    default_ssh_pwd=PASSWORD
    default_ssh_port=22

    grepo_service_url=rmi://192.168.1.91:11099

    qahome_db_driver=cubrid.jdbc.driver.CUBRIDDriver
    qahome_db_url=jdbc:cubrid:192.168.1.86:33080:qaresu:dba::
    qahome_db_user=dba
    qahome_db_pwd=PASSWORD

    qahome_server_host=192.168.1.86
    qahome_server_port=22
    qahome_server_user=qahome
    qahome_server_pwd=PASSWORD

    activemq_user=admin
    activemq_pwd=PASSWORD
    activemq_url=failover:tcp://192.168.1.91:61616?wireFormat.maxInactivityDurationInitalDelay=30000

    mail_from_nickname=CUBRIDQA_BJ
    mail_from_address=<CUBRIDQA DL e-mail address>
    ```
3. Add path to `.bash_profile`
    ```
    export CTP_HOME=$HOME/CTP
    export CTP_BRANCH_NAME=develop
    export CTP_SKIP_UPDATE=0
    export init_path=$CTP_HOME/shell/init_path
    export PATH=$JAVA_HOME/bin:$CTP_HOME/bin:$CTP_HOME/common/script:$PATH
    ```
### 2.2.2 Configurations for shell_heavy test
Create a config file named `CTP/conf/shell_template_for_shell_heavy.conf` and configure as below.
```
# shell_template_for_shell_heavy.conf 
default.cubrid.cubrid_port_id=1578
default.broker1.BROKER_PORT=30790
default.broker1.APPL_SERVER_SHM_ID=30790
default.broker2.BROKER_PORT=33791
default.broker2.APPL_SERVER_SHM_ID=33791
default.ha.ha_port_id=59979

env.99.ssh.host=192.168.1.99
env.99.ssh.port=22
env.99.ssh.user=shell_heavy
env.99.ssh.pwd=PASSWORD

env.100.ssh.host = 192.168.1.100
env.100.ssh.port = 22
env.100.ssh.user = shell_heavy
env.100.ssh.pwd = PASSWORD

env.101.ssh.host = 192.168.1.101
env.101.ssh.port = 22
env.101.ssh.user = shell_heavy
env.101.ssh.pwd = PASSWORD

env.102.ssh.host = 192.168.1.102
env.102.ssh.port = 22
env.102.ssh.user = shell_heavy
env.102.ssh.pwd = PASSWORD

env.103.ssh.host = 192.168.1.103
env.103.ssh.port = 22
env.103.ssh.user = shell_heavy
env.103.ssh.pwd = PASSWORD

scenario=${HOME}/cubrid-testcases-private-ex/shell_heavy
test_continue_yn=yes
cubrid_download_url=
testcase_exclude_from_file=${HOME}/cubrid-testcases-private-ex/shell_heavy/config/daily_regression_test_excluded_list_linux.conf
testcase_update_yn=true
testcase_git_branch=develop
testcase_timeout_in_secs=10800
test_platform=linux
test_category=shell_heavy
testcase_exclude_by_macro=LINUX_NOT_SUPPORTED
testcase_retry_num=2
delete_testcase_after_each_execution_yn=false
enable_check_disk_space_yn=true

feedback_type=database
feedback_notice_qahome_url=http://192.168.1.86:8080/qaresult/shellImportAction.nhn?main_id=<MAINID>

owner_email=<owner email address>
cc_email=<CUBRIDQA DL email address>

git_user=cubridqa
git_email=<CUBRIDQA DL email address>
git_pwd=PASSWORD

feedback_db_host=192.168.1.86
feedback_db_port=33080
feedback_db_name=qaresu
feedback_db_user=dba
feedback_db_pwd=PASSWORD
```
When you need to test shell_heavy_debug, then need to copy `CTP/conf/shell_template_for_shell_heavy.conf` as `CTP/conf/shell_template_for_shell_heavy_debug.conf`.   
If there is no corresponding template config file, it will uses the `CTP/conf/shell_template.conf` file.   
If none of these configuration files exist, the test will end with error like `[SHELL] ERROR: Not found configuration file`.
### 2.2.3 Start Consumer
1. Create `start_test.sh`
Create a script named `$HOME/start_test.sh` like below.
    ```
    $ cat ~/start_test.sh
    nohup start_consumer.sh -q QUEUE_CUBRID_QA_SHELL_HEAVY_LINUX -exec run_shell &
    ```
    In the current daily qa test, the `shell_heavy`, `shell_long`, and `RQG` tests are using the same test machines, so we use one controllar to listening to the three test messages. The `start_test.sh` is like below in the daily qa test environment. 
    ```
    nohup start_consumer.sh -q QUEUE_CUBRID_QA_SHELL_HEAVY_LINUX,QUEUE_CUBRID_QA_RQG,QUEUE_CUBRID_QA_shell_heavy_LINUX -exec run_shell,run_shell,run_shell  &
    ```
2. Start Consumer
    ```
    $ cd ~
    $ sh start_test.sh
    ```
    Run `stop_consumer.sh` to stop listening.  

## 2.3 On Test Nodes
1. Install CTP   
Follow the steps at [2.2.1 Install CTP](#221-install-ctp), install the CTP to each test nodes.
2. Edit `.bash_profile`   
    Add following settings to `~/.bash_profile` then source it.
    ```
    # for code coverage test
    export GCOV_PREFIX=/home/shell_heavy
    export GCOV_PREFIX_STRIP=2

    # to generage core dump file
    ulimit -c unlimited
    ```
3. Checkout test cases   
    Checkout shell_heavy test cases to each test nodes.
    ```
    $ cd ~
    $ git clone https://github.com/CUBRID/cubrid-testcases-private-ex.git
    $ ls ~/cubrid-testcases-private-ex/
    shell  shell_heavy
    $ ls ~/cubrid-testcases-private-ex/shell_heavy/
    _01_cursor_holdability      _06_issues        bug_bts_13188    bug_bts_17504  bug_bts_5359  bug_xdbms48  cbrd_21070  config
    _02_group_and_async_commit  _09_64bit         bug_bts_14738_1  bug_bts_17550  bug_bts_5868  cbrd_20591   cbrd_21119  issue_5765_timezone_support
    _04_misc                    _26_apricot_qa    bug_bts_14738_2  bug_bts_17599  bug_bts_6995  cbrd_21003   cbrd_21186
    _05_addition                _27_aprium_qa     bug_bts_14894    bug_bts_20481  bug_bts_7233  cbrd_21020   cbrd_21361
    _05_multitbl_query_01       _29_features_920  bug_bts_14980    bug_bts_5188   bug_bts_7313  cbrd_21036   cbrd_21362
    _05_multitbl_query_02       bug_bts_10295     bug_bts_17460    bug_bts_5349   bug_bts_8675  cbrd_21049   cbrd_21378
    ```
4. Install CUBRID
    ```
    run_cubrid_install http://192.168.1.91:8080/REPO_ROOT/store_01/10.2.0.8368-b85a234/drop/CUBRID-10.2.0.8368-b85a234-Linux.x86_64.sh
    ```
# 3. Regression Tests
We perform shell_heavy test for every CI builds and perform shell_heavy code coverage test for monthly. You can find the settings from http://10.113.153.154:10086/qaresult/job/job.conf   
## 3.1 Daily Regressoin test
When the build server has a new build and meet the conditions of the crontab task, a shell_leavy test will be executed. If there is something wrong and need to run shell_leavy test again, you can send a test message.
### Check for test config  
Go to `controller node`, check `CTP/conf/shell_template_for_shell_heavy.conf`. This config file is only for regression test. We usually don't modify it except we have new test nodes.
### Send test message  
1. Login to message server `message@192.168.1.91` 
2. Send shell_heavy test message  
    For daily QA, we run shell_heavy test with a release build. 
    ```
    sender.sh QUEUE_CUBRID_QA_SHELL_HEAVY_LINUX http://192.168.1.91:8080/REPO_ROOT/store_01/10.2.0.8274-0350ce7/drop/CUBRID-10.2.0.8274-0350ce7-Linux.x86_64.sh shell_heavy default
    ```
3. Send shell_heavy_debug test message   
For FT(Full Test, usually perform it before release), we also need to perform `shell_heavy_debug`, which is run shell_heavy test cases with a debug build. 
    ```
    sender.sh QUEUE_CUBRID_QA_SHELL_HEAVY_LINUX http://192.168.1.91:8080/REPO_ROOT/store_01/10.2.0.8274-0350ce7/drop/CUBRID-10.2.0.8274-0350ce7-Linux.x86_64-debug.sh shell_heavy_debug default
    ```
## 3.2 Verify Test Result
### Check if there is the test result  
![verify1](./shell_heavy_image/verify1.png)  
* Go to QA homepage and click the CI build, wait for the page loading, see the 'Function' tab and find the shell_heavy result.
* The `Fail` column includes `Total` and `New` columns, the number in `Total` links to the whole failed cases, and the number in `New` links to the new failed cases that compared with previous build.  
* The `shell_heavy` in the `Category` column links to the test cases that have been finished at the moment.  
![verify2](./shell_heavy_image/verify2.png)  
You can choose the `ALL`, `OK`, and `NOK` filters as you need.  
* If it shows 'NO RESULT (OR RUNNING)' as bellow, you need to find the reason. 
![verify3](./shell_heavy_image/verify3.png)    
Usually, it caused by insufficient disk space or test is waiting for shell_long or RQG tests finish. The `shell_long`, `RQG`, and `shell_heavy` are executed sequentially. You can check it from the [Test Machines](http://10.113.153.154:10086/qaresult/monitor/checkQueue.nhn) link in the QA homepage.  
![verify_queue](./shell_heavy_image/verify_queue.png)  
    
### Verify Failed test cases
Please refer to [result overview](https://github.com/CUBRID/cubrid-testtools/blob/a066d8861858fa49885c77796515890b3f35b062/CTP/doc/shell/shell_guide.md#result-overview) and [verfy the failed cases](https://github.com/CUBRID/cubrid-testtools/blob/a066d8861858fa49885c77796515890b3f35b062/CTP/doc/shell/shell_guide.md#verfy-the-failed-cases) of shell guide. 

### Check Elapse Time  
Usually a shell_heavy test takes 20000s ~ 30000s, if the test didn't finished in 30000s, you need to check why the test is so slow. It may because there is a server crash, server hangs up, or performance drop.

## 3.3 Run Code Coverage Test
Code coverage test starts on the last Sunday of each month.

### Send code coverage testing message
1. Log into message server `message@192.168.1.91`.
2. Using the `sender_code_coverage_testing_message.sh` to send a code coverate test message.
    ```
    cd manual/
    sh sender_code_coverage_testing_message.sh QUEUE_CUBRID_QA_SHELL_HEAVY_LINUX http://192.168.1.91:8080/REPO_ROOT/store_01/10.2.0.8270-c897055/drop/CUBRID-10.2.0.8270-c897055-gcov-Linux.x86_64.tar.gz http://192.168.1.91:8080/REPO_ROOT/store_01/10.2.0.8270-c897055/drop/cubrid-10.2.0.8270-c897055-gcov-src-Linux.x86_64.tar.gz shell_heavy
    ```

### Verify code coverage testing result
Go to QA homepage and find the 'code coverage' node in the left area, click the link of latest result.  
![cov1](./shell_heavy_image/cov1.png)  
Click the shell_heavy link.  
![cov2](./shell_heavy_image/cov2.png)  
There is a coverage rate of lines. Its coverage rate of lines is usually in 41.7%~42%. You can see the expected rate from http://jira.cubrid.org/browse/CUBRIDQA-851.  
![cov3](./shell_heavy_image/cov3.png)

## 3.4 Report issues
Here are the issues that you can refer to http://jira.cubrid.org/browse/CBRD-22880.
Please read [Issue Reporting Guide for QA](http://jira.cubrid.org/browse/CUBRIDQA-1?focusedCommentId=4739243&page=com.atlassian.jira.plugin.system.issuetabpanels:comment-tabpanel#comment-4739243), [Template for Crash Issues](http://jira.cubrid.org/browse/CUBRIDQA-1?focusedCommentId=4739244&page=com.atlassian.jira.plugin.system.issuetabpanels:comment-tabpanel#comment-4739244), and [How to Report Regression Crash Issues](http://jira.cubrid.org/browse/CUBRIDQA-1?focusedCommentId=4739245&page=com.atlassian.jira.plugin.system.issuetabpanels:comment-tabpanel#comment-4739245) to report issues.

# 4. Shell_heavy Test Case
The current isolation test case is located in the https://github.com/CUBRID/cubrid-testcases-private-ex/tree/develop/shell_heavy repository. The directory structure, naming rule, and convention rules of shell_heavy test case are definitely same as that of shell and shell_long test cases. Please refer to [8. shell case standards](https://github.com/CUBRID/cubrid-testtools/blob/a066d8861858fa49885c77796515890b3f35b062/CTP/doc/shell/shell_guide.md#8-shell-case-standards) or [5. Test Case Specification](https://github.com/CUBRID/cubrid-testtools/blob/fbc476b6b76e8a93fe7af21ce726bb29c14380b5/doc/shell_long/README.md#5test-case-specification) to write test case.

