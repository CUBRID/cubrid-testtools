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

public class ScriptInput {

	StringBuilder cmds;
	boolean isPureWindows = false;

	public static final String LINE_SEPARATOR = "\n";
	public static final String LINE_SEPARATOR_WIN = "\n\r";

	public static final String START_FLAG_MOCK = "ALL_${NOTEXIST}STARTED";
	public static final String COMP_FLAG_MOCK = "ALL_${NOTEXIST}COMPLETED";

	public static final String START_FLAG_MOCK_WIN = "ALL_%NOTEXIST%STARTED";
	public static final String COMP_FLAG_MOCK_WIN = "ALL_%NOTEXIST%COMPLETED";

	public static final String START_FLAG = "ALL_STARTED";
	public static final String COMP_FLAG = "ALL_COMPLETED";

	public ScriptInput() {
		this(null);
	}

	public ScriptInput(String cmd) {
		this(cmd, false);
	}

	public ScriptInput(String cmd, boolean isPureWindows) {
		this.isPureWindows = isPureWindows;
		this.cmds = new StringBuilder();

		if (cmd != null) {
			addCommand(cmd);
		}
	}

	public void addCommand(String cmd) {
		cmds.append(cmd).append(isPureWindows ? LINE_SEPARATOR_WIN : LINE_SEPARATOR);
	}

	public String getCommands() {
		if (isPureWindows) {
			return "echo " + START_FLAG_MOCK_WIN + LINE_SEPARATOR_WIN + cmds.toString() + "@ECHO OFF" + LINE_SEPARATOR_WIN + "echo " + COMP_FLAG_MOCK_WIN + LINE_SEPARATOR_WIN;
		} else {
			return "pri_ctp_home=$CTP_HOME; if  [ -f ~/.bash_profile ]; then . ~/.bash_profile; fi; if [ \"$pri_ctp_home\" != \"\" ];then export CTP_HOME=${pri_ctp_home}; fi; " + LINE_SEPARATOR
					+ "echo " + START_FLAG_MOCK + LINE_SEPARATOR + cmds.toString() + "echo " + COMP_FLAG_MOCK + LINE_SEPARATOR;
		}
	}

	@Override
	public String toString() {
		return getCommands();
	}
}
