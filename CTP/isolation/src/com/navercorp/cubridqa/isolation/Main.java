/**
 * Copyright (c) 2016, Search Solution Corporation. All rights reserved.\


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

package com.navercorp.cubridqa.isolation;

import java.util.ArrayList;



import java.util.Properties;
import java.util.Set;
import com.navercorp.cubridqa.common.CommonUtils;
import com.navercorp.cubridqa.common.Log;

public class Main {

	public static void exec(String configFilename) throws Exception {
		
		Properties system = System.getProperties();
		system.setProperty("sun.rmi.transport.connectionTimeout", "10000000");

		Context context = new Context(configFilename);
		context.setBuildId(CommonUtils.getBuildId(context.getCubridPackageUrl()));
		context.setBuildBits(CommonUtils.getBuildBits(context.getCubridPackageUrl()));

		System.out.println("Build Id: " + context.getBuildId());
		System.out.println("Build Bits: " + context.getBuildBits());

		ArrayList<String> envList = context.getEnvList();
		System.out.println("Available Env: " + envList);

		if (context.getEnvList().size() == 0) {
			throw new Exception("Not found any environment instance to test on it.");
		}

		Log contextSnapshot = new Log(CommonUtils.concatFile(context.getCurrentLogDir(), "main_snapshot.properties"), true, false);
		Properties props = context.getProperties();
		Set set = props.keySet();		
		for (Object key : set) {
			contextSnapshot.println(key + "=" + props.getProperty((String) key));
		}
		contextSnapshot.println("AUTO_BUILD_ID=" + context.getBuildId());
		contextSnapshot.println("AUTO_BUILD_BITS=" + context.getBuildBits());
		contextSnapshot.close();

		TestFactory factory = new TestFactory(context);
		factory.execute();
	}
}
