# 1 Test Objective
SQL is an ANSI (American National Standards Institute) standard computer language, but there are still many different versions of SQL.However, in order to be compatible with ANSI standards, they must all support some major commands (such as SELECT, UPDATE, DELETE, INSERT, WHERE, and so on) in a similar way.  
CUBRID SQL test, SQL detection for each build to detect the basic functions of the database and ensure the correct output of commands.

# 2 Test Deployments  
To facilitate the execution of various test requirements.This chapter describes the environment construction.
  * Test Machines  

    No|role|user|ip|hostname
    :---:|:--:|:---:|:---:|:---:
    1|Test node|sql1|192.168.1.76|func01
    2|Test node|sql2|192.168.1.76|func01
    3|Test node|sql3|192.168.1.76|func01
    4|Test node|sqlbycci|192.168.1.76|func01
    5|Test node|sql|192.168.1.77|func02
    6|Test node|qa|192.168.1.161|winfunc01
  * Linux  
	* Install CTP  
		* Step 1: Checkout git repository
		  ```
		  cd ~
		  git clone https://github.com/CUBRID/cubrid-testtools.git
		  cd ~/cubrid-testtools 
		  git checkout develop
		  cp -rf CTP ~/
		  ```
		* Step 2: Configurations
		  * touch and configure ~/CTP/conf/common.conf  
            ```
            git_user=cubridqa
            git_pwd=PASSWORD
            git_email=<e-mail address>
            default_ssh_pwd=PASSWORD
            default_ssh_port=22

            grepo_service_url=rmi://192.168.1.91:11099
            coverage_controller_pwd=PASSWORD

            qahome_db_driver=cubrid.jdbc.driver.CUBRIDDriver
            qahome_db_url=jdbc:cubrid:192.168.1.86:33080:qaresu:dba::
            qahome_db_user=dba
            qahome_db_pwd=

            qahome_server_host=192.168.1.86
            qahome_server_port=22
            qahome_server_user=qahome
            qahome_server_pwd=PASSWORD

            activemq_user=admin
            activemq_pwd=PASSWORD
            activemq_url=failover:tcp://192.168.1.91:61616?
            wireFormat.maxInactivityDurationInitalDelay=30000

            mail_from_nickname=CUBRIDQA_BJ  
            mail_from_address=your_mail@navercorp.com
            ```
		  * configure ~/.bash_profile
            ```
            export CTP_HOME=$HOME/CTP
            export CTP_SKIP_UPDATE=0
            export CTP_BRANCH_NAME=develop
            . ~/.cubrid.sh
            export PATH=$HOME/CTP/bin:$HOME/CTP/common/script:$PATH:$HOME/.local/bin:$PATH:$HOME/bin

            export TZ='Asia/Seoul'
            export LC_ALL=en_US
            ```
            source ~/.bash_profile

		  * touch and configure ~/CTP/conf/sql_local.conf
            ```
            #Copyright (c) 2016, Search Solution Corporation. All rights reserved.
            #--------------------------------------------------------------------
            #
            #Redistribution and use in source and binary forms, with or without 
            #modification, are permitted provided that the following conditions are met:
            #
            #  * Redistributions of source code must retain the above copyright notice, 
            #    this list of conditions and the following disclaimer.
            #
            #  * Redistributions in binary form must reproduce the above copyright 
            #    notice, this list of conditions and the following disclaimer in 
            #    the documentation and/or other materials provided with the distribution.
            #
            #  * Neither the name of the copyright holder nor the names of its contributors may be used to endorse or promote products 
            #    derived from this software without specific prior written permission.
            #
            #THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, 
            #INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE 
            #DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, 
            #SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR 
            #SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, 
            #WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE 
            #USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

            # SQL section - a section for CTP tool configuration when executing sql/medium testing
            [sql]
            # The location of your testing scenario
            scenario=${HOME}/cubrid-testcases/sql
            # Configure an alias name for testing result
            test_category=sql
            # Config file for I18N client charset configuration and init session parameter via 'set system parameter xxx'
            jdbc_config_file=test_default.xml
            # Config database charset for db creation
            db_charset=en_US
            # If test need do make locale or not
            need_make_locale=yes

            # SQL cubrid.conf section - a section for cubrid.conf configuration
            [sql/cubrid.conf]
            # To decide if the Java store procedure will be used when testing
            java_stored_procedure=yes
            # Allow scenario to change database system parameter
            test_mode=yes
            # To increase the speed of execution
            max_plan_cache_entries=1000
            # To increase the speed of execution
            unicode_input_normalization=no
            # To change port of cubrid_port_id to avoid port conflict
            cubrid_port_id=1285
            # In order to simulate the scenario customer use
            ha_mode=yes
            # To reduce the lock wait time to fast testing execution
            lock_timeout=10sec

            # SQL cubrid_ha.conf section - a section for ha related configuration
            [sql/cubrid_ha.conf]
            # Once ha_mode=yes is configured in cubrid.conf, you will require to configure cubrid_ha.conf except ha_db_list 
            ha_mode=yes
            # To reduce memory use
            ha_apply_max_mem_size=300
            # To set what port will be used for ha_port_id
            ha_port_id=12859

            # SQL cubrid_broker.conf query editor section - a section to change parameters under query_editor
            [sql/cubrid_broker.conf/%query_editor]
            # To close one service to avoid port conflict and reduce configuration complexity
            SERVICE=OFF

            # SQL cubrid_broker.conf broker1 section - a section to change parameters under broker1
            [sql/cubrid_broker.conf/%BROKER1]
            # To change broker port to avoid port conflict, if you are sure the port will not conflict, just ignore.
            BROKER_PORT=33285
            # To change ID of shared memory used by CAS, if you are sure the port will not conflict, just ignore.
            APPL_SERVER_SHM_ID=33285

            # SQL cubrid_broker.conf broker section - a section to configure parameters under broker section
            [sql/cubrid_broker.conf/broker]
            # To change the identifier of shared memory to avoid conflict to cause server start fail
            MASTER_SHM_ID=32851
            ```
		  * touch and configure ~/CTP/conf/sql_by_cci_template.conf  
		    Add cci configuration "sql_interface_type=cci" to sql_local.conf  
		* Step 3: Make sure that CTP installed successfully.
          ```
          $ ctp.sh -h
          Welcome to use CUBRID Test Program (CTP)
          usage: ctp.sh <sql|medium|shell|ha_repl|isolation|jdbc|unittest> -c
                  <config_file>
          -c,--config <arg>   provide a configuration file
          -h,--help           show help
            --interactive    interactive mode to run single test case or cases in
                    a folder
          -v,--version        show version

          utility: ctp.sh webconsole <start|stop>

          For example: 
              ctp.sh sql -c conf/sql.conf
              ctp.sh medium -c conf/medium.conf
              ctp.sh shell -c conf/shell.conf
              ctp.sh ha_repl -c conf/ha_repl.conf
              ctp.sh isolation -c conf/isolation.conf
              ctp.sh jdbc -c conf/jdbc.conf
              ctp.sh sql              #use default configuration file: /home/memory1/CTP/conf/sql.conf
              ctp.sh medium           #use default configuration file: /home/memory1/CTP/conf/medium.conf
              ctp.sh shell            #use default configuration file: /home/memory1/CTP/conf/shell.conf
              ctp.sh ha_repl          #use default configuration file: /home/memory1/CTP/conf/ha_repl.conf
              ctp.sh isolation                #use default configuration file: /home/memory1/CTP/conf/isolation.conf
              ctp.sh jdbc             #use default configuration file: /home/memory1/CTP/conf/jdbc.conf
              ctp.sh unittest #use default configuration file: /home/memory1/CTP/conf/unittest.conf
              ctp.sh sql medium       #run both sql and medium with default configuration
              ctp.sh medium medium    #execute medium twice
              ctp.sh webconsole start #start web console to view sql test results
          ```
	* Check out test cases  
	  ```
	  cd ~
	  git clone https://github.com/CUBRID/cubrid-testcases.git 
	  ```
	* Install CUBRID.  
	* touch start_test.sh  
      ```
      stop_consumer.sh 

      prefix=`date "+%Y%m%d%H%M%S"`
      cp nohup.out nohup.out.$prefix
      echo "" > nohup.out

      nohup start_consumer.sh -q [QUEUE_NAME] -exec [run_name] &
      ```
	* sh start_test.sh  
    
  * Windows  
	  * Prepare     
    We need create a new user for dailyqa test: qa  
    use 'Change Account Type' to change user 'qa' to 'Administrator'  
    passwd is the normal root passwd.   
           
    * Login as user 'qa'  
      * install JDK  
      jdk version must greater than 1.6.0_07  
      We use jdk-8u201-windows-x64  

      * Install visual studio 2017  
      Visual studio is used by make_local.bat  
      When install visual studio 2017, Choose 'Workloads' view(tab), in 'Windows (3)' section, Choose "Desktop development with C++", then click 'Install' or 'Modify' to start the installation.  
      After installation, check system variable '%VS140COMNTOOLS%'   
      * If 'VS140COMNTOOLS' is not add to the system variables automatically, please add it manually.  
        Variable name: VS140COMNTOOLS  
        Variable value: C:\Program Files (x86)\Microsoft Visual Studio\2017\Community\Common7\Tools\   
      * If 'VS140COMNTOOLS' is add to the system variables automatically, please check its value. Sometimes, the value is not correct for make_locale to use it. In this situation, please change it to the correct one.  
        e.g.  
        Wrong: C:\Program Files (x86)\Microsoft Visual Studio 14.0\Common7\Tools\  
        Correct: C:\Program Files (x86)\Microsoft Visual Studio\2017\Community\Common7\Tools\ (required for last '\')  

      * install cygwin  
      * We need choose this packages manually since they will not be installed by default: wget, zip, unzip, dos2unix, bc, expect.  
        gcc and mingw packages do not need to be installed.  
      * check the versions of these packages (or components): gawk, grep, sed  
        The invalid versions for cygwin components:  
        grep: 3.0-2  
        gawk: 4.1.4-3  
        sed: 4.4-1  
        We must use the versions before the versions list above.  
        In my test, I use:  
        gawk: 4.1.3-1  
        grep: 3.0-1  
        sed: 4.2.2-3  
        To install the old versions. please refer to this comment Install old packages of cygwin  
      
      * install git  
      https://git-for-windows.github.io/  
      In the installation wizard, choose these options:  
      'Adjusting your PATH environment', choose 'Use Git from the Windows Command Prompt'  
      'Confifuring the line ending conversions', choose 'Checkout as-is, commit as-is'  

      * install a text editor tool which can used both in linux and windows format  
      I intall 'Notepad++ v7.5.1'  

      * install cubrid  
      Use the msi installation file to install cubrid for the first time.  

      * set 'Environment Variables'   
        * After install cubrid by msi file, these system parameter will be added automatically:  
          ```
          CUBRID
          C:\CUBRID\

          CUBRID_DATABASES
          C:\CUBRID\databases
          ```
        * Add new 'System variables':  
          ```
          JAVA_HOME
          C:\Program Files\Java\jdk1.8.0_201

          CTP_BRANCH_NAME
          develop

          CTP_SKIP_UPDATE
          0
          ```
        * Edit 'path'  
        add '%JAVA_HOME%\bin C:\cygwin64\bin' in the 'path'.  

      * Install CTP  
        * Please follow the guide to install CTP.  
        * touch ~/CTP/conf/sql_local.conf  
        Here is the config file that we use for current daily QA test: sql_local.conf  

      * Check out test cases  
        ```
        cd ~
        git clone https://github.com/CUBRID/cubrid-testcases.git 
        ```
      * touch start_test.sh    
        ```
        start_test.sh
        rm nohup.out
        nohup start_consumer.sh -q QUEUE_CUBRID_QA_SQL_WIN64 -exec run_sql &
        ```
        sh start_test.sh  
# 3 CTP Introduction  
This is an implementation of SQL functions that we used to test CUBRID. 

* CTP Tool  
CTP is a testing tool for an open source project CUBRID. It is written in Java and easy to execute tests with a simple configuration
Reference to CTP description: [CTP_README](https://github.com/CUBRID/cubrid-testtools/blob/develop/CTP/README.md)
* Enable configuration listening  
  * touch start_test.sh  
    ```
    stop_consumer.sh 

    prefix=`date "+%Y%m%d%H%M%S"`
    cp nohup.out nohup.out.$prefix
    echo "" > nohup.out

    nohup start_consumer.sh -q [QUEUE_NAME] -exec [run_name] &
    ```
  * sh start_test.sh  
* send test message  
  login message@192.168.1.91 and send test message like:  
  ```
  sender.sh  [QUEUE_NAME]  [CI_BUILD] [Category] default
  ```
  [QUEUE_NAME]:Message queue name  
  [CI_BUILD]:Corresponds to the build installation package address
  [Category]:sql,sql_debug,sql_by_cci,medium,medium_debug  
  
  eg: run sql_by_cci test
  sender.sh QUEUE_CUBRID_QA_SQL_LINUX_GIT http://192.168.1.91:8080/REPO_ROOT/store_01/10.2.0.8369-5a75e41/drop/CUBRID-10.2.0.8369-5a75e41-Linux.x86_64-debug.sh sql_debug default  
## manual test  
### ctp  
* run ctp.sh
  ```
  ctp.sh sql -c CTP/conf/sql_local.conf --interactive
  ```
  Modify the working directory and configuration parameters in the file CTP/conf/sql_local.conf
* run test
  ```
  ======================================  Welcome to Interactive Mode ======================================  
  Usage: 
      help         print the usage of interactive
      run <arg>    the path of case file to run
      run_cci <arg>    the path of case file to run_cci by cci driver
      quit         quit interactive mode 

  For example:
      run .                                                 #run the current directory cases
      run ./_001_primary_key/_002_uniq                      #run the cases which are based on the relative path
      run test.sql                                          #run the test.sql file
      run /home/user1/dailyqa/trunk/scenario/sql/_02_object #run the cases which are based on the absolute path
      run_cci ./_001_primary_key/_002_uniq                  #run the cases which are based on the relative path
      run_cci ./_001_primary_key/_002_uniq/test.sql         #run the cases file
  sql>run .
  ```

  ![run_test](./sql_image/ctp_run_test.png)  

### csql  
* sql test
   * cubrid createdb testdb en_US  
   * cubrid server start testdb
   * csql -u dba testdb  
  ![run_test](./sql_image/csql_run_test.png)  
   
* medium test
   * cubrid createdb testdb en_US  
   * cubrid loaddb -u dba -d mdb_objects -s mdb_schema -i mdb_indexes  testdb
     It¡®s difference between sql and medium,  there is a unload file cubrid-testcases/medium/files/mdb.tar.gz that is used to medium test.(The files mdb_indexes,mdb_schema and mdb_objects are in the mdb.tar.gz archive)
   * cubrid server start testdb
   * csql -u dba testdb  
   
# 4. Regression Tests  
* Daily regression test  
When the build server has a new build, a SQL test will be executed. If there is something wrong and need to run SQL test again, you can send a test message.   
How to view qahome results:  
Go to QA homepage and click the CI build, wait for the page loading, then click the 'Function',look at the case of Fail  
![qa_result](./sql_image/qa_result.png)

* Code coverage test  
	* VERIFY CODE COVERAGE TESTING RESULT  
	Go to QA homepage and find the ¡®code coverage¡¯ node in the left area, click the link of latest result.  
	![coverage](./sql_image/coverage.png)  
	Click the Category(sql,medium,sql_by_cci) link.   
	![category](./sql_image/category.png)  
	There is a coverage rate of lines. Its sql and sql_by_cci coverage rate of lines is usually in 58%~60%, the medium coverage rate of lines is usually in 30%~31%.  
	![coverage_result](./sql_image/coverage_result.png)

	* SEND CODE COVERAGE TESTING MESSAGE  
	sh  sender_code_coverage_testing_message.sh [QUEUE_NAME]  [COVERAGE_BUILD] [Category] default
	eg:
      ```
      cd manual
      sh  sender_code_coverage_testing_message.sh QUEUE_CUBRID_QA_SQL_LINUX_GIT_test http://192.168.1.91:8080/REPO_ROOT/store_01/10.2.0.8270-c897055/drop/CUBRID-10.2.0.8270-c897055-gcov-Linux.x86_64.tar.gz http://192.168.1.91:8080/REPO_ROOT/store_01/10.2.0.8270-c897055/drop/cubrid-10.2.0.8270-c897055-gcov-src-Linux.x86_64.tar.gz  sql
      ```
* Report issues  
	* SQL ISSUE  
	You can refer to http://jira.cubrid.org/browse/CBRD-22721   
    ![issues1](./sql_image/issues1.png)  
    ![issues2](./sql_image/issues2.png)  
	It is also necessary to add this information in the comment to facilitate the developers to find the information they need. Note that choose Restricted to Developers since they have they contain sensitive information such as IP, port, password, etc..  
* how to verify issue?  
	```
	Test Build: 10.2.0.8239-1f051a0 debug
	Test OS: Linux 64bit
	Test Result: Pass.
	Add test case for this issue: 

	Close this issue.
	```
# 5. test case specification  
* When add a test case for a bug fix, add a case to path  
	cubrid-testcases/sql/_13_issues/_{yy}_{1|2}h/cases with naming rules:  
	    cbrd_xxxxx.sql  
	    cbrd_xxxxx_1.sql  
	    cbrd_xxxxx_2.sql  
	    cbrd_xxxxx_xasl.sql  


* When add a test case for a feature test, add a case to path  
	cubrid-testcases/sql/_{no}_{release_code}/cbrd_xxxxx_{feature}/cases with naming rules:   
	    {any_structured_name}.sql

* How to make a SQL test case  
	A SQL test cases follows following basic structure:  
	any_testcase_folder/cases/case_file.sql  
	any_testcase_folder/answers/case_file.answer  
	any_testcase_folder/answers/case_file.answer_WIN (If the answers are different in Windows)  
	any_testcase_folder/answers/case_file.answer_cci (If the answers are different in CCI)  
  
	A SQL file goes to 'cases' folder, an answer file goes to 'answers' folder, and they should be in the same folder.The test case and answer should have the same name.
 
* Notes for writing case:  
  * Drop table before and after test  
  Delete the table before the test to prevent the table of the same name from affecting the test.Remove the table after the test to prevent the table of the same name produced by this test from affecting the next test    
  * Drop variable session after test  
  Delete the variable session after testing to clear memory and prevent impact on other tests  
  * System parameters restores default values  
  We need to restore the system parameters after the test, so as not to affect other test execution  
  * Select ... order by  
  We need to sort the test results so that they are not unstable
  * Note the boundary values   

