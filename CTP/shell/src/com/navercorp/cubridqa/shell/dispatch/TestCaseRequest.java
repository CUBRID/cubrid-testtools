package com.navercorp.cubridqa.shell.dispatch;

import java.util.ArrayList;
import java.util.Properties;

import com.navercorp.cubridqa.common.CommonUtils;

public class TestCaseRequest {

	private String testCase;
	Properties props;
	private ArrayList<TestNode> nodeList;
	private boolean hasTestNodes;

	public TestCaseRequest(String testCase, Properties props) {
		this.testCase = testCase;
		this.props = props;
		this.hasTestNodes = true;
	}

	public String getTestCase() {
		return testCase;
	}

	public String getExpectedMachines() {
		String expectedMachines = props == null ? null : props.getProperty("machines");
		return expectedMachines == null || expectedMachines.trim().equals("") ? null : expectedMachines.trim();
	}

	public String[] getCubridDeps() {
		String cubridDeps = props == null ? null : props.getProperty("cubrid_deps");
		return cubridDeps == null ? null : cubridDeps.trim().split(",");
	}

	public String[] getCubridPkgDeps() {
		String cubridPkgDeps = props == null ? null : props.getProperty("cubrid_pkg_deps");
		return cubridPkgDeps == null ? null : cubridPkgDeps.trim().split(",");
	}

	public String[] getRepoDeps() {
		String repoDeps = props == null ? null : props.getProperty("repo_deps");
		return repoDeps == null ? null : repoDeps.trim().split(",");
	}

	public String getNote() {
		String note = props == null ? null : props.getProperty("note");
		return note;
	}

	public boolean isExclusive() {
		String exclusive = props == null ? null : props.getProperty("exclusive");
		return CommonUtils.convertBoolean(exclusive, false);

	}

	public void setProperty(String key, String value) {
		if (key == null) {
			return;
		}
		if (props == null) {
			props = new Properties();
		}
		if (key != null) {
			key = key.trim();
		}
		if (value != null) {
			value = value.trim();
		}
		this.props.setProperty(key, value);
	}

	public String getProperty(String key) {
		if (props == null)
			return key;
		return props.getProperty(key);
	}

	public void addTestNode(TestNode node) {
		if (this.nodeList == null) {
			this.nodeList = new ArrayList<TestNode>();
		}
		this.nodeList.add(node);

	}

	public void setNodeList(ArrayList<TestNode> nodeList) {
		this.nodeList = nodeList;
	}

	public ArrayList<TestNode> getNodeList() {
		return this.nodeList;
	}

	@Override
	public int hashCode() {
		return testCase == null ? 0 : testCase.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		TestCaseRequest other = (TestCaseRequest) obj;
		return this.testCase.equals(other.getTestCase());
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(this.testCase).append(" ");
		if (this.props == null) {
			sb.append("NULL");
		} else {
			sb.append(this.props.toString());
		}
		sb.append("\n");
		if (nodeList != null) {
			for (TestNode n : nodeList) {
				sb.append(n.toString()).append("\n");
			}
		}
		return sb.toString();
	}

	public void setHasTestNodes(boolean value) {
		this.hasTestNodes = value;
	}

	public boolean hasTestNodes() {
		return this.hasTestNodes;
	}
}
