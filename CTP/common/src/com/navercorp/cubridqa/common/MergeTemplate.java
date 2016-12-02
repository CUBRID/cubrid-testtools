package com.navercorp.cubridqa.common;

import java.io.File;
import java.util.Iterator;
import java.util.Properties;
import java.util.Set;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.PosixParser;
import com.navercorp.cubridqa.common.CommonUtils;


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
		
		if (!cmd.hasOption("t") || !cmd.hasOption("d") || !cmd.hasOption("o")) {
			showHelp("Please input MergeTemplate <-t>, <-d>, <-o> ", options);
			return;
		}
		
		String templateFile = cmd.getOptionValue("t");
		String dataFile = cmd.getOptionValue("d");
		String outputFile = cmd.getOptionValue("o");
		
		File tmpFile = new File(templateFile);
		if(!tmpFile.exists()){
			showHelp("The template file " + tmpFile.getAbsolutePath() + " does not exist!", options);
			return;
		}
		
		File dFile = new File(dataFile);
		if(!dFile.exists()){
			showHelp("The template file " + dFile.getAbsolutePath() + " does not exist!", options);
			return;
		}
		
		String templateFileContents = CommonUtils.getFileContent(templateFile, false);
		Properties dataProp = CommonUtils.getProperties(dataFile);
		Set<Object> set = dataProp.keySet();
		Iterator<Object> it = set.iterator();
		String key;
		while (it.hasNext()) {
			key = (String) it.next();
			String value = dataProp.getProperty(key);
			if(!CommonUtils.isEmpty(value) && value.toLowerCase().startsWith("json_file:")){
				String[] vals = value.split(":");
				if(vals == null || vals.length < 2){
					throw new Exception("Please confirm your key format " + value + "  in data file, key->json(key=json_file:data.txt)");
				}
				
				String jsonFileName = vals[1];
				File jsonData = new File(jsonFileName);
				if(!jsonData.exists()){
					throw new Exception("Please confirm your json data file " + jsonData.getAbsolutePath() +  " exists!");
				}
				
				String jsonDataContent = CommonUtils.getFileContent(jsonData.getAbsolutePath());
				templateFileContents = CommonUtils.replace(templateFileContents, "#" + key + "#", CommonUtils.getJson(jsonDataContent));
				
			}else if(!CommonUtils.isEmpty(value) && value.toLowerCase().startsWith("file:")){
				String[] vals = value.split(":");
				if(vals == null || vals.length < 2){
					throw new Exception("Please confirm your key format " + value + "  in data file, key->file(key=file:data.txt)");
				}
				
				String dataFileName = vals[1];
				File fdata = new File(dataFileName);
				if(!fdata.exists()){
					throw new Exception("Please confirm your data file " + fdata.getAbsolutePath() +  " exists!");
				}
				
				String fDataContent = CommonUtils.getFileContent(fdata.getAbsolutePath());
				templateFileContents = CommonUtils.replace(templateFileContents, "#" + key + "#", fDataContent);
			}else{
				templateFileContents = CommonUtils.replace(templateFileContents, "#" + key + "#", value);
			}
		}
	
		if(CommonUtils.isEmpty(templateFileContents)){
			System.out.println("Please confirm your tempalte file and data file are correct!");
			return;
		}
	
		MakeFile outputFileMaker = new MakeFile(outputFile, true);
		outputFileMaker.println(templateFileContents, false);
		outputFileMaker.close();
	}

	private static void showHelp(String error, Options options) {
		if (error != null) {
			System.out.println("Error: " + error);
		}
		HelpFormatter formatter = new HelpFormatter();
		formatter.printHelp("MergeTemplate <-t|--template-file> template-file <-d|--data-file> data-file <-o|--output-file> out-file", options);
		System.out.println();
	}

}
