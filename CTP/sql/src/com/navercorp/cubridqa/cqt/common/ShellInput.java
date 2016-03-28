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
package com.navercorp.cubridqa.cqt.common;

public class ShellInput {

	StringBuilder cmds;

	public static final String LINE_SEPARATOR = "\n";

	public static final String START_FLAG = "'CUBRID'_'COMMON'_'STARTED'";
	public static final String COMP_FLAG = "'CUBRID'_'COMMON'_'COMPLETED'";

	public static final String START_FLAG_RESULT = "CUBRID_COMMON_STARTED";
	public static final String COMP_FLAG_RESULT = "CUBRID_COMMON_COMPLETED";

	public ShellInput() {
		this(null, null);
	}

	public ShellInput(String cmd) {
		this(cmd, null);
	}

	public ShellInput(String cmd, String scriptToRun) {
		cmds = new StringBuilder();
		if (scriptToRun == null) {
			scriptToRun = ". ~/.bash_profile";
		}
		addCommand(scriptToRun);
		addCommand("echo " + START_FLAG);
		if (cmd != null)
			addCommand(cmd);
	}

	public void addCommand(String cmd) {
		cmds.append(cmd).append(LINE_SEPARATOR);
	}

	public String getCommands() {
		return cmds.toString() + "echo " + COMP_FLAG + LINE_SEPARATOR;
	}

	public String toString() {
		return getCommands();
	}
}
