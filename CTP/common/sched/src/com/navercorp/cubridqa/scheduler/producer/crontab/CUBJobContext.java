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
package com.navercorp.cubridqa.scheduler.producer.crontab;

import java.util.ArrayList;
import java.util.Properties;

import com.navercorp.cubridqa.scheduler.common.CommonUtils;
import com.navercorp.cubridqa.scheduler.producer.Configure;

public class CUBJobContext {

	Configure conf;
	String jobId;
	String service;
	String crontab;
	String acceptversions;
	String denyversions;
	String listenFilename;
	ArrayList<String> listenMoreFilenames;
	String tests;
	String pkgBits;
	String pkgType;

	public CUBJobContext(Configure conf, String jobId) {
		this.conf = conf;
		Properties props = conf.getProperties();
		this.jobId = jobId;
		this.service = props.getProperty(jobId + ".service");
		this.crontab = props.getProperty(jobId + ".crontab");
		this.acceptversions = props.getProperty(jobId + ".acceptversions");
		if (this.acceptversions != null) {
			this.acceptversions = this.acceptversions.trim().toUpperCase();
			if (this.acceptversions.equals("")) {
				this.acceptversions = null;
			}
		}
		this.denyversions = props.getProperty(jobId + ".denyversions");
		if (this.denyversions != null) {
			this.denyversions = this.denyversions.trim().toUpperCase();
			if (this.denyversions.equals("")) {
				this.denyversions = null;
			}
		}
		this.listenFilename = props.getProperty(jobId + ".listenfile").trim();

		String fname;
		int index = 1;
		while (true) {
			fname = props.getProperty(jobId + ".listenfile." + index);
			if (fname == null || fname.trim().equals("")) {
				break;
			}
			if (this.listenMoreFilenames == null) {
				this.listenMoreFilenames = new ArrayList<String>();
			}
			this.listenMoreFilenames.add(fname.trim());
			index++;
		}

		this.tests = props.getProperty(jobId + ".tests");
		this.pkgBits = props.getProperty(jobId + ".package_bits");
		this.pkgType = props.getProperty(jobId + ".package_type");
		if (this.acceptversions != null && denyversions != null) {
			System.out.println("[Scheduler] Error in " + jobId + ". Should enable acceptversions or denyversions. Cannot enable both of them.");
		}
	}

	public ArrayList<String> getListenMoreFilenames() {
		return this.listenMoreFilenames;
	}

	public String getService() {
		return service;
	}

	public String getCrontab() {
		return crontab;
	}

	public String getAcceptversions() {
		return acceptversions;
	}

	public String getDenyversions() {
		return denyversions;
	}

	public String getListenFilename() {
		return listenFilename;
	}

	public String getTests() {
		return tests;
	}

	public String getJobId() {
		return this.jobId;
	}

	public String toString() {
		return "service=" + this.service + ", crontab=" + crontab + ", acceptversions=" + acceptversions + ", denyversions= " + denyversions + ", listen_file=" + listenFilename + ", tests=" + tests;
	}

	public Configure getMainConfigure() {
		return this.conf;
	}

	public String getPkgBits() {
		return pkgBits;
	}

	public String getPkgType() {
		return pkgType;
	}

	public Properties getCommonMKEYProps() {
		ArrayList<String[]> mlist = conf.filterProps(jobId + ".MKEY_", null, null);
		Properties props = new Properties();
		String key, value;
		for (String[] arr : mlist) {
			key = arr[0];
			value = arr[1];
			props.put(CommonUtils.replace(key, jobId + ".", ""), value);
		}
		return props;
	}

	public ArrayList<Properties> getTestList() {
		ArrayList<String[]> mlist = conf.filterProps(jobId + ".test.", ".queue", null);
		ArrayList<Properties> testList = new ArrayList<Properties>();
		ArrayList<String[]> subList;
		Properties p;
		String subKey;
		for (String[] arr : mlist) {
			subKey = CommonUtils.replace(arr[0], ".queue", "");
			subList = conf.filterProps(subKey, null, null);
			p = new Properties();
			for (String[] subarr : subList) {
				p.put(CommonUtils.replace(subarr[0], subKey + ".", ""), subarr[1]);
			}
			testList.add(p);
		}
		return testList;
	}
}
