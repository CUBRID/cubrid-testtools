package com.navercorp.cubridqa.common.grepo.service;

import java.io.ByteArrayOutputStream;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.Properties;
import java.util.jar.JarOutputStream;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

import com.navercorp.cubridqa.common.CommonUtils;
import com.navercorp.cubridqa.common.grepo.GeneralEntryListener;
import com.navercorp.cubridqa.common.grepo.RepoService;
import com.navercorp.cubridqa.common.grepo.service.RepoServiceImpl;
import com.navercorp.cubridqa.common.grepo.service.RepoUtil;
import com.navercorp.cubridqa.common.grepo.service.UpdateRepoThread;

public class RepoServiceImpl extends UnicastRemoteObject implements RepoService {

	Properties props;
	String dataRoot;
	String repoRoot;

	protected RepoServiceImpl(Properties props) throws RemoteException {
		super();
		this.props = props;
		this.dataRoot = props.getProperty("data_root", "");
		this.repoRoot = props.getProperty("repo_root", "");
		String gitUser = props.getProperty("git.user", "");
		String gitPwd = props.getProperty("git.pwd", "");

		System.out.println("data_root: " + this.dataRoot);
		System.out.println("repo_root: " + this.repoRoot);
		System.out.println("grepo_port: " + props.getProperty("grepo_port"));
		System.out.println("git.user: " + gitUser);
		System.out.println("git.pwd: (" + gitPwd.length() + ")");

		boolean enableFetchThread = CommonUtils.convertBoolean(props.getProperty("grepo_enable_fetch_thread"), false);
		System.out.println("grepo_enable_fetch_thread: " + enableFetchThread);
		if (enableFetchThread) {
			new UpdateRepoThread(this.repoRoot, gitUser, gitPwd).start();
		}
	}

	public String hello() throws Exception {
		return "HELLO";
	}

	public String fetch(String repo, String branch, String subPath, String clientSHA1) throws Exception {
		return fetch(repo, "local", branch, subPath, clientSHA1);
	}

	@Override
	public String fetch(String repo, String remote, String branch, String subPath, String clientSHA1) throws Exception {
		System.out.println("repo:" + repo + ", remote: " + remote + ", branch: " + branch + ", path:" + subPath + ", client sha1:" + clientSHA1 + ", client: " + this.getClientHost());

		if (subPath != null) {
			subPath = subPath.trim();
			if (subPath.equals("/") || subPath.equals(".")) {
				subPath = "";
			}
		}

		FetchEntryListener listener = null;
		if (clientSHA1 == null || clientSHA1.trim().equals("")) {
			listener = new FetchEntryListener(repo, branch, subPath, null);
			RepoUtil.traverseRepo(repoRoot + File.separator + repo, remote, branch, subPath, listener);
		} else {
			listener = new FetchEntryListener(repo, branch, subPath, clientSHA1);
			RepoUtil.traverseRepo(repoRoot + File.separator + repo, remote, branch, subPath, clientSHA1, listener);
		}
		if (listener.getZipFile() == null) {
			return null;
		} else {
			return listener.getZipFile().getAbsolutePath();
		}

	}

	@Override
	public byte[] readFile(String fileName, long start) throws Exception {

		File root = new File(dataRoot);
		if (fileName.startsWith(root.getAbsolutePath()) == false) {
			throw new Exception("Deny");
		}

		if (start == 0) {
			System.out.println("DOWN " + fileName + "(" + new java.util.Date() + ")");
		}

		RandomAccessFile rf = null;
		ByteArrayOutputStream out = null;
		int remainLen = 10 * 1000 * 1000;
		int buff_size;
		byte[] buffer = new byte[4096];
		byte[] data;
		try {
			rf = new RandomAccessFile(fileName, "r");
			rf.seek(start);

			out = new ByteArrayOutputStream();

			while (true) {
				buff_size = rf.read(buffer);
				if (buff_size == -1) {
					break;
				}

				out.write(buffer, 0, buff_size);
				remainLen = remainLen - buff_size;
				if (remainLen <= 0) {
					break;
				}
			}
			data = out.toByteArray();
		} finally {
			try {
				if (rf != null)
					rf.close();
			} catch (Exception e) {
				e.printStackTrace();
			}

			try {
				if (out != null) {
					out.close();
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return data;
	}

	class FetchEntryListener extends GeneralEntryListener {

		String repo;
		String branch;
		String subPath;
		String clientSHA1;
		File zipFile, tmpFile;
		String theLastestSHA1;
		boolean enterFeed = false;

		JarOutputStream jout;

		public FetchEntryListener(String repo, String branch, String subPath, String clientSHA1) {
			this.repo = repo;
			this.branch = branch;
			this.subPath = subPath;
			this.clientSHA1 = clientSHA1;
			this.enterFeed = false;
		}

		@Override
		public boolean beforeFeed(String theLastestSHA1) throws Exception {
			this.theLastestSHA1 = theLastestSHA1;

			if (this.theLastestSHA1 != null && this.clientSHA1 != null && this.theLastestSHA1.equals(this.clientSHA1)) {
				return false;
			}

			String branchName = branch.replace('/', '_');
			String subPathName = (subPath == null || subPath.equals("")) ? "ALL" : subPath.replace('/', '_');
			String fromSHA1Name = this.clientSHA1 == null ? "BEGIN" : clientSHA1.substring(0, 10);
			String toSHA1Name = theLastestSHA1.substring(0, 10);

			this.zipFile = new File(dataRoot + File.separator + repo + "_" + branchName + "_" + subPathName + "_" + fromSHA1Name + "_" + toSHA1Name + ".zip");
			if (zipFile.exists()) {
				return false;
			}

			if (EmptyCache.getInstance().has(this.zipFile.getName())) {
				this.zipFile = null;
				return false;
			}

			zipFile.getParentFile().mkdirs();
			tmpFile = new File(zipFile.getAbsolutePath() + ".tmp." + System.currentTimeMillis());
			this.jout = new JarOutputStream(new FileOutputStream(tmpFile));
			return true;
		}

		@Override
		public void feed(String file, boolean isDirectory, InputStream is) {
			try {
				enterFeed = true;
				RepoUtil.saveZip(is, file, jout, subPath);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		private File getZipFile() {
			return zipFile;
		}

		@Override
		public void afterFeed(String data) throws Exception {
			if (enterFeed == false) {
				try {
					if (jout != null)
						jout.close();
				} catch (Exception e) {
					// e.printStackTrace();
				}

				EmptyCache.getInstance().cache(this.zipFile.getName());
				zipFile = null;
				if (tmpFile != null && tmpFile.exists()) {
					tmpFile.delete();
				}
			} else {
				StringBuffer qaMeta = new StringBuffer();
				qaMeta.append("sha1=" + theLastestSHA1);
				RepoUtil.saveZip(qaMeta.toString(), "QA_META.INF", jout, subPath);

				try {
					if (jout != null)
						jout.close();
				} catch (Exception e) {
					// e.printStackTrace();
				}

				if (tmpFile != null && tmpFile.exists()) {
					if (zipFile.exists() == false) {
						tmpFile.renameTo(zipFile);
						java.util.Date d = new java.util.Date();
						PackageInf.getInstance().println(zipFile.getName(), d.toString() + "|" + d.getTime(), (clientSHA1 == null || clientSHA1.trim().equals("") ? "BEGIN" : clientSHA1),
								this.theLastestSHA1);
					} else {
						tmpFile.delete();
					}
				}
			}
		}
	}

	public static void main(String[] args) throws RemoteException {
		Properties props = CommonUtils.getConfig("conf/common.properties");
		RepoService service = new RepoServiceImpl(props);

		int port = Integer.parseInt(props.getProperty("grepo_port", "1099"));
		LocateRegistry.createRegistry(port);

		LocateRegistry.getRegistry(port).rebind("repoService", service);

		System.out.println("Service Start!");
	}
}

class UpdateRepoThread extends Thread {

	ArrayList<File> repoList = new ArrayList<File>();
	CredentialsProvider credentialsProvider;

	public UpdateRepoThread(String repoRoot, String gitUser, String gitPwd) {
		File rootFile = new File(repoRoot);
		File[] list = rootFile.listFiles();
		for (File f : list) {
			if (f.isDirectory()) {
				repoList.add(f);
				System.out.println("[REPO_UPDATE] add " + f);
			}
		}
		this.credentialsProvider = new UsernamePasswordCredentialsProvider(gitUser, gitPwd);
	}

	@Override
	public void run() {
		Git git = null;
		while (true) {
			for (File f : repoList) {
				try {
					git = Git.open(f);
					git.fetch().setTimeout(30 * 1000).setCredentialsProvider(credentialsProvider).call();
					System.out.println("[REPO_UPDATE] fetch done " + f + " (" + new java.util.Date() + ")");
				} catch (Exception e) {
					e.printStackTrace();
				} finally {
					if (git != null)
						git.close();
				}
			}
			try {
				Thread.sleep(5 * 1000);
			} catch (InterruptedException e) {
			}
		}
	}
}
