# CTP - CUBRID Test Program

## Introduction
CTP is a testing tool for an open source project CUBRID. It is written in Java and easy to execute tests with a simple configuration. 


## Requirements
* It supports Linux and Windows (Cygwin is required).
* Install Java 6 or higher version, and you also need to set ``JAVA_HOME`` environment variable to point to the installation directory.
* CUBRID and CUBRID_DATABASES environment variables should be configured before executing testing, please refer to http://www.cubrid.org/ for configurations.
* CUBRID QA executes testing for SQL and Medium on linux is based on ha mode, so you must make sure ports in cubrid.conf, cubrid_broker.conf and cubrid_ha.conf will
  not conflict with another instance exists. Otherwise, start server or broker will be fail.

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
	* Checkout test cases from our GitHub projects or make your own test cases.
	* Install CUBRID and make sure your environment variable of ``CUBRID`` is correctly set.
	* Check configuration files, for **SQL/Medium** test, you can modify parameters of ``conf/sql.conf`` or ``conf/medium.conf``. 
	* Set ``scenario`` of ``[sql]`` section to the test cases directory.
	* Set ``data_file`` of ``[sql]`` section to the directory path of initial data files for **Medium** test.
	* ``test_mode=yes`` and ``java_stored_procedure=yes`` parameters must be set for **SQL/Medium** test.
	* Please see ``conf/sql.conf`` or  ``conf/medium.conf`` for details about other parameters.

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
	* Use one server as controller to checkout CTP, and test node may be one or more, they will be controlled by controller, and CTP must be deployed on each node.
	* Controller Node configuration:
	
	  ```
	    Test nodes are configured in CTP/conf/shell.conf as below:
		env.instance1.ssh.host=192.168.1.10
		env.instance1.ssh.port=22
		env.instance1.ssh.user=<user>
		env.instance1.ssh.pwd=<pwd>
		env.instance1.cubrid.cubrid_port_id=11523
		env.instance1.broker1.BROKER_PORT=35000
		env.instance1.broker2.BROKER_PORT=35500
		
		env.instance2.ssh.host=192.168.1.11
		env.instance2.ssh.port=22
		env.instance2.ssh.user=<user>
		env.instance2.ssh.pwd=<pwd>
		env.instance2.cubrid.cubrid_port_id=11523
		env.instance2.broker1.BROKER_PORT=35000
		env.instance2.broker2.BROKER_PORT=35500
		
	  ```
	  
	  Regarding more parameters for shell testing, please refer to [CTP/conf/shell.conf](conf/shell.conf)
	* Test Node:
	  
	  ```
	     export JAVA_HOME=$java_installation_directory 
	     export CTP_HOME=$CTP_installation_directory
	  ```

 - Run Tests 
	* For **Shell** test:
	
	  ```
	  $ bin/ctp.sh shell -c ./conf/shell.conf
	  ```   
    
 - Examine the results
	* Once it completes, you can find the results and logs from ``CTP/result/shell/current_runtime_logs``
	* ``dispatch_tc_ALL.txt`` will show the total case list, and ``dispatch_tc_FIN_${Node_Name}.txt`` will show the case list which is executed on this server node.
	* ``main_snapshot.properties`` will save the configurations for your current testing.
	* ``test_${Node_Name}.log`` will show the logs of testing based on this server node.
	
- **Isolation**
  - Prepare
	* Use one server as controller to checkout CTP, and test node may be one or more, they will be controlled by controller, and CTP must be deployed on each node.
	* Controller Node configuration is basically same as ``Shell``.
	  Regarding more parameters for ``isolation`` testing, please refer to [CTP/conf/isolation.conf](conf/isolation.conf)
	* Environment variables set on test Node:
	  
	  ```
	     JAVA_HOME=$java_nstallation_directory 
	     CTP_HOME=$HOME/CTP
	  ```
	  
 - Run Tests 
	* For **Isolation** test:
	
	  ```
	  $ bin/ctp.sh isolation -c ./conf/isolation.conf
	  ```   
    
 - Examine the results
	* Once it completes, you can find the results and logs from ``CTP/result/isolation/current_runtime_logs``
	* ``dispatch_tc_ALL.txt`` will show the total case list, and ``dispatch_tc_FIN_${Node_Name}.txt`` will show the case list which is executed on this server node.
	* ``main_snapshot.properties`` will save the configurations for your current testing.
	* ``test_${Node_Name}.log`` will show the logs of testing based on this server node.

- **HA Replication**
  - Prepare
	* Use one server as controller to checkout CTP, and test node may be one or more, they will be controlled by controller, and CTP must be deployed on each node.
	* Controller Node configuration is basically same as ``Shell``.
	  Regarding more parameters for ``HA Replication`` testing, please refer to [CTP/conf/ha_repl_template.conf](conf/ha_repl_template.conf)
	* Environment variables set on test Node:
	  
	  ```
	     JAVA_HOME=$java_nstallation_directory 
	     CTP_HOME=$HOME/CTP
	  ```
	  
 - Run Tests 
	* For **HA Replication** test:
	
	  ```
	  $ bin/ctp.sh ha_repl -c ./conf/ha_repl_template.conf
	  ```   
    
 - Examine the results
	* Once it completes, you can find the results and logs from ``CTP/result/ha_repl/current_runtime_logs``
	* ``dispatch_tc_ALL.txt`` will show the total case list, and ``dispatch_tc_FIN_${Node_Name}.txt`` will show the case list which is executed on this server node.
	* ``main_snapshot.properties`` will save the configurations for your current testing.
	* ``test_${Node_Name}.log`` will show the logs of testing based on this server node.


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
   * Test cases: Since ``HA Replication`` is using ``SQL`` scenarios to test on HA mode to verify the data synchronization between an active server and a standby server,
     so the cases are same as ``SQL``
   * CTP will transform case file to be ``case_name.test`` file with some checking statement flags around the SQL statement. And If the SQL does not contain primary key,
     CTP will add primary key on column
   * Sample for reference
     ```
       --test: #test flag
       create class DML_0001 \
       (       int_col int not null, \
       var_col varchar(20), \
       set_col set (int,varchar(20)));
       --check: #check data flag between master and slave
       @HC_CHECK_FOR_EACH_STATEMENT #check schema flag between master and slave 
       --test:
       
       create class DML_0002 \
       (       int_col int not null, \
       var_col varchar(20), \
       set_col set (int, varchar(20)), \
       ref_col DML_0001, \
       set_ref_col set of DML_0001);
       --check:
       @HC_CHECK_FOR_EACH_STATEMENT
       --test:
       
       create class DML_0003 under DML_0001 \
       (       int_col1 int not null PRIMARY KEY);
       --check:
       @HC_CHECK_FOR_EACH_STATEMENT
       --test:
       insert into DML_0001 values (1,'test1', {1,'test1'});
       --check:
       $HC_CHECK_FOR_DML
       
       --test:
       insert into DML_0001 values (2,'test1', {1,'test1'});
       --check:
       $HC_CHECK_FOR_DML
       
       --test:
       insert into DML_0001 values (3,'test2', {1,'test2'});
       --check:
       $HC_CHECK_FOR_DML
       
       --test:
       insert into DML_0001 values (4,'test1', {2,'test1'});
       --check:
       $HC_CHECK_FOR_DML
       
       --test:
       insert into DML_0001 values (5,'test2', {2,'test2'});
       --check:
       $HC_CHECK_FOR_DML
       
       --test:
       
       
       
       drop class DML_0001;
       --check:
       @HC_CHECK_FOR_EACH_STATEMENT
       "1006.test" 64L, 1143C 

     ```    


## License
CTP is published under the BSD 3-Clause license. See [LICENSE.md](LICENSE.md) for more details.
Third-party libraries used by CTP are under their own licenses. See [LICENSE-3RD-PARTY.md](LICENSE-3RD-PARTY.md) for details on the license they use.

