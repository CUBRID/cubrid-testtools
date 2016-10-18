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

import java.io.BufferedInputStream;
import java.io.File;
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
import java.math.BigInteger;
import java.net.URL;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
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
	
	
	public static boolean isEmpty(String s) {
		if (s == null) {
			return true;
		}
		return s.trim().equals("");
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
	
	public static void main(String[] args){
		String file = "conf/shell.conf";
		String lst = parseInstanceParametersByRole(getConfig(file), "env.instance2", "broker1");
		System.out.println(lst);
	}
	
	public static Properties parsePropertiesByPrefix(Properties config, String prefix){
		Properties prop = new Properties();
		final Iterator<Object> keyIterator = config.keySet().iterator();
		while(keyIterator.hasNext()){
			String key = keyIterator.next().toString();
			if(key.startsWith(prefix)){
				prop.setProperty(key.substring(prefix.length() + 1), config.getProperty(key));
			}
		}
		
		return prop;
	}
	
	public static Properties parsePropertiesByPrefix(Properties config, String prefix, String defaultPrefix) {
	    Properties prop1 = parsePropertiesByPrefix(config, prefix);
	    Properties prop2 = parsePropertiesByPrefix(config, defaultPrefix);
        prop2.putAll(prop1);
        
	    return prop2;
	}
	
	public static String parsePropertiesStringByPrefix(Properties config, String prefix, String defaultPrefix) {
	    Properties prop = parsePropertiesByPrefix(config, prefix, defaultPrefix);
	    if(prop == null) return "";
	    
	    Set<Object> set = prop.keySet();
		Iterator<Object> it = set.iterator();
		String resultList = "";
		String key;
		String val;
		while (it.hasNext()) {
			key = (String) it.next();
			val = prop.getProperty(key);
			resultList += key + "=" + val + "||";
		}
		
		return resultList;
	}
	
	public static String parseInstanceParametersByRole(Properties config, String instancePrefix, String role){
		String prefix = instancePrefix + "." + role;
		String defaultPrefix = "default." + role;
		return parsePropertiesStringByPrefix(config, prefix, defaultPrefix);
	}

	public static String concatFile(String p1, String p2){
		String p;
		
		if (p1 == null)
			p1 = "";
		if (p2 == null)
			p2 = "";

		p1 = p1.trim().replace('\\', '/');
		p2 = p2.trim().replace('\\', '/');
		
		if (p1.equals("")) {
			p = p2;
		} else if (p2.equals("")) {
			p = p1;
		} else {
			p = p1 + "/" + p2;
		}  
		
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

	public static boolean checKURLIsAvailable(String urlStr){
		if (isEmpty(urlStr))
			return false;

		boolean res = false;

		URL url;
		try {
			url = new URL(urlStr);
			InputStream in = url.openStream();
			res = true;
		} catch (Exception e1) {
			res = false;
			url = null;
		}

		return res;
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

		if (filename == null)
			return null;

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
		return getLinuxStylePath(path, isCygwinPlatform());
	}
	
	public static String getLinuxStylePath(String path, boolean useCygPath) {
		path = path.trim();
		if (useCygPath) {
			String linStylePath = "";
			int max = 20;
			while (max-- > 0) {
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
		if (path == null)
			return path;

		path = path.trim();

		try {
			File file = new File(path);
			if (file.exists())
				return file.getCanonicalPath();
			if (CommonUtils.isWindowsPlatform()) {
				file = new File(getWindowsStylePath(path));
				if (file.exists()) {
					return file.getCanonicalPath();
				}
			}
		} catch (Exception e) {
			throw new RuntimeException("fail to get fixed path: " + path);
		}
		return path;
	}

	public static void main2(String[] args) throws IOException {
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
	
	
	public static Properties getConfig(InputStream is) {
		try {
			Properties props = new Properties();
			props.load(is);
			return props;
		} catch (IOException e) {
			throw new RuntimeException(e);
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

	public static boolean convertBoolean(String str) {
		return convertBoolean(str, false);
	}

	public static boolean convertBoolean(String value, boolean defaultValue) {
		if (value == null)
			return defaultValue;

		value = value.trim().toUpperCase();
		return value.equals("1") || value.equals("TRUE") || value.equals("YES") || value.equals("T") || value.equals("Y");
	}

	public static String getSystemProperty(String key, String defaultValue, Properties props) {
		if (key == null) {
			return null;
		}
		String value = System.getProperty(key);
		if (value != null && value.trim().equals("") == false) {
			return value.trim();
		}
		value = System.getenv(key);
		if (value != null && value.trim().equals("") == false) {
			return value.trim();
		}

		if (props != null) {
			value = props.getProperty(key);
			if (value != null && value.trim().equals("") == false) {
				return value.trim();
			}
		}
		return defaultValue;
	}
	
	public static int getShellType(boolean supportPureWindows) {
		int shellType;
		if (CommonUtils.isWindowsPlatform()) {
			if (supportPureWindows) {
				shellType = LocalInvoker.SHELL_TYPE_WINDOWS;
			} else {
				shellType = LocalInvoker.SHELL_TYPE_CYGWIN;
			}
		} else {
			shellType = LocalInvoker.SHELL_TYPE_LINUX;
		}
		return shellType;
	}
	
	public static String getSimplifiedBuildId(String cubridPackageUrl) {
		// get test build number
		String sBuildId = null;
		Pattern pattern = Pattern.compile("\\d+\\.\\d+\\.\\d+\\.\\d+");
		Matcher matcher = pattern.matcher(cubridPackageUrl);
		while (matcher.find()) {
			sBuildId = matcher.group();
		}
		return sBuildId;
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
	
	public static String getBuildId(String cubridPackageUrl) {
		String simplifiedBuild = getSimplifiedBuildId(cubridPackageUrl);

		if (isNewBuildNumberSystem(simplifiedBuild)) {
			String buildId;

			int p1 = cubridPackageUrl.lastIndexOf(simplifiedBuild);
			int p2 = cubridPackageUrl.indexOf("-", p1 + simplifiedBuild.length() + 1);
			int p3 = p2 == -1 ? cubridPackageUrl.indexOf(")", p1 + simplifiedBuild.length() + 1) : p2;
			
			if (p3 == -1) {
				p3 = cubridPackageUrl.indexOf(".", p1 + simplifiedBuild.length() + 1);
			}

			buildId = p3 == -1 ? cubridPackageUrl.substring(p1) : cubridPackageUrl.substring(p1, p3);
			return buildId;
		} else {
			return simplifiedBuild;
		}
	}
	
	public static String getBuildBits(String cubridPackageUrl) {
		String version = null;
		int idx1 = cubridPackageUrl.indexOf("_64");
		int idx2 = cubridPackageUrl.indexOf("x64");
		int idx3 = cubridPackageUrl.indexOf("ppc64"); // AIX BUILD.
														// CUBRID-8.4.4.0136-AIX-ppc64.sh
		int idx4 = cubridPackageUrl.indexOf("64bit"); //Parse cubrid_rel result

		if (idx1 >= 0 || idx2 >= 0 || idx3 >= 0 || idx4 >= 0) {
			version = "64bits";
		} else {
			version = "32bits";
		}
		return version;
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
	
	public static String getFileMD5(File file) throws Exception {
		if (!file.isFile() || file.exists() == false ) {
			return null;
		}
		MessageDigest digest = null;
		FileInputStream in = null;
		byte buffer[] = new byte[8192];
		int len;
		try {
			digest = MessageDigest.getInstance("MD5");
			in = new FileInputStream(file);
			while ((len = in.read(buffer)) != -1) {
				digest.update(buffer, 0, len);
			}
			BigInteger bigInt = new BigInteger(1, digest.digest());
			return bigInt.toString(16);
		} finally {
			try {
				in.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
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
	
	public static boolean supportInquireOnExit(String buildId) {
		String arr[] = buildId.split("\\.");
		return Integer.parseInt(arr[0]) >= 10;
	}
	
	public static String translateVariable(String value) {
		if (value == null)
			return value;

		if (Constants.ENV_HOME != null) {
			value = CommonUtils.replace(value, "${HOME}", Constants.ENV_HOME);
		}

		if (Constants.ENV_CTP_HOME != null) {
			value = CommonUtils.replace(value, "${CTP_HOME}", Constants.ENV_CTP_HOME);
		}
		return value;
	}
}
