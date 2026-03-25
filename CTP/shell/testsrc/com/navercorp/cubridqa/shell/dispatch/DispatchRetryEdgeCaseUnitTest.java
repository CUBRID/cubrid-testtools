package com.navercorp.cubridqa.shell.dispatch;

import com.navercorp.cubridqa.shell.main.Context;
import java.io.File;
import java.io.FileWriter;
import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import sun.misc.Unsafe;

public class DispatchRetryEdgeCaseUnitTest {

	private static void assertTrue(String message, boolean condition) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}

	private static Context createContextWithRetry(int maxRetry) throws Exception {
		File tempRoot = File.createTempFile("dispatch-edge-unit-", "");
		tempRoot.delete();
		tempRoot.mkdirs();
		File cfg = new File(tempRoot, "dispatch-edge.conf");
		FileWriter writer = new FileWriter(cfg);
		try {
			writer.write("feedback_type=file\n");
			writer.write("test_category=shell\n");
			writer.write("testcase_retry_num=" + maxRetry + "\n");
		} finally {
			writer.close();
		}

		Context context = new Context(cfg.getAbsolutePath());
		context.setToolHome(tempRoot.getAbsolutePath());
		context.setLogDir("dispatch-edge-unit");
		context.setMaxRetryCount(maxRetry);
		return context;
	}

	private static Dispatch allocateDispatch() throws Exception {
		Field f = Unsafe.class.getDeclaredField("theUnsafe");
		f.setAccessible(true);
		Unsafe unsafe = (Unsafe) f.get(null);
		return (Dispatch) unsafe.allocateInstance(Dispatch.class);
	}

	private static void setField(Object target, String fieldName, Object value) throws Exception {
		Field f = Dispatch.class.getDeclaredField(fieldName);
		f.setAccessible(true);
		f.set(target, value);
	}

	private static Object getField(Object target, String fieldName) throws Exception {
		Field f = Dispatch.class.getDeclaredField(fieldName);
		f.setAccessible(true);
		return f.get(target);
	}

	public static void main(String[] args) throws Exception {
		Context context = createContextWithRetry(2);

		Dispatch dispatch = allocateDispatch();
		HashMap<String, Integer> retryMap = new HashMap<String, Integer>();
		retryMap.put("cases/a.sh", new Integer(1));
		ArrayDeque<String> initialQueue = new ArrayDeque<String>();
		initialQueue.addLast("cases/a.sh");
		HashSet<String> initialQueuedSet = new HashSet<String>();
		initialQueuedSet.add("cases/a.sh");

		setField(dispatch, "context", context);
		setField(dispatch, "tbdList", new ArrayList<String>());
		setField(dispatch, "totalTbdSize", new Integer(0));
		setField(dispatch, "nextTestFileIndex", new Integer(0));
		setField(dispatch, "retryCountMap", retryMap);
		setField(dispatch, "retryQueue", initialQueue);
		setField(dispatch, "queuedRetrySet", initialQueuedSet);
		setField(dispatch, "inProgressRetrySet", new HashSet<String>());
		setField(dispatch, "isFinished", Boolean.FALSE);

		Dispatch.DispatchItem firstRetry = dispatch.nextTestItem();
		assertTrue("first retry item should be dispatched", firstRetry != null);
		assertTrue("first retry count should be 1", firstRetry.getRetryCount() == 1);

		Dispatch.DispatchItem second = dispatch.nextTestItem();
		assertTrue("second pull should be null when queue temporarily empty", second == null);
		assertTrue("dispatch should remain active while retry is in progress", !dispatch.isFinished());

		dispatch.finalizeRetryDispatch("cases/a.sh", false, 1);
		assertTrue("dispatch can be temporarily finished after in-progress retry is completed", dispatch.isFinished());

		dispatch.enqueueRetryFromAttempt("cases/a.sh", 1);
		HashMap mapAfterRequeue = (HashMap) getField(dispatch, "retryCountMap");
		assertTrue("retry map should have requeued item", mapAfterRequeue.size() == 1);
		assertTrue("dispatch should become active again after requeue", !dispatch.isFinished());

		Dispatch.DispatchItem third = dispatch.nextTestItem();
		assertTrue("requeued retry item should be dispatchable", third != null);
		assertTrue("requeued retry count should be 2", third.getRetryCount() == 2);

		dispatch.finalizeRetryDispatch("cases/a.sh", false, 2);
		assertTrue("after processing last retry item dispatch is finished", dispatch.isFinished());

		Dispatch dispatch2 = allocateDispatch();
		HashMap<String, Integer> retryMap2 = new HashMap<String, Integer>();
		setField(dispatch2, "context", context);
		setField(dispatch2, "tbdList", new ArrayList<String>());
		setField(dispatch2, "totalTbdSize", new Integer(0));
		setField(dispatch2, "nextTestFileIndex", new Integer(0));
		setField(dispatch2, "retryCountMap", retryMap2);
		setField(dispatch2, "retryQueue", new ArrayDeque<String>());
		setField(dispatch2, "queuedRetrySet", new HashSet<String>());
		setField(dispatch2, "inProgressRetrySet", new HashSet<String>());
		setField(dispatch2, "isFinished", Boolean.FALSE);

		dispatch2.enqueueRetryFromAttempt("cases/b.sh", 2);
		HashMap mapAfterMax = (HashMap) getField(dispatch2, "retryCountMap");
		assertTrue("max retry should not requeue", mapAfterMax.isEmpty());

		Dispatch dispatch3 = allocateDispatch();
		setField(dispatch3, "context", context);
		setField(dispatch3, "tbdList", new ArrayList<String>());
		setField(dispatch3, "totalTbdSize", new Integer(0));
		setField(dispatch3, "nextTestFileIndex", new Integer(0));
		setField(dispatch3, "retryCountMap", new HashMap<String, Integer>());
		setField(dispatch3, "retryQueue", new ArrayDeque<String>());
		setField(dispatch3, "queuedRetrySet", new HashSet<String>());
		setField(dispatch3, "inProgressRetrySet", new HashSet<String>());
		setField(dispatch3, "isFinished", Boolean.FALSE);

		dispatch3.enqueueRetryFromAttempt("Aa", 0);
		dispatch3.enqueueRetryFromAttempt("BB", 0);

		Dispatch.DispatchItem fifoFirst = dispatch3.nextTestItem();
		assertTrue("FIFO: first dequeued item must be Aa (inserted first)", "Aa".equals(fifoFirst.getTestCase()));
		dispatch3.finalizeRetryDispatch("Aa", false, 1);

		Dispatch.DispatchItem fifoSecond = dispatch3.nextTestItem();
		assertTrue("FIFO: second dequeued item must be BB (inserted second)", "BB".equals(fifoSecond.getTestCase()));
		dispatch3.finalizeRetryDispatch("BB", false, 1);

		assertTrue("dispatch3 should be finished after all FIFO items processed", dispatch3.isFinished());

		System.out.println("DispatchRetryEdgeCaseUnitTest PASSED");
	}
}
