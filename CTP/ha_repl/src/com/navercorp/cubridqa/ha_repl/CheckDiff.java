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

package com.navercorp.cubridqa.ha_repl;

import com.navercorp.cubridqa.common.CommonUtils;
import com.navercorp.cubridqa.common.LocalInvoker;

public class CheckDiff {
	public int check(String filePath, String masterName, String slaveOrReplicaName, String fileSuffix) {
		String masterFile = filePath + "." + masterName + ".dump";
		String slaveFile = filePath + "." + slaveOrReplicaName + ".dump";
		String master_slaveOrReplicaDiffFile = filePath + "." + masterName + ".slave1." + fileSuffix;
		String master_slaveOrReplicaDiffFileTemp = master_slaveOrReplicaDiffFile + ".temp";
		
		StringBuilder scripts = new StringBuilder();
		scripts.append("diff " + masterFile + " " + slaveFile + " >" + master_slaveOrReplicaDiffFileTemp + " 2>&1\n");
		scripts.append("if [ $? -eq 0 ]; then\n");
		scripts.append("    echo PASS \n");
		scripts.append("else\n");
		scripts.append("    if [ -f "+ master_slaveOrReplicaDiffFile +" ]; then");
		scripts.append("        diff " + master_slaveOrReplicaDiffFile + " " + master_slaveOrReplicaDiffFileTemp + "\n");
		scripts.append("        if [ $? -eq 0 ]; then\n");
		scripts.append("            echo PASS\n");
		scripts.append("        else\n");
		scripts.append("            echo FAIL\n");
		scripts.append("        fi\n");
		scripts.append("    else\n");
		scripts.append("        echo FAIL\n");
		scripts.append("    fi\n");
		scripts.append("fi\n");
		int shellType = CommonUtils.getShellType(false);
		String result = LocalInvoker.exec(scripts.toString(), shellType, false);
		return result.indexOf("PASS") != -1 ? 0: 1;
	}
}
