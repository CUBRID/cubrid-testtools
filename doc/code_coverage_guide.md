# 1. Test Objective

Code coverage is a measurement of how many lines/blocks/arcs of CUBRID code are executed while the automated tests are running. We use valgrind tool to instrument the binaries and run a full set of automated tests. Near all of existing test cases (SQL, MEDIUM, SQL_BY_CCI, HA_REPL, CCI, ISOLATION, SHELL, HA_SHELL, SHELL_HEAVY, SHELL_LONG, YCSB, SYSBENCH, TPC-W, TPC-C, DOTS) are used for code coverage test and scheduled by manual.

# 2. Regression Test Deployment

## 2.1 Deployment overview

<table>
<tr>
<th>Description</th>
<th>User Name</th>
<th>IP</th>
<th>Hostname</th>
<th>Tools to deploy</th>
</tr>
<tr class="even">
<td>Controller</td>
<td>codecov</td>
<td>192.168.1.98</td>
<td>func23</td>
<td> CTP<br>
cc4c <br>
CUBRID source <br>  
 </td>
</tr>
</table>


## 2.2 Installation

* ### Install CTP
  Please follow this [guide to install CTP as regression test configuration](https://github.com/CUBRID/cubrid-testtools/blob/develop/doc/ctp_install_guide.md#3-install-ctp-as-regression-test-platform).

  Then, create configuration file used by code coverage controller.

  File `~/CTP/conf/coverage.conf`:

      covarage_build_server_cn_host=192.168.1.91
      coverage_build_server_cn_usr=ci_build
      coverage_build_server_cn_pwd=******
      coverage_build_server_cn_port=22

      coverage_controller_cc4c_home=/home/codecov/cc4c
      coverage_controller_host=192.168.1.98
      coverage_controller_user=codecov
      coverage_controller_pwd=******
      coverage_controller_port=22
      coverage_controller_home=/home/codecov

* ### Install cc4c

  Download cc4c: 

      cd ~
      git clone https://github.com/CUBRID/cubrid-testtools-internal.git
      cd cubrid-testtools-internal
      git checkout develop
      cp -rf cov_compl_tool/cc4c  ~/cc4c

  Then change to actual values in below script file:

  File `cc4c/coverage_monitor.sh`:

      ...
      cc4c_home=/home/codecov/cc4c
      ...
      qahomeUser="qahome"
      qahomePwd="******"
      qahomeTargetPath="/home/qahome/qaresult_en/web/qaresultfile/cubrid/code\ coverage"
      qahomeIP="192.168.1.86"
      port=22
      ...

* ### Append to .bash_profile

  File `.bash_profile`:

      export CTP_SKIP_UPDATE=0
      export CTP_BRANCH_NAME=develop
      export CTP_HOME=~/CTP
      source /opt/rh/devtoolset-8/enable
      export PATH=$CTP_HOME/bin:$CTP_HOME/common/script:$PATH:$HOME/bin

* ### Configure crontab

  Execute `crontab -e` and set content as below:

      0 * * * * sh /home/codecov/cc4c/coverage_monitor.sh >/home/codecov/cc4c/monitor.log 2>&1
      
* ### Create quick start script

  File `~/start_test.sh`:
 
      nohup start_consumer.sh -q QUEUE_CUBRID_QA_CODE_COVERAGE -exec run_coverage -s china &

# 3. Regression Test Sustaining

## 3.1 How to start test?

* ### Start test daemon process:

  Log into test server, keep daemon process for `start_test.sh`:

      nohup sh start_test.sh &

  After startup, it will keep listening for new test messages. Once come, it will fire the test immediately. Except CI test messages which were generated automatically monthly, you may send test message by manual.
  
* ### Send manual test message same as regular configuration:

  Log into message server (message@192.168.1.91) first.
  
      sender.sh QUEUE_CUBRID_QA_CODE_COVERAGE http://192.168.1.91:8080/REPO_ROOT/store_01/10.2.0.8270-c897055/drop/cubrid-10.2.0.8270-c897055.tar.gz gcov_package default
  
