package com.navercorp.cubridqa.isolation;

import com.navercorp.cubridqa.shell.common.ShellInput;

public class IsolationShellInput extends ShellInput {

	private final static String INIT_SCRIPTS;
	static {
		StringBuffer scripts = new StringBuffer();
		scripts.append("if [ \"${CTP_HOME}\" == \"\" ]; then").append('\n');
		scripts.append("  if which ctp.sh >/dev/null 2>&1 ; then").append('\n');
		scripts.append("    CTP_HOME=$(dirname $(readlink -f `which ctp.sh`))/..").append('\n');
		scripts.append("  elif [ ! \"${init_path}\" == \"\" ]; then").append('\n');
		scripts.append("    CTP_HOME=${init_path}/../..").append('\n');
		scripts.append("  fi").append('\n');
		scripts.append("fi").append('\n');
		scripts.append("export CTP_HOME=$(cd ${CTP_HOME}; pwd)").append('\n');
		scripts.append("export ctlpath=${CTP_HOME}/isolation/ctltool").append('\n');
		scripts.append("export PATH=${CTP_HOME}/bin:${CTP_HOME}/common/script:${ctlpath}:$PATH").append('\n');
		INIT_SCRIPTS = scripts.toString();
	}

	public IsolationShellInput() {
		super(INIT_SCRIPTS);
	}

	public IsolationShellInput(String scripts) {

		super(INIT_SCRIPTS + scripts);
	}

}
