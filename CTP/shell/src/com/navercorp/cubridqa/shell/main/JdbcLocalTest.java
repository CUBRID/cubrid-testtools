package com.navercorp.cubridqa.shell.main;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.junit.runner.JUnitCore;
import org.junit.runner.Request;
import org.junit.runner.Result;
import org.junit.runner.notification.RunListener;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.TestClass;

import com.navercorp.cubridqa.common.LocalInvoker;
import com.navercorp.cubridqa.shell.common.CommonUtils;
import com.navercorp.cubridqa.shell.common.Constants;
import com.navercorp.cubridqa.shell.common.Log;

public class JdbcLocalTest {
	
	private Context context;
	Log log;
	String category;
	private int totalCaseCount = 0;
	private int failCaseCount = 0;
	private int succCaseCount = 0;
	
	public JdbcLocalTest(Context context){
		this.context = context;

		category = context.getTestCategory();
		if (CommonUtils.isEmpty(category)) {
			category = "jdbc";
		}
		
		context.setTestCategory(category);
		this.context.setLogDir("jdbc");

		String buildId = context.getProperty("main.testing.build_id", "BUILD_ID", false);
		if (!CommonUtils.isEmpty(buildId)) {
			context.setTestBuild(buildId);
		}
		
		String buildBit = context.getProperty("main.testing.build_bits", "BUILD_BITS", false);
		if (!CommonUtils.isEmpty(buildBit)) {
			context.setVersion(buildBit);
		}
		
		log = new Log(CommonUtils.concatFile(context.getCurrentLogDir(), "test_jdbc_" + buildId + ".log"), false);
		this.context.setTestCaseRoot(com.navercorp.cubridqa.common.CommonUtils.translateVariable(this.context.getTestCaseRoot()));
	}

	private void start() throws ClassNotFoundException{
		String scenarioDir = this.context.getTestCaseRoot();
		if (scenarioDir == null || scenarioDir.length() <= 0) {
			this.log.println("[ERROR] Scenario is not configured.");
			return;
		}
		
		ArrayList testCaseList = findTestCase(scenarioDir);
		int totalCaseFile = (testCaseList == null || testCaseList.isEmpty()) ? 0 : testCaseList.size();
		this.log.println("Test Start!");
		this.log.println("Total Case File Count:" + totalCaseFile);
		if (totalCaseFile > 0) {
			context.getFeedback().onTaskStartEvent(context.getTestBuild());
			context.getFeedback().setTotalTestCase(totalCaseCount, 0, 0);
			this.log.println("TEST BUILD:" + context.getTestBuild());

			for (int i = 0; i < totalCaseFile; i++) {
				String caseFullName = testCaseList.get(i).toString();
				this.log.println("TestFile:" + caseFullName + Constants.LINE_SEPARATOR);
				String caseWithPackageName = convertCaseName(caseFullName);
				Class<?> cls = Class.forName(caseWithPackageName);
				runTests(cls, caseWithPackageName);
			}

		} else {
			this.log.println("[ERROR] Not found any test cases.");
		}
		
		this.log.println("Test Finished!");
		this.log.println("==================== Test Summary ====================");
		this.log.println("Total Case:" + totalCaseCount);
		this.log.println("Success Case:" + succCaseCount);
		this.log.println("Fail Case:" + failCaseCount);
	}
	
	
	private void runTests(Class<?> clazz, String caseFullNameWithPackageName){
		TestClass testClass = new TestClass(clazz);
		List<FrameworkMethod> tests = testClass.getAnnotatedMethods(org.junit.Test.class);
		for(FrameworkMethod m: tests){
			String res = "";
			String mothodName = m.getName();
			boolean isSucc = false;
			Request request = Request.method(clazz, mothodName);
			JUnitCore core = new JUnitCore();
			RunListener listener = new RunListener();
			core.addListener(listener);
			Result result = core.run(request);
			if (result.wasSuccessful()) {
				isSucc = true;
				succCaseCount++;
			}else{
				isSucc = false;
				failCaseCount++;
			}
			res = caseFullNameWithPackageName + "=>" +  mothodName + " : " + (isSucc ? "OK" : "NOK") + "=>";
			res += core.toString();
			long runTime = result.getRunTime();
			this.log.println(res + " => Elapse Time:"  + runTime + Constants.LINE_SEPARATOR);
			
			context.getFeedback().onTestCaseStopEvent(caseFullNameWithPackageName + "=>" +  mothodName, isSucc, runTime, res, "local", false, false, Constants.SKIP_TYPE_NO, 0);
		}
	}
	
	
	private String convertCaseName(String caseFullName){
		if(caseFullName == null || caseFullName.length() <0) return null;
		
		String caseUnityName = com.navercorp.cubridqa.common.CommonUtils.concatFile(caseFullName); 
		
		String caseName = "";
		if(this.context.getTestCaseRoot().endsWith(File.separator)){
			caseName = caseUnityName.substring(this.context.getTestCaseRoot().length() + "src/".length(), caseUnityName.length());
		}else{
			caseName = caseUnityName.substring(this.context.getTestCaseRoot().length() + File.separator.length() + "src/".length(), caseUnityName.length());
		}
		
		String caseWithPackageName = caseName.replaceAll(File.separator, ".");
		
		return caseWithPackageName==null? caseWithPackageName: caseWithPackageName.substring(0, caseWithPackageName.indexOf(".class"));
	}
	
	private static String getAllTestCaseScripts(String dir) {
		return "find " + dir + " -name \"*.class\" -type f -print";
	}
	
	private ArrayList<String> findTestCase(String scenarioPath) throws ClassNotFoundException{
		ArrayList<String> caseList = null;
		if(scenarioPath == null) return caseList;
		
		caseList = new ArrayList<String>();
		String result = LocalInvoker.exec(getAllTestCaseScripts(this.context.getTestCaseRoot()), LocalInvoker.SHELL_TYPE_LINUX, false);
		String[] tcArray = result.split("\n");
		
		for (String tc : tcArray) {
			tc = tc.trim();
			if(tc.equals("")) continue;
			
			String className = tc.substring(tc.lastIndexOf(File.separator) + 1, tc.length());
			if(className == null || className.indexOf("$")!=-1) continue;

			String caseClassNameWithoutExt = convertCaseName(tc);
			Class<?> cls = Class.forName(caseClassNameWithoutExt);
			TestClass testClass = new TestClass(cls);
			
			List<FrameworkMethod> tests = testClass.getAnnotatedMethods(org.junit.Test.class);
			if(tests == null || tests.isEmpty()) continue;
			totalCaseCount += tests.size();
			
			caseList.add(tc);
		}
		
		return caseList;
	}
	
	
	public static void main(String[] args) throws IOException, ClassNotFoundException {
		if (args == null || args.length == 0) {
			System.out.println("[ERROR] jdbc.propeties is missing!!");
			return;
		}
		
		String configFilename = args[0];
		Context context = new Context(configFilename);
		JdbcLocalTest test = new JdbcLocalTest(context);
		test.start();
	}

}
