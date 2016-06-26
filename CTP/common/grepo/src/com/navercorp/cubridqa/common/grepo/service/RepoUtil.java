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
package com.navercorp.cubridqa.common.grepo.service;

import java.io.ByteArrayInputStream;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;

import org.eclipse.jgit.api.DiffCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffEntry.ChangeType;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;

import com.navercorp.cubridqa.common.grepo.EntryListener;
import com.navercorp.cubridqa.common.grepo.GeneralEntryListener;

public class RepoUtil {

	public static final HashMap<String, Git> gitMap = new HashMap<String, Git>();

	public static void traverseRepo(String gitRoot, String remoteName, String branchName, String filePath, EntryListener listener) throws Exception {

		Git git = getGit(gitRoot);
		Repository repository = git.getRepository();

		RevCommit revCommit = getTheLatestRevCommit(gitRoot, remoteName, branchName);
		if (listener != null) {
			if (listener.beforeFeed(revCommit.name()) == false) {
				return;
			}
		}
		RevTree revTree = revCommit.getTree();

		TreeWalk treeWalk = new TreeWalk(repository);
		treeWalk.addTree(revTree);
		if (filePath != null && filePath.trim().equals("") == false) {
			treeWalk.setFilter(PathFilter.create(filePath));
		}
		treeWalk.setRecursive(true);

		ObjectId objectId;
		ObjectLoader loader;
		while (treeWalk.next()) {
			objectId = treeWalk.getObjectId(0);

			loader = repository.open(objectId);
			if (listener != null)
				listener.feed(treeWalk.getPathString(), treeWalk.isSubtree(), loader.openStream());
		}
		treeWalk.close();

		if (listener != null) {
			listener.afterFeed(null);
		}

	}

	public static void traverseRepo(String gitRoot, String remoteName, String branchName, String filePath, String sinceSHA1, EntryListener listener) throws Exception {
		Git git = getGit(gitRoot);
		Repository repository = git.getRepository();

		RevCommit theLatestCommit = getTheLatestRevCommit(gitRoot, remoteName, branchName);
		if (listener != null) {
			if (listener.beforeFeed(theLatestCommit.name()) == false) {
				return;
			}
		}
		ObjectReader reader = repository.newObjectReader();
		
		CanonicalTreeParser newTree = new CanonicalTreeParser();
		newTree.reset(reader, theLatestCommit.getTree().getId());

		ObjectId oldObj = repository.resolve(sinceSHA1 + "^{tree}");
		CanonicalTreeParser oldTree = new CanonicalTreeParser();
		oldTree.reset(reader, oldObj);

		ObjectId objectId;
		ObjectLoader loader;
		DiffCommand diff = git.diff().setShowNameAndStatusOnly(false).setOldTree(oldTree).setNewTree(newTree);
		if (filePath != null && filePath.trim().equals("") == false) {
			diff = diff.setPathFilter(PathFilter.create(filePath));
		}
		List<DiffEntry> list = diff.call();
		for (DiffEntry f : list) {
			if (f.getChangeType() == ChangeType.DELETE) {
				continue;
			}
			objectId = f.getNewId().toObjectId();
			

			if (listener != null) {
				loader = repository.open(objectId);
				listener.feed(f.getNewPath(), false, loader.openStream());
			}
		}
		if (listener != null) {
			listener.afterFeed(null);
		}
	}

	public static RevCommit getTheLatestRevCommit(String gitRoot, String remoteName, String branchName) throws IOException {
		Git git = getGit(gitRoot);
		Repository repository = git.getRepository();

		String path;
		if (remoteName == null || remoteName.equals("local")) {
			path = "refs/heads/" + branchName;
		} else {
			path = "refs/remotes/" + remoteName + "/" + branchName;
		}
		ObjectId objId = repository.resolve(path);
		RevWalk walk = new RevWalk(repository);
		RevCommit revCommit = walk.parseCommit(objId);

		return revCommit;
	}

	private static Git getGit(String gitRoot) throws IOException {
		Git git = gitMap.get(gitRoot);

		if (git == null) {
			File gitFile = new File(gitRoot);
			if (gitFile.exists() == false)
				gitFile = new File(gitRoot + ".git");
			git = Git.open(gitFile);
			gitMap.put(gitRoot, git);
		}

		return git;
	}

	public static void saveFile(String pathRoot, String subPath, boolean isFoler, InputStream in) {
		FileOutputStream fos = null;
		File file = new File(pathRoot + File.separator + subPath);

		if (file.getParentFile().exists() == false) {
			file.getParentFile().mkdirs();
		}
		if (isFoler) {
			if (file.exists() == false) {
				file.mkdirs();
			}
			return;
		}

		byte[] buffer = new byte[2048];
		int len;
		try {
			if (file.exists()) {
				file.delete();
			}
			fos = new FileOutputStream(file);
			while ((len = in.read(buffer)) != -1) {
				fos.write(buffer, 0, len);
			}
			fos.close();
		} catch (Exception e) {
			throw new RuntimeException(e);
		} finally {
			try {
				if (fos != null)
					fos.close();
			} catch (Exception e1) {
				e1.printStackTrace();
			}
		}
	}

	public static void traverseZip(String file, EntryListener listener) throws Exception {
		FileInputStream fileIn = new FileInputStream(file);

		try {
			traverseZip(fileIn, listener);
		} finally {
			try {
				fileIn.close();
			} catch (Exception e) {
			}
		}
	}

	public static void traverseZip(InputStream is, EntryListener listener) throws Exception {
		boolean r = listener.beforeFeed(null);
		if (r == false)
			return;

		JarInputStream jarIn = null;
		ZipEntry entry;

		try {
			jarIn = new JarInputStream(is);
			while (true) {
				entry = jarIn.getNextEntry();
				if (entry == null) {
					break;
				}

				listener.feed(entry.getName(), entry.isDirectory(), jarIn);
			}
		} finally {
			try {
				if (is != null)
					is.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
			try {
				if (jarIn != null)
					jarIn.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
			listener.afterFeed(null);
		}

	}

	public static void traverseFileSystem(File file, EntryListener listener) throws Exception {
		if (listener == null) {
			return;
		}
		File[] list = file.listFiles();
		for (File f : list) {
			if (f.isDirectory()) {
				traverseFileSystem(f, listener);
				listener.feed(f.getAbsolutePath(), true, null);
			} else {
				listener.feed(f.getAbsolutePath(), false, null);
			}
		}
	}

	public static void saveZip(InputStream is, String filename, JarOutputStream out) throws IOException {
		saveZip(is, filename, out, null);
	}

	public static void saveZip(String fileContent, String filename, JarOutputStream out, String trimPath) throws IOException {
		ByteArrayInputStream bis = new ByteArrayInputStream(fileContent.getBytes());
		saveZip(bis, filename, out, trimPath);

	}

	public static void saveZip(InputStream is, String filename, JarOutputStream out, String trimPath) throws IOException {
		byte[] buffer = new byte[4096];
		int len = -1;

		ZipEntry entry = new ZipEntry(convertPath(filename, trimPath));
		out.putNextEntry(entry);

		CRC32 crc32 = new CRC32();
		while ((len = is.read(buffer)) != -1) {
			out.write(buffer, 0, len);
			crc32.update(buffer, 0, len);
		}
		entry.setCrc(crc32.getValue());
		out.closeEntry();
	}

	public static String convertPath(String oriPath, String trimPath) {
		if (trimPath == null || trimPath.equals("")) {
			return oriPath;
		}
		oriPath.replace('\\', '/');
		trimPath.replace('\\', '/');

		if (oriPath.startsWith("/")) {
			oriPath = oriPath.substring(1);
		}
		if (trimPath.startsWith("/")) {
			trimPath = trimPath.substring(1);
		}
		int p = oriPath.indexOf(trimPath);
		if (p == 0) {
			oriPath = oriPath.substring(trimPath.length());
		}
		if (oriPath.startsWith("/")) {
			oriPath = oriPath.substring(1);
		}
		return oriPath;

	}

}
