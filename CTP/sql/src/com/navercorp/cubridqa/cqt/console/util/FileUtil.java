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
package com.navercorp.cubridqa.cqt.console.util;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

public class FileUtil {

	/**
	 * read the content of file .
	 * 
	 * @param file
	 * @return
	 */
	public static String readFile(String file) {
		return readFile(file, TestUtil.DEFAULT_CODESET);
	}

	/**
	 * read the content of file .
	 * 
	 * @param file
	 * @return
	 */
	public static String readFile(String file, String charset) {
		if (file == null) {
			return null;
		}

		String ret = null;
		byte[] data = null;
		List<byte[]> list = new ArrayList<byte[]>();
		byte[] temp = new byte[1024];
		int totalSize = 0;
		int index = 0;
		FileInputStream input = null;
		try {
			File f = new File(file);
			if (!f.exists()) {
				return null;
			}

			input = new FileInputStream(f);
			int count = input.read(temp);
			while (count != -1) {
				byte[] bytes = new byte[count];
				System.arraycopy(temp, 0, bytes, 0, count);
				list.add(bytes);
				totalSize = totalSize + count;

				count = input.read(temp);
			}

			data = new byte[totalSize];
			for (int i = 0; i < list.size(); i++) {
				temp = (byte[]) list.get(i);
				System.arraycopy(temp, 0, data, index, temp.length);
				index = index + temp.length;
			}
			ret = new String(data, charset);
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (input != null) {
				try {
					input.close();
				} catch (IOException e) {
				}
			}
		}

		return ret;
	}

	public static String AddTab(int num) {
		StringBuilder sb = new StringBuilder();

		for (int i = 0; i < num; i++) {
			sb.append("\t");
		}

		return sb.toString();
	}

	public static String AddSpace(int num) {
		StringBuilder sb = new StringBuilder();

		for (int i = 0; i < num; i++) {
			sb.append(" ");
		}

		return sb.toString();
	}

	public static void writeToFile(String file, String data) {
		writeToFile(file, data, false);
	}

	public static void writeToFile(String file, String data, String charset) {
		writeToFile(file, data, false, charset);
	}

	public static void writeToFile(String file, String data, boolean append) {
		writeToFile(file, data, append, "UTF-8");
	}

	public static void closeFileHandle(BufferedWriter bw) {
		try {
			if (bw != null) {
				bw.close();
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public static void writeDataToFileWithHandle(BufferedWriter bw, String data) {
		if (bw == null || data == null) {
			return;
		}

		try {
			bw.write(data);
			bw.flush();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public static void writeHeadForXML(BufferedWriter bw) {
		String head = "";
		String S_FAILSUMMARY = "<results>";
		head += S_FAILSUMMARY + System.getProperty("line.separator");
		writeDataToFileWithHandle(bw, head);
	}

	public static void writeFooterForXml(BufferedWriter bw) {
		String head = "";
		String S_FAILSUMMARY = "</results>";
		head += S_FAILSUMMARY + System.getProperty("line.separator");
		writeDataToFileWithHandle(bw, head);
	}

	public static void writeSQLFileIntoResultFolder(String source, String target) {
		File src = new File(source);
		File tget = new File(target);
		FileInputStream in = null;
		FileOutputStream out = null;
		FileChannel inC = null;
		FileChannel outC = null;

		if (!src.exists()) {
			return;
		}

		try {
			in = new FileInputStream(src);
			out = new FileOutputStream(tget);
			inC = in.getChannel();
			outC = out.getChannel();

			inC.transferTo(0, inC.size(), outC);

		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				in.close();
				inC.close();
				outC.close();
				out.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	public static BufferedWriter openOneFileHandle(String file) {
		if (file == null) {
			return null;
		}

		File f = new File(file);
		if (!f.exists()) {
			try {
				f.createNewFile();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		f.setWritable(true);
		f.setExecutable(true);

		BufferedWriter writer = null;
		try {
			writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f, true), "utf8"));
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return writer;
	}

	/**
	 * write data to file .
	 * 
	 * @param file
	 * @param data
	 * @param append
	 *            if append or rewrite .
	 */
	public static void writeToFile(String file, String data, boolean append, String charset) {
		if (file == null || data == null) {
			return;
		}

		BufferedWriter writer = null;
		try {
			File f = new File(file);
			if (!f.exists()) {
				try {
					f.createNewFile();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			f.setWritable(true);
			f.setExecutable(true);

			writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f, append), charset));
			writer.write(data);
			writer.flush();
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (writer != null) {
				try {
					writer.close();
				} catch (IOException e) {
				}
			}
		}
	}

	/**
	 * create directory.
	 * 
	 * @param dirPath
	 */
	public static void createDir(String dirPath) {
		if (dirPath == null) {
			return;
		}

		File file = new File(dirPath);
		if (!file.exists()) {
			StringBuilder path = new StringBuilder("");
			boolean flag = dirPath.startsWith(File.separator);
			if (flag) {
				path.append(File.separator);
			}

			StringTokenizer token = new StringTokenizer(dirPath, File.separator);
			while (token.hasMoreTokens()) {
				String dirName = token.nextToken();
				path.append(dirName);
				try {
					File currentDir = new File(path.toString());
					if (!currentDir.exists()) {
						currentDir.mkdir();
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
				path.append(File.separator);
			}
		}
	}

	/**
	 * get the directory name by the file name .
	 * 
	 * @param file
	 * @return
	 */
	public static String getDirPath(String file) {
		if (file == null) {
			return null;
		}

		String ret = null;
		int position = file.lastIndexOf("/");
		if (position != -1) {
			ret = file.substring(0, position);
		}
		return ret;
	}

	/**
	 * copy file from source directory to target directory.
	 * 
	 * @param srcFile
	 * @param targetFile
	 */
	public static void copyFile(String srcFile, String targetFile) {
		File f = new File(srcFile);
		if (!f.exists()) {
			return;
		}
		String targetDir = FileUtil.getDirPath(targetFile);
		FileUtil.createDir(targetDir);
		CommandUtil.execute("cp " + srcFile + " " + targetFile, null);
	}

	/**
	 * get the directory by the file name .
	 * 
	 * @param file
	 * @return
	 */
	public static String getDir(String file) {
		if (file == null || file.trim().equals("")) {
			return null;
		}

		String ret = StringUtil.replaceSlashBasedSystem(file);
		int position = ret.lastIndexOf(File.separator);
		if (position != -1) {
			ret = file.substring(0, position);
		}
		return ret;
	}

	/**
	 * get the file name by the path .
	 * 
	 * @param path
	 * @return
	 */
	public static String getFileName(String path) {
		String ret = path;
		int position = path.lastIndexOf("/");
		if (position > 0) {
			ret = path.substring(position + 1);
			int position2 = ret.indexOf(".");
			if (position2 != -1) {
				ret = ret.substring(0, position2);
			}
		}
		return ret;
	}

	/**
	 * determin if file exist.
	 * 
	 * @param filePath
	 * @return
	 */
	public static boolean isFileExist(String filePath) {
		File file = new File(filePath);
		return file.exists();
	}
}
