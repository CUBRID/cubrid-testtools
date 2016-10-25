/**
 * Copyright (c) 2016, Search Solution Corporation. All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without 
 * modification, are permitted provided that the following conditions are met:
 * 
 *   * Redistributions of source code must retain the above copyright notice, 
 *     this list of conditions and the following disclaimer.
 * 
 *   * Redistributions in binary form must reproduce the above copyright 
 *     notice, this list of conditions and the following disclaimer in 
 *     the documentation and/or other materials provided with the distribution.
 * 
 *   * Neither the name of the copyright holder nor the names of its contributors may be used to endorse or promote products 
 *     derived from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, 
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE 
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, 
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR 
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE 
 * USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE. 
 */
package com.navercorp.cubridqa.common;

import java.io.File;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.SftpException;

public class SFTP {

	ChannelSftp sftp;

	public SFTP(ChannelSftp sftp) throws JSchException {
		this.sftp = sftp;
		this.sftp.connect();
	}

	public void download(String remoteFile, String localDir) throws Exception {
		sftp.get(remoteFile, localDir);
	}

	public void copy(String fromFiles, String toFiles, SSHConnect toSsh) throws Exception {
		SFTP toSftp = toSsh.createSFTP();
		copy(fromFiles, toFiles, toSftp);
		toSftp.close();
	}

	public void copy(String fromFiles, String toFiles, SFTP toSftp) throws Exception {
		File localDir = new File("tmp");
		if (localDir.exists() == false) {
			localDir.mkdirs();
		} else {
			File[] subList = localDir.listFiles();
			for (File f : subList) {
				f.delete();
			}
		}
		download(fromFiles, localDir + File.separator);
		int pos = fromFiles.lastIndexOf("\\");
		if (pos == -1) {
			pos = fromFiles.lastIndexOf("/");
		}
		String localFromFiles;
		if (pos == -1) {
			localFromFiles = fromFiles;
		} else {
			localFromFiles = fromFiles.substring(pos + 1);
		}

		toSftp.upload(localDir + File.separator + localFromFiles, toFiles);
	}

	public void mkdir(String path) throws SftpException {
		try {
			sftp.mkdir(path);
		} catch (SftpException e) {
			e.printStackTrace();
		}
	}

	public void cd(String path) throws SftpException {
		sftp.cd(path);
	}

	public void upload(String localFile) throws Exception {
		upload(localFile, ".");
	}

	public void upload(String localFile, String remoteFile) throws Exception {
		sftp.put(localFile, remoteFile);
	}

	public void close() {
		sftp.disconnect();
		sftp.exit();
	}

	public static void main(String[] args) throws Exception {
		SSHConnect ssh1 = new SSHConnect("10.34.64.58", 22, "cubrid_daily", "PG%RB*12");
		SSHConnect ssh2 = new SSHConnect("10.34.64.58", 22, "xdbms", "PG%RB*12");
		SFTP sftp2 = ssh2.createSFTP();

		// sftp1.copy("CUBRID/conf/*.conf", "fanzq", ssh2);
		// sftp1.copy("CUBRID/databases/databases.txt", "fanzq", ssh2);

		sftp2.mkdir("fanzq");
		sftp2.cd("fanzq");
		sftp2.mkdir("fanzq");
		sftp2.cd("fanzq");

		sftp2.mkdir("fanzq");
		sftp2.cd("fanzq");

		sftp2.mkdir("fanzq");
		sftp2.cd("fanzq");

		sftp2.upload("tmp\\databases.txt");

		sftp2.close();
		ssh1.close();
		ssh2.close();
	}
}
