package com.navercorp.cubridqa.common.coreanalyzer;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.Properties;
import java.util.Set;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.PosixParser;

import com.navercorp.cubridqa.common.ConfigParameterConstants;
import com.navercorp.cubridqa.common.MakeFile;

public class MergeTemplate {
	public static void main(String[] args) throws Exception {
		Options options = new Options();
		options.addOption("t", "template-file", true, "The template file path");
		options.addOption("d", "data-file", true, "The data file path");
		options.addOption("o", "output-file", true, "The out file");
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
		
		if (!cmd.hasOption("t") || !cmd.hasOption("d") || !cmd.hasOption("o")) {
			showHelp("Please input MergeTemplate <-t>, <-d>, <-o> ", options);
			return;
		}
		
		String templateFile = cmd.getOptionValue("t");
		String dataFile = cmd.getOptionValue("d");
		String outputFile = cmd.getOptionValue("o");
		
		File tmpFile = new File(templateFile);
		if(!tmpFile.exists()){
			showHelp("The template file" + tmpFile.getAbsolutePath() + " does not exist!", options);
			return;
		}
		
		File dFile = new File(dataFile);
		if(!dFile.exists()){
			showHelp("The template file" + dFile.getAbsolutePath() + " does not exist!", options);
			return;
		}
		
		
		File outFile = new File(outputFile);
		if(!outFile.exists()){
			showHelp("The template file" + outFile.getAbsolutePath() + " does not exist!", options);
			return;
		}
		
		String templateFileContents = com.navercorp.cubridqa.common.CommonUtils.getFileContent(templateFile);
		
		Properties dataProp = com.navercorp.cubridqa.common.CommonUtils.getProperties(dataFile);
		Set<Object> set = dataProp.keySet();
		Iterator<Object> it = set.iterator();
		String key;
		while (it.hasNext()) {
			key = (String) it.next();
			String value = dataProp.getProperty(key);
			if(!com.navercorp.cubridqa.common.CommonUtils.isEmpty(value) && value.toLowerCase().startsWith("json_file:")){
				String[] vals = value.split(":");
				if(vals == null || vals.length < 2){
					throw new Exception("Please confirm your key format " + value + "  in data file, key->json(key=json_file:data.txt)");
				}
				
				String jsonFileName = vals[2];
				File jsonData = new File(jsonFileName);
				if(!jsonData.exists()){
					throw new Exception("Please confirm your json data file " + jsonData.getAbsolutePath() +  " exists!");
				}
				
				String jsonDataContent = com.navercorp.cubridqa.common.CommonUtils.getFileContent(jsonData.getAbsolutePath());
				com.navercorp.cubridqa.common.CommonUtils.replace(templateFileContents, key, escape(jsonDataContent));
				
			}else if(!com.navercorp.cubridqa.common.CommonUtils.isEmpty(value) && value.toLowerCase().startsWith("file:")){
				String[] vals = value.split(":");
				if(vals == null || vals.length < 2){
					throw new Exception("Please confirm your key format " + value + "  in data file, key->file(key=file:data.txt)");
				}
				
				String dataFileName = vals[2];
				File fdata = new File(dataFileName);
				if(!fdata.exists()){
					throw new Exception("Please confirm your data file " + fdata.getAbsolutePath() +  " exists!");
				}
				
				String fDataContent = com.navercorp.cubridqa.common.CommonUtils.getFileContent(fdata.getAbsolutePath());
				com.navercorp.cubridqa.common.CommonUtils.replace(templateFileContents, key, fDataContent);
			}else{
				com.navercorp.cubridqa.common.CommonUtils.replace(templateFileContents, key, value);
			}
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
		formatter.printHelp("MergeTemplate.sh <-t|--template-file> template-file <-d|--data> data-file <-o|--out> out-file", options);
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
