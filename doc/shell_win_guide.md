# Windows Shell Test Guide
# 1. Test Objective

Windows Shell test is performed to test CUBRID adaptability on Windows platform. It uses the same test cases as introduced in SHELL test guide for Linux platform. This guide will introduce the different parts between Windows Shell test and Linux Shell test.

# 2 Windows Shell Test via CTP
Please refer to [SHELL guide for Linux platform](shell_guide.md#2-shell-test-via-ctp).

# 3. Regression Test Deployment

## 3.1 Test Machines

|Role|User|IP|Hostname| Deployments|
|---|---|---|---|---|
|controller node|wshell_ctrl|192.168.1.90|qa02| CTP |
|worker node|qa|192.168.1.162|winfunc02|CTP<br>cubrid-testcases-private-ex|
|worker node|qa|192.168.1.163|winfunc03|CTP<br>cubrid-testcases-private-ex|
|worker node|qa|192.168.1.164|winfunc04|CTP<br>cubrid-testcases-private-ex|
|worker node|qa|192.168.1.165|winfunc05|CTP<br>cubrid-testcases-private-ex|
|worker node|qa|192.168.1.166|winfunc06|CTP<br>cubrid-testcases-private-ex|
|worker node|qa|192.168.1.167|winfunc07|CTP<br>cubrid-testcases-private-ex|
|worker node|qa|192.168.1.168|winfunc08|CTP<br>cubrid-testcases-private-ex|
|worker node|qa|192.168.1.169|winfunc09|CTP<br>cubrid-testcases-private-ex|
|worker node|qa|192.168.1.170|winfunc10|CTP<br>cubrid-testcases-private-ex|

Note: Current controller is deployed on Linux platform. All workers are deployed on Windows platform.

## 3.2 Before Deployment

* ### Create users

  According to above table, create all OS users.

  **Controller node:**

      useradd wshell_ctrl
      passwd wshell_ctrl

      # set the user's password to never expire
      chage -E 2999-1-1 -m 0 -M 99999 wshell_ctrl

  **Worker nodes:**

  We need create a new user `'qa'`. Grant `'qa'` as `'Administrator'` permission. Set password as our common password for user `'qa'`.

* ### Turn off `'Windows Firewall'` on workers

* ### Recommend to install a text editor tool
 
  We need a text editor tool which can used in both linux and windows format. For example, `Notepad++`.

* ### Install mingw-w64
  The gcc, g++, mingw packages in cygwin cannot support multiple threads well, so we need to install mingw-w64 tool. Please use installation file `mingw-w64-install.exe` to install `MingW-W64`. In the installation wizard, select `'X86_64'` in `'Architecture'` field. After installation, add a system variable:  
  
      MINGW_PATH = C:\mingw-w64\mingw64
      PATH = %MINGW_PATH%\bin;%MINGW_PATH%\x86_64-w64-mingw32\bin;%MINGW_PATH%\libexec\gcc\x86_64-w64-mingw32\7.2.0;%PATH%

## 3.3 Install CUBRID

Use CUBRID MSI package to install CUBRID for the first time.

## 3.4 Deploy CTP

* Controller

  Please follow [this guide](ctp_install_guide.md#3-install-ctp-as-regression-test-platform) to install CTP as controller role on Linux platform.
  
  Create test configuration file.

  File ~/CTP/conf/shell_template.conf:  

      default.cubrid.cubrid_port_id=1568
      default.broker1.BROKER_PORT=30090
      default.broker2.BROKER_PORT=33091
      default.ha.ha_port_id=59909

      env.win162.ssh.host=192.168.1.162
      env.win162.ssh.port=10095
      env.win162.ssh.user=qa
      env.win162.ssh.pwd=PASSWORD

      env.win163.ssh.host=192.168.1.163
      env.win163.ssh.port=10095
      env.win163.ssh.user=qa
      env.win163.ssh.pwd=PASSWORD

      env.win164.ssh.host=192.168.1.164
      env.win164.ssh.port=10095
      env.win164.ssh.user=qa
      env.win164.ssh.pwd=PASSWORD

      env.win165.ssh.host=192.168.1.165
      env.win165.ssh.port=10095
      env.win165.ssh.user=qa
      env.win165.ssh.pwd=PASSWORD

      env.win166.ssh.host=192.168.1.166
      env.win166.ssh.port=10095
      env.win166.ssh.user=qa
      env.win166.ssh.pwd=PASSWORD

      env.win167.ssh.host=192.168.1.167
      env.win167.ssh.port=10095
      env.win167.ssh.user=qa
      env.win167.ssh.pwd=PASSWORD

      env.win168.ssh.host=192.168.1.168
      env.win168.ssh.port=10095
      env.win168.ssh.user=qa
      env.win168.ssh.pwd=PASSWORD


      env.win169.ssh.host=192.168.1.169
      env.win169.ssh.port=10095
      env.win169.ssh.user=qa
      env.win169.ssh.pwd=PASSWORD

      env.win170.ssh.host=192.168.1.170
      env.win170.ssh.port=10095
      env.win170.ssh.user=qa
      env.win170.ssh.pwd=PASSWORD

      scenario=/c/cubrid-testcases-private-ex/shell
      testcase_exclude_from_file=/c/cubrid-testcases-private-ex/shell/config/daily_regression_test_excluded_list_windows.conf
      #testcase_workspace_dir=/c/workspace/scenario/shell
      test_continue_yn=false
      cubrid_download_url=http://192.168.1.91:8080/REPO_ROOT/store_01/10.2.0.8462-fad7030/drop/CUBRID-Windows-x64-10.2.0.8462-fad7030.zip
      testcase_update_yn=true
      testcase_git_branch=develop
      testcase_timeout_in_secs=604800
      test_platform=windows
      test_category=shell
      testcase_exclude_by_macro=WINDOWS_NOT_SUPPORTED
      testcase_retry_num=0
      delete_testcase_after_each_execution_yn=false
      #delete_testcase_after_each_execution_yn=true
      enable_check_disk_space_yn=true

      owner_email=<owner e-mail>

      git_user=<git user>
      git_email=<git e-mail>
      git_pwd=********

      agent_protocol=rmi
      enable_status_trace_yn=fasle
      large_space_dir=/c/big_space_stage

      feedback_type=database
      feedback_notice_qahome_url=http://192.168.1.86:6060/qaresult/shellImportAction.nhn?main_id=<MAINID>
      feedback_db_host=192.168.1.86
      feedback_db_port=33080
      feedback_db_name=qaresu
      feedback_db_user=dba
      feedback_db_pwd=********

* Workers

  Please follow [this guide](ctp_install_guide.md#2-install-ctp-in-windows-platform) to install CTP as worker role on Windows platform.

  And common configuration file is also required.

  File `CTP/conf/common.conf`:

      git_user=<git user>
      git_pwd=<git password>
      git_email=<email address>
      default_ssh_pwd=<password for ssh connect>
      default_ssh_port=<port for ssh connect>

      # Update CTP itself from local repository
      grepo_service_url=rmi://192.168.1.91:11099
      coverage_controller_pwd=<ssh password for code coverage controller>

      cubrid_build_list_url=http://192.168.1.91:8080/REPO_ROOT/list.jsp

      # Define JDBC parameters to QA home database server
      qahome_db_driver=cubrid.jdbc.driver.CUBRIDDriver
      qahome_db_url=jdbc:cubrid:192.168.1.86:33080:qaresu:dba::
      qahome_db_user=dba
      qahome_db_pwd=

      # Define SSH connect to QA homepage server
      qahome_server_host=192.168.1.86
      qahome_server_port=22
      qahome_server_user=<user for ssh connect>
      qahome_server_pwd=<password for ssh connect>

      # Define ActiveMQ parameters for message service
      activemq_user=<user>
      activemq_pwd=<password>
      activemq_url=failover:tcp://192.168.1.91:61616?wireFormat.maxInactivityDurationInitalDelay=30000

      mail_from_nickname=CUBRIDQA_BJ
      mail_from_address=dl_cubridqa_bj_internal@navercorp.com

  File `~/CTP/common/shell_agent.conf`:

      agent_whitelist_hosts=127.0.0.1,192.168.1.90

## 3.5 Check out Test Cases
 
    cd /c
    git clone --no-checkout https://github.com/CUBRID/cubrid-testcases-private-ex.git
    cd cubrid-testcases-private-ex
    git config core.sparseCheckout true
    echo 'shell/*' > .git/info/sparse-checkout
    git checkout develop
    ls shell/
    git remote set-url origin https://github.com/CUBRID/cubrid-testcases-private-ex.git

## 3.6 Configure Environment Variables

Final variables:

    CUBRID = C:/CUBRID  # Do not use like 'C:\\CUBRID'
    CUBRID_DATABASES = C:\\CUBRID\\databases
    JAVA_HOME = C:\Program Files\Java\jdk1.6.0_45
    CTP_HOME = $HOME/CTP
    CTP_BRANCH_NAME = develop
    CTP_SKIP_UPDATE = 0
    PATH = C:\CUBRID\bin\;%MINGW_PATH%\bin;%MINGW_PATH%\x86_64-w64-mingw32\bin;%MINGW_PATH%\libexec\gcc\x86_64-w64-mingw32\7.2.0;%JAVA_HOME%\bin;C:\cygwin64\bin%SystemRoot%\system32;%SystemRoot%;%SystemRoot%\System32\Wbem;%SYSTEMROOT%\System32\WindowsPowerShell\v1.0\;C:\Program Files\Git\cmd
    
## 3.7 Create quick start script on controller

Log into controller node. Create quick start script file.

File `~/start_test.sh`:

    nohup start_consumer.sh -q QUEUE_CUBRID_QA_SHELL_WIN64 -exec run_shell &

# 4. Regression Test Sustaining

Please refer to general SHELL guide for ['Regression Test Sustaining'](shell_guide.md#4-regression-test-sustaining).

## 4.1 Start Listener

* Controller 

      cd ~
      nohup sh start_test.sh&

* Workers

  Keep agent as daemon process on all workers. Open a cmd console:

      >cd c:\CTP\shell
      >start_service.bat

## 4.2 Send Test Messages

We use `.zip` installation file in test.

    sender.sh QUEUE_CUBRID_QA_SHELL_WIN64 http://192.168.1.91:8080/REPO_ROOT/store_01/10.2.0.8462-fad7030/drop/CUBRID-Windows-x64-10.2.0.8462-fad7030.zip shell default

# 5. Shell Case Standards

Be same as it in [SHELL guide for Linux platform](shell_guide.md#5-shell-case-standards).
