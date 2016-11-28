package com.navercorp.cubridqa.common.coreanalyzer;

import java.io.File;
import java.io.IOException;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.PosixParser;

import com.navercorp.cubridqa.common.MakeFile;

public class AnalyzeCore {
	public static void main(String[] args) throws Exception {
		Options options = new Options();
		options.addOption(null, "create-issue-template", true, "A template of issue creation");
		options.addOption(null, "add-comment-template", true, "A template of issue comment adding");
		options.addOption(null, "output-issue-json-name", true, "A output json file name of issue creation");
		options.addOption(null, "output-comment-json-name", true, "A output json file name of issue comment adding");
		options.addOption("f", "core", true, "A core file");
		options.addOption("pkg", "package-path", true, "The package file path");
		options.addOption("dbv", "db-volume", true, "The package file path");
		options.addOption("err", "err-log", true, "The error log path");
		options.addOption("case", "case-file", true, "The case file path");
		options.addOption("v", "build-version", true, "Affects Version of issue");
		options.addOption("i", "env-info", true, "Environment information");
		options.addOption("d", "target-dir", true, "Target directory to save files");
		options.addOption("h", "help", false, "Show Help");

		CommandLineParser parser = null;
		CommandLine cmd = null;

		try {
			parser = new PosixParser();
			cmd = parser.parse(options, args);
		} catch (Exception e) {
			showHelp(e.getMessage(), options);
			return;
		}
		
		String[] args1 = cmd.getArgs();
		if (args.length == 0 || args1.length == 0 || cmd.hasOption("h")) {
			showHelp(null, options);
			return;
		}
		
		if (!cmd.hasOption("create-issue-template") || !cmd.hasOption("add-comment-template") || !cmd.hasOption("f")|| !cmd.hasOption("v")|| !cmd.hasOption("i")|| !cmd.hasOption("d")) {
			showHelp("Please input parameters <create-issue-templa>, <add-comment-template>, <f>, <v>, <i>, <d>.", options);
			return;
		}

		String buildInfo = cmd.getOptionValue("v");
		String envInfo = cmd.getOptionValue("i");
		String pkgFile = cmd.getOptionValue("pkg");
		String dbVolume = cmd.getOptionValue("dbv");
		String errFile = cmd.getOptionValue("err");
		String caseFile = cmd.getOptionValue("case");
		String issueJsonFileName = cmd.getOptionValue("output-issue-json-name");
		String commentJsonFileName = cmd.getOptionValue("output-comment-json-name");
		
		String createIssueTemplateFile = cmd.getOptionValue("create-issue-template");
		String addIssueCommentTemplateFile = cmd.getOptionValue("add-comment-template");
		String coreFile = cmd.getOptionValue("f");
		File createIssueFile = new File(createIssueTemplateFile);
		if (!createIssueFile.exists()) {
			showHelp("Not exists for " + createIssueFile.getAbsolutePath(), options);
			return;
		}
		
		File addCommentFile = new File(addIssueCommentTemplateFile);
		if (!addCommentFile.exists()) {
			showHelp("Not exists for " + addCommentFile.getAbsolutePath(), options);
			return;
		}
		
		String targetDir = cmd.getOptionValue("d");
		File targetFileDir = new File(targetDir);
		if(!targetFileDir.exists() && !targetFileDir.isDirectory()){
			targetFileDir.mkdir();
		}
		
		File core = new File(coreFile);
		if(!core.exists()){
			showHelp("Not exists for " + core.getAbsolutePath(), options);
		}
		
		String[] results = AnalyzerMain.fetchCoreFullStack(core);
		if (results != null && results.length >= 2) {
			String summaryInfo = results[0];
			System.out.println(summaryInfo);
			String coreFullCallStack = results[1];
			
			
			
			StringBuffer descriptionContents = new StringBuffer();
			descriptionContents.append("*Test Build:*" + buildInfo);
			descriptionContents.append("*Call Stack Info:*" + Constants.LINE_SEPARATOR + coreFullCallStack);
			String createISsueJsonContent = readJsonTemplate(createIssueTemplateFile);
			com.navercorp.cubridqa.common.CommonUtils.replace(createISsueJsonContent, "#DESCRIPTION#", escape(descriptionContents.toString()));
			com.navercorp.cubridqa.common.CommonUtils.replace(createISsueJsonContent, "#SUMMARY#", escape(summaryInfo));
			
			
			StringBuffer commentBodyContents = new StringBuffer();
			commentBodyContents.append("*Test Server:*" + Constants.LINE_SEPARATOR + envInfo);
			commentBodyContents.append("pwd: <please use general password>");
			commentBodyContents.append(Constants.LINE_SEPARATOR  + "*All Info:*" + Constants.LINE_SEPARATOR);
			commentBodyContents.append(envInfo + ":" + pkgFile);
			commentBodyContents.append("*Core Location:*" + core.getAbsolutePath());
			commentBodyContents.append("*DB-Volume Location:*" + dbVolume);
			commentBodyContents.append("*Error Log Location:*" + errFile);
			commentBodyContents.append("*Related Case:*" + caseFile);
			
			String addISsueCommentJsonContent = readJsonTemplate(addIssueCommentTemplateFile);
			com.navercorp.cubridqa.common.CommonUtils.replace(addISsueCommentJsonContent, "#COMMENTBODY#", escape(commentBodyContents.toString()));
			
			MakeFile issueJsonfile = new MakeFile(targetDir + File.separator + issueJsonFileName, false);
			issueJsonfile.println(createISsueJsonContent);
			issueJsonfile.close();
			
			MakeFile issueCommentJsonFile = new MakeFile(targetDir + File.separator + commentJsonFileName, false);
			issueCommentJsonFile.println(addISsueCommentJsonContent);
			issueCommentJsonFile.close();
		}
	}

	public static String readJsonTemplate(String templateFilePath) throws IOException {
		String templateContents = com.navercorp.cubridqa.common.CommonUtils.getFileContent(templateFilePath);
		return templateContents;
	}
	
	private static void showHelp(String error, Options options) {
		if (error != null) {
			System.out.println("Error: " + error);
		}
		HelpFormatter formatter = new HelpFormatter();
		formatter.printHelp("analyzer_core.sh [options] core_file <bts_issue_no>", options);

		System.out.println("\nfor example: ");
		System.out.println(" sh analyzer.sh core_file");
		System.out.println(" sh analyzer.sh --save core_file bts_issue_no");
		System.out.println();
	}

	public static String escape(String string) {
		StringBuilder result = new StringBuilder();

		if (string == null || string.length() == 0) {
			result.append("\"\"");
			return result.toString();
		}

		char b;
		char c = 0;
		String hhhh;
		int i;
		int len = string.length();

		result.append('"');
		for (i = 0; i < len; i += 1) {
			b = c;
			c = string.charAt(i);
			switch (c) {
			case '\\':
			case '"':
				result.append('\\');
				result.append(c);
				break;
			case '/':
				if (b == '<') {
					result.append('\\');
				}
				result.append(c);
				break;
			case '\b':
				result.append("\\b");
				break;
			case '\t':
				result.append("\\t");
				break;
			case '\n':
				result.append("\\n");
				break;
			case '\f':
				result.append("\\f");
				break;
			case '\r':
				result.append("\\r");
				break;
			default:
				if (c < ' ' || (c >= '\u0080' && c < '\u00a0') || (c >= '\u2000' && c < '\u2100')) {
					result.append("\\u");
					hhhh = Integer.toHexString(c);
					result.append("0000", 0, 4 - hhhh.length());
					result.append(hhhh);
				} else {
					result.append(c);
				}
			}
		}
		result.append('"');
		return result.toString();
	}

}
