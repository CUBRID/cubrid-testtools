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

import com.navercorp.cubridqa.cqt.console.util.FileUtil;

public class TestCaseSummary {
	
	private static final String S_FAILLISTTAG = "<scenario>";
	
	private static final String E_FAILLISTTAG = "</scenario>";
	
	private static final String S_CASE = "<case>";
	
	private static final String E_CASE = "</case>";
	
	private static final String S_ANSWER = "<answer>";
	
	private static final String E_ANSWER = "</answer>";
	
	private static final String S_ELAPSETIME = "<elapsetime>";
	
	private static final String E_ELAPSETIME = "</elapsetime>";
	
    private static final String S_RESULT = "<result>";
	
	private static final String E_RESULT = "</result>";
	
	private String caseFile;
	
    private String answerFile;
	
	private String elapseTime;
	
	private String testResult;
	
	public String getCaseFile() {
		return caseFile;
	}
	
	public String toXmlString()
	{
		StringBuffer sb = new StringBuffer();
		sb.append(FileUtil.AddSpace(2) + TestCaseSummary.S_FAILLISTTAG + System.getProperty("line.separator"));
		sb.append(FileUtil.AddSpace(5) + TestCaseSummary.S_CASE + this.caseFile + TestCaseSummary.E_CASE + System.getProperty("line.separator"));
		sb.append(FileUtil.AddSpace(5) + TestCaseSummary.S_ANSWER + this.answerFile + TestCaseSummary.E_ANSWER + System.getProperty("line.separator"));
		sb.append(FileUtil.AddSpace(5) + TestCaseSummary.S_ELAPSETIME + this.elapseTime + TestCaseSummary.E_ELAPSETIME + System.getProperty("line.separator"));
		sb.append(FileUtil.AddSpace(5) + TestCaseSummary.S_RESULT + this.testResult + TestCaseSummary.E_RESULT + System.getProperty("line.separator"));
		sb.append(FileUtil.AddSpace(2) + TestCaseSummary.E_FAILLISTTAG + System.getProperty("line.separator"));
		return sb.toString();
	}

	public void setCaseFile(String caseFile) {
		this.caseFile = caseFile;
	}

	public String getAnswerFile() {
		return answerFile;
	}

	public void setAnswerFile(String answerFile) {
		this.answerFile = answerFile;
	}

	public String getElapseTime() {
		return elapseTime;
	}

	public void setElapseTime(String elapseTime) {
		this.elapseTime = elapseTime;
	}

	public String getTestResult() {
		return testResult;
	}

	public void setTestResult(String testResult) {
		this.testResult = testResult;
	}

}
