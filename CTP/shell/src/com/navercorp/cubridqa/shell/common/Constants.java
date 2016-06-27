package com.navercorp.cubridqa.shell.common;

public class Constants {
	public static final String LINE_SEPARATOR = System.getProperty("line.separator");;

	public static final int TYPE_MASTER = 1; 
	public static final int TYPE_SLAVE = 2; 
	public static final int TYPE_REPLICA = 3;
	
	public final static String FM_DATE_COREDIR="yyyyMMdd";
	
	public static final String DIR_CONF = "./conf";
	public static final String DIR_LOG_ROOT = "./log";
	
	public static final String TC_RESULT_OK = "OK";
	public static final String TC_RESULT_NOK = "NOK";
	
	public static final String RETRY_FLAG = "Retry_Count";

	public static final String SKIP_TYPE_NO = "0";
	public static final String SKIP_TYPE_BY_MACRO = "1";
	public static final String SKIP_TYPE_BY_TEMP = "2";
	
	public static final String WIN_KILL_PROCESS_NATIVE = createWinKillNativeScripts();
	public static final ShellInput WIN_KILL_PROCESS = createWinKillScripts();
	public static final ShellInput LIN_KILL_PROCESS = createLinKillScripts();

	
	private static ShellInput createWinKillScripts (){
		WinShellInput scripts = new WinShellInput();
		scripts.addCommand("wmic PROCESS WHERE \\( name = \\'java.exe\\' AND NOT CommandLine LIKE \\'%com.nhncorp.cubrid.service.Server%\\' \\) DELETE");
		scripts.addCommand("$CUBRID/bin/cubrid.exe service stop ");
		scripts.addCommand("cubrid service stop");
		scripts.addCommand("tasklist |  grep cubridservice | awk '{print $2}' | xargs -i taskkill '/T' '/F' '/PID' {} ");
		scripts.addCommand("tasklist |  grep cub_master | awk '{print $2}' | xargs -i taskkill '/T' '/F' '/PID' {} ");
		scripts.addCommand("tasklist |  grep cub_server | awk '{print $2}' | xargs -i taskkill '/T' '/F' '/PID' {} ");
		scripts.addCommand("tasklist |  grep cub_broker | awk '{print $2}' | xargs -i taskkill '/T' '/F' '/PID' {} ");
		scripts.addCommand("tasklist |  grep cub | awk '{print $2}' | xargs -i echo {} | xargs -i taskkill '/T' '/F' '/PID' {} ");
		scripts.addCommand("tasklist |  grep cub | awk '{print $2}' | xargs -i taskkill '/T' '/F' '/PID' {} ");
		scripts.addCommand("tasklist |  grep broker | awk '{print $2}' | xargs -i taskkill '/T' '/F' '/PID' {} ");
		scripts.addCommand("tasklist |  grep shard | awk '{print $2}' | xargs -i taskkill '/T' '/F' '/PID' {} ");
		scripts.addCommand("tasklist |  grep convert_password | awk '{print $2}' | xargs -i taskkill '/T' '/F' '/PID' {} ");
		scripts.addCommand("tasklist |  grep csql | awk '{print $2}' | xargs -i taskkill '/T' '/F' '/PID' {} ");
		scripts.addCommand("tasklist |  grep ctrlservice | awk '{print $2}' | xargs -i taskkill '/T' '/F' '/PID' {} ");
		scripts.addCommand("tasklist |  grep -i CUBRID | awk '{print $2}' | xargs -i taskkill '/T' '/F' '/PID' {} ");
		scripts.addCommand("tasklist |  grep loadjava | awk '{print $2}' | xargs -i taskkill '/T' '/F' '/PID' {} ");
		scripts.addCommand("tasklist |  grep migrate | awk '{print $2}' | xargs -i taskkill '/T' '/F' '/PID' {} ");
		scripts.addCommand("tasklist |  grep setupmanage | awk '{print $2}' | xargs -i taskkill '/T' '/F' '/PID' {} ");
		scripts.addCommand("tasklist |  grep make_locale | awk '{print $2}' | xargs -i taskkill '/T' '/F' '/PID' {} ");
		scripts.addCommand("taskkill /F /IM cub_master.exe");
		scripts.addCommand("taskkill /F /IM cub_server.exe");
		scripts.addCommand("taskkill /F /IM cub_cas.exe");
		scripts.addCommand("taskkill /F /IM cub_broker.exe");
		scripts.addCommand("wmic PROCESS WHERE \\( name = \\'java.exe\\' AND NOT CommandLine LIKE \\'%com.nhncorp.cubrid.service.Server%\\' \\) DELETE");
                scripts.addCommand("taskkill /F /IM cat.exe");
                scripts.addCommand("taskkill /F /IM ps.exe");
                scripts.addCommand("taskkill /F /IM sed.exe");
                scripts.addCommand("taskkill /F /IM awk.exe");
		return scripts;
	}
	
	private static String createWinKillNativeScripts() {
		StringBuffer sb = new StringBuffer();
		sb.append("wmic PROCESS WHERE ( name = 'java.exe' AND NOT CommandLine LIKE '%%com.nhncorp.cubrid.service.Server%%' ) DELETE").append(LINE_SEPARATOR);		
		sb.append("cubrid service stop").append(LINE_SEPARATOR);		
		sb.append("taskkill /T /F /IM broker_changer.exe").append(LINE_SEPARATOR);
		sb.append("taskkill /T /F /IM broker_log_converter.exe").append(LINE_SEPARATOR);
		sb.append("taskkill /T /F /IM broker_log_runner.exe").append(LINE_SEPARATOR);
		sb.append("taskkill /T /F /IM broker_log_top.exe").append(LINE_SEPARATOR);
		sb.append("taskkill /T /F /IM broker_monitor.exe").append(LINE_SEPARATOR);
		sb.append("taskkill /T /F /IM broker_tester.exe").append(LINE_SEPARATOR);
		sb.append("taskkill /T /F /IM cm_admin.exe").append(LINE_SEPARATOR);
		sb.append("taskkill /T /F /IM convert_password.exe").append(LINE_SEPARATOR);
		sb.append("taskkill /T /F /IM csql.exe").append(LINE_SEPARATOR);
		sb.append("taskkill /T /F /IM ctrlservice.exe").append(LINE_SEPARATOR);
		sb.append("taskkill /T /F /IM cubrid.exe").append(LINE_SEPARATOR);
		sb.append("taskkill /T /F /IM cubridservice.exe").append(LINE_SEPARATOR);
		sb.append("taskkill /T /F /IM cubrid_broker.exe").append(LINE_SEPARATOR);
		sb.append("taskkill /T /F /IM cubrid_esql.exe").append(LINE_SEPARATOR);
		sb.append("taskkill /T /F /IM cubrid_rel.exe").append(LINE_SEPARATOR);
		sb.append("taskkill /T /F /IM cubrid_replay.exe").append(LINE_SEPARATOR);
		sb.append("taskkill /T /F /IM CUBRID_Service_Tray.exe").append(LINE_SEPARATOR);
		sb.append("taskkill /T /F /IM cub_admin.exe").append(LINE_SEPARATOR);
		sb.append("taskkill /T /F /IM cub_auto.exe").append(LINE_SEPARATOR);
		sb.append("taskkill /T /F /IM cub_broker.exe").append(LINE_SEPARATOR);
		sb.append("taskkill /T /F /IM cub_cas.exe").append(LINE_SEPARATOR);
		sb.append("taskkill /T /F /IM cub_cmserver.exe").append(LINE_SEPARATOR);
		sb.append("taskkill /T /F /IM cub_commdb.exe").append(LINE_SEPARATOR);
		sb.append("taskkill /T /F /IM cub_job.exe").append(LINE_SEPARATOR);
		sb.append("taskkill /T /F /IM cub_jobfile.exe").append(LINE_SEPARATOR);
		sb.append("taskkill /T /F /IM cub_jobsa.exe").append(LINE_SEPARATOR);
		sb.append("taskkill /T /F /IM cub_js.exe").append(LINE_SEPARATOR);
		sb.append("taskkill /T /F /IM cub_master.exe").append(LINE_SEPARATOR);
		sb.append("taskkill /T /F /IM cub_proxy.exe").append(LINE_SEPARATOR);
		sb.append("taskkill /T /F /IM cub_sainfo.exe").append(LINE_SEPARATOR);
		sb.append("taskkill /T /F /IM cub_server.exe").append(LINE_SEPARATOR);
		sb.append("taskkill /T /F /IM loadjava.exe").append(LINE_SEPARATOR);
		sb.append("taskkill /T /F /IM migrate_91_to_92.exe").append(LINE_SEPARATOR);
		sb.append("taskkill /T /F /IM setupmanage.exe").append(LINE_SEPARATOR);
		sb.append("taskkill /T /F /IM make_locale.bat").append(LINE_SEPARATOR);
		sb.append("taskkill /T /F /IM bash.exe").append(LINE_SEPARATOR);
		sb.append("taskkill /T /F /IM sh.exe").append(LINE_SEPARATOR);
		sb.append("wmic PROCESS WHERE ( name = 'java.exe' AND NOT CommandLine LIKE '%%com.nhncorp.cubrid.service.Server%%' ) DELETE").append(LINE_SEPARATOR);
		sb.append("tasklist").append(LINE_SEPARATOR);
               sb.append("taskkill /T /F /IM cat.exe").append(LINE_SEPARATOR);
               sb.append("taskkill /T /F /IM ps.exe").append(LINE_SEPARATOR);
               sb.append("taskkill /T /F /IM sed.exe").append(LINE_SEPARATOR);
               sb.append("taskkill /T /F /IM awk.exe").append(LINE_SEPARATOR);
		return sb.toString();
	}
	
	private static ShellInput createLinKillScripts (){
		ShellInput scripts = new ShellInput();
		scripts.addCommand("cubrid service stop");
		scripts.addCommand(bothKill("ps -u $USER -o pid,comm| grep -v grep | grep cub_admin | awk '{print $1}'"));
		scripts.addCommand(bothKill("ps -u $USER -o pid,comm| grep -v grep | grep cub_master | awk '{print $1}'"));
		scripts.addCommand(bothKill("ps -u $USER -o pid,comm| grep -v grep | grep cub_auto | awk '{print $1}'"));
		scripts.addCommand(bothKill("ps -u $USER -o pid,comm| grep -v grep | grep cub_broker| awk '{print $1}'"));
		scripts.addCommand(bothKill("ps -u $USER -o pid,comm| grep -v grep | grep cub_server | awk '{print $1}'"));
		scripts.addCommand(bothKill("ps -u $USER -o pid,comm| grep -v grep | grep cub | awk '{print $1}'"));
		scripts.addCommand(bothKill("ps -u $USER -o pid,comm| grep -v grep | grep broker | awk '{print $1}'"));
		scripts.addCommand(bothKill("ps -u $USER -o pid,comm| grep -v grep | grep cm_admin | awk '{print $1}'"));
		scripts.addCommand(bothKill("ps -u $USER -o pid,comm| grep -v grep | grep csql | awk '{print $1}'"));
		scripts.addCommand(bothKill("ps -u $USER -o pid,comm| grep -v grep | grep loadjava | awk '{print $1}'"));
		scripts.addCommand(bothKill("ps -u $USER -o pid,comm| grep -v grep | grep make_locale | awk '{print $1}'"));
		scripts.addCommand(bothKill("ps -u $USER -o pid,comm| grep -v grep | grep migrate | awk '{print $1}'"));
		scripts.addCommand(bothKill("ps -u $USER -o pid,comm| grep -v grep | grep shard | awk '{print $1}'"));
		scripts.addCommand("ipcs | grep $USER | awk '{print $2}'  | xargs -i ipcrm -m {}");
		scripts.addCommand(bothKill("ps -u $USER -o pid,comm| grep -v grep | grep -i java | awk '{print $1}'"));
		scripts.addCommand(bothKill("ps -u $USER -o pid,comm| grep -v grep | grep -i sleep | awk '{print $1}'"));
		scripts.addCommand(bothKill("ps -u $USER -o pid,comm| grep -v grep | grep -i expect | awk '{print $1}'"));
		scripts.addCommand(bothKill("ps -u $USER -o pid,cmd| grep -v grep | grep -i '\\.sh' | awk '{print $1}'"));
		scripts.addCommand("ps -u $USER -f");
		return scripts;
	}
	
	private static String bothKill(String k) {
		return k + " | xargs -i kill -9 {} " + ShellInput.LINE_SEPARATOR + "kill -9 `" + k + "`";
	}
	
	public static void main(String args[]) {
		System.out.println(WIN_KILL_PROCESS);
	}
}
