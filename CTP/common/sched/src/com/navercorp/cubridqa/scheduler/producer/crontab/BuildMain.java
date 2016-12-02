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
package com.navercorp.cubridqa.scheduler.producer.crontab;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Properties;

import com.navercorp.cubridqa.scheduler.common.CommonUtils;
import com.navercorp.cubridqa.scheduler.common.Constants;
import com.navercorp.cubridqa.scheduler.producer.Configure;

public class BuildMain {

	Configure conf;
	String buildId;

	public BuildMain(Configure conf) throws Exception {
		this.conf = conf;
	}

	public void startup(File buildRootFile) throws Exception {

		ArrayList<CUBJobContext> jctxList = conf.findValidJobContexts();
		CUBJob job;
		Properties lastProps;
		for (CUBJobContext jctx : jctxList) {
			job = new CUBJob();
			lastProps = new Properties();
			job.processBuild(jctx, buildRootFile, lastProps);
		}
	}

	/**
	 * @param args
	 * @throws Exception
	 */
	public static void main(String[] args) throws Exception {
		if (args == null || args.length != 2) {
			usage("Invalid arguments");
			return;
		}

		String config = args[0];
		String buildId = args[1];

		File f = new File(Constants.CTP_HOME + File.separator + "conf" + File.separator + config);
		if (f.exists() == false) {
			usage("The config_file_name does not exist. Please check " + f.getAbsolutePath());
			return;
		}

		if (CommonUtils.isEmpty(buildId)) {
			usage("The cubrid_build_id does not exist. Please check it.");
			return;
		}

		config = config.trim();
		buildId = buildId.trim();

		Configure conf = new Configure(config);

		File buildRootFile = getBuildRootFile(conf, buildId);
		if (buildRootFile == null) {
			usage("The cubrid_build_id does not exist. Please check it.");
			return;
		}

		File dropDir = new File(buildRootFile.getAbsolutePath() + File.separator + "drop");
		if (buildRootFile.exists() == false || buildRootFile.isDirectory() == false || dropDir.exists() == false || dropDir.isDirectory() == false || dropDir.length() == 0) {
			usage("Invalid cubrid_build_id. Please check " + buildId + " and ensure there are packages in " + dropDir.getAbsolutePath());
			return;
		}

		try {
			BuildMain m = new BuildMain(conf);
			m.startup(buildRootFile);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static void usage(String err) {
		if (CommonUtils.isEmpty(err) == false) {
			System.out.println("ERROR: " + err);
		}
		System.out.println("Usage: generate_build_test.sh config_file_name cubrid_build_id");
		System.out.println("For example: ");
		System.out.println("    generate_build_test.sh job_manual.conf 10.1.0.7260-37a6808");
		System.out.println("");
		System.out.println("Note: job_manual.conf should be in " + Constants.CTP_HOME + File.separator + "conf" + File.separator);
		System.out.println("");
	}

	private static File getBuildRootFile(Configure conf, String buildId) {
		String root = conf.getRepoRoot();

		File rootFile = new File(root);
		File[] storeList = rootFile.listFiles(new FilenameFilter() {
			@Override
			public boolean accept(File dir, String name) {
				return com.navercorp.cubridqa.scheduler.common.CommonUtils.isValidStore(name, true);
			}
		});

		File[] buildList;
		for (File storeFile : storeList) {
			buildList = storeFile.listFiles();
			for (File f : buildList) {
				if (f.getName().toLowerCase().equals(buildId.toLowerCase())) {
					return f;
				}
			}
		}
		return null;
	}
}