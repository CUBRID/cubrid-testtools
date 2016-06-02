package com.navercorp.cubridqa.common.grepo.service;

import java.util.ArrayList;

public class EmptyCache {

	private final static EmptyCache instance = new EmptyCache();

	private ArrayList<String> list = new ArrayList<String>();

	public static EmptyCache getInstance() {
		return instance;
	}

	public void cache(String item) {
		System.out.println("[EMPTY CACHE] " + item + "  ADD");
		list.add(item);
		int len = list.size() - 1000;
		if (len > 0) {
			for (int i = 0; i < len; i++) {
				try {
					list.remove(0);
				} catch (Exception e) {
				}
			}
		}
	}

	public boolean has(String item) {
		boolean r = list.contains(item);
		System.out.println("[EMPTY CACHE] " + item + "  " + (r ? "FOUND" : "NOT FOUND"));
		return r;
	}
}
