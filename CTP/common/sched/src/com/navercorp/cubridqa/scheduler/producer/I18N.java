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
package com.navercorp.cubridqa.scheduler.producer;

import java.io.File;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Properties;

import com.navercorp.cubridqa.scheduler.common.CommonUtils;
import com.navercorp.cubridqa.scheduler.common.Constants;

public class I18N {
private static I18N instance;
	
	Configure conf;
	HashMap<String, Properties> i18nMap;
	Properties in18nConfig;
	
	private I18N(Configure conf) {
		this.conf = conf;
		try{
			this.in18nConfig = CommonUtils.getProperties(Constants.CTP_HOME + File.separator + "conf" + File.separator + "i18n.properties");
		}catch(Exception e) {
			this.in18nConfig = new Properties();
		}
		
		this.i18nMap = new HashMap<String, Properties>();
		
		File[] files = new File(Constants.CTP_HOME + File.separator + "conf" + File.separator + "i18n").listFiles();
		for(File f: files) {
			if(f.getName().endsWith(".msg")) {
				this.i18nMap.put(f.getName(), loadMsgProperties(f));
			}
		}
	}
	
	public static void init(Configure conf) {
		instance = new I18N(conf);
	}
	
	public static I18N getInstance() {
		return instance;
	}
	
	private Properties loadMsgProperties(File file) {
		Properties props = null;
		try{
			props = CommonUtils.getProperties(file.getAbsolutePath());
			props.put(Constants.MSG_I18N_TEST_CATAGORY, CommonUtils.replace(file.getName(), ".msg", ""));
			props.put(Constants.MSG_MSG_FILEID, CommonUtils.replace(file.getName(), ".msg", ""));
		} catch(Exception e) {
			e.printStackTrace();
		}
		
		if(props ==null) return props;
		
		return props;
	}
	
	public void setCompatibility(String name, String value){
		this.in18nConfig.setProperty(name, value);
	}

	
	public ArrayList<Properties> getMsgProperties(String i18nKey) {
		String value = this.in18nConfig.getProperty(i18nKey);
		if(value == null) return null;
		String[] list = value.split(",");
		
		ArrayList<Properties> resultList = new ArrayList<Properties>();
		
		Properties p;
		for(String item: list) {
			if(item.trim().equals("")) continue;
			
			p = this.i18nMap.get(item.trim());
			
			if(p==null) {
				continue;
			}
			resultList.add(p);
		}
		return resultList;
	}
}
