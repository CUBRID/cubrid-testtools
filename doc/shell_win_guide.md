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

* ### Turn Off `'Windows Firewall'` on workers

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
    CTP_BRANCH_NAME = develop
    CTP_SKIP_UPDATE = 0
    PATH = C:\CUBRID\bin\;%MINGW_PATH%\bin;%MINGW_PATH%\x86_64-w64-mingw32\bin;%MINGW_PATH%\libexec\gcc\x86_64-w64-mingw32\7.2.0;%JAVA_HOME%\bin;C:\cygwin64\bin%SystemRoot%\system32;%SystemRoot%;%SystemRoot%\System32\Wbem;%SYSTEMROOT%\System32\WindowsPowerShell\v1.0\;C:\Program Files\Git\cmd
    
## 3.7 Create quick start script on controller

Log into controller node. Create quick start script file.

File `~/start_test.sh`:

    nohup start_consumer.sh -q QUEUE_CUBRID_QA_SHELL_WIN64 -exec run_shell &

# 4 Regression Test Sustaining
Please refer to shell guide: ['Regression Test Sustaining'](https://github.com/CUBRID/cubrid-testtools/blob/develop/doc/shell_guide.md#4-regression-test-sustaining)

## 3.9 Start Rmi Service
open a cmd session:
```
>cd c:\CTP\shell
>start_service.bat
```



## Windows Shell Message
We use `.zip` installation file in test.
```
sender.sh QUEUE_CUBRID_QA_SHELL_WIN64 http://192.168.1.91:8080/REPO_ROOT/store_01/10.2.0.8462-fad7030/drop/CUBRID-Windows-x64-10.2.0.8462-fad7030.zip shell default
```

# 5 Shell Case Standards
Please refer to shell guide: ['Shell Case Standards'](https://github.com/CUBRID/cubrid-testtools/blob/develop/doc/shell_guide.md#5-shell-case-standards)

# Appendix A: Install Old Package Version of Cygwin
## Why to Install the Old Versions
Take 'grep' as an example. Sometimes, '\r' is appended in the texts on windows. But in ANNOUNCEMENT ['Updated \[test\]: grep\-3.0\-2'](http://cygwin.1069669.n5.nabble.com/ANNOUNCEMENT\-Updated\-test\-grep\-3\-0\-2\-td132384.html), it is said:   
```
This build modifies the behavior of grep to no longer force text mode on 
binary-mounted file descriptors.  Since this includes pipelines by 
default, this means that if you pipe text data through a pipeline (such 
as the output of a windows program), you may need to insert a call to 
d2u to sanitize your input before passing it to grep.
```
We do not intend to modify test cases, since the cases are used both by linux and windows platform.

So we need to use grep before 3.0-2.  

## How to Install the Old Versions
Take 'grep' as an example.  
*Method 1, Install from Internet*
1. start cygwin installation file 'setup-x86_64.exe'
2. In step 'Choose A Download Source', select 'Install from Internet'
3. In step 'Select Packages', in the field of 'View', choose 'Category' or 'Full', and in the field of 'Search', input 'grep'.
Then, find the line of 'grep: search for regular expression matches in test files'.
Click the column of "New" (the second column) on this line, until 3.0-1 appears.
* If '3.0-1' can be shown automatically, choose 'Pending' in 'View' field, to check the pending list is correct:   
a. this package is in the list  
b. if there are other packages which you do not want to update this time, please click the second columns of these lines on by one to mark them as 'keep'.  
**Note**: Check the pending list is important, since new versions of other packages are put in pending list and will be updated automatically.
For example, last time, I reverted  'gawk' to old version, and this time, I try to install old version of 'grep' and forget to check the pending list, 'gawk' will be updated to the newer version at this time.  
So we'd better to install 'gawk', 'grep', 'sed' at once, instead of install them separately. 

* If '3.0-1' cannot be shown automatically, cancel this installation, and use the second installation method below.

4. use the default options in the following steps

*Method 2, Install from Local Directory*  
When the required old versions cannot be found in Method 1, we need to install it from local directory.
1. Find your previous dowload/installation path, like `'http%3a%2f%2fcygwin.mirror.constant.com%2f'`
2.  Add the previous grep package in the installation path.  
* First, download the previous package of "grep" from ['http://mirrors.opencas.org/cygwin/x86_64/release/grep/'](http://mirrors.opencas.org/cygwin/x86_64/release/grep/)  
Example: http://mirrors.opencas.org/cygwin/x86_64/release/grep/grep-3.0-1.tar.xz  
* put this package in the previous installation path  
Example: `C:\winshell_setup\http%3a%2f%2fcygwin.mirror.constant.com%2f\x86_64\release\grep`  
* edit the setup.ini file  
Example:   `C:\winshell_setup\http%3a%2f%2fcygwin.mirror.constant.com%2f\x86_64\setup.ini`  
Add a `'[prev]'` section for this previous pack in "@ grep" part. If it already exists, just ignore this step.
```
[prev]
version: 3.0-1
install: x86_64/release/grep/grep-3.0-1.tar.xz 361740 a34cf6fc689a62005f7a33287c86419d7a24d262f694489af0dc864affd031f61f9f15970f2f211f335aa7a0234211facf98cc76d83639c7c631ffe5386b00ac
source: x86_64/release/grep/grep-3.0-1-src.tar.xz 1379944 9d7b08c7e21d0d5058faff728dc575aab95d8c0ab8f70897ff0b2910f32b7f8dd1cdab530564a2786ffb24f676fa16bf7a51d8e0fb1488d2971dcc1d1c443d99
```

3. start setup-x86_64.exe  
* In step "Choose A Download Source":
Choose "Install from Local Directory"
* In step "Select Local Package Directory":
Specify the path as `C:\winshell_setup\http%3a%2f%2fcygwin.mirror.constant.com%2f`  
* use the default options in the following steps, until you met step `'Select Packages'`.  
Choose the correct version of "grep", in this case, I select `'3.0-1'`.
* use the default options in the following steps

