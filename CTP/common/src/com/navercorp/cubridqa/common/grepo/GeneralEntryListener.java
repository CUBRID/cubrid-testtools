package com.navercorp.cubridqa.common.grepo;

import java.io.InputStream;

public class GeneralEntryListener implements EntryListener {

	@Override
	public boolean beforeFeed(String data) throws Exception {
		return true;
	}

	@Override
	public void feed(String file, boolean isDirectory, InputStream out) {
	}

	@Override
	public void afterFeed(String data) throws Exception {
	}
}
