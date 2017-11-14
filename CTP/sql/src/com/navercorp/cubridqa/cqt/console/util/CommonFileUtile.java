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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;

import com.navercorp.cubridqa.cqt.model.Resource;

public class CommonFileUtile {

	private static String localPath = PropertiesUtil.getValue("local.path");

	private static String rootNodeName = PropertiesUtil.getValue("rootnode.name");

	/**
	 * replace file separation character "\" to "/"
	 * 
	 * @param filePath
	 * @return
	 */
	public static String changeFilePath(String filePath) {
		return filePath.replaceAll("\\\\", "/");
	}

	/**
	 * generate the element of special directory Recursively.
	 * <p>
	 * for example :{ a/c/d => <a><b><c></c></b><a> }
	 * 
	 * @param f
	 *            the directory path
	 * @return
	 */
	public static Element createDesc(File f) {
		Element e = DocumentHelper.createElement(f.getName());
		Pattern pattern = Pattern.compile("[0-9]*");
		if (f.isDirectory() && hasDirectory(f)) {
			File[] files = f.listFiles();
			Arrays.sort(files);
			for (File file : files) {
				if (file.isDirectory() && file.getAbsolutePath().indexOf(".svn") < 0 && !file.getName().startsWith(".") && !pattern.matcher(file.getName().substring(0, 1)).matches()) {
					e.add(createDesc(file));
				}
			}
		}
		return e;
	}

	/**
	 * determine if the file contains directory .
	 * 
	 * @param f
	 * @return boolean
	 */
	public static boolean hasDirectory(File f) {
		File[] files = f.listFiles();
		Arrays.sort(files);
		for (File file : files) {
			if (file.isDirectory()) {
				return true;
			}
		}
		return false;
	}

	/**
	 * determine if the file is directory .
	 * 
	 * @param path
	 * @return
	 */
	public static boolean isDirectory(String path) {
		return new File(path).isDirectory();
	}

	/**
	 * get the absolute path of the string through the resource.
	 * 
	 * @param resource
	 * @param string
	 * @return
	 */
	public static String getPath(Resource resource, String string) {
		String path = "";
		if (resource == null) {
			path = localPath + string;
		} else {
			String parentPath = resource.getName();
			path = parentPath + "/" + string;
		}
		return path;
	}

	/**
	 * 
	 * 
	 * @param resource
	 * @return
	 */
	public static String getXpath(Resource resource) {
		String xpath = resource.getName();
		xpath = xpath.substring(xpath.indexOf(rootNodeName), xpath.length());
		return xpath;
	}

	/**
	 * read the file content and convert to string.
	 * 
	 * @param filePath
	 * @return
	 */
	public static String readFileContent(String filePath) {
		String text = "";
		BufferedReader reader = null;
		try {
			File file = new File(filePath);
			if (file != null && file.exists() && file.isFile()) {
				reader = new BufferedReader(new InputStreamReader(new FileInputStream(filePath), "utf-8"));
				StringBuffer buffer = new StringBuffer();
				int n;
				while ((n = reader.read()) != -1) {
					buffer.append((char) n);
				}
				text = buffer.toString();
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {

		} finally {
			if (reader != null) {
				try {
					reader.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		return text;
	}

	/**
	 * read the file content and convert to Object list.
	 * 
	 * @param filePath
	 * @param object
	 * @return
	 */
	public static List<Object> readFile(String filePath, Object object) {
		if (filePath == null) {
			filePath = "";
		}
		filePath = filePath.replaceAll(" ", "");
		File file = new File(filePath);
		List<Object> list = new ArrayList<Object>();
		if (file != null && file.getAbsolutePath() != null && !file.getAbsolutePath().equals("") && file.exists() && !file.isDirectory()) {
			BufferedReader bufferedReader = null;
			try {
				bufferedReader = new BufferedReader(new InputStreamReader(new FileInputStream(filePath), "utf-8"));
				String buffer = "";
				while ((buffer = bufferedReader.readLine()) != null) {
					list.add(buffer);
				}
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			} finally {
				if (bufferedReader != null) {
					try {
						bufferedReader.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
			if (list.size() == 0) {
				list.add("");
			}
			return list;
		} else {
			return new ArrayList<Object>();
		}
	}

	/**
	 * get the file content convert to list .
	 * 
	 * @param filePath
	 * @param object
	 * @return
	 */
	public static List<Object> readFileAddLine(String filePath, Object object) {
		if (filePath == null) {
			filePath = "";
		}
		filePath = filePath.replaceAll(" ", "");
		File file = new File(filePath);
		List<Object> list = new ArrayList<Object>();
		if (file != null && file.getAbsolutePath() != null && !file.getAbsolutePath().equals("") && file.exists() && !file.isDirectory()) {
			BufferedReader bufferedReader = null;
			try {
				bufferedReader = new BufferedReader(new InputStreamReader(new FileInputStream(filePath), "utf-8"));
				String buffer = "";
				int lineNumber = 1;
				while ((buffer = bufferedReader.readLine()) != null) {
					if (buffer.startsWith("==========")) {
						if (lineNumber < 10) {
							buffer = buffer.replaceFirst("=", "[  " + lineNumber + "  ]  ");
						} else if (lineNumber < 100) {
							buffer = buffer.replaceFirst("=", "[  " + lineNumber + " ]  ");
						} else {
							buffer = buffer.replaceFirst("=", "[ " + lineNumber + " ]  ");
						}
						lineNumber++;
					}
					list.add(buffer);
				}
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			} finally {
				if (bufferedReader != null) {
					try {
						bufferedReader.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
			if (list.size() == 0) {
				list.add("");
			}
			return list;
		} else {
			return new ArrayList<Object>();
		}
	}

	/**
	 * read the file content and convert to string.
	 * 
	 * @deprecated
	 * @param filePath
	 * @param object
	 *            default is null.
	 * @return
	 */
	public static String readFileAddLines(String filePath, Object object) {
		if (filePath == null) {
			filePath = "";
		}
		filePath = filePath.replaceAll(" ", "");
		File file = new File(filePath);
		StringBuffer stringBuffer = new StringBuffer("");
		if (file != null && file.getAbsolutePath() != null && !file.getAbsolutePath().equals("") && file.exists() && !file.isDirectory()) {
			BufferedReader bufferedReader = null;
			try {
				bufferedReader = new BufferedReader(new InputStreamReader(new FileInputStream(filePath), "utf-8"));
				String buffer = "";
				int lineNumber = 1;
				while ((buffer = bufferedReader.readLine()) != null) {
					if (buffer.startsWith("==========")) {
						if (lineNumber < 10) {
							buffer = buffer.replaceFirst("=", "[  " + lineNumber + "  ]  ");
						} else if (lineNumber < 100) {
							buffer = buffer.replaceFirst("=", "[  " + lineNumber + " ]  ");
						} else {
							buffer = buffer.replaceFirst("=", "[ " + lineNumber + " ]  ");
						}
						lineNumber++;
					}
					stringBuffer.append(buffer + System.getProperty("line.separator"));
				}
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			} finally {
				if (bufferedReader != null) {
					try {
						bufferedReader.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
		}
		return stringBuffer.toString();
	}

	/**
	 * get object list from file .
	 * 
	 * @param filePath
	 * @return
	 */
	public static Object[] readFile(String filePath) {
		if (filePath == null) {
			filePath = "";
		}
		filePath = filePath.replaceAll(" ", "");
		File file = new File(filePath);
		List<String> list = new ArrayList<String>();
		if (file != null && file.getAbsolutePath() != null && !file.getAbsolutePath().equals("") && file.exists() && !file.isDirectory()) {
			BufferedReader bufferedReader = null;
			try {
				bufferedReader = new BufferedReader(new InputStreamReader(new FileInputStream(filePath), "utf-8"));
				String buffer = "";
				while ((buffer = bufferedReader.readLine()) != null) {
					list.add(buffer);
				}
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			} finally {
				if (bufferedReader != null) {
					try {
						bufferedReader.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
			return list.toArray();
		} else {
			return new String[0];
		}
	}

	/**
	 * recursively get the file list.
	 * 
	 * @param list
	 * @param rootFile
	 * @return
	 */
	public static List<File> getAllFile(List<File> list, File rootFile) {

		if (rootFile.getAbsolutePath().indexOf(".svn") < 0) {
			list.add(rootFile);
			if (rootFile.isDirectory()) {
				File[] files = rootFile.listFiles();
				Arrays.sort(files);
				for (File file : files) {
					getAllFile(list, file);
				}
			}
		}
		return list;
	}

	/**
	 * write the content to file .
	 * 
	 * @param content
	 *            the content be written to file .
	 * @param path
	 *            the file path .
	 * @return
	 */
	public static File writeFile(String content, String path) {
		File f = new File(path);
		if (!f.exists()) {
			try {
				if (f.isFile()) {
					File dir = new File(f.getParent());
					if (!dir.exists())
						dir.mkdirs();
				}
				f.createNewFile();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		BufferedWriter bw = null;
		try {
			bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f), "utf-8"));
			bw.write(content);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			if (bw != null) {
				try {
					bw.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}

		return f;
	}

	/**
	 * write multiline data to file .
	 * 
	 * @param lines
	 * @param path
	 */
	public static void writeFile(List<String> lines, String path) {
		File f = new File(path);
		BufferedWriter bw = null;
		String separator = System.getProperty("line.separator");
		if (!f.exists() && lines.size() > 0) {
			try {
				f.createNewFile();
				bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f), "utf-8"));
				for (String line : lines) {
					bw.write(line + separator);
				}
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} finally {
				if (bw != null) {
					try {
						bw.close();
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}
		}
	}

	/**
	 * determine if the os is linux or not .
	 * 
	 * @return
	 */
	public static boolean isLinux() {
		boolean isLinux = true;
		String systemInfo = EnvGetter.getenv().get("OS");
		if (systemInfo != null) {
			String string = EnvGetter.getenv().get("OS").toLowerCase();
			if (string.indexOf("windows") >= 0) {
				isLinux = false;
			}
		}
		return isLinux;
	}

	/**
	 * get the target file located in a directory .
	 * 
	 * @param srcDirectory
	 * @param aimFile
	 * @return
	 */
	public static List<File> getYourWantFiles(String srcDirectory, String aimFile) {
		List<File> list = new ArrayList<File>();
		File src = new File(srcDirectory);
		if (src.exists() && src.isDirectory()) {
			getChildrenFiles(src, list, aimFile);
		}
		return list;
	}

	public static List<File> getCoreFiles(String targetDirectory, List<String> existsCoreList){
		File file = new File(targetDirectory);
		List<File> list = new ArrayList<File>();
		if(!file.exists()) return list;
		searchCoreFromChildenDirectory(file, list, existsCoreList);
		return list;
		
	}
	
	private static void searchCoreFromChildenDirectory(File file, List<File> list, List<String> existsCoreList) {
		File[] listFiles = file.listFiles();
		for (File child : listFiles) {
			if (child.isDirectory()) {
				searchCoreFromChildenDirectory(child, list, existsCoreList);
			} else {
				String fileName = child.getName();
				String suffix = fileName.substring(fileName.indexOf(".") + 1, fileName.length());
				if (fileName.toUpperCase().startsWith("CORE.") && StringUtils.isNumeric(suffix)) {
					if (existsCoreList.isEmpty() || !existsCoreList.contains(child.getAbsolutePath())) {
						list.add(child);
						existsCoreList.add(child.getAbsolutePath());
					}
				}
			}
		}

	}
	/**
	 * get the target file in child directory.
	 * 
	 * @param file
	 *            the parent directory
	 * @param list
	 *            the reference to the file result .
	 * @param aimFile
	 */
	private static void getChildrenFiles(File file, List<File> list, String aimFile) {
		if (file.exists() && file.isDirectory()) {
			File[] listFiles = file.listFiles();
			for (File child : listFiles) {
				if (child.isDirectory()) {
					if (child.getName().equals(aimFile)) {
						list.add(child);
					} else {
						getChildrenFiles(child, list, aimFile);
					}
				}
			}
		}
	}

	/**
	 * get the target file which contain special contents from source directory.
	 * 
	 * @param srcDirectory
	 * @param contents
	 * @return
	 */
	public static List<File> getYourWantFilesByContents(String srcDirectory, String contents) {
		List<File> list = new ArrayList<File>();
		File src = new File(srcDirectory);
		if (src.exists() && src.isDirectory()) {
			getChildrenFilesByContents(src, list, contents);
		}
		return list;
	}

	/**
	 * set the files to list which contain special content in special directory.
	 * 
	 * @param file
	 * @param list
	 * @param contents
	 */
	private static void getChildrenFilesByContents(File file, List<File> list, String contents) {
		if (file.exists() && file.isDirectory()) {
			File[] listFiles = file.listFiles();
			for (File child : listFiles) {
				if (child.isDirectory()) {
					getChildrenFilesByContents(child, list, contents);
				} else {
					String readFileContent = readFileContent(child.getAbsolutePath());
					if (readFileContent.contains(contents) && !child.getAbsolutePath().endsWith(".class") && !child.getAbsolutePath().endsWith(".svn-base")) {
						list.add(child);
					}
				}
			}
		}
	}
}
