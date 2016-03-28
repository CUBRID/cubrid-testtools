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

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Date;
import java.util.Map;

import org.apache.commons.io.IOUtils;

import com.navercorp.cubridqa.cqt.console.util.SystemUtil;

public abstract class EnvSetter {

	private static boolean USE_EXCEPT = true;

	private static final String REG_ADD_CMD_WIN = "cmd /c  setx ";
	private static final String USER_HOME = SystemUtil.getUserHomePath();
	public static final String ETC_PRO_LIN = USER_HOME + "/.bash_profile";

	private static final String REF_ETC_PRO_LIN = "source " + ETC_PRO_LIN;

	/**
	 * set environment parameter to window or linux .if success ,return
	 * true,else false .
	 * 
	 * @param key
	 * @param value
	 * @return
	 */
	public static boolean setEnv(String key, String value) {
		String os = SystemUtil.getOS();
		if (os.startsWith("window")) {
			return addReg4Win(key, value);
		} else {
			return addProfile4Lin(key, value);
		}
	}

	/**
	 * set environment parameter to window or linux .if success ,return
	 * true,else false
	 * 
	 * @param addmap
	 * @return
	 */
	public static boolean setEnv(Map<String, String> addmap) {
		String os = SystemUtil.getOS();
		if (os.startsWith("window")) {
			return addReg4Win(addmap);
		} else {
			return addProfile4Lin(addmap);
		}
	}

	/**
	 * register parameter to windows platform through Registry.
	 * 
	 * @param map
	 * @return
	 */
	private static boolean addReg4Win(Map<String, String> map) {
		boolean result = true;
		try {
			for (String key : map.keySet()) {
				if (false == addReg4Win(key, map.get(key))) {
					result = false;
				}
			}
		} catch (Throwable t) {
			t.printStackTrace();
			result = false;
		}
		return result;
	}

	/**
	 * register parameter to linux platform through profile
	 * 
	 * @param map
	 * @return
	 */
	@SuppressWarnings("deprecation")
	private static boolean addProfile4Lin(Map<String, String> map) {
		try {
			Date date = new Date();
			
			String ymd = (date.getYear() + 1900) + "-" + (1 + date.getMonth())
					+ "-" + date.getDay();
			String old = IOUtils.toString(new FileInputStream(ETC_PRO_LIN));
			String newContents = old;
			newContents += "\n#---------------------------------------------";
			newContents += "\n#Import Cubridqa Env " + ymd + " begin";
			newContents += "\n#---------------------------------------------";
			for (String key : map.keySet()) {
				newContents += "\n" + "export " + key + "=" + map.get(key);
			}
			newContents += "\n#---------------------------------------------";
			newContents += "\n#Import Cubridqa Env " + ymd + " end";
			newContents += "\n#---------------------------------------------";
			IOUtils.write(newContents, new FileOutputStream(ETC_PRO_LIN));
		} catch (Throwable t) {
			return false;
		}

		return true;
	}

	/**
	 * register parameter to windows platform through Registry.
	 * 
	 * @param map
	 * @return
	 */
	private static boolean addReg4Win(String key, String value) {
		boolean result = true;
		try {
			String cmdLine = REG_ADD_CMD_WIN + key + " " + value;
			Runtime.getRuntime().exec(cmdLine);

		} catch (Throwable e) {
			e.printStackTrace();
			System.out.println("error add in set windows env(key=" + key
					+ ",value=" + value + ")");
			result = false;
		}
		return result;
	}

	/**
	 * register parameter to linux platform through profile
	 * 
	 * @param map
	 * @return
	 */
	private static boolean addProfile4Lin(String key, String value) {
		try {
			String old = IOUtils.toString(new FileInputStream(ETC_PRO_LIN));
			String newContents = old + "\n" + "export " + key + "=" + value;
			IOUtils.write(newContents, new FileOutputStream(ETC_PRO_LIN));
		} catch (Throwable t) {
			return false;
		}
		return true;
	}
}
