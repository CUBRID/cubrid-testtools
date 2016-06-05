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

import javax.jms.JMSException;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.pool.PooledConnection;
import org.apache.activemq.pool.PooledConnectionFactory;

public class MQPoolUtil {

	private static PooledConnection conn;
	private int maxConnections=5;
	private int idleTimeout=500;
	private int expiryTimeout=1000;
	
	
	private String Url = "tcp://localhost:61616";
	private int maxmunActive=1;
	
	
	//default value for below variables
	private int initialReconnectDelay = 1000;
	private int timeout = 3000;
	private int startupMaxReconnectAttempts = 2;

	public MQPoolUtil() {

	}

	public MQPoolUtil(String url) {
		String tmp = url;
		this.Url = "failover:(" + tmp + "?initialReconnectDelay="
				+ this.initialReconnectDelay + "&timeout=" + this.timeout
				+ "&startupMaxReconnectAttempts=" + startupMaxReconnectAttempts;
	}
	
	public MQPoolUtil(String url, int initialReconnectDelay, int timeout, int startupMaxReconnectAttempts) {
		String tmp = url;
		this.Url = "failover:(" + tmp + "?initialReconnectDelay="
				+ this.initialReconnectDelay + "&timeout=" + this.timeout
				+ "&startupMaxReconnectAttempts=" + startupMaxReconnectAttempts;
	}

	public void init() {
		String url=getUrl();
		ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(url);
		
		factory.setMaxThreadPoolSize(getMaxmunActive());

		try {
			PooledConnectionFactory poolFactory = new PooledConnectionFactory(
					factory);
			conn = (PooledConnection) poolFactory.createConnection();
			conn.start();

		} catch (JMSException e) {
			e.printStackTrace();
		}
	}

	public static void destroy() throws Exception {
		if (conn != null) {
			conn.close();
		}
	}

	public static PooledConnection getConn() {

		return conn;
	}

	public static void setConn(PooledConnection conn) {

		MQPoolUtil.conn = conn;
	}
	
	public int getMaxmunActive() {
		return maxmunActive;
	}

	public void setMaxmunActive(int maxmunActive) {
		this.maxmunActive = maxmunActive;
	}

	public int getMaxConnections() {
		return maxConnections;
	}

	public void setMaxConnections(int maxConnections) {
		this.maxConnections = maxConnections;
	}

	public int getIdleTimeout() {
		return idleTimeout;
	}

	public void setIdleTimeout(int idleTimeout) {
		this.idleTimeout = idleTimeout;
	}

	public int getExpiryTimeout() {
		return expiryTimeout;
	}

	public void setExpiryTimeout(int expiryTimeout) {
		this.expiryTimeout = expiryTimeout;
	}

	public String getUrl() {
		return Url;
	}

}
