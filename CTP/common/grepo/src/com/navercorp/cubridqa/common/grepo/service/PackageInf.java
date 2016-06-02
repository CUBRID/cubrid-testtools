package com.navercorp.cubridqa.common.grepo.service;

import java.io.File;


import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Properties;

import com.navercorp.cubridqa.common.CommonUtils;

public class PackageInf {

	private static PackageInf instance = new PackageInf();

	File file;
	PrintWriter out = null;

	public static PackageInf getInstance() {
		return instance;
	}

	private PackageInf() {
		Properties props = CommonUtils.getConfig("conf/common.properties");

		this.file = new File(props.getProperty("data_root") + File.separator + "package.inf");

		if (!file.exists()) {
			file.getParentFile().mkdirs();
		}
	}

	public void println(String... line) {
		if (line == null || line.length == 0)
			return;
		try {
			out = new PrintWriter(new FileOutputStream(file, true));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		StringBuffer cont = new StringBuffer();
		try {
			for (String item : line) {
				cont.append(item.trim() + "\t");
			}
			out.write(cont.toString() + "\n");
			out.flush();
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			out.close();
		}
	}
}