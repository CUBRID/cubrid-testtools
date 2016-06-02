package com.navercorp.cubridqa.common.grepo;

import java.rmi.Remote;

public interface RepoService extends Remote {

	public String fetch(String repo, String branch, String subPath, String clientSHA1) throws Exception;

	public String fetch(String repo, String remote, String branch, String subPath, String clientSHA1) throws Exception;

	public byte[] readFile(String fileName, long start) throws Exception;
	
	public String hello()  throws Exception;

}
