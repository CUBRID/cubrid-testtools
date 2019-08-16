# 1. Test Objective

JDBC test is used to verify CUBRID JDBC driver. It focuses on verification of standard JDBC API implementation by CUBRID. It also verifies its native methods.
CTP as test tool can be used to execute CUBRID jdbc test cases which are located in https://github.com/CUBRID/cubrid-testcases-private/tree/develop/interface/JDBC/test_jdbc.

# 2. JDBC Test Usage

## 2.1 Quick Start

Let's show an example that how to execute jdbc test via CTP.

### Step 1: Prepare for tested database

Suppose that you have already installed CUBRID. Then, let's create a database that we plan to test.

    cubrid createdb jdbcdb en_US
    cubrid server start jdbcdb
    cubrid broker start    

### Step 2: Prepare for jdbc test cases

    cd ~/
    git clone https://github.com/CUBRID/cubrid-testcases-private.git
    git checkout develop  

### Step 3: Install CTP

Please follow [the guide to install CTP](ctp_install_guide.md).

### Step 4: Prepare for test configuration

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
    
### Step 5: Execute jdbc test

    cd CTP
    sh ctp.sh jdbc -c ./conf/jdbc.conf

### Step 6: Examine the results

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


## 2.2 Completed configuration parameters

CTP ships an example configuration file under CTP/conf/jdbc.conf. You may change it as your requirement or create your own configuration file. Here is full parameters used:
    
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
    
## 2.3 Test cases introduction

Test cases locate in https://github.com/CUBRID/cubrid-testcases-private/tree/develop/interface/JDBC/test_jdbc.

    [jdbc@func23 test_jdbc]$ pwd
    /home/jdbc/cubrid-testcases-private/interface/JDBC/test_jdbc
    [jdbc@func23 test_jdbc]$ ls -l
    total 32
    -rw-rw-r--. 1 jdbc jdbc  1666 Mar  6 16:19 build.xml
    -rw-rw-r--  1 jdbc jdbc   185 Aug 15 17:16 jdbc.properties
    drwxrwxr-x. 2 jdbc jdbc  4096 Aug 15 17:16 lib
    drwxrwxr-x. 8 jdbc jdbc    84 Mar  6 16:19 src
    [jdbc@func23 test_jdbc]$ 
    
All test cases are written in Java program language under jUnit framework.

File `jdbc.properties` configures JDBC connection parameters. 

    jdbc.driverClassName=cubrid.jdbc.driver.CUBRIDDriver
    jdbc.url=jdbc:cubrid:localhost:33000:tdb:::
    jdbc.username=dba
    jdbc.password=
    jdbc.ip=localhost
    jdbc.port=33000
    jdbc.dbname=tdb
    
# 3. JDBC Test Case Specification

* ### Example a jdbc test case

  Source: https://github.com/CUBRID/cubrid-testcases-private/blob/develop/interface/JDBC/test_jdbc/src/com/cubrid/jdbc/test/spec/connection/TestReadOnly.java

        package com.cubrid.jdbc.test.spec.connection;

        import java.sql.DatabaseMetaData;
        import java.sql.SQLException;

        import org.junit.Assert;
        import org.junit.Ignore;
        import org.junit.Test;

        import com.cubrid.jdbc.test.spec.GeneralTestCase;

        public class TestReadOnly extends GeneralTestCase {

            @Test
            public void test1() throws SQLException {
                conn().setReadOnly(false);
                Assert.assertEquals(false, conn().isReadOnly());
            }

            @Ignore
            @Test
            public void test2() throws SQLException {
                conn().setReadOnly(true);
                Assert.assertEquals(true, conn().isReadOnly());
            }

            @Test
            public void test3() throws SQLException {
                DatabaseMetaData dmd = conn().getMetaData();

                if (dmd.isReadOnly()) {
                    Assert.assertEquals(true, conn().isReadOnly());
                } else {
                    Assert.assertEquals(false, conn().isReadOnly());
                }
            }

            @Test
            public void test4() throws SQLException {
                DatabaseMetaData dmd = conn().getMetaData();
                Assert.assertEquals(false, dmd.isReadOnly());
            }
        }



* ###  Annotation `@Test` declares a test case

        @Test
        public void test1() throws SQLException {
            conn().setReadOnly(false);
            Assert.assertEquals(false, conn().isReadOnly());
        }

* ###  Annotation `@Ignore` declares test case will be ignored during runtime

        @Ignore
        @Test
        public void test2() throws SQLException {
            conn().setReadOnly(true);
            Assert.assertEquals(true, conn().isReadOnly());
        }

* ###  Test cases follow jUnit framework

    jUnit version: junit-4.8.2
    
    Many assertions can be used in test cases.    

# 4. Regression Test Deployment

# 5. Regression Test Sustaining
