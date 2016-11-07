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
/**
 * Class Description
 * <pre>
 * Filename        : ProcessMonitor.java 
 * Date			   : Jul 30, 2008
 * Creator         : Anson Cheung
 * Revised Description
 * ----------------------------------------------
 * ver	revised date	reviser	revised contents
 * 0.1    Jul 30, 2008        Anson            create
 * ----------------------------------------------
 * 
 *</pre>
 */
package com.navercorp.cubridqa.cqt.console.bean;

public class ProcessMonitor {
	public static final int Status_Stoped = 0;
	public static final int Status_Stoping = 1;
	public static final int Status_Started = 2;
	public static final int Status_Starting = 3;
	private String processName;
	private String processDesc;
	private long startTime;
	private long endTime;
	private int percentage;
	private int successFile;
	private int completeFile;
	private int failedFile;
	private int allFile;
	private int currentstate = 3;
	private long currentprocessid;

	public long getCurrentprocessid() {
		return currentprocessid;
	}

	/**
	 * refer to CaseResult.TYPE_GROOVY
	 * 
	 * @return
	 */
	public int getCurrentfiletype() {
		return currentfiletype;
	}

	public void setCurrentprocessid(long currentprocessid) {
		this.currentprocessid = currentprocessid;
	}

	public void setCurrentfiletype(int currentfiletype) {
		this.currentfiletype = currentfiletype;
	}

	private int currentfiletype;

	/**
	 * 
	 * @return
	 */
	public int getCurrentstate() {
		return currentstate;
	}

	/**
	 * 
	 * @param currentstate
	 *            ProcessMonitor.Status_xxxx
	 */
	public void setCurrentstate(int currentstate) {
		this.currentstate = currentstate;
	}

	public String getProcessName() {
		return processName;
	}

	public String getProcessDesc() {
		return processDesc;
	}

	public long getStartTime() {
		return startTime;
	}

	public long getEndTime() {
		return endTime;
	}

	/**
	 * get the complete percentage
	 * 
	 * @return
	 */
	public int getPercentage() {
		if (this.currentstate == this.Status_Stoped) {
			if (completeFile == allFile)
				return 100;
			else
				return -1;
		}

		if (allFile == 0)
			return 0;
		else
			return (100 * (completeFile + failedFile) / allFile);
	}

	public int getSuccessFile() {
		return successFile;
	}

	public int getFailedFile() {
		return failedFile;
	}

	public int getAllFile() {
		return allFile;
	}

	public void setProcessName(String processName) {
		this.processName = processName;
	}

	public void setProcessDesc(String processDesc) {
		this.processDesc = processDesc;
	}

	public void setStartTime(long startTime) {
		this.startTime = startTime;
	}

	public void setEndTime(long endTime) {
		this.endTime = endTime;
	}

	public void setSuccessFile(int successFile) {
		this.successFile = successFile;
	}

	public void setFailedFile(int failedFile) {
		this.failedFile = failedFile;
	}

	public void setAllFile(int allFile) {
		this.allFile = allFile;
	}

	public int getCompleteFile() {
		return completeFile;
	}

	public void setCompleteFile(int completeFile) {
		this.completeFile = completeFile;
	}

	public void doException() {

		this.currentstate = this.Status_Stoped;
	}

	private static int recentlyPer = 0;

	private void CallBackComplete() {
		this.completeFile = this.allFile;
	}

}
