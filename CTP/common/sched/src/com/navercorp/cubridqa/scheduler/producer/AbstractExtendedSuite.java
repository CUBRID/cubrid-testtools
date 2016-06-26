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

public abstract class AbstractExtendedSuite {
	
	Configure conf;
	HashMap<String, Properties> extendedMap;
	Properties extendedConfig;
	
	protected AbstractExtendedSuite(Configure conf, String extendedConfig) {
		this.conf = conf;
		try{
			this.extendedConfig = CommonUtils.getProperties(Constants.CTP_HOME + File.separator + "conf" + File.separator + extendedConfig);
		}catch(Exception e) {
			this.extendedConfig = new Properties();
		}
		
		this.extendedMap = new HashMap<String, Properties>();
		
		String extendedDir = CommonUtils.concatFile(Constants.CTP_HOME + File.separator + "conf", extendedConfig.substring(0, extendedConfig.lastIndexOf(".")));
		
		File[] files = new File(extendedDir).listFiles();
		for(File f: files) {
			if(f.getName().endsWith(".msg")) {
				this.extendedMap.put(f.getName(), loadMsgProperties(f));
			}
		}
	}
	
//	public static void init(Configure conf, String extendedDir, String extendedConfig) {
//		instance = new AbstractExtendedSuite(conf, extendedDir, extendedConfig);
//	}
	
	private Properties loadMsgProperties(File file) {
		Properties props = null;
		try{
			props = CommonUtils.getProperties(file.getAbsolutePath());
		} catch(Exception e) {
			e.printStackTrace();
		}
		
		if(props ==null) return props;
		
		loadMsgProperties(file, props);
		
		props.put(Constants.MSG_MSG_FILEID, CommonUtils.replace(file.getName(), ".msg", ""));				
		
		return props;
	}
	
	public String getProperty(String key) {
		return extendedConfig.getProperty(key);
	}
	
	public abstract boolean loadMsgProperties(File file, Properties props);
	
	public void setCompatibility(String name, String value){
		this.extendedConfig.setProperty(name, value);
	}
	
	public ArrayList<Properties> getMsgProperties(String extendedKey) {
		String value = this.extendedConfig.getProperty(extendedKey);
		if(value == null) return null;
		String[] list = value.split(",");
		
		ArrayList<Properties> resultList = new ArrayList<Properties>();
		
		Properties p;
		for(String item: list) {
			if(item.trim().equals("")) continue;
			
			p = this.extendedMap.get(item.trim());
			
			if(p==null) {
				continue;
			}
			resultList.add(p);
		}
		return resultList;
	}
	
	//[0] buildId, [1] storeId
	protected String[] getExactBuildInfo(String buildId) {
		
		File[] storeArray = new File(conf.getRepoRoot()).listFiles();
		File[] buildArray;		
		File justFile = null;
		
		int searchIndexForMax = buildId.indexOf("{max}");
		String maxSubId = "0";
		String suffixInBuildId = "";
		if (searchIndexForMax == -1 ) {
			searchIndexForMax = buildId.indexOf("{dmax}");
			maxSubId = "0.0";
		}		
		if (searchIndexForMax == -1 ) {
			searchIndexForMax = buildId.indexOf("{tmax}");
			maxSubId = "0.0.0";
		}
		if (searchIndexForMax == -1 ) {
			searchIndexForMax = buildId.indexOf("{qmax}");
			maxSubId = "0.0.0.0";
		}
		String searchPrefix = null;
		if (searchIndexForMax != -1) {
			searchPrefix = buildId.substring(0, searchIndexForMax);
		} else {
			searchPrefix = buildId;
		}
		
		String curBuildId;
		String curSimplifiedBuildId;
		String curSuffixInBuildId = null;
		boolean hitOne = false;
		
		for(File storeFile: storeArray){
			if (searchIndexForMax == -1 && justFile != null) break;			
			if(storeFile.isDirectory() == false) continue;
			
			buildArray = storeFile.listFiles();
			for(File buildFile: buildArray){
				if(buildFile.isDirectory() == false) continue;
				
				curBuildId = buildFile.getName();
				curSimplifiedBuildId = CommonUtils.toSimplifiedBuildId(curBuildId);
				hitOne = false;
				
				
				if (conf.isAcceptableBuildInRepository(curSimplifiedBuildId) && curBuildId.startsWith(searchPrefix)) {	
					curSuffixInBuildId = getSuffixInBuildId(curBuildId);
					
					if (searchIndexForMax == -1) {
						hitOne = curSuffixInBuildId.length() >= suffixInBuildId.length();
					} else {
						String curMaxSubId = getMaxSubId(maxSubId, curSimplifiedBuildId);
						
						if (curMaxSubId != null) {
							if (curMaxSubId.equals(maxSubId)) {
								if (curSimplifiedBuildId.endsWith(maxSubId)) {
									hitOne = curSuffixInBuildId.length() >= suffixInBuildId.length();
								}
							} else {
								hitOne = true;
							}
						}
						if (hitOne) {
							maxSubId = curMaxSubId;
						}
					}

					if (hitOne) {						
						suffixInBuildId = curSuffixInBuildId;
						justFile = buildFile;
					}
				}				
			}
		}
		
		if(justFile == null) return null;
		
		String[] result = new String[3];
		result[0] = justFile.getName();
		result[1] = justFile.getParentFile().getName();
		
		if(searchIndexForMax!=-1) {
			result[2] = maxSubId;	
		} else {
			result[2] = null;
		}		
		return result;
	}

	private static String getMaxSubId(String sub, String buildId) {
		if (sub == null || buildId == null) {
 			return null;
		}
		String[] s1 = sub.split("\\.");
		String[] s2 = buildId.split("\\.");
		
		if(s1.length == 0 || s2.length != 4) {
			return null;
		}

		int i1, i2;
		for (int i = 0; i < s1.length; i++) {
			try{
				i1 = Integer.parseInt(s1[i]);
				i2 = Integer.parseInt(s2[(s2.length - s1.length) + i]);
			} catch(Exception e) {
				return null;
			}
			if (i1 < i2) {
				StringBuilder r = new StringBuilder();
				for (int j = s2.length - s1.length; j < s2.length; j++) {
					r.append(s2[j]);
					if(j<s2.length-1) r.append(".");
				}
				return r.toString();
			} else if (i1 > i2) {
				return sub;
			}
		}
		
		return sub;
	}
	
	private static String getSuffixInBuildId(String buildId) {
		String suffix = "";
		int pos = buildId.indexOf("-");
		if (pos != -1) {
			suffix = buildId.substring(pos);
		}
		return suffix;
	}
	
	public static void main(String[] args) {
		System.out.println(getMaxSubId("11.0003", "9.2.2.0002"));
	}
}
