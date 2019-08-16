# Test Objective
Structured Query Language (SQL), SQL is an ANSI (American National Standards Institute) standard computer language. Its functions include data query, data manipulation, data definition and data control. It is a general relational database language with strong functions. It is now the standard language for relational databases. (such as SELECT, UPDATE, DELETE, INSERT, WHERE and etc)   
CUBRID SQL test, SQL detection for each build to detect the basic functions of the database and ensure the correct output of commands.
  
# General SQL Test (Developer)
## Linux  
* Install CTP [CTP_install_guide](https://github.com/CUBRID/cubrid-testtools/blob/develop/doc/ctp_install_guide.md)  
* Install CUBRID  
* Check out test cases  
    ```
    cd ~  
    git clone https://github.com/CUBRID/cubrid-testcases.git 
    ```
## Windows  
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
When install visual studio 2017, Choose 'Workloads' view(tab), in 'Windows (3)'section, Choose "Desktop development with C++", then click 'Install' or 'Modify' tostart the installation.  
After installation, check system variable '%VS140COMNTOOLS%'   
* If 'VS140COMNTOOLS' is not add to the system variables automatically, please add itmanually.  
    Variable name: VS140COMNTOOLS  
    Variable value: C:\Program Files (x86)\Microsoft Visual Studio\2017\Community\Common7\Tools\   
* If 'VS140COMNTOOLS' is add to the system variables automatically, please check itsvalue. Sometimes, the value is not correct for make_locale to use it. In thissituation, please change it to the correct one.  
    e.g.  
    Wrong: C:\Program Files (x86)\Microsoft Visual Studio 14.0\Common7\Tools\  
    Correct: C:\Program Files (x86)\Microsoft Visual Studio\2017\Community\Common7\Tools\ (required for last '\')  
* install cygwin  
* We need choose this packages manually since they will not be installed by default:wget, zip, unzip, dos2unix, bc, expect.  
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
## Executed Test
### ctp  
* run ctp.sh
  ```
  ctp.sh sql -c CTP/conf/sql_local.conf --interactive
  ```
  Modify the working directory and configuration parameters in the file CTP/conf/sql_local.conf\
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
     It‘s difference between sql and medium,  there is a unload file cubrid-testcases/medium/files/mdb.tar.gz that is used to medium test.(The files mdb_indexes,mdb_schema and mdb_objects are in the mdb.tar.gz archive)
   * cubrid server start testdb
   * csql -u dba testdb  
# Regression Test Deployment  
* Test Machines  

    No|role|user|ip|hostname|QUEUE_NAME|run_name
    :---:|:--:|:---:|:---:|:---:|:---:|:---:
    1|Test node|sql1|192.168.1.76|func01|QUEUE_CUBRID_QA_SQL_LINUX_GIT|run_sql
    2|Test node|sql2|192.168.1.76|func01|QUEUE_CUBRID_QA_SQL_LINUX_GIT|run_sql
    3|Test node|sql3|192.168.1.76|func01|QUEUE_CUBRID_QA_SQL_LINUX_GIT|run_sql
    4|Test node|sqlbycci|192.168.1.76|func01|QUEUE_CUBRID_QA_SQL_CCI_LINUX_GIT|run_sql_by_cci
    5|Test node|sql|192.168.1.77|func02|QUEUE_CUBRID_QA_SQL_PERF_LINUX|run_sql
    6|Test node|qa|192.168.1.161|winfunc01|QUEUE_CUBRID_QA_SQL_WIN64|run_sql  
* Install reference [General SQL Test (Developer)](#General-SQL-Test-(Developer)) 
* Linux Queue configure    
    touch start_test.sh  
    ```
    stop_consumer.sh 
    prefix=`date "+%Y%m%d%H%M%S"`
    cp nohup.out nohup.out.$prefix
    echo "" > nohup.out
    nohup start_consumer.sh -q [QUEUE_NAME] -exec [run_name] &
    ```       
* Windows Queue configure   
    touch start_test.sh
    ```
    start_test.sh
    rm nohup.out
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
   
# Regression Test Sustaining  
* Daily regression test  
When the build server has a new build, a SQL test will be executed. If there is something wrong and need to run SQL test again, you can send a test message.   
How to view qahome results:  
Go to QA homepage and click the CI build, wait for the page loading, then click the 'Function',look at the case of Fail  
![qa_result](./sql_image/qa_result.png)

* Code coverage test  
	* VERIFY CODE COVERAGE TESTING RESULT  
	Go to QA homepage and find the ‘code coverage’ node in the left area, click the link of latest result.  
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
# Test Case Specification  
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
  * CREATE TABLE should be preceded by DROP TABLE if exists.  
    drop table if exists t1;  
  * All schema objects created should be dropped.  
  * All session variables created should be dropped.  
  * All system parameters should be reset as default  
  We need to restore the system parameters after the test, so as not to affect other test execution   
  * Add ORDER BY for a select statement to make result stable.  
  * Answer should be stable.  
  * Don't write too much queries in one test case file, it's hard to verify when the case fails. You can separate them into several files.  
  * Avoid time-consuming queries.  
  * If need to print a query plan, add '--@queryplan' in front of the query. Or add an empty file like below:  
    /path/to/cases/case_file.sql  
    /path/to/cases/case_file.queryPlan  (make sure to output query plan info)  
    ![queryplan](./sql_image/queryplan.png)
