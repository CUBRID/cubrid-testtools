# 1. Test Objective

JDBC test is used to verify CUBRID JDBC driver. It focuses on verification of standard JDBC API implementation by CUBRID. It also verifies its native methods.
CTP as test tool can be used to execute CUBRID jdbc test cases which are located in https://github.com/CUBRID/cubrid-testcases-private/tree/develop/interface/JDBC/test_jdbc.

# 2. JDBC Test Usage

## 2.1 Quick Start

Let's show an example that how to execute jdbc test via CTP.

### Step 1: Check out jdbc test cases

    cd ~/
    git clone https://github.com/CUBRID/cubrid-testcases-private.git
    git checkout develop  

### Step 2: Install CTP
Please follow [the guide to install CTP](ctp_install.md).

### Step 3: Prepare for test configuration

File CTP/conf/jdbc.conf:

    [common]
    # Define the path of test cases we just check out.
    scenario = ${HOME}/cubrid-testcases/interface/JDBC/test_jdbc

    [jdbc/cubrid.conf]
    cubrid_port_id = 1822

    [jdbc/cubrid_broker.conf/%query_editor]
    SERVICE = OFF

    [jdbc/cubrid_broker.conf/%BROKER1]
    BROKER_PORT = 33120
    APPL_SERVER_SHM_ID = 33120

    [jdbc/cubrid_broker.conf/broker]
    MASTER_SHM_ID = 33122

### Step 4: Execute jdbc test

    cd CTP
    sh ctp.sh jdbc -c ./conf/jdbc.conf

### Step 5: Examine the results

When the test is completed, you can find the results and from directory as below:

File CTP/result/jdbc/current_runtime_logs/run_case_details.log:

    [INFO]: Test Start!
    [INFO]: Total Case:2315
    [INFO]: TEST BUILD:10.2.0.8398-48e1a3c
    [TESTCASE]: /home/jdbc/cubrid-testcases-private/interface/JDBC/test_jdbc/src/com/cubrid/jdbc/test/spec/connection/TestCatalog.java => test1() => OK
    [ELAPSE TIME(ms)]: 57
    ===================================================
    [TESTCASE]: /home/jdbc/cubrid-testcases-private/interface/JDBC/test_jdbc/src/com/cubrid/jdbc/test/spec/connection/TestClientInfo4.java => test1() => OK
    [ELAPSE TIME(ms)]: 1
    ===================================================
    [TESTCASE]: /home/jdbc/cubrid-testcases-private/interface/JDBC/test_jdbc/src/com/cubrid/jdbc/test/spec/connection/TestClientInfo4.java => test2() => OK
    [ELAPSE TIME(ms)]: 0

    ......
    ......
    
    [TESTCASE]: /home/jdbc/cubrid-testcases-private/interface/JDBC/test_jdbc/src/testsuite/BaseTestCase.java => runMultiHostTests() => OK
    [ELAPSE TIME(ms)]: 0
    ===================================================
    [INFO]: Test Finished!
    ==================== Test Summary ====================
    [INFO]: Total Case:2315
    [INFO]: Success Case:2313
    [INFO]: Fail Case:2


## 2.2 JDBC test usage via CTP

### Introduce configuration

Full configuration 
    
    [common]
    # Define the location of your testing scenario
    scenario = ${HOME}/cubrid-testcases-private/interface/JDBC/test_jdbc
    
    # test result will be categoried with specific defination in QA homepage
    test_platform = linux
    test_category = jdbc
    build_bits = 64bits
    owner_email = fan.zaiqiang@navercorp.com
    
    # feedback type: 'database' or 'file'. 
    # 'database': test result will be stored to database. 'file': test result will save as local files.
    feedback_type = database
    # Defined QA homepage database. The parameters are only enabled when feedback_type is configured as 'database'. 
    feedback_db_host = 192.168.1.86
    feedback_db_port = 33080
    feedback_db_name = qaresu
    feedback_db_user = dba
    feedback_db_pwd =
    
    # Notice test completion event to QA homepage once test has been done.
    feedback_notice_qahome_url = http://192.168.1.86:6060/qaresult/shellImportAction.nhn?main_id=<MAINID>

    [jdbc/cubrid.conf]
    # All parameters in this section will be set to cubrid.conf
    cubrid_port_id = 1822

    [jdbc/cubrid_broker.conf/%query_editor]
    # All parameters in this section will be set to section '%query_editor' in cubrid_broker.conf.
    SERVICE = OFF

    [jdbc/cubrid_broker.conf/%BROKER1]
    # All parameters in this section will be set to section '%BROKER1' in cubrid_broker.conf.
    BROKER_PORT = 33120
    APPL_SERVER_SHM_ID = 33120

    [jdbc/cubrid_broker.conf/broker]
    # All parameters in this section will be set to section 'broker' in cubrid_broker.conf.    
    MASTER_SHM_ID = 33122

# 3. JDBC Test Case Specification

# 4. Regression Test Deployment

# 5. Regression Test Sustaining
