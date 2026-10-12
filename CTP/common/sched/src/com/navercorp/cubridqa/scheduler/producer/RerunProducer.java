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
package com.navercorp.cubridqa.scheduler.producer;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import javax.jms.DeliveryMode;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.apache.activemq.ActiveMQConnectionFactory;

import com.navercorp.cubridqa.scheduler.common.CommonUtils;
import com.navercorp.cubridqa.scheduler.common.Constants;
import com.navercorp.cubridqa.scheduler.common.Message;

/**
 * Producer side of the QAHome failed-case rerun (CUBRIDQA-1631).
 *
 * <p>QAHome writes a rerun request as a {@code REQUESTED} row of {@code verify_rerun_batch} in qaresu.
 * This loop claims each one, finds the message that started the original run in {@code msg_sended}
 * (same build and category), and sends a clone of it - same build package, build type, testcase
 * branch and {@code MKEY_*} keys - to {@code RERUN_<original queue>} with {@code BUILD_GENERATE_MSG_WAY=RERUN}
 * and the {@code MKEY_RERUN_*} keys the consumer's {@code run_rerun.sh} needs. The consumers of the
 * original queue poll that rerun queue before their regular ones.
 *
 * <p>The {@code RERUN_} prefix (not a suffix) keeps rerun messages out of QAHome's
 * {@code queue_id like 'QUEUE_CUBRID_QA%'} elapsed-time totals.
 *
 * <pre>
 * Usage: RerunProducer [--once] [--dry-run]
 *   --once     one pass, then exit (cron)
 *   --dry-run  print the messages that would be sent; change nothing
 * Keys (conf/common.conf, producer.conf or rerun.conf): qahome_db_*, activemq_*, build.repository.root,
 *   rerun.poll.interval.seconds (60), rerun.queue.prefix (RERUN_), rerun.batch.limit (20),
 *   rerun.queue.exclude (MEMORY_LEAK,CODE_COVERAGE: queues whose runs are not the functional run of a category)
 * </pre>
 */
public class RerunProducer {

	static final String WAY_RERUN = "RERUN";
	static final String KEY_BATCH_ID = "MKEY_RERUN_BATCH_ID";
	static final String KEY_TOKEN = "MKEY_RERUN_TOKEN";
	static final String KEY_FEEDBACK_URL = "MKEY_RERUN_FEEDBACK_URL";
	static final String KEY_LOG_DIR = "MKEY_RERUN_LOG_DIR";
	static final String KEY_CASE_COUNT = "MKEY_RERUN_CASE_COUNT";
	private static final int MAX_MSG_CONT = 4096;
	private static final int MAX_ERROR = 2000;

	private final Configure conf;
	private final boolean dryRun;
	private final String queuePrefix;
	private final int batchLimit;
	private final String[] excludedQueueParts;

	public RerunProducer(Configure conf, boolean dryRun) {
		this.conf = conf;
		this.dryRun = dryRun;
		this.queuePrefix = conf.getProperty("rerun.queue.prefix", "RERUN_").trim();
		this.batchLimit = toInt(conf.getProperty("rerun.batch.limit"), 20);
		this.excludedQueueParts = conf.getProperty("rerun.queue.exclude", "MEMORY_LEAK,CODE_COVERAGE").toUpperCase().split("\\s*,\\s*");
	}

	public static void main(String[] args) throws Exception {
		boolean once = false;
		boolean dryRun = false;
		for (String arg : args) {
			if ("--once".equals(arg)) {
				once = true;
			} else if ("--dry-run".equals(arg)) {
				dryRun = true;
			} else {
				System.out.println("Usage: RerunProducer [--once] [--dry-run]");
				return;
			}
		}
		Configure conf = new Configure("rerun.conf");
		RerunProducer producer = new RerunProducer(conf, dryRun);
		int intervalSeconds = toInt(conf.getProperty("rerun.poll.interval.seconds"), 60);
		log("RerunProducer started" + (dryRun ? " (dry run)" : "") + (once ? ", one pass" : ", every " + intervalSeconds + "s"));
		while (true) {
			try {
				producer.runOnce();
			} catch (Exception e) {
				log("pass failed: " + e);
				e.printStackTrace();
			}
			if (once) {
				break;
			}
			Thread.sleep(intervalSeconds * 1000L);
		}
	}

	/** One pass over the waiting requests; returns how many batches were handled. */
	public int runOnce() throws Exception {
		Connection db = DriverManager.getConnection(conf.getProperty("qahome_db_url"), conf.getProperty("qahome_db_user"),
				conf.getProperty("qahome_db_pwd"));
		int handled = 0;
		try {
			for (Batch batch : findRequestedBatches(db)) {
				if (!dryRun && !claim(db, batch.id)) {
					continue; // another producer took it
				}
				try {
					send(db, batch);
				} catch (Exception e) {
					String reason = e.getMessage() == null ? e.toString() : e.getMessage();
					log("batch " + batch.id + " failed: " + reason);
					if (!dryRun) {
						fail(db, batch.id, reason);
					}
				}
				handled++;
			}
		} finally {
			db.close();
		}
		return handled;
	}

	private void send(Connection db, Batch batch) throws Exception {
		Original original = findOriginalMessage(db, batch);
		if (original == null) {
			throw new IllegalStateException("No original test message for build " + batch.buildId + " and category "
					+ batch.category + " in msg_sended, so the rerun cannot reuse its build package and settings.");
		}
		checkBuildPackage(original.props);

		String queue = queuePrefix + original.queue;
		Message message = new Message(queue, "Rerun of failed " + batch.category + " cases of build " + batch.buildId
				+ " requested from QAHome");
		message.putAll(original.props);
		message.setProperty(Constants.MSG_BUILD_GENERATE_MSG_WAY, WAY_RERUN);
		message.setProperty(Constants.MSG_BUILD_SEND_TIME, String.valueOf(System.currentTimeMillis()));
		message.setProperty(Constants.MSG_BUILD_SEND_DELAY, "0");
		message.setProperty(KEY_BATCH_ID, String.valueOf(batch.id));
		message.setProperty(KEY_TOKEN, batch.token);
		message.setProperty(KEY_FEEDBACK_URL, batch.feedbackUrl);
		message.setProperty(KEY_LOG_DIR, batch.logDir);
		message.setProperty(KEY_CASE_COUNT, String.valueOf(batch.caseCount));
		message.setPriority(4);
		// The consumer exports every property through unquoted word splitting (sched/init.sh).
		for (Object key : message.getPropertyKeys()) {
			String value = message.getProperty((String) key);
			if (value != null && !value.equals(value.replaceAll("[\\s*?\\[]", ""))) {
				throw new IllegalStateException("Message key " + key + " has a value with blanks or glob characters: " + value);
			}
		}

		if (dryRun) {
			log("[dry-run] batch " + batch.id + " (" + batch.caseCount + " cases) would go to " + queue + ":"
					+ Constants.LINE_SEPARATOR + message.toString().replace(batch.token, "<token>"));
			return;
		}
		sendToQueue(message);
		saveMsgSended(db, message);
		markSent(db, batch.id, queue, message.getMsgId(), original.msgId);
		log("batch " + batch.id + " (" + batch.caseCount + " cases) sent to " + queue + " as " + message.getMsgId()
				+ ", cloned from " + original.msgId);
	}

	private List<Batch> findRequestedBatches(Connection db) throws SQLException {
		List<Batch> batches = new ArrayList<Batch>();
		PreparedStatement ps = db.prepareStatement("SELECT b.id, b.build_id, b.sce_cat, b.env_os, b.token, b.feedback_url, b.log_dir,"
				+ " (SELECT count(*) FROM verify_rerun r WHERE r.batch_id = b.id) FROM verify_rerun_batch b WHERE b.status = 'REQUESTED'");
		try {
			ResultSet rs = ps.executeQuery();
			while (rs.next()) {
				Batch batch = new Batch();
				batch.id = rs.getInt(1);
				batch.buildId = rs.getString(2);
				batch.category = rs.getString(3);
				batch.os = rs.getString(4);
				batch.token = rs.getString(5);
				batch.feedbackUrl = rs.getString(6);
				batch.logDir = rs.getString(7);
				batch.caseCount = rs.getInt(8);
				batches.add(batch);
			}
			rs.close();
		} finally {
			ps.close();
		}
		// Oldest first, at most rerun.batch.limit per pass (sorted here: no ORDER BY on the server side).
		java.util.Collections.sort(batches, new java.util.Comparator<Batch>() {
			public int compare(Batch a, Batch b) {
				return a.id - b.id;
			}
		});
		return batches.size() > batchLimit ? batches.subList(0, batchLimit) : batches;
	}

	private boolean claim(Connection db, int batchId) throws SQLException {
		return update(db, "UPDATE verify_rerun_batch SET status = 'SENDING' WHERE id = ? AND status = 'REQUESTED'", batchId) == 1;
	}

	/**
	 * The newest message that started the functional run of this build and category: not a rerun, not
	 * filtered out, not a coverage build, and not on a queue that reuses the category name for another
	 * purpose (memory-leak runs send "medium" too). No ORDER BY: sorting on id would let CUBRID walk the
	 * primary key instead of idx_msg_sended_build_id_queue_id.
	 */
	private Original findOriginalMessage(Connection db, Batch batch) throws SQLException {
		PreparedStatement ps = db.prepareStatement("SELECT id, msg_id, queue_id, msg_cont, build_type FROM msg_sended WHERE build_id = ?"
				+ " AND build_scenarios = ? AND is_filtered_out = 'N' AND build_generate_msg_way <> ?");
		Original newest = null;
		int newestId = -1;
		try {
			ps.setString(1, batch.buildId);
			ps.setString(2, batch.category);
			ps.setString(3, WAY_RERUN);
			ResultSet rs = ps.executeQuery();
			boolean linux = batch.os == null || batch.os.toLowerCase().indexOf("win") < 0;
			while (rs.next()) {
				String queue = rs.getString(3);
				if (queue == null || queue.startsWith(queuePrefix)) {
					continue;
				}
				if (linux && queue.toUpperCase().indexOf("WIN") >= 0) {
					continue;
				}
				if ("coverage".equalsIgnoreCase(rs.getString(5)) || isExcludedQueue(queue)) {
					continue;
				}
				if (rs.getInt(1) > newestId) {
					newestId = rs.getInt(1);
					newest = new Original();
					newest.msgId = rs.getString(2);
					newest.queue = queue;
					newest.props = parseMessageContent(rs.getString(4));
				}
			}
			rs.close();
		} finally {
			ps.close();
		}
		return newest;
	}

	private boolean isExcludedQueue(String queue) {
		String upper = queue.toUpperCase();
		for (String part : excludedQueueParts) {
			if (part.length() > 0 && upper.indexOf(part) >= 0) {
				return true;
			}
		}
		return false;
	}

	/** {@code msg_sended.msg_cont} is {@link Message#toString()}: three header lines, then sorted KEY=VALUE lines. */
	static Properties parseMessageContent(String content) {
		Properties props = new Properties();
		if (content == null) {
			return props;
		}
		for (String line : content.split("\r?\n")) {
			if (line.startsWith("Message Content:") || line.startsWith("MSG_ID =") || line.startsWith("MSG_PRIORITY =")) {
				continue;
			}
			int eq = line.indexOf('=');
			if (eq <= 0) {
				continue;
			}
			String key = line.substring(0, eq).trim();
			if (key.matches("[A-Z][A-Z0-9_]*")) {
				props.setProperty(key, line.substring(eq + 1).trim());
			}
		}
		return props;
	}

	/** The package must still be in the build store, or every consumer would fail to install it. */
	private void checkBuildPackage(Properties props) {
		String url = props.getProperty(Constants.MSG_BUILD_URLS, "");
		String fileName = url.substring(url.lastIndexOf('/') + 1);
		if (fileName.length() == 0) {
			throw new IllegalStateException("The original message has no BUILD_URLS.");
		}
		List<File> candidates = new ArrayList<File>();
		String absolutePath = props.getProperty(Constants.MSG_BUILD_ABSOLUTE_PATH);
		if (absolutePath != null && absolutePath.length() > 0) {
			candidates.add(new File(absolutePath, fileName));
		}
		String repoRoot = conf.getRepoRoot();
		String store = props.getProperty(Constants.MSG_BUILD_STORE_ID);
		String buildId = props.getProperty(Constants.MSG_BUILD_ID);
		if (repoRoot != null && repoRoot.trim().length() > 0 && store != null && buildId != null) {
			candidates.add(new File(repoRoot.trim() + File.separator + store + File.separator + buildId + File.separator + "drop", fileName));
		}
		if (candidates.isEmpty()) {
			log("cannot check the build store for " + fileName + " (no BUILD_ABSOLUTE_PATH, no build.repository.root)");
			return;
		}
		for (File candidate : candidates) {
			if (candidate.exists()) {
				return;
			}
		}
		throw new IllegalStateException("The build package is no longer in the build store: " + candidates.get(0).getPath());
	}

	private void sendToQueue(Message message) throws Exception {
		ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(conf.getProperty("activemq_user"),
				conf.getProperty("activemq_pwd"), conf.getProperty("activemq_url"));
		javax.jms.Connection connection = factory.createConnection();
		try {
			Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
			MessageProducer producer = session.createProducer(session.createQueue(message.getQueue()));
			producer.setDeliveryMode(DeliveryMode.PERSISTENT);
			TextMessage text = session.createTextMessage(message.getText());
			text.setStringProperty(Constants.MSG_MSGID, message.getMsgId());
			Set<?> keys = message.getPropertyKeys();
			for (Object key : keys) {
				text.setStringProperty((String) key, message.getProperty((String) key));
			}
			producer.send(text, DeliveryMode.PERSISTENT, message.getPriority(), 0);
			session.commit();
			session.close();
		} finally {
			connection.close();
		}
	}

	/** Same row SendMessage writes, so ConsumerTimer can track consume_start_time/consume_stop_time. */
	private void saveMsgSended(Connection db, Message message) {
		String buildId = message.getProperty(Constants.MSG_BUILD_ID);
		PreparedStatement ps = null;
		try {
			ps = db.prepareStatement("INSERT INTO msg_sended (msg_id, queue_id, build_version, build_id, build_package_pattern, build_bit,"
					+ " build_type, build_scenarios, build_generate_msg_way, build_create_time, produce_msg_send_time, consume_flag,"
					+ " is_filtered_out, full_scenario_id, msg_cont, msg_delay) VALUES (?,?,?,?,?,?,?,?,?,?,NOW(),'NOT START','N',?,?,0)");
			ps.setString(1, message.getMsgId());
			ps.setString(2, message.getQueue());
			ps.setString(3, CommonUtils.getVersion(buildId));
			ps.setString(4, buildId);
			ps.setString(5, message.getProperty(Constants.MSG_BUILD_PACKAGE_PATTERN));
			ps.setInt(6, toInt(message.getProperty(Constants.MSG_BUILD_BIT), 0));
			ps.setString(7, message.getProperty(Constants.MSG_BUILD_TYPE));
			ps.setString(8, message.getProperty(Constants.MSG_BUILD_SCENARIOS));
			ps.setString(9, WAY_RERUN);
			ps.setTimestamp(10, new Timestamp(toLong(message.getProperty(Constants.MSG_BUILD_CREATE_TIME), System.currentTimeMillis())));
			ps.setString(11, message.getProperty(Constants.MSG_BUILD_SCENARIOS));
			String content = message.toString();
			ps.setString(12, content.length() > MAX_MSG_CONT ? content.substring(0, MAX_MSG_CONT) : content);
			ps.executeUpdate();
		} catch (Exception e) {
			// The message is already on the queue; only the consume-time tracking is lost.
			log("could not record " + message.getMsgId() + " in msg_sended: " + e);
		} finally {
			close(ps);
		}
	}

	private void markSent(Connection db, int batchId, String queue, String msgId, String origMsgId) throws SQLException {
		PreparedStatement ps = db.prepareStatement("UPDATE verify_rerun_batch SET status = 'SENT', queue_id = ?, msg_id = ?, orig_msg_id = ?,"
				+ " sent_at = SYS_DATETIME WHERE id = ? AND status = 'SENDING'");
		try {
			ps.setString(1, queue);
			ps.setString(2, msgId);
			ps.setString(3, origMsgId);
			ps.setInt(4, batchId);
			ps.executeUpdate();
		} finally {
			ps.close();
		}
		update(db, "UPDATE verify_rerun SET status = 'SENT' WHERE batch_id = ? AND status = 'REQUESTED'", batchId);
	}

	private void fail(Connection db, int batchId, String reason) {
		String message = reason.length() > MAX_ERROR ? reason.substring(0, MAX_ERROR) : reason;
		PreparedStatement ps = null;
		try {
			ps = db.prepareStatement("UPDATE verify_rerun_batch SET status = 'ERROR', error_message = ?, finished_at = SYS_DATETIME WHERE id = ?");
			ps.setString(1, message);
			ps.setInt(2, batchId);
			ps.executeUpdate();
			close(ps);
			ps = db.prepareStatement("UPDATE verify_rerun SET status = 'ERROR', message = ?, finished_at = SYS_DATETIME"
					+ " WHERE batch_id = ? AND status IN ('REQUESTED', 'SENT', 'RUNNING')");
			ps.setString(1, message);
			ps.setInt(2, batchId);
			ps.executeUpdate();
		} catch (SQLException e) {
			log("could not mark batch " + batchId + " as failed: " + e);
		} finally {
			close(ps);
		}
	}

	private static int update(Connection db, String sql, int id) throws SQLException {
		PreparedStatement ps = db.prepareStatement(sql);
		try {
			ps.setInt(1, id);
			return ps.executeUpdate();
		} finally {
			ps.close();
		}
	}

	private static void close(PreparedStatement ps) {
		if (ps != null) {
			try {
				ps.close();
			} catch (SQLException e) {
				// ignore
			}
		}
	}

	private static int toInt(String value, int defaultValue) {
		try {
			return Integer.parseInt(value.trim());
		} catch (Exception e) {
			return defaultValue;
		}
	}

	private static long toLong(String value, long defaultValue) {
		try {
			return Long.parseLong(value.trim());
		} catch (Exception e) {
			return defaultValue;
		}
	}

	private static void log(String message) {
		System.out.println(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()) + " [RERUN] " + message);
	}

	static class Batch {
		int id;
		String buildId;
		String category;
		String os;
		String token;
		String feedbackUrl;
		String logDir;
		int caseCount;
	}

	static class Original {
		String msgId;
		String queue;
		Properties props;
	}
}
