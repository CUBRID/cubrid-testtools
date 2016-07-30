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
package com.navercorp.cubridqa.ha_repl.common;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

	public static String getFileNameForDispatchAll() {
		return CommonUtils.concatFile(Constants.DIR_CONF, "dispatch_tc_ALL.txt");
	}

	public static String getFileNameForDispatchFin(String envName) {
		return CommonUtils.concatFile(Constants.DIR_CONF, "dispatch_tc_FIN_" + envName + ".txt");
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
			resultList.add(line.trim());
		}
		lineReader.close();
		reader.close();
		fis.close();
		return resultList;
	}

	public static boolean containToken(String filename, String token) throws IOException {

		File file = new File(filename);
		if (!file.exists()) {
			return false;
		}

		FileInputStream fis = new FileInputStream(file);
		InputStreamReader reader = new InputStreamReader(fis, "UTF-8");

		LineNumberReader lineReader = new LineNumberReader(reader);
		String line;
		String upperToken = token.toUpperCase().trim();

		boolean hasToken = false;
		while ((line = lineReader.readLine()) != null) {
			if (line.toUpperCase().indexOf(upperToken) != -1) {
				hasToken = true;
				break;
			}
		}
		lineReader.close();
		reader.close();
		fis.close();
		return hasToken;
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

	// public static void main(String[] args) {
	// String s = " 'FIND_PK_CLASS' 'album' 3";
	// ArrayList list = extractTableToBeVerified(s+s+s+s, "FIND_PK_CLASS");
	// }

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
			result.append(line.trim()).append(Constants.LINE_SEPARATOR);
		}
		lineReader.close();
		reader.close();
		fis.close();
		return result.toString();
	}

	public static int greaterThanVersion(String v1, String v2) {
		String[] a1 = v1.split("\\.");
		String[] a2 = v2.split("\\.");
		int p1, p2;
		for (int i = 0; i < 4; i++) {
			p1 = Integer.parseInt(a1[i]);
			p2 = Integer.parseInt(a2[i]);
			if (p1 == p2)
				continue;
			return (p1 > p2) ? 1 : -1;
		}
		return 0;
	}

	public static int getVersionNum(String versionId, int pos) {
		String[] arr = versionId.split("\\.");
		return Integer.parseInt(arr[pos - 1]);
	}

	public static boolean haveCharsetToCreateDB(String versionId) {
		if (greaterThanVersion(versionId, Constants.HAVE_CHARSET_10) >= 0) {
			return true;
		} else if (getVersionNum(versionId, 1) == 9 && greaterThanVersion(versionId, Constants.HAVE_CHARSET_9) >= 0) {
			return true;
		} else {
			return false;
		}
	}

	public static String dateToString(Date date, String fm) {
		SimpleDateFormat dateFormat = new SimpleDateFormat(fm);
		return dateFormat.format(date);
	}

	public static boolean isNewBuildNumberSystem(String simplifiedBuildId) {
		if (simplifiedBuildId == null) {
			return false;
		}
		String curValue = convertNumberSystemToFixedLength(simplifiedBuildId);
		String stdValue = convertNumberSystemToFixedLength("10.1.0.6858");
		return curValue.compareTo(stdValue) >= 0;
	}

	public static String convertNumberSystemToFixedLength(String simplifiedBuildId) {
		if (simplifiedBuildId == null) {
			return simplifiedBuildId;
		}

		String[] items = simplifiedBuildId.split("\\.");
		return toFixedLength(items[0], 3, '0') + toFixedLength(items[1], 3, '0') + toFixedLength(items[2], 3, '0') + toFixedLength(items[3], 10, '0');

	}

	public static String toFixedLength(String str, int len, char fillChar) {

		if (str == null)
			return null;
		String result = str;
		for (int i = 0; i < len; i++) {
			result = fillChar + result;
		}
		return result.substring(result.length() - len);
	}

	public static void main(String[] args) {
		System.out.println(haveCharsetToCreateDB("10.0.0.0073"));
	}
}
