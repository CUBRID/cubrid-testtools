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

# 3. Regression Test Sustaining



