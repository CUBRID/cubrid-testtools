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

