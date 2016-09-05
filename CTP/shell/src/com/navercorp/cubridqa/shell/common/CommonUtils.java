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

package com.navercorp.cubridqa.shell.common;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.navercorp.cubridqa.shell.main.Context;

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
			if (str.charAt(i) == ' ' || str.charAt(i) == '\n'
					|| str.charAt(i) == '\r' || str.charAt(i) == '\t') {
				count++;
			} else {
				break;
			}
		}
		return str.substring(0, len - count);

	}
	
	public static String getBuildVersionInfo(SSHConnect ssh) {
		String ver = null;
		if(ssh == null) return ver;
		
		try {
			ver = ssh.execute(Constants.GET_VERSION_SCRIPT);
		} catch (NumberFormatException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}

		return ver;
	}
	
	public static ArrayList<String[]> extractTableToBeVerified(String input, String flag) {
		
		ArrayList<String[]> list = new ArrayList<String[]>();
		if(input == null) return list;
		
		Pattern pattern = Pattern.compile("'"+flag+"'\\s*'(.*?)'\\s*([0-9]*)");
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
	
	public static boolean isWindowsPlatformForController(){
		return com.navercorp.cubridqa.common.CommonUtils.isWindowsPlatform();
	}
	
	public static String getFileContent(String filename) throws IOException {
		File file = new File(filename);
		if(!file.exists()) {
			return null;
		}
		StringBuffer result = new StringBuffer();
		FileInputStream fis = new FileInputStream(file);
		InputStreamReader reader = new InputStreamReader(fis, "UTF-8");
		
		LineNumberReader lineReader = new LineNumberReader(reader);
		String line;
		
		while((line = lineReader.readLine()) != null) {
			if(line.trim().equals("")) continue;
			result.append(line.trim()).append(Constants.LINE_SEPARATOR);
		}
		lineReader.close();
		reader.close();
		fis.close();
		return result.toString();
	}
	
	public static ArrayList<String> getLineList(String filename) throws IOException {
		
		File file = new File(filename);
		if(!file.exists()) {
			return null;
		}
		ArrayList<String> resultList = new ArrayList<String>();
		FileInputStream fis = new FileInputStream(file);
		InputStreamReader reader = new InputStreamReader(fis, "UTF-8");
		
		LineNumberReader lineReader = new LineNumberReader(reader);
		String line;
		
		while((line = lineReader.readLine()) != null) {
			if(line.trim().equals("")) continue;
			resultList.add(line.trim());
		}
		lineReader.close();
		reader.close();
		fis.close();
		return resultList;
	}
	
	public static Properties getProperties(String filename) throws IOException {
		Properties props = new Properties();
		if (isEmpty(filename)) {
			return props;
		}
		
		FileInputStream fis = new FileInputStream(filename);		
		props.load(fis);
		fis.close();
		return  props;
	}
	
	public static Properties getPropertiesWithPriority(String filename) throws IOException {
		Properties props = getProperties(filename);
		props.putAll(System.getProperties());
		return props;
	}
	
	public static void writeProperties(String filename,Properties props) throws IOException {
        File f = new File(filename);
        OutputStream out = new FileOutputStream( f );
        props.store(out,"");
	}
	
	public static void sleep(int sec){
		try {
			Thread.sleep(sec*1000);
		} catch (InterruptedException e) {				
		}
	}
	
	public static String resetProcess(SSHConnect ssh, boolean isWindows) {
		try {			
			if(isWindows) {
				return ssh.execute(Constants.WIN_KILL_PROCESS) + ssh.execute(Constants.WIN_KILL_PROCESS_NATIVE, true);
			} else {
				return ssh.execute(Constants.LIN_KILL_PROCESS);
			}
		} catch (Exception e) {
			return "fail to reset processes: " + e.getMessage();
		}
	}
	
	public static String dateToString(Date date, String fm) {
		SimpleDateFormat dateFormat = new SimpleDateFormat(fm);
		return dateFormat.format( date );
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
	
	public static void generateFailBackupPackage(Context context){
		if(isWindowsPlatformForController()) return;
		
		String backupFileName = "";
		Calendar cal = Calendar.getInstance();
		int year = cal.get(Calendar.YEAR);
		int month = cal.get(Calendar.MONTH) + 1;
		int day = cal.get(Calendar.DAY_OF_MONTH);
		int hour = cal.get(Calendar.HOUR);
		int minute = cal.get(Calendar.MINUTE);
		int second = cal.get(Calendar.SECOND);
		String curTimestamp = year + "." + month + "." + day + "_" + hour + "."
				+ minute + "." + second;
		String build = context.getTestBuild();
		String bit = context.getVersion();
		Integer taskId = context.getTaskId();

		backupFileName = "shell_result_" + build + "_" + bit + "_" + taskId
				+ "_" + curTimestamp + ".tar.gz";
		LocalInvoker.exec(
				"cd " + context.getRootLogDir() + "; tar zvcf "
						+ backupFileName + " " + context.getCurrentLogDir(), false, false);
	}
	
	public static boolean isEmpty(String s) {
		return com.navercorp.cubridqa.common.CommonUtils.isEmpty(s);
	}
	
	public static void main(String[] args) throws IOException {
		System.out.println(getExactFilename("conf/core.1234"));
		System.out.println(getExactFilename("conf\\core.1234"));
		System.out.println(getExactFilename("/core\\conf\\core.1234"));
	}
	
}
