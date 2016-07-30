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
package com.navercorp.cubridqa.ha_repl.migrate;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.OutputStreamWriter;

import com.navercorp.cubridqa.ha_repl.common.CommonUtils;
import com.navercorp.cubridqa.ha_repl.common.Constants;

public class SQLFileReader {

	private FileInputStream fis = null;
	private InputStreamReader reader = null;
	private LineNumberReader lineReader = null;

	OutputStreamWriter out = null;
	boolean isDML = false;

	public SQLFileReader(File file) throws Exception {
		this.fis = new FileInputStream(file);
		this.reader = new InputStreamReader(fis, "UTF-8");
		this.lineReader = new LineNumberReader(reader);

		String outFilename = file.getAbsolutePath();
		outFilename = outFilename.substring(0, outFilename.lastIndexOf(".")) + ".test";
		File outFile = new File(outFilename);
		if (!outFile.exists()) {
			try {
				outFile.createNewFile();
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}

		try {
			out = new OutputStreamWriter(new FileOutputStream(outFile), "UTF-8");
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private int CountString(String sourcestr, String countstr) {
		if (!sourcestr.contains(countstr)) {
			return 0;
		}
		int count = 0;
		String startSubString = sourcestr;
		while (startSubString.length() > 0) {
			startSubString = startSubString.substring(startSubString.indexOf(countstr) + 1);
			count++;
			if (!startSubString.contains(countstr)) {
				break;
			}
		}
		return count;
	}

	private EditLine checkEnum(String line, EditLine editLine) {
		if (line == null || editLine == null) {
			return null;
		}
		String startEnumSubString = line.substring(line.toUpperCase().indexOf("ENUM"));
		int countLeftBracket = CountString(startEnumSubString, "(");
		int countRightBracket = CountString(startEnumSubString, ")");
		if (countLeftBracket <= countRightBracket && countRightBracket > 0) {
			String startSubString = line.substring(0, line.toUpperCase().lastIndexOf("'"));
			String endSubString = line.toUpperCase().substring(line.toUpperCase().lastIndexOf("'")).replaceFirst("\\)", ")  PRIMARY KEY");
			line = startSubString + endSubString;
			editLine.setLine(line);
			editLine.setEnumType(false);
			editLine.setCreateTableOrClass(false);
			return editLine;
		} else {
			editLine.setLine(line);
			editLine.setEnumType(true);
			editLine.setCreateTableOrClass(true);
			return editLine;
		}

	}

	// Edited by cn15209 20120627
	// add primary key
	private EditLine editLineForCreateTableOrClass(String line, boolean isCreateTableOrClass, boolean isEnumType) {
		EditLine editLine = new EditLine();
		editLine.setLine(line);
		editLine.setCreateTableOrClass(isCreateTableOrClass);
		editLine.setEnumType(isEnumType);

		if (line.toUpperCase().indexOf("PRIMARY KEY") >= 0) {
			editLine.setCreateTableOrClass(false);
			return editLine;
		}
		if (line.toUpperCase().indexOf("CREATE TABLE") >= 0 || line.toUpperCase().indexOf("CREATE CLASS") >= 0) {
			if (line.toUpperCase().indexOf("ENUM") >= 0 && line.toUpperCase().indexOf("'") >= 0) {
				return checkEnum(line, editLine);
			} else if (line.toUpperCase().indexOf(",") >= 0) {
				if (line.toUpperCase().indexOf("(") != line.toUpperCase().lastIndexOf("(")) {
					if ((line.toUpperCase().substring(line.toUpperCase().indexOf("(") + 1)).indexOf("(") > (line.toUpperCase().substring(line.toUpperCase().indexOf("(") + 1)).indexOf(",")) {
						editLine.setLine(line.replaceFirst(",", " PRIMARY KEY,"));
						editLine.setCreateTableOrClass(false);
						return editLine;
					} else if (line.toUpperCase().indexOf(")") < line.toUpperCase().indexOf(",")) {
						if (line.toUpperCase().indexOf(")") == -1) {
							editLine.setCreateTableOrClass(true);
							return editLine;
						} else {
							if (line.indexOf("(", line.indexOf("(") + 1) > line.indexOf(")")) {
								editLine.setLine(line.replaceFirst("\\)", " PRIMARY KEY ) "));
								editLine.setCreateTableOrClass(false);
								return editLine;

							} else {
								editLine.setLine(line.replaceFirst("\\)", " ) PRIMARY KEY"));
								editLine.setCreateTableOrClass(false);
								return editLine;
							}
						}
					} else {
						editLine.setLine(line.replaceFirst("\\)", ") PRIMARY KEY"));
						editLine.setCreateTableOrClass(false);
						return editLine;
					}
				} else {
					editLine.setLine(line.replaceFirst(",", " PRIMARY KEY,"));
					editLine.setCreateTableOrClass(false);
					return editLine;
				}
			}
			if (line.toUpperCase().indexOf(");") > 0) {
				editLine.setLine(line.replaceFirst("\\);", " PRIMARY KEY);"));
				editLine.setCreateTableOrClass(false);
				return editLine;
			} else if (line.toUpperCase().indexOf(";") > 0 && line.toUpperCase().indexOf("(") == -1) {
				editLine.setCreateTableOrClass(false);
				return editLine;
			} else if (line.toUpperCase().indexOf(")") > 0) {
				if (line.toUpperCase().indexOf("(") == line.toUpperCase().lastIndexOf("(")) {
					editLine.setLine(line.replaceFirst("\\)", " PRIMARY KEY)"));
					editLine.setCreateTableOrClass(false);
					return editLine;
				} else if (line.toUpperCase().indexOf(")") < line.toUpperCase().lastIndexOf(")")) {
					if (line.toUpperCase().lastIndexOf("(") < line.toUpperCase().indexOf(")")) {
						editLine.setLine(line.replaceFirst("\\)", ") PRIMARY KEY"));
						editLine.setCreateTableOrClass(false);
						return editLine;
					} else {
						editLine.setLine(line.replaceFirst("\\)", " PRIMARY KEY)"));
						editLine.setCreateTableOrClass(false);
						return editLine;
					}
				} else {
					editLine.setLine(line.replaceFirst("\\)", ") PRIMARY KEY"));
					editLine.setCreateTableOrClass(false);
					return editLine;
				}
			} else {
				editLine.setCreateTableOrClass(true);
				return editLine;
			}
		}
		if (true == isCreateTableOrClass) {
			if (line.toUpperCase().indexOf("ENUM") >= 0 && line.toUpperCase().indexOf("'") >= 0) {
				return checkEnum(line, editLine);
			} else if (true == isEnumType && line.contains(")")) {
				String startSubString = line.substring(0, line.toUpperCase().lastIndexOf("'"));
				String endSubString = line.toUpperCase().substring(line.toUpperCase().lastIndexOf("'")).replaceFirst("\\)", ")  PRIMARY KEY");
				line = startSubString + endSubString;
				editLine.setLine(line);
				editLine.setEnumType(false);
				editLine.setCreateTableOrClass(false);
				return editLine;
			} else if (line.toUpperCase().indexOf(",") >= 0) {
				if (line.toUpperCase().indexOf("(") >= 0) {
					if (line.toUpperCase().indexOf("(") > line.toUpperCase().indexOf(",")) {
						editLine.setLine(line.replaceFirst(",", " PRIMARY KEY,"));
						editLine.setCreateTableOrClass(false);
						return editLine;
					} else if (line.toUpperCase().indexOf(")") < line.toUpperCase().indexOf(",")) {
						if (line.indexOf("(", line.indexOf("(") + 1) > line.indexOf(")")) {
							if (line.indexOf(",") > line.indexOf(")") && line.indexOf("(") < line.indexOf(")")) {
								String temp = line.substring(line.indexOf("(") + 1, line.indexOf(")"));
								String str[] = temp.split(" ");
								if (str.length > 1) {
									editLine.setLine(line.replaceFirst("\\)", " PRIMARY KEY ) "));
									editLine.setCreateTableOrClass(false);
									return editLine;

								} else {
									editLine.setLine(line.replaceFirst("\\)", " ) PRIMARY KEY  "));
									editLine.setCreateTableOrClass(false);
									return editLine;
								}
							} else {
								editLine.setLine(line.replaceFirst("\\)", " PRIMARY KEY ) "));
								editLine.setCreateTableOrClass(false);
								return editLine;
							}
						} else {
							editLine.setLine(line.replaceFirst("\\)", " ) PRIMARY KEY"));
							editLine.setCreateTableOrClass(false);
							return editLine;
						}
					} else {
						editLine.setLine(line.replaceFirst("\\)", ") PRIMARY KEY"));
						editLine.setCreateTableOrClass(false);
						return editLine;
					}
				} else {
					if (line.toUpperCase().indexOf("'") >= 0) {
						editLine.setLine(line.replaceFirst("\\)", ") PRIMARY KEY"));
						editLine.setCreateTableOrClass(false);
						return editLine;
					} else {
						editLine.setLine(line.replaceFirst(",", " PRIMARY KEY,"));
						editLine.setCreateTableOrClass(false);
						return editLine;
					}
				}
			}
			if (line.toUpperCase().indexOf(");") > 0) {
				editLine.setLine(line.replaceFirst("\\);", " PRIMARY KEY);"));
				editLine.setCreateTableOrClass(false);
				return editLine;
			} else if (line.toUpperCase().trim().length() > 0) {
				if (line.toUpperCase().trim().equals("(")) { // only (
					editLine.setCreateTableOrClass(true);
					return editLine;
				} else if (line.toUpperCase().indexOf(")") < line.toUpperCase().lastIndexOf(")")) {
					if (line.toUpperCase().lastIndexOf("(") < line.toUpperCase().indexOf(")")) {
						editLine.setLine(line.replaceFirst("\\)", ") PRIMARY KEY"));
						editLine.setCreateTableOrClass(false);
						return editLine;
					} else {
						editLine.setLine(line.replaceFirst("\\)", " PRIMARY KEY)"));
						editLine.setCreateTableOrClass(false);
						return editLine;
					}
				} else {
					editLine.setLine(line + " PRIMARY KEY");
					editLine.setCreateTableOrClass(false);
					return editLine;
				}
			} else {
				editLine.setCreateTableOrClass(true);
				return editLine;
			}
		}
		return editLine;
	}

	private class EditLine {
		private String line = "";
		private boolean isCreateTableOrClass = false;
		private boolean isEnumType = false;

		public EditLine() {
			setLine("");
			setCreateTableOrClass(false);
			setEnumType(false);
		}

		public void setLine(String line) {
			this.line = line;
		}

		public String getLine() {
			return line;
		}

		public void setCreateTableOrClass(boolean isCreateTableOrClass) {
			this.isCreateTableOrClass = isCreateTableOrClass;
		}

		public boolean isCreateTableOrClass() {
			return isCreateTableOrClass;
		}

		public void setEnumType(boolean isEnumType) {
			this.isEnumType = isEnumType;
		}

		public boolean isEnumType() {
			return isEnumType;
		}

	}

	public void convert() throws IOException {
		String line = null;
		boolean isMultipleLine = false;
		boolean shouldBeDeleted = false;
		out.write("--test:" + Constants.LINE_SEPARATOR);
		boolean isCreateTableOrClass = false;
		boolean isEnumType = false;
		EditLine editLine = null;
		while ((line = lineReader.readLine()) != null) {
			line = line.trim();

			if (line.equals("")) {
				if (isMultipleLine) {
					out.write("\\" + Constants.LINE_SEPARATOR);
				} else {
					out.write(Constants.LINE_SEPARATOR);
				}
				continue;
			}
			if (line.startsWith("--"))
				continue;

			// edited by cn15209 20120627
			editLine = editLineForCreateTableOrClass(line, isCreateTableOrClass, isEnumType);
			line = editLine.getLine();
			isCreateTableOrClass = editLine.isCreateTableOrClass();
			isEnumType = editLine.isEnumType();
			if (line.endsWith(";")) {
				if (isMultipleLine) {
					// last line among multiple lines.
					if (!shouldBeDeleted) {
						out.write(line + Constants.LINE_SEPARATOR);
						addCheckPoint();
					}
				} else {
					// single line statement
					shouldBeDeleted = shouldBeDeleted(line);
					isDML = isDML(line);
					if (!shouldBeDeleted) {
						out.write(line + Constants.LINE_SEPARATOR);
						addCheckPoint();
					}
				}

				isMultipleLine = false;
				shouldBeDeleted = false;
				isDML = false;
			} else {
				if (isMultipleLine) {
					// middle line among multiple lines.
					if (!shouldBeDeleted) {
						out.write(line + " \\" + Constants.LINE_SEPARATOR);
					}
				} else {
					// first line among multipe lines.
					shouldBeDeleted = shouldBeDeleted(line);
					if (!shouldBeDeleted) {
						isDML = isDML(line);
						out.write(line + " \\" + Constants.LINE_SEPARATOR);
					}
					isMultipleLine = true;
				}
			}
		}
		out.flush();
		out.close();
	}

	private void addCheckPoint() throws IOException {
		out.write("--check:" + Constants.LINE_SEPARATOR);

		if (this.isDML) {
			out.write("$HC_CHECK_FOR_DML" + Constants.LINE_SEPARATOR);
		} else {
			// added by cn15209 2012.08.07
			out.write("@HC_CHECK_FOR_EACH_STATEMENT");
		}
		out.write("" + Constants.LINE_SEPARATOR);
		out.write("--test:" + Constants.LINE_SEPARATOR);
	}

	private boolean shouldBeDeleted(String line) {
		String n1 = line.toUpperCase();
		if (n1.startsWith("AUTOCOMMIT"))
			return true;
		if (n1.startsWith("ROLLBACK"))
			return true;
		if (n1.startsWith("COMMIT"))
			return true;
		if (n1.startsWith("$"))
			return true;
		if (n1.startsWith("SHOW"))
			return true;
		if (n1.startsWith("CALL"))
			return true;
		if (n1.startsWith("SELECT")) {
			if (n1.indexOf("INCR") == -1 && n1.indexOf("DECR") == -1) {
				return true;
			}
		}
		// n1 = CommonUtils.replace(n1, " ", "");
		// if (n1.startsWith("SETSYSTEMPARAMETERS"))
		// return true;
		return false;
	}

	private boolean isDML(String firstLine) {
		String n1 = firstLine.toUpperCase();
		if (n1.startsWith("SELECT")) {
			if (n1.indexOf("INCR") != -1 || n1.indexOf("DECR") != -1)
				return true;
		}
		if (n1.startsWith("INSERT "))
			return true;
		if (n1.startsWith("UPDATE "))
			return true;
		if (n1.startsWith("DELETE "))
			return true;
		if (n1.startsWith("TRUNCATE "))
			return true;
		if (n1.startsWith("MERGE "))
			return true;
		if (n1.startsWith("REPLACE "))
			return true;
		if (n1.startsWith("PREPARE "))
			return true;
		if (n1.startsWith("EXECUTE "))
			return true;
		if (n1.startsWith("DO "))
			return true;

		n1 = CommonUtils.replace(n1, " ", "");
		if (n1.startsWith("DEALLOCATEPREPARE"))
			return true;
		if (n1.startsWith("DEALLOCATEPREPARE"))
			return true;
		return false;
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

		try {
			if (out != null) {
				out.close();
			}
		} catch (Exception e) {
		}

	}
}
