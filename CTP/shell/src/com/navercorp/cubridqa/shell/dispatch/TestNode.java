package com.navercorp.cubridqa.shell.dispatch;

public class TestNode {

	public final static int TYPE_DEFAULT = 1;
	public final static int TYPE_FOLLOW = 2;
	public final static int TYPE_SPECIFIC = 3;

	private String envId;
	private int type;
	private Host host;
	private boolean active;

	public TestNode(String envId, int type, Host host) {
		this.envId = envId;
		this.type = type;
		this.host = host;
		this.active = false;
	}

	public String getEnvId() {
		return envId;
	}

	public int getType() {
		return this.type;
	}

	public Host getHost() {
		return host;
	}

	@Override
	public int hashCode() {
		return envId == null ? 0 : envId.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		TestNode other = (TestNode) obj;
		if (envId == null) {
			if (other.envId != null)
				return false;
		} else if (!envId.trim().equals(other.envId.trim()))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "TestNode [envId=" + envId + ", type=" + type + ", host=" + host + "]";
	}

	public boolean isActive() {
		return active;
	}
	
	public boolean isIdle() {
		return !active;
	}

	public void activate(boolean exclusive) {
		this.active = true;
		getHost().startUse(exclusive);
	}
	
	public void deactivate() {
		this.active = false;
		getHost().stopUse();
	}

	public boolean isAvailable(boolean expectedExclusive) {
		return isActive() ? false: this.host.isAvailable(expectedExclusive);
	}
}
