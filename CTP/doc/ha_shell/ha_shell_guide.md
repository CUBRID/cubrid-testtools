# HA Shell Test Guide
# 1 Test Introduction
HA Shell test suit is used to test CUBRID HA features.  
To run a HA shell case, we usually need at least two machines. But sometimes, we need more than two machines.  
So HA shell test is divided to two categories:    
1. Test the HA shell cases which can be run on two machines. The cases are put in 'HA' case path. And these cases are executed for each CI build.  
2. Test the other HA shell cases. The cases are put in 'shell_ext' path. And these cases are usually run before a release.  

In this document, I will mainly introduce the first kind of test. This kind of test is almost the same as shell test.  
For the second kind of test , I will introduce it an the end of this document.  

# 2 Tools Introduction
CTP is the only test tool which is used in HA shell test.   
Source URL: [https://github.com/CUBRID/cubrid-testtools](https://github.com/CUBRID/cubrid-testtools)

# 3 Test Deployments
## 3.1 create and set users  
### controller node
We need create a new user: controller.  
Login root user and execute:  
```
sudo useradd controller
```
Set password as our common password for user controller.  
```
sudo passwd controller
```
 Set the user's password to never expire.  
 ```
 sudo chage -E 2999-1-1 -m 0 -M 99999 controller
 ```
 
 ### worker nodes
We need create two new users: ha, dev.
Login root user and execute:  
```
sudo useradd ha
sudo useradd dev
```
Set password as our common password for user shell and user dev.  
```
sudo passwd ha
sudo passwd dev
```
 Set these users' password to never expire.  
 ```
 sudo chage -E 2999-1-1 -m 0 -M 99999 ha
 sudo chage -E 2999-1-1 -m 0 -M 99999 dev
 ```
 ## 3.2 install software packages
Required software packages: jdk, lcov, bc, lrzsz.   

|software|version|usage|  
|---|---|---|  
|jdk|1.8.0 (need larger than 1.6)|run CTP, run shell test case|  
|lcov|lcov-1.11|run code coverage test|  
|bc|latest version|run shell test case|  
|lrzsz|latest version|upload/download files|  

These software packages are installed by root user and can be used by all the users.  

## 3.3 Deploy controller node
### install CTP 
**Step 1: download CTP**  
*method 1: install from git*    
```
cd ~
git colne https://github.com/CUBRID/cubrid-testtools.git
cd cubrid-testtools
git checkout develop
cp -rf ~/cubrid-testtools/CTP ~
```  
*method 2: install from our server*     
```
cd ~
wget http://192.168.1.91:8080/REPO_ROOT/CTP.tar.gz
tar zxvf CTP.tar.gz
```  
Usually, we use method 2.

**Step 2: set CTP configuration files**    
*~/CTP/conf/common.conf*   
```
git_user=cubridqa
git_pwd=GITPASSWORD
git_email=dl_cubridqa_bj_internal@navercorp.com
default_ssh_pwd=PASSWORD
default_ssh_port=22

grepo_service_url=rmi://192.168.1.91:11099
coverage_controller_pwd=PASSWORD

qahome_db_driver=cubrid.jdbc.driver.CUBRIDDriver
qahome_db_url=jdbc:cubrid:192.168.1.86:33080:qaresu:dba::
qahome_db_user=dba
qahome_db_pwd=

qahome_server_host=192.168.1.86
qahome_server_port=22
qahome_server_user=qahome
qahome_server_pwd=PASSWORD

activemq_user=admin
activemq_pwd=admin
activemq_url=failover:tcp://192.168.1.91:61616?wireFormat.maxInactivityDurationInitalDelay=30000

mail_from_nickname=CUBRIDQA_BJ
mail_from_address=dl_cubridqa_bj_internal@navercorp.com
```
*~/CTP/conf/shell_template.conf* 
```
default.ssh.port=22
default.ssh.user=ha
default.ssh.pwd=PASSWORD
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

#env.89.ssh.host=192.168.1.89
#env.89.ssh.relatedhosts=192.168.1.97

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
feedback_db_pwd=
feedback_notice_qahome_url=http://192.168.1.86:8080/qaresult/shellImportAction.nhn?main_id=<MAINID>
```
shell_template.conf will be copied to \~/CTP/conf/shell_runtime.conf when test is started.  
For more details about the parameters, please refer to CTP guide. 

### set ~/.bash_profile 
*~/.bash_profile*  
```
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
```

### create a script to start consumer
~/start_test.sh
```
nohup start_consumer.sh -q QUEUE_CUBRID_QA_SHELL_HA_LINUX -exec run_shell &
```
In our regression test, ha shell test and isolation test share the same controller and worker machines. So the script is like this:  
```
nohup start_consumer.sh -q QUEUE_CUBRID_QA_CC_BASIC,QUEUE_CUBRID_QA_SHELL_HA_LINUX -exec run_isolation,run_shell &
```
Execute the script to start listening the test message after deployment. This will start a shell test when the consumer receive the test message.
```
cd ~
sh start_test.sh
```

## 3.4 Deploy worker node  
### install CTP
This step is the same as 'install CTP' on controller node. Plese refer to [install CTP](#install_CTP).  
### set ~/.bash_profile
*~/.bash_profile*
```
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
export GCOV_PREFIX=/home/ha
export GCOV_PREFIX_STRIP=2
ulimit -c unlimited
```

### deploy test cases
```
git clone --no-checkout https://github.com/CUBRID/cubrid-testcases-private-ex.git
cd ~/cubrid-testcases-private-ex
git config core.sparseCheckout true
echo 'HA/*' > ~/cubrid-testcases-private-ex/.git/info/sparse-checkout
git checkout develop
```

### make directories for test
```
cd
mkdir do_not_delete_core
mkdir ERROR_BACKUP
```

### create .cubrid.sh file 
If cubrid has never been installed on the machine, we need create file '.cubrid.sh' at $HOME path manually
*.cubrid.sh file:*   
```
CUBRID=/home/shell/CUBRID
CUBRID_DATABASES=$CUBRID/databases
if [ "x${LD_LIBRARY_PATH}x" = xx ]; then
  LD_LIBRARY_PATH=$CUBRID/lib
else
  LD_LIBRARY_PATH=$CUBRID/lib:$LD_LIBRARY_PATH
fi
SHLIB_PATH=$LD_LIBRARY_PATH
LIBPATH=$LD_LIBRARY_PATH
PATH=$CUBRID/bin:$PATH
export CUBRID
export CUBRID_DATABASES
export LD_LIBRARY_PATH
export SHLIB_PATH
export LIBPATH
export PATH
```

# 4 Regression Test
We execute HA shell test for each CI build, and execute code coverage test monthly. Both of these test are started automatically when the controller receive a test message. We just need to prepare the conf files, verify the test results, and report issues.
