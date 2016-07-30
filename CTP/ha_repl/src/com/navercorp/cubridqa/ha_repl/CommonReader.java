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

import java.io.File;

import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.util.Properties;

import com.navercorp.cubridqa.ha_repl.common.CommonUtils;
import com.navercorp.cubridqa.ha_repl.common.Constants;

public class CommonReader {

	private FileInputStream fis = null;
	private InputStreamReader reader = null;
	private LineNumberReader lineReader = null;

	Properties props;

	public CommonReader(String filename) throws Exception {
		File file = new File(filename);
		this.fis = new FileInputStream(file);
		this.reader = new InputStreamReader(fis, "UTF-8");
		this.lineReader = new LineNumberReader(reader);
		this.props = new Properties();

		String statement;

		while (true) {
			statement = readOneRawStatement();
			if (statement == null)
				break;
		}
		reader.close();
	}

	private String readOneRawStatement() throws Exception {
		StringBuffer lines = new StringBuffer();
		String line, statement, key, value;
		int count = 0;
		while ((line = this.lineReader.readLine()) != null) {
			count++;
			line = CommonUtils.rightTrim(line);
			if (line.endsWith("\\")) {
				lines.append(line.substring(0, line.length() - 1)).append(Constants.LINE_SEPARATOR);
			} else {
				lines.append(line);
				break;
			}
		}
		line = null;
		if (count == 0) {
			return null;
		} else {
			statement = lines.toString();
			int p = statement.indexOf("=");
			if (p == -1)
				return readOneRawStatement();
			key = statement.substring(0, p);
			value = statement.substring(p + 1);
			this.props.setProperty(key, value);
			return statement;
		}
	}

	public void close() {
		try {
			if (lineReader != null) {
				lineReader.close();
			}
		} catch (Exception e) {
		}
		try {
			if (reader != null) {
				reader.close();
			}
		} catch (Exception e) {
		}

		try {
			if (fis != null) {
				fis.close();
			}
		} catch (Exception e) {
		}

	}

	public String getValue(String key) {
		return this.props.getProperty(key);
	}
}
