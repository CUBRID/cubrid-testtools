package com.navercorp.cubridqa.shell.main;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.util.ArrayList;

import org.junit.runner.JUnitCore;
import org.junit.runner.Request;
import org.junit.runner.Result;
import org.junit.runner.notification.RunListener;

import com.navercorp.cubridqa.common.LocalInvoker;
import com.navercorp.cubridqa.shell.common.CommonUtils;
import com.navercorp.cubridqa.shell.common.Constants;
import com.navercorp.cubridqa.shell.common.Log;

public class JdbcLocalTest {
	
	private Context context;
	Log runCaseLog;
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
		
		runCaseLog = new Log(CommonUtils.concatFile(context.getCurrentLogDir(), "run_case_details.log"), false);
		this.context.setTestCaseRoot(com.navercorp.cubridqa.common.CommonUtils.translateVariable(this.context.getTestCaseRoot()));
	}

	private void start(){
		String scenarioDir = this.context.getTestCaseRoot();
		try {
			if (scenarioDir == null || scenarioDir.length() <= 0) {
				out("[ERROR]:", " Scenario is not configured.", true);
				return;
			}

			ArrayList<JdbcCaseMethodBean> testCaseList = getAllTestCase(scenarioDir);
			int totalCase = (testCaseList == null || testCaseList.isEmpty()) ? 0
					: testCaseList.size();
			out("[INFO]: ", "Test Start!", true);
			out("[INFO]: ", "Total Case:" + totalCase, true);
			if (totalCase > 0) {
				totalCaseCount = totalCase;
				context.getFeedback().onTaskStartEvent(context.getTestBuild());
				context.getFeedback().setTotalTestCase(totalCaseCount, 0, 0);
				out("[INFO]: ", "TEST BUILD:" + context.getTestBuild(), true);
				for (int i = 0; i < totalCase; i++) {
					JdbcCaseMethodBean jdbcCaseMethodBean = (JdbcCaseMethodBean)testCaseList.get(i);
					runTests(jdbcCaseMethodBean);
				}

			} else {
				out("[ERROR]: ", "Not found any test cases.", true);
				return;
			}
		} catch (Exception ex) {
			ex.printStackTrace();
		}finally{
			out("[INFO]: ", "Test Finished!", true);
			out("", "==================== Test Summary ====================", false);
			out("[INFO]: ", "Total Case:" + totalCaseCount, true);
			out("[INFO]: ", "Success Case:" + succCaseCount, true);
			out("[INFO]: ", "Fail Case:" + failCaseCount, true);
			this.runCaseLog.close();
			System.out.println();
			this.context.getFeedback().onTaskStopEvent();
		}
	}
	
	
	private void runTests(JdbcCaseMethodBean caseMethodBean){
			boolean isSucc = false;
			String res = "";
			String failureMessage = "";
			Result result = null;
			String caseFile = caseMethodBean.getCaseFile();
			String methodName = caseMethodBean.getMethodName();
			final PrintStream origStdout = System.out;
			final PrintStream origStderr = System.err;
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			PrintStream ps = new PrintStream(baos);
			System.setOut(ps);
			System.setErr(ps);
			Request req = caseMethodBean.getRequest();
			try {
				JUnitCore core = new JUnitCore();
				RunListener listener = new RunListener();
				core.addListener(listener);
				result = core.run(req);
			} catch (Exception ex) {
				ex.printStackTrace();
				out("[ERROR]: ", ex.getMessage(), true);
				failureMessage += ex.getMessage();
			}
			
			if (result != null && result.wasSuccessful()) {
				isSucc = true;
				succCaseCount++;
			}else{
				isSucc = false;
				failureMessage += result == null ? "" : result.getFailures().get(0).getMessage();
				failCaseCount++;
			}
			
			long runTime = result == null ? 0 : result.getRunTime();
			if(failureMessage.length()>0) {
					res += Constants.LINE_SEPARATOR + failureMessage;
			}
			System.out.flush();
			System.err.flush();
			if(baos.toString()!=null && baos.toString().trim().length() >0){
				res += Constants.LINE_SEPARATOR + baos.toString();
			}
			
			System.setOut(origStdout);
			System.setErr(origStderr);
			out("[TESTCASE]: ", caseFile + " => " + methodName + "() => " + (isSucc?"OK":"NOK"), true);
			out("[ELAPSE TIME(ms)]: ", String.valueOf(runTime), true);
			if(res.trim().length()>0) out("[RUNTIME LOG]: ", Constants.LINE_SEPARATOR + res, true);
			out("", "===================================================", true);
			context.getFeedback().onTestCaseStopEvent(caseFile + " => " + methodName + "()", isSucc, runTime, isSucc? null:res, "local", false, false, Constants.SKIP_TYPE_NO , 0);
	}
	
	
	private String convertCaseName(String caseFullName){
		if(caseFullName == null || caseFullName.length() <0) return null;
		
		String caseUnityName = com.navercorp.cubridqa.common.CommonUtils.concatFile(caseFullName, ""); 
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
	
	private void out(String header, String out, boolean showConsole){
		boolean needHeader = false;
		if(header != null && header.length()>0) needHeader = true;
		
		if(showConsole) System.out.println(needHeader ? (header + out) : out);
	    this.runCaseLog.println(needHeader ? (header + out) : out);
	}
	
	private ArrayList<JdbcCaseMethodBean> getAllTestCase(String scenarioPath){
		ArrayList<JdbcCaseMethodBean> caseList = null;
		if(scenarioPath == null) return caseList;
		
		caseList = new ArrayList<JdbcCaseMethodBean>();
		String result = LocalInvoker.exec(getAllTestCaseScripts(this.context.getTestCaseRoot()), LocalInvoker.SHELL_TYPE_LINUX, false);
		String[] tcArray = result.split("\n");
		for (String tc : tcArray) {
			tc = tc.trim();
			if(tc.equals("")) continue;
			
			String className = tc.substring(tc.lastIndexOf(File.separator) + 1, tc.length());
			if(className == null || className.indexOf("$")!=-1) continue;
			
			String caseClassNameWithoutExt = convertCaseName(tc);
			try {
				Class<?> cls = Class.forName(caseClassNameWithoutExt, false, this.getClass().getClassLoader());
				Method[] methods = cls.getDeclaredMethods();
				if(methods == null || methods.length <=0) continue;
				for(Method m:methods){
					String methodName = m.getName();
					boolean isTestAnnotationMethod = m.isAnnotationPresent(org.junit.Test.class);
					boolean isIgnoreTestAnnotationMethod = m.isAnnotationPresent(org.junit.Ignore.class);
					if(isIgnoreTestAnnotationMethod) continue;
					
					if(isTestAnnotationMethod || (methodName != null && methodName.toUpperCase().indexOf("TEST") != -1)){
						JdbcCaseMethodBean jCaseMethodBean = new JdbcCaseMethodBean();
						Request request = Request.method(cls, methodName);
						jCaseMethodBean.setCaseFile(tc.replaceAll("\\.class", ".java"));
						jCaseMethodBean.setMethodName(methodName);
						jCaseMethodBean.setRequest(request);
						jCaseMethodBean.setClassPackageFullName(caseClassNameWithoutExt);
						caseList.add(jCaseMethodBean);
					}
				}
				
			} catch (ClassNotFoundException e) {
				e.printStackTrace();
			}
			
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

	class JdbcCaseMethodBean {
		private String caseFile;
		private Request request;
		private String classPackageFullName;
		private String methodName;
		
		public String getMethodName() {
			return methodName;
		}
		public void setMethodName(String methodName) {
			this.methodName = methodName;
		}
		public String getClassPackageFullName() {
			return classPackageFullName;
		}
		public void setClassPackageFullName(String classPackageFullName) {
			this.classPackageFullName = classPackageFullName;
		}
		public String getCaseFile() {
			return caseFile;
		}
		public void setCaseFile(String caseFile) {
			this.caseFile = caseFile;
		}
		public Request getRequest() {
			return request;
		}
		public void setRequest(Request request) {
			this.request = request;
		}
	}
}
