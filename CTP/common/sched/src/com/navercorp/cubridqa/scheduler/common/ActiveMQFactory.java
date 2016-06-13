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

import javax.jms.Connection;


import javax.jms.JMSException;
import javax.jms.Destination;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.TextMessage;

import javax.jms.Session;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.RedeliveryPolicy;

public class ActiveMQFactory {
	
	
	private String user = null;
	private String pwd = null;
	private String url = null;
	private int ackMode = Session.AUTO_ACKNOWLEDGE;
	private boolean trans = false;
	private Connection conn=null;
	private RedeliveryPolicy redeliveryPolicy=null;


	public RedeliveryPolicy getRedeliveryPolicy() {
		
		return redeliveryPolicy;
	}

	public void setRedeliveryPolicy(RedeliveryPolicy redeliveryPolicy) {
		this.redeliveryPolicy = redeliveryPolicy;
	}

	public ActiveMQFactory() {
		super();
	}
	
	public ActiveMQFactory(String user, String password, String url) throws JMSException, Exception {
		this.user=user;
		this.pwd=password;
		this.url=url;
		this.conn=createConnectionWithParameters(user, password, url);
	}

	public boolean isTrans() {
		return trans;
	}

	public void setTrans(boolean trans) {
		this.trans = trans;
	}

	public int getAckMode() {
		return ackMode;
	}

	public String getUser() {
		return user;
	}

	public void setUser(String user) {
		this.user = user;
	}

	public String getPwd() {
		return pwd;
	}

	public void setPwd(String pwd) {
		this.pwd = pwd;
	}

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public String getClientID() {
		return clientID;
	}

	public void setClientID(String clientID) {
		this.clientID = clientID;
	}
	
	public Connection getConn() {
		return conn;
	}
	
	public Session getSec() throws JMSException {
		return createSessionWithParameters(this.conn, this.trans, this.ackMode);
	}

	public void setAckMode(String ackMode) {
		if ("CLIENT_ACKNOWLEDGE".equals(ackMode)) {
			this.ackMode = Session.CLIENT_ACKNOWLEDGE;
		}
		if ("AUTO_ACKNOWLEDGE".equals(ackMode)) {
			this.ackMode = Session.AUTO_ACKNOWLEDGE;
		}
		if ("DUPS_OK_ACKNOWLEDGE".equals(ackMode)) {
			this.ackMode = Session.DUPS_OK_ACKNOWLEDGE;
		}
		if ("SESSION_TRANSACTED".equals(ackMode)) {
			this.ackMode = Session.SESSION_TRANSACTED;
		}
	}

	private String clientID = null;

	protected Connection createConnection() throws JMSException, Exception {
		ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory(
				user, pwd, url);
		
		Connection connection = connectionFactory.createConnection();
		connection.start();
		return connection;
	}

	protected Connection createConnectionWithID(String id) throws JMSException,
			Exception {
		ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory(
				user, pwd, url);
		Connection connection = connectionFactory.createConnection();
		if (id != null) {
			connection.setClientID(id);
		}
		connection.start();
		return connection;
	}

	protected Connection createConnectionWithParameters(String user, String password, String url)
			throws JMSException, Exception {

		ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory(
				user, password, url);
		RedeliveryPolicy policy=connectionFactory.getRedeliveryPolicy();
		policy.setMaximumRedeliveries(-1);
		Connection connection = connectionFactory.createConnection();
		connection.start();
		return connection;
	}

	protected Session createSession(Connection connection, boolean trans,
			int actMode) throws JMSException {
		Session session = connection.createSession(trans, ackMode);

		return session;
	}
	
	protected Session createSessionWithParameters(Connection connection, boolean tranctions, int ackMode) throws JMSException {
		Session session = connection.createSession(trans, ackMode);

		return session;
	}

	protected MessageProducer createProducer(Session session, String qunuename,
			int deMode, int tToLive) throws Exception {
		Destination destination = session.createQueue(qunuename);
		MessageProducer producer = session.createProducer(destination);
		producer.setDeliveryMode(deMode);

		if (tToLive != 0)
			producer.setTimeToLive(tToLive);
		return producer;
	}

	protected void commitTasks(Session session, MessageProducer producer,
			String taskname) throws JMSException {
		TextMessage message = session.createTextMessage(taskname);
		producer.send(message);
	}

	protected MessageConsumer createMessageConsumer(Destination dest,
			Session session) throws JMSException {

		MessageConsumer mc = session.createConsumer(dest);

		while (true) {
			TextMessage message = (TextMessage) mc.receive(1000);
			if (null != message)
				// TODO
				System.out.println("Got Message:" + message.getText());
			else
				break;
		}

		return mc;
	}

}
