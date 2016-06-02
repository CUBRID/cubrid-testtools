package com.navercorp.cubridqa.common.grepo;

import java.io.InputStream;

public interface EntryListener {
	
	public boolean beforeFeed(String data) throws Exception;
	
	public void feed(String file, boolean isDirectory, InputStream out);
	
	public void afterFeed(String data) throws Exception;

}
