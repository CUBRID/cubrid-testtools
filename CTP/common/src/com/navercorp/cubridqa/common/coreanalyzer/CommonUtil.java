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
package com.navercorp.cubridqa.common.coreanalyzer;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;
import java.util.Stack;

public class CommonUtil {

	public static String getTimestampText(long time, String format) {
		SimpleDateFormat f = new SimpleDateFormat(format);
		String timeStamp = f.format(new Date(time));
		return timeStamp;
	}

	public static String date2String(Date date, String format) {
		SimpleDateFormat f = new SimpleDateFormat(format);
		String result = f.format(date);
		return result;
	}

	public static String formatTPS(double d) {
		return new DecimalFormat("#.######").format(d);
	}

	public static String formatNumberWithSameWidth(int d) {
		return formatNumberWithSameWidth(d, String.valueOf(d).length());

	}

	public static String formatNumberWithSameWidth(int d, int width) {

		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < width; i++) {
			sb.append("0");
		}
		sb.append(d);
		String s = sb.toString();
		return s.substring(s.length() - width);

	}

	public static Date string2Date(String string, String format) throws ParseException {
		DateFormat df = new SimpleDateFormat(format);
		return df.parse(string);
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

	public static boolean matchNumber(String s) {
		if (s == null) {
			return false;
		}

		s = s.trim();

		char c;
		int len = s.length();
		for (int i = 0; i < len; i++) {
			c = s.charAt(i);
			if (Constants.ALLNUMBERS.indexOf(c) == -1) {
				return false;
			}
		}
		return true;
	}

	public static String getFileContent(String filename) throws IOException {
		File file = new File(filename);
		if (!file.exists()) {
			return null;
		}

		FileInputStream fis = new FileInputStream(file);
		ByteArrayOutputStream out = new ByteArrayOutputStream();

		byte[] buff = new byte[1024];
		int len = 0;
		while ((len = fis.read(buff)) != -1) {
			out.write(buff, 0, len);
		}

		out.close();
		fis.close();
		return out.toString();
	}

	public static String removeMoreBlanks(String cont) {
		if (cont == null)
			return null;

		cont = cont.trim();
		
		char currChar;
		boolean lastCharIsBlank = false;
		int len = cont.length();

		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < len; i++) {
			currChar = cont.charAt(i);
			if(currChar == ' ' ) {
				if (!lastCharIsBlank) {
					sb.append(currChar);
				}
			} else {
				sb.append(currChar);
			}
			lastCharIsBlank = currChar == ' ';
		}
		
		return sb.toString();
	}

	public static String removeBackets(String cont) {
		if (cont == null)
			return null;

		int len = cont.length();

		Stack stack = new Stack();

		char c;
		for (int i = 0; i < len; i++) {
			c = cont.charAt(i);
			if (c == ')') {
				while (true) {
					c = (Character) stack.pop();
					if (c == '(') {
						break;
					}
				}
			} else {
				stack.push(c);
			}
		}

		StringBuilder sb = new StringBuilder();

		while (stack.isEmpty() == false) {
			c = (Character) stack.pop();
			sb.append(c);
		}

		return sb.reverse().toString();
	}

	public static void main(String[] args) {
		System.out.println(removeMoreBlanks("ABC(DEF   (aaaa) dd) xxx (sss###)"));

	}
}
