# 1. Test Objective  
This guide is to introduce how to execute shell_long test suite via test tool CTP.  The shell_long test case specification is exactly same as general SHELL test case's. The only difference between them is that shell_long test cases ask a little long time to execute.     

# 2. Execute Shell_long Test
To perform the test, we need to install CTP first.
## 2.1 Install CTP
Please refer to the guide to [install CTP in Linux platform](ctp_install_guide.md#1-install-ctp-in-linux-platform).    
Create shell_long test configuration file as below:   
File `CTP/conf/shell_long.conf`
```
# path to a test case folder
scenario=${HOME}/cubrid-testcases-private/longcase/shell/1hour/bug_bts_9382
```
>Note: scenario should be a path to a folder but not a file.

## 2.2  Prepare Test Cases
Generally, we check out test cases from git repository. Shell long test cases are located in  https://github.com/CUBRID/cubrid-testcases-private/tree/develop/longcase.      
```
$ cd ~
$ git clone https://github.com/CUBRID/cubrid-testcases-private.git
$ ls ~/cubrid-testcases-private/longcase
shell
$ cd shell/
$ ls
_04_misc  _06_issues  1hour  2hour  _30_banana_qa  _32_features_930  3hour  5hour  config  other
```
## 2.3 Install CUBRID
You may install CUBRID as your way or install CUBRID via `run_cubrid_install` script in CTP.
```
run_cubrid_install http://192.168.1.91:8080/REPO_ROOT/store_01/10.2.0.8368-b85a234/drop/CUBRID-10.2.0.8368-b85a234-Linux.x86_64-debug.sh
```
## 2.4 Execute test
```
ctp.sh shell -c ./cci.conf 
```
Screen output:    
Druing the test, CTP prints testing information like test configs, deploy status, test results, execution time and so on.
```
$ ctp.sh shell -c shell_long.conf 

====================================== SHELL ==========================================
[SHELL] TEST STARTED (Tue Sep 17 15:36:26 KST 2019)

[SHELL] CONFIG FILE: /home/zll/cubrid-testcases-private/longcase/shell/1hour/shell_long.conf

scenario=${HOME}/cubrid-testcases-private/longcase/shell/1hour/bug_bts_9382


----------END OF FILE----------
Available Env: [local]
Continue Mode: false
Build Number: 10.2.0.8456-70f72ff
java.runtime.name=Java(TM) SE Runtime Environment
sun.boot.library.path=/home/zll/opt/jdk1.6.0_07/jre/lib/amd64
java.vm.version=10.0-b23
java.vm.vendor=Sun Microsystems Inc.
java.vendor.url=http://java.sun.com/
path.separator=:
java.vm.name=Java HotSpot(TM) 64-Bit Server VM
file.encoding.pkg=sun.io
user.country=US
sun.java.launcher=SUN_STANDARD
sun.os.patch.level=unknown
java.vm.specification.name=Java Virtual Machine Specification
user.dir=/home/zll/cubrid-testcases-private/longcase/shell/1hour
java.runtime.version=1.6.0_07-b06
java.awt.graphicsenv=sun.awt.X11GraphicsEnvironment
java.endorsed.dirs=/home/zll/opt/jdk1.6.0_07/jre/lib/endorsed
os.arch=amd64
java.io.tmpdir=/tmp
line.separator=

java.vm.specification.vendor=Sun Microsystems Inc.
os.name=Linux
sun.jnu.encoding=ISO-8859-1
java.library.path=/home/zll/opt/jdk1.6.0_07/jre/lib/amd64/server:/home/zll/opt/jdk1.6.0_07/jre/lib/amd64:/home/zll/opt/jdk1.6.0_07/jre/../lib/amd64:/home/zll/CUBRID/lib:/home/zll/CUBRID/lib::/home/zll/opt/jdk1.6.0_07/jre/lib/amd64:/home/zll/opt/jdk1.6.0_07/jre/lib/amd64/server:/home/zll/CTP/shell/init_path/commonforc/lib/:/usr/java/packages/lib/amd64:/lib:/usr/lib
scenario=${HOME}/cubrid-testcases-private/longcase/shell/1hour/bug_bts_9382
java.specification.name=Java Platform API Specification
java.class.version=50.0
sun.management.compiler=HotSpot 64-Bit Server Compiler
os.version=3.10.0-327.el7.x86_64
user.home=/home/zll
user.timezone=Asia/Seoul
java.awt.printerjob=sun.print.PSPrinterJob
file.encoding=ISO-8859-1
java.specification.version=1.6
user.name=zll
java.class.path=/home/zll/CTP/common/lib/cubridqa-common.jar
java.vm.specification.version=1.0
sun.arch.data.model=64
java.home=/home/zll/opt/jdk1.6.0_07/jre
java.specification.vendor=Sun Microsystems Inc.
user.language=en
java.vm.info=mixed mode
java.version=1.6.0_07
java.ext.dirs=/home/zll/opt/jdk1.6.0_07/jre/lib/ext:/usr/java/packages/lib/ext
sun.boot.class.path=/home/zll/opt/jdk1.6.0_07/jre/lib/resources.jar:/home/zll/opt/jdk1.6.0_07/jre/lib/rt.jar:/home/zll/opt/jdk1.6.0_07/jre/lib/sunrsasign.jar:/home/zll/opt/jdk1.6.0_07/jre/lib/jsse.jar:/home/zll/opt/jdk1.6.0_07/jre/lib/jce.jar:/home/zll/opt/jdk1.6.0_07/jre/lib/charsets.jar:/home/zll/opt/jdk1.6.0_07/jre/classes
java.vendor=Sun Microsystems Inc.
file.separator=/
java.vendor.url.bug=http://java.sun.com/cgi-bin/bugreport.cgi
sun.cpu.endian=little
sun.io.unicode.encoding=UnicodeLittle
sun.rmi.transport.connectionTimeout=10000000
sun.cpu.isalist=
AUTO_TEST_VERSION=10.2.0.8456-70f72ff
AUTO_TEST_BITS=64bits
BEGIN TO CHECK: 
=================== Check local============================
==> Check connection(ssh) ...... PASS
==> Check variable 'HOME' ...... PASS
==> Check variable 'USER' ...... PASS
==> Check variable 'JAVA_HOME' ...... PASS
==> Check variable 'CTP_HOME' ...... PASS
==> Check variable 'init_path' ...... PASS
==> Check variable 'CUBRID' ...... PASS
==> Check command 'java' ...... PASS
==> Check command 'javac' ...... PASS
==> Check command 'diff' ...... PASS
==> Check command 'wget' ...... PASS
==> Check command 'find' ...... PASS
==> Check command 'cat' ...... PASS
==> Check command 'kill' ...... PASS
==> Check command 'dos2unix' ...... PASS
==> Check command 'tar' ...... PASS
==> Check directory '${CTP_HOME}/bin' ...... PASS
==> Check directory '${CTP_HOME}/common/script' ...... PASS
==> Check directory 'cubrid-testcases-private/longcase/shell/1hour/bug_bts_9382' ...... PASS

CHECK RESULT: PASS
============= UPDATE TEST CASES ==================
CLEAN PROCESSES:
@ cubrid broker stop
@ cubrid broker stop
++ cubrid broker is not running.
@ cubrid manager server stop
++ cubrid manager server is not running.
@ cubrid master stop
++ cubrid master stop: success
@ cubrid broker stop
++ cubrid broker is not running.
@ cubrid manager server stop
++ cubrid manager server is not running.
@ cubrid master stop
++ cubrid master stop: success
UID        PID  PPID  C STIME TTY          TIME CMD
zll       5193  5191  0 Sep16 ?        00:00:04 sshd: zll@pts/0,pts/1,pts/2
zll       5195  5193  0 Sep16 pts/0    00:00:00 -bash
zll       6673  5193  0 Sep16 pts/1    00:00:00 -bash
zll      22337  5193  0 Sep16 pts/2    00:00:00 -bash
zll      22397     1  0 Sep16 pts/2    00:03:18 /home/zll/opt/jdk1.6.0_07/jre/bin/java -cp /home/zll/CTP/common/lib/cubridqa-common.jar:/home/zll/CTP/sql/webconsole/../lib/cubridqa-cqt.jar com.navercorp.cubridqa.cqt.webconsole.WebServer 8888 /home/zll/CTP/sql/webconsole /home/zll/CTP/sql/result /
zll      31235  5195  0 15:36 pts/0    00:00:00 /bin/sh /home/zll/CTP/bin/ctp.sh shell -c shell_long.conf
zll      31242 31235 15 15:36 pts/0    00:00:00 /home/zll/opt/jdk1.6.0_07/bin/java -cp /home/zll/CTP/common/lib/cubridqa-common.jar com.navercorp.cubridqa.ctp.CTP shell -c shell_long.conf
zll      31243 31235  0 15:36 pts/0    00:00:00 tee /home/zll/CTP/.output_2019091715361568702186.log
zll      31584 31242  0 15:36 pts/0    00:00:00 sh /tmp/.localexec39935.sh 2>&1
zll      31790 31584  0 15:36 pts/0    00:00:00 ps -u zll -f
zll      22397     1  0 Sep16 pts/2    00:03:18 /home/zll/opt/jdk1.6.0_07/jre/bin/java -cp /home/zll/CTP/common/lib/cubridqa-common.jar:/home/zll/CTP/sql/webconsole/../lib/cubridqa-cqt.jar com.navercorp.cubridqa.cqt.webconsole.WebServer 8888 /home/zll/CTP/sql/webconsole /home/zll/CTP/sql/result /
zll      31242 31235 15 15:36 pts/0    00:00:00 /home/zll/opt/jdk1.6.0_07/bin/java -cp /home/zll/CTP/common/lib/cubridqa-common.jar com.navercorp.cubridqa.ctp.CTP shell -c shell_long.conf
zll      31792 31584  0 15:36 pts/0    00:00:00 grep --color=auto cub
Active Internet connections (servers and established)
Proto Recv-Q Send-Q Local Address           Foreign Address         State       User       Inode      PID/Program name    
tcp        0      0 0.0.0.0:14024           0.0.0.0:*               LISTEN      0          11166      -                   
tcp        0      0 127.0.0.1:25            0.0.0.0:*               LISTEN      0          14922      -                   
tcp        0      0 10.34.64.63:14024       10.34.63.43:59669       ESTABLISHED 0          404386     -                   
tcp6       0      0 :::14024                :::*                    LISTEN      0          11168      -                   
tcp6       0      0 :::3307                 :::*                    LISTEN      27         20664      -                   
tcp6       0      0 :::8888                 :::*                    LISTEN      1000       419673     22397/java          
tcp6       0      0 ::1:25                  :::*                    LISTEN      0          14923      -                   
udp        0      0 10.34.64.63:123         0.0.0.0:*                           38         8623       -                   
udp        0      0 127.0.0.1:123           0.0.0.0:*                           0          20643      -                   
udp        0      0 0.0.0.0:123             0.0.0.0:*                           0          20637      -                   
udp6       0      0 fe80::be30:5bff:fef:123 :::*                                38         8624       -                   
udp6       0      0 ::1:123                 :::*                                0          20644      -                   
udp6       0      0 :::123                  :::*                                0          20638      -                   
Active UNIX domain sockets (servers and established)
Proto RefCnt Flags       Type       State         I-Node   PID/Program name     Path
unix  2      [ ACC ]     STREAM     LISTENING     14940    -                    private/rewrite
unix  2      [ ACC ]     STREAM     LISTENING     14949    -                    private/trace
unix  2      [ ACC ]     STREAM     LISTENING     14937    -                    private/tlsmgr
unix  2      [ ACC ]     STREAM     LISTENING     14943    -                    private/bounce
unix  2      [ ACC ]     STREAM     LISTENING     14946    -                    private/defer
unix  2      [ ACC ]     STREAM     LISTENING     14952    -                    private/verify
unix  2      [ ]         DGRAM                    7772     -                    /run/systemd/cgroups-agent
unix  2      [ ACC ]     STREAM     LISTENING     7774     -                    /run/systemd/private
unix  2      [ ACC ]     STREAM     LISTENING     14958    -                    private/proxymap
unix  2      [ ACC ]     STREAM     LISTENING     14961    -                    private/proxywrite
unix  2      [ ACC ]     STREAM     LISTENING     14964    -                    private/smtp
unix  2      [ ACC ]     STREAM     LISTENING     14967    -                    private/relay
unix  2      [ ACC ]     STREAM     LISTENING     14973    -                    private/error
unix  2      [ ACC ]     STREAM     LISTENING     14976    -                    private/retry
unix  2      [ ACC ]     STREAM     LISTENING     14979    -                    private/discard
unix  2      [ ACC ]     STREAM     LISTENING     14982    -                    private/local
unix  2      [ ACC ]     STREAM     LISTENING     14985    -                    private/virtual
unix  2      [ ACC ]     STREAM     LISTENING     14988    -                    private/lmtp
unix  2      [ ACC ]     STREAM     LISTENING     14991    -                    private/anvil
unix  2      [ ACC ]     STREAM     LISTENING     14994    -                    private/scache
unix  2      [ ACC ]     STREAM     LISTENING     14926    -                    public/pickup
unix  2      [ ACC ]     STREAM     LISTENING     14933    -                    public/qmgr
unix  2      [ ACC ]     STREAM     LISTENING     14955    -                    public/flush
unix  2      [ ACC ]     STREAM     LISTENING     14970    -                    public/showq
unix  2      [ ACC ]     STREAM     LISTENING     14930    -                    public/cleanup
unix  2      [ ]         DGRAM                    10451    -                    /run/systemd/notify
unix  2      [ ACC ]     STREAM     LISTENING     16905    -                    /var/mysql/mysql.sock
unix  2      [ ACC ]     STREAM     LISTENING     10462    -                    /run/systemd/journal/stdout
unix  5      [ ]         DGRAM                    10465    -                    /run/systemd/journal/socket
unix  10     [ ]         DGRAM                    10467    -                    /dev/log
unix  2      [ ACC ]     STREAM     LISTENING     15598    -                    /run/dbus/system_bus_socket
unix  2      [ ]         DGRAM                    16630    -                    /run/systemd/shutdownd
unix  2      [ ACC ]     SEQPACKET  LISTENING     16632    -                    /run/udev/control
unix  2      [ ]         DGRAM                    10915    -                    
unix  3      [ ]         STREAM     CONNECTED     14945    -                    
unix  3      [ ]         STREAM     CONNECTED     14993    -                    
unix  3      [ ]         STREAM     CONNECTED     14925    -                    
unix  3      [ ]         STREAM     CONNECTED     14957    -                    
unix  3      [ ]         DGRAM                    7863     -                    
unix  3      [ ]         STREAM     CONNECTED     14989    -                    
unix  3      [ ]         STREAM     CONNECTED     14972    -                    
unix  3      [ ]         STREAM     CONNECTED     1655     -                    
unix  2      [ ]         DGRAM                    21645    -                    
unix  3      [ ]         STREAM     CONNECTED     14953    -                    
unix  3      [ ]         STREAM     CONNECTED     14984    -                    
unix  3      [ ]         STREAM     CONNECTED     14965    -                    
unix  2      [ ]         DGRAM                    15618    -                    
unix  2      [ ]         DGRAM                    15000    -                    
unix  3      [ ]         STREAM     CONNECTED     19616    -                    
unix  3      [ ]         STREAM     CONNECTED     14980    -                    
unix  3      [ ]         STREAM     CONNECTED     14942    -                    
unix  3      [ ]         STREAM     CONNECTED     14187    -                    
unix  3      [ ]         STREAM     CONNECTED     14992    -                    
unix  3      [ ]         STREAM     CONNECTED     10916    -                    
unix  3      [ ]         STREAM     CONNECTED     14944    -                    
unix  3      [ ]         STREAM     CONNECTED     14956    -                    
unix  2      [ ]         DGRAM                    7854     -                    
unix  3      [ ]         STREAM     CONNECTED     15613    -                    
unix  3      [ ]         STREAM     CONNECTED     14969    -                    
unix  3      [ ]         STREAM     CONNECTED     14947    -                    
unix  3      [ ]         STREAM     CONNECTED     14852    -                    
unix  3      [ ]         STREAM     CONNECTED     14968    -                    
unix  3      [ ]         STREAM     CONNECTED     14934    -                    
unix  2      [ ]         DGRAM                    14731    -                    
unix  3      [ ]         STREAM     CONNECTED     19617    -                    
unix  3      [ ]         STREAM     CONNECTED     14981    -                    
unix  3      [ ]         STREAM     CONNECTED     16855    -                    /run/systemd/journal/stdout
unix  3      [ ]         STREAM     CONNECTED     10823    -                    
unix  3      [ ]         STREAM     CONNECTED     14996    -                    
unix  3      [ ]         STREAM     CONNECTED     14977    -                    
unix  3      [ ]         STREAM     CONNECTED     14960    -                    
unix  3      [ ]         STREAM     CONNECTED     14939    -                    
unix  3      [ ]         STREAM     CONNECTED     8476     -                    /run/systemd/journal/stdout
unix  3      [ ]         STREAM     CONNECTED     8459     -                    /run/systemd/journal/stdout
unix  3      [ ]         STREAM     CONNECTED     16852    -                    /run/systemd/journal/stdout
unix  3      [ ]         STREAM     CONNECTED     19618    -                    /run/dbus/system_bus_socket
unix  3      [ ]         STREAM     CONNECTED     14927    -                    
unix  3      [ ]         STREAM     CONNECTED     14959    -                    
unix  3      [ ]         STREAM     CONNECTED     10917    -                    
unix  3      [ ]         STREAM     CONNECTED     14974    -                    
unix  3      [ ]         STREAM     CONNECTED     14017    -                    
unix  3      [ ]         STREAM     CONNECTED     8612     -                    /run/dbus/system_bus_socket
unix  3      [ ]         STREAM     CONNECTED     14948    -                    
unix  3      [ ]         STREAM     CONNECTED     14951    -                    
unix  3      [ ]         STREAM     CONNECTED     14987    -                    
unix  3      [ ]         STREAM     CONNECTED     19619    -                    /run/dbus/system_bus_socket
unix  2      [ ]         STREAM     CONNECTED     425999   22397/java           
unix  3      [ ]         STREAM     CONNECTED     1719     -                    
unix  3      [ ]         STREAM     CONNECTED     14938    -                    
unix  2      [ ]         DGRAM                    19621    -                    
unix  3      [ ]         STREAM     CONNECTED     16677    -                    /run/systemd/journal/stdout
unix  3      [ ]         STREAM     CONNECTED     14963    -                    
unix  3      [ ]         STREAM     CONNECTED     14931    -                    
unix  3      [ ]         STREAM     CONNECTED     399020   -                    
unix  3      [ ]         STREAM     CONNECTED     14995    -                    
unix  3      [ ]         STREAM     CONNECTED     14978    -                    
unix  3      [ ]         STREAM     CONNECTED     11053    -                    
unix  3      [ ]         STREAM     CONNECTED     14928    -                    
unix  2      [ ]         DGRAM                    399017   -                    
unix  3      [ ]         STREAM     CONNECTED     14975    -                    
unix  3      [ ]         STREAM     CONNECTED     9500     -                    
unix  3      [ ]         STREAM     CONNECTED     14990    -                    
unix  2      [ ]         DGRAM                    15607    -                    
unix  3      [ ]         STREAM     CONNECTED     8454     -                    /run/systemd/journal/stdout
unix  2      [ ]         DGRAM                    441703   -                    
unix  3      [ ]         STREAM     CONNECTED     14971    -                    
unix  3      [ ]         STREAM     CONNECTED     14954    -                    
unix  3      [ ]         STREAM     CONNECTED     14950    -                    
unix  3      [ ]         STREAM     CONNECTED     14924    -                    
unix  3      [ ]         STREAM     CONNECTED     8457     -                    /run/systemd/journal/stdout
unix  3      [ ]         STREAM     CONNECTED     14986    -                    
unix  3      [ ]         DGRAM                    7862     -                    
unix  3      [ ]         STREAM     CONNECTED     14941    -                    
unix  3      [ ]         STREAM     CONNECTED     14935    -                    
unix  3      [ ]         STREAM     CONNECTED     399021   -                    
unix  3      [ ]         STREAM     CONNECTED     14983    -                    
unix  3      [ ]         STREAM     CONNECTED     14966    -                    
unix  3      [ ]         STREAM     CONNECTED     14962    -                    
unix  3      [ ]         STREAM     CONNECTED     14932    -                    
unix  2      [ ]         DGRAM                    9392     -                    
unix  3      [ ]         STREAM     CONNECTED     11153    -                    

------ Message Queues --------
key        msqid      owner      perms      used-bytes   messages    

------ Shared Memory Segments --------
key        shmid      owner      perms      bytes      nattch     status      

------ Semaphore Arrays --------
key        semid      owner      perms      nsems
EXPECT NOTHING FOR BELOW (local)
Above EnvId is local
SKIP TEST CASES UPDATE
DONE
============= FETCH TEST CASES ==================
Test Category:shell
The Number of Test Cases: 1 (macro skipped: 0, bug skipped: 0)
The Number of Test Case : 1
============= DEPLOY ==================
CLEAN PROCESSES:
++ cubrid service is not running.
UID        PID  PPID  C STIME TTY          TIME CMD
zll       5193  5191  0 Sep16 ?        00:00:04 sshd: zll@pts/0,pts/1,pts/2
zll       5195  5193  0 Sep16 pts/0    00:00:00 -bash
zll       6673  5193  0 Sep16 pts/1    00:00:00 -bash
zll      22337  5193  0 Sep16 pts/2    00:00:00 -bash
zll      22397     1  0 Sep16 pts/2    00:03:18 /home/zll/opt/jdk1.6.0_07/jre/bin/java -cp /home/zll/CTP/common/lib/cubridqa-common.jar:/home/zll/CTP/sql/webconsole/../lib/cubridqa-cqt.jar com.navercorp.cubridqa.cqt.webconsole.WebServer 8888 /home/zll/CTP/sql/webconsole /home/zll/CTP/sql/result /
zll      31235  5195  0 15:36 pts/0    00:00:00 /bin/sh /home/zll/CTP/bin/ctp.sh shell -c shell_long.conf
zll      31242 31235 12 15:36 pts/0    00:00:00 /home/zll/opt/jdk1.6.0_07/bin/java -cp /home/zll/CTP/common/lib/cubridqa-common.jar com.navercorp.cubridqa.ctp.CTP shell -c shell_long.conf
zll      31243 31235  0 15:36 pts/0    00:00:00 tee /home/zll/CTP/.output_2019091715361568702186.log
zll      31889 31242  2 15:36 pts/0    00:00:00 sh /tmp/.localexec39939.sh 2>&1
zll      32089 31889  0 15:36 pts/0    00:00:00 ps -u zll -f
zll      22397     1  0 Sep16 pts/2    00:03:18 /home/zll/opt/jdk1.6.0_07/jre/bin/java -cp /home/zll/CTP/common/lib/cubridqa-common.jar:/home/zll/CTP/sql/webconsole/../lib/cubridqa-cqt.jar com.navercorp.cubridqa.cqt.webconsole.WebServer 8888 /home/zll/CTP/sql/webconsole /home/zll/CTP/sql/result /
zll      31242 31235 12 15:36 pts/0    00:00:00 /home/zll/opt/jdk1.6.0_07/bin/java -cp /home/zll/CTP/common/lib/cubridqa-common.jar com.navercorp.cubridqa.ctp.CTP shell -c shell_long.conf
zll      32091 31889  0 15:36 pts/0    00:00:00 grep --color=auto cub
Active Internet connections (servers and established)
Proto Recv-Q Send-Q Local Address           Foreign Address         State       User       Inode      PID/Program name    
tcp        0      0 0.0.0.0:14024           0.0.0.0:*               LISTEN      0          11166      -                   
tcp        0      0 127.0.0.1:25            0.0.0.0:*               LISTEN      0          14922      -                   
tcp        0      0 10.34.64.63:14024       10.34.63.43:59669       ESTABLISHED 0          404386     -                   
tcp6       0      0 :::14024                :::*                    LISTEN      0          11168      -                   
tcp6       0      0 :::3307                 :::*                    LISTEN      27         20664      -                   
tcp6       0      0 :::8888                 :::*                    LISTEN      1000       419673     22397/java          
tcp6       0      0 ::1:25                  :::*                    LISTEN      0          14923      -                   
udp        0      0 10.34.64.63:123         0.0.0.0:*                           38         8623       -                   
udp        0      0 127.0.0.1:123           0.0.0.0:*                           0          20643      -                   
udp        0      0 0.0.0.0:123             0.0.0.0:*                           0          20637      -                   
udp6       0      0 fe80::be30:5bff:fef:123 :::*                                38         8624       -                   
udp6       0      0 ::1:123                 :::*                                0          20644      -                   
udp6       0      0 :::123                  :::*                                0          20638      -                   
Active UNIX domain sockets (servers and established)
Proto RefCnt Flags       Type       State         I-Node   PID/Program name     Path
unix  2      [ ACC ]     STREAM     LISTENING     14940    -                    private/rewrite
unix  2      [ ACC ]     STREAM     LISTENING     14949    -                    private/trace
unix  2      [ ACC ]     STREAM     LISTENING     14937    -                    private/tlsmgr
unix  2      [ ACC ]     STREAM     LISTENING     14943    -                    private/bounce
unix  2      [ ACC ]     STREAM     LISTENING     14946    -                    private/defer
unix  2      [ ACC ]     STREAM     LISTENING     14952    -                    private/verify
unix  2      [ ]         DGRAM                    7772     -                    /run/systemd/cgroups-agent
unix  2      [ ACC ]     STREAM     LISTENING     7774     -                    /run/systemd/private
unix  2      [ ACC ]     STREAM     LISTENING     14958    -                    private/proxymap
unix  2      [ ACC ]     STREAM     LISTENING     14961    -                    private/proxywrite
unix  2      [ ACC ]     STREAM     LISTENING     14964    -                    private/smtp
unix  2      [ ACC ]     STREAM     LISTENING     14967    -                    private/relay
unix  2      [ ACC ]     STREAM     LISTENING     14973    -                    private/error
unix  2      [ ACC ]     STREAM     LISTENING     14976    -                    private/retry
unix  2      [ ACC ]     STREAM     LISTENING     14979    -                    private/discard
unix  2      [ ACC ]     STREAM     LISTENING     14982    -                    private/local
unix  2      [ ACC ]     STREAM     LISTENING     14985    -                    private/virtual
unix  2      [ ACC ]     STREAM     LISTENING     14988    -                    private/lmtp
unix  2      [ ACC ]     STREAM     LISTENING     14991    -                    private/anvil
unix  2      [ ACC ]     STREAM     LISTENING     14994    -                    private/scache
unix  2      [ ACC ]     STREAM     LISTENING     14926    -                    public/pickup
unix  2      [ ACC ]     STREAM     LISTENING     14933    -                    public/qmgr
unix  2      [ ACC ]     STREAM     LISTENING     14955    -                    public/flush
unix  2      [ ACC ]     STREAM     LISTENING     14970    -                    public/showq
unix  2      [ ACC ]     STREAM     LISTENING     14930    -                    public/cleanup
unix  2      [ ]         DGRAM                    10451    -                    /run/systemd/notify
unix  2      [ ACC ]     STREAM     LISTENING     16905    -                    /var/mysql/mysql.sock
unix  2      [ ACC ]     STREAM     LISTENING     10462    -                    /run/systemd/journal/stdout
unix  5      [ ]         DGRAM                    10465    -                    /run/systemd/journal/socket
unix  10     [ ]         DGRAM                    10467    -                    /dev/log
unix  2      [ ACC ]     STREAM     LISTENING     15598    -                    /run/dbus/system_bus_socket
unix  2      [ ]         DGRAM                    16630    -                    /run/systemd/shutdownd
unix  2      [ ACC ]     SEQPACKET  LISTENING     16632    -                    /run/udev/control
unix  2      [ ]         DGRAM                    10915    -                    
unix  3      [ ]         STREAM     CONNECTED     14945    -                    
unix  3      [ ]         STREAM     CONNECTED     14993    -                    
unix  3      [ ]         STREAM     CONNECTED     14925    -                    
unix  3      [ ]         STREAM     CONNECTED     14957    -                    
unix  3      [ ]         DGRAM                    7863     -                    
unix  3      [ ]         STREAM     CONNECTED     14989    -                    
unix  3      [ ]         STREAM     CONNECTED     14972    -                    
unix  3      [ ]         STREAM     CONNECTED     1655     -                    
unix  2      [ ]         DGRAM                    21645    -                    
unix  3      [ ]         STREAM     CONNECTED     14953    -                    
unix  3      [ ]         STREAM     CONNECTED     14984    -                    
unix  3      [ ]         STREAM     CONNECTED     14965    -                    
unix  2      [ ]         DGRAM                    15618    -                    
unix  2      [ ]         DGRAM                    15000    -                    
unix  3      [ ]         STREAM     CONNECTED     19616    -                    
unix  3      [ ]         STREAM     CONNECTED     14980    -                    
unix  3      [ ]         STREAM     CONNECTED     14942    -                    
unix  3      [ ]         STREAM     CONNECTED     14187    -                    
unix  3      [ ]         STREAM     CONNECTED     14992    -                    
unix  3      [ ]         STREAM     CONNECTED     10916    -                    
unix  3      [ ]         STREAM     CONNECTED     14944    -                    
unix  3      [ ]         STREAM     CONNECTED     14956    -                    
unix  2      [ ]         DGRAM                    7854     -                    
unix  3      [ ]         STREAM     CONNECTED     15613    -                    
unix  3      [ ]         STREAM     CONNECTED     14969    -                    
unix  3      [ ]         STREAM     CONNECTED     14947    -                    
unix  3      [ ]         STREAM     CONNECTED     14852    -                    
unix  3      [ ]         STREAM     CONNECTED     14968    -                    
unix  3      [ ]         STREAM     CONNECTED     14934    -                    
unix  2      [ ]         DGRAM                    14731    -                    
unix  3      [ ]         STREAM     CONNECTED     19617    -                    
unix  3      [ ]         STREAM     CONNECTED     14981    -                    
unix  3      [ ]         STREAM     CONNECTED     16855    -                    /run/systemd/journal/stdout
unix  3      [ ]         STREAM     CONNECTED     10823    -                    
unix  3      [ ]         STREAM     CONNECTED     14996    -                    
unix  3      [ ]         STREAM     CONNECTED     14977    -                    
unix  3      [ ]         STREAM     CONNECTED     14960    -                    
unix  3      [ ]         STREAM     CONNECTED     14939    -                    
unix  3      [ ]         STREAM     CONNECTED     8476     -                    /run/systemd/journal/stdout
unix  3      [ ]         STREAM     CONNECTED     8459     -                    /run/systemd/journal/stdout
unix  3      [ ]         STREAM     CONNECTED     16852    -                    /run/systemd/journal/stdout
unix  3      [ ]         STREAM     CONNECTED     19618    -                    /run/dbus/system_bus_socket
unix  3      [ ]         STREAM     CONNECTED     14927    -                    
unix  3      [ ]         STREAM     CONNECTED     14959    -                    
unix  3      [ ]         STREAM     CONNECTED     10917    -                    
unix  3      [ ]         STREAM     CONNECTED     14974    -                    
unix  3      [ ]         STREAM     CONNECTED     14017    -                    
unix  3      [ ]         STREAM     CONNECTED     8612     -                    /run/dbus/system_bus_socket
unix  3      [ ]         STREAM     CONNECTED     14948    -                    
unix  3      [ ]         STREAM     CONNECTED     14951    -                    
unix  3      [ ]         STREAM     CONNECTED     14987    -                    
unix  3      [ ]         STREAM     CONNECTED     19619    -                    /run/dbus/system_bus_socket
unix  2      [ ]         STREAM     CONNECTED     425999   22397/java           
unix  3      [ ]         STREAM     CONNECTED     1719     -                    
unix  3      [ ]         STREAM     CONNECTED     14938    -                    
unix  2      [ ]         DGRAM                    19621    -                    
unix  3      [ ]         STREAM     CONNECTED     16677    -                    /run/systemd/journal/stdout
unix  3      [ ]         STREAM     CONNECTED     14963    -                    
unix  3      [ ]         STREAM     CONNECTED     14931    -                    
unix  3      [ ]         STREAM     CONNECTED     399020   -                    
unix  3      [ ]         STREAM     CONNECTED     14995    -                    
unix  3      [ ]         STREAM     CONNECTED     14978    -                    
unix  3      [ ]         STREAM     CONNECTED     11053    -                    
unix  3      [ ]         STREAM     CONNECTED     14928    -                    
unix  2      [ ]         DGRAM                    399017   -                    
unix  3      [ ]         STREAM     CONNECTED     14975    -                    
unix  3      [ ]         STREAM     CONNECTED     9500     -                    
unix  3      [ ]         STREAM     CONNECTED     14990    -                    
unix  2      [ ]         DGRAM                    15607    -                    
unix  3      [ ]         STREAM     CONNECTED     8454     -                    /run/systemd/journal/stdout
unix  2      [ ]         DGRAM                    441703   -                    
unix  3      [ ]         STREAM     CONNECTED     14971    -                    
unix  3      [ ]         STREAM     CONNECTED     14954    -                    
unix  3      [ ]         STREAM     CONNECTED     14950    -                    
unix  3      [ ]         STREAM     CONNECTED     14924    -                    
unix  3      [ ]         STREAM     CONNECTED     8457     -                    /run/systemd/journal/stdout
unix  3      [ ]         STREAM     CONNECTED     14986    -                    
unix  3      [ ]         DGRAM                    7862     -                    
unix  3      [ ]         STREAM     CONNECTED     14941    -                    
unix  3      [ ]         STREAM     CONNECTED     14935    -                    
unix  3      [ ]         STREAM     CONNECTED     399021   -                    
unix  3      [ ]         STREAM     CONNECTED     14983    -                    
unix  3      [ ]         STREAM     CONNECTED     14966    -                    
unix  3      [ ]         STREAM     CONNECTED     14962    -                    
unix  3      [ ]         STREAM     CONNECTED     14932    -                    
unix  2      [ ]         DGRAM                    9392     -                    
unix  3      [ ]         STREAM     CONNECTED     11153    -                    

------ Message Queues --------
key        msqid      owner      perms      used-bytes   messages    

------ Shared Memory Segments --------
key        shmid      owner      perms      bytes      nattch     status      

------ Semaphore Arrays --------
key        semid      owner      perms      nsems
DONE
============= TEST ==================
[ENV START] local
STARTED
[TESTCASE] cubrid-testcases-private/longcase/shell/1hour/bug_bts_9382/cases/bug_bts_9382.sh EnvId=local [NOK]
[ENV STOP] local
============= PRINT SUMMARY ==================
Test Category:shell
Total Case:1
Total Execution Case:1
Total Success Case:0
Total Fail Case:1
Total Skip Case:0

TEST COMPLETE
[SHELL] TEST END (Tue Sep 17 16:46:41 KST 2019)
[SHELL] ELAPSE TIME: 4215 seconds
```
## 2.5 Examine test results

# 3. Deploy Regression Test Environment
In this section, we will introduce how to deploy the Shell_long Regression Test Environment.  
## 3.1 Test Machines
For current daily regression test, there are one controller node and five test nodes.      
**Controller node** : This node listens to test messages and starts a test when there is a test message.    
**Test node** : CUBRID server is deployed on this node, we execute test cases on it.     

Information about each test machines.    

Description|User Name|IP|Hostname|Tools to deploy
--|--|--|--|--
Controller node|rqgcontroller|192.168.1.99|func24|CTP
Test node|shell_long|192.168.1.99|func24|CTP,cubrid-testcases-private
Test node|shell_long|192.168.1.100|func25|CTP,cubrid-testcases-private
Test node|shell_long|192.168.1.101|func26|CTP,cubrid-testcases-private
Test node|shell_long|192.168.1.102|func27|CTP,cubrid-testcases-private
Test node|shell_long|192.168.1.103|func28|CTP,cubrid-testcases-private
## 3.2	Deploy Test Environment
### On Controller node  
1. Install CTP        
Please follow [the guide to install CTP](ctp_install_guide.md#3-install-ctp-as-regression-test-platform). Then create shell_long test configuration.
File `~/CTP/conf/shell_template_for_shell_long.conf`:   
 ```
$ cat shell_template_for_shell_long.conf 
default.cubrid.cubrid_port_id=1568
default.broker1.BROKER_PORT=30090
default.broker1.APPL_SERVER_SHM_ID=30090
default.broker2.BROKER_PORT=33091
default.broker2.APPL_SERVER_SHM_ID=33091
default.ha.ha_port_id=59909

env.99.ssh.host=192.168.1.99
env.99.ssh.port=22
env.99.ssh.user=shell_long
env.99.ssh.pwd=******

env.100.ssh.host=192.168.1.100
env.100.ssh.port=22
env.100.ssh.user=shell_long
env.100.ssh.pwd=******

env.101.ssh.host=192.168.1.101
env.101.ssh.port=22
env.101.ssh.user=shell_long
env.101.ssh.pwd=******

env.102.ssh.host=192.168.1.102
env.102.ssh.port=22
env.102.ssh.user=shell_long
env.102.ssh.pwd=******

env.103.ssh.host=192.168.1.103
env.103.ssh.port=22
env.103.ssh.user=shell_long
env.103.ssh.pwd=******


scenario=${HOME}/cubrid-testcases-private/longcase/shell
test_continue_yn=false
cubrid_download_url=http://127.0.0.1/REPO_ROOT/store_02/10.1.0.6876-f9026f8/drop/CUBRID-10.1.0.6876-f9026f8-Linux.x86_64.sh
testcase_exclude_from_file=${HOME}/cubrid-testcases-private/longcase/shell/config/daily_regression_test_excluded_list_linux.conf
testcase_update_yn=true
testcase_git_branch=develop
#testcase_timeout_in_secs=604800
test_platform=linux
test_category=shell_long
testcase_exclude_by_macro=LINUX_NOT_SUPPORTED
testcase_retry_num=0
delete_testcase_after_each_execution_yn=false
enable_check_disk_space_yn=true

feedback_type=database
feedback_notice_qahome_url=http://192.168.1.86:8080/qaresult/shellImportAction.nhn?main_id=<MAINID>


owner_email=Orchid<lanlan.zhan@navercorp.com>
cc_email=CUBRIDQA<dl_cubridqa_bj_internal@navercorp.com>

git_user=cubridqa
git_email=dl_cubridqa_bj_internal@navercorp.com
git_pwd=******

feedback_db_host=192.168.1.86
feedback_db_port=33080
feedback_db_name=qaresu
feedback_db_user=dba
feedback_db_pwd=
```
>Note: when you need to test shell_long_debug, copy `~/CTP/conf/shell_template_for_shell_long.conf` as `~/CTP/conf/shell_template_for_shell_long_debug.conf` 

2. Create quick start script     
File `start_test.sh` 
```bash
 cat ~/start_test.sh

# If only need to listen the shell_long test message
# nohup start_consumer.sh -q QUEUE_CUBRID_QA_SHELL_LONG_LINUX -exec run_shell &

# We use one controllar to listening shell_heavy, shell_long, and RQG test messages in dailyqa.
nohup start_consumer.sh -q QUEUE_CUBRID_QA_SHELL_HEAVY_LINUX,QUEUE_CUBRID_QA_RQG,QUEUE_CUBRID_QA_SHELL_LONG_LINUX -exec run_shell,run_shell,run_shell &   
```
 3. Configure .bash_profile      
 ```
export DEFAULT_BRANCH_NAME=develop
export CTP_HOME=$HOME/CTP
export CTP_BRANCH_NAME=develop
export CTP_SKIP_UPDATE=0
export init_path=$HOME/CTP/shell/init_path

ulimit -c unlimited
export LC_ALL=en_US

. $HOME/.cubrid.sh
export PATH=$CTP_HOME/bin:$CTP_HOME/common/script:$JAVA_HOME/bin:/usr/local/bin:/bin:/usr/bin:$PATH
```
### On Test nodes
1. Install CTP    
Please follow [the guide to install CTP](ctp_install_guide.md#3-install-ctp-as-regression-test-platform).
2. Check out test cases   
```
cd ~
git clone https://github.com/CUBRID/cubrid-testcases-private.git 
cd ~/cubrid-testcases-private/
git checkout develop
```
3. Install CUBRID.    

4. Add following settings to ~/.bash_profile and source it.
```bash
$ cat .bash_profile
# .bash_profile

# Get the aliases and functions
if [ -f ~/.bashrc ]; then
        . ~/.bashrc
fi

# User specific environment and startup programs

PATH=$PATH:$HOME/.local/bin:$HOME/bin

export PATH

export LC_ALL=en_US

export CTP_HOME=$HOME/CTP
export LCOV_HOME=/usr/local/cubridqa/lcov-1.11

export init_path=$HOME/CTP/shell/init_path

export GCOV_PREFIX=$HOME
export GCOV_PREFIX_STRIP=2

export CTP_BRANCH_NAME="develop"
export CTP_SKIP_UPDATE="0"

export PATH=$CTP_HOME/bin:$CTP_HOME/common/script:$CUBRID/bin:$LCOV_HOME/bin:$JAVA_HOME/bin:$PATH:/usr/local/sbin:/usr/sbin

ulimit -c unlimited

#-------------------------------------------------------------------------------
# set CUBRID environment variables
#-------------------------------------------------------------------------------
. ~/.cubrid.sh
ulimit -c unlimited
```
# 4. Regression Test Sustaining
We perform shell_long test twice a week (actually it is controlled through crontab) and perform code coverage test for monthly.  
Crontab task for shell_long test is as below:
```
#######################################################################
job_shell_long.service=ON
job_shell_long.crontab=* 0 11 ? * SUN,THU
job_shell_long.listenfile=CUBRID-{1}-linux.x86_64.sh
job_shell_long.acceptversions=10.0.*.0~8999,10.1.*,10.2.*
job_shell_long.package_bits=64
job_shell_long.package_type=general

job_shell_long.test.1.scenario=shell_long
job_shell_long.test.1.queue=QUEUE_CUBRID_QA_SHELL_LONG_LINUX

#######################################################################
```
## 4.1 Daily regression test
When the build server has a new build and meet the conditions of the crontab task, the shell_long regression test will be started. If there is something wrong and need to run shell_long test again, you can send a test message. 
### Start the listener
```bash
$ cd ~
$ sh start_test.sh &
$ tail -f nohup.out
```
### Send a test message
Login `message@192.168.1.91` and send test message.  
By default, it will find the `~/CTP/conf/shell_template_for_shell_long.conf` to execute. Otherwise it will find the `~/CTP/conf/shell_template.conf`   
```bash
sender.sh QUEUE_CUBRID_QA_SHELL_LONG_LINUX http://192.168.1.91:8080/REPO_ROOT/store_01/10.2.0.8268-6e0edf3/drop/CUBRID-10.2.0.8268-6e0edf3-Linux.x86_64.sh shell_long default
```
### Verify test result 
* ### Check whether there are results
	Open [QA homepage](http://qahome.cubrid.org/qaresult/index.nhn), then navigate as below to find shell_long test result.   
	![verify_func_tab](./media/image1.png)    
	If some test shows 'NO RESULT', we need investigate reasons and resolve it.
* ### Both Test Rate and Verified Rate should be 100%
	In above picture, the figures with red color mean number of failures. Click it to open verification page. Then follow the same way as [shell test's](shell_guide.md#41-verify-regression-test-results) to verify all failures. Both Test Rate and Verified Rate should be 100%.
   
## 4.2 Code coverage test  
Code coverage test starts on the last Sunday of each month.      
You can find the setting from http://qahome.cubrid.org/qaresult/job/job.conf     
```
#######################################################################
job_codecoverage.service=ON
job_codecoverage.crontab=0 1 10 ? * 7L
job_codecoverage.listenfile=cubrid-{1}.tar.gz
job_codecoverage.acceptversions=10.2.*
job_codecoverage.package_bits=64
job_codecoverage.package_type=general

job_codecoverage.test.1.scenario=gcov_package
job_codecoverage.test.1.queue=QUEUE_CUBRID_QA_CODE_COVERAGE
#######################################################################
job_coverage_test.service=ON
job_coverage_test.crontab=0/7 * * * * ?
job_coverage_test.listenfile=CUBRID-{1}-gcov-linux.x86_64.tar.gz
job_coverage_test.listenfile.1=cubrid-{1}-gcov-src-linux.x86_64.tar.gz
job_coverage_test.acceptversions=10.2.*
job_coverage_test.package_bits=64
job_coverage_test.package_type=coverage

job_coverage_test.test.15.scenario=shell_long
job_coverage_test.test.15.queue=QUEUE_CUBRID_QA_SHELL_LONG_LINUX
```
### Send code coverage testing message    
Login `message@192.168.1.91`, using the `sender_code_coverage_testing_message.sh` script to send a code coverate test message.   
>
```bash
$ cd ~/manual
$ sh sender_code_coverage_testing_message.sh
Usage: sh  sender_code_coverage_testing_message queue url1 url2 category
``` 
Example to send code coverage test message:  
```bash
$ cd ~/manual
$ sh sender_code_coverage_testing_message.sh QUEUE_CUBRID_QA_SHELL_LONG_LINUX http://192.168.1.91:8080/REPO_ROOT/store_01/10.2.0.8270-c897055/drop/CUBRID-10.2.0.8270-c897055-gcov-Linux.x86_64.tar.gz http://192.168.1.91:8080/REPO_ROOT/store_01/10.2.0.8270-c897055/drop/cubrid-10.2.0.8270-c897055-gcov-src-Linux.x86_64.tar.gz shell_long    
Queue:QUEUE_CUBRID_QA_SHELL_LONG_LINUX
Build URL:http://192.168.1.91:8080/REPO_ROOT/store_01/10.2.0.8270-c897055/drop/CUBRID-10.2.0.8270-c897055-gcov-Linux.x86_64.tar.gz
Source URL:http://192.168.1.91:8080/REPO_ROOT/store_01/10.2.0.8270-c897055/drop/cubrid-10.2.0.8270-c897055-gcov-src-Linux.x86_64.tar.gz
Category:shell_long

Message: 

Message Content: Test for build 10.2.0.8270-c897055 by CUBRID QA Team, China
MSG_ID = 190705-165519-836-000001
MSG_PRIORITY = 4
BUILD_ABSOLUTE_PATH=/home/ci_build/REPO_ROOT/store_01/10.2.0.8270-c897055/drop
BUILD_BIT=0
BUILD_CREATE_TIME=1551930752000
BUILD_GENERATE_MSG_WAY=MANUAL
BUILD_ID=10.2.0.8270-c897055
BUILD_IS_FROM_GIT=1
BUILD_PACKAGE_PATTERN=CUBRID-{1}-gcov-Linux.x86_64.tar.gz
BUILD_SCENARIOS=shell_long
BUILD_SCENARIO_BRANCH_GIT=develop
BUILD_SEND_DELAY=10382567
BUILD_SEND_TIME=1562313319834
BUILD_STORE_ID=store_01
BUILD_SVN_BRANCH=RB-10.2.0
BUILD_SVN_BRANCH_NEW=RB-10.2.0
BUILD_TYPE=coverage
BUILD_URLS=http://192.168.1.91:8080/REPO_ROOT/store_01/10.2.0.8270-c897055/drop/CUBRID-10.2.0.8270-c897055-gcov-Linux.x86_64.tar.gz
BUILD_URLS_1=http://192.168.1.91:8080/REPO_ROOT/store_01/10.2.0.8270-c897055/drop/cubrid-10.2.0.8270-c897055-gcov-src-Linux.x86_64.tar.gz
BUILD_URLS_CNT=2
BUILD_URLS_KR=http://192.168.1.91:8080/REPO_ROOT/store_01/10.2.0.8270-c897055/drop/CUBRID-10.2.0.8270-c897055-gcov-Linux.x86_64.tar.gz
BUILD_URLS_KR_1=http://192.168.1.91:8080/REPO_ROOT/store_01/10.2.0.8270-c897055/drop/cubrid-10.2.0.8270-c897055-gcov-src-Linux.x86_64.tar.gz
MKEY_COVERAGE_UPLOAD_DIR=/home/codecov/cc4c/result
MKEY_COVERAGE_UPLOAD_IP=192.168.1.98
MKEY_COVERAGE_UPLOAD_PWD=******
MKEY_COVERAGE_UPLOAD_USER=codecov


Do you accept above message [Y/N]:
Y
```
queue: queue name    
url1: gcov build package url    
url2: gcov source package url    
catecory: shell_long   
### Verify code coverage testing result  
1. Go to QA homepage and find the 'code coverage' node in the left area, click the link of latest result.  
![code_cov](./media/image8.png)     

2. Click the `shell_long` link.  
![code_cov_whole](./media/image9.png)     

3. There is a coverage rate of lines. Its coverage rate of lines is usually in 40%~42%.   
![code_cov_shell_long](./media/image10.png)      

## 4.3 Report issues
Please refer to [`report issues of shell test`](shell_guide.md#43-report-issues).     
### General issue  
You can refer to http://jira.cubrid.org/browse/CBRD-21989.     
![regrssion_issue](./media/image11.png)

It is necessary to add such information: `Test Build`,`Test OS`,`Description`,`Repro Steps`,`Expected Result`,`Actual Result` and `Test Cases`.     
Sometimes we need save database files and logs to analyze this issue.      

### Crash issue
Here are examples you can refer to.   
http://jira.cubrid.org/browse/CBRD-22097  
http://jira.cubrid.org/browse/CBRD-21772  
 
We can report crash issue though tools:        
* #### Click `REPORT ISSUE FOR BELOW CRASH`
	![report_issue](./media/image14.png)   
* #### Enter jira user and password,then click `Analyze Falure`,and click `Submit To Jira`  
	![report_issue2](./media/image15.png)   

## 4.4 Maintenance
### Delete `do_not_delete_core` Directory
There are a lot of backup files when we report crash issues,once these issus have been closed,we need to delete them.
```bash
$ cd ~/do_not_delete_core/
$ ls 
10.2.0.7925-616b134_20180521-143021.tar.gz  AUTO_10.2.0.7773-e468e09_20171225_063924.tar.gz  AUTO_10.2.0.8038-b6e1d4b_20181010_152229.tar.gz  readme.txt
```
### Check `ERROR_BACKUP` Directory
When we execute shell_long test,server appears crash or fatal error,the current db and other important information will be save in ~/ERROR_BACKUP,you need check it for each build and clear them in time.
```bash
$ cd ~/ERROR_BACKUP/
$ ls
AUTO_10.2.0.8038-b6e1d4b_20181010_152229.tar.gz  AUTO_10.2.0.8254-c015eb2_20190214_005631.tar.gz               AUTO_SERVER-START_10.2.0.8254-c015eb2_20190213_023728.tar.gz
AUTO_10.2.0.8060-689ccdd_20181019_014026.tar.gz  AUTO_10.2.0.8329-51e235e_20190413_151229.tar.gz               AUTO_SERVER-START_10.2.0.8362-fbf9d84_20190526_055005.tar.gz
AUTO_10.2.0.8060-689ccdd_20181019_040434.tar.gz  AUTO_10.2.0.8349-bb21e2d_20190428_190725.tar.gz               AUTO_SERVER-START_10.2.0.8362-fbf9d84_20190526_055233.tar.gz
AUTO_10.2.0.8107-a05cfaa_20181028_053656.tar.gz  AUTO_10.2.0.8362-fbf9d84_20190526_060734.tar.gz
AUTO_10.2.0.8254-c015eb2_20190213_025432.tar.gz  AUTO_SERVER-START_10.2.0.8254-c015eb2_20190213_020024.tar.gz
```  
# 5.Test Case Specification 
The directory structure, naming rule, and convention rules of shell_long test case are definitely same as that of shell test cases. Please refer to [5. Shell Case Standards ](shell_guide.md#5-shell-case-standards) to write test cases.


  
    



