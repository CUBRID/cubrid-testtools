cubrid service stop
reg delete "HKEY_LOCAL_MACHINE\SYSTEM\ControlSet001\Control\Session Manager\Environment" /v %1 /f
cubrid service start