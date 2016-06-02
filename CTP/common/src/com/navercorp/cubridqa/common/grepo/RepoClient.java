package com.navercorp.cubridqa.common.grepo;

import java.io.ByteArrayInputStream;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.util.Properties;

import com.navercorp.cubridqa.common.CommonUtils;

public class RepoClient {
	public static String url;

	static {
		url = CommonUtils.getSystemProperty("grepo_service_url", null, null) + "/repoService";
		System.out.println("grepo_service_url: " + url);
	}

	public static void askHello() {
		RepoService service;
		String result;
		while (true) {
			try {
				service = getRepoService();
				result = service.hello();
				if (result.equals("HELLO")) {
					break;
				}
			} catch (Exception e) {
				System.out.println("Fail to connect Repo Service.");
			}
			try {
				Thread.sleep(10000);
			} catch (InterruptedException e) {
			}
			System.out.println("RETRY " + new java.util.Date());
		}
	}

	public static InputStream fetch(String repo, String branch, String subPath, String clientSHA1) throws Exception {
		return fetch(repo, "local", branch, subPath, clientSHA1);
	}

	public static InputStream fetch(String repo, String remote, String branch, String subPath, String clientSHA1) throws Exception {
		RepoService service = getRepoService();

		String file = service.fetch(repo, remote, branch, subPath, clientSHA1);

		if (file == null)
			return null;

		return new RemoteFileInputStream(service, file);
	}

	private static RepoService getRepoService() throws MalformedURLException, RemoteException, NotBoundException {
		return (RepoService) Naming.lookup(url);
	}
}

class RemoteFileInputStream extends InputStream {

	RepoService service;
	String remoteFilename;
	int start = 0;
	ByteArrayInputStream bis;

	public RemoteFileInputStream(RepoService service, String remoteFilename) throws Exception {
		this.service = service;
		this.remoteFilename = remoteFilename;
		this.start = 0;
		load();
	}

	@Override
	public int available() throws IOException {
		throw new RuntimeException("Not Support");
	}

	@Override
	public void close() throws IOException {
		bis.close();
	}

	@Override
	public synchronized void mark(int readlimit) {
		throw new RuntimeException("Not Support");
	}

	@Override
	public boolean markSupported() {
		throw new RuntimeException("Not Support");
	}

	@Override
	public int read(byte[] b, int off, int len) throws IOException {
		int cnt = bis.read(b, off, len);
		if (cnt != -1) {
			return cnt;
		}

		boolean hasMore = false;
		try {
			hasMore = load();
		} catch (Exception e) {
			throw new IOException(e);
		}
		if (hasMore == false) {
			return -1;
		}
		return bis.read(b, off, len);
	}

	@Override
	public int read(byte[] b) throws IOException {
		int cnt = bis.read(b);
		if (cnt != -1) {
			return cnt;
		}

		boolean hasMore = false;
		try {
			hasMore = load();
		} catch (Exception e) {
			throw new IOException(e);
		}
		if (hasMore == false) {
			return -1;
		}
		return bis.read(b);
	}

	private boolean load() throws Exception {
		byte[] data = service.readFile(remoteFilename, start);
		if (data == null || data.length == 0) {
			this.bis.close();
			return false;
		}
		this.start += data.length;
		this.bis = new ByteArrayInputStream(data);
		return true;
	}

	@Override
	public synchronized void reset() throws IOException {
		throw new RuntimeException("Not Support");
	}

	@Override
	public long skip(long n) throws IOException {
		throw new RuntimeException("Not Support");
	}

	@Override
	public int read() throws IOException {
		throw new RuntimeException("Not Support");
	}
}
