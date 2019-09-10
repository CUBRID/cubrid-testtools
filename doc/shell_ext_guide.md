# 1. Test Objective

There are some more flexible test cases that they require more than one test server (e.g., HA test with 1:1:2 nodes), or depend on repositories like `cubrid-testcases` or `cubrid-testcases-private` or `cubrid-testcases-private-ex`, or hold test server(s) exclusively, or connect test server with `'root'` to change configurations in system level, etc. In order to make such case automation, we make SHELL_EXT suite to implement advanced test.

# 2. SHELL_EXT Test via CTP

Like SHELL test, SHELL_EXT test is executed by CTP test tool. But you have to note that SHELL_EXT use the CTP with branch version `'develop_automation'`. We hope to finally merge `'develop_automation'` into `'develop'` after codes are mature.

## 2.1 Quick Start

  Suppose that we plan to execute some HA test cases in `'https://github.com/CUBRID/cubrid-testcases-private/tree/develop/shell_ext/HA/issue_10843'` which is a subset of SHELL_EXT test cases. Let's show how to deploy and run the test.
  
  Under folder `'cubrid-testcases-private/shell_ext/HA/issue_10843'`, there are total 12 test cases related to HA test.
  
  No | Test case  |  HA test nodes <br> (master:slave:replica)
  -|-|- 
  1 | issue_10843_err | 1 : 2 : 0 
  2 | issue_10843_fmr | 1 : 1 : 1
  3 | issue_10843_fms | 1 : 1 : 1  
  4 | issue_10843_frs | 1 : 1 : 1  
  5 | issue_10843_mr_1 | 1 : 1 : 1  
  6 | issue_10843_mr_2 | 1 : 1 : 1  
  7 | issue_10843_ms_1 | 1 : 2 : 0    
  8 | issue_10843_ms_3 | 1 : 1 : 1    
  9 | issue_10843_rr | 1 : 1 : 2    
  10 | issue_10843_rs | 1 : 1 : 1    
  11 | issue_10843_sr | 1 : 1 : 1    
  12 | issue_10843_ss | 1 : 2: 0     

* ### Prepare for at least 4 test servers
 
  According to requirements of these test cases, we need prepare for at least 4 test servers. And as you known, every HA instance should run on system user with same name. Let's create three system users in each server.
  
    useradd shell_ext1
    useradd shell_ext2
    useradd shell_ext3
    
  And make sure that all users adopt same password. Please use command `'passwd'` to set it.
  
  Then, we need create a controller user. Below table shows the result:
  
  IP | Users 
  -|-
  192.168.1.123 | controller <br> shell_ext1 <br> shell_ext2 <br> shell_ext3
  192.168.1.124 | shell_ext1 <br> shell_ext2 <br> shell_ext3
  192.168.1.125 | shell_ext1 <br> shell_ext2 <br> shell_ext3
  192.168.1.126 | shell_ext1 <br> shell_ext2 <br> shell_ext3

* ### Install CTP.

  We need install CTP among all users on all servers.

  Please follow the guide to [install CTP](doc/ctp_install_guide.md).
  
  But you need pay attention to one point that you should check out CTP from branch `develop_automation` instead of provided `'develop'` version.
  
      cd ~
      git clone https://github.com/CUBRID/cubrid-testtools.git
      cd ~/cubrid-testtools 
      git checkout develop_automation
      cp -rf CTP ~/

* ### Check out test cases



# 3. Regression Test Deployment
# 4. Regression Test Sustaining
# 5. SHELL_EXT Test Case Specification
