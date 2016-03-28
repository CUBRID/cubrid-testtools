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
package com.navercorp.cubridqa.cqt.console.bean;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.navercorp.cubridqa.cqt.console.util.EnvGetter;


public class Summary {

	public static final int TYPE_BOTTOM = 1;

	public static final int TYPE_NOT_BOTTOM = 0;

	private int totalCount;

	private int successCount;

	private int failCount;

	private String version;

	private int siteRunTimes;

	public int getSiteRunTimes() {
		return siteRunTimes;
	}

	public void setSiteRunTimes(int siteRunTimes) {
		this.siteRunTimes = siteRunTimes;
	}

	private long totalTime;

	private int type;

	private List<CaseResult> caseList = new ArrayList<CaseResult>();

	private Map<String, Summary> childSummaryMap = new Hashtable<String, Summary>();

	private String resultDir;

	private String catPath;

	public int getFailCount() {
		return failCount;
	}

	public void setFailCount(int failCount) {
		this.failCount = failCount;
	}

	public int getTotalCount() {
		return totalCount;
	}

	public void setTotalCount(int totalCount) {
		this.totalCount = totalCount;
	}

	public long getTotalTime() {
		return totalTime;
	}

	public void setTotalTime(long totalTime) {
		this.totalTime = totalTime;
	}

	public List<CaseResult> getCaseList() {
		return caseList;
	}

	public String toString() {
		StringBuilder ret = new StringBuilder();
		ret
				.append("total:" + totalCount
						+ System.getProperty("line.separator"));
		ret.append("success:" + successCount
				+ System.getProperty("line.separator"));
		ret.append("fail:" + failCount + System.getProperty("line.separator"));
		ret.append("totalTime:" + (totalTime) + "ms"
				+ System.getProperty("line.separator"));
		ret.append("SiteRunTimes:" + (siteRunTimes) + "times"
				+ System.getProperty("line.separator"));
		if(EnvGetter.getenv("MSG_ID") != null && EnvGetter.getenv("MSG_ID").length() > 0)
			ret.append("msg_id:" + EnvGetter.getenv("MSG_ID") + System.getProperty("line.separator"));
		if (this.getType() == Summary.TYPE_BOTTOM) {
			for (int i = 0; i < caseList.size(); i++) {
				CaseResult caseResult = (CaseResult) caseList.get(i);
				String caseFile = caseResult.getCaseFile();
				String ok = "";
				if (caseResult.isShouldRun()) {
					ok = caseResult.isSuccessFul() ? "ok" : "nok";
				}
				ret.append(caseFile + ":" + ok + "    "
						+ caseResult.getTotalTime() + "ms"
						+ System.getProperty("line.separator"));
			}
		} else {
			Iterator<Summary> iter = childSummaryMap.values().iterator();
			while (iter.hasNext()) {
				Summary childSummary = (Summary) iter.next();
				String resultDir = childSummary.getCatPath() + "/";
				ret.append(resultDir + ":" + childSummary.getTotalCount()
						+ "    " + childSummary.getSuccessCount() + "    "
						+ childSummary.getFailCount()
						+ System.getProperty("line.separator"));
			}
		}
		return ret.toString();
	}

	public String getResultDir() {
		return resultDir;
	}

	public void setResultDir(String resultDir) {
		this.resultDir = resultDir;
	}

	public int getType() {
		return type;
	}

	public void setType(int type) {
		this.type = type;
	}

	public Map<String, Summary> getChildSummaryMap() {
		return childSummaryMap;
	}

	public String getCatPath() {
		return catPath;
	}

	public void setCatPath(String catPath) {
		this.catPath = catPath;
	}

	public int getSuccessCount() {
		return successCount;
	}

	public void setSuccessCount(int successCount) {
		this.successCount = successCount;
	}

	public String getVersion() {
		return version;
	}

	public void setVersion(String version) {
		this.version = version;
	}

}
