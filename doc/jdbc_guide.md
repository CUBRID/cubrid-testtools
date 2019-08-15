# 1. Test Objective

JDBC test is used to verify CUBRID JDBC driver. It focuses on verification of standard JDBC API implementation by CUBRID. It also verifies its native methods.
CTP as test tool can be used to execute CUBRID jdbc test cases which are located in https://github.com/CUBRID/cubrid-testcases-private/tree/develop/interface/JDBC/test_jdbc.

# 2. JDBC Test Usage

## 2.1 Quick Start

Let's show an example that how to execute jdbc test via CTP.

### Check out jdbc test cases

    cd ~/
    git clone https://github.com/CUBRID/cubrid-testcases-private.git
    git checkout develop  

### Install CTP
Please follow [the guide to install CTP](ctp_install.md).

### Configure test

File CTP/conf/jdbc.conf:

    [common]
    # Define the path of test cases used for testing, it should be checked out in advance
    scenario = ${HOME}/cubrid-testcases/interface/JDBC/test_jdbc

    # JDBC cubrid.conf section - a section for cubrid.conf configuration
    [jdbc/cubrid.conf]
    # Define the port of cubrid_port_id to avoid port conflict
    cubrid_port_id = 1822

    # JDBC cubrid_broker.conf query editor section - a section to change parameters under query_editor
    [jdbc/cubrid_broker.conf/%query_editor]
    # Close one service to avoid port conflict and reduce configuration complexity
    SERVICE = OFF

    # JDBC cubrid_broker.conf broker1 section - a section to change parameters under broker1
    [jdbc/cubrid_broker.conf/%BROKER1]
    # Define the port of broker to avoid conflict
    BROKER_PORT = 33120

    # Define ID of shared memory used by CAS
    APPL_SERVER_SHM_ID = 33120

    # JDBC cubrid_broker.conf broker section - a section to configure parameters under broker section
    [jdbc/cubrid_broker.conf/broker]
    # Define the identifier of shared memory to avoid conflict
    MASTER_SHM_ID = 33122

### Execute jdbc test

    cd CTP
    sh ctp.sh jdbc -c ./conf/jdbc.conf

### Examine the results

When the test is completed, you can find the results and logs from CTP/result/jdbc/current_runtime_logs
run_case_details.log shows all the details of case running.
    

# 3. JDBC Test Case Specification

# 4. Regression Test Deployment

# 5. Regression Test Sustaining
