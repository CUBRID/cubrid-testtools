package com.navercorp.cubridqa.shell.common;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;

public class Log {

	PrintWriter pw = null;

	String fileName;
	String filePath;

	boolean echo = false;

	public Log(String fileName, boolean echo) {
		this(fileName, echo, false);
	}

	public Log(String fileName, boolean echo, boolean append) {

		this.echo = echo;
		this.fileName = fileName;

		File file = new File(fileName);

		if (!file.exists()) {
			try {
				file.getParentFile().mkdirs();
				file.createNewFile();
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}

		// add to get file path
		try {
			filePath = file.getCanonicalPath();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		try {
			pw = new PrintWriter(new FileOutputStream(file, append), true);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

	}

	public void println(String msg) {
		println(msg, true);
	}

	public void print(String msg) {
		println(msg, false);
	}

	public void log(String msg) {
		println(msg, true);
	}

	public void println(String msg, boolean newLine) {
		if (msg == null)
			return;
		if (echo)
			System.out.println(msg);
		try {
			pw.write(msg);
		} catch (Exception e) {
			throw new RuntimeException(e);
		} finally {
			try {
				if (newLine)
					pw.write(Constants.LINE_SEPARATOR);
				pw.flush();
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}
	}

	public String getFileName() {
		return this.fileName;
	}

	public String getFilePath() {
		return filePath;
	}

	public void close() {
		if (pw != null) {
			try {
				pw.flush();
				pw.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
}
