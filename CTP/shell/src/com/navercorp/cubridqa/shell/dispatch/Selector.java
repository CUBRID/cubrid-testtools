package com.navercorp.cubridqa.shell.dispatch;

public class Selector {

	String id;
	String rule;

	public Selector(String id, String rule) {
		this.id = id;
		this.rule = rule == null ? "" : rule.trim();
	}

	public String getId() {
		return this.id;
	}
	
	public String getRule() {
		return rule;
	}
}
