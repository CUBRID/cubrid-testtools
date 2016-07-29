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
This ``Quick Start`` is only for user for reference about how to use ``CTP`` to start ``SQL`` test quickly. But CTP supports more categories testing than this section mentioned, such as ``Shell``, ``CCI``, ``HA Shell`` and so on. Regarding more information please refer to the related sections.

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
	* Controller Node:
	
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
	  
	  Regarding more parameters for shell testing, please refer to [CTP/conf/shell.conf](CTP/conf/shell.conf)
	* Test Node:
	  
	  ```
	     JAVA_HOME=$java_nstallation_directory 
	     init_path=CTP/shell/init_path
	  ```

 - Run Tests 
	* For **Shell** test:
	
	  ```
	  $ bin/ctp.sh shell -c ./conf/shell.conf
	  ```   
    
 - Examine the results
	* Once it completes, you can find the results/logs from ``CTP/result/current_runtime_logs``
	* ``dispatch_tc_ALL.txt`` will show the total case list, and ``dispatch_tc_FIN_${Node_Name}.txt`` will show the case list which is executed on this server node.
	* ``main_snapshot.properties`` will save the configurations for your current testing.
	* ``test_${Node_Name}.log`` will show the logs of testing based on this server node.

## How To Build CTP
You are not required to build CTP from source codes, unless you make some changes. To make your own build, please install ant and make a build as follows: 
  ```
    $ ant clean dist
  ```
You can find generated jar files ``common/lib/cubridqa-common.jar``, ``sql/lib/cubridqa-cqt.jar``, ``common/sched/lib/cubridqa-scheduler.jar``, ``shell/init_path/commonforjdbc.jar`` and ``shell/lib/cubridqa-shell.jar``.

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

## License
CTP is published under the BSD 3-Clause license. See [LICENSE.md](LICENSE.md) for more details.
Third-party libraries used by CTP are under their own licenses. See [LICENSE-3RD-PARTY.md](LICENSE-3RD-PARTY.md) for details on the license they use.

