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
  (CTP supports more categories testing than readme mentioned, such as ``CCI``, ``Isolation``, ``HA Shell`` and so on. Regarding more information please refer to the related sections)


## How To Execute
#### Prepare
* Checkout test cases from our GitHub projects or make your own test cases.
* Install CUBRID and make sure your environment variable of ``CUBRID`` is correctly set.
* Check configuration files
  * For **SQL** test, you can modify parameters of ``conf/sql.conf``. 
    * Set ``scenario`` of ``[sql]`` section to the test cases directory.
    * ``test_mode=yes`` and ``java_stored_procedure=yes`` parameters must be set for **SQL** test.
    * Please see ``conf/sql.conf`` for details about other parameters.
  * For **Medium** test, you can tune parameters of ``conf/medium.conf``. 
    * Set ``scenario`` of ``[sql]`` section to the test cases directory.
    * Set ``data_file`` of ``[sql]`` section to the directory path of initial data files for **Medium** test.
    * ``test_mode=yes`` parameter is required.
    * Please see ``conf/medium.conf`` for details about other parameters.
  * For **Shell** test, you can prepare one configuration file for your testing which is named as shell_template_for_[category_name], and can tune parameters of your configuration file. 
    * Set ``port`` for broker, cubrid_port_id and ha, you can choose to configure default common port for all instance or configure port for each instance node.
    * Set instance node for testing environment, it should cover ``host``, ``sshd port``, ``username`` and ``password``, and start with ``env.``, end with ``host, port, user, pwd`` keywords.
    * Set ``main.testcase.root`` for case root directory.
    * Set ``main.testcase.branch_git`` for the branch you will used.
    * Configure ``init_path`` environment variable to ``CTP/shell/init_path`` for case required.

#### Run Tests
* For **SQL** test:
    ```
    $ bin/ctp.sh sql -c ./conf/sql.conf
    ```

* For **Medium** test:
    ```
    $ bin/ctp.sh medium -c ./conf/medium.conf
    ```
    
* For **Shell** test:
    ```
    $ bin/ctp.sh shell -c ./conf/shell_template_for_[category_name].conf
    ```
    
* For **Interactive** mode test:
    ```
    $ bin/ctp.sh sql --interactive
    ```
    
#### Examine the results
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
  
        
## How To Build CTP
You are not required to build CTP from source codes, unless you make some changes. To make your own build, please install ant and make a build as follows: 
  ```
    $ ant clean dist
  ```
You can find generated jar files ``common/lib/cubridqa-common.jar`` and ``sql/lib/cubridqa-cqt.jar``.

## How To Write Testcase
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


## License
CTP is published under the BSD 3-Clause license. See [LICENSE.md](LICENSE.md) for more details.
Third-party libraries used by CTP are under their own licenses. See [LICENSE-3RD-PARTY.md](LICENSE-3RD-PARTY.md) for details on the license they use.

