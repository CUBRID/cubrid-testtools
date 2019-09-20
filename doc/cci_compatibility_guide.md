# CCI Compatibility Test Guide

# 1. Test Objective
The CCI compatibility test is aimed to test CUBRID compatibility with different version's CCI driver and server. Actually the test cases is the same with CCI test.

# 2. Execute CCI Compatibility Test

To perform the test, we need to install CTP first.   

## 2.1 Install CTP
Please refer to [CTP installation in CCI guide](cci_guide.md#21-install-ctp).    

## 2.2 Prepare Test Cases
Please refer to [Prepare Test Cases In CCI guide](cci_guide.md#22-prepare-test-cases).    

## 2.3 Install CUBRID 
For compatibility test, we need to install different CCI driver and CUBRID Server version. For example:
If want to test 10.2's server with 8.4.1's driver, install CUBRID as below:     
```bash    
run_cubrid_install -s http://192.168.1.91:8080/REPO_ROOT/store_01/10.2.0.8396-1bc28b2/drop/CUBRID-10.2.0.8396-1bc28b2-Linux.x86_64.sh http://192.168.1.91:8080/REPO_ROOT/store_03/8.4.1.35001/drop/CUBRID-8.4.1.35001-linux.x86_64.sh   
```
If want to test 10.2's driver with 8.4.1's server, install CUBRID as below:     
```bash    
run_cubrid_install -d http://192.168.1.91:8080/REPO_ROOT/store_01/10.2.0.8396-1bc28b2/drop/CUBRID-10.2.0.8396-1bc28b2-Linux.x86_64.sh http://192.168.1.91:8080/REPO_ROOT/store_03/8.4.1.35001/drop/CUBRID-8.4.1.35001-linux.x86_64.sh   
```

## 2.4 Execute test      
Please refer to [Execute test in CCI guide](cci_guide.md#24-execute-test).     

## 2.5 Examine test results
Please refer to [Examine test results in CCI guide](cci_guide.md#25-examine-test-results).      

# 3. Deploy Regression Test Environment
## 3.1 Test Machines
For current daily regression test, there are 4 test instances in parallel. The whole test for a test instance is deployed under a system user environment.

No.|role|user|ip|hostname
--|--|--|--|--
1.|Test instance 1|ccompat1|192.168.1.80|func05
2.|Test instance 2|ccompat2|192.168.1.80|func05
3.|Test instance 3|ccompat3|192.168.1.80|func05
4.|Test instance 4|ccompat4|192.168.1.80|func05

## 3.2 Deploy Test Environment

* ### Install CTP     

    Please refer to [install CTP as Regression Test platform](ctp_install_guide.md#3-install-ctp-as-regression-test-platform). Then create CCI compatibility test configuration.

    File `~/CTP/conf/shell_template.conf`:     

    On instance 1:

        default.cubrid.cubrid_port_id=8217
        default.broker1.BROKER_PORT=8417
        default.broker1.APPL_SERVER_SHM_ID=8417
        default.broker2.BROKER_PORT=8517
        default.broker2.APPL_SERVER_SHM_ID=8517

        scenario=$HOME/cubrid-testcases-private/interface/CCI/shell/_20_cci
        test_continue_yn=false
        testcase_exclude_from_file=
        testcase_update_yn=true
        testcase_git_branch=develop
        testcase_timeout_in_secs=7200
        test_platform=linux
        test_category=cci
        testcase_exclude_by_macro=LINUX_NOT_SUPPORTED
        testcase_retry_num=0

        git_user=cubridqa
        git_pwd=******
        git_email=dl_cubridqa_bj_internal@navercorp.com

        feedback_type=database
        feedback_notice_qahome_url=http://192.168.1.86:6060/qaresult/shellImportAction.nhn?main_id=<MAINID>
        feedback_db_host=192.168.1.86
        feedback_db_port=33080
        feedback_db_name=qaresu
        feedback_db_user=dba
        feedback_db_pwd=

    On instance 2, change to use different ports:

        default.cubrid.cubrid_port_id=8218
        default.broker1.BROKER_PORT=8418
        default.broker1.APPL_SERVER_SHM_ID=8418
        default.broker2.BROKER_PORT=8518
        default.broker2.APPL_SERVER_SHM_ID=8518
        ...

    On instance 3, be same as above:
    
        default.cubrid.cubrid_port_id=8219
        default.broker1.BROKER_PORT=8419
        default.broker1.APPL_SERVER_SHM_ID=8419
        default.broker2.BROKER_PORT=8519
        default.broker2.APPL_SERVER_SHM_ID=8519
        ...
    
    On instance 4, be same as above:
    
        default.cubrid.cubrid_port_id=8220
        default.broker1.BROKER_PORT=8420
        default.broker1.APPL_SERVER_SHM_ID=8420
        default.broker2.BROKER_PORT=8520
        default.broker2.APPL_SERVER_SHM_ID=8520
        ...
   
* ### Create quick start script 

    File `~/start_test.sh`

        #!/bin/sh
        cd $HOME/CTP/common/script
        sh upgrade.sh
        cd $HOME
        rm -f nohup.out
        nohup start_consumer.sh -q QUEUE_CUBRID_QA_COMPAT_CCI_SHELL_DRIVER_64,QUEUE_CUBRID_QA_COMPAT_CCI_SHELL_SERVER_64 -exec run_compat_cci,run_compat_cci -s china &

* ### Check out test cases         

        cd ~
        git clone https://github.com/CUBRID/cubrid-testcases-private.git 

* ### Configure .bash_profile    
    
        export DEFAULT_BRANCH_NAME=develop
        export CTP_HOME=$HOME/CTP
        export CTP_BRANCH_NAME=develop
        export CTP_SKIP_UPDATE=0
        export init_path=$HOME/CTP/shell/init_path

        ulimit -c unlimited
        export LC_ALL=en_US

        . $HOME/.cubrid.sh
        export PATH=$CTP_HOME/bin:$CTP_HOME/common/script:$JAVA_HOME/bin:/usr/local/bin:/bin:/usr/bin:$PATH
        
* ### Job configuration

    Log into message@192.168.0.90, configure job configuration related to CCI compatibility test.
    
  * CTP/conf/job.conf 
  
        ...
        job_compat.service=ON
        job_compat.crontab=0/5 * * * * ?
        job_compat.listenfile=CUBRID-{1}-linux.x86_64.sh
        job_compat.acceptversions=10.0.*.0~8999,10.1.*,10.2.*
        #job_compat.denyversions=
        job_compat.package_bits=64
        job_compat.package_type=general
        ...
        job_compat.test.3.scenario=compat_cci_D64
        job_compat.test.3.queue=QUEUE_CUBRID_QA_COMPAT_CCI_SHELL_DRIVER_64
        job_compat.test.3.ext_config=compat.conf
        job_compat.test.3.ext_keys=cci_compatibility_for_{version}_D64

        job_compat.test.4.scenario=compat_cci_S64
        job_compat.test.4.queue=QUEUE_CUBRID_QA_COMPAT_CCI_SHELL_SERVER_64
        job_compat.test.4.ext_config=compat.conf
        job_compat.test.4.ext_keys=cci_compatibility_for_{version}_S64
        ...

   * CTP/conf/compat.conf
 
         EXT_KEY_FOR_FIX_MAX=COMPAT_BUILD_ID
         EXT_KEY_FOR_FIX_MAX_FOLLOW=COMPAT_BUILD_URLS
         EXT_KEY_FOR_FIX_MAX_FOLLOW_KR=COMPAT_BUILD_URLS_KR
         EXT_KEY_FOR_TEST_CATAGORY=COMPAT_TEST_CATAGORY
         ...
         cci_compatibility_for_10.2.0_D64=cci_shell_10.1_S64.msg,cci_shell_10.0_S64.msg

         cci_compatibility_for_10.1.0_D64=cci_shell_10.2_S64.msg,cci_shell_10.0_S64.msg

         cci_compatibility_for_10.0.0_D64=cci_shell_10.2_S64.msg,cci_shell_10.1_S64.msg
         cci_compatibility_for_10.2.0_S64=cci_shell_10.1_D64.msg,cci_shell_10.0_D64.msg,cci_shell_9.3_D64.msg,cci_shell_9.2_D64.msg,cci_shell_8.4.4_D64.msg,cci_shell_8.4.3_D64.msg,cci_shell_8.4.1_D64.msg,

          cci_compatibility_for_10.1.0_S64=cci_shell_10.2_D64.msg,cci_shell_10.0_D64.msg,cci_shell_9.3_D64.msg,cci_shell_9.2_D64.msg,cci_shell_8.4.4_D64.msg,cci_shell_8.4.3_D64.msg,cci_shell_8.4.1_D64.msg,

          cci_compatibility_for_10.0.0_S64=cci_shell_10.2_D64.msg,cci_shell_10.1_D64.msg,cci_shell_9.3_D64.msg,cci_shell_9.2_D64.msg,cci_shell_8.4.4_D64.msg,cci_shell_8.4.3_D64.msg,cci_shell_8.4.1_D64.msg,

   *  Detail compat configuration
      
          ~/CTP/conf/compat/cci_shell_8.4.3_D64.msg
          COMPAT_BUILD_ID=8.4.3.{max}
          COMPAT_BUILD_URLS=CUBRID-{BUILD_ID}-linux.x86_64.sh
          COMPAT_BUILD_BIT=64
          COMPAT_BUILD_TYPE=general
          COMPAT_BUILD_SCENARIOS=cci

          ~/CTP/conf/compat/cci_shell_10.0_S64.msg
          COMPAT_BUILD_ID=10.0.{dmax}
          COMPAT_BUILD_URLS=CUBRID-{BUILD_ID}-linux.x86_64.sh
          COMPAT_BUILD_BIT=64
          COMPAT_BUILD_TYPE=general
          COMPAT_BUILD_SVN_BRANCH=unknown
          COMPAT_BUILD_SCENARIOS=cci
          COMPAT_BUILD_SCENARIO_BRANCH_GIT=release/10.0

          ~/CTP/conf/compat/cci_shell_10.1_D64.msg
          COMPAT_BUILD_ID=10.1.{dmax}
          COMPAT_BUILD_URLS=CUBRID-{BUILD_ID}-Linux.x86_64.sh
          COMPAT_BUILD_BIT=64
          COMPAT_BUILD_TYPE=general
          COMPAT_BUILD_SCENARIOS=cci

          ~/CTP/conf/compat/cci_shell_10.2_D64.msg
          COMPAT_BUILD_ID=10.2.{dmax}
          COMPAT_BUILD_URLS=CUBRID-{BUILD_ID}-Linux.x86_64.sh
          COMPAT_BUILD_BIT=64
          COMPAT_BUILD_TYPE=general
          COMPAT_BUILD_SCENARIOS=cci

          ~/CTP/conf/compat/cci_shell_9.1_D64.msg
          COMPAT_BUILD_ID=9.1.0.0212
          COMPAT_BUILD_URLS=CUBRID-{BUILD_ID}-linux.x86_64.sh
          COMPAT_BUILD_BIT=64
          COMPAT_BUILD_TYPE=general
          COMPAT_BUILD_SCENARIOS=cci

          ~/CTP/conf/compat/cci_shell_9.2_D64.msg
          COMPAT_BUILD_ID=9.2.{dmax}
          COMPAT_BUILD_URLS=CUBRID-{BUILD_ID}-linux.x86_64.sh
          COMPAT_BUILD_BIT=64
          COMPAT_BUILD_TYPE=general
          COMPAT_BUILD_SCENARIOS=cci

          ~/CTP/conf/compat/cci_shell_10.1_S64.msg
          COMPAT_BUILD_ID=10.1.{dmax}
          COMPAT_BUILD_URLS=CUBRID-{BUILD_ID}-Linux.x86_64.sh
          COMPAT_BUILD_BIT=64
          COMPAT_BUILD_TYPE=general
          COMPAT_BUILD_SVN_BRANCH=unknown
          COMPAT_BUILD_SCENARIOS=cci
          COMPAT_BUILD_SCENARIO_BRANCH_GIT=release/10.1

          ~/CTP/conf/compat/cci_shell_10.2_S64.msg
          COMPAT_BUILD_ID=10.2.{dmax}
          COMPAT_BUILD_URLS=CUBRID-{BUILD_ID}-Linux.x86_64.sh
          COMPAT_BUILD_BIT=64
          COMPAT_BUILD_TYPE=general
          COMPAT_BUILD_SVN_BRANCH=unknown
          COMPAT_BUILD_SCENARIOS=cci
          COMPAT_BUILD_SCENARIO_BRANCH_GIT=develop

          ~/CTP/conf/compat/cci_shell_10.0_D64.msg
          COMPAT_BUILD_ID=10.0.{dmax}
          COMPAT_BUILD_URLS=CUBRID-{BUILD_ID}-linux.x86_64.sh
          COMPAT_BUILD_BIT=64
          COMPAT_BUILD_TYPE=general
          COMPAT_BUILD_SCENARIOS=cci

          ~/CTP/conf/compat/cci_shell_9.0_D64.msg
          COMPAT_BUILD_ID=9.0.{dmax}
          COMPAT_BUILD_URLS=CUBRID-{BUILD_ID}-linux.x86_64.sh
          COMPAT_BUILD_BIT=64
          COMPAT_BUILD_TYPE=general
          COMPAT_BUILD_SCENARIOS=cci

          ~/CTP/conf/compat/cci_shell_8.4.5_D64.msg
          COMPAT_BUILD_ID=8.4.5.{max}
          COMPAT_BUILD_URLS=CUBRID-{BUILD_ID}-linux.x86_64.sh
          COMPAT_BUILD_BIT=64
          COMPAT_BUILD_TYPE=general
          COMPAT_BUILD_SCENARIOS=cci

          ~/CTP/conf/compat/cci_shell_8.4.4_D64.msg
          COMPAT_BUILD_ID=8.4.4.{max}
          COMPAT_BUILD_URLS=CUBRID-{BUILD_ID}-linux.x86_64.sh
          COMPAT_BUILD_BIT=64
          COMPAT_BUILD_TYPE=general
          COMPAT_BUILD_SCENARIOS=cci

          ~/CTP/conf/compat/cci_shell_9.3_D64.msg
          COMPAT_BUILD_ID=9.3.{dmax}
          COMPAT_BUILD_URLS=CUBRID-{BUILD_ID}-linux.x86_64.sh
          COMPAT_BUILD_BIT=64
          COMPAT_BUILD_TYPE=general
          COMPAT_BUILD_SCENARIOS=cci

          ~/CTP/conf/compat/cci_shell_9.4_D64.msg
          COMPAT_BUILD_ID=9.4.{dmax}
          COMPAT_BUILD_URLS=CUBRID-{BUILD_ID}-linux.x86_64.sh
          COMPAT_BUILD_BIT=64
          COMPAT_BUILD_TYPE=general
          COMPAT_BUILD_SCENARIOS=cci

          ~/CTP/conf/compat/cci_shell_8.4.1_D64.msg
          COMPAT_BUILD_ID=8.4.1.{max}
          COMPAT_BUILD_URLS=CUBRID-{BUILD_ID}-linux.x86_64.sh
          COMPAT_BUILD_BIT=64
          COMPAT_BUILD_TYPE=general
          COMPAT_BUILD_SCENARIOS=cci

      >Note: cci_shell_10.0_D64.msg - it means test current build's server with 10.0's driver     
      cci_shell_10.0_S64.msg - it means test current build's driver with 10.0's server     
      cci_shell_8.4.1_D64.msg - it means test current build's server with 8.4.1's driver         

* ### Install depended packages

    Test cases use `killall` command. If no such utility in your OS, please install it.
    
       yum install psmisc
       
# 4. Regression Test Sustaining

We perform CCI compatibility test for each build as regression policy.     

## 4.1 Start the listener

When a new build comes, the test will start. We just need to make sure that test environment has no problem and listener has been started

    $ cd ~
    $ sh start_test.sh &
    $ tail -f nohup.out

## 4.2 Send test messages

Sometimes, in order to investigate or correct a test, we need to send messages manually.
 
* ### Send test messages for server test by manual  
    
    For current server test, we use queue `"QUEUE_CUBRID_QA_COMPAT_CCI_SHELL_SERVER_64"`, and select driver configurations in `"~/CTP/conf/compat"`. 
    
    For example, login message@192.168.1.91     
    
    ```bash
    sender.sh QUEUE_CUBRID_QA_COMPAT_CCI_SHELL_SERVER_64 http://192.168.1.91:8080/REPO_ROOT/store_01/10.2.0.8369-5a75e41/drop/CUBRID-10.2.0.8369-5a75e41-Linux.x86_64.sh compat_cci default ~/CTP/conf/compat/cci_shell_8.4.1_D64.msg 
    ```
    
    >Note: you just need to select the message configuration file corresponding to the driver version   
    > 8.4.1 driver -> cci_shell_8.4.1_D64.msg   
    > 8.4.3 driver -> cci_shell_8.4.1_D64.msg   
    > 8.4.4 driver -> cci_shell_8.4.1_D64.msg   
    > 9.2.x driver -> cci_shell_9.2_D64.msg   
    > 9.3.x driver -> cci_shell_9.3_D64.msg   
    > 10.0 driver -> cci_shell_10.0_D64.msg   
    > 10.1 driver -> cci_shell_10.1_D64.msg    

    **Example to send all messages for different driver:**     
    
        sender.sh QUEUE_CUBRID_QA_COMPAT_CCI_SHELL_SERVER_64 http://192.168.1.91:8080/REPO_ROOT/store_01/10.2.0.8429-2e1a113/drop/CUBRID-10.2.0.8429-2e1a113-Linux.x86_64.sh compat_cci default -compatALL
    
    **Example to send message for 10.2 server and 8.4.1 driver:**
        
        $ sender.sh QUEUE_CUBRID_QA_COMPAT_CCI_SHELL_SERVER_64 http://192.168.1.91:8080/REPO_ROOT/store_01/10.2.0.8369-5a75e41/drop/CUBRID-10.2.0.8369-5a75e41-Linux.x86_64.sh compat_cci default ~/CTP/conf/compat/cci_shell_8.4.1_D64.msg 

        Message: 

        Message Content: Test for build 10.2.0.8369-5a75e41 by CUBRID QA Team, China
        MSG_ID = 190903-162810-654-000001
        MSG_PRIORITY = 4
        BUILD_ABSOLUTE_PATH=/home/ci_build/REPO_ROOT/store_01/10.2.0.8369-5a75e41/drop
        BUILD_BIT=0
        BUILD_CREATE_TIME=1561143743000
        BUILD_GENERATE_MSG_WAY=MANUAL
        BUILD_ID=10.2.0.8369-5a75e41
        BUILD_IS_FROM_GIT=1
        BUILD_PACKAGE_PATTERN=CUBRID-{1}-Linux.x86_64.sh
        BUILD_SCENARIOS=compat_cci
        BUILD_SCENARIO_BRANCH_GIT=develop
        BUILD_SEND_DELAY=6351947
        BUILD_SEND_TIME=1567495690653
        BUILD_STORE_ID=store_01
        BUILD_SVN_BRANCH=RB-10.2.0
        BUILD_SVN_BRANCH_NEW=RB-10.2.0
        BUILD_TYPE=general
        BUILD_URLS=http://192.168.1.91:8080/REPO_ROOT/store_01/10.2.0.8369-5a75e41/drop/CUBRID-10.2.0.8369-5a75e41-Linux.x86_64.sh
        BUILD_URLS_CNT=1
        BUILD_URLS_KR=http://192.168.1.91:8080/REPO_ROOT/store_01/10.2.0.8369-5a75e41/drop/CUBRID-10.2.0.8369-5a75e41-Linux.x86_64.sh
        COMPAT_BUILD_BIT=64
        COMPAT_BUILD_ID=8.4.1.35001
        COMPAT_BUILD_SCENARIOS=cci
        COMPAT_BUILD_TYPE=general
        COMPAT_BUILD_URLS=http://192.168.1.91:8080/REPO_ROOT/store_03/8.4.1.35001/drop/CUBRID-8.4.1.35001-linux.x86_64.sh
        COMPAT_BUILD_URLS_KR=null/8.4.1.35001/drop/CUBRID-8.4.1.35001-linux.x86_64.sh
        COMPAT_TEST_CATAGORY=cci_shell_8.4.1_D64
        MSG_FILEID=cci_shell_8.4.1_D64


        Do you accept above message [Y/N]:  Y


* ### Send test messages for driver test by manual  

    For current driver test, we use queue `"QUEUE_CUBRID_QA_COMPAT_CCI_SHELL_DRIVER_64"`, and select server configurations in `"~/CTP/conf/compat"`.    
    
    For example, login message@192.168.1.91.
    
    ```bash
    sender.sh QUEUE_CUBRID_QA_COMPAT_CCI_SHELL_DRIVER_64 http://192.168.1.91:8080/REPO_ROOT/store_01/10.2.0.8369-5a75e41/drop/CUBRID-10.2.0.8369-5a75e41-Linux.x86_64.sh compat_cci default ~/CTP/conf/compat/cci_shell_8.4.1_S64.msg
    ```
    >Note: you just need to select the message configuration file corresponding to the server version    
    > 8.4.1 server -> cci_shell_8.4.1_S64.msg   
    > 8.4.3 server -> cci_shell_8.4.3_S64.msg    
    > 8.4.4 server -> cci_shell_8.4.4_S64.msg    
    > 9.2.x server -> cci_shell_9.2_S64.msg    
    > 9.3.x server -> cci_shell_9.3_S64.msg    
    > 10.0 server -> cci_shell_10.0_S64.msg    
    > 10.1 server -> cci_shell_10.1_S64.msg     
    > 10.2 server -> cci_shell_10.2_S64.msg     


    **Example to send message for 10.2 driver and 9.2 server:**
    
         $ sender.sh QUEUE_CUBRID_QA_COMPAT_CCI_SHELL_DRIVER_64 http://192.168.1.91:8080/REPO_ROOT/store_01/10.2.0.8369-5a75e41/drop/CUBRID-10.2.0.8369-5a75e41-Linux.x86_64.sh compat_cci default ~/CTP/conf/compat/cci_shell_9.2_S64.msg 

        Message: 

        Message Content: Test for build 10.2.0.8369-5a75e41 by CUBRID QA Team, China
        MSG_ID = 190903-164149-543-000001
        MSG_PRIORITY = 4
        BUILD_ABSOLUTE_PATH=/home/ci_build/REPO_ROOT/store_01/10.2.0.8369-5a75e41/drop
        BUILD_BIT=0
        BUILD_CREATE_TIME=1561143743000
        BUILD_GENERATE_MSG_WAY=MANUAL
        BUILD_ID=10.2.0.8369-5a75e41
        BUILD_IS_FROM_GIT=1
        BUILD_PACKAGE_PATTERN=CUBRID-{1}-Linux.x86_64.sh
        BUILD_SCENARIOS=compat_cci
        BUILD_SCENARIO_BRANCH_GIT=develop
        BUILD_SEND_DELAY=6352766
        BUILD_SEND_TIME=1567496509541
        BUILD_STORE_ID=store_01
        BUILD_SVN_BRANCH=RB-10.2.0
        BUILD_SVN_BRANCH_NEW=RB-10.2.0
        BUILD_TYPE=general
        BUILD_URLS=http://192.168.1.91:8080/REPO_ROOT/store_01/10.2.0.8369-5a75e41/drop/CUBRID-10.2.0.8369-5a75e41-Linux.x86_64.sh
        BUILD_URLS_CNT=1
        BUILD_URLS_KR=http://192.168.1.91:8080/REPO_ROOT/store_01/10.2.0.8369-5a75e41/drop/CUBRID-10.2.0.8369-5a75e41-Linux.x86_64.sh
        COMPAT_BUILD_BIT=64
        COMPAT_BUILD_ID=9.2.30.0002
        COMPAT_BUILD_SCENARIOS=cci
        COMPAT_BUILD_SVN_BRANCH=RB-9.2.0
        COMPAT_BUILD_TYPE=general
        COMPAT_BUILD_URLS=http://192.168.1.91:8080/REPO_ROOT/store_03/9.2.30.0002/drop/CUBRID-9.2.30.0002-linux.x86_64.sh
        COMPAT_BUILD_URLS_KR=null/9.2.30.0002/drop/CUBRID-9.2.30.0002-linux.x86_64.sh
        COMPAT_TEST_CATAGORY=cci_shell_9.2_S64
        MSG_FILEID=cci_shell_9.2_S64

        Do you accept above message [Y/N]: Y

        
## 4.3 Check running status 

There are two ways. One is to check nohup.out log on the controller node. The other way is to check cci compatibility items on qahome monitor page. Please refer to [Check running status of cci guide](cci_guide.md#check-running-status)

## 4.4 Verify test result
* #### Check whether there are results

    Open [QA homepage](http://qahome.cubrid.org), then navigate as below to find CCI compatibility test results. 

    ![CCI Compatibility Test Result](./cci_compatibility_image/image_main.png)

    If some test shows `'NO RESULT'`, we need investigate reasons and resolve it.
    
* #### Both Test Rate and Verified Rate should be 100%
   
   In above picture, the figures with red color mean number of failures. Click it to open verification page. Then follow the same way as CCI test's to verify all failures. Both Test Rate and Verified Rate should be 100%.
    
# 5. CCI Compatibility Test Case 
## 5.1 Choose matched test cases

CCI compatibility test seperates different CUBRID on Server and Driver. When perform a combination to test, we need choose matched test cases. That is to say, which branch does test cases come. This answer is just that test case branch should follow the CUBRID as Server role. Let's example it.

CCI Driver | CUBRID Server | Expected Test Case Branch|
-|-|-
10.2 CCI Driver| 10.1 Server | [10.1 Test Cases](https://github.com/CUBRID/cubrid-testcases-private/tree/release/10.1/interface/CCI/shell)
10.2 CCI Driver| 10.0 Server | [10.0 Test Cases](https://github.com/CUBRID/cubrid-testcases-private/tree/release/10.0/interface/CCI/shell)
8.4.4 CCI Driver| 10.2 Server | [10.2 Test Cases](https://github.com/CUBRID/cubrid-testcases-private/tree/develop/interface/CCI/shell)
9.3 CCI Driver| 10.2 Server | [10.2 Test Cases](https://github.com/CUBRID/cubrid-testcases-private/tree/develop/interface/CCI/shell)


## 5.2 Test Case Specification
Please follow [CCI test case specification](cci_guide.md#5-cci-test-case-specification). They are same.
