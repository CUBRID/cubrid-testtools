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

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

public class Message implements Cloneable {

	String msgId;
	String queue;
	String text;
	Properties props;
	int priority;

	public Message(String queue, String text) {
		this.msgId = CommonUtils.genMsgId();
		this.queue = queue;
		this.text = text;
		this.props = new Properties();
	}

	public void setProperty(String name, String value) {
		this.props.setProperty(name, value);
	}

	public String getQueue() {
		return this.queue;
	}

	public String getText() {
		return this.text;
	}

	public String getMsgId() {
		return this.msgId;
	}

	public Set getPropertyKeys() {
		return this.props.keySet();
	}

	public String getProperty(String key) {
		return this.props.getProperty(key);
	}

	public void putAll(Properties props) {
		if (props != null) {
			this.props.putAll(props);
		}
	}

	@Override
	public Object clone() {
		Message obj = new Message(queue, text);
		obj.setPriority(priority);
		obj.putAll(props);
		return obj;
	}

	public String toString() {
		StringBuffer result = new StringBuffer();
		result.append("Message Content: ").append(text).append(Constants.LINE_SEPARATOR);
		result.append("MSG_ID = ").append(this.msgId).append(Constants.LINE_SEPARATOR);
		result.append("MSG_PRIORITY = ").append(this.priority).append(Constants.LINE_SEPARATOR);

		Set<String> set = getPropertyKeys();
		TreeSet<String> tset = new TreeSet<String>(new java.util.Comparator<String>() {
			@Override
			public int compare(String o1, String o2) {
				return o1.compareTo(o2);
			}

		});

		for (String key : set) {
			tset.add(key + '=' + getProperty(key));
		}
		for (String item : tset) {
			result.append(item).append(Constants.LINE_SEPARATOR);
		}

		return result.toString();
	}

	public String getMD5() throws NoSuchAlgorithmException {
		StringBuffer cont = new StringBuffer();
		cont.append(this.queue).append('\n');

		Set set = props.keySet();
		for (Object key : set) {
			if ("BUILD_SEND_DELAY,BUILD_CREATE_TIME,BUILD_SEND_TIME,".indexOf("," + key + ",") == -1) {
				cont.append(getProperty(key.toString())).append('\n');
			}
		}

		// cont.append(getProperty(Constants.MSG_BUILD_URLS)).append('\n');
		// cont.append(getProperty(Constants.MSG_BUILD_SCENARIOS)).append('\n');
		// cont.append(getProperty(Constants.MSG_BUILD_BIT)).append('\n');
		//
		// if(getProperty(Constants.MSG_COMPAT_BUILD_ID)!=null) {
		// cont.append(getProperty(Constants.MSG_COMPAT_BUILD_ID)).append('\n');
		// }
		//
		// if(getProperty(Constants.MSG_COMPAT_TEST_CATAGORY)!=null) {
		// cont.append(getProperty(Constants.MSG_COMPAT_TEST_CATAGORY)).append('\n');
		// }
		//
		// String i18n_scenario = getProperty("I18N_BUILD_SCENARIOS");
		// if(i18n_scenario!=null) {
		// cont.append(i18n_scenario).append('\n');
		// }
		// String i18n_charset = getProperty("DB_CHARSET");
		// if(i18n_charset!=null) {
		// cont.append(i18n_charset).append('\n');
		// }
		// String i18n_xml = getProperty("RESET_CONFIG_FILE");
		// if(i18n_xml!=null) {
		// cont.append(i18n_xml).append('\n');
		// }

		MessageDigest md = MessageDigest.getInstance("MD5");
		return new BigInteger(1, cont.toString().getBytes()).toString(16);
	}

	public int getPriority() {
		return priority;
	}

	public void setPriority(int priority) {
		this.priority = priority;
	}

}
