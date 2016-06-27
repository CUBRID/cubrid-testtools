package com.navercorp.cubridqa.shell.common;


import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class LocalInvoker {
	
	public static String exec(String cmds, boolean pureWindows, boolean isDebug) {
		File tmpFile;
		try {
			tmpFile = File.createTempFile(".localexec", pureWindows ? ".bat": ".sh");
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		
		FileWriter writer = null;
		try {
			writer = new FileWriter(tmpFile);
			writer.write(cmds);
			writer.flush();
		} catch (IOException e) {
			throw new RuntimeException(e);
		} finally{
			if(writer!=null) {
				try {
					writer.close();
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			}
		}
		
		if (isDebug) {
			System.out.println("Script File: " + tmpFile);
		}
		
		String result = execPlainShell(tmpFile.getAbsolutePath(), pureWindows, isDebug);
		try{
			tmpFile.delete();
		} catch(Exception e) {
			System.out.println("Fail to delete " + tmpFile + " but NO HARM");
		}
		return result;
	}
	
	private static String execPlainShell(String scriptFilename, boolean pureWindows, boolean isDebug) {
		String cmds;
		String pathConversionResult = null;
		if (pureWindows) {
			cmds = scriptFilename + " 2>&1";
		} else {
			while (true) {
				pathConversionResult = execCommands("cygpath " + scriptFilename, false);
				if (pathConversionResult != null && pathConversionResult.trim().length() > 0) {
					break;
				}
			}
			
			cmds = "bash.exe -c " + pathConversionResult.trim() + " 2>&1 \"";
		}
		return execCommands(cmds, true);		
	}

	private static String execCommands(String cmds, boolean isDebug) {
		Runtime run = Runtime.getRuntime();
		Process p = null;
		int pos;

		String result;

		try {			
			p = run.exec(cmds);
            StreamGobbler stdout = new StreamGobbler(p.getInputStream());
            StreamGobbler errout = new StreamGobbler(p.getErrorStream());
            stdout.start();
			errout.start();
			p.waitFor();
			
			result = stdout.getResult() + errout.getResult();
			if(isDebug) {
				System.out.println(result);
			}
			pos = result.indexOf(ShellInput.START_FLAG);
			if (pos != -1) {
				result = result.substring(pos + ShellInput.START_FLAG.length());
			}
			pos = result.indexOf(ShellInput.COMP_FLAG);
			if (pos != -1) {
				result = result.substring(0, pos);
			}
			  
		} catch (Exception e) {  
			throw new RuntimeException(e);  
		}
		return result.toString();
	}
	
	public static void main(String[] args) {
		System.out.println(exec(Constants.WIN_KILL_PROCESS_NATIVE, true, true));
	}
}

class StreamGobbler extends Thread {
	InputStream in;
	StringBuffer buffer = new StringBuffer();

	StreamGobbler(InputStream in) {
		this.in = in;
	}

	public void run() {
		String line;
		InputStreamReader reader = null;
		BufferedReader breader = null;
		
		try {
			reader = new InputStreamReader(in);
			breader = new BufferedReader(reader);
			
			while ((line = breader.readLine()) != null) {
				buffer.append(line).append(Constants.LINE_SEPARATOR);
				if (buffer.toString().indexOf(ShellInput.COMP_FLAG) > 0) {
					break;
				}
			}
		} catch (IOException e) {
			buffer.append("Throw Java IOException: " + e.getMessage());
		} finally{
			try{
				this.in.close();
			} catch(Exception e) {
				buffer.append("Throw Java IOException: " + e.getMessage());
			}
			try{
				if(reader != null ) reader.close();
			} catch(Exception e) {
				buffer.append("Throw Java IOException: " + e.getMessage());
			}
			try{
				if(breader != null ) breader.close();
			} catch(Exception e) {
				buffer.append("Throw Java IOException: " + e.getMessage());
			}			
		}
	}
	
	public String getResult() {
		return buffer.toString();
	}
}
