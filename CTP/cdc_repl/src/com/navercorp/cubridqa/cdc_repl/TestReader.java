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
package com.navercorp.cubridqa.cdc_repl;

import java.io.File;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;

import com.navercorp.cubridqa.common.CommonUtils;
import com.navercorp.cubridqa.cdc_repl.common.Constants;

public class TestReader {

	private FileInputStream fis = null;
	private InputStreamReader reader = null;
	private LineNumberReader lineReader = null;

	private boolean isTest = false, isCheck = false, isSQL = false, isCMD = false, appendCheckPointPrefix = false, appendCheckPoint = false;

	private String hostId;
	private String statement;
	private String HC_CHECK_FOR_EACH_STATEMENT = "";
	int lineNum = 0;
	CommonReader commonReader;

	public TestReader(File file, CommonReader commonReader) throws Exception {
		this.fis = new FileInputStream(file);
		this.reader = new InputStreamReader(fis, "UTF-8");
		this.lineReader = new LineNumberReader(reader);
		this.commonReader = commonReader;

		String tempResult = "";
		String result = "";
		int i = 0;
		while (true) {
			i++;
			String strTemp = "HC_CHECK_FOR_EACH_STATEMENT_" + i;
			tempResult = commonReader.getValue(strTemp);
			if (tempResult == null) {
				break;
			}
			result = result + Constants.LINE_SEPARATOR + tempResult;
		}
		HC_CHECK_FOR_EACH_STATEMENT = result;
	}

	public String readOneStatement() throws Exception {
		this.statement = _readOneStatement();
		return this.statement;

	}

	private String _readOneStatement() throws Exception {
		String line;
		String flag;
		int p1, p2;

		while ((line = readOneRawStatement()) != null) {
			line = line.trim();
			if (line.startsWith("--")) {
				flag = CommonUtils.replace(line, " ", "").toUpperCase();
				if (flag.startsWith("--TEST:")) {
					this.isTest = true;
					this.isCheck = false;
				} else if (flag.startsWith("--CHECK:")) {
					this.isTest = false;
					this.isCheck = true;
				}
				return readOneStatement();
			} else if (line.equals("")) {
				return readOneStatement();
			} else if (line.startsWith("cmd")) {
				p1 = line.indexOf(":");
				if (p1 == -1) {
					isSQL = false;
					this.hostId = null;
					isCMD = false;
					return line;
				} else {
					isSQL = false;
					isCMD = true;
					flag = line.substring(0, p1);
					p2 = flag.indexOf("@");
					if (p2 == -1) {
						this.hostId = null;
					} else {
						this.hostId = flag.substring(p2 + 1);
					}
					return line.substring(p1 + 1);
				}
			} else {
				isSQL = true;
				this.hostId = null;
				isCMD = false;
				return line;
			}
		}
		return null;
	}

	private String readOneRawStatement() throws IOException {
		StringBuffer lines = new StringBuffer();
		String line;
		int count = 0;
		String result;
		while ((line = this.lineReader.readLine()) != null) {
			lineNum++;
			count++;
			line = CommonUtils.rightTrim(line);
			if (line.endsWith("\\")) {
				lines.append(line.substring(0, line.length() - 1)).append(Constants.LINE_SEPARATOR);

			} else {
				lines.append(line);
				break;
			}
		}
		if (count == 0) {
			return null;
		} else {
			result = lines.toString().trim();
			if (result.startsWith("@")) {

				// added by cn15209 2012.08.07
				String key = result.substring(1);
				if ("HC_CHECK_FOR_EACH_STATEMENT".equals(key)) {
					result = HC_CHECK_FOR_EACH_STATEMENT;
					if ("".equals(result) || result == null) {
						return readOneRawStatement();
					}
				} else {
					result = commonReader.getValue(key);
					if (result == null) {
						return readOneRawStatement();
					}
				}
			}
			return result;
		}
	}

	@Deprecated
	private String getNextLine() throws IOException {
		String result;

		if (appendCheckPoint) {
			isTest = false;
			isCheck = true;
			if (appendCheckPointPrefix) {
				result = "--check:";
				appendCheckPointPrefix = false;
			} else {
				result = "@HC_CHECK_FOR_EACH_STATEMENT";
				appendCheckPoint = false;

			}
		} else {
			isTest = true;
			isCheck = false;
			result = this.lineReader.readLine();
			if (result != null) {
				if (result.endsWith("\\")) {
					appendCheckPoint = false;

				} else {
					appendCheckPoint = true;
					appendCheckPointPrefix = true;
				}
			}
		}
		return result;
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

	public String statement() {
		return "[isTest=" + isTest + ",isCheck=" + isCheck + ",isSQL=" + isSQL + ",isCMD=" + isCMD + ", hostId=" + hostId + "]\t\t" + statement;

	}

	public boolean isTest() {
		return isTest;
	}

	public void setTest(boolean isTest) {
		this.isTest = isTest;
	}

	public boolean isCheck() {
		return isCheck;
	}

	public void setCheck(boolean isCheck) {
		this.isCheck = isCheck;
	}

	public boolean isSQL() {
		return isSQL;
	}

	public void setSQL(boolean isSQL) {
		this.isSQL = isSQL;
	}

	public boolean isCMD() {
		return isCMD;
	}

	public void setCMD(boolean isCMD) {
		this.isCMD = isCMD;
	}

	public String getHostId() {
		return hostId;
	}

	public void setHostId(String hostId) {
		this.hostId = hostId;
	}
}
