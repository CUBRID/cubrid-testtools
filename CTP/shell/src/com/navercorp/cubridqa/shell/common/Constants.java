/**
 * Copyright (c) 2016, Search Solution Corporation. All rights reserved.

 * 
 * Redistribution and use in source and binary forms, with or without 
 * modification, are permitted provided that the following conditions are met:
 * 
 *   * Redistributions of source code must retain the above copyright notice, 
 *     this list of conditions and the following disclaimer.
 * 
 *   * Redistributions in binary form must reproduce the above copyright 
 *     notice, this list of conditions and the following disclaimer in 
 *     the documentation and/or other materials provided with the distribution.
 * 
 *   * Neither the name of the copyright holder nor the names of its contributors may be used to endorse or promote products 
 *     derived from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, 
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE 
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, 
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR 
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE 
 * USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE. 
 */

package com.navercorp.cubridqa.shell.common;

public class Constants {
	public static final String LINE_SEPARATOR = System.getProperty("line.separator");;

	public static final int TYPE_MASTER = 1; 
	public static final int TYPE_SLAVE = 2; 
	public static final int TYPE_REPLICA = 3;
	
	public final static String FM_DATE_COREDIR="yyyyMMdd";
	
	public static final String TC_RESULT_OK = "OK";
	public static final String TC_RESULT_NOK = "NOK";
	
	public static final String RETRY_FLAG = "TRY->";
	
	public static final String RUNTIME_ROOT_LOG_DIR = "result/shell";
	public static final String CURRENT_LOG_DIR = RUNTIME_ROOT_LOG_DIR + "/" + "current_runtime_logs";
	public static final String ENV_CTP_HOME_KEY="CTP_HOME";
	
	public static final String SKIP_TYPE_NO = "0";
	public static final String SKIP_TYPE_BY_MACRO = "1";
	public static final String SKIP_TYPE_BY_TEMP = "2";
	
	public static final String WIN_KILL_PROCESS_NATIVE = createWinKillNativeScripts(false);
	public static final ShellScriptInput WIN_KILL_PROCESS = createWinKillScripts(false);
	public static final ShellScriptInput LIN_KILL_PROCESS = createLinKillScripts(false);

	public static final String WIN_KILL_PROCESS_NATIVE_LOCAL = createWinKillNativeScripts(true);
	public static final ShellScriptInput WIN_KILL_PROCESS_LOCAL = createWinKillScripts(true);
	public static final ShellScriptInput LIN_KILL_PROCESS_LOCAL = createLinKillScripts(true);
	
	public static final ShellScriptInput GET_VERSION_SCRIPT = craeteGetVersionScript();

	
	private static ShellScriptInput createWinKillScripts (boolean inLocal){
		ShellScriptInput scripts = new ShellScriptInput();
		if(inLocal == false) {
			scripts.addCommand("wmic PROCESS WHERE \\( name = \\'java.exe\\' AND NOT CommandLine LIKE \\'%service.Server%\\' \\) DELETE");
		}
		scripts.addCommand("$CUBRID/bin/cubrid.exe service stop ");
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
		if(inLocal == false) {
			scripts.addCommand("wmic PROCESS WHERE \\( name = \\'java.exe\\' AND NOT CommandLine LIKE \\'%service.Server%\\' AND NOT CommandLine LIKE '%%com.navercorp.cubridqa%%') DELETE");
		}
		scripts.addCommand("taskkill /F /IM cat.exe");
		scripts.addCommand("taskkill /F /IM ps.exe");
		scripts.addCommand("taskkill /F /IM sed.exe");
		scripts.addCommand("taskkill /F /IM awk.exe");
		return scripts;
	}
	
	private static ShellScriptInput craeteGetVersionScript()
	{
		ShellScriptInput scripts = new ShellScriptInput();
		scripts.addCommand("cubrid_rel");
		
		return scripts;
	}
	
	private static String createWinKillNativeScripts(boolean inLocal) {
		StringBuffer sb = new StringBuffer();
		if(inLocal == false) {
			sb.append("wmic PROCESS WHERE ( name = 'java.exe' AND NOT CommandLine LIKE '%%service.Server%%') DELETE").append(LINE_SEPARATOR);
		}
		sb.append("%CUBRID%/bin/cubrid service stop").append(LINE_SEPARATOR);		
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
		if(inLocal == false) {
			sb.append("taskkill /T /F /IM bash.exe").append(LINE_SEPARATOR);
			sb.append("taskkill /T /F /IM sh.exe").append(LINE_SEPARATOR);
			sb.append("wmic PROCESS WHERE ( name = 'java.exe' AND NOT CommandLine LIKE '%%service.Server%%' AND NOT CommandLine LIKE '%%com.navercorp.cubridqa%%') DELETE").append(LINE_SEPARATOR);
		}
		sb.append("tasklist").append(LINE_SEPARATOR);
		sb.append("taskkill /T /F /IM cat.exe").append(LINE_SEPARATOR);
		sb.append("taskkill /T /F /IM ps.exe").append(LINE_SEPARATOR);
		sb.append("taskkill /T /F /IM sed.exe").append(LINE_SEPARATOR);
		sb.append("taskkill /T /F /IM awk.exe").append(LINE_SEPARATOR);
		return sb.toString();
	}
	
	private static ShellScriptInput createLinKillScripts (boolean inLocal){
		ShellScriptInput scripts = new ShellScriptInput();
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
		scripts.addCommand("ctp_java_pid_list=`ps -u $USER -o pid,command| grep -v grep | grep -E 'com.navercorp.cubridqa|service.Server' | awk '{print $1}'`");
		scripts.addCommand("all_java_pid_list=`ps -u $USER -o pid,comm| grep -v grep | grep -i java | awk '{print $1}'`");
		scripts.addCommand("final_list=\"\"");
		scripts.addCommand("for x in ${all_java_pid_list};do isExistPid=`echo ${ctp_java_pid_list}|grep -w $x|wc -l`;if [ $isExistPid -eq 0];then final_list=\"${final_list} $x\" ;fi;done");
		scripts.addCommand(bothKill("echo ${final_list}"));
		scripts.addCommand(bothKill("ps -u $USER -o pid,comm| grep -v grep | grep -i sleep | awk '{print $1}'"));
		scripts.addCommand(bothKill("ps -u $USER -o pid,comm| grep -v grep | grep -i expect | awk '{print $1}'"));
		if(inLocal == false) {
			scripts.addCommand(bothKill("ps -u $USER -o pid,cmd| grep -v grep | grep -i '\\.sh' | awk '{print $1}'"));
		}
		scripts.addCommand("ps -u $USER -f");
		return scripts;
	}
	
	private static String bothKill(String k) {
		return k + " | xargs -i kill -9 {} " + ScriptInput.LINE_SEPARATOR + "kill -9 `" + k + "`";
	}
	
	public static void main(String args[]) {
		System.out.println(LIN_KILL_PROCESS);
	}
}
