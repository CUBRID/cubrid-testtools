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

package com.navercorp.cubridqa.scheduler.common;

import java.io.File;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Properties;

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
		return getProperties(new File(filename));
	}

	public static Properties getProperties(File file) throws IOException {
		if (file.exists() == false) {
			return null;
		}

		FileInputStream fis = new FileInputStream(file);
		Properties props = new Properties();
		props.load(fis);
		fis.close();
		return props;
	}

	public static void sleep(int sec) {
		try {
			Thread.sleep(sec * 1000);
		} catch (InterruptedException e) {
		}
	}

	public static String getMainVersionId(String buildId) {
		if (buildId == null)
			return null;

		String[] items = buildId.split("\\.");
		if (items.length == 4) {
			return items[0].trim() + "." + items[1].trim() + "." + items[2].trim();
		}
		return buildId;
	}

	public static String getLastRevisionId(String buildId) {
		if (buildId == null)
			return null;

		String[] items = buildId.split("\\.");
		if (items.length == 4) {
			return items[3].trim();
		} else {
			return null;
		}
	}

	public static boolean isGreaterThan_For_BuildId(String buildId1, String buildId2) {
		if (buildId1 == null)
			return false;
		if (buildId2 == null)
			return true;

		String m1 = getMainVersionId(buildId1);
		String m2 = getMainVersionId(buildId2);

		if (m1.equals(m2)) {
			try {
				int n1 = Integer.parseInt(getLastRevisionId(buildId1));
				int n2 = Integer.parseInt(getLastRevisionId(buildId2));
				return n1 > n2;
			} catch (Exception e) {
				return false;
			}
		} else {
			return false;
		}
	}

	public static int[] getLastVersionPolicy(String buildId) {
		if (buildId == null)
			return null;

		int[] pair = new int[2];

		String[] items = buildId.split("\\.");
		if (items.length <= 3) {
			pair[0] = Integer.MIN_VALUE;
			pair[1] = Integer.MAX_VALUE;
			return pair;
		}

		int p1 = items[3].indexOf("~");
		if (p1 != -1) {
			String fixItem = "0" + (replace(items[3], " ", "").trim());
			fixItem = replace(fixItem, "~", "~0");
			String[] both = fixItem.split("~");
			pair[0] = Integer.parseInt(both[0]);
			pair[1] = Integer.parseInt(both[1]);
			if (pair[1] == 0)
				pair[1] = Integer.MAX_VALUE;
		} else {
			pair[0] = Integer.parseInt(items[3].trim());
			pair[1] = pair[0];
			return pair;
		}
		return pair;
	}

	public static boolean isBuildInRules(String rules, String buildId) {

		String testVersionId;
		int testRevisionId;

		try {
			testVersionId = getMainVersionId(buildId);
			testRevisionId = Integer.parseInt(getLastRevisionId(buildId));
		} catch (Exception e) {
			return false;
		}

		String value = rules;
		String[] items = value.split(",");
		String policyMainVersion;
		int[] policyLastPair;
		for (String it : items) {
			policyMainVersion = CommonUtils.getMainVersionId(it);
			policyLastPair = CommonUtils.getLastVersionPolicy(it);
			if (isMainVersionMatch(policyMainVersion, testVersionId)) {
				if (testRevisionId >= policyLastPair[0] && testRevisionId <= policyLastPair[1]) {
					return true;
				}
			}
		}
		return false;
	}

	public static boolean isMainVersionMatch(String m1, String m2) {
		if (m1 == null || m2 == null) {
			return false;
		}

		m1 = CommonUtils.replace(m1, " ", "");
		m2 = CommonUtils.replace(m2, " ", "");

		if (m1.equals(m2)) {
			return true;
		}

		String[] a1 = m1.split("\\.");
		String[] a2 = m2.split("\\.");

		if (a1.length != a2.length) {
			return false;
		}

		for (int i = 0; i < a1.length; i++) {
			if (a1[i].equals(a2[i]) || a1[i].equals("*") || a2[i].equals("*")) {
				continue;
			} else {
				return false;
			}
		}

		return true;

	}

	public static void main(String[] args) {
		System.out.println("getFirstVersion: " + getFirstVersion("10.0.0.1234"));
		System.out.println("getSecondVersion: " + getSecondVersion("10.1.0.1234"));
		// System.out.println(getMainVersionId("8.4.4.0211"));
		// int[] pair = getLastVersionPolicy("8.4.4.0211");
		// System.out.println(pair[0]+", " + pair[1]);
		//
		// pair = getLastVersionPolicy("8.4.4.0211~");
		// System.out.println(pair[0]+", " + pair[1]);
		//
		// pair = getLastVersionPolicy("10.0.0.9001~11111");
		// System.out.println(pair[0]+", " + pair[1]);

		// String fm = "{abc}_{def}_{gh}";
		// Properties props = new Properties();
		// props.setProperty("abc", "shell");
		// props.setProperty("def", "helo");
		// props.setProperty("gh", "64");
		// System.out.println(formatScenarioId(fm, props));
		// System.out.println(getMainVersionId("10.0.0.0376"));
		// System.out.println(isBuildInRules("8.3.1",
		// "8.3.1.0165_disable_owfs"));

		// System.out.println(isBuildInRules("9.2.*.0~0003", "9.2.12.0003"));

		// System.out.println(isMainVersionMatch("9.2.*", "9.2.12"));
		// for(int i=0;i<Integer.MAX_VALUE; i++) {
		// if (i % 1 == 0) {
		// System.out.println(genMsgId());
		// }else {
		// genMsgId();
		// }
		// }

	}

	public static String formatScenarioId(String fm, Properties props) {
		if (fm == null)
			return null;
		fm = fm.trim();
		ArrayList<String> keyList = new ArrayList<String>();
		int len = fm.length();
		StringBuffer key = null;
		for (int i = 0; i < len; i++) {
			if (fm.charAt(i) == '{') {
				key = new StringBuffer();
			} else if (fm.charAt(i) == '}') {
				keyList.add(key.toString());
				key = null;
			} else {
				if (key != null)
					key.append(fm.charAt(i));
			}
		}
		String value = fm;
		for (String k : keyList) {
			value = replace(value, "{" + k + "}", props.getProperty(k));
		}
		return value;
	}

	public static String getSVNBranch(String trunkVersion, String buildId) {
		return buildId.startsWith(trunkVersion) ? "trunk" : "RB-" + getVersion(buildId);
	}

	public static String getSVNBranchIgnorePatch(String trunkVersion, String buildId) {
		if (buildId.startsWith(trunkVersion)) {
			return "trunk";
		} else {
			return "RB-" + getVersionIgnorePatch(buildId);
		}
	}

	public static String getVersion(String buildId) {
		return buildId.substring(0, buildId.lastIndexOf("."));
	}

	public static String getFirstVersion(String buildId) {
		String[] items = buildId.split("\\.");
		return items[0];
	}

	public static String getSecondVersion(String buildId) {
		String[] items = buildId.split("\\.");
		return items[1];
	}

	public static String getVersionIgnorePatch(String buildId) {
		String[] items = buildId.split("\\.");
		int fstNum = Integer.parseInt(items[0]);
		if (fstNum < 9) {
			return getVersion(buildId);
		} else {
			return items[0] + "." + items[1] + "." + 0;
		}
	}

	public static String dateToString(Date date, String fm) {
		SimpleDateFormat dateFormat = new SimpleDateFormat(fm);
		return dateFormat.format(date);
	}

	public static String genMsgId() {
		String seq = ("00000" + genSequenceValue());
		seq = seq.substring(seq.length() - 6);
		return dateToString(new Date(), Constants.MSG_ID_DATE_FM) + "-" + seq;
	}

	private static int sequenceValue = 1;

	private static synchronized int genSequenceValue() {
		if (sequenceValue >= 10000000) {
			sequenceValue = 1;
		}
		return sequenceValue++;
	}
	
	public static String toSimplifiedBuildId(String buildId) {
		if (buildId == null)
			return null;
		int pos = buildId.indexOf("-");
		if (pos != -1) {
			buildId = buildId.substring(0, pos);
		}
		return buildId.trim();
	}
	
	public static boolean isNewBuildNumberSystem(String simplifiedBuildId) {
		if(simplifiedBuildId == null) {
			return false;
		}		
		String curValue = convertNumberSystemToFixedLength(simplifiedBuildId);
		String stdValue = convertNumberSystemToFixedLength("10.1.0.6858");
		return curValue.compareTo(stdValue) >= 0;
	}
	
	public static String convertNumberSystemToFixedLength (String simplifiedBuildId) {
		if(simplifiedBuildId == null) {
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
	
	public static String fixListenFilename(String listenFilename, String buildId, boolean toRegular) {
		boolean isNewNumberSystem = isNewBuildNumberSystem(toSimplifiedBuildId(buildId));
		if (isNewNumberSystem) {
			
			if(toRegular) {
				listenFilename = CommonUtils.replace(listenFilename, "Linux", "linux");	
			} else {
				//Exact
				listenFilename = CommonUtils.replace(listenFilename, "linux", "Linux");
			}			
		}
		return listenFilename;
	}
}
