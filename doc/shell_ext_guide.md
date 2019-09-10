# 1. Test Objective

There are some more flexible test cases that they require more than one test server (e.g., HA test with 1:1:2 nodes), or depend on repositories like `cubrid-testcases` or `cubrid-testcases-private` or `cubrid-testcases-private-ex`, or hold test server(s) exclusively, or connect test server with `'root'` to change configurations in system level, and so on. In order to make such case automate, we make SHELL_EXT suite to implement advanced test.

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

  Check out test cases on all users except `controller`.

      cd ~
      git clone https://github.com/CUBRID/cubrid-testcases-private.git
      cd ~/cubrid-testcases-private
      git checkout develop
      
* ### Configure controller

  Create test configuration file in only controller.
  
  File `~/CTP/conf/shell_ext.conf`
  
      #HA(1:1:2)
      selector.ha_m1s1r2_nodes.hosts=m123_shell_ext1,m124_shell_ext1,m125_shell_ext1,m126_shell_ext1;m123_shell_ext2,m124_shell_ext2,m125_shell_ext2,m126_shell_ext2;m123_shell_ext3,m124_shell_ext3,m125_shell_ext3,m126_shell_ext3;
      selector.ha_m1s1r2_nodes.type=HA

      #HA(1:1:1)
      selector.ha_m1s1r1_nodes.hosts=m124_shell_ext1,m125_shell_ext1,m126_shell_ext1;m124_shell_ext2,m125_shell_ext2,m126_shell_ext2;m124_shell_ext3,m125_shell_ext3,m126_shell_ext3
      selector.ha_m1s1r1_nodes.type=HA

      #HA(1:2:0)
      selector.ha_m1s2_nodes.hosts=m124_shell_ext1,m125_shell_ext1,m126_shell_ext1;m124_shell_ext2,m125_shell_ext2,m126_shell_ext2;m124_shell_ext3,m125_shell_ext3,m126_shell_ext3
      selector.ha_m1s2_nodes.type=HA

      default.ssh.port=22
      default.ssh.pwd=********

      env.m123_shell_ext1.ssh.host=192.168.1.123
      env.m123_shell_ext1.ssh.user=shell_ext1
      env.m123_shell_ext1.cubrid.cubrid_port_id=12024
      env.m123_shell_ext1.broker1.BROKER_PORT=22024
      env.m123_shell_ext1.broker2.BROKER_PORT=32024
      env.m123_shell_ext1.ha.ha_port_id=42024

      env.m123_shell_ext2.ssh.host=192.168.1.123
      env.m123_shell_ext2.ssh.user=shell_ext2
      env.m123_shell_ext2.cubrid.cubrid_port_id=12025
      env.m123_shell_ext2.broker1.BROKER_PORT=22025
      env.m123_shell_ext2.broker2.BROKER_PORT=32025
      env.m123_shell_ext2.ha.ha_port_id=42025

      env.m123_shell_ext3.ssh.host=192.168.1.123
      env.m123_shell_ext3.ssh.user=shell_ext3
      env.m123_shell_ext3.cubrid.cubrid_port_id=12026
      env.m123_shell_ext3.broker1.BROKER_PORT=22026
      env.m123_shell_ext3.broker2.BROKER_PORT=32026
      env.m123_shell_ext3.ha.ha_port_id=42026

      env.m124_shell_ext1.ssh.host=192.168.1.124
      env.m124_shell_ext1.ssh.user=shell_ext1
      env.m124_shell_ext1.cubrid.cubrid_port_id=12028
      env.m124_shell_ext1.broker1.BROKER_PORT=22028
      env.m124_shell_ext1.broker2.BROKER_PORT=32028
      env.m124_shell_ext1.ha.ha_port_id=42028

      env.m124_shell_ext2.ssh.host=192.168.1.124
      env.m124_shell_ext2.ssh.user=shell_ext2
      env.m124_shell_ext2.cubrid.cubrid_port_id=12029
      env.m124_shell_ext2.broker1.BROKER_PORT=22029
      env.m124_shell_ext2.broker2.BROKER_PORT=32029
      env.m124_shell_ext2.ha.ha_port_id=42029

      env.m124_shell_ext3.ssh.host=192.168.1.124
      env.m124_shell_ext3.ssh.user=shell_ext3
      env.m124_shell_ext3.cubrid.cubrid_port_id=12030
      env.m124_shell_ext3.broker1.BROKER_PORT=22030
      env.m124_shell_ext3.broker2.BROKER_PORT=32030
      env.m124_shell_ext3.ha.ha_port_id=42030

      env.m125_shell_ext1.ssh.host=192.168.1.125
      env.m125_shell_ext1.ssh.user=shell_ext1
      env.m125_shell_ext1.cubrid.cubrid_port_id=12032
      env.m125_shell_ext1.broker1.BROKER_PORT=22032
      env.m125_shell_ext1.broker2.BROKER_PORT=32032
      env.m125_shell_ext1.ha.ha_port_id=42032

      env.m125_shell_ext2.ssh.host=192.168.1.125
      env.m125_shell_ext2.ssh.user=shell_ext2
      env.m125_shell_ext2.cubrid.cubrid_port_id=12033
      env.m125_shell_ext2.broker1.BROKER_PORT=22033
      env.m125_shell_ext2.broker2.BROKER_PORT=32033
      env.m125_shell_ext2.ha.ha_port_id=42033

      env.m125_shell_ext3.ssh.host=192.168.1.125
      env.m125_shell_ext3.ssh.user=shell_ext3
      env.m125_shell_ext3.cubrid.cubrid_port_id=12034
      env.m125_shell_ext3.broker1.BROKER_PORT=22034
      env.m125_shell_ext3.broker2.BROKER_PORT=32034
      env.m125_shell_ext3.ha.ha_port_id=42034

      env.m126_shell_ext1.ssh.host=192.168.1.126
      env.m126_shell_ext1.ssh.user=shell_ext1
      env.m126_shell_ext1.cubrid.cubrid_port_id=12036
      env.m126_shell_ext1.broker1.BROKER_PORT=22036
      env.m126_shell_ext1.broker2.BROKER_PORT=32036
      env.m126_shell_ext1.ha.ha_port_id=42036

      env.m126_shell_ext2.ssh.host=192.168.1.126
      env.m126_shell_ext2.ssh.user=shell_ext2
      env.m126_shell_ext2.cubrid.cubrid_port_id=12037
      env.m126_shell_ext2.broker1.BROKER_PORT=22037
      env.m126_shell_ext2.broker2.BROKER_PORT=32037
      env.m126_shell_ext2.ha.ha_port_id=42037

      env.m126_shell_ext3.ssh.host=192.168.1.126
      env.m126_shell_ext3.ssh.user=shell_ext3
      env.m126_shell_ext3.cubrid.cubrid_port_id=12038
      env.m126_shell_ext3.broker1.BROKER_PORT=22038
      env.m126_shell_ext3.broker2.BROKER_PORT=32038
      env.m126_shell_ext3.ha.ha_port_id=42038

      cubrid_download_url=http://192.168.1.91:8080/REPO_ROOT/store_01/10.2.0.8295-aeaf5c8/drop/CUBRID-10.2.0.8295-aeaf5c8-Linux.x86_64.sh
      scenario=cubrid-testcases-private/shell_ext/HA/issue_10843
      test_category=shell_ext

      git_user=<git user>
      git_email=<git e-mail>
      git_pwd=<git password>

      owner_email=Fan<fan.zaiqiang@navercorp.com>
      feedback_type=file
  
  Please note that there are three selectors: `ha_m1s1r2_nodes`, `ha_m1s1r1_nodes` and `ha_m1s2_nodes`. They should match selector declaration in `test.conf` defined in test case.
  
* ### Execute test

  Log into controller, and execute 
    
      ctp.sh shell -c ~/CTP/conf/shell_ext.conf
      
* ### Examine test results

  It's same as the way of general SHELL test. Please refer to [shell guide](doc/shell_guide.md).

## 2.2 Selector usage

  Selector definines a set of specific servers to execute a particular test case. It's defined in test configuration file in CTP in controller only. Test case is required to declare which selector used.
  
  The format to define selector shows as below:
  
      selector.<selector name>.hosts=<rules for choosing servers>
  
  For example, 
  
      #define selector 'any_two_nodes' to choose any two nodes.
      selector.any_two_nodes.hosts=*,*

      #define selector 'two_nodes_with_diff_ip' to choose any two nodes with different ip address.
      selector.two_nodes_with_diff_ip.hosts=%,%

      #define selector 'performance_node' to choose any one from two.
      selector.performance_node.hosts=m123_shell_ext1|m124_shell_ext1
      
      #define selector 'performance_ha' to choose any one group from 3 groups.
      selector.performance_ha.hosts=m123_shell_ext1,m124_shell_ext1;m123_shell_ext2,m124_shell_ext2;m123_shell_ext3,m124_shell_ext3
      selector.performance_ha.type=HA
      
      #define selector 'ycsb_extend_node' to choose the one provided only.
      selector.ycsb_extend_node.hosts=m123_shell_ext1
      
      #define selector 'disk_1T_node' to choose the 1T node.
      selector.disk_1T_node.hosts=m124_shell_ext1|m124_shell_ext2|m124_shell_ext3
      
  **Declare `selector` in test case**
  
  Each particular test case should have a test case configuration file `test.conf`. In this file, selector will be declared.
  
      machines: <selector name>
      
  For example,
  
      machines: performance_ha

## 2.3 Test case configuration: test.conf

* ### Parameter: machines
  
      machines:  <selector_name>
      
* ### Parameter: exclusive
      
      exclusive: true | false
      
  Define whether hold test servers exclusively.
      
* ### Parameter: cubrid_deps
  
      cubrid_deps: 10.1.1.7666-4324548,9.3.0.0206
      
  With this defination, before runing of case, two builds will be automatically installed in `~/CUBRID_10.1.1.7666-4324548` and `~/CUBRID_9.3.0.0206` which are ready to use in test case.
  
* ### Parameter: cubrid_pkg_deps
      
      cubrid_pkg_deps: CUBRID-{BUILD_ID}-Linux.x86_64.rpm
  
  With this defination, before runing of case, the specific package will be automatically downloaded and stored under `$HOME` directory which test case intends to use it. 

* ### Parameter: repo_deps
  
      repo_deps: cubrid-testcases
      
  With this defination, before runing of case, the specific repository will be automatically downloaded and stored under `$HOME` directory which test case depends on it.
  
# 3. Regression Test Deployment
# 4. Regression Test Sustaining
# 5. SHELL_EXT Test Case Specification
