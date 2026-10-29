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
import java.security.MessageDigest;
import java.security.SecureRandom;
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
import java.util.regex.Pattern;

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
 * (the run's own message when the run recorded it, else the one consumed last before the run ended),
 * and sends a clone of it - same build package, build type, testcase branch and {@code MKEY_*} keys - to
 * {@code RERUN_<original queue>} with {@code BUILD_GENERATE_MSG_WAY=RERUN} and the {@code MKEY_RERUN_*}
 * keys the consumer's {@code run_rerun.sh} needs. The consumers of the original queue poll that rerun
 * queue before their regular ones.
 *
 * <p>The batch token is made here when the batch is claimed: the message carries it, qaresu keeps only
 * its SHA-256 (QAHome compares against that), and the msg_sended copy has it masked.
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
 *   rerun.queue.exclude (MEMORY_LEAK,CODE_COVERAGE: queues whose runs are not the functional run of a category),
 *   rerun.message.ttl.hours (24: an unconsumed rerun message leaves the queue; keep it at QAHome's rerun.timeout.hours)
 * </pre>
 */
public class RerunProducer {

	static final String WAY_RERUN = "RERUN";
	static final String KEY_BATCH_ID = "MKEY_RERUN_BATCH_ID";
	static final String KEY_TOKEN = "MKEY_RERUN_TOKEN";
	static final String KEY_FEEDBACK_URL = "MKEY_RERUN_FEEDBACK_URL";
	static final String KEY_LOG_DIR = "MKEY_RERUN_LOG_DIR";
	static final String KEY_CASE_COUNT = "MKEY_RERUN_CASE_COUNT";
	/** Set for a pull request rerun: the consumer runs the cases from this ref of the testcase repository. */
	static final String KEY_TC_REF = "MKEY_RERUN_TC_REF";
	/** The commit QAHome knows on that ref; the consumer refuses a ref that no longer contains it. */
	static final String KEY_TC_SHA = "MKEY_RERUN_TC_SHA";
	private static final int MAX_MSG_CONT = 4096;
	private static final int MAX_ERROR = 2000;
	/** Keys a consumer needs to take the message (start_consumer.sh hasTestBuild) and the runners to run it. */
	private static final String[] REQUIRED_KEYS = { Constants.MSG_BUILD_ID, Constants.MSG_BUILD_SCENARIOS, "BUILD_SVN_BRANCH",
			Constants.MSG_BUILD_URLS, "BUILD_SCENARIO_BRANCH_GIT" };
	/** Only these keys are cloned: every one becomes an environment variable on the consumer. */
	private static final Pattern CLONED_KEY = Pattern.compile("(BUILD|MKEY|MSG|COMPAT)_[A-Z0-9_]*");
	/**
	 * Values are exported by sched/init.sh with unquoted word splitting and pasted into the shell harness's
	 * {@code export K="v"} lines, so only characters that are inert in both pass (all of today's values do).
	 */
	private static final Pattern SAFE_VALUE = Pattern.compile("[A-Za-z0-9._:/@%+,=~{}!-]*");
	private static final Pattern MSG_ID = Pattern.compile("\\d{6}-\\d{6}-\\d{3}-\\d{6}");
	private static final Pattern TC_REF = Pattern.compile("refs/pull/[1-9][0-9]*/head");
	private static final Pattern TC_SHA = Pattern.compile("[0-9a-f]{40}");
	private static final Pattern SECRET_KEY = Pattern.compile(".*(PWD|PASSWORD|PASSWD|TOKEN).*");
	private static final SecureRandom RANDOM = new SecureRandom();

	private final Configure conf;
	private final boolean dryRun;
	private final String queuePrefix;
	private final int batchLimit;
	private final String[] excludedQueueParts;
	private final long messageTtlMillis;

	public RerunProducer(Configure conf, boolean dryRun) {
		this.conf = conf;
		this.dryRun = dryRun;
		this.queuePrefix = conf.getProperty("rerun.queue.prefix", "RERUN_").trim();
		this.batchLimit = Math.max(1, toInt(conf.getProperty("rerun.batch.limit"), 20));
		this.excludedQueueParts = conf.getProperty("rerun.queue.exclude", "MEMORY_LEAK,CODE_COVERAGE").trim().toUpperCase().split("\\s*,\\s*");
		this.messageTtlMillis = Math.max(1, toInt(conf.getProperty("rerun.message.ttl.hours"), 24)) * 3600000L;
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
		int intervalSeconds = Math.max(5, toInt(conf.getProperty("rerun.poll.interval.seconds"), 60));
		log("RerunProducer started" + (dryRun ? " (dry run)" : "") + (once ? ", one pass" : ", every " + intervalSeconds + "s")
				+ ", queue prefix " + producer.queuePrefix + ", at most " + producer.batchLimit + " requests per pass");
		while (true) {
			boolean ok = true;
			try {
				producer.runOnce();
			} catch (Exception e) {
				ok = false;
				log("pass failed: " + e);
				e.printStackTrace();
			}
			if (once) {
				System.exit(ok ? 0 : 1);
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
			List<Batch> batches = findRequestedBatches(db);
			if (batches.isEmpty()) {
				return 0;
			}
			// Connect first: with the broker down nothing is claimed, so no batch is left half sent.
			javax.jms.Connection broker = dryRun ? null : openBroker();
			try {
				for (Batch batch : batches) {
					batch.token = newToken();
					if (!dryRun && !claim(db, batch)) {
						continue; // another producer took it, or it was cancelled
					}
					try {
						send(db, broker, batch);
					} catch (Exception e) {
						// send() throws only before the message is committed to the queue
						String reason = e.getMessage() == null ? e.toString() : e.getMessage();
						log("batch " + batch.id + " failed: " + reason);
						if (!dryRun) {
							fail(db, batch.id, reason);
						}
					}
					handled++;
				}
			} finally {
				if (broker != null) {
					broker.close();
				}
			}
		} finally {
			db.close();
		}
		return handled;
	}

	private void send(Connection db, javax.jms.Connection broker, Batch batch) throws Exception {
		if (batch.tcRef != null && !TC_REF.matcher(batch.tcRef).matches()) {
			throw new IllegalStateException("Unsupported testcase ref " + batch.tcRef + " (refs/pull/<number>/head only).");
		}
		if (batch.tcRef != null && (batch.tcSha == null || !TC_SHA.matcher(batch.tcSha).matches())) {
			// a pull request number alone could name a pull request of another testcase repository
			throw new IllegalStateException("A pull request rerun needs the commit QAHome knows on " + batch.tcRef + ".");
		}
		if (batch.tcSha != null && batch.tcRef == null) {
			throw new IllegalStateException("A testcase commit without a pull request ref.");
		}
		Original original = findOriginalMessage(db, batch);
		checkBuildPackage(original.props);

		String queue = queuePrefix + original.queue;
		Message message = new Message(queue, (batch.tcRef == null ? "Rerun of failed " : "Rerun with " + batch.tcRef + " of ")
				+ batch.category + " cases of build " + batch.buildId + " requested from QAHome");
		message.putAll(original.props);
		message.setProperty(Constants.MSG_BUILD_GENERATE_MSG_WAY, WAY_RERUN);
		message.setProperty(Constants.MSG_BUILD_SEND_TIME, String.valueOf(System.currentTimeMillis()));
		message.setProperty(Constants.MSG_BUILD_SEND_DELAY, "0");
		message.setProperty(KEY_BATCH_ID, String.valueOf(batch.id));
		message.setProperty(KEY_TOKEN, batch.token);
		message.setProperty(KEY_FEEDBACK_URL, batch.feedbackUrl);
		message.setProperty(KEY_LOG_DIR, batch.logDir);
		message.setProperty(KEY_CASE_COUNT, String.valueOf(batch.caseCount));
		if (batch.tcRef != null) {
			message.setProperty(KEY_TC_REF, batch.tcRef);
			if (batch.tcSha != null) {
				message.setProperty(KEY_TC_SHA, batch.tcSha);
			}
		}
		message.setPriority(4);
		for (Object key : message.getPropertyKeys()) {
			String value = message.getProperty((String) key);
			if (value != null && !SAFE_VALUE.matcher(value).matches()) {
				// the key only: the value may be a secret and this text is shown in QAHome
				throw new IllegalStateException("Message key " + key + " has a value with characters a consumer shell could"
						+ " interpret (blanks, quotes, $, `, ;, |, &, <, >, parentheses or wildcards).");
			}
		}

		if (dryRun) {
			log("[dry-run] batch " + batch.id + " (" + batch.caseCount + " cases) would go to " + queue + ", cloned from "
					+ original.msgId + " (" + original.how + "):" + Constants.LINE_SEPARATOR + masked(message));
			return;
		}
		sendToQueue(broker, message);
		// On the queue now: bookkeeping failures below are logged, never turned into a batch error.
		saveMsgSended(db, message, batch.token);
		try {
			if (!markSent(db, batch.id, queue, message.getMsgId(), original.msgId)) {
				log("batch " + batch.id + " was sent as " + message.getMsgId() + " but is no longer waiting for it (cancelled?)");
			}
		} catch (SQLException e) {
			log("batch " + batch.id + " was sent as " + message.getMsgId() + " but could not be marked SENT: " + e);
		}
		log("batch " + batch.id + " (" + batch.caseCount + " cases) sent to " + queue + " as " + message.getMsgId()
				+ ", cloned from " + original.msgId + " (" + original.how + ")");
	}

	/** The message text with the token and every password-like key masked, for logs. */
	private static String masked(Message message) {
		StringBuilder text = new StringBuilder();
		for (String line : message.toString().split("\r?\n")) {
			int eq = line.indexOf('=');
			if (eq > 0 && SECRET_KEY.matcher(line.substring(0, eq).trim()).matches()) {
				line = line.substring(0, eq + 1) + "<masked>";
			}
			text.append(line).append(Constants.LINE_SEPARATOR);
		}
		return text.toString();
	}

	static String newToken() {
		byte[] bytes = new byte[16];
		RANDOM.nextBytes(bytes);
		return hex(bytes);
	}

	static String sha256Hex(String value) throws Exception {
		return hex(MessageDigest.getInstance("SHA-256").digest(value.getBytes("UTF-8")));
	}

	private static String hex(byte[] bytes) {
		StringBuilder hex = new StringBuilder();
		for (byte b : bytes) {
			hex.append(String.format("%02x", Integer.valueOf(b & 0xff)));
		}
		return hex.toString();
	}

	private List<Batch> findRequestedBatches(Connection db) throws SQLException {
		List<Batch> batches = new ArrayList<Batch>();
		PreparedStatement ps = db.prepareStatement("SELECT b.id, b.build_id, b.sce_cat, b.env_os, b.verify_main_id, b.feedback_url, b.log_dir,"
				+ " (SELECT count(*) FROM verify_rerun r WHERE r.batch_id = b.id), b.src_tb, b.src_id, b.tc_ref, b.tc_sha"
				+ " FROM verify_rerun_batch b WHERE b.status = 'REQUESTED'");
		try {
			ResultSet rs = ps.executeQuery();
			while (rs.next()) {
				Batch batch = new Batch();
				batch.id = rs.getInt(1);
				batch.buildId = rs.getString(2);
				batch.category = rs.getString(3);
				batch.os = rs.getString(4);
				batch.verifyMainId = rs.getInt(5);
				batch.feedbackUrl = rs.getString(6);
				batch.logDir = rs.getString(7);
				batch.caseCount = rs.getInt(8);
				batch.srcTb = rs.getString(9);
				batch.srcId = rs.getInt(10);
				batch.tcRef = emptyToNull(rs.getString(11));
				batch.tcSha = emptyToNull(rs.getString(12));
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

	/** Claim a batch and store the SHA-256 of its new token, which QAHome checks the consumer's calls against. */
	private boolean claim(Connection db, Batch batch) throws Exception {
		PreparedStatement ps = db.prepareStatement("UPDATE verify_rerun_batch SET status = 'SENDING', token = ? WHERE id = ? AND status = 'REQUESTED'");
		try {
			ps.setString(1, sha256Hex(batch.token));
			ps.setInt(2, batch.id);
			return ps.executeUpdate() == 1;
		} finally {
			ps.close();
		}
	}

	/**
	 * The message that started the run being rerun. Candidates: messages of this build and category that are
	 * not a rerun, not filtered out, not a coverage build, not on a queue that reuses the category name for
	 * another purpose (memory-leak runs send "medium" too), and complete enough to clone. Preference:
	 * <ol>
	 * <li>the run's own message, when the run recorded it ({@code resultstat.qacomment} for the sql family);</li>
	 * <li>the message consumed last before the run ended ({@code verify_main.test_time}): a resent message
	 * started later belongs to a later run;</li>
	 * <li>the newest consumed message, then the newest one.</li>
	 * </ol>
	 * No ORDER BY: sorting on id would let CUBRID walk the primary key instead of idx_msg_sended_build_id_queue_id.
	 */
	private Original findOriginalMessage(Connection db, Batch batch) throws SQLException {
		List<Original> candidates = new ArrayList<Original>();
		int unusable = 0;
		PreparedStatement ps = db.prepareStatement("SELECT id, msg_id, queue_id, msg_cont, build_type, consume_flag, consume_start_time"
				+ " FROM msg_sended WHERE build_id = ? AND build_scenarios = ? AND is_filtered_out = 'N' AND build_generate_msg_way <> ?");
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
				String content = rs.getString(4);
				Properties props = parseMessageContent(content);
				if (content == null || content.length() >= MAX_MSG_CONT || !hasRequiredKeys(props)) {
					unusable++; // cut off at the column size, or not a message this producer can clone
					continue;
				}
				Original candidate = new Original();
				candidate.id = rs.getInt(1);
				candidate.msgId = rs.getString(2);
				candidate.queue = queue;
				candidate.props = props;
				candidate.consumed = "FINISHED".equalsIgnoreCase(rs.getString(6));
				candidate.consumeStart = rs.getTimestamp(7);
				candidates.add(candidate);
			}
			rs.close();
		} finally {
			ps.close();
		}
		if (candidates.isEmpty()) {
			throw new IllegalStateException("No usable original test message for build " + batch.buildId + " and category "
					+ batch.category + " in msg_sended" + (unusable > 0 ? " (" + unusable + " incomplete)" : "")
					+ ", so the rerun cannot reuse its build package and settings.");
		}
		String runMsgId = runMessageId(db, batch);
		if (runMsgId != null) {
			for (Original candidate : candidates) {
				if (runMsgId.equals(candidate.msgId)) {
					candidate.how = "the run's own message";
					return candidate;
				}
			}
		}
		Timestamp runEnd = runTime(db, batch);
		Original best = null;
		if (runEnd != null) {
			for (Original candidate : candidates) {
				if (candidate.consumeStart != null && !candidate.consumeStart.after(runEnd)
						&& (best == null || candidate.consumeStart.after(best.consumeStart))) {
					best = candidate;
				}
			}
			if (best != null) {
				best.how = "consumed last before the run ended";
				return best;
			}
		}
		for (Original candidate : candidates) {
			if (best == null || (candidate.consumed && !best.consumed) || (candidate.consumed == best.consumed && candidate.id > best.id)) {
				best = candidate;
			}
		}
		best.how = best.consumed ? "newest consumed message" : "newest message";
		return best;
	}

	private static boolean hasRequiredKeys(Properties props) {
		for (String key : REQUIRED_KEYS) {
			if (props.getProperty(key, "").trim().length() == 0) {
				return false;
			}
		}
		return true;
	}

	/** The message id the run recorded, if any: the sql family keeps it in resultstat.qacomment. */
	private String runMessageId(Connection db, Batch batch) {
		if (!"resultstat".equalsIgnoreCase(batch.srcTb) || batch.srcId <= 0) {
			return null;
		}
		PreparedStatement ps = null;
		try {
			ps = db.prepareStatement("SELECT qacomment FROM resultstat WHERE id = ?");
			ps.setInt(1, batch.srcId);
			ResultSet rs = ps.executeQuery();
			String value = rs.next() ? rs.getString(1) : null;
			rs.close();
			return value != null && MSG_ID.matcher(value.trim()).matches() ? value.trim() : null;
		} catch (SQLException e) {
			log("could not read the message id of run " + batch.srcTb + "/" + batch.srcId + ": " + e);
			return null;
		} finally {
			close(ps);
		}
	}

	/** When the run ended (verify_main.test_time), or null. */
	private Timestamp runTime(Connection db, Batch batch) {
		PreparedStatement ps = null;
		try {
			ps = db.prepareStatement("SELECT test_time FROM verify_main WHERE id = ?");
			ps.setInt(1, batch.verifyMainId);
			ResultSet rs = ps.executeQuery();
			Timestamp value = rs.next() ? rs.getTimestamp(1) : null;
			rs.close();
			return value;
		} catch (SQLException e) {
			log("could not read the time of run " + batch.verifyMainId + ": " + e);
			return null;
		} finally {
			close(ps);
		}
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
			// the clone gets its own id and priority
			if (CLONED_KEY.matcher(key).matches() && !Constants.MSG_MSGID.equals(key) && !"MSG_PRIORITY".equals(key)) {
				props.setProperty(key, line.substring(eq + 1).trim());
			}
		}
		return props;
	}

	/**
	 * The package must still be in the build store, or every consumer would fail to install it. The
	 * producer node sees the store on disk; elsewhere (a staging producer) the package URL is asked with
	 * an HTTP HEAD instead.
	 */
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
		File storeMissing = null;
		for (File candidate : candidates) {
			if (candidate.exists()) {
				return;
			}
			// <store root>/<store>/<build>/drop/<file>: when the store itself is here, the file is really gone
			File storeDir = candidate.getParentFile() == null ? null : candidate.getParentFile().getParentFile();
			storeDir = storeDir == null ? null : storeDir.getParentFile();
			if (storeMissing == null && storeDir != null && storeDir.isDirectory()) {
				storeMissing = candidate;
			}
		}
		if (storeMissing != null) {
			throw new IllegalStateException("The build package is no longer in the build store: " + storeMissing.getPath());
		}
		int httpStatus = headStatus(url);
		if (httpStatus == 404 || httpStatus == 410) {
			throw new IllegalStateException("The build package is no longer in the build store: " + url + " (HTTP " + httpStatus + ")");
		}
		if (httpStatus != 200) {
			// unknown (no store here, server error, unreachable): send; a failed install comes back as an error
			log("cannot confirm " + url + " is in the build store (" + (httpStatus < 0 ? "unreachable" : "HTTP " + httpStatus)
					+ "); sending anyway");
		}
	}

	/** HTTP status of a HEAD request to the package URL, or -1 when it cannot be asked. */
	private static int headStatus(String url) {
		java.net.HttpURLConnection connection = null;
		try {
			connection = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
			connection.setRequestMethod("HEAD");
			connection.setConnectTimeout(10000);
			connection.setReadTimeout(10000);
			return connection.getResponseCode();
		} catch (Exception e) {
			return -1;
		} finally {
			if (connection != null) {
				connection.disconnect();
			}
		}
	}

	private javax.jms.Connection openBroker() throws Exception {
		ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(conf.getProperty("activemq_user"),
				conf.getProperty("activemq_pwd"), boundedBrokerUrl(conf.getProperty("activemq_url")));
		javax.jms.Connection connection = factory.createConnection();
		try {
			connection.start();
		} catch (Exception e) {
			connection.close();
			throw e;
		}
		return connection;
	}

	/**
	 * A failover URL retries forever by default, so a broker outage would hang the producer while it holds a
	 * claimed batch. Bound it unless the configuration already sets a timeout.
	 */
	static String boundedBrokerUrl(String url) {
		String value = url == null ? "" : url.trim();
		if (!value.startsWith("failover:") || value.indexOf("timeout=") >= 0) {
			return value;
		}
		String bounds = "timeout=60000&startupMaxReconnectAttempts=3";
		String rest = value.substring("failover:".length());
		if (rest.startsWith("(")) {
			return value + (rest.indexOf(")?") >= 0 ? "&" : "?") + bounds;
		}
		return "failover:(" + rest + ")?" + bounds;
	}

	private void sendToQueue(javax.jms.Connection broker, Message message) throws Exception {
		Session session = broker.createSession(true, Session.SESSION_TRANSACTED);
		try {
			MessageProducer producer = session.createProducer(session.createQueue(message.getQueue()));
			producer.setDeliveryMode(DeliveryMode.PERSISTENT);
			TextMessage text = session.createTextMessage(message.getText());
			text.setStringProperty(Constants.MSG_MSGID, message.getMsgId());
			Set<?> keys = message.getPropertyKeys();
			for (Object key : keys) {
				text.setStringProperty((String) key, message.getProperty((String) key));
			}
			// expires with QAHome's timeout, so a queue nobody serves does not grow without limit
			producer.send(text, DeliveryMode.PERSISTENT, message.getPriority(), messageTtlMillis);
			session.commit();
		} finally {
			session.close();
		}
	}

	/** Same row SendMessage writes, so ConsumerTimer can track consume_start_time/consume_stop_time. */
	private void saveMsgSended(Connection db, Message message, String token) {
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
			// the history copy never carries a usable token
			String content = message.toString().replace(token, "<masked>");
			ps.setString(12, content.length() > MAX_MSG_CONT ? content.substring(0, MAX_MSG_CONT) : content);
			ps.executeUpdate();
		} catch (Exception e) {
			// The message is already on the queue; only the consume-time tracking is lost.
			log("could not record " + message.getMsgId() + " in msg_sended: " + e);
		} finally {
			close(ps);
		}
	}

	/**
	 * Record where the batch went; SENT unless a fast consumer already started it (RUNNING keeps its status
	 * but still gets the queue and message ids). False when the batch was closed meanwhile.
	 */
	private boolean markSent(Connection db, int batchId, String queue, String msgId, String origMsgId) throws SQLException {
		PreparedStatement ps = db.prepareStatement("UPDATE verify_rerun_batch SET status = CASE WHEN status = 'SENDING' THEN 'SENT'"
				+ " ELSE status END, queue_id = ?, msg_id = ?, orig_msg_id = ?, sent_at = SYS_DATETIME"
				+ " WHERE id = ? AND status IN ('SENDING', 'RUNNING')");
		int updated;
		try {
			ps.setString(1, queue);
			ps.setString(2, msgId);
			ps.setString(3, origMsgId);
			ps.setInt(4, batchId);
			updated = ps.executeUpdate();
		} finally {
			ps.close();
		}
		update(db, "UPDATE verify_rerun SET status = 'SENT' WHERE batch_id = ? AND status = 'REQUESTED'", batchId);
		return updated == 1;
	}

	private void fail(Connection db, int batchId, String reason) {
		String message = reason.length() > MAX_ERROR ? reason.substring(0, MAX_ERROR) : reason;
		PreparedStatement ps = null;
		try {
			// Only a batch this producer still owns: a cancel from QAHome (CANCELLED) must not be overwritten.
			ps = db.prepareStatement("UPDATE verify_rerun_batch SET status = 'ERROR', error_message = ?, finished_at = SYS_DATETIME"
					+ " WHERE id = ? AND status IN ('REQUESTED', 'SENDING')");
			ps.setString(1, message);
			ps.setInt(2, batchId);
			int updated = ps.executeUpdate();
			close(ps);
			ps = null;
			if (updated != 1) {
				return; // cancelled meanwhile: its cases are closed already
			}
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

	private static String emptyToNull(String value) {
		return value == null || value.trim().length() == 0 ? null : value.trim();
	}

	static class Batch {
		int id;
		String buildId;
		String category;
		String os;
		int verifyMainId;
		String srcTb;
		int srcId;
		String token;
		String feedbackUrl;
		String logDir;
		int caseCount;
		/** A pull request rerun: refs/pull/<n>/head and the commit QAHome knows on it (null for a normal rerun). */
		String tcRef;
		String tcSha;
	}

	static class Original {
		int id;
		String msgId;
		String queue;
		Properties props;
		boolean consumed;
		Timestamp consumeStart;
		/** Which rule picked it, for the log. */
		String how;
	}
}
