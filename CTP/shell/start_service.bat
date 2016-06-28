@echo off
@title start rmi service 

SET CP=lib/cubridqa-shell.jar

:while_loop
echo RMI START Time: %date% %time% >>.\run.log
"%JAVA_HOME%\bin\java" -cp %CP% com.navercorp.cubridqa.shell.service.Server
goto :while_loop
