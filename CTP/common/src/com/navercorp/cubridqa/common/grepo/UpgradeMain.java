package com.navercorp.cubridqa.common.grepo;

import java.io.File;

import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Properties;
import java.util.jar.JarOutputStream;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.PosixParser;

import com.navercorp.cubridqa.common.CommonUtils;

public class UpgradeMain {

	public static void main(String[] args) throws Exception {

		Options options = new Options();
		options.addOption("r", "repo", true, "Repository name");
		options.addOption("b", "branch", true, "Branch name in repository");
		options.addOption("e", "exclude", true, "Excluded list to update");
		options.addOption("p", "path", true, "Path in repository");
		options.addOption(null, "reset", false, "Reset files");
		options.addOption(null, "check-only", false, "Check whether thers is updated codes");
		options.addOption("h", "help", false, "List help");

		CommandLineParser parser = null;
		CommandLine cmd = null;

		try {
			parser = new PosixParser();
			cmd = parser.parse(options, args);
		} catch (Exception e) {
			showHelp(e.getMessage(), options);
			return;
		}

		if (cmd.hasOption("help") || cmd.getArgList().size() != 1) {
			showHelp(null, options);
			return;
		}

		if (cmd.getArgList().isEmpty()) {
			showHelp("Please input the destination directory", options);
			return;
		}

		if (cmd.hasOption("repo") == false) {
			showHelp("Please set value for 'repo' option", options);
			return;
		}
		if (cmd.hasOption("branch") == false) {
			showHelp("Please set value for 'branch' option", options);
			return;
		}
		if (cmd.hasOption("path") == false) {
			showHelp("Please set value for 'path' option", options);
			return;
		}

		boolean skipUpgrade = CommonUtils.convertBoolean(CommonUtils.getSystemProperty("SKIP_UPGRADE", "FALSE", null), false);
		if (skipUpgrade) {
			System.out.println("[INFO] SKIP UPGRADE");
			return;
		}

		String grepoServiceUrl = CommonUtils.getSystemProperty("grepo_service_url", null, null);
		if (grepoServiceUrl == null || grepoServiceUrl.trim().equals("")) {
			System.out.println("[ERROR] not found grepo service url. Please set grepo_service_url in configuration file");
			return;
		}

		System.out.println("[INFO] START (" + new java.util.Date() + ")");
		RepoClient.askHello();

		final String destDir = new File((String) cmd.getArgList().get(0)).getCanonicalPath();
		final String repo = cmd.getOptionValue("repo");
		final String branch = cmd.getOptionValue("branch");
		String subPath = cmd.getOptionValue("path");
		String excludes = cmd.getOptionValue("exclude");
		boolean isReset = cmd.hasOption("reset");
		boolean isCheckOnly = cmd.hasOption("check-only");

		final File destDirFile = new File(destDir);

		if (destDirFile.exists() == false) {
			destDirFile.mkdirs();
		}

		final ArrayList<String> excludedList = convertToExcludedList(destDirFile.getAbsolutePath(), excludes);
		excludedList.add("QA_META.INF");
		excludedList.add(".dailyqa");

		File qaDirFile = new File(destDirFile.getAbsolutePath() + File.separator + ".dailyqa");
		if (qaDirFile.exists() == false) {
			qaDirFile.mkdirs();
		}

		subPath = subPath.trim();
		if (subPath.equals("/") || subPath.equals(".")) {
			subPath = "";
		}
		String subPathname = subPath.replace('/', '_');
		String pkgFilename = qaDirFile.getAbsolutePath() + File.separator + repo + "_" + (branch.replace('/', '_'));
		if (subPathname.equals("") == false) {
			pkgFilename += "_" + subPathname;
		}
		pkgFilename += ".zip";

		File pkgFile = new File(pkgFilename);

		File pkgTmpFile = new File(pkgFile.getAbsolutePath() + ".tmp");
		if (pkgTmpFile.exists())
			pkgTmpFile.delete();

		final Properties ini = new Properties();
		String sha1 = "";
		if (pkgFile.exists()) {
			EntryListener listener = new GeneralEntryListener() {
				@Override
				public void feed(String file, boolean isDirectory, InputStream is) {
					if (file.equals("QA_META.INF")) {
						ini.putAll(CommonUtils.getConfig(is));
					}
				}
			};
			RepoUtil.traverseZip(pkgFile.getAbsolutePath(), listener);
		}
		sha1 = ini.getProperty("sha1");
		if (sha1 == null) {
			sha1 = "";
		}

		System.out.println("[INFO] begin to fetch ... ");
		System.out.println("\tREPO:\t\t" + repo);
		System.out.println("\tBRANCH:\t\t" + branch);
		System.out.println("\tPATH:\t\t" + subPath);
		System.out.println("\tCLIENT SHA1:\t" + sha1);

		InputStream is = RepoClient.fetch(repo, branch, subPath, sha1);

		System.out.println("[INFO] fetch done: " + (is == null ? "NO CHANGE" : "CHANGED"));

		if (isCheckOnly) {
			return;
		}

		if (is != null) {
			System.out.println("[INFO] begin to download ... ");
			if (sha1 == null || pkgFile.exists() == false) {
				RepoUtil.saveFile(qaDirFile.getAbsolutePath(), pkgFile.getName(), false, is);
			} else {
				final JarOutputStream out = new JarOutputStream(new FileOutputStream(pkgTmpFile));
				final ArrayList<String> fileList = new ArrayList<String>();
				EntryListener listener = new GeneralEntryListener() {

					@Override
					public void feed(String file, boolean isDirectory, InputStream is) {
						try {
							if (isDirectory == false && fileList.contains(file) == false) {
								RepoUtil.saveZip(is, file, out);
								fileList.add(file);
							}
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
				};
				RepoUtil.traverseZip(is, listener);
				RepoUtil.traverseZip(pkgFile.getAbsolutePath(), listener);
				if (pkgFile.exists())
					pkgFile.delete();

				try {
					if (out != null)
						out.close();
				} catch (Exception e) {

				}
				pkgTmpFile.renameTo(pkgFile);
				if (pkgTmpFile.exists())
					pkgTmpFile.delete();
			}
		}
		System.out.println("[INFO] got package file: " + pkgFile.getCanonicalPath());

		final ArrayList<String> pkgFileList = new ArrayList<String>();
		EntryListener listener = new GeneralEntryListener() {

			@Override
			public void feed(String file, boolean isDirectory, InputStream is) {
				pkgFileList.add(file);
				if (isInExcludedList(excludedList, file) == false) {
					try {
						RepoUtil.saveFile(destDir, file, isDirectory, is);
					} catch (Exception e) {
						System.err.println("[ERROR] can't save file " + file + "  :" + e.getMessage());
					}
				}
			}
		};
		System.out.println("[INFO] begin to expand files ... ");
		RepoUtil.traverseZip(pkgFile.getAbsolutePath(), listener);
		System.out.println("[INFO] expand done");

		if (isReset) {
			EntryListener listener1 = new GeneralEntryListener() {

				String root = destDir + File.separator;
				String fixedfile;

				@Override
				public void feed(String file, boolean isDirectory, InputStream is) {
					fixedfile = CommonUtils.replace(file, root, "").replace('\\', '/');

					boolean has = pkgFileList.remove(fixedfile);

					if (has == false && isInExcludedList(excludedList, fixedfile) == false) {
						new File(file).delete();
					}
				}
			};

			System.out.println("[INFO] begin to reset files ... ");
			RepoUtil.traverseFileSystem(destDirFile, listener1);
			System.out.println("[INFO] reset done");
		}

		System.out.println("[INFO] DONE " + (new java.util.Date()));
	}

	private static ArrayList<String> convertToExcludedList(String root, String excludes) {
		ArrayList<String> excludedList = new ArrayList<String>();
		if (excludes == null) {
			return excludedList;
		}
		String[] arr = excludes.split(" ");
		File f;
		String sub;
		for (String i : arr) {
			sub = i.trim().replace('\\', '/');
			if (sub.equals(""))
				continue;
			if (sub.startsWith("/")) {
				sub = sub.substring(1);
			}
			f = new File(root + File.separator + sub);
			if (f.exists() == false) {
				excludedList.add(sub);
				continue;
			}

			if (f.isDirectory()) {
				if (sub.endsWith("/")) {
					excludedList.add(sub);
				} else {
					excludedList.add(sub + "/");
				}
			} else {
				excludedList.add(sub);
			}
		}

		return excludedList;
	}

	private static boolean isInExcludedList(ArrayList<String> list, String name) {
		name = name.replace('\\', '/');
		for (String r : list) {
			if (r.equals(name))
				return true;
		}

		for (String r : list) {
			if ((name + "/").startsWith(r.endsWith("/") ? r : r + "/"))
				return true;
		}

		return false;
	}

	private static void showHelp(String error, Options options) {
		if (error != null)
			System.out.println("Error: " + error);
		HelpFormatter formatter = new HelpFormatter();
		formatter.printHelp("run_grepo_fetch: fetch files in CUBRID repositories", options);
		System.out.println();
	}
}
