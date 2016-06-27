@echo off
@title start rmi service 

if exist bin (
rd /s /q bin
)
mkdir bin

SET CP=bin\;lib\commons-dbcp-1.4.jar;lib\commons-logging-1.1.jar;lib\commons-logging-api-1.0.4.jar;lib\commons-pool-1.6.jar;lib\httpclient-4.2.5.jar;lib\httpcore-4.2.4.jar;lib\jsch-20090701.jar
"%JAVA_HOME%\bin\javac" -cp %CP% src\com\nhncorp\cubrid\service\*.java src\com\nhncorp\cubrid\common\*.java -d bin

:while_loop
echo RMI START Time: %date% %time% >>.\run.log
"%JAVA_HOME%\bin\java" -cp %CP% com.nhncorp.cubrid.service.Server
goto :while_loop



