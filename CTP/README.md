# CTP - CUBRID Test Program

## Introduction
CTP is a testing tool for an open source project CUBRID. It is written in Java and easy to execute tests with a simple configuration. 


## Requirements
* It supports Linux and Windows (Cygwin is required).
* Install Java 6 or higher version, and you also need to set ``JAVA_HOME`` environment variable to point to the installation directory.
* CUBRID and CUBRID_DATABASES environment variables should be configured before executing testing, please refer to http://www.cubrid.org/ for configurations.
* CUBRID QA executes testing for SQL and Medium on linux is based on ha mode, so you must make sure ports in cubrid.conf, cubrid_broker.conf and cubrid_ha.conf will not conflict with another instances exist. Otherwise, start server or broker will be fail.

## Quick Start
This ``Quick Start`` is only for user for reference about how to use ``CTP`` to start ``SQL`` test quickly. But CTP supports more categories testing than this section mentioned, such as ``Shell``, ``CCI``, ``HA Shell``, ``Isolation``, ``HA Replication`` and so on. Regarding more information please refer to the related sections.

* Install a CUBRID build and make sure ``CUBRID`` environment variable is set correctly.
* Execute a sample test as follows:
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
	
* Please open the URL with your browser.
  
## How To Execute
- **SQL** 
 - Prepare
	* Prepare environments (e.g.: one account ``sql1`` as controller and test instance).
	* Checkout CTP and scenario from our GitHub projects or make your own test cases.
	* Install CUBRID and make sure your environment variable of ``CUBRID`` is correctly set.
	* Example ``sql.conf`` for scenario, data file and some important parameters changes
	  ```
		# SQL section - a section for CTP tool configuration when executing sql/medium testing
		[sql]

		# The location of your testing scenario
		scenario=${HOME}/cubrid-testcases/sql
		
		# SQL cubrid.conf section - a section for cubrid.conf configuration
		[sql/cubrid.conf]

		# To decide if the Java store procedure will be used when testing
		java_stored_procedure=yes

		# Allow scenario to change database system parameter
		test_mode=yes
		
		# In order to simulate the scenario customer use
		ha_mode=yes
		
	  ```
	  NOTE:
		`1.` Set ``data_file`` under ``[sql]`` section to the directory path of the initial data files for ``Medium`` test.
		`2.` ``java_stored_procedure=yes``, ``test_mode=yes`` and ``ha_mode=yes`` are used for the existing ``SQL`` and ``Medium`` scenario test, If you want to execute the scenario your own, they are not required. 

	  For more parameters setting of ``sql.conf`` and parameters explanation, please refer to [CTP/conf/sql.conf](conf/sql.conf)

  - Run Tests
	* For **SQL/Medium** test:
	    ```
	    $ bin/ctp.sh sql -c ./conf/sql.conf
	    ```

	    ```
	    $ bin/ctp.sh medium -c ./conf/medium.conf
	    ```
	* Use interactive mode to debug your **SQL/Medium** case.

	    ```
            $ bin/ctp.sh sql --interactive
	    ```

    
  - Examine the results

	* Once it completes, you will see a result message.
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
	* You can find the details of the test result from ``Test Result Directory``.
	* You can also use your web browser to examine the result with webconsole service of CTP.
	* ``bin/ctp.sh webconsole start`` will show you the URL of the result as follows:
	  ```
	  Config: /home/user/CTP/conf/webconsole.conf
	  Web Root: /home/user/CTP/sql/webconsole
	  Begin to start ...
	         
	  Done
	  URL:  http://127.0.0.1:8888
	  ```
	* Please open the ``URL`` with your browser.
  
- **SHELL**
  - Prepare
	* Prepare environments (e.g.: one account ``controller`` as controller, one account ``shell_instance1`` as test instance).
	* Checkout CTP in both accounts above.
	* Configure environment variables for CTP ``controller`` and test instance ``shell_instance1``.
	  
	  ```
	   // on controller node
	   JAVA_HOME (e.g.: export JAVA_HOME=$HOME/opt/jdk1.6.0_07)

	   // on test instance node
	   CTP_HOME (e.g.: export CTP_HOME=$HOME/CTP) 
	   JAVA_HOME (e.g.: export JAVA_HOME=$HOME/opt/jdk1.6.0_07)
          ```	 
	* Example ``shell.conf`` for test instance, scenario and build:
	
	  ```
	        # Test instances should be configured in CTP/conf/shell.conf as below:
		env.instance1.ssh.host=192.168.1.10
		env.instance1.ssh.port=22
		env.instance1.ssh.user=shell_instance1
		env.instance1.ssh.pwd=123456
		env.instance1.cubrid.cubrid_port_id=11523
		env.instance1.broker1.BROKER_PORT=35000
		env.instance1.broker2.BROKER_PORT=35500
		
		env.instance2.ssh.host=192.168.1.11
		env.instance2.ssh.port=22
		env.instance2.ssh.user=shell_instance2
		env.instance2.ssh.pwd=123456
		env.instance2.cubrid.cubrid_port_id=11523
		env.instance2.broker1.BROKER_PORT=35000
		env.instance2.broker2.BROKER_PORT=35500
		
	  ```
	  ```
                # Define the path of test cases used for testing, it should be checked out on controller node.
                # CTP gets all the cases from this path and distributes to each test node to execute test.
                main.testcase.root=/home/qa/shell_instance1/shell
          ```

          ```
                # Define the URL of build which will be used to test.
                # If set 'main.deploy.rebuild_yn' as 'false', this parameter will be ignored.
                main.testbuild.url=http://127.0.0.1/REPO_ROOT/store_01/10.1.0.6929-b049ba5/drop/CUBRID-10.1.0.6929-b049ba5-Linux.x86_64.sh
          ```	 
 
	  For more parameters setting of ``shell.conf`` and parameters explanation, please refer to [CTP/conf/shell.conf](conf/shell.conf)

 - Run Tests 
	* For **Shell** test:
	
	  ```
	  $ bin/ctp.sh shell -c ./conf/shell.conf
	  ```   
    
 - Examine the results
	* When it is completed, you can find the results and logs from ``CTP/result/shell/current_runtime_logs``
	* ``dispatch_tc_ALL.txt`` will show the total case list, and ``dispatch_tc_FIN_${Node_Name}.txt`` will show the case list which is executed on this instance.
	* ``main_snapshot.properties`` will save all values of parameters configured during testing.
	* ``test_${Node_Name}.log`` will show the logs of testing based on this instance.
	
- **Isolation**
  - Prepare
        * Prepare environments (e.g.: one account ``controller`` as controller, one account ``isolation_instance1`` as test instance).
        * Checkout CTP in both accounts above.
        * Configure environment variables for CTP ``controller`` and test instance ``isolation_instance1``.
	```
          // on controller node
          JAVA_HOME (e.g.: export JAVA_HOME=$HOME/opt/jdk1.6.0_07)

          // on test instance node
          CTP_HOME (e.g.: export CTP_HOME=$HOME/CTP)
          JAVA_HOME (e.g.: export JAVA_HOME=$HOME/opt/jdk1.6.0_07)
        ```
	* Example ``isolation.conf`` for test instance, scenario and build:
	```
		# Test instances should be configured in CTP/conf/isolation.conf as below:
		env.instance1.ssh.host=192.168.1.10
		env.instance1.ssh.port=22
		env.instance1.ssh.user=isolation_instance1
		env.instance1.ssh.pwd=12345
		env.instance2.broker1.BROKER_PORT=30093
                env.instance2.broker2.BROKER_PORT=30094

		env.instance2.ssh.host=192.168.1.11
		env.instance2.ssh.port=22
		env.instance2.ssh.user=isolation_instance2
		env.instance2.ssh.pwd=12345
		env.instance2.broker1.BROKER_PORT=30093
		env.instance2.broker2.BROKER_PORT=30094
	```
	```
                # Define the path of test cases used for testing, it should be checked out on controller node.
                # CTP gets all the cases from this path and distributes to each test node to execute test.
                main.testcase.root=/home/qa/isolation_instance1/isolation
        ```

        ```
                # Define the URL of build which will be used to test.
                # If set 'main.deploy.rebuild_yn' as 'false', this parameter will be ignored.
                main.testbuild.url=http://127.0.0.1/REPO_ROOT/store_01/10.1.0.6929-b049ba5/drop/CUBRID-10.1.0.6929-b049ba5-Linux.x86_64.sh
        ``` 
	
	For more parameters setting of ``isolation.conf`` and parameters explanation, please refer to [CTP/conf/isolation.conf](conf/isolation.conf)

 - Run Tests 
	* For **Isolation** test:
	
	  ```
	  $ bin/ctp.sh isolation -c ./conf/isolation.conf
	  ```   
    
 - Examine the results
	* When it is completed, you can find the results and logs from ``CTP/result/isolation/current_runtime_logs``
	* ``dispatch_tc_ALL.txt`` will show the total case list, and ``dispatch_tc_FIN_${Node_Name}.txt`` will show the case list which is executed on this instance.
	* ``main_snapshot.properties`` will save all values of parameters configured during testing.
	* ``test_${Node_Name}.log`` will show the logs of testing based on this instance.

- **HA Replication**
  - Prepare
	* Prepare environments (e.g.: one account ``controller`` as controller, one account ``ha_repl_instance1`` as a master test instance, one same account name ``ha_repl_instance1`` on other machines as a slave test instance).
        * Checkout CTP in three accounts above.
        * Configure environment variables for CTP ``controller`` and test instances ``ha_repl_instance1`` (both master and slave) .

          ```
           // on controller node
           JAVA_HOME (e.g.: export JAVA_HOME=$HOME/opt/jdk1.6.0_07)

           // on test instance nodes (master and slave)
           CTP_HOME (e.g.: export CTP_HOME=$HOME/CTP)
           JAVA_HOME (e.g.: export JAVA_HOME=$HOME/opt/jdk1.6.0_07)
          ```	
	* Example ``ha_repl.conf`` for test instances(master and slave), scenario and build::
	
		```
		# Test instances should be configured in CTP/conf/ha_repl.conf as below:
		env.instance1.master.ssh.host=192.168.1.10
		env.instance1.master.ssh.user=ha_repl_instance1
		env.instance1.slave1.ssh.host=192.168.1.11
		env.instance1.slave1.ssh.user=ha_repl_instance1
		env.instance1.cubrid.cubrid_port_id=1137
		env.instance1.ha.ha_port_id=59001
		env.instance1.broker.BROKER_PORT=35000
	
		env.instance2.master.ssh.host=192.168.1.10
		env.instance2.master.ssh.user=ha_repl_instance2
		env.instance2.slave1.ssh.host=192.168.1.11
		env.instance2.slave1.ssh.user=ha_repl_instance2
		env.instance2.cubrid.cubrid_port_id=1138
		env.instance2.ha.ha_port_id=59002
		env.instance2.broker.BROKER_PORT=35001
		```
		
		```
		# Define the path of test cases used for testing, it should be checked out on controller node.
		# CTP gets all the cases from this path and distributes to each test node to execute test.
		main.testcase.root=/home/controller/testcases/sql
		```
		NOTE: the scenario must be checkouted on ``controller`` account, not on the test instance.

		```
		# Define the URL of build which will be used to test. 
		# If set 'main.deploy.rebuild_yn' as 'false', this parameter will be ignored. 
		main.testbuild.url=http://127.0.0.1/REPO_ROOT/store_01/10.1.0.6929-b049ba5/drop/CUBRID-10.1.0.6929-b049ba5-Linux.x86_64.sh	
		```
		
		For more parameters setting of ``ha_repl.conf`` and parameters explanation, please refer to [CTP/conf/ha_repl.conf](conf/ha_repl.conf)	
	  
 - Run Tests 
	* For **HA Replication** test:
	
	  ```
	  $ bin/ctp.sh ha_repl -c ./conf/ha_repl.conf
	  ```   
    
 - Examine the results
	* When the test is completed, you can find the results and logs from ``CTP/result/ha_repl/current_runtime_logs``
	* ``dispatch_tc_ALL.txt`` will show the total case list, and ``dispatch_tc_FIN_${Node_Name}.txt`` will show the cases which are executed on this instance.
	* ``main_snapshot.properties`` will save all values of parameters configured during testing.
	* ``test_${Node_Name}.log`` will show the logs of testing based on this instance.


## How To Build CTP
You are not required to build CTP from source codes, unless you make some changes. To make your own build, please install ant and make a build as follows: 
  ```
    $ ant clean dist
  ```
You can find generated jar files ``common/lib/cubridqa-common.jar``, ``sql/lib/cubridqa-cqt.jar``, ``common/sched/lib/cubridqa-scheduler.jar``, ``shell/init_path/commonforjdbc.jar``, ``shell/lib/cubridqa-shell.jar``, ``isolation/lib/cubridqa-isolation.jar`` and ``ha_repl/lib/cubridqa-ha_repl.jar``.

## How To Write Testcase
 - **SQL**
    
   When you want to write your own test case, please follow the following rules.
   * Test cases: The file extension is ``.sql`` and it is located in ``cases`` subdirectory. 
   * Expected results: The file extension is ``.answer`` and it is located in ``answers`` subdirectory. 
     - An example for a case is:
	  ```
	   _08_primary_foreign_key
	                       /cases
	                             /int_primary_key_test.sql
	                       /answers
	                             /int_primary_key_test.answer
	  ```
   * When you also want to examine query plan of a case, you have two options:
     - Create an empty ``case_name.queryPlan`` file in the test case directory, its query plan will be printed with the results of queries. 
	  ```
	  _08_primary_foreign_key
	                       /cases
	                             /int_primary_key_test.sql
	                             /int_primary_key_test.queryPlan
	                       /answers
	                             /int_primary_key_test.answer
	  ```
	  - Add ``--@queryplan`` line before the query statement you want to check, and query plan of the hinted SQL will be dumped.
	  ```
	  --@queryplan
	  SELECT /*+ recompile */ median(a) FROM x;
	  ```

   * You can add "autocommit off;", "autocommit on;" to change autocommit mode. 

- **SHELL**
   * Test cases: the file extension is ``.sh``, and it is located in ``cases`` subdirectory, naming rule: ``/path/to/test_name/cases/test_name.sh``
   * Sample for reference
     ```
     #!/bin/sh
     # to initialize the environment variables which are required by case
	. $init_path/init.sh
	init test
	dbname=tmpdb

	cubrid_createdb $dbname
	
	dosomethings
	...
	
	if [condition]
	then
	        #print testing result according to the condition, PASS means ok, otherwise nok
	        write_ok
	else
	        write_nok
	fi
	
	cubrid server stop $dbname
	cubrid broker stop
	
	cubrid deletedb $dbname
	#clean environment
	finish
	```

- **Isolation**
   * Test cases: the file extension is ``.ctl``
   * Sample for reference
     ```
    /*
	Test Case: Changing Owner
	Priority: 1
	Reference case:
	Author: xxx

	Test Plan: 
	Test update locks (X_LOCK on instance) and SELECT not need locks, they are not blocked each other.

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
        - The purpose/author/reference/priority of case to help reader understand your points
        - C1~n means transaction session
        - MC means main controller, it will control and coordinate the order of all transaction clients


- **HA Replication**
   * Test cases: Since ``HA Replication`` is using ``SQL`` scenarios to test on HA mode to verify the data synchronization between an active server and a standby server, so the cases are same as ``SQL``
   * CTP will transform case file to be ``case_name.test`` file with some checking statement flags around the SQL statement. And If the SQL does not contain primary key, CTP will add primary key on column
   * Sample for reference
     ```
       --test: #execute test flag for statement
       create table t1 (id int primary key, name varchar)

       --check: #check data between master and slave
       @HC_CHECK_FOR_EACH_STATEMENT #check if schema is consistent between master and slave 
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


## License
CTP is published under the BSD 3-Clause license. See [LICENSE.md](LICENSE.md) for more details.
Third-party libraries used by CTP are under their own licenses. See [LICENSE-3RD-PARTY.md](LICENSE-3RD-PARTY.md) for details on the license they use.

