# 1. Test Objective

Unittest cases are provided by developers for the purpose of unit test. In order to enhance quality of CUBRID, these unittest cases were decided to add to daily regression test and execute on both release and debug build. In this guide, the detail usage will be introduced.

# 2. Unittest Test Usage

## 2.1 Quick Start

Let's show an example that how to execute unittest test via CTP.

### Step 1: Prepare test cases

Test cases are written in C or C++ programming language by developers. We need compile them to make executable test cases.

Download cubrid source codes from [git repository of CUBRID](https://github.com/CUBRID/cubrid.git) or package (cubrid-<build id>.tar.gz) in CI build:
  
    wget http://192.168.1.91:8080/REPO_ROOT/store_01/10.2.0.8414-294c026/drop/cubrid-10.2.0.8414-294c026.tar.gz
    tar xzvf cubrid-10.2.0.8414-294c026.tar.gz
    cd cubrid-10.2.0.8414-294c026
    
    # generate test cases of unittest based on release build
    sh build.sh  -t 64 -m release -b build_release 
    
    # generate test cases of unittest_debug on debug build
    # sh build.sh -t 64 -m debug -b build_debug
    
After compile, we may get unittest test cases as below:

    ./build_release/bin/unittests_area
    ./build_release/bin/unittests_bit
    ./build_release/bin/unittests_lf
    ./build_release/bin/unittests_snapshot
  
Then, export `CUBRID` environment variable in order that CTP may find cases by it.

Add to `~/.bash_profile`:
    
    export CUBRID=$HOME/cubrid-10.2.0.8414-294c026

### Step 2: Install CTP

Please refer to guide to [install CTP in Linux platform](ctp_install_guide.md#1-install-ctp-in-linux-platform).

Then prepare test configuration as below:
File CTP/conf/unittest.conf:

    test_category = unittest
    test_platform = linux
    build_id = 10.2.0.8414-294c026
    build_bits = 64bits
    feedback_type = file
    
### Step 3: Start to test

    ctp.sh unittest -c CTP/conf/unittest.conf
    
Output:

    ====================================== UNITTEST ==========================================
    [UNITTEST] TEST STARTED (Wed Aug 28 07:06:54 KST 2019)

    [UNITTEST] CONFIG FILE: /home/unittest/CTP/conf/unittest.conf

    feedback_type=file
    test_category=unittest
    build_bits=64bits
    test_platform=linux
    build_id=10.2.0.8414-294c026

    ----------END OF FILE----------
    => Init Step:


    => List Step:
    /home/unittest/cubrid/build_release/bin/unittests_area
    /home/unittest/cubrid/build_release/bin/unittests_bit
    /home/unittest/cubrid/build_release/bin/unittests_lf
    /home/unittest/cubrid/build_release/bin/unittests_snapshot


    => Execute Step:
    [TESTCASE-1] /home/unittest/cubrid/build_release/bin/unittests_area [SUCC]
    [TESTCASE-2] /home/unittest/cubrid/build_release/bin/unittests_bit [SUCC]
    [TESTCASE-3] /home/unittest/cubrid/build_release/bin/unittests_lf [SUCC]
    [TESTCASE-4] /home/unittest/cubrid/build_release/bin/unittests_snapshot [SUCC]
    => Finish Step:


    Success num: 4, Fail_num: 0, Skipped(macro): 0, Skipped(temp): 0, Total Scenario: 4
    Test Rate: 100.0%
    Success Rate: 100.0%
    [UNITTEST] TEST END (Wed Aug 28 07:56:15 KST 2019)
    [UNITTEST] ELAPSE TIME: 2961 seconds

### Step 4: Examine test result

Test result will be saved in directory `CTP/result/unittest/current_runtime_logs`. 
You may get detail information in file `feedback.log`.

## 2.2 Completed parameters

    # Configure 'unittest' for release build, 'unittest_debug' for debug build.
    test_category = unittest
    test_platform = linux
    
    # Specify test build used
    build_id = 10.2.0.8415-85a422f
    
    # Specify build bits used
    build_bits = 64bits
    
    # Configure 'file' or 'database'.
    # 'file': test result will be saved to CTP/result/unittest/current_runtime_logs/feedback.log
    # 'database': test result will be saved to QA homepage database.
    feedback_type = database
    feedback_db_host = 192.168.1.86
    feedback_db_port = 33080
    feedback_db_name = qaresu
    feedback_db_user = dba
    feedback_db_pwd = 
    
    # When configure 'feedback_type=database', once test is done, it would notice QA homepage to generate verification.
    feedback_notice_qahome_url = http://192.168.1.86:6060/qaresult/shellImportAction.nhn?main_id=<MAINID>    

# 3. Unittest Test Case Specification



# 4. Regression Test Deployment

# 5. Regression Test Sustaining
