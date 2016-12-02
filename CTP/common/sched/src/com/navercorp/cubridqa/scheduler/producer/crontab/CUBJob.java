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
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import com.navercorp.cubridqa.scheduler.common.CommonUtils;
import com.navercorp.cubridqa.scheduler.common.Constants;
import com.navercorp.cubridqa.scheduler.common.FileReady;
import com.navercorp.cubridqa.scheduler.common.HttpUtil;
import com.navercorp.cubridqa.scheduler.common.Log;
import com.navercorp.cubridqa.scheduler.producer.GeneralExtendedSuite;

@DisallowConcurrentExecution
public class CUBJob implements Job {

	@Override
	public void execute(JobExecutionContext ctx) throws JobExecutionException {
		CUBJobContext jctx = (CUBJobContext) ctx.getJobDetail().getJobDataMap().get("jctx");
		System.out.println("[INFO] Start (job_id=" + jctx.getJobId() + ") " + new java.util.Date());

		Collection<File> freshList = getAllMaxBuild(jctx);

		String lastPropsFilename = CommonUtils.concatFile(CommonUtils.concatFile(Constants.CTP_HOME + File.separator + "conf", "producer"), "scheduler_" + jctx.getJobId() + ".m");
		Properties lastProps;
		try {
			lastProps = loadLastProps(jctx, lastPropsFilename);
		} catch (IOException e1) {
			throw new JobExecutionException(e1);
		}
		for (File f : freshList) {
			try {
				processBuild(jctx, f, lastProps);
				saveLastProps(jctx, lastProps, lastPropsFilename);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	public void processBuild(CUBJobContext jctx, File buildRootFile, Properties lastProps) {

		String buildId = buildRootFile.getName();
		String simpliedBuildId = CommonUtils.toSimplifiedBuildId(buildId);
		String versionId = CommonUtils.getMainVersionId(simpliedBuildId);
		String lastSimplifiedBuildId;
		String autoKey;
		String scenario;
		String queue;
		boolean resetCache = false;
		boolean msgSended = false;
		ArrayList<Properties> testList = jctx.getTestList();
		for (Properties testProps : testList) {
			queue = testProps.getProperty("queue");
			scenario = testProps.getProperty("scenario");
			autoKey = "build_id_for_" + versionId + "_" + queue + "_" + scenario;
			lastSimplifiedBuildId = lastProps.getProperty(autoKey);
			if (lastSimplifiedBuildId == null || (lastSimplifiedBuildId != null && CommonUtils.isGreaterThan_For_BuildId(simpliedBuildId, lastSimplifiedBuildId))) {
				// System.out.println("[INFO] Build Id: " + buildId + "(job_id="
				// + jctx.getJobId() + ", " + autoKey + "=" +
				// lastSimplifiedBuildId + ", " + new Date());
				if (resetCache == false) {
					GeneralExtendedSuite.getInstance(jctx.getMainConfigure(), null, true);
					resetCache = true;
				}
				if (processTest(jctx, buildRootFile, testProps)) {
					lastProps.setProperty(autoKey, simpliedBuildId);
					msgSended = true;
				}
			}
		}

		if (msgSended) {
			String url = jctx.getMainConfigure().getProperty("url.notice_new_build");
			if (url != null) {
				try {
					url = CommonUtils.replace(url, "{buildId}", buildId);
					HttpUtil.getHtmlSource(url);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
	}

	private File waitOneFile(CUBJobContext jctx, File buildRootFile, Properties testProps, String listenFilename) {
		String filename = CommonUtils.concatFile(buildRootFile.getAbsolutePath(), "drop");

		String buildId = buildRootFile.getName();
		String simplifiedBuildId = CommonUtils.toSimplifiedBuildId(buildId);
		listenFilename = CommonUtils.replace(listenFilename, "{1}", buildId);
		listenFilename = CommonUtils.replace(listenFilename, "{1:s}", simplifiedBuildId);

		filename = CommonUtils.concatFile(filename, listenFilename);

		File exactFile = new File(filename);
		FileReady fready = new FileReady(exactFile, true);
		try {
			fready.waitFile();
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
		if (exactFile.exists() == false) {
			return null;
		}

		return exactFile;
	}

	private boolean processTest(CUBJobContext jctx, File buildRootFile, Properties testProps) {

		File exactFile = waitOneFile(jctx, buildRootFile, testProps, CommonUtils.fixListenFilename(jctx.getListenFilename(), buildRootFile.getName(), false));
		if (exactFile == null) {
			return false;
		}

		File[] exactMoreFiles = null;
		if (jctx.getListenMoreFilenames() != null) {
			exactMoreFiles = new File[jctx.getListenMoreFilenames().size()];
			for (int i = 0; i < jctx.getListenMoreFilenames().size(); i++) {
				exactMoreFiles[i] = waitOneFile(jctx, buildRootFile, testProps, CommonUtils.fixListenFilename(jctx.getListenMoreFilenames().get(i), buildRootFile.getName(), false));
				if (exactMoreFiles[i] == null) {
					return false;
				}
			}
		}

		System.out.println("FRESH BUILD: " + buildRootFile);

		String pkgBits = jctx.getPkgBits();
		String pkgType = jctx.getPkgType();
		String queue = testProps.getProperty("queue");
		String scenario = testProps.getProperty("scenario");
		long delay = 0; // delay in millisecond
		try {
			delay = Long.parseLong(testProps.getProperty("delay", "0")) * 1000;
		} catch (Exception e) {
		}
		String extConfig = testProps.getProperty("ext_config");
		String extKeys = testProps.getProperty("ext_keys");

		Properties msgProps = new Properties();
		Properties commonMKEYs = jctx.getCommonMKEYProps();
		if (commonMKEYs != null) {
			msgProps.putAll(commonMKEYs);
		}
		Set set = testProps.keySet();
		Iterator<String> it = set.iterator();
		String key, value;
		while (it.hasNext()) {
			key = it.next().trim();
			value = testProps.getProperty(key);
			if (key.startsWith("MKEY_")) {
				msgProps.put(key, value);
			}
		}

		FileProcess fp = new FileProcess(jctx.getMainConfigure(), exactFile, exactMoreFiles, jctx.getListenFilename(), pkgBits, pkgType, queue, scenario, delay, extConfig, extKeys, msgProps);

		try {
			fp.process();
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		} finally {
			fp.close();
		}
		return true;
	}

	private Properties loadLastProps(CUBJobContext jctx, String lastPropsFilename) throws IOException {

		Properties lastProps = CommonUtils.getProperties(lastPropsFilename);
		if (lastProps == null)
			lastProps = new Properties();
		return lastProps;
	}

	private void saveLastProps(CUBJobContext jctx, Properties lastProps, String lastPropsFilename) {
		Log log = new Log(lastPropsFilename, false);
		Set set = lastProps.keySet();
		Iterator<String> it = set.iterator();
		String key, value;
		while (it.hasNext()) {
			key = it.next();
			value = lastProps.getProperty(key);
			log.println(key + "=" + value);
		}
		log.close();
	}

	public Collection<File> getAllMaxBuild(final CUBJobContext jctx) {
		ArrayList<File> freshList = new ArrayList<File>();

		String root = jctx.getMainConfigure().getRepoRoot();

		File rootFile = new File(root);
		File[] storeList = rootFile.listFiles(new FilenameFilter() {
			@Override
			public boolean accept(File dir, String name) {
				return  CommonUtils.isValidStore(name, jctx.getMainConfigure().isExtendStore());
			}
		});

		File[] buildList;
		File dropFile;
		String simplifiedBuildId, lastSimplifiedBuildId;
		String versionId;
		HashMap<String, String> versionVsBuildIdMap = new HashMap<String, String>();
		HashMap<String, File> versionVsFileMap = new HashMap<String, File>();

		Pattern pattern = Pattern.compile("\\d+\\.\\d+\\.\\d+\\.\\d+");

		for (int i = 0; i < storeList.length; i++) {

			buildList = storeList[i].listFiles();
			for (int j = 0; j < buildList.length; j++) {

				dropFile = new File(CommonUtils.concatFile(buildList[j].getPath(), "drop"));
				if (!dropFile.exists())
					continue;

				simplifiedBuildId = CommonUtils.toSimplifiedBuildId(buildList[j].getName());

				Matcher matcher = pattern.matcher(simplifiedBuildId);
				if (matcher.matches() == false)
					continue;

				if (jctx.getAcceptversions() != null) {
					if (!CommonUtils.isBuildInRules(jctx.getAcceptversions(), simplifiedBuildId)) {
						continue;
					}
				}

				if (jctx.getDenyversions() != null) {
					if (CommonUtils.isBuildInRules(jctx.getDenyversions(), simplifiedBuildId)) {
						continue;
					}
				}

				versionId = CommonUtils.getMainVersionId(simplifiedBuildId);
				if (versionId == null)
					continue;

				lastSimplifiedBuildId = versionVsBuildIdMap.get(versionId);

				if (CommonUtils.isGreaterThan_For_BuildId(simplifiedBuildId, lastSimplifiedBuildId)) {
					versionVsFileMap.put(versionId, buildList[j]);
					versionVsBuildIdMap.put(versionId, simplifiedBuildId);
				}
			}
		}
		return versionVsFileMap.values();
	}

}
