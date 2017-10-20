# CTP - CUBRID Test Program

## Introduction
CTP is a testing tool for an open source project CUBRID. It is written in Java and easy to execute tests with a simple configuration 


## Requirements
* It supports Linux and Windows (Cygwin is required)
* Install Java 6 or higher version, and you also need to set ``JAVA_HOME`` environment variable to point to the installation directory
* CUBRID and CUBRID_DATABASES environment variables should be configured before executing testing, please refer to http://www.cubrid.org/ for configuration guides

## Quick Start
The section guides users to quickly start SQL test with CTP, for the more categories of testing , please refer to the related sections
* Install a CUBRID build and make sure ``CUBRID`` environment variable is set correctly
* Execute an example test as follows:

    ``` 
    $ bin/ctp.sh sql -c ./conf/sample.conf
    ```
* Once it finishes, please launch webconsole to examine the result:
    ```
    $ bin/ctp.sh webconsole start
    ```
    
* You will have an output as follows:
    ```
    $ bin/ctp.sh webconsole start
	Config: /home/user/CTP/conf/webconsole.conf
	Web Root: /home/user/CTP/sql/webconsole
	Begin to start ...

	Done
	URL:  http://127.0.0.1:8888    
    ```
	
* Please open the URL with your browser
  
## How To Execute
- **SQL**
 - Prepare
	* Make sure to set JAVA_HOME and the required environment variables correctly
 	* Check out the scenarios from [cubrid-testcases](https://github.com/CUBRID/cubrid-testcases) project or prepare your own test cases for testing
 	* Check out CTP and update the value of ``scenario`` parameter within ``CTP/conf/sql.conf`` to point to the path of your scenarios. For the current existing ``SQL`` test, you need to make sure the parameters `` java_stored_procedure=yes``, ``test_mode=yes`` and ``ha_mode=yes`` are configured
 	* **Example** ``sql.conf`` for scenario, data file and some important parameters changes
	  ```
	  # SQL section - a section for CTP tool configuration when executing SQL/MEDIUM testing
	  [sql]

	  # The location of your testing scenario
	  scenario=${HOME}/cubrid-testcases/sql

	  # SQL cubrid.conf section - a section for cubrid.conf configuration
	  [sql/cubrid.conf]

	  # To decide if the Java store procedure will be used when testing
	  java_stored_procedure=yes

	  # Allow scenario to change database system parameter
	  test_mode=yes

	  # In order to simulate the scenario customer uses
	  ha_mode=yes
	  ```
 	* For ``MEDIUM`` test, ``data_file`` must be configured into the [sql] section of the conf file with the path of the initial data file
 	
	  ```
	  # Path of the data file for initial loading
	  data_file=${HOME}/cubrid-testcases/medium/files/mdb.tar.gz
 	  ```   
 	* For ``SQL_BY_CCI`` test, ``sql_interface_type=cci`` must be configured into the [sql] section of the conf file
 	
	  ```
	  # Define the interface type of SQL testing
	  sql_interface_type=cci
 	  ```   
	Regarding more explanations for parameters setting, please refer to [CTP/conf/sql.conf](conf/sql.conf) for SQL, [CTP/conf/medium.conf](conf/medium.conf) for MEDIUM

  - Run Tests
	* For **SQL** test:
	    ```
	    $ bin/ctp.sh sql -c ./conf/sql.conf
	    ```
	    
	* For **MEDIUM** test:
	    ```
	    $ bin/ctp.sh medium -c ./conf/medium.conf
	    ```

	* For **SQL_BY_CCI** test:
	    ```
	    $ bin/ctp.sh sql_by_cci -c ./conf/sql.conf
	    ``` 

	* Use interactive mode to debug your **SQL/MEDIUM** case (this feature does not support SQL_BY_CCI)          
	    ```
	    $ bin/ctp.sh sql --interactive
	    ```

    
  - Examine the results

	* When the test is completed, CTP will print the summary result message, please see the example of SQL result for reference
	    ```
	    -----------------------
	    Fail:0
	    Success:1
	    Total:1
	    Elapse Time:193
	    Test Result Directory:/home/user/CTP/sql/result/y2016/m3/schedule_linux_sql_64bit_24202122_10.0.0_1376
	    Test Log:/home/user/CTP/sql/log/sql_10.0.0.1376_1458818452.log
	    -----------------------
		
	    -----------------------
	    Testing End!
	    -----------------------
	    ```
	* Alternatively, you can use the webconsole of CTP to check results(the current webconsole feature only support SQL and MEDIUM)
	    ```
	    $ bin/ctp.sh webconsole start
	    Config: /home/user/CTP/conf/webconsole.conf
	    Web Root: /home/user/CTP/sql/webconsole
	    Begin to start ...
	         
	    Done
	    URL:  http://127.0.0.1:8888
	    ```
	* Please open the ``URL`` with your browser to see the details
  
- **SHELL**
 - Prepare
	* For local test
		* Make sure to set JAVA_HOME and the required environment variables correctly
		* Since RQG is one extension of SHELL, so if you want to execute RQG test, you need checkout ``random_query_generator`` tool firstly, and export RQG_HOME environment 
		  variable to the path of ``random_query_generator`` tool 
		* Check out the SHELL test cases (e.g., check out or create the test cases at the ${HOME}/cubrid-testcases/shell directory)
		* Check out CTP and configure CTP/conf/shell.conf for testing
		```
		# Configure parameters for cubrid.conf following the format outlined below, e.g., default.cubrid.cubrid_port_id=1523
		# The property values configured will be updated into CUBRID conf for testing
		default.cubrid.cubrid_port_id=1523 
		# Configure parameters for cubrid_broker.conf following the format outlined below
		default.broker1.BROKER_PORT=35000 
		default.broker2.BROKER_PORT=35500
		```
		```
		# Define the path of test cases used for testing
		scenario=${HOME}/cubrid-testcases/shell
		```

	* For multi-instance test
		* Prepare at least two accounts for the multiple test instances (e.g., one account named as ``controller``, another account named as ``shell_instance1``)
		* On ``controller`` account
			* Make sure to set JAVA_HOME and the required environment variables correctly
			* Check out CTP and configure CTP/conf/shell.conf for testing
			```
			# Test instance information. For HA SHELL, relatedhosts must be configured for slave
			# The property values configured will be updated into CUBRID conf for testing
			env.instance1.ssh.host=192.168.1.10
			# env.instance1.ssh.relatedhosts=192.168.1.11
			env.instance1.ssh.port=22
			env.instance1.ssh.user=shell_instance1
			env.instance1.ssh.pwd=123456
			env.instance1.cubrid.cubrid_port_id=11523
			env.instance1.broker1.BROKER_PORT=35000
			env.instance1.broker2.BROKER_PORT=35500
			```
			``` 	   
			# Define the path of test cases used for testing, it should be checked out on test instance node. For SHELL HEAVY, SHELL LONG, RQG and HA SHELL, scenario should be configured accordingly.
			scenario=${HOME}/cubrid-testcases/shell
			```
			```
			# Define the URL of test build which will be used to test.
			# If this parameter is not set or commented out, CTP will execute testing without build installation.
			cubrid_download_url=http://127.0.0.1/download/CUBRID-10.1.0.6929-b049ba5-Linux.x86_64.sh
			```
			Regarding more explanations for parameters setting, please refer to [CTP/conf/shell.conf](conf/shell.conf)

		* On ``shell_instance1`` account
			* Check out CTP
			* Make sure to set JAVA_HOME and the required environment variables correctly
			```
			CTP_HOME (e.g., export CTP_HOME=$HOME/CTP) 
			```
			* Check out the SHELL test cases (e.g., check out or create the test cases at the ${HOME}/cubrid-testcases/shell directory)				

  - Run Tests 
	* For **SHELL** test:
	
	  ```
	  $ bin/ctp.sh shell -c ./conf/shell.conf
	  ```   
    
  - Examine the results
	* When the test is completed, you can find the results and logs from ``CTP/result/shell/current_runtime_logs``
	* ``dispatch_tc_ALL.txt`` shows the total case list, and ``dispatch_tc_FIN_${Node_Name}.txt`` shows the case list which is executed on this instance
	* ``main_snapshot.properties`` contains all the values of parameters configured during testing
	* ``test_${Node_Name}.log`` shows the logs of testing based on this instance
	
- **ISOLATION**
 - Prepare
	* For local test
		* Make sure to set JAVA_HOME and the required environment variables correctly
		* Check out ISOLATION test cases (e.g., check out or create the test cases at the ${HOME}/cubrid-testcases/isolation directory)
		* Check out CTP and configure CTP/conf/isolation.conf for testing
		```
		# Configure parameters for cubrid.conf following the format outlined below, e.g., default.cubrid.cubrid_port_id=1523
		# The property values configured will be updated into CUBRID conf for testing
		default.cubrid.cubrid_port_id=1523 
		# Configure parameters for cubrid_broker.conf following the format outlined below
		default.broker1.BROKER_PORT=35000 
		default.broker2.BROKER_PORT=35500
		```
		```
		# Define the path of test cases used for testing
		scenario=${HOME}/cubrid-testcases/isolation
		```

	* For multi-instance test
		* Prepare at least two accounts for the multiple test instances (e.g., one account named as ``controller``, another account named as ``isolation_instance1``)
		* On ``controller`` account
			* Make sure to set JAVA_HOME and the required environment variables correctly
			* Check out CTP and configure CTP/conf/isolation.conf for testing
			```
			# The property values configured will be updated into CUBRID conf for testing
			env.instance1.ssh.host=192.168.1.10
			env.instance1.ssh.port=22
			env.instance1.ssh.user=isolation_instance1
			env.instance1.ssh.pwd=123456
			env.instance1.cubrid.cubrid_port_id=11523
			env.instance1.broker1.BROKER_PORT=35000
			env.instance1.broker2.BROKER_PORT=35500
			```
			``` 	   
			# Define the path of test cases used for testing, it should be checked out on test instance node
			scenario=${HOME}/cubrid-testcases/isolation
			```
			```
			# Define the URL of test build which will be used to test.
			# If this parameter is not set or commented out, CTP will execute testing without build installation.
			cubrid_download_url=http://127.0.0.1/download/CUBRID-10.1.0.6929-b049ba5-Linux.x86_64.sh
			```
			Regarding more explanations for parameters setting, please refer to [CTP/conf/isolation.conf](conf/isolation.conf)

		* On ``isolation_instance1`` account
			* Check out CTP
			* Make sure to set JAVA_HOME and the required environment variables correctly
			```
			CTP_HOME (e.g., export CTP_HOME=$HOME/CTP) 
			```
			* Check out the ISOLATION test cases (e.g., check out or create the test cases at the ${HOME}/cubrid-testcases/isolation directory)				

 - Run Tests 
	* For **ISOLATION** test:

      ```
	  $ bin/ctp.sh isolation -c ./conf/isolation.conf
      ```   
        
 - Examine the results
	* When the test is completed, you can find the results and logs from ``CTP/result/isolation/current_runtime_logs``
	* ``dispatch_tc_ALL.txt`` shows the total case list, and ``dispatch_tc_FIN_${Node_Name}.txt`` shows the case list which is executed on this instance
	* ``main_snapshot.properties`` contains all the values of parameters configured during testing
	* ``test_${Node_Name}.log`` shows the logs of testing based on this instance

- **HA REPLICATION**
  - Prepare
	* Prepare at least three accounts for the multiple test instances (e.g., one account named as ``controller`` will be used as controller, another accounts named as ``ha_repl_instance1`` will be used as HA pairs accounts)
	* On ``controller`` account
		* Make sure to set JAVA_HOME and the required environment variables correctly
		* Check out the SQL test cases (e.g., check out or create the test cases at the ${HOME}/cubrid-testcases/sql directory)
		* Check out CTP and configure CTP/conf/ha_repl.conf for testing
		```
		# Test instance information:
		# The property values configured will be updated into CUBRID conf for testing
		env.instance1.master.ssh.host=192.168.1.10
		env.instance1.master.ssh.user=ha_repl_instance1
		env.instance1.slave.ssh.host=192.168.1.11
		env.instance1.slave.ssh.user=ha_repl_instance1
		env.instance1.cubrid.cubrid_port_id=1137
		env.instance1.ha.ha_port_id=59001
		env.instance1.broker.BROKER_PORT=35000
		```
		``` 	   
		# Define the path of test cases used for testing, it should be checked out on test instance node
		scenario=${HOME}/cubrid-testcases/sql
		```
		```
		# Define the URL of test build which will be used to test.
		# If this parameter is not set or commented out, CTP will execute testing without build installation.
		cubrid_download_url=http://127.0.0.1/download/CUBRID-10.1.0.6929-b049ba5-Linux.x86_64.sh
		```
		Regarding more explanations for parameters setting, please refer to [CTP/conf/ha_repl.conf](conf/ha_repl.conf)

	* On ``ha_repl_instance1`` account
		* Check out CTP
		* Make sure to set JAVA_HOME and the required environment variables correctly
		```
		CTP_HOME (e.g., export CTP_HOME=$HOME/CTP) 
		```
			  
 - Run Tests 
	* For **HA REPLICATION** test:

      ```
	  $ bin/ctp.sh ha_repl -c ./conf/ha_repl.conf
      ```   
    
 - Examine the results
	* When the test is completed, you can find the results and logs from ``CTP/result/ha_repl/current_runtime_logs``
	* ``dispatch_tc_ALL.txt`` shows the total case list, and ``dispatch_tc_FIN_${Node_Name}.txt`` shows the cases which are executed on this instance
	* ``main_snapshot.properties`` contains all the values of parameters configured during testing
	* ``test_${Node_Name}.log`` shows the logs of testing based on this instance

- **JDBC**
  - Prepare
	* Make sure to set JAVA_HOME and the required environment variables correctly
	* **Example** ``jdbc.conf`` for test scenario and ports of CUBRID:
      ```
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
      ```

	Regarding more explanations for parameters setting, please refer to [CTP/conf/jdbc.conf](conf/jdbc.conf)	
	  
 - Run Tests 
	* For **JDBC** test:

      ```
	  $ bin/ctp.sh jdbc -c ./conf/jdbc.conf
      ```   
    
 - Examine the results
	* When the test is completed, you can find the results and logs from ``CTP/result/jdbc/current_runtime_logs``
	* ``run_case_details.log`` shows all the details of case running
	

## How To Build CTP
It's not required that you execute the build for CTP, unless you make some changes within source codes. In order to build CTP from source code, you must need install Apache Ant on your environment, and then change to the CTP directory, executing ant command as the below:

```
    $ ant clean dist
```
    
## How To Write Testcase
 - **SQL/MEDIUM/SQL_BY_CCI**
    
   When you want to write your own test case, please follow the following rules
   * Test cases: The file extension is ``.sql`` and it is located in ``cases`` subdirectory
   * Expected results: The file extension is ``.answer`` and it is located in ``answers`` subdirectory
     - An example for a case is:
	  ```
	   _08_primary_foreign_key
	                       /cases
	                             /int_primary_key_test.sql
	                       /answers
	                             /int_primary_key_test.answer
	  ```
   * If you want to examine the query plan of a case, you have two options to achieve:
     - Create an empty ``case_name.queryPlan`` file in the test case directory, its query plan will be printed with the results of queries 
	  ```
	  _08_primary_foreign_key
	                       /cases
	                             /int_primary_key_test.sql
	                             /int_primary_key_test.queryPlan
	                       /answers
	                             /int_primary_key_test.answer
	  ```
	  - Add ``--@queryplan`` line before the query statement you want to check, and query plan of the hinted SQL will be dumped
	  
	  ```
	  --@queryplan
	  SELECT /*+ recompile */ median(a) FROM x;
	  ```

   * You can add "autocommit off;", "autocommit on;" to change autocommit mode 

- **SHELL**
   * Test cases: The file extension is ``.sh``, and it is located in ``cases`` subdirectory, naming rule: ``/path/to/test_name/cases/test_name.sh``
   * Example for reference
    
     ```
     #!/bin/sh
     # to initialize the environment variables which are required by case
     . $init_path/init.sh
     # to support RQG regression, the environment variables and functions in rqg_init.sh should be initialized, so just need uncomment the below statement($init_path/rqg_init.sh), 
     # if you want to understand the functions which will be used in RQG case, please refer to $init_path/rqg_init.sh  
     # . $init_path/rqg_init.sh
     init test
     dbname=tmpdb

     cubrid_createdb $dbname
	
     dosomethings
     ...
	
     if [condition]
     then
	        # Print testing result according to the condition, PASS means ok, otherwise nok
	        write_ok
     else
	        write_nok
     fi
	
     cubrid server stop $dbname
     cubrid broker stop
	
     cubrid deletedb $dbname
     # clean environment
     finish
	  ```

- **ISOLATION**
   * Test cases: The file extension is ``.ctl``
   * Example for reference
   
     ```
      /*
     Test Case: Changing Owner
     Priority: 1
     Reference case:
     Author: xxx

     Test Plan: 
     Test update locks (X_LOCK on instance) and SELECT not need locks, they are not blocked each other

     Test Scenario:
     C1 granting authorization, C2 verify authorization, 
     C1 verify authorization, 
     C1 commit, C2 commit, 
     Metrics: data size = small, where clause = simple (multiple columns)
	
     Test Point:
     1) C1 and C2 will not be waiting 
     2) All the data affected from C1 and C2 should be deleted

     NUM_CLIENTS = 2
     C1: granting authorization - verify authorization;  
     C2: verify authorization;  
     */

     MC: setup NUM_CLIENTS = 2;

     C1: login as 'dba';
     C1: set transaction lock timeout INFINITE;
     C1: set transaction isolation level read committed;

     C2: set transaction lock timeout INFINITE;
     C2: set transaction isolation level read committed;

     /* preparation */
     C1: DROP TABLE IF EXISTS t1;
     C1: CREATE USER company GROUPS public;
     C1: CREATE USER engineering GROUPS company;
     C1: CREATE USER jones GROUPS engineering; 
     C1: CREATE USER brown;
     C1: CREATE USER design MEMBERS brown;
     C1: COMMIT;
     MC: wait until C1 ready;

     C1: CREATE TABLE t1 (id INT primary key);
     C1: GRANT SELECT, UPDATE ON t1 TO company;
     C1: GRANT ALTER, INDEX, DELETE ON t1 TO design;
     C1: insert into t1 values (1),(2),(3),(4),(5),(6),(7);
     C1: COMMIT;
     MC: wait until C1 ready;

     C1: ALTER TABLE t1 OWNER TO public;
     C1: COMMIT;
     MC: wait until C1 ready;
     C2: login as 'company';
     C2: TRUNCATE table t1;
     C2: COMMIT;
     MC: wait until C2 ready;
     C2: select * from t1 order by 1;
     C2: COMMIT;
     MC: wait until C2 ready;

     C1: login as 'dba';
     C1: DROP table t1;
     C1: DROP USER jones;
     C1: DROP USER brown;
     C1: DROP USER design;
     C1: DROP USER engineering;
     C1: DROP USER company;
     C1: COMMIT;
     MC: wait until C1 ready;

     C1: quit;
     C2: quit;
	  ```
   Note:
     - The purpose/author/reference/priority of case will help reader understand your points
      - C1~n means transaction session name
      - MC means main controller, it controls and coordinates the order of all transaction clients


- **HA REPLICATION**
   * Test cases: Since ``HA REPLICATION`` is using ``SQL`` scenarios to test on HA mode to verify the data replication between an active server and a standby server, so the cases are the same as ``SQL``
   * CTP will transform case file to be ``case_name.test`` format with some checking statement flags around the SQL statements. And If the SQL does not contain primary key, CTP will add primary key on one column
   * Example for reference
    
     ```
       --test: # execute test flag for statement
       create table t1 (id int primary key, name varchar)

       --check: # check data between master and slave
       @HC_CHECK_FOR_EACH_STATEMENT # check if schema is consistent between master and slave 
       --test:
       
       insert into t1 values (1, 'qa'), (2, 'cubrid');
       --check:
       $HC_CHECK_FOR_DML
       
       --test:
       drop table t1;
       --check:
       @HC_CHECK_FOR_EACH_STATEMENT
       --test
     ``` 
     
- **JDBC**
   * Test cases: Since ``JDBC`` unit test cases are designed based on junit framework, so all the cases need follow junit syntax and rule 
   * The current CTP identifies the case according to the @Test annotation and keywords 'test' on test method name, and ignore the case according to the @Ignore annotation on test method
   * Example for reference
    
     ```
      @Test
      public void testInvalidUrlException() throws SQLException {
                try {
                        Class.forName(DRIVER).newInstance();
                        String strArray[] = URL.split("\\:");
                        String strTemp = "";
                        for (int i = 0; i < strArray.length; i++) {

                                strTemp += strArray[i];
                                if (i == 4) {
                                        break;
                                } else {
                                        strTemp += ":";
                                }
                        }
                        strTemp += ":::?";
                        Connection conn = DriverManager.getConnection(strTemp, USER, PASS);
                        conn.close();
                        Assert.assertTrue(false);
                } catch (Exception e) {
                        Assert.assertTrue(true);
                } finally {
                }
       }
     ```   

## License
CTP is published under the BSD 3-Clause license. See [LICENSE.md](LICENSE.md) for more details
Third-party libraries used by CTP are under their own licenses. See [LICENSE-3RD-PARTY.md](LICENSE-3RD-PARTY.md) for details on the license they use
