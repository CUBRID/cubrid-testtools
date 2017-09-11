package com.navercorp.cubridqa.shell.dispatch;

public class Host {

	private String ip;
	private int usedNum;
	private boolean exclusive;

	public Host(String ip) {
		this.ip = ip.trim();
		this.usedNum = 0;
	}

	public String getIp() {
		return ip;
	}

	public int getUsedNum() {
		return this.usedNum;
	}

	public void startUse(boolean exclusive) {
		if(exclusive && this.usedNum != 0 ) {
			throw new RuntimeException("Status is incorrect on " + this.ip + " (Used: " + this.usedNum + ") which is expected to use exclusively.");
		}
		
		this.exclusive = exclusive;
		this.usedNum++;
	}

	public void stopUse() {
		this.usedNum--;
		if (this.usedNum < 0) {
			this.usedNum = 0;
		}
		if (this.usedNum == 0) {
			this.exclusive = false;
		}
	}

	@Override
	public String toString() {
		return "Host [ip=" + ip + ", usedNum=" + usedNum + "]";
	}

	protected boolean reachMaximumClients() {
		return usedNum >= 3;
	}

	public boolean isAvailable(boolean expectedExclusive) {
		if (expectedExclusive) {
			return exclusive || usedNum > 0 ? false : true;
		} else {
			return exclusive ? false : !reachMaximumClients();
		}
	}
	
	public boolean isExclusive() {
		return exclusive; 
	}
}
