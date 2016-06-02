package com.navercorp.cubridqa.common.grepo;

import java.io.ByteArrayInputStream;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;

public class RepoUtil {

	public static void main(String[] args) throws FileNotFoundException {
		saveFile("e:\\", "a.txt", false, new FileInputStream(new File("e:\\a0.txt")));
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
		
		boolean succ = true;

		byte[] buffer = new byte[2048];
		int len;
		try {
			try {
				fos = new FileOutputStream(file);
			} catch (Exception e0) {
				succ = false;
				file = new File(file.getAbsolutePath() + ".qafetch");
				fos = new FileOutputStream(file);
			}
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
		
		if(succ == false) {
			throw new RuntimeException("error. save as to " + file.getAbsolutePath());
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
