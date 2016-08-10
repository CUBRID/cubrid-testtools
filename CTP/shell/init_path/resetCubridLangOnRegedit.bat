cubrid service stop
reg delete "HKEY_LOCAL_MACHINE\SYSTEM\ControlSet001\Control\Session Manager\Environment" /v CUBRID_LANG /f
reg    add "HKEY_LOCAL_MACHINE\SYSTEM\ControlSet001\Control\Session Manager\Environment" /v CUBRID_LANG /d %1 /f
cubrid service start