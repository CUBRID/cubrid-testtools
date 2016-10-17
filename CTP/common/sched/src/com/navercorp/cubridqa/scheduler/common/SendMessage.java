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

import java.sql.DriverManager;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Properties;
import java.util.Set;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.ScheduledMessage;

public class SendMessage {

	Properties props;
	ActiveMQConnectionFactory fact;
	ArrayList<Message> messageList;
	java.sql.Connection dbConn;

	public SendMessage(Properties props) {
		this.props = props;
		String user = props.getProperty("activemq_user");
		String pwd = props.getProperty("activemq_pwd");
		String url = props.getProperty("activemq_url");
		
		this.fact = new ActiveMQConnectionFactory(user, pwd, url);
		this.messageList = new ArrayList<Message>();
	}
	
	public void send() throws JMSException {
		send(0);
	}

	public void send(long delay) throws JMSException {
		if (this.messageList.size() == 0)
			return;
		Connection mqConn = null;
		Session session = null;

		Destination dest;
		MessageProducer producer;
		TextMessage textMsg;
		Set<String> set;
		boolean isFilteredOut = false;
		try {
			this.dbConn = this.getConnection();
			mqConn = fact.createConnection();
			session = mqConn.createSession(true, Session.AUTO_ACKNOWLEDGE);

			for (Message message : messageList) {
				dest = session.createQueue(message.getQueue());
				producer = session.createProducer(dest);
				textMsg = session.createTextMessage(message.getText());
				textMsg.setJMSPriority(message.getPriority());
				if (delay > 0) {
					textMsg.setLongProperty(ScheduledMessage.AMQ_SCHEDULED_DELAY, delay);
				}
				textMsg.setStringProperty(Constants.MSG_MSGID, message.getMsgId());
				set = message.getPropertyKeys();
				for (String key : set) {
					textMsg.setStringProperty(key, message.getProperty(key));
				}
				
				if (dbConn != null) {
					isFilteredOut = isFilteredOut(message) || (isPatchBuild(message) && !isPatchAccepted(message));
					saveMsgToDatabase(message, isFilteredOut, delay);
					if(isFilteredOut == false) {
						producer.send(textMsg);
					} else {
						System.out.println("SORRY. FILTERED OUT!");
					}
				} else {
					producer.send(textMsg);
				}
			}
			session.commit();
		} catch (Exception e) {
			e.printStackTrace();
			if (session != null)
				session.rollback();
		} finally {
			if (mqConn != null)
				mqConn.close();
			this.messageList.clear();
			
			if (dbConn != null) {
				try {
					dbConn.close();
				} catch (SQLException e) {
					e.printStackTrace();
				}
			}
		}

	}

	public void addMessage(Message message) {
		this.messageList.add(message);
	}
	
	public void clearMessages(){
		this.messageList.clear();
	}
	
	public String saveMsgToDatabase(Message msg, boolean isFilteredOut, long delay) {
		
		String msgId = msg.getMsgId();
		
		String QUEUE_ID = msg.getQueue();
		String BUILD_ID = msg.getProperty(Constants.MSG_BUILD_ID);
		String BUILD_VERSION = CommonUtils.getVersion(BUILD_ID);
		String BUILD_PACKAGE_PATTERN = msg.getProperty(Constants.MSG_BUILD_PACKAGE_PATTERN);
		String BUILD_BIT = msg.getProperty(Constants.MSG_BUILD_BIT);
		String BUILD_TYPE = msg.getProperty(Constants.MSG_BUILD_TYPE);
		String BUILD_SCENARIOS = msg.getProperty(Constants.MSG_BUILD_SCENARIOS);
		String BUILD_GENERATE_MSG_WAY = msg.getProperty(Constants.MSG_BUILD_GENERATE_MSG_WAY);
		String BUILD_CREATE_TIME = msg.getProperty(Constants.MSG_BUILD_CREATE_TIME);
		String MSG_FILEID = msg.getProperty(Constants.MSG_MSG_FILEID);
		String fullScenarioId = MSG_FILEID == null || MSG_FILEID.trim().equals("") ? BUILD_SCENARIOS : MSG_FILEID;
		String CONSUME_FLAG = "NOT START";
		String IS_FILTERED_OUT = isFilteredOut ? "Y" : "N";

		String sql = "insert into MSG_SENDED (MSG_ID, QUEUE_ID,BUILD_VERSION,BUILD_ID,BUILD_PACKAGE_PATTERN,BUILD_BIT,BUILD_TYPE,BUILD_SCENARIOS,BUILD_GENERATE_MSG_WAY,BUILD_CREATE_TIME,PRODUCE_MSG_SEND_TIME,CONSUME_FLAG,IS_FILTERED_OUT,FULL_SCENARIO_ID, MSG_CONT, MSG_DELAY) values (?,?,?,?,?,?,?,?,?,?,NOW(),?,?,?,?,?);";
		PreparedStatement pstmt = null;
		
		try{
			pstmt = getConnection().prepareStatement(sql);
			pstmt.setString(1, msgId);
			pstmt.setString(2, QUEUE_ID);
			pstmt.setString(3, BUILD_VERSION);
			pstmt.setString(4, BUILD_ID);
			pstmt.setString(5, BUILD_PACKAGE_PATTERN);
			pstmt.setInt(6, Integer.parseInt(BUILD_BIT));
			pstmt.setString(7, BUILD_TYPE);
			pstmt.setString(8, BUILD_SCENARIOS);
			pstmt.setString(9, BUILD_GENERATE_MSG_WAY);
			pstmt.setTimestamp(10, new Timestamp(Long.parseLong(BUILD_CREATE_TIME)));
			pstmt.setString(11, CONSUME_FLAG);
			pstmt.setString(12, IS_FILTERED_OUT);
			pstmt.setString(13, fullScenarioId);
			pstmt.setString(14, msg.toString());
			pstmt.setLong(15, delay);
			
			pstmt.executeUpdate();
			
		} catch(SQLException e) {
			e.printStackTrace();
		} finally {
			try {
				pstmt.close();
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
		
		return msgId;
	}
	
	public boolean isFilteredOut(Message msg) {
		
		String QUEUE_ID = msg.getQueue();
		String BUILD_ID = msg.getProperty(Constants.MSG_BUILD_ID);
		String BUILD_VERSION = CommonUtils.getVersion(BUILD_ID);
		String BUILD_PACKAGE_PATTERN = msg.getProperty(Constants.MSG_BUILD_PACKAGE_PATTERN);
		String BUILD_SCENARIOS = msg.getProperty(Constants.MSG_BUILD_SCENARIOS);
		String MSG_FILEID = msg.getProperty(Constants.MSG_MSG_FILEID);
		String fullScenarioId = MSG_FILEID == null || MSG_FILEID.trim().equals("") ? BUILD_SCENARIOS : MSG_FILEID;
		String BUILD_GENERATE_MSG_WAY = msg.getProperty(Constants.MSG_BUILD_GENERATE_MSG_WAY);
		
		String sql_rule = "select AFFECTED_TIMES from MSG_RULE_EXCLUDED_BY_DAY where QUEUE_ID=? and BUILD_VERSION=? and BUILD_PACKAGE_PATTERN=? and FULL_SCENARIO_ID=? and BUILD_GENERATE_MSG_WAY=? order by AFFECTED_TIMES asc";
		PreparedStatement pstmt = null;
		ResultSet rs = null;
		
		int ruleAffectedTimes = -1;
		try{
			pstmt = getConnection().prepareStatement(sql_rule);
			pstmt.setString(1, QUEUE_ID);
			pstmt.setString(2, BUILD_VERSION);
			pstmt.setString(3, BUILD_PACKAGE_PATTERN);
			pstmt.setString(4, fullScenarioId);
			pstmt.setString(5, BUILD_GENERATE_MSG_WAY);
			
			rs = pstmt.executeQuery();
			if(rs.next()) {
				ruleAffectedTimes = rs.getInt(1);
			}
			rs.close();
		} catch(SQLException e) {
			e.printStackTrace();
		} finally {
			try {
				pstmt.close();
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
		
		if (ruleAffectedTimes < 0) return false;
		
		int acctualTimes = 0;
		String sql_check = "select count(*) from MSG_SENDED where QUEUE_ID=? and BUILD_VERSION=? and BUILD_PACKAGE_PATTERN=? and FULL_SCENARIO_ID=? and BUILD_GENERATE_MSG_WAY=? and IS_FILTERED_OUT='N' and PRODUCE_MSG_SEND_TIME>=?";
		try{
			pstmt = getConnection().prepareStatement(sql_check);			
			pstmt.setString(1, QUEUE_ID);			
			pstmt.setString(2, BUILD_VERSION);			
			pstmt.setString(3, BUILD_PACKAGE_PATTERN);
			pstmt.setString(4, fullScenarioId);
			pstmt.setString(5, BUILD_GENERATE_MSG_WAY);
			pstmt.setTimestamp(6, getStartOfToday());
			
			rs = pstmt.executeQuery();
			if(rs.next()) {
				acctualTimes = rs.getInt(1);
			}
			rs.close();
		} catch(SQLException e) {
			e.printStackTrace();
		} finally {
			try {
				pstmt.close();
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
		
		return acctualTimes >= ruleAffectedTimes;
	}
	
	public boolean isPatchBuild (Message msg) {
			
		String BUILD_ID = msg.getProperty(Constants.MSG_BUILD_ID);
			
		String sql_is_patch = "select 1 from msg_patch_list where '" + BUILD_ID + "' like batch_prefix||'%' limit 1";
			
		PreparedStatement pstmt = null;
		ResultSet rs = null;
			
		boolean isPatchBuild = false;
		try{
			pstmt = getConnection().prepareStatement(sql_is_patch);
			rs = pstmt.executeQuery();
			isPatchBuild = rs.next();
		} catch(SQLException e) {
			e.printStackTrace();
		} finally {
			try {
				rs.close();
			} catch (SQLException e) {
				e.printStackTrace();
			}
			try {
				pstmt.close();
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
		return isPatchBuild;
	}
	
	public boolean isPatchAccepted(Message msg) {
		
		String QUEUE_ID = msg.getQueue();
		String BUILD_PACKAGE_PATTERN = msg.getProperty(Constants.MSG_BUILD_PACKAGE_PATTERN);
		String BUILD_SCENARIOS = msg.getProperty(Constants.MSG_BUILD_SCENARIOS);
		String MSG_FILEID = msg.getProperty(Constants.MSG_MSG_FILEID);
		String fullScenarioId = MSG_FILEID == null || MSG_FILEID.trim().equals("") ? BUILD_SCENARIOS : MSG_FILEID;
		String BUILD_GENERATE_MSG_WAY = msg.getProperty(Constants.MSG_BUILD_GENERATE_MSG_WAY);
		
		if(!BUILD_GENERATE_MSG_WAY.equals("AUTO")) {
			return true;
		}
		
		String sql_rule = "select 1 from msg_rule_included_for_patch where build_package_pattern=? and queue_id=? and full_scenario_id=? and build_generate_msg_way=?";
		PreparedStatement pstmt = null;
		ResultSet rs = null;
		boolean accept = false;		
		try{
			pstmt = getConnection().prepareStatement(sql_rule);
			pstmt.setString(1, BUILD_PACKAGE_PATTERN);
			pstmt.setString(2, QUEUE_ID);
			pstmt.setString(3, fullScenarioId);
			pstmt.setString(4, BUILD_GENERATE_MSG_WAY);			
			rs = pstmt.executeQuery();
			accept = rs.next();
			
		} catch(SQLException e) {
			e.printStackTrace();
		} finally {
			try {
				rs.close();
			} catch (SQLException e) {
				e.printStackTrace();
			}
			try {
				pstmt.close();
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
		return accept;
	}
	
	private java.sql.Connection createConnection() throws SQLException {
		String url = props.getProperty("qahome_db_url");
		String user = props.getProperty("qahome_db_user");
		String pwd = props.getProperty("qahome_db_pwd");
		return DriverManager.getConnection(url, user, pwd);
	}
	
	private java.sql.Connection getConnection() {
		if(dbConn != null) return dbConn;
		try{
			dbConn = createConnection();
		}catch(Exception e) {
			e.printStackTrace();
			dbConn = null;
		}
		
		return dbConn;
	}
	
	public static void main(String[] args) {
		System.out.println(getStartOfToday());
	}
	
	public static Timestamp getStartOfToday() {
		SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
		String s = format.format(new Date());
		java.util.Date d;
		Timestamp t = null;
		try {
			d = format.parse(s);
			t = new Timestamp(d.getTime());
		} catch (ParseException e) {
			e.printStackTrace();
		}
		return t;
	}
}
