package com.navercorp.cubridqa.shell.dispatch;

import java.util.ArrayList;

import com.navercorp.cubridqa.common.CommonUtils;
import com.navercorp.cubridqa.common.ConfigParameterConstants;

public class TestNodePool {

	private static boolean enableDebug = CommonUtils.convertBoolean(System.getenv(ConfigParameterConstants.CTP_DEBUG_ENABLE), false);

	private ArrayList<TestNode> pool;

	public TestNodePool() {
		pool = new ArrayList<TestNode>();
	}

	public synchronized void addTestNode(String envId, int type, String ip) throws Exception {
		Host host = getOrCreateHost(ip);
		TestNode node = new TestNode(envId, type, host);
		if (this.pool.contains(node)) {
			throw new Exception("Can not add duplicated node to the pool. EnvId = " + envId);
		}
		this.pool.add(node);
	}

	protected synchronized TestNode borrowNode(String envId, boolean exclusive) {
		TestNode node;
		for (int i = 0; i < pool.size(); i++) {
			node = pool.get(i);
			if (node.isAvailable(exclusive) && node.getEnvId().equals(envId)) {
				node.activate(exclusive);
				if (enableDebug) {
					System.out.println("=>borrow " + envId + ", " + exclusive);
				}
				return node;
			}
		}
		return null;
	}

	protected synchronized boolean isAnyAvailable(String envId) {
		for (TestNode node : pool) {
			if (envId.equals(node.getEnvId())) {
				return node.getHost().isExclusive() == false && node.getHost().reachMaximumClients() == false;
			}
		}
		return false;
	}

	protected synchronized ArrayList<TestNode> borrowNodes(String leaderEnvId, String rule, boolean exclusive) {
		ArrayList<TestNode> resultList = searchNodes(leaderEnvId, rule, exclusive);
		if (resultList == null) {
			return null;
		}
		
		ArrayList<String> ips = new ArrayList<String>();
		for (TestNode node : resultList) {
			if (enableDebug) {
				System.out.println("=>borrow " + node.getEnvId() + ", " + exclusive);
			}
			
			if (ips.contains(node.getHost().getIp())) {
				node.activate(false);
			} else {
				node.activate(exclusive);
			}
			ips.add(node.getHost().getIp());
		}
		return resultList;
	}

	private ArrayList<TestNode> searchNodes(String leaderEnvId, String rule, boolean exclusive) {
		if (rule == null)
			return null;

		ArrayList<TestNode> resultList = new ArrayList<TestNode>();
		String[] groupArr = rule.split(";");
		String[] instanceArr;
		String[] orArr;
		String currEnvId;
		TestNode currNode;
		boolean found;
		for (int i = 0; i < groupArr.length; i++) {
			if (groupArr[i].trim().equals("")) {
				continue;
			}

			resultList.clear();

			instanceArr = groupArr[i].trim().split(",");
			for (int j = 0; j < instanceArr.length; j++) {
				if (instanceArr[j].trim().equals("")) {
					continue;
				}
				found = false;
				orArr = instanceArr[j].trim().split("\\|");
				for (int k = 0; k < orArr.length; k++) {
					currEnvId = orArr[k].trim();
					if (currEnvId.equals("")) {
						continue;
					}
					currNode = getAvailTestNode(currEnvId, resultList, leaderEnvId, exclusive);
					if (currNode != null) {
						resultList.add(currNode);
						found = true;
						break;
					}
				}
				if (found == false) {
					resultList.clear();
					break;
				}
			}
			if (resultList.size() > 0) {
				return resultList;
			}
		}
		return null;
	}

	protected synchronized void returnNodes(ArrayList<TestNode> finishedNodeList) {
		if (finishedNodeList == null || finishedNodeList.size() == 0) {
			return;
		}
		for (TestNode n : finishedNodeList) {
			for (int i = 0; i < pool.size(); i++) {
				if (this.pool.get(i).getEnvId().equals(n.getEnvId())) {
					if(enableDebug) {
						System.out.println("=>return " + n.getEnvId());
					}
					this.pool.get(i).deactivate();
				}
			}
		}
	}

	protected boolean checkRule(String rule) {
		rule = CommonUtils.replace(rule, "|", ",");
		rule = CommonUtils.replace(rule, ";", ",");
		String[] list = rule.split(",");
		boolean find = false;
		for (String envId : list) {
			if (CommonUtils.isEmpty(envId)) {
				continue;
			}
			if (envId.trim().equals("%")) {
				continue;
			}

			if (envId.trim().equals("*")) {
				continue;
			}
			find = false;
			for (TestNode node : pool) {
				if (node.getEnvId().equals(envId.trim())) {
					find = true;
					break;
				}
			}
			if (!find) {
				return false;
			}

		}
		return true;
	}

	private void returnAllNodes() {
		returnNodes(this.pool);
	}

	private Host getOrCreateHost(String ip) {
		ip = ip.trim();
		for (TestNode p : pool) {
			if (p.getHost().getIp().equals(ip)) {
				return p.getHost();
			}
		}
		return new Host(ip);
	}

	private TestNode getAvailTestNode(String envId, ArrayList<TestNode> existingList, String leaderEnvId, boolean exclusive) {
		if (envId == null || envId.trim().equals("")) {
			return null;
		}
		envId = envId.trim();
		
		boolean expectDiffIp = false;
		if(envId.equals("%")) {
			expectDiffIp = true;
			envId = "*";
		}

		boolean isFirstTestNode = existingList.size() == 0;
		TestNode node;

		if (isFirstTestNode) {
			if (envId.equals("*") == false && envId.equals(leaderEnvId) == false) {
				return null;
			}
			for (int i = 0; i < pool.size(); i++) {
				node = pool.get(i);
				if (node.isAvailable(exclusive) && node.getEnvId().equals(leaderEnvId)) {
					return node;
				}
			}
			return null;
		}

		if (envId.equals("*")) {
//			if (!isFirstTestNode) {
//				for (int i = 0; i < pool.size(); i++) {
//					node = pool.get(i);
//					if (node.isAvailable(exclusive) && node.getType() == TestNode.TYPE_FOLLOW && contains(existingList, node, exclusive) == false && meetIpConstanit(existingList, node, expectDiffIp)) {
//						return node;
//					}
//				}
//			}

			for (int i = 0; i < pool.size(); i++) {
				node = pool.get(i);
				if (node.isAvailable(exclusive) && node.getType() == TestNode.TYPE_DEFAULT && contains(existingList, node, exclusive) == false && meetIpConstanit(existingList, node, expectDiffIp)) {
					return node;
				}
			}

		} else {
			for (TestNode p : pool) {
				if (p.isAvailable(exclusive) && p.getEnvId().equals(envId)) {
					return p;
				}
			}
		}
		return null;
	}

	private boolean contains(ArrayList<TestNode> list, TestNode checkNode, boolean exclusive) {
		for (TestNode node : list) {
			if (exclusive) {
				if (node.getEnvId().equals(checkNode.getEnvId()) || node.getHost() == checkNode.getHost()) {
					return true;
				}
			} else if (node.getEnvId().equals(checkNode.getEnvId())) {
				return true;
			}
		}
		return false;
	}
	
	private boolean meetIpConstanit(ArrayList<TestNode> list, TestNode checkNode, boolean expectDiffIp) {
		if (expectDiffIp == false) {
			return true;
		}
		for (TestNode node : list) {
			if (node.getHost() == checkNode.getHost()) {
				return false;
			}
		}
		return true;
	}

	public static void main(String[] args) throws Exception {
		TestNodePool pool = new TestNodePool();
		pool.addTestNode("instance1", TestNode.TYPE_DEFAULT, "10.34.64.59");
		pool.addTestNode("instance2", TestNode.TYPE_DEFAULT, "10.34.64.59");
		pool.addTestNode("instance3", TestNode.TYPE_FOLLOW, "10.34.64.59");
		pool.addTestNode("instance4", TestNode.TYPE_FOLLOW, "10.34.64.59");
		pool.addTestNode("instance5", TestNode.TYPE_SPECIFIC, "10.34.64.59");

		ArrayList<TestNode> list;
		boolean exclusive = false;

		list = pool.borrowNodes("instance1", "instance1", exclusive);
		System.out.println(list.size() == 1);
		System.out.println(list.get(0).getEnvId().equals("instance1"));
		System.out.println("-----1-----");
		pool.returnNodes(list);

		list = pool.borrowNodes("instance1", "instance1,instance2", exclusive);
		System.out.println(list.size() == 2);
		System.out.println(list.get(0).getEnvId().equals("instance1"));
		System.out.println(list.get(1).getEnvId().equals("instance2"));
		pool.returnNodes(list);
		System.out.println("-----2-----");

		list = pool.borrowNodes("instance2", "instance1,instance2", exclusive);
		System.out.println(list == null || list.size() == 0);
		pool.returnNodes(list);
		System.out.println("------3----");

		list = pool.borrowNodes("instance2", "instance1|instance2", exclusive);
		System.out.println(list.size() == 1);
		System.out.println(list.get(0).getEnvId().equals("instance2"));
		pool.returnNodes(list);
		System.out.println("-----4-----");

		pool.borrowNodes("instance2", "instance2", exclusive);
		list = pool.borrowNodes("instance3", "instance1,instance2;instance3,instance4|instance5", exclusive);
		System.out.println(list.size() == 2);
		System.out.println(list.get(0).getEnvId().equals("instance3"));
		System.out.println(list.get(1).getEnvId().equals("instance4"));
		pool.returnAllNodes();
		System.out.println("----5------");

		pool.borrowNodes("instance2", "instance2", exclusive);
		pool.borrowNodes("instance4", "instance4", exclusive);
		list = pool.borrowNodes("instance3", "instance1,instance2;instance3,instance4|instance5", exclusive);
		System.out.println(list.size() == 2);
		System.out.println(list.get(0).getEnvId().equals("instance3"));
		System.out.println(list.get(1).getEnvId().equals("instance5"));
		pool.returnAllNodes();
		System.out.println("-----6-----");

		pool.borrowNodes("instance2", "instance2", exclusive);
		pool.borrowNodes("instance4", "instance4", exclusive);
		list = pool.borrowNodes("instance3", "instance1,instance2;instance3,*", exclusive);
		System.out.println(list.size() == 2);
		System.out.println(list.get(0).getEnvId().equals("instance3"));
		System.out.println(list.get(1).getEnvId().equals("instance5") || list.get(1).getEnvId().equals("instance1"));
		pool.returnAllNodes();
		System.out.println("-----7-----");

		pool.borrowNodes("instance2", "instance2", exclusive);
		pool.borrowNodes("instance4", "instance4", exclusive);
		list = pool.borrowNodes("instance1", "instance1,instance2;*,*", exclusive);
		System.out.println(list.size() == 2);
		System.out.println(list.get(0).getEnvId().equals("instance1"));
		System.out.println(list.get(1).getEnvId().equals("instance3"));
		pool.returnAllNodes();
		System.out.println("-----8-----");

		list = pool.borrowNodes("instance2", "*,*", exclusive);
		System.out.println(list.size() == 2);
		System.out.println(list.get(0).getEnvId());
		System.out.println(list.get(1).getEnvId());
		pool.returnAllNodes();
		System.out.println("-----8a-----");

		pool.borrowNodes("instance2", "instance2,instance3,instance5", exclusive);
		list = pool.borrowNodes("instance1", "instance1,*", exclusive);
		System.out.println(list.size() == 2);
		System.out.println(list.get(0).getEnvId().equals("instance1"));
		System.out.println(list.get(1).getEnvId().equals("instance4"));
		pool.returnAllNodes();
		System.out.println("-----9-----");

	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		for (TestNode node : pool) {
			sb.append(node.toString()).append("\n");
		}
		return sb.toString();
	}
}