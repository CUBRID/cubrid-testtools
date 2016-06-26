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

package com.navercorp.cubridqa.common.grepo.service;

import java.io.File;


import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Properties;

import com.navercorp.cubridqa.common.CommonUtils;
import com.navercorp.cubridqa.common.Constants;

public class PackageInf {

	private static PackageInf instance = new PackageInf();

	File file;
	PrintWriter out = null;

	public static PackageInf getInstance() {
		return instance;
	}

	private PackageInf() {
		Properties props = Constants.COMMON_DAILYQA_CONF;

		this.file = new File(props.getProperty("grepo_srv_data_root") + File.separator + "package.inf");

		if (!file.exists()) {
			file.getParentFile().mkdirs();
		}
	}

	public void println(String... line) {
		if (line == null || line.length == 0)
			return;
		try {
			out = new PrintWriter(new FileOutputStream(file, true));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		StringBuffer cont = new StringBuffer();
		try {
			for (String item : line) {
				cont.append(item.trim() + "\t");
			}
			out.write(cont.toString() + "\n");
			out.flush();
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			out.close();
		}
	}
}