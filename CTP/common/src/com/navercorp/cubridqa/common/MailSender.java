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

package com.navercorp.cubridqa.common;


import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.mail.Address;
import javax.mail.Message;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.PosixParser;
import org.masukomi.aspirin.Aspirin;
import org.masukomi.aspirin.listener.AspirinListener;
import org.masukomi.aspirin.listener.ResultState;

public class MailSender {

	public static void main(String[] args) throws Exception {
		Options options = new Options();
		options.addOption("to", true, "the to recipient of mail");
		options.addOption("cc", true, "the cc recipient of mail");
		options.addOption("title", true, "the subject of mail");
		options.addOption("content", true, "the content of mail");
		options.addOption("filecont", true, "the content of mail from a file");
		options.addOption("br", false, "replace with <br>");
		options.addOption("help", false, "List help");

		Properties props = Constants.COMMON_DAILYQA_CONF;

		InternetAddress from = new InternetAddress(props.getProperty("mail_from_address"), props.getProperty("mail_from_nickname"));
		String dearContent = "";

		CommandLineParser parser = null;
		CommandLine cmd = null;

		try {
			parser = new PosixParser();
			cmd = parser.parse(options, args);
		} catch (Exception e) {
			showHelp(e.getMessage(), options);
			return;
		}

		if (args.length == 0 || cmd.hasOption("help")) {
			showHelp(null, options);
			return;
		}

		if (!cmd.hasOption("to")) {
			showHelp("Please input mail <to>.", options);
			return;
		}

		if (!cmd.hasOption("title") && !cmd.hasOption("content")) {
			showHelp("Please give mail subject and content", options);
			return;
		}

		String to = cmd.getOptionValue("to");
		String cc = cmd.getOptionValue("cc");
		String title = cmd.getOptionValue("title");
		String content;
		if (cmd.hasOption("content")) {
			content = cmd.getOptionValue("content");
		} else {
			content = CommonUtils.getFileContent(cmd.getOptionValue("filecont"));
		}
		if (cmd.hasOption("br")) {
			content = CommonUtils.replace(content, "\n", "<br>");
		}

		String[] toList = to.split(",");
		ArrayList<InternetAddress> toAdrrList = new ArrayList<InternetAddress>();
		ArrayList<InternetAddress> ccAdrrList = new ArrayList<InternetAddress>();
		for (int i = 0; i < toList.length; i++) {
			if (toList[i].indexOf("@") <= 0) {
				continue;
			}
			if (toList[i].indexOf("<") >= 0) {
				String mailAlias = toList[i].substring(0, toList[i].indexOf("<"));
				String mailAddr = toList[i].substring(toList[i].indexOf("<") + 1, toList[i].indexOf(">"));
				InternetAddress idxTo = new InternetAddress(mailAddr, mailAlias);
				dearContent += mailAlias + ", ";
				toAdrrList.add(idxTo);
			} else {
				String mailAlias = toList[i].substring(0, toList[i].indexOf("@"));
				String mailAddr = toList[i];
				dearContent += mailAlias + ", ";
				InternetAddress idxTo = new InternetAddress(mailAddr, mailAlias);
				toAdrrList.add(idxTo);
			}
		}

		if (cc != null && cc.length() > 0) {
			String[] ccList = cc.split(",");
			for (int j = 0; j < ccList.length; j++) {
				if (ccList[j].indexOf("@") <= 0) {
					continue;
				}
				if (ccList[j].indexOf("<") >= 0) {
					String mailAlias = ccList[j].substring(0, ccList[j].indexOf("<"));
					String mailAddr = ccList[j].substring(ccList[j].indexOf("<") + 1, ccList[j].indexOf(">"));
					InternetAddress idxCc = new InternetAddress(mailAddr, mailAlias);
					ccAdrrList.add(idxCc);
				} else {
					String mailAlias = ccList[j].substring(0, ccList[j].indexOf("@"));
					String mailAddr = ccList[j];
					InternetAddress idxCc = new InternetAddress(mailAddr, mailAlias);
					ccAdrrList.add(idxCc);
				}
			}
		}

		if (content != null && content.length() > 0) {
			dearContent = dearContent.substring(0, dearContent.lastIndexOf(",")).trim();
			content = content.replace("#TO#", dearContent);
		}

		if ((toAdrrList != null && toAdrrList.size() > 0) || (ccAdrrList != null && ccAdrrList.size() > 0)) {
			MailSender.getInstance().send(from, toAdrrList, ccAdrrList, title, content);
		}

	}

	private static MailSender instance;

	public static MailSender getInstance() {
		if (instance == null) {
			instance = new MailSender();
		}
		return instance;
	}

	public void send(String from, String to, String title, String mailContent) throws Exception {
		InternetAddress f = new InternetAddress(from);
		InternetAddress t = new InternetAddress(to);
		send(f, t, title, mailContent);
	}

	public void send(InternetAddress from, InternetAddress to, String title, String mailContent) throws Exception {
		ArrayList<InternetAddress> toList = new ArrayList<InternetAddress>();
		toList.add(to);
		send(from, toList, null, title, mailContent);
	}

	public void send(String from, String to, String cc, String title, String mailContent) throws Exception {
		InternetAddress f = new InternetAddress(from);
		InternetAddress t = new InternetAddress(to);
		InternetAddress c = new InternetAddress(cc);
		send(f, t, c, title, mailContent);
	}

	public void sendBatch(String from, String to, String cc, String title, String mailContent) throws Exception {
		InternetAddress f = new InternetAddress(from);
		ArrayList<InternetAddress> toList = new ArrayList<InternetAddress>();
		ArrayList<InternetAddress> ccList = null;
		String[] arr = to.split(",");
		for (String s : arr) {
			if (CommonUtils.isEmpty(s)) {
				continue;
			}
			toList.add(new InternetAddress(s));
		}
		if (CommonUtils.isEmpty(cc) == false) {
			ccList = new ArrayList<InternetAddress>();
			arr = cc.split(",");
			for (String s : arr) {
				if (CommonUtils.isEmpty(s)) {
					continue;
				}
				ccList.add(new InternetAddress(s));
			}
		}
		send(f, toList, ccList, title, mailContent);
	}

	public void send(InternetAddress from, InternetAddress to, InternetAddress cc, String title, String mailContent) throws Exception {
		ArrayList<InternetAddress> toList = new ArrayList<InternetAddress>();
		toList.add(to);

		ArrayList<InternetAddress> ccList = new ArrayList<InternetAddress>();
		ccList.add(cc);
		send(from, toList, ccList, title, mailContent);
	}

	public void send(InternetAddress from, ArrayList<InternetAddress> to, ArrayList<InternetAddress> cc, String title, String mailContent) throws Exception {
		Properties prop = System.getProperties();
		prop.put("mail.smtp.host", "localhost");

		MimeMessage message = Aspirin.createNewMimeMessage();
		message.setSubject(title);
		message.setContent(mailContent, "text/html");
		message.setSentDate(new Date());
		message.setFrom(from);

		Address[] addrs = new Address[to.size()];
		Address[] ccAddrs = null;
		for (int i = 0; i < addrs.length; i++) {
			addrs[i] = to.get(i);
			System.out.println("TO: " + addrs[i]);
		}

		if (cc != null && cc.size() > 0) {
			ccAddrs = new Address[cc.size()];
			for (int j = 0; j < ccAddrs.length; j++) {
				ccAddrs[j] = cc.get(j);
				System.out.println("CC: " + ccAddrs[j]);
			}
		}

		message.addRecipients(Message.RecipientType.TO, addrs);
		if (ccAddrs != null && ccAddrs.length > 0) {
			message.addRecipients(Message.RecipientType.CC, ccAddrs);
		}
		
		Aspirin.add(message);
		final CountDownLatch stop = new CountDownLatch(1);
		Aspirin.addListener(new AspirinListener() {

			@Override
			public void delivered(String id, String mail, ResultState state, String result) {
				System.out.println(id);
				System.out.println(mail);
				System.out.println(state);
				System.out.println(result);
				if (state.equals(ResultState.FINISHED)) {
					Aspirin.shutdown();
					stop.countDown();
					System.out.println("MAIL DONE");
				}
			}
		});
		stop.await(600, TimeUnit.SECONDS);
	}

	public static Properties getProperties(String filename) throws IOException {
		FileInputStream fis = new FileInputStream(filename);
		Properties props = new Properties();
		props.load(fis);
		fis.close();
		return props;
	}

	private static void showHelp(String error, Options options) {
		if (error != null)
			System.out.println("Error: " + error);
		HelpFormatter formatter = new HelpFormatter();
		formatter.printHelp("mail_sender: send mail agent", options);
		System.out.println();
	}

}
