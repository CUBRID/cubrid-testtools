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
    Visual studio is used by make_locale.bat  
    When install visual studio 2017, choose 'Workloads' view(tab), in 'Windows (3)'section, choose "Desktop development with C++", then click 'Install' or 'Modify' to start the installation.  
    After installation, check system variable '%VS140COMNTOOLS%'   
  * If 'VS140COMNTOOLS' is not add to the system variables automatically, please add it manually.  
      Variable name: VS140COMNTOOLS  
      Variable value: C:\Program Files (x86)\Microsoft Visual Studio\2017\Community\Common7\Tools\     
* ### Install cygwin  
  * We need choose this packages manually since they will not be installed by default:wget, zip, unzip, dos2unix, bc, expect.  
      gcc and mingw packages do not need to be installed.  
  * Check the versions of these packages (or components): gawk, grep, sed  
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
  * Change environment variable 'PATH'  
    Add 'C:\cygwin64\bin' in the 'PATH' 
* ### Install git  
    Download git in https://git-for-windows.github.io/.  
    In the installation wizard, choose these options:  
    'Adjusting your PATH environment', choose 'Use Git from the Windows Command Prompt'  
    'Confifuring the line ending conversions', choose 'Checkout as-is, commit as-is'  
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
