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

package com.navercorp.cubridqa.scheduler.consumer;

import java.io.IOException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.Queue;
import javax.jms.QueueBrowser;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.apache.activemq.command.ActiveMQQueue;

import com.navercorp.cubridqa.scheduler.common.ActiveMQFactory;
import com.navercorp.cubridqa.scheduler.common.ConsumerContext;
import com.navercorp.cubridqa.scheduler.consumer.Configure;

public class ObserverMessage extends Thread {
	Configure conf;
	Properties props;
	private Session ses;
	private Destination destination;
	private ActiveMQFactory mq;
	private Connection conn;

	static Map<String, List<String>> mVer = new HashMap<String, List<String>>();

	static Map<String, String> maxVersionList = new HashMap<String, String>();

	public ObserverMessage(Configure conf) throws IOException {
		this.conf = conf;
	}

	public void monitorController(ConsumerContext cc) throws JMSException {
		try {
			String queueName=cc.getQueueName();
			boolean isdg=cc.getIsDubugEnv();
			boolean isOlyMax=cc.isOnlyMax();
			
			if (isOlyMax) {
				 viewQueueMessage(queueName);
				 saveSummaryInfo();
				 removeLowerAndRunUpperMessageFromQuenu(cc.getQueueName(), isdg);
			} else {
				 generalMonitorTaskMessageAndInitTaskContext(queueName,
						isdg);
			}

		} catch (JMSException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void generalMonitorTaskMessageAndInitTaskContext(String queueType,
			boolean isNotCommit) throws JMSException {
		String user = this.conf.getProperty("activemq.user");
		String passwd = this.conf.getProperty("activemq.pwd");
		String url = this.conf.getProperty("activemq.url");
		try {
			mq = new ActiveMQFactory(user, passwd, url);
			conn = mq.getConn();
			mq.setTrans(true);
			mq.setAckMode("AUTO_ACKNOWLEDGE");
			
			ses = mq.getSec();
			destination = ses.createQueue(queueType);
			MessageConsumer consumer = ses.createConsumer(destination);

			while (true) {
				TextMessage message = (TextMessage) consumer.receive(1000);
				if (null != message) {
					printMsgContext(message);
					break;
				} else {
					break;
				}
			}

			if (!isNotCommit) {
				ses.commit();
			}else
			{
				if (ses != null)
					ses.rollback();
			}
		} catch (Exception e) {
			if (ses != null){
				ses.rollback();
			    ses.close();}
			e.printStackTrace();
		} finally {
			if (conn != null)
				conn.close();
		}

	}

	public void saveSummaryInfo() {
		for (Map.Entry<String, List<String>> entry : mVer.entrySet()) {
			String key;
			String maxVersion;
			key = entry.getKey().toString();
			maxVersion = Collections.max(entry.getValue(),
					new Comparator<String>() {
						@Override
						public int compare(String ver1, String ver2) {

							String[] v1 = ver1.split("\\.");
							String[] v2 = ver2.split("\\.");

							int len = v1.length > v2.length ? v2.length
									: v1.length;
							int v;
							for (int i = 0; i < len; i++) {
								v = Integer.parseInt(v1[i])
										- Integer.parseInt(v2[i]);
								if (v != 0)
									return v;
							}

							return v1.length - v2.length;
						}
					});

			maxVersionList.put(key, maxVersion);
			System.out.println("Max Version of " + key + ": " + maxVersion);
		}
	}
	
	public void removeLowerAndRunUpperMessageFromQuenu(String queueName, boolean isDebug) throws JMSException {
		String user = this.conf.getProperty("activemq.user");
		String passwd = this.conf.getProperty("activemq.pwd");
		String url = this.conf.getProperty("activemq.url");

		try {

			mq = new ActiveMQFactory(user, passwd, url);
			conn = mq.getConn();
			mq.setTrans(true);
			mq.setAckMode("AUTO_ACKNOWLEDGE");
			

			ses = mq.getSec();
			destination = ses.createQueue(queueName);
			
			MessageConsumer consumer = ses.createConsumer(destination);
			System.out
					.println("++++++++++++++++ Summary Info For Message Queue ++++++++++++++++");
			while (true) {
				TextMessage message = (TextMessage) consumer.receive(1000);
				if (null != message) {
					String branch;
					String build_bit;
					String build_type;
					String build_num;
					branch = message.getStringProperty("BUILD_SVN_BRANCH");
					build_bit = message.getStringProperty("BUILD_BIT");
					build_type = message.getStringProperty("BUILD_TYPE");
					build_num = message.getStringProperty("BUILD_ID");
					String key = branch + "_" + build_bit + "bit_" + build_type;

					if (maxVersionList.containsKey(key)
							&& build_num.equals(maxVersionList.get(key))) {
						printMsgContext(message);
						if(!isDebug)
						{
							ses.commit();
						}
						break;
					} else {
						if(!isDebug)
						{
							ses.commit();
						}
					}

				} else {
					break;
				}
			}

		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (ses != null){
				ses.rollback();
				ses.close();}
			if (conn != null)
				conn.close();
			System.out
			.println("==============================================================================================");
			// System.out.println("Remove Done!");
		}

	}

	public void collectMsgContext(TextMessage tMsg) throws JMSException {
		List<String> lst = new ArrayList<String>();
		String key;
		String build_num;
		String build_tpy;
		String build_bit;
		key = tMsg.getStringProperty("BUILD_SVN_BRANCH");
		build_num = tMsg.getStringProperty("BUILD_ID");
		build_tpy = tMsg.getStringProperty("BUILD_TYPE");
		build_bit = tMsg.getStringProperty("BUILD_BIT");

		String key1 = key + "_" + build_bit + "bit_" + build_tpy;

		if (mVer.isEmpty()) {
			lst.add(build_num);
			mVer.put(key1, lst);
		} else if (!mVer.isEmpty() && mVer.containsKey(key1)) {
			lst.add(build_num);
			mVer.get(key1).add(build_num);
		} else if (!mVer.isEmpty() && !mVer.containsKey(key1)) {
			lst.add(build_num);
			mVer.put(key1, lst);
		}
	}

	public void viewQueueMessage(String queueType) throws JMSException {

		String user = this.conf.getProperty("activemq.user");
		String passwd = this.conf.getProperty("activemq.pwd");
		String url = this.conf.getProperty("activemq.url");

		try {
			mq = new ActiveMQFactory(user, passwd, url);
			conn = mq.getConn();
			mq.setTrans(true);
			mq.setAckMode("AUTO_ACKNOWLEDGE");
			ses = mq.getSec();
			ActiveMQQueue destination = new ActiveMQQueue(queueType);
			QueueBrowser browser = ses.createBrowser((Queue)destination);
			Enumeration msgs =browser.getEnumeration();
			if(!msgs.hasMoreElements())
			{
				System.out.println("No Message In Queue!");
			}else
			{
				while(msgs.hasMoreElements())
				{
					TextMessage msgTxt=(TextMessage)msgs.nextElement();
					collectMsgContext(msgTxt);
				}
				
			}
			
			//MessageConsumer consumer = ses.createConsumer(destination);
			// System.out
			// .println("-------------- Task Message Information -------------- ");
//			while (true) {
//				TextMessage message = (TextMessage) consumer.receive(1000);
//				if (null != message) {
//					collectMsgContext(message);
//					// break;
//				} else {
//					break;
//				}
//			}

		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (ses != null)
				ses.rollback();
				ses.close();
			if (conn != null)
				conn.close();
		}

	}

	public void printMsgContext(TextMessage tMsg) throws JMSException {
		Enumeration msgEnum = tMsg.getPropertyNames();
		String key;
		while (msgEnum.hasMoreElements()) {
			key = (String) msgEnum.nextElement();
			System.out.println(key + ":" + tMsg.getStringProperty(key));
		}
	}
}
