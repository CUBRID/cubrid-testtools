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
import java.util.Collections;
import java.util.List;

import com.navercorp.cubridqa.cqt.console.util.PropertiesUtil;
import com.thoughtworks.xstream.annotations.XStreamAlias;

@XStreamAlias(value = "summary")
public class SummaryInfo implements Comparable {
	public SummaryInfo() {
		this.localPath = PropertiesUtil.getValue("local.path");
	}

	public static final String ALIAS = "summary";

	private SummaryInfo parent;

	private String localPath;

	private List<SummaryInfo> childList = new ArrayList<SummaryInfo>();

	private String resultDir;

	private String catPath;

	private String name;

	private int totalCount;
	private int siteRunTimes;
	private int successCount;

	private int failCount;

	private long totalTime;

	private int type;

	private String version;

	private List<CaseResult> okList = new ArrayList<CaseResult>();

	private List<CaseResult> nokList = new ArrayList<CaseResult>();

	private List<CaseResult> notRunList = new ArrayList<CaseResult>();

	public String getCatPath() {
		return catPath;
	}

	public void setCatPath(String catPath) {
		this.catPath = catPath;
	}

	public List<SummaryInfo> getChildList() {
		return childList;
	}

	public void addChild(SummaryInfo child) {
		this.childList.add(child);
		Collections.sort(childList);
	}

	public int getFailCount() {
		return failCount;
	}

	public void setFailCount(int failCount) {
		this.failCount = failCount;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public List<CaseResult> getNokList() {
		return nokList;
	}

	public void addNokCase(CaseResult caseResult) {
		this.nokList.add(caseResult);
	}

	public List<CaseResult> getOkList() {
		return okList;
	}

	public void addOkCase(CaseResult caseResult) {
		this.okList.add(caseResult);
	}

	public List<CaseResult> getNotRunList() {
		return notRunList;
	}

	public void addNotRunCase(CaseResult caseResult) {
		this.notRunList.add(caseResult);
	}

	public SummaryInfo getParent() {
		return parent;
	}

	public void setParent(SummaryInfo parent) {
		this.parent = parent;
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

	public int getType() {
		return type;
	}

	public void setType(int type) {
		this.type = type;
	}

	public String getResultDir() {
		return resultDir;
	}

	public void setResultDir(String path) {
		this.resultDir = path;
	}

	public int getSuccessCount() {
		return successCount;
	}

	public void setSuccessCount(int successCount) {
		this.successCount = successCount;
	}

	public int compareTo(Object o) {
		SummaryInfo compareObject = (SummaryInfo) o;

		if (this.getName() == null || compareObject == null || compareObject.getName() == null) {
			return 0;
		}
		return this.getName().compareTo(compareObject.getName());
	}

	public String getLocalPath() {
		return localPath;
	}

	public void setLocalPath(String localPath) {
		this.localPath = localPath;
	}

	public int getSiteRunTimes() {
		return siteRunTimes;
	}

	public void setSiteRunTimes(int siteRunTimes) {
		this.siteRunTimes = siteRunTimes;
	}

	public String getVersion() {
		return version;
	}

	public void setVersion(String version) {
		this.version = version;
	}

}
