@echo off
@title start rmi service 

cd %~dp0

SET init_path=%~dp0init_path

echo init_path=%init_path%
echo PATH=%PATH%
echo ====================================
 
:while_loop
echo RMI START Time: %date% %time% >>.\run.log
"%JAVA_HOME%\bin\java" -cp lib/cubridqa-shell.jar com.navercorp.cubridqa.shell.service.Server
goto :while_loop

