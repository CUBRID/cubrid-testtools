# Windows Shell Test Guide
# 1 Test Objective
Shell test suite is used to execute CUBRID functional test in a very flexible way. Shell test cases are written in Linux shell programing language. It can easily integrate other programing languages, like Java, C, Perl. With shell test cases, almost all features and performance which cannot be tested by SQL test or other test suites can be tested and automated. For examples:
* check whether a cubrid utility works well  
* check whether the error message of a sql statement is expected  
* check a specific cubrid parameter works

Shell test case path is located in [https://github.com/CUBRID/cubrid-testcases-private-ex/tree/develop/shell](https://github.com/CUBRID/cubrid-testcases-private-ex/tree/develop/shell).
Shell test is contained by daily regression test. The test cases are run on both Linux platform and Windows platform. This guide will introduce the different parts between windows shell test and linux shell test.


# 2 Windows Shell Test via CTP
Please refer to shell_guide: [Shell Test via CTP](https://github.com/CUBRID/cubrid-testtools/blob/develop/doc/shell_guide.md#2-shell-test-via-ctp)  
`shell_template.conf` file for windows shell test:  
```
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

owner_email=Mandy<cui.man@navercorp.com>

git_user=cubridqa
git_email=dl_cubridqa_bj_internal@navercorp.com
git_pwd=N6P0Sm5U7h

agent_protocol=rmi
enable_status_trace_yn=fasle
large_space_dir=/c/big_space_stage

feedback_type=database
feedback_notice_qahome_url=http://192.168.1.86:6060/qaresult/shellImportAction.nhn?main_id=<MAINID>
feedback_db_host=192.168.1.86
feedback_db_port=33080
feedback_db_name=qaresu
feedback_db_user=dba
feedback_db_pwd=
```

# 3 Regression Test Deployment
## 3.1 Test Machines
|Role|User|IP|Hostname|
|---|---|---|---|
|controller node|wshell_ctrl|192.168.1.90|qa02|
|worker node|qa|192.168.1.162|winfunc02|
|worker node|qa|192.168.1.163|winfunc03|
|worker node|qa|192.168.1.164|winfunc04|
|worker node|qa|192.168.1.165|winfunc05|
|worker node|qa|192.168.1.166|winfunc06|
|worker node|qa|192.168.1.167|winfunc07|
|worker node|qa|192.168.1.168|winfunc08|
|worker node|qa|192.168.1.169|winfunc09|
|worker node|qa|192.168.1.170|winfunc10|


## 3.2 Create and Set Users  
### Controller Node
We need create a new user `'wshell_ctrl'`.  
Then login root user and execute:  
```
sudo useradd wshell_ctrl
```
Set password as our common password for user `'wshell_ctrl'`.  
```
sudo passwd wshell_ctrl
```
 Set the user's password to never expire.  
 ```
 sudo chage -E 2999-1-1 -m 0 -M 99999 wshell_ctrl
 ```
 
### Worker Nodes
We need create a new user `'qa'`.  
Set `'qa'` as `'Administrator' ` type.  
Set password as our common password for user `'qa'`.

## 3.3 Install Software Packages
Required software packages:    

|software|version|usage|  
|---|---|---|  
|jdk|1.8.0 (need larger than 1.6)|run CTP, run shell test case|  
|visual studio 2017|community version|make_locale|  
|mingw-w64|latest version|gcc/g++|  
|cygwin|latest version|execute linux command|
|git|atest version |checkout CTP/test cases |
|text editor software(such as Nodepad++)|free version| edit linux format text on windows|

### Install JDK
Jdk version must greater than 1.6.0_07. We use jdk-6u45-windows-x64.exe.

### Install visual studio 2017
Visual studio is used by make_local.bat.  
When install visual studio 2017, Choose 'Workloads' view(tab), in 'Windows (3)' section, Choose "Desktop development with C++", then click 'Install' or 'Modify' to start the installation.
After installation, check system variable '%VS140COMNTOOLS%'
1) If 'VS140COMNTOOLS' is not add to the system variables automatically, please add it manually.  
Variable name: `VS140COMNTOOLS`  
Variable value: `C:\Program Files (x86)\Microsoft Visual Studio\2017\Community\Common7\Tools
VS140COMNTOOLS ` 
2) If 'VS140COMNTOOLS' is add to the system variables automatically, please check its value. Sometimes, the value is not correct for make_locale to use it. In this situation, please change it to the correct one.  
e.g.  
Wrong: `C:\Program Files (x86)\Microsoft Visual Studio 14.0\Common7\Tools\`  
Correct: `C:\Program Files (x86)\Microsoft Visual Studio\2017\Community\Common7\Tools\`  
(Note: the last '\\' is required)

### install mingw-w64
The gcc, g++, mingw packages in sygwin cannot support multi threads well, so we need to install mingw-w64 tool.  
Please use installation file 'mingw-w64-install.exe' to install MingW-W64.  
In the installation wizard, select 'X86_64' in 'Architecture' field.  
After installation, add a system variable:  
key: `MINGW_PATH`  
value: `C:\mingw-w64\mingw64`  
And add `%MINGW_PATH%` related path (%MINGW_PATH%\bin;%MINGW_PATH%\x86_64-w64-mingw32\bin;%MINGW_PATH%\libexec\gcc\x86_64-w64-mingw32\7.2.0 in system variable `path`.  
For example:  
key: `path`   
valule: `C:\CUBRID\bin\;%JAVA_HOME%\bin;%MINGW_PATH%\bin;%MINGW_PATH%\x86_64-w64-mingw32\bin;%MINGW_PATH%\libexec\gcc\x86_64-w64-mingw32\7.2.0;C:\cygwin64\bin;%SystemRoot%\system32;%SystemRoot%;%SystemRoot%\System32\Wbem;%SYSTEMROOT%\System32\WindowsPowerShell\v1.0\;C:\Program Files\Git\cmd;C:\Program Files\TortoiseSVN\bin`

### install cygwin
1. We need choose this packages manually since they will not be installed by default: wget, zip, unzip, dos2unix, bc, expect.
gcc and mingw packages do not need to be installed.  
2. Edit /etc/fstab. Change `'none /cygdrive cygdrive binary,posix=0,user 0 0'` to `'none / cygdrive binary,noacl,posix=0,user 0 0'`  
(Note: use '/' in the second column, and add noacl)  
3. check the versions of these packages (or components): gawk, grep, sed.  
We must use the versions before this versions list:   
grep: 3.0-2  
gawk: 4.1.4-3  
sed: 4.4-1  
In current test, we use:  
gawk: 4.1.3-1  
grep: 3.0-1  
sed: 4.2.2-3  
To install the old versions, please refer: ['Install old packages of cygwin'](TBD)

### install git
official website for git: [https://git-for-windows.github.io/](https://git-for-windows.github.io/)
In the installation wizard, choose these options:
* 'Adjusting your PATH environment', choose 'Use Git from the Windows Command Prompt'
* 'Confifuring the line ending conversions', choose 'Checkout as-is, commit as-is'

### install a text editor tool
We need a text editor tool which can used in both linux and windows format.  
For example: Notepad++

## 3.4 Install cubrid
Use the msi installation file to install cubrid for the first time.

## 3.5 Set `'Environment Variables'`
1. After install cubrid by msi file, these system parameter will be added automatically:

|key|value|  
|---|---|  
|CUBRID|C:\\CUBRID\\|  
|CUBRID_DATABASES|C:\\CUBRID\\databases|
But we need to chane value of variable "CUBRID" to
|key|value|  
|---|---|  
|CUBRID|C:/CUBRID|  

2. Add new 'System variables':

|key|value|  
|---|---|  
|JAVA_HOME|C:\Program Files\Java\jdk1.6.0_45|
|CTP_BRANCH_NAME|develop|
|CTP_SKIP_UPDATE|0|

3. Edit `'path'`
add '%JAVA_HOME%\bin C:\cygwin64\bin' in the `'path'` at the correct location.  
Example:  
The original path is:
```
C:\CUBRID\bin\;%SystemRoot%\system32;%SystemRoot%;%SystemRoot%\System32\Wbem;%SYSTEMROOT%\System32\WindowsPowerShell\v1.0\;C:\Program Files\Git\cmd
```
After adding `%JAVA_HOME%\bin` and `C:\cygwin64\bin`:  
```
C:\CUBRID\bin\;%JAVA_HOME%\bin;C:\cygwin64\bin;%SystemRoot%\system32;%SystemRoot%;%SystemRoot%\System32\Wbem;%SYSTEMROOT%\System32\WindowsPowerShell\v1.0\;C:\Program Files\Git\cmd
```

## 3.6 Set CTP
Edit the configuration files:  
* common.conf: the same as linux shell  
* shell_agent.conf: add the controller ip in agent_whitelist_hosts  
Example:   
```
agent_whitelist_hosts=127.0.0.1,192.168.1.1,10.99.116.65
```
 ## 3.7 Deploy Test Cases
 ```
 cd /c
git clone --no-checkout https://github.com/CUBRID/cubrid-testcases-private-ex.git
cd cubrid-testcases-private-ex
git config core.sparseCheckout true
echo 'shell/*' > .git/info/sparse-checkout
git checkout develop
ls shell/
git remote set-url origin https://github.com/CUBRID/cubrid-testcases-private-ex.git
```

## 3.8 Turn Off `'Windows Firewall'`

## 3.9 Start Rmi Service
open a cmd session:
```
>cd c:\CTP\shell
>start_service.bat
```

# 4 Regression Test Sustaining
Please refer to shell guide: ['Regression Test Sustaining'](https://github.com/CUBRID/cubrid-testtools/blob/develop/doc/shell_guide.md#4-regression-test-sustaining)

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

