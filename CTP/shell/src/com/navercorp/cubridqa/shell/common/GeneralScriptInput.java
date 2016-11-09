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

public class GeneralScriptInput extends ScriptInput {

	public static final String INIT_SCRIPT;
	static {
		StringBuffer scripts = new StringBuffer();
		scripts.append("if [ \"${CTP_HOME}\" == \"\" ]; then").append('\n');
		scripts.append("  if which ctp.sh >/dev/null 2>&1 ; then").append('\n');
		scripts.append("    CTP_HOME=$(dirname $(readlink -f `which ctp.sh`))/..").append('\n');
		scripts.append("  elif [ ! \"${init_path}\" == \"\" ]; then").append('\n');
		scripts.append("    CTP_HOME=${init_path}/../..").append('\n');
		scripts.append("  fi").append('\n');
		scripts.append("fi").append('\n');
		scripts.append("ulimit -c unlimited").append('\n');
		scripts.append("if [ \"${CTP_HOME}\" != \"\" ]; then ").append('\n');
		scripts.append("  export CTP_HOME=$(cd ${CTP_HOME}; pwd)").append('\n');
		scripts.append("  export PATH=${CTP_HOME}/bin:${CTP_HOME}/common/script:$PATH").append('\n');
		scripts.append("fi").append('\n');
		scripts.append("cd").append('\n');
		INIT_SCRIPT = scripts.toString();
	}

	public GeneralScriptInput() {
		super(INIT_SCRIPT);
	}

	public GeneralScriptInput(String scripts) {
		super(INIT_SCRIPT + scripts);
	}

}
