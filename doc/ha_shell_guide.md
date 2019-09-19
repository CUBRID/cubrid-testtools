# HA Shell Test Guide
# 1. Test Objective
HA Shell test suite is used to test CUBRID HA feature. To run a HA shell case, we usually need at least two servers. But sometimes, we need more than two servers. So HA test cases are divided to two categories:
* `HA Shell`: Located in `https://github.com/CUBRID/cubrid-testcases-private/tree/develop/HA/shell` and scheduled by daily regression. Each test case can only run on two servers.
* `Shell_ext`: Located in `https://github.com/CUBRID/cubrid-testcases-private/tree/develop/shell_ext` and not executed regularly for regression. We usually execute it in a full test. In `Shell_ext`, only a partial test cases are related to HA. These HA cases require variable number of test servers instead of only two.

In this document, I will only introduce the first one `HA shell` which is also an extension of SHELL test cases. For the second one `Shell_ext`, it will be introduced in seperated guide. 

Additionally, HA shell test is also a part of code coverage test.  

# 2. HA Shell Test Usage

CTP is the only test tool which is used to execute HA shell test cases.   

## 2.1 Quick Start

* ### Install CUBRID

  Please follow CUBRID installation guide to install CUBRID on both master node and slave node. Suppose that we install CUBRID at $HOME/CUBRID. Then add this line `'source .cubrid.sh'` in `~/.bash_profile`:
  ```
  source .cubrid.sh
  ```

* ### Check out HA shell cases
  ```
  cd ~
  git clone https://github.com/CUBRID/cubrid-testcases-private.git
  cd ~/cubrid-testcases-private 
  git checkout develop
  ```

* ### Install CTP
  Please refer to ["CTP Installation Guide"](ctp_install_guide.md#1-install-ctp-in-linux-platform), then create configuration file as below.  

  File ~/CTP/conf/shell.conf: 
  ```
  # These parameters are used to set cubrid.conf, cubrid_broker.conf and cubrid_ha.conf
  # We can add the parameters as needed.
  # For example, if we need change set 'error_log_size = 800000000', just need add line 'default.cubrid.error_log_size = 800000000' here.
  default.cubrid.cubrid_port_id=1568
  default.broker1.BROKER_PORT=10090
  default.broker1.APPL_SERVER_SHM_ID=10090
  default.broker2.BROKER_PORT=13091
  default.broker2.APPL_SERVER_SHM_ID=13091
  default.ha.ha_port_id=19909

  #specify the master node and slave node
  env.83.ssh.host=192.168.1.83
  env.83.ssh.relatedhosts=192.168.1.93

  # Specify the case path and exclude list file
  # We can also specify a sub path under the test case path to run a sub set of the test cases.
  scenario=${HOME}/cubrid-testcases-private/HA/shell
  testcase_exclude_from_file=${HOME}/cubrid-testcases-private/HA/shell/config/daily_regression_test_excluded_list_linux.conf

  # When the test is interrupted and started again, we can choose whether to run it continuously or re-run it.
  test_continue_yn=false

  testcase_timeout_in_secs=604800

  # When the case is failed, we can re-run it. This paramter specify the max time we want to re-run the failed case.
  testcase_retry_num=0

  # Some times there is not enough disk space on the test machine, so we need to delete all the files under the test case path after the case is run.
  delete_testcase_after_each_execution_yn=false
  enable_check_disk_space_yn=true
  owner_email=cui.man@navercorp.com

  # set test result feed back type: file or database
  feedback_type=file
  ```

* ### Start test

  ```
  cd ~/CTP/bin
  nohup ./ctp.sh shell -c ~/CTP/conf/shell.conf &
  ```
* ### Examine test results

  Please follow to general SHELL guide for it.
  
## 2.2 Test Configuration

* HA instance configuration

  As example below, these two servers create a HA instance `ha1`. The master is `192.168.1.83`, and the slave is `192.168.1.93`.

      env.ha1.ssh.host=192.168.1.83
      env.ha1.ssh.relatedhosts=192.168.1.93

# 3. Regression Test Deployment

This chapter introduces how to deploy HA regression test environment.

## 3.1 Deployment Overview

|Role|Master or Slave|User|IP|Hostname| Deployments |
|---|---|---|---|---|---|
|controller node|NA|controller|192.168.1.83|func08|CTP |
|worker node|master|ha|192.168.1.83|func08|CTP<br>cubrid-testcases-private|
|worker node|slave|ha|192.168.1.93|func18|CTP<br>cubrid-testcases-private|
|worker node|master|ha|192.168.1.84|func09|CTP<br>cubrid-testcases-private|
|worker node|slave|ha|192.168.1.94|func19|CTP<br>cubrid-testcases-private|
|worker node|master|ha|192.168.1.85|func10|CTP<br>cubrid-testcases-private|
|worker node|slave|ha|192.168.1.95|func20|CTP<br>cubrid-testcases-private|
|worker node|master|ha|192.168.1.87|func12|CTP<br>cubrid-testcases-private|
|worker node|slave|ha|192.168.1.97|func22|CTP<br>cubrid-testcases-private|
|worker node|master|ha|192.168.1.88|func13|CTP<br>cubrid-testcases-private|
|worker node|slave|ha|192.168.1.92|func17|CTP<br>cubrid-testcases-private|
|worker node|master|ha|192.168.1.89|func14|CTP<br>cubrid-testcases-private|
|worker node|slave|ha|192.168.1.96|func21|CTP<br>cubrid-testcases-private|

*Note: HA shell test and isolation test share the same controller and workers.*

## 3.2 Before Deployment

* ### Create users  

    According to above table, create all OS users.

        useradd <user>
        passwd <user>

        # set the user's password to never expire
        chage -E 2999-1-1 -m 0 -M 99999 <user>

* ### Install dependent packages

    |software|version|usage|  
    |---|---|---|  
    |jdk|1.8.0 (need larger than 1.6)|run CTP, run shell test case|  
    |lcov|lcov-1.11|run code coverage test|  
    |bc|latest version|run shell test case|  

## 3.3 Deploy Controller Node

* ### Install CTP   

    Please refer to ["CTP Installation Guide"](ctp_install_guide.md#3-install-ctp-as-regression-test-platform)

* ### Set shell configure file  

    File ~/CTP/conf/shell_template.conf:
    
      default.ssh.port=22
      default.ssh.user=ha
      default.ssh.pwd=*********
      default.cubrid.cubrid_port_id = 1568
      default.broker1.BROKER_PORT = 30090
      default.broker2.BROKER_PORT = 33091
      default.broker1.APPL_SERVER_SHM_ID=30090
      default.broker2.APPL_SERVER_SHM_ID=33091
      default.ha.ha_port_id = 59907

      env.83.ssh.host=192.168.1.83
      env.83.ssh.relatedhosts=192.168.1.93

      env.84.ssh.host=192.168.1.84
      env.84.ssh.relatedhosts=192.168.1.94

      env.85.ssh.host=192.168.1.85
      env.85.ssh.relatedhosts=192.168.1.95

      env.87.ssh.host=192.168.1.87
      env.87.ssh.relatedhosts=192.168.1.97

      env.88.ssh.host=192.168.1.88
      env.88.ssh.relatedhosts=192.168.1.92

      env.89.ssh.host=192.168.1.89
      env.89.ssh.relatedhosts=192.168.1.96

      test_continue_yn=false
      scenario=$HOME/cubrid-testcases-private/HA/shell
      testcase_exclude_from_file=$HOME/cubrid-testcases-private/HA/shell/config/daily_regression_test_excluded_list_linux.conf
      testcase_git_branch=develop

      testcase_timeout_in_secs=604800
      testcase_update_yn=true
      test_platform=linux
      test_category=ha_shell
      testcase_retry_num=0
      ignore_core_by_keywords=
      owner_email=cui.man@navercorp.com
      enable_check_disk_space_yn=true

      feedback_type=database
      feedback_db_host=192.168.1.86
      feedback_db_port=33080
      feedback_db_name=qaresu
      feedback_db_user=dba
      feedback_db_pwd=**********
      feedback_notice_qahome_url=http://192.168.1.86:6060/qaresult/shellImportAction.nhn?main_id=<MAINID>

    During runtime, `shell_template.conf` will be copied to `~/CTP/conf/shell_runtime.conf`.  

* ### Set `~/.bash_profile`  

    Set `~/.bash_profile` like this:  

      # .bash_profile

      # Get the aliases and functions
      if [ -f ~/.bashrc ]; then
              . ~/.bashrc
      fi

      # User specific environment and startup programs

      PATH=$JAVA_HOME/bin:$HOME/CTP/common/script:$PATH:$HOME/.local/bin:$HOME/bin

      export PATH

      export CTP_BRANCH_NAME="develop"
      export CTP_SKIP_UPDATE=0

* ### Create a quick start script

    File ~/start_test.sh
    
      nohup start_consumer.sh -q QUEUE_CUBRID_QA_CC_BASIC,QUEUE_CUBRID_QA_SHELL_HA_LINUX -exec run_isolation,run_shell &
    
    *Note: HA shell test and isolation test share the same controller node.*

## 3.5 Deploy Worker Nodes  

* ### Install CTP

    Please refer to ["CTP Installation Guide"](ctp_install_guide.md#3-install-ctp-as-regression-test-platform).

* ### Set `~/.bash_profile`  

    File `~/.bash_profile`:  
    
      # .bash_profile

      # Get the aliases and functions
      if [ -f ~/.bashrc ]; then
              . ~/.bashrc
      fi

      # User specific environment and startup programs

      PATH=$PATH:$HOME/.local/bin:$HOME/bin

      export CTP_HOME=$HOME/CTP
      ## init_path is used when we run shell case manually on this machine.
      export init_path=$CTP_HOME/shell/init_path

      export PATH=$CTP_HOME/bin:$CTP_HOME/common/script:$PATH

      export CTP_BRANCH_NAME="develop"
      export CTP_SKIP_UPDATE=0

      . ~/.cubrid.sh
      export GCOV_PREFIX=/home/shell
      export GCOV_PREFIX_STRIP=2
      ulimit -c unlimited

* ### Deploy test cases

      git clone --no-checkout https://github.com/CUBRID/cubrid-testcases-private.git
      cd ~/cubrid-testcases-private
      git config core.sparseCheckout true
      echo 'HA/*' > ~/cubrid-testcases-private/.git/info/sparse-checkout
      git checkout develop

* ### Make directories to store test logs

      cd
      mkdir do_not_delete_core
      mkdir ERROR_BACKUP

* ### Add `.cubrid.sh` file
    
    If CUBRID has never been installed on the machine, we need add file `'.cubrid.sh'` at $HOME path manually.  

# 4. Regression Test Sustaining

## 4.1 Start Listener

On controller node, execute the script `start_test.sh` to start listening the test message after deployment.  
This will start a HA shell test when the consumer receives the test message.

    cd ~
    sh start_test.sh

## 4.2 Sent Test Messages

* General test messages

      sender.sh QUEUE_CUBRID_QA_SHELL_HA_LINUX http://192.168.1.91:8080/REPO_ROOT/store_01/10.2.0.8330-d4d8464/drop/CUBRID-10.2.0.8330-d4d8464-Linux.x86_64.sh ha_shell default

* Code coverage test messages

      sender.sh QUEUE_CUBRID_QA_SHELL_HA_LINUX "http://192.168.1.91:8080/REPO_ROOT/store_01/10.2.0.8270-c897055/drop/CUBRID-10.2.0.8270-c897055-gcov-Linux.x86_64.tar.gz,http://192.168.1.91:8080/REPO_ROOT/store_01/10.2.0.8270-c897055/drop/cubrid-10.2.0.8270-c897055-gcov-src-Linux.x86_64.tar.gz" ha_shell default

# 5. HA shell case standards

HA shell case is a special kind of shell test case. It should follow all the SHELL test case standards. For SHELL test case standards, please refer to SHELL test guide. In this section, I will only introduce the contents which are not included in SHELL test guide.  

## 5.1 Test Case Path

* Basis of case naming

      /path/to/test_name/cases/test_name.sh     
      
    So, it's same as general SHELL test case's.      

* Test cases added for new features 

    When we need add a test case for a feature test, add a case to path like this:  
    ```
    cubrid-testcases-private/HA/shell/_{no}_{release_code}/cbrd_xxxxx_{feature}/
    ```
    with naming rules:
    ```
    structured_name_1/cases/structured_name_1.sh
    structured_name_2/cases/structured_name_2.sh
    ```

    Example:
    ```
    cubrid-testcases-private/HA/shell/_31_cherry/issue_21506_online_index
    cubrid-testcases-private/HA/shell/_31_cherry/issue_21506_online_index/cbrd_21506_01/cases/cbrd_21506_01.sh
    ```

* Test cases added for Jira issues

    When add a test case for a bug fix, add a case to path:  
    ```
    cubrid-testcases-private/HA/shell/_12_bts_issue/_{yy}_{1|2}h/
    ```
    with naming rules:
    ```
    cbrd_xxxxx/cases/cbrd_xxxxx.sh
    cbrd_xxxxx_1/cases/cbrd_xxxxx_1.sh
    cbrd_xxxxx_{issue_key}/cases/cbrd_xxxxx_{issue_keyword}.sh
    ```
    Example:
    ```
    cubrid-testcases-private/HA/shell/_12_bts_issue/_19_2h/cbrd_22207/cases/cbrd_22207.sh
    ```

## 5.2 Case Template

    #!/bin/sh
    # Initialize the environment variables, and import all the functions. 
    . $init_path/init.sh
    . $init_path/make_ha.sh

    init test
    setup_ha_environment

    # Test steps

    # Check the result
    if [condition]
    then
           write_ok
    else
           write_nok
    fi

    revert_ha_environment

    # Clean environment, such as delete the jave class files

    # 'finish' is a function in init.sh, which will revert all the conf files to the original status.
    finish
 
## 5.3 Common Functions

* ### wait_for_active

    Wait server 'hatestdb' to be changed to active mode on current node.  
    If the server is changed to active to active mode whthin 120 seconds, the wait loop will break.   
    Otherwise, this function will print "NOK: db server is not active after long time".  
    This is usually used before we update data in hatestdb.

* ### wait_for_slave_active

    Wait server 'hatestdb' to be changed to active mode on slave node.  
    If the server is changed to active to active mode whthin 120 seconds, the wait loop will break.   
    Otherwise, this function will print "NOK: db server is not active after long time".  
    This is usually used after a failover.  

* ### run_on_slave

      alias run_on_slave='${init_path}/../../common/script/run_remote_script -host $SLAVE_SERVER_IP -port $SLAVE_SERVER_SSH_PORT -user $SLAVE_SERVER_USER -password "$SLAVE_SERVER_PW"'

  Examples:

      run_on_slave -c "cubrid hb stop"
      run_on_slave -c "grep 'Process event: Encountered an unrecoverable error and will shut itself down' $CUBRID/log/${dbname}@${masterHostName}_copylogdb.err | wc -l"
      run_on_slave -c "cd $HOME; sh monitor_bug_11301.sh $dbname $masterHostName $port" > slave.log 2>&1

* ### run_download_on_slave

      alias run_download_on_slave='${init_path}/../../common/script/run_download -host $SLAVE_SERVER_IP -port $SLAVE_SERVER_SSH_PORT -user $SLAVE_SERVER_USER -password "$SLAVE_SERVER_PW"'

  Examples:

      run_download_on_slave -from ~/slave.log -to ./
      run_download_on_slave -from $CUBRID/log/hatestdb@${masterHostName}_copylogdb.err -to .

* ### run_upload_on_slave

      alias run_upload_on_slave='${init_path}/../../common/script/run_upload -host $SLAVE_SERVER_IP -port $SLAVE_SERVER_SSH_PORT -user $SLAVE_SERVER_USER -password "$SLAVE_SERVER_PW"'

Examples:

      run_upload_on_slave -from $CUBRID/conf/cubrid.conf -to $CUBRID/conf/
      run_upload_on_slave -from ./monitor_bug_11301.sh -to $HOME/

* ### setup_ha_environment  

    Used to setup a HA environment which contains one master node, and one slave node. The current machine is used as the master node, and slave node is set in ~/CTP/shell/init_path/HA.properties.  
Example for HA.properties:  

      ##Configure HA enviorment.
      MASTER_SERVER_IP = 192.168.1.83
      MASTER_SERVER_USER = ha
      MASTER_SERVER_PW = PASSWORD
      MASTER_SERVER_SSH_PORT = 22
      SLAVE_SERVER_IP = 192.168.1.93
      SLAVE_SERVER_USER = ha
      SLAVE_SERVER_PW = PASSWORD
      SLAVE_SERVER_SSH_PORT = 22
      #set port numbers according to different users
      CUBRID_PORT_ID = 1568
      HA_PORT_ID = 59907
      MASTER_SHM_ID = 1568
      BROKER_PORT1 = 30090
      APPL_SERVER_SHM_ID1 = 30090
      BROKER_PORT2 = 33091
      APPL_SERVER_SHM_ID2 = 33091
      CM_PORT = 8001

  If we need to run a HA shell manually, we need configure `'HA.properties'` first. In regression test,  file `'HA.properties'` is configured by CTP. CTP reads information from file `'~/CTP/conf/shell_runtime.conf'` on controller node, and synchronizes file `'$init_path/HA.properties'` on each master node. 

* ### revert_ha_environment  

    Revert all config files to the original status on master node and slave node, and delete database on both nodes.

* ### wait_for_slave

    Used to wait the data replication finished on slave node. If we need to check the data on slave node, we need to check the data after the data replication finished. So this function is required.

* ### wait_for_slave_failover  

    When a failover occurs, we need use function `wait_for_slave_failover` to wait the new slave node (previous master node) to finish the data replication before we check the data.

* ### format_hb_status
  
  Used to format the hostname, process id, path for 'cubrid hb status' output.  

  Usage:

      cubrid hb status > test.log
      format_hb_status test.log

  Before format:

      $ cat test.log 

       HA-Node Info (current func09, state master)
         Node func19 (priority 2, state slave)
         Node func09 (priority 1, state master)


       HA-Process Info (master 12167, state master)
         Applylogdb hatestdb@localhost:/home/ha/CUBRID/databases/hatestdb_func19 (pid 12407, state registered)
         Copylogdb hatestdb@func19:/home/ha/CUBRID/databases/hatestdb_func19 (pid 12405, state registered)
         Server hatestdb (pid 12175, state registered_and_active)

      @ cubrid heartbeat status


  After format:

      $ cat test.log             

       HA-Node Info (current host1, state master)
         Node host2 (priority 2, state slave)
         Node host1 (priority 1, state master)


       HA-Process Info (master , state master)
         Applylogdb hatestdb@localhost:CUBRID/databases/hatestdb_host2 (pid , state registered)
         Copylogdb hatestdb@host2:CUBRID/databases/hatestdb_host2 (pid , state registered)
         Server hatestdb (pid , state registered_and_active)

      @ cubrid heartbeat status

* ### add_ha_db
  
  Used to add new database after `setup_ha_environment`. It will create database on master node and slave node, add the database name in ha_db_list in $CUBRID/conf/cubrid_ha.conf, and restart the heartbeat on both nodes.  

  Example:  
    
      #!/bin/bash

      . $init_path/init.sh
      init test

      . $init_path/make_ha.sh
      dbname=tdb01
      setup_ha_environment
      add_ha_db tdb02 tdb03

  Three databases will be created in this case: tdb01, tdb02 and tdb03. 
