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
package com.navercorp.cubridqa.common;

import java.io.File;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.OutputStream;
import java.io.Reader;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.navercorp.cubridqa.common.CommonUtils;

public class CommonUtils {
	public static String replace(String strSource, String strFrom, String strTo) {
		if (strFrom == null || strFrom.equals(""))
			return strSource;

		String strDest = "";
		int intFromLen = strFrom.length();
		int intPos;
		while ((intPos = strSource.indexOf(strFrom)) != -1) {
			strDest = strDest + strSource.substring(0, intPos);
			strDest = strDest + strTo;
			strSource = strSource.substring(intPos + intFromLen);
		}
		strDest = strDest + strSource;
		return strDest;
	}

	public static String rightTrim(String str) {
		if (str == null)
			return str;
		int len = str.length();

		int count = 0;
		for (int i = len - 1; i >= 0; i--) {
			if (str.charAt(i) == ' ' || str.charAt(i) == '\n' || str.charAt(i) == '\r' || str.charAt(i) == '\t') {
				count++;
			} else {
				break;
			}
		}
		return str.substring(0, len - count);

	}

	public static ArrayList<String[]> extractTableToBeVerified(String input, String flag) {

		ArrayList<String[]> list = new ArrayList<String[]>();
		if (input == null)
			return list;

		Pattern pattern = Pattern.compile("'" + flag + "'\\s*'(.*?)'\\s*([0-9]*)");
		Matcher matcher = pattern.matcher(input);

		String[] item;

		while (matcher.find()) {
			item = new String[2];
			item[0] = matcher.group(1);
			item[1] = matcher.group(2);

			list.add(item);
		}
		return list;

	}

	public static String concatFile(String p1, String p2) {
		String p;
		if (p1 == null)
			p1 = "";
		if (p2 == null)
			p2 = "";

		p1 = p1.trim().replace('\\', '/');
		p2 = p2.trim().replace('\\', '/');
		p = p1 + "/" + p2;
		String t;
		while (true) {
			t = replace(p, "//", "/");
			if (p.equals(t)) {
				break;
			} else {
				p = t;
			}
		}
		return p.replace('/', File.separatorChar);
	}

	public static String getFileContent(String filename) throws IOException {
		File file = new File(filename);
		if (!file.exists()) {
			return null;
		}
		StringBuffer result = new StringBuffer();
		FileInputStream fis = new FileInputStream(file);
		InputStreamReader reader = new InputStreamReader(fis, "UTF-8");

		LineNumberReader lineReader = new LineNumberReader(reader);
		String line;

		while ((line = lineReader.readLine()) != null) {
			if (line.trim().equals(""))
				continue;
			result.append(line.trim()).append('\n');
		}
		lineReader.close();
		reader.close();
		fis.close();
		return result.toString();
	}

	public static ArrayList<String> getLineList(String filename) throws IOException {

		File file = new File(filename);
		if (!file.exists()) {
			return null;
		}
		ArrayList<String> resultList = new ArrayList<String>();
		FileInputStream fis = new FileInputStream(file);
		InputStreamReader reader = new InputStreamReader(fis, "UTF-8");

		LineNumberReader lineReader = new LineNumberReader(reader);
		String line;

		while ((line = lineReader.readLine()) != null) {
			if (line.trim().equals(""))
				continue;
			resultList.add(line.trim());
		}
		lineReader.close();
		reader.close();
		fis.close();
		return resultList;
	}

	public static Properties getProperties(String filename) throws IOException {
		FileInputStream fis = new FileInputStream(filename);
		Properties props = new Properties();
		props.load(fis);
		fis.close();
		return props;
	}

	public static Properties getPropertiesWithPriority(String filename) throws IOException {
		Properties props = getProperties(filename);
		props.putAll(System.getProperties());
		return props;
	}

	public static void writeProperties(String filename, Properties props) throws IOException {
		File f = new File(filename);
		OutputStream out = new FileOutputStream(f);
		props.store(out, "");
	}

	public static void sleep(int sec) {
		try {
			Thread.sleep(sec * 1000);
		} catch (InterruptedException e) {
		}
	}

	public static String dateToString(Date date, String fm) {
		SimpleDateFormat dateFormat = new SimpleDateFormat(fm);
		return dateFormat.format(date);
	}

	public static String getExactFilename(String fullFilename) {
		if (fullFilename == null) {
			return null;
		}

		String exactName = fullFilename.replace('\\', '/');
		int p = exactName.lastIndexOf('/');
		if (p == -1) {
			return fullFilename;
		}

		return exactName.substring(p + 1);
	}

	public static String getExportsOfMEKYParams() {
		Map<String, String> map = System.getenv();

		Set<Map.Entry<String, String>> entries = map.entrySet();
		StringBuffer result = new StringBuffer();

		String key, value;
		for (Map.Entry<String, String> entry : entries) {
			key = entry.getKey().trim();
			value = entry.getValue();

			if (key.startsWith("MKEY") && value != null) {
				result.append("export ").append(key).append("=\"").append(value).append("\";");
			}
		}
		return result.toString();
	}

	public static String getEnvInFile(String var) {
		String value = System.getenv(var);
		
		if (value == null) {
			return value;
		}
		
		value = getFixedPath(value);
		
		File file = new File(value);
		try {
			return file.getCanonicalPath();
		} catch (Exception e) {
			return file.getAbsolutePath();
		}
	}

	public final static boolean isWindowsPlatform() {
		String osName = System.getProperty("os.name");
		return osName != null && osName.toLowerCase().indexOf("windows") != -1;
	}

	public final static boolean isCygwinPlatform() {
		String term = System.getenv("TERM");
		return term != null && term.toLowerCase().indexOf("cygwin") != -1;
	}
	
	public static String getLinuxStylePath(String path) {
		path = path.trim();
		if (isCygwinPlatform()) {
			String linStylePath;
			while (true) {
				linStylePath = LocalInvoker.execCommands("cygpath " + path, false);
				if (linStylePath != null && linStylePath.trim().length() > 0) {
					break;
				}
			}
			return linStylePath.trim();
		} else {
			return path;
		}
	}

	public static String getWindowsStylePath(String path) {
		path = path.trim();		
		if (isCygwinPlatform()) {
			String winStylePath;
			while (true) {
				winStylePath = LocalInvoker.execCommands("cygpath -m " + path, false);
				if (winStylePath != null && winStylePath.trim().length() > 0) {
					break;
				}
			}
			return winStylePath.trim();
		} else {
			return path;
		}
	}
		

	public static String getFixedPath(String path) {
		if(path == null) return path;
		
		path = path.trim();
		
		try{
			File file = new File(path);
			if (file.exists())
				return file.getCanonicalPath();
			if (CommonUtils.isWindowsPlatform()) {
				file = new File(getWindowsStylePath(path));
				if(file.exists()) {
					return file.getCanonicalPath();
				}
			}
		} catch(Exception e) {
			throw new RuntimeException("fail to get fixed path: " + path);
		}
		return path;
	}

	public static void main(String[] args) throws IOException {
		System.out.println(getExactFilename("conf/core.1234"));
		System.out.println(getExactFilename("conf\\core.1234"));
		System.out.println(getExactFilename("/core\\conf\\core.1234"));
	}
	
	public static Properties getConfig(String configFile) {
		FileInputStream fis = null;

		try {
			fis = new FileInputStream(configFile);
			InputStream is = new BufferedInputStream(fis);
			Properties props = new Properties();
			props.load(is);
			return props;
		} catch (IOException e) {
			throw new RuntimeException(e);
		} finally {
			if (fis != null) {
				try {
					fis.close();
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			}
		}
	}

	public static Timestamp getCurrentTimestamp() {
		return new Timestamp(System.currentTimeMillis());
	}

	public static Reader readFile(String file) {
		try {
			return new FileReader(file);
		} catch (FileNotFoundException e) {
			return null;
		}
	}

	public static int getRadomNum(int max) {
		return (int) Math.floor(Math.random() * max);

	}

	public static String getCurrentTimeStamp(String format) {
		SimpleDateFormat f = new SimpleDateFormat(format);
		String timeStamp = f.format(new Date());
		return timeStamp;
	}
	
	public static boolean valueOfBoolean(String str) {
		if(str == null) return false;
		
		str = str.toUpperCase().trim();
		
		return str.equals("TRUE") || str.equals("YES") || str.equals("1");
	}	
}
