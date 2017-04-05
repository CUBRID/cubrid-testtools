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
import java.io.IOException;
import java.util.ArrayList;

public class SparseFsMain {

	private static ArrayList<String> ruleIncludeList, ruleExcludeList;

	public static void main(String[] args) throws IOException {
		if (args.length != 2) {
			usage();
			return;
		}

		String checkRoot = args[0];
		File checkRootFile = new File(checkRoot);
		if (checkRootFile.exists() == false) {
			System.out.println("[ERROR] " + checkRoot + " doesn't exist");
			usage();
			return;
		}
		String rules = args[1];

		if (rules == null || rules.trim().equals("")) {
			return;
		}

		ruleIncludeList = new ArrayList<String>();
		ruleExcludeList = new ArrayList<String>();
		String[] a = rules.split(",");

		File f;
		boolean isExcluded = false;
		for (String i : a) {
			i = i.trim();
			if (i.equals("")) {
				continue;
			}
			if (i.startsWith("!")) {
				isExcluded = true;
				i = i.substring(1);
			} else {
				isExcluded = false;
			}
			f = new File(checkRoot + "/" + i);
			if (f.exists()) {
				i = f.getCanonicalPath();
				if (isExcluded) {
					ruleExcludeList.add(i);
				} else {
					ruleIncludeList.add(i);
				}
				System.out.println("[INFO] Sparse rule: " + i + (isExcluded ? "(exclude)" : ""));
			} else {
				System.out.println("[ERROR] Sparse rule: invalid " + i);
			}
		}

		if (ruleIncludeList.isEmpty() && ruleExcludeList.isEmpty()) {
			System.out.println("[ERROR] not found valid sparse rules in " + rules);
			return;
		}

		for (String e : ruleExcludeList) {
			deleteAll(new File(e));
		}

		if (ruleIncludeList.isEmpty() == false) {
			travelFsAndAction(checkRootFile);
		}
	}

	private static boolean travelFsAndAction(File curDir) throws IOException {
		if (curDir.getName().equals(".git")) {
			return true;
		}

		if (ruleIncludeList.contains(curDir.getCanonicalPath())) {
			return true;
		}

		// System.out.println(curDir.getCanonicalPath());

		File[] list = curDir.listFiles();
		boolean keepCurrDir = false;
		for (File f : list) {
			if (f.isDirectory()) {
				if (travelFsAndAction(f)) {
					keepCurrDir = true;
				}
			}
		}

		delete(curDir, keepCurrDir);
		return keepCurrDir;
	}

	private static void delete(File curDir, boolean keepCurrDir) {
		File[] list = curDir.listFiles();
		for (File f : list) {
			if (f.isFile()) {
				deleteOne(f);
			}
		}
		if (keepCurrDir == false) {
			deleteOne(curDir);
		}
	}

	private static void deleteAll(File curDir) {
		File[] list = curDir.listFiles();
		for (File f : list) {
			if (f.isFile()) {
				deleteOne(f);
			} else if (f.isDirectory()) {
				deleteAll(f);
			}
		}
		deleteOne(curDir);
	}

	private static void deleteOne(File f) {
		try {
			f.delete();
		} catch (Exception e) {
			System.out.println("Faile to delete " + f.getAbsolutePath() + ". " + e.getMessage());
		}
	}

	private static void usage() {
		System.out.println("Usage: run_sparsefs <check_dir> <rule1,rule2,rule3>");
	}
}
