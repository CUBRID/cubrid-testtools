# CTP Installation Guide

## 1. Install CTP in Linux platform

* ### Step 1: Install Java

  Java 6 or higher version is required.

  After installation, environment veriables should be added to `.bash_profile`.

      export JAVA_HOME=/path/to/jdk
      export PATH=$JAVA_HOME/bin:$PATH

* ### Step 2: Download and install CTP

  Download CTP and deploy it to path $HOME/CTP as below:

      cd ~
      git clone https://github.com/CUBRID/cubrid-testtools.git
      cd ~/cubrid-testtools 
      git checkout develop
      cp -rf CTP ~/

  Recommend to add it to environment variable `PATH` in `.bash_prfile`:

      export PATH=$HOME/CTP/bin:$HOME/CTP/common/script:$PATH

* ### Step 3: Show help to confirm result

      [fanzq@fmdev059 ~]$ ctp.sh -h
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
              ctp.sh sql              #use default configuration file: /home/fanzq/CTP/conf/sql.conf
              ctp.sh medium           #use default configuration file: /home/fanzq/CTP/conf/medium.conf
              ctp.sh shell            #use default configuration file: /home/fanzq/CTP/conf/shell.conf
              ctp.sh ha_repl          #use default configuration file: /home/fanzq/CTP/conf/ha_repl.conf
              ctp.sh isolation                #use default configuration file: /home/fanzq/CTP/conf/isolation.conf
              ctp.sh jdbc             #use default configuration file: /home/fanzq/CTP/conf/jdbc.conf
              ctp.sh unittest #use default configuration file: /home/fanzq/CTP/conf/unittest.conf
              ctp.sh sql medium       #run both sql and medium with default configuration
              ctp.sh medium medium    #execute medium twice
              ctp.sh webconsole start #start web console to view sql test results

## 2. Install CTP in Windows platform  
* ### Install Visual Studio 2017  
    Visual Studio is required by  CUBRID/bin/make_locale.bat.  
    When install Visual Studio 2017, choose `Workloads` tab, in `Windows (3)` section, choose `Desktop development with C++`, then click `Install` or `Modify` to start the installation.  
    After installation, check system variable `VS140COMNTOOLS`   
  * If `VS140COMNTOOLS` is not added to the system variables automatically, please add it manually.  
    ```
    VS140COMNTOOLS=C:\Program Files (x86)\Microsoft VisualStudio\2017\Community\Common7\Tools\    
    ``` 
* ### Install cygwin  
  * Required packages:  `wget`, `zip`, `unzip`, `dos2unix`, `bc`, `expect`.  
    Do not choose: `gcc` and `MinGW`  
  * Package versions required  
    Unsatisfied versions which will lead to many case failures:  
    ```
    grep: 3.0-2  
    gawk: 4.1.4-3  
    sed: 4.4-1  
    ```
    Satisfied versions:  
    ```
    gawk: 4.1.3-1  
    grep: 3.0-1  
    sed: 4.2.2-3  
    ```
    To install the old versions, please refer to this satisfied versions Install old packages of cygwin.  
    * Why install the old versions?  
        Take 'grep' as an example:  
        Sometimes, '\r' is appended in the texts on windows. But in [\[ANNOUNCEMENT\] Updated \[test\]: grep\-3.0\-2](http://cygwin.1069669.n5.nabble.com/ANNOUNCEMENT-TEST-Cygwin-3-1-0-0-2-td147352.html)  
        ```
        This build modifies the behavior of grep to no longer force text mode on 
        binary-mounted file descriptors.  Since this includes pipelines by 
        default, this means that if you pipe text data through a pipeline (such 
        as the output of a windows program), you may need to insert a call to 
        d2u to sanitize your input before passing it to grep.
        ```
        We do not intend to modify test cases, since the cases are used both by linux and windows platform.  
        So we need to use grep before 3.0-2.  
  * Change environment variable `PATH`  
    Add 'C:\cygwin64\bin' in the `PATH`  
* ### Install git  
    Download git in https://git-for-windows.github.io/.  
    In the installation wizard, choose these options:  
    `Adjusting your PATH environment`, choose `Use Git from the Windows Command Prompt`  
    `Confifuring the line ending conversions`, choose `Checkout as-is, commit as-is`  
* ### Install CTP   
    [Install CTP](#1-install-ctp-in-linux-platform) using cygwin64 follows the same steps as Linux.  

## 3. Install CTP as Regression Test platform

  Follow last chapter to install CTP as general installation. Then let's continue to support regression test.

* ### Provide Common Configuration

    Common configuration will be used by many script files in CTP located in CTP/common/script.

    File ~/CTP/conf/common.conf:

      git_user=<git user>
      git_pwd=<git password>
      git_email=<email address>
      default_ssh_pwd=<password for ssh connect>
      default_ssh_port=<port for ssh connect>

      # Update CTP itself from local repository
      grepo_service_url=rmi://192.168.1.91:11099
      coverage_controller_pwd=<ssh password for code coverage controller>

      # Define JDBC parameters to QA home database server
      qahome_db_driver=cubrid.jdbc.driver.CUBRIDDriver
      qahome_db_url=jdbc:cubrid:192.168.1.86:33080:qaresu:dba::
      qahome_db_user=dba
      qahome_db_pwd=

      # Define SSH connect to QA homepage server
      qahome_server_host=192.168.1.86
      qahome_server_port=22
      qahome_server_user=<user for ssh connect>
      qahome_server_pwd=<password for ssh connect>

      # Define ActiveMQ parameters for message service
      activemq_user=<user>
      activemq_pwd=<password>
      activemq_url=failover:tcp://192.168.1.91:61616?wireFormat.maxInactivityDurationInitalDelay=30000

      mail_from_nickname=CUBRIDQA_BJ
      mail_from_address=dl_cubridqa_bj_internal@navercorp.com

* ### Add CTP environment variables  
    ```
    # CTP HOME
    export CTP_HOME=$HOME/CTP

    # Whether to update CTP when execute CTP/common/script/upgrade.sh. 
    export CTP_SKIP_UPDATE=0

    # Branch used when upgrade CTP
    export CTP_BRANCH_NAME=develop
    ```
# Appendix
* ### Install the old versions 
    Take 'grep' as an example.  
    **Method 1, Install from Internet**
    1. start cygwin installation file 'setup-x86_64.exe'  
    2. In step 'Choose A Download Source', select 'Install from Internet'  
    3. In step 'Select Packages', in the field of 'View', choose 'Category' or 'Full', and in the field of 'Search', input 'grep'.  
    Then, find the line of 'grep: search for regular expression matches in test files'.  
    Click the column of "New" (the second column) on this line, until 3.0-1 appears.  
    (1) If '3.0-1' can be shown automatically, choose 'Pending' in 'View' field, to check the pending list is correct:   
    a. this package is in the list  
    b. if there are other packages which you do not want to update this time, please click the second columns of these lines on by one to mark them as 'keep'.  
    Check the pending list is important, since new versions of other packages are put in pending list and will be updated automatically.  
    For example, last time, I reverted  'gawk' to old version, and this time, I try to install old version of 'grep' and forget to check the pending list, 'gawk' will be updated to the newer version at this time.  
    So we'd better to install 'gawk', 'grep', 'sed' at once, instead of install them separately.   
    (2) If '3.0-1' cannot be shown automatically, cancel this installation, and use the second installation method below.
    4. use the default options in the following steps  
    **Method 2, Install from Local Directory**  
    When the required old versions cannot be found in Method 1, we need to install it from local directory.  
    1. Find your last dowload/installation path, like "http%3a%2f%2fcygwin.mirror.constant.com%2f"  
    2.  Add the previous grep package in the installation path.  
    (1) download the previous package of "grep"  
    (2) put this package in the previous installation path  
    e.g. C:\winshell_setup\http%3a%2f%2fcygwin.mirror.constant.com%2f\x86_64\release\grep  
    (3) edit the setup.ini file  
    e.g. C:\winshell_setup\http%3a%2f%2fcygwin.mirror.constant.com%2f\x86_64\setup.ini  
    Add a "[prev]" section for this previous pack in "@ grep" part. If it already exists, just ignore this step.  
        ```
        [prev]
        version: 3.0-1
        install: x86_64/release/grep/grep-3.0-1.tar.xz 361740 a34cf6fc689a62005f7a33287c86419d7a24d262f694489af0dc864affd031f61f9f15970f2f211f335aa7a0234211facf98cc76d83639c7c631ffe5386b00ac
        source: x86_64/release/grep/grep-3.0-1-src.tar.xz 1379944 9d7b08c7e21d0d5058faff728dc575aab95d8c0ab8f70897ff0b2910f32b7f8dd1cdab530564a2786ffb24f676fa16bf7a51d8e0fb1488d2971dcc1d1c443d99
        ```
    3. start setup-x86_64.exe  
    (1) In step "Choose A Download Source":  
    Choose "Install from Local Directory"  
    (2) In step "Select Local Package Directory":  
    Specify the path as "C:\winshell_setup\http%3a%2f%2fcygwin.mirror.constant.com%2f"  
    (3) use the default options in the following steps, until you met step "Select Packages"  
    choose the correct version of "grep", in this case, I select "3.0-1"  
    (4) use the default options in the following steps  
