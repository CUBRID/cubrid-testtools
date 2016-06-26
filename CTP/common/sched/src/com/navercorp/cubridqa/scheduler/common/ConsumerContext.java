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
package com.navercorp.cubridqa.scheduler.common;

public class ConsumerContext {

	private String build_URL;
	private String build_Create_Time;
	private String build_Bit;
	private String build_Send_Time;
	private String messageType;
	private String queueName;
	private boolean isDubugEnv;
	private boolean isStarted;
	private boolean isFinished;
	private boolean isOnlyMax;
	
	public boolean isOnlyMax() {
		return isOnlyMax;
	}

	public void setOnlyMax(boolean isOnlyMax) {
		this.isOnlyMax = isOnlyMax;
	}

	public ConsumerContext() {
		this.isDubugEnv = false;
		this.isFinished = false;
		this.isStarted = false;
	}

	public String getBuild_URL() {
		return build_URL;
	}

	public void setBuild_URL(String build_URL) {
		this.build_URL = build_URL;
	}

	public String getBuild_Create_Time() {
		return build_Create_Time;
	}

	public void setBuild_Create_Time(String build_Create_Time) {
		this.build_Create_Time = build_Create_Time;
	}

	public String getBuild_Bit() {
		return build_Bit;
	}

	public void setBuild_Bit(String build_Bit) {
		this.build_Bit = build_Bit;
	}

	public String getBuild_Send_Time() {
		return build_Send_Time;
	}

	public void setBuild_Send_Time(String build_Send_Time) {
		this.build_Send_Time = build_Send_Time;
	}

	public String getMessageType() {
		return messageType;
	}

	public void setMessageType(String messageType) {
		this.messageType = messageType;
	}

	public boolean isStarted() {
		return isStarted;
	}

	public void setStarted(boolean isStarted) {
		this.isStarted = isStarted;
	}

	public boolean isFinished() {
		return isFinished;
	}

	public void setFinished(boolean isFinished) {
		this.isFinished = isFinished;
	}

	public String getQueueName() {
		return queueName;
	}

	public void setQueueName(String queueName) {
		this.queueName = queueName;
	}

	public boolean getIsDubugEnv() {
		return isDubugEnv;
	}

	public void setIsDubugEnv(boolean isDubugEnv) {
		this.isDubugEnv = isDubugEnv;
	}

}
