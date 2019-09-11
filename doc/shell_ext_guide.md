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
  192.168.1.123 | controller, shell_ext1, shell_ext2, shell_ext3
  192.168.1.124 | shell_ext1, shell_ext2, shell_ext3
  192.168.1.125 | shell_ext1, shell_ext2, shell_ext3
  192.168.1.126 | shell_ext1, shell_ext2, shell_ext3

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
  
## 2.4 How to run single test case

  It's better add environmental variable `init_path` into `~/.bash_profile`.

    export init_path=$HOME/CTP/shell/init_path  
  
    cd /path/to/onetest/cases/onetest.sh
    
    #Below exports depend on actual selector.
    export D_DEFAULT_PORT=22
    export D_DEFAULT_PWD=<pwd>
    export D_HOST1_IP=192.168.1.123
    export D_HOST1_USER=shell_ext1
    export D_HOST2_IP=192.168.1.124
    export D_HOST2_USER=shell_ext1
    ...
    ...
    
    # initialize test case
    $init_path/prepare.sh
    
    # run test case
    sh onetest.sh
    
  After running, check test result following the regular way of SHELL test.
  
# 3. Regression Test Deployment

## 3.1 Deployment overview

Role	| User Name |	IP	| Hostname	| Tools to deploy
-|-|-|-|-
controller node|shell_ext_ctrl | 192.168.1.117 | func42 | CTP
worker |shell_ext1, shell_ext2, shell_ext3 | 192.168.1.117 | func42 | CTP, cubrid-testcases-private
worker |shell_ext1, shell_ext2, shell_ext3 | 192.168.1.118 | func43 | CTP, cubrid-testcases-private
worker |shell_ext1, shell_ext2, shell_ext3 | 192.168.1.119 | func44 | CTP, cubrid-testcases-private
worker |shell_ext1, shell_ext2, shell_ext3 | 192.168.1.120 | func45 | CTP, cubrid-testcases-private
worker |shell_ext1, shell_ext2, shell_ext3 | 192.168.1.121 | func46 | CTP, cubrid-testcases-private
worker |shell_ext1, shell_ext2, shell_ext3 | 192.168.1.122 | func47 | CTP, cubrid-testcases-private
worker |shell_ext1, shell_ext2, shell_ext3 | 192.168.1.123 | func48 | CTP, cubrid-testcases-private
worker |shell_ext1, shell_ext2, shell_ext3 | 192.168.1.124 | func49 | CTP, cubrid-testcases-private
worker |shell_ext1, shell_ext2, shell_ext3 | 192.168.1.125 | func50 | CTP, cubrid-testcases-private
worker |shell_ext1, shell_ext2, shell_ext3 | 192.168.1.126 | func51 | CTP, cubrid-testcases-private
worker |shell_ext1, shell_ext2, shell_ext3 | 192.168.1.127 | func52 | CTP, cubrid-testcases-private
worker |shell_ext1, shell_ext2, shell_ext3 | 192.168.1.128 | func53 | CTP, cubrid-testcases-private

## 3.2 Installation

* ### Install CTP as regression test

  Please follow [the guide to install CTP](doc/ctp_install_guide.md#3-install-ctp-as-regression-test-platform) into directory `$HOME/CTP`. You should fully install it in all controller node and each worker node.
  
  Please note that you should use the CTP in `'develop_automation'` branch instead of `'develop'` branch.
  
  Change to use below in corresponding steps.
  
      git checkout develop_automation
      
  And change to use below when change `.bash_profile`:
  
      export CTP_BRANCH_NAME=develop_automation

* ###  Check out test cases on all worker nodes

  Execute on all worker nodes: 
  
      cd $HOME
      git clone https://github.com/CUBRID/cubrid-testcases-private.git

* ### Configure test on controller node

  File `~/CTP/conf/shell_template.conf`
  
		#any two nodes
		selector.any_two_nodes.hosts=*,*

		#any two nodes (different ip)
		selector.two_nodes_with_diff_ip.hosts=%,%

		#any three nodes (different ip)
		selector.three_nodes_with_diff_ip.hosts=%,%,%

		#any four nodes (different ip)
		selector.four_nodes_with_diff_ip.hosts=%,%,%,%

		#D1 is root for current machine
		selector.normal_node_with_root.hosts=m128_shell_ext1,m128_root

		#Two nodes. The second has root id and rpmlint command.
		selector.reboot_nodes.hosts=m127_shell_ext1,m128_root;m127_shell_ext2,m128_root;m127_shell_ext3,m128_root;m128_shell_ext1,m127_root;m128_shell_ext2,m127_root;m128_shell_ext3,m127_root

		#24 cpu cores, bc, ulimit -n 4096
		selector.performance_node.hosts=m117_shell_ext1|m117_shell_ext2|m117_shell_ext3|m118_shell_ext1|m118_shell_ext2|m118_shell_ext3|m119_shell_ext1|m119_shell_ext2|m119_shell_ext3|m120_shell_ext1|m120_shell_ext2|m120_shell_ext3|m121_shell_ext1|m121_shell_ext2|m121_shell_ext3|m122_shell_ext1|m122_shell_ext2|m122_shell_ext3

		#for bug_bts_11962
		selector.performance_node_for_11962.hosts=m117_shell_ext2|m118_shell_ext2|m119_shell_ext2|m120_shell_ext2|m121_shell_ext2|m122_shell_ext2

		#extended ycsb
		selector.ycsb_extend_node.hosts=m120_shell_ext1|m121_shell_ext1|m122_shell_ext1

		#python>2.4
		selector.python_node.hosts=m123_shell_ext1|m123_shell_ext2|m123_shell_ext3|m124_shell_ext1|m124_shell_ext2|m124_shell_ext3

		#telnet
		selector.telnet_node.hosts=m125_shell_ext1|m125_shell_ext2|m125_shell_ext3

		#php
		selector.php_node.hosts=m126_shell_ext1|m126_shell_ext2|m126_shell_ext3

		#sudo(date -s)
		selector.sudo_node.hosts=m125_shell_ext1|m125_shell_ext2|m125_shell_ext3|m126_shell_ext1|m126_shell_ext2|m126_shell_ext3

		#cmake and build cubrid
		selector.cubrid_build_node.hosts=m122_shell_ext_make

		#cubrid-testcases
		selector.pub_testcases_node.hosts=m123_shell_ext2|m124_shell_ext2|m125_shell_ext2|m126_shell_ext2|m127_shell_ext2|m128_shell_ext2

		#valgrind
		selector.valgrind_node.hosts=m120_shell_ext3|m121_shell_ext3

		#valgrind(1), sysbench(2)
		selector.valgrind_sysbench_nodes.hosts=m118_shell_ext1,m119_shell_ext1

		#valgrind(both), ha(same user)
		selector.valgrind_ha_nodes.hosts=m118_shell_ext2,m119_shell_ext2;m118_shell_ext3,m119_shell_ext3
		selector.valgrind_ha_nodes.type=HA

		#ha(same user)
		selector.ha_m1s1_nodes.hosts=m121_shell_ext1,m122_shell_ext1;m121_shell_ext2,m122_shell_ext2;m121_shell_ext3,m122_shell_ext3;m123_shell_ext1,m124_shell_ext1;m123_shell_ext2,m124_shell_ext2;m123_shell_ext3,m124_shell_ext3;m126_shell_ext3,m127_shell_ext3
		selector.ha_m1s1_nodes.type=HA

		selector.ha_m1s1_200g_nodes.hosts=m119_shell_ext1,m120_shell_ext1;m119_shell_ext2,m120_shell_ext2;m119_shell_ext3,m120_shell_ext3;m121_shell_ext1,m122_shell_ext1;m121_shell_ext2,m122_shell_ext2;m121_shell_ext3,m122_shell_ext3;m123_shell_ext1,m124_shell_ext1;m123_shell_ext2,m124_shell_ext2;m123_shell_ext3,m124_shell_ext3;m126_shell_ext3,m127_shell_ext3
		selector.ha_m1s1_200g_nodes.type=HA

		#ha(one master and one slave. and the third is root  for slave server)
		selector.ha_m1s1root_nodes.hosts=m127_shell_ext1,m128_shell_ext1,m127_root;m127_shell_ext2,m128_shell_ext2,m127_root;m127_shell_ext3,m128_shell_ext3,m127_root
		selector.ha_m1s1root_nodes.type=HA

		#ha (with RQG): TBD (Colin)
		selector.ha_m1s1_RQG_nodes.hosts=m120_shell_ext2,m117_shell_ext2;m121_shell_ext2,m122_shell_ext2
		selector.ha_m1s1_RQG_nodes.type=HA

		#two nodes (fst has RQG)
		selector.two_nodes_with_fst_rqg.hosts=m120_shell_ext2,*;m121_shell_ext3,*;m122_shell_ext3,*;m118_shell_ext1,*;m119_shell_ext3,*

		#ha(4 nodes, same user), expect
		selector.ha_m1s1r2_nodes.hosts=m123_shell_ext1,m124_shell_ext1,m125_shell_ext1,m126_shell_ext1;m123_shell_ext2,m124_shell_ext2,m125_shell_ext2,m126_shell_ext2;m123_shell_ext3,m124_shell_ext3,m125_shell_ext3,m126_shell_ext3;m119_shell_ext1,m120_shell_ext1,m121_shell_ext1,m122_shell_ext1;m119_shell_ext2,m120_shell_ext2,m121_shell_ext2,m122_shell_ext2;m119_shell_ext3,m120_shell_ext3,m121_shell_ext3,m122_shell_ext3
		selector.ha_m1s1r2_nodes.type=HA

		#ha(3 nodes, same user)
		selector.ha_m1s1r1_nodes.hosts=m126_shell_ext1,m127_shell_ext1,m128_shell_ext1;m126_shell_ext2,m127_shell_ext2,m128_shell_ext2;m126_shell_ext3,m127_shell_ext3,m128_shell_ext3;m122_shell_ext1,m123_shell_ext1,m124_shell_ext1;m122_shell_ext2,m123_shell_ext2,m124_shell_ext2;m122_shell_ext3,m123_shell_ext3,m124_shell_ext3
		selector.ha_m1s1r1_nodes.type=HA

		#ha(3 nodes, m:s is 1:1. the last with root user for ping role)
		selector.ha_m1s1_pingwithroot_nodes.hosts=m125_shell_ext1,m126_shell_ext1,m128_root;m125_shell_ext2,m126_shell_ext2,m128_root;m125_shell_ext3,m126_shell_ext3,m128_root

		#ha(6 nodes)
		selector.ha_m1s1r4_nodes.hosts=m128_shell_ext1,m127_shell_ext1,m126_shell_ext1,m125_shell_ext1,m124_shell_ext1,m123_shell_ext1;m128_shell_ext2,m127_shell_ext2,m126_shell_ext2,m125_shell_ext2,m124_shell_ext2,m123_shell_ext2;m128_shell_ext3,m127_shell_ext3,m126_shell_ext3,m125_shell_ext3,m124_shell_ext3,m123_shell_ext3

		#ha(3 nodes: one master, two slaves)
		selector.ha_m1s2_nodes.hosts=m117_shell_ext1,m118_shell_ext1,m119_shell_ext1;m117_shell_ext2,m118_shell_ext2,m119_shell_ext2;m117_shell_ext3,m118_shell_ext3,m119_shell_ext3;m120_shell_ext1,m121_shell_ext1,m122_shell_ext1;m120_shell_ext2,m121_shell_ext2,m122_shell_ext2;m120_shell_ext3,m121_shell_ext3,m122_shell_ext3
		selector.ha_m1s2_nodes.type=HA

		#ha(4 nodes: one master, two slaves, the last host should provide root on master machine)
		selector.ha_m1s2_root4master_nodes.hosts=m128_shell_ext1,m127_shell_ext1,m126_shell_ext1,m128_root;m128_shell_ext2,m127_shell_ext2,m126_shell_ext2,m128_root;m128_shell_ext3,m127_shell_ext3,m126_shell_ext3,m128_root

		#ha(3 nodes ha plus extra one(d_host3) which give root to d_host2 machine)
		selector.ha_3nodes_and_lastroot.hosts=m124_shell_ext1,m125_shell_ext1,m128_shell_ext1,m128_root;m124_shell_ext2,m125_shell_ext2,m128_shell_ext2,m128_root;m124_shell_ext3,m125_shell_ext3,m128_shell_ext3,m128_root;m119_shell_ext1,m120_shell_ext1,m128_shell_ext1,m128_root;m119_shell_ext1,m120_shell_ext1,m128_shell_ext1,m128_root

		#ha(3 nodes ha plus lasttworoot. Ex: main, D1, D2, D3, D4. D3 is root for D1, D4 is root for D2)
		selector.ha_3nodes_and_lasttworoot.hosts=m117_shell_ext1,m126_shell_ext1,m128_shell_ext1,m126_root,m128_root;m117_shell_ext2,m126_shell_ext2,m128_shell_ext2,m126_root,m128_root;m117_shell_ext3,m126_shell_ext3,m128_shell_ext3,m126_root,m128_root

		#oltpbench
		selector.oltpbench_node.hosts=m120_shell_ext2,m121_shell_ext2

		#disk space:700G
		selector.disk_700g_node.hosts=m120_shell_ext1|m120_shell_ext2|m120_shell_ext3|m121_shell_ext1|m121_shell_ext2|m121_shell_ext3

		#disk space:350G
		selector.disk_350g_node.hosts=m120_shell_ext1|m120_shell_ext2|m120_shell_ext3|m121_shell_ext1|m121_shell_ext2|m121_shell_ext3

		#two nodes, expect(1)
		selector.expect_2_nodes.hosts=m125_shell_ext1,*;m125_shell_ext2,*;m125_shell_ext3,*

		#/dev/shm
		selector.devshm_node.hosts=m119_shell_ext1|m119_shell_ext2|m119_shell_ext3

		#ha(same user, /dev/shm)
		selector.ha_m1s1_devshm_nodes.hosts=m119_shell_ext1,m121_shell_ext1;m119_shell_ext2,m121_shell_ext2;m119_shell_ext3,m121_shell_ext3
		selector.ha_m1s1_devshm_nodes.type=HA

		#ha, tpcw
		selector.tpcw_ha_nodes.hosts=m122_shell_ext1,m117_shell_ext1,m118_shell_ext1
		selector.tpcw_ha_nodes.type=HA

		#valgrind,RQG
		selector.valgrind_RQG_node.hosts=m117_shell_ext3|m118_shell_ext3|m119_shell_ext3|m120_shell_ext3|m121_shell_ext3|m122_shell_ext3

		#systemtap
		selector.systemtap_node.hosts=m124_shell_ext1|m124_shell_ext2|m124_shell_ext3

		default.ssh.port=22
		default.ssh.pwd=**********

		env.m117_shell_ext1.ssh.host=192.168.1.117
		env.m117_shell_ext1.ssh.user=shell_ext1
		env.m117_shell_ext1.cubrid.cubrid_port_id=12000
		env.m117_shell_ext1.broker1.BROKER_PORT=22000
		env.m117_shell_ext1.broker2.BROKER_PORT=32000
		env.m117_shell_ext1.ha.ha_port_id=42000

		env.m117_shell_ext2.ssh.host=192.168.1.117
		env.m117_shell_ext2.ssh.user=shell_ext2
		env.m117_shell_ext2.cubrid.cubrid_port_id=12001
		env.m117_shell_ext2.broker1.BROKER_PORT=22001
		env.m117_shell_ext2.broker2.BROKER_PORT=32001
		env.m117_shell_ext2.ha.ha_port_id=42001

		env.m117_shell_ext3.ssh.host=192.168.1.117
		env.m117_shell_ext3.ssh.user=shell_ext3
		env.m117_shell_ext3.cubrid.cubrid_port_id=12002
		env.m117_shell_ext3.broker1.BROKER_PORT=22002
		env.m117_shell_ext3.broker2.BROKER_PORT=32002
		env.m117_shell_ext3.ha.ha_port_id=42002

		env.m117_root.type=follow
		env.m117_root.ssh.host=192.168.1.117
		env.m117_root.ssh.user=root
		env.m117_root.ssh.pwd=********
		env.m117_root.cubrid.cubrid_port_id=12003
		env.m117_root.broker1.BROKER_PORT=22003
		env.m117_root.broker2.BROKER_PORT=32003
		env.m117_root.ha.ha_port_id=42003

		env.m118_shell_ext1.ssh.host=192.168.1.118
		env.m118_shell_ext1.ssh.user=shell_ext1
		env.m118_shell_ext1.cubrid.cubrid_port_id=12004
		env.m118_shell_ext1.broker1.BROKER_PORT=22004
		env.m118_shell_ext1.broker2.BROKER_PORT=32004
		env.m118_shell_ext1.ha.ha_port_id=42004

		env.m118_shell_ext2.ssh.host=192.168.1.118
		env.m118_shell_ext2.ssh.user=shell_ext2
		env.m118_shell_ext2.cubrid.cubrid_port_id=12005
		env.m118_shell_ext2.broker1.BROKER_PORT=22005
		env.m118_shell_ext2.broker2.BROKER_PORT=32005
		env.m118_shell_ext2.ha.ha_port_id=42005

		env.m118_shell_ext3.ssh.host=192.168.1.118
		env.m118_shell_ext3.ssh.user=shell_ext3
		env.m118_shell_ext3.cubrid.cubrid_port_id=12006
		env.m118_shell_ext3.broker1.BROKER_PORT=22006
		env.m118_shell_ext3.broker2.BROKER_PORT=32006
		env.m118_shell_ext3.ha.ha_port_id=42006

		env.m118_root.type=follow
		env.m118_root.ssh.host=192.168.1.118
		env.m118_root.ssh.user=root
		env.m118_root.ssh.pwd=********
		env.m118_root.cubrid.cubrid_port_id=12007
		env.m118_root.broker1.BROKER_PORT=22007
		env.m118_root.broker2.BROKER_PORT=32007
		env.m118_root.ha.ha_port_id=42007

		env.m119_shell_ext1.ssh.host=192.168.1.119
		env.m119_shell_ext1.ssh.user=shell_ext1
		env.m119_shell_ext1.cubrid.cubrid_port_id=12008
		env.m119_shell_ext1.broker1.BROKER_PORT=22008
		env.m119_shell_ext1.broker2.BROKER_PORT=32008
		env.m119_shell_ext1.ha.ha_port_id=42008

		env.m119_shell_ext2.ssh.host=192.168.1.119
		env.m119_shell_ext2.ssh.user=shell_ext2
		env.m119_shell_ext2.cubrid.cubrid_port_id=12009
		env.m119_shell_ext2.broker1.BROKER_PORT=22009
		env.m119_shell_ext2.broker2.BROKER_PORT=32009
		env.m119_shell_ext2.ha.ha_port_id=42009

		env.m119_shell_ext3.ssh.host=192.168.1.119
		env.m119_shell_ext3.ssh.user=shell_ext3
		env.m119_shell_ext3.cubrid.cubrid_port_id=12010
		env.m119_shell_ext3.broker1.BROKER_PORT=22010
		env.m119_shell_ext3.broker2.BROKER_PORT=32010
		env.m119_shell_ext3.ha.ha_port_id=42010

		env.m119_root.type=follow
		env.m119_root.ssh.host=192.168.1.119
		env.m119_root.ssh.user=root
		env.m119_root.ssh.pwd=********
		env.m119_root.cubrid.cubrid_port_id=12011
		env.m119_root.broker1.BROKER_PORT=22011
		env.m119_root.broker2.BROKER_PORT=32011
		env.m119_root.ha.ha_port_id=42011

		env.m120_shell_ext1.ssh.host=192.168.1.120
		env.m120_shell_ext1.ssh.user=shell_ext1
		env.m120_shell_ext1.cubrid.cubrid_port_id=12012
		env.m120_shell_ext1.broker1.BROKER_PORT=22012
		env.m120_shell_ext1.broker2.BROKER_PORT=32012
		env.m120_shell_ext1.ha.ha_port_id=42012

		env.m120_shell_ext2.ssh.host=192.168.1.120
		env.m120_shell_ext2.ssh.user=shell_ext2
		env.m120_shell_ext2.cubrid.cubrid_port_id=12013
		env.m120_shell_ext2.broker1.BROKER_PORT=22013
		env.m120_shell_ext2.broker2.BROKER_PORT=32013
		env.m120_shell_ext2.ha.ha_port_id=42013

		env.m120_shell_ext3.ssh.host=192.168.1.120
		env.m120_shell_ext3.ssh.user=shell_ext3
		env.m120_shell_ext3.cubrid.cubrid_port_id=12014
		env.m120_shell_ext3.broker1.BROKER_PORT=22014
		env.m120_shell_ext3.broker2.BROKER_PORT=32014
		env.m120_shell_ext3.ha.ha_port_id=42014

		env.m120_root.type=follow
		env.m120_root.ssh.host=192.168.1.120
		env.m120_root.ssh.user=root
		env.m120_root.ssh.pwd=********
		env.m120_root.cubrid.cubrid_port_id=12015
		env.m120_root.broker1.BROKER_PORT=22015
		env.m120_root.broker2.BROKER_PORT=32015
		env.m120_root.ha.ha_port_id=42015

		env.m121_shell_ext1.ssh.host=192.168.1.121
		env.m121_shell_ext1.ssh.user=shell_ext1
		env.m121_shell_ext1.cubrid.cubrid_port_id=12016
		env.m121_shell_ext1.broker1.BROKER_PORT=22016
		env.m121_shell_ext1.broker2.BROKER_PORT=32016
		env.m121_shell_ext1.ha.ha_port_id=42016

		env.m121_shell_ext2.ssh.host=192.168.1.121
		env.m121_shell_ext2.ssh.user=shell_ext2
		env.m121_shell_ext2.cubrid.cubrid_port_id=12017
		env.m121_shell_ext2.broker1.BROKER_PORT=22017
		env.m121_shell_ext2.broker2.BROKER_PORT=32017
		env.m121_shell_ext2.ha.ha_port_id=42017

		env.m121_shell_ext3.ssh.host=192.168.1.121
		env.m121_shell_ext3.ssh.user=shell_ext3
		env.m121_shell_ext3.cubrid.cubrid_port_id=12018
		env.m121_shell_ext3.broker1.BROKER_PORT=22018
		env.m121_shell_ext3.broker2.BROKER_PORT=32018
		env.m121_shell_ext3.ha.ha_port_id=42018

		env.m121_root.type=follow
		env.m121_root.ssh.host=192.168.1.121
		env.m121_root.ssh.user=root
		env.m121_root.ssh.pwd=********
		env.m121_root.cubrid.cubrid_port_id=12019
		env.m121_root.broker1.BROKER_PORT=22019
		env.m121_root.broker2.BROKER_PORT=32019
		env.m121_root.ha.ha_port_id=42019

		env.m122_shell_ext1.ssh.host=192.168.1.122
		env.m122_shell_ext1.ssh.user=shell_ext1
		env.m122_shell_ext1.cubrid.cubrid_port_id=12020
		env.m122_shell_ext1.broker1.BROKER_PORT=22020
		env.m122_shell_ext1.broker2.BROKER_PORT=32020
		env.m122_shell_ext1.ha.ha_port_id=42020

		env.m122_shell_ext2.ssh.host=192.168.1.122
		env.m122_shell_ext2.ssh.user=shell_ext2
		env.m122_shell_ext2.cubrid.cubrid_port_id=12021
		env.m122_shell_ext2.broker1.BROKER_PORT=22021
		env.m122_shell_ext2.broker2.BROKER_PORT=32021
		env.m122_shell_ext2.ha.ha_port_id=42021

		env.m122_shell_ext3.ssh.host=192.168.1.122
		env.m122_shell_ext3.ssh.user=shell_ext3
		env.m122_shell_ext3.cubrid.cubrid_port_id=12022
		env.m122_shell_ext3.broker1.BROKER_PORT=22022
		env.m122_shell_ext3.broker2.BROKER_PORT=32022
		env.m122_shell_ext3.ha.ha_port_id=42022

		env.m122_shell_ext_make.ssh.host=192.168.1.122
		env.m122_shell_ext_make.ssh.user=shell_ext_make
		env.m122_shell_ext_make.cubrid.cubrid_port_id=18722
		env.m122_shell_ext_make.broker1.BROKER_PORT=28722
		env.m122_shell_ext_make.broker2.BROKER_PORT=38722
		env.m122_shell_ext_make.ha.ha_port_id=48722

		env.m122_root.type=follow
		env.m122_root.ssh.host=192.168.1.122
		env.m122_root.ssh.user=root
		env.m122_root.ssh.pwd=********
		env.m122_root.cubrid.cubrid_port_id=12023
		env.m122_root.broker1.BROKER_PORT=22023
		env.m122_root.broker2.BROKER_PORT=32023
		env.m122_root.ha.ha_port_id=42023

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

		env.m123_root.type=follow
		env.m123_root.ssh.host=192.168.1.123
		env.m123_root.ssh.user=root
		env.m123_root.ssh.pwd=********
		env.m123_root.cubrid.cubrid_port_id=12027
		env.m123_root.broker1.BROKER_PORT=22027
		env.m123_root.broker2.BROKER_PORT=32027
		env.m123_root.ha.ha_port_id=42027

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

		env.m124_root.type=follow
		env.m124_root.ssh.host=192.168.1.124
		env.m124_root.ssh.user=root
		env.m124_root.ssh.pwd=********
		env.m124_root.cubrid.cubrid_port_id=12031
		env.m124_root.broker1.BROKER_PORT=22031
		env.m124_root.broker2.BROKER_PORT=32031
		env.m124_root.ha.ha_port_id=42031

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

		env.m125_root.type=follow
		env.m125_root.ssh.host=192.168.1.125
		env.m125_root.ssh.user=root
		env.m125_root.ssh.pwd=********
		env.m125_root.cubrid.cubrid_port_id=12035
		env.m125_root.broker1.BROKER_PORT=22035
		env.m125_root.broker2.BROKER_PORT=32035
		env.m125_root.ha.ha_port_id=42035

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

		env.m126_root.type=follow
		env.m126_root.ssh.host=192.168.1.126
		env.m126_root.ssh.user=root
		env.m126_root.ssh.pwd=********
		env.m126_root.cubrid.cubrid_port_id=12039
		env.m126_root.broker1.BROKER_PORT=22039
		env.m126_root.broker2.BROKER_PORT=32039
		env.m126_root.ha.ha_port_id=42039

		env.m127_shell_ext1.ssh.host=192.168.1.127
		env.m127_shell_ext1.ssh.user=shell_ext1
		env.m127_shell_ext1.cubrid.cubrid_port_id=12040
		env.m127_shell_ext1.broker1.BROKER_PORT=22040
		env.m127_shell_ext1.broker2.BROKER_PORT=32040
		env.m127_shell_ext1.ha.ha_port_id=42040

		env.m127_shell_ext2.ssh.host=192.168.1.127
		env.m127_shell_ext2.ssh.user=shell_ext2
		env.m127_shell_ext2.cubrid.cubrid_port_id=12041
		env.m127_shell_ext2.broker1.BROKER_PORT=22041
		env.m127_shell_ext2.broker2.BROKER_PORT=32041
		env.m127_shell_ext2.ha.ha_port_id=42041

		env.m127_shell_ext3.ssh.host=192.168.1.127
		env.m127_shell_ext3.ssh.user=shell_ext3
		env.m127_shell_ext3.cubrid.cubrid_port_id=12042
		env.m127_shell_ext3.broker1.BROKER_PORT=22042
		env.m127_shell_ext3.broker2.BROKER_PORT=32042
		env.m127_shell_ext3.ha.ha_port_id=42042

		env.m127_root.type=follow
		env.m127_root.ssh.host=192.168.1.127
		env.m127_root.ssh.user=root
		env.m127_root.ssh.pwd=********
		env.m127_root.cubrid.cubrid_port_id=12043
		env.m127_root.broker1.BROKER_PORT=22043
		env.m127_root.broker2.BROKER_PORT=32043
		env.m127_root.ha.ha_port_id=42043

		env.m128_shell_ext1.ssh.host=192.168.1.128
		env.m128_shell_ext1.ssh.user=shell_ext1
		env.m128_shell_ext1.cubrid.cubrid_port_id=12044
		env.m128_shell_ext1.broker1.BROKER_PORT=22044
		env.m128_shell_ext1.broker2.BROKER_PORT=32044
		env.m128_shell_ext1.ha.ha_port_id=42044

		env.m128_shell_ext2.ssh.host=192.168.1.128
		env.m128_shell_ext2.ssh.user=shell_ext2
		env.m128_shell_ext2.cubrid.cubrid_port_id=12045
		env.m128_shell_ext2.broker1.BROKER_PORT=22045
		env.m128_shell_ext2.broker2.BROKER_PORT=32045
		env.m128_shell_ext2.ha.ha_port_id=42045

		env.m128_shell_ext3.ssh.host=192.168.1.128
		env.m128_shell_ext3.ssh.user=shell_ext3
		env.m128_shell_ext3.cubrid.cubrid_port_id=12046
		env.m128_shell_ext3.broker1.BROKER_PORT=22046
		env.m128_shell_ext3.broker2.BROKER_PORT=32046
		env.m128_shell_ext3.ha.ha_port_id=42046

		env.m128_root.type=follow
		env.m128_root.ssh.host=192.168.1.128
		env.m128_root.ssh.user=root
		env.m128_root.ssh.pwd=********
		env.m128_root.cubrid.cubrid_port_id=12047
		env.m128_root.broker1.BROKER_PORT=22047
		env.m128_root.broker2.BROKER_PORT=32047
		env.m128_root.ha.ha_port_id=42047

		cubrid_download_url=http://192.168.1.91:8080/REPO_ROOT/store_01/10.2.0.8295-aeaf5c8/drop/CUBRID-10.2.0.8295-aeaf5c8-Linux.x86_64.sh
		scenario=cubrid-testcases-private/shell_ext
		testcase_retry_num=0
		#testcase_timeout_in_secs=14400
		testcase_update_yn=y
		testcase_git_branch=develop
		test_category=shell_ext
		#testcase_exclude_from_file=/home/shell_ext_ctrl/CTP/conf/shell_exclude.txt
		test_continue_yn=n

		feedback_type=database
		feedback_notice_qahome_url=http://192.168.1.86:8080/qaresult/shellImportAction.nhn?main_id=<MAINID>

		owner_email=Fan<fanzq@navercorp.com>

		git_user=<git user>
		git_email=<git e-mail>
		git_pwd=********

		feedback_db_host=192.168.1.86
		feedback_db_port=33080
		feedback_db_name=qaresu
		feedback_db_user=dba
		feedback_db_pwd=*******

* ### Installation required by test cases

	Please find all selectors and deploy test environments one by one so that test case environment can be met. This is a little time-consuming. 
	
	For example, there is the selector below:

		#python>2.4
		selector.python_node.hosts=m123_shell_ext1|m123_shell_ext2|m123_shell_ext3|m124_shell_ext1|m124_shell_ext2|m124_shell_ext3	
		
	Find one related test case in `cubrid-testcases-private/shell_ext`. In its `test.conf`, the `note` property records requirements of test case. Please deploy following it. In this example, please install python on `m123` and `m124` servers.

* ### Add quick start script file

	On controller node, add a quick start script file.
	
	File ~/start_test.sh
	
		start_consumer.sh -q QUEUE_CUBRID_QA_SHELL_EXT_LINUX -exec run_shell
		

# 4. Regression Test Sustaining

## 4.1 How to start test?

* ### Start test daemon process:

	Log into controller server, keep daemon process for start_test.sh:

	 nohup sh start_test.sh &
	 
	Note: we didn't configure regular execution for SHELL_EXT test automatically. We need send test message to execute by manual.
	
* ### Send test message same as daily configuration

		[message@qa03 ~]$ sender.sh QUEUE_CUBRID_QA_SHELL_EXT_LINUX http://192.168.1.91:8080/REPO_ROOT/store_01/10.2.0.8396-1bc28b2/drop/CUBRID-10.2.0.8396-1bc28b2-Linux.x86_64.sh shell_ext default 

		Message: 

		Message Content: Test for build 10.2.0.8396-1bc28b2 by CUBRID QA Team, China
		MSG_ID = 190910-183309-552-000001
		MSG_PRIORITY = 4
		BUILD_ABSOLUTE_PATH=/home/ci_build/REPO_ROOT/store_01/10.2.0.8396-1bc28b2/drop
		BUILD_BIT=0
		BUILD_CREATE_TIME=1565376350000
		BUILD_GENERATE_MSG_WAY=MANUAL
		BUILD_ID=10.2.0.8396-1bc28b2
		BUILD_IS_FROM_GIT=1
		BUILD_PACKAGE_PATTERN=CUBRID-{1}-Linux.x86_64.sh
		BUILD_SCENARIOS=shell_ext
		BUILD_SCENARIO_BRANCH_GIT=develop
		BUILD_SEND_DELAY=2731639
		BUILD_SEND_TIME=1568107989551
		BUILD_STORE_ID=store_01
		BUILD_SVN_BRANCH=RB-10.2.0
		BUILD_SVN_BRANCH_NEW=RB-10.2.0
		BUILD_TYPE=general
		BUILD_URLS=http://192.168.1.91:8080/REPO_ROOT/store_01/10.2.0.8396-1bc28b2/drop/CUBRID-10.2.0.8396-1bc28b2-Linux.x86_64.sh
		BUILD_URLS_CNT=1
		BUILD_URLS_KR=http://192.168.1.91:8080/REPO_ROOT/store_01/10.2.0.8396-1bc28b2/drop/CUBRID-10.2.0.8396-1bc28b2-Linux.x86_64.sh


		Do you accept above message [Y/N]: Y
		
	After test, related test result will be shown in QA homepage.

## 4.2 Verify test Results

To verify SHELL_EXT test results is similar to general SHELL test except the category name is `'shell_ext'` in QA homepage. Please refer to general SHELL test guide.


# 5. SHELL_EXT Test Case Specification

* ## Source $init_path/init_ext.sh
	
	SHELL_EXT test cases generally need source `$init_path/init_ext.sh` which imports many functions that they will be used in test codes.
	
		#!/bin/bash
		. $init_path/init.sh

		# source common functions used by SHELL_EXT
		. $init_path/init_ext.sh

		init test

		# CASE BODY
		...
		...

		if [ condition ]; then
			write_ok
		else
			write_nok
		fi

		finish 

* ## rexec

		rexec D_HOST1  -c "cmd"
		rexec D_HOST2  -c "cmd"
		...
		rexec D_HOST(n)  -c "cmd"
	
	Execute `cmd` on remote server D_HOST1, D_HOST2, ..., D_HOST(n).
	
	Before execution, please define host for each. These exports are only used for case execution by manual. The automatic regression test will set them automatically. For example,

		export D_DEFAULT_PORT=22
		export D_DEFAULT_PWD=<pwd>
		
		export D_HOST1_IP=192.168.1.123
		export D_HOST1_USER=<user>
		
		export D_HOST2_IP=192.168.1.124
		export D_HOST2_USER=<user>
		
		...
		...
		
		export D_HOST(n)_IP=192.168.1.199
		export D_HOST(n)_USER=<user>
		
* ## r_upload

		r_upload D_HOST1 -from /path/to/local/filename -to /path/to/remote/folder
	
	Upload a file to remote server. Regarding as host defination, it's same as it in `rexec`.
	
* ## r_download

		r_upload D_HOST1 -from /path/to/remote/filename -to /path/to/local/folder
			
	Download a file from remote server. Regarding as host defination, it's same as it in `rexec`.
	
* ## use_cubrid and use_cubrid_main

	If test case depends on more than one CUBRID for some purpose of test, we have more CUBRID to use and we can switch CUBRID with `use_cubrid` and `use_cubrid_main`. 
	
		# switch to assigned CUBRID 
		use_cubrid 10.1.3.7765-265e708
		
		# switch back to default CUBRID
		use_cubrid_main
	
	Note: if want to define more CUBRIDs, please define it in `test.conf` as below
	
		# Except current CUBRID, define another two CUBRIDs.
		cubrid_deps: 10.1.1.7666-4324548,9.3.0.0206

	
