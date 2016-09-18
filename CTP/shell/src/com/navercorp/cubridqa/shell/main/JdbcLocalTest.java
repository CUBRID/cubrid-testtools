package com.navercorp.cubridqa.shell.main;

import java.io.File;
import java.io.IOException;
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
		
		log = new Log(CommonUtils.concatFile(context.getCurrentLogDir(), "test_jdbc.log"), false);
		this.context.setTestCaseRoot(com.navercorp.cubridqa.common.CommonUtils.translateVariable(this.context.getTestCaseRoot()));
	}

	private void start(){
		String scenarioDir = this.context.getTestCaseRoot();
		try {
			if (scenarioDir == null || scenarioDir.length() <= 0) {
				this.log.println("[ERROR] Scenario is not configured.");
				return;
			}

			ArrayList<JdbcCaseMethodBean> testCaseList = getAllTestCase(scenarioDir);
			int totalCase = (testCaseList == null || testCaseList.isEmpty()) ? 0
					: testCaseList.size();
			this.log.println("Test Start!");
			this.log.println("Total Case:" + totalCase);
			if (totalCase > 0) {
				totalCaseCount = totalCase;
				context.getFeedback().onTaskStartEvent(context.getTestBuild());
				context.getFeedback().setTotalTestCase(totalCaseCount, 0, 0);
				this.log.println("TEST BUILD:" + context.getTestBuild());
				for (int i = 0; i < totalCase; i++) {
					JdbcCaseMethodBean jdbcCaseMethodBean = (JdbcCaseMethodBean)testCaseList.get(i);
					runTests(jdbcCaseMethodBean);
					this.log.println("===================================================");
				}

			} else {
				this.log.println("[ERROR] Not found any test cases.");
				return;
			}
		} catch (Exception ex) {
			ex.printStackTrace();
		}finally{
			this.log.println("Test Finished!");
			this.log.println("==================== Test Summary ====================");
			this.log.println("Total Case:" + totalCaseCount);
			this.log.println("Success Case:" + succCaseCount);
			this.log.println("Fail Case:" + failCaseCount);
			this.log.close();
		}
	}
	
	
	private void runTests(JdbcCaseMethodBean caseMethodBean){
			boolean isSucc = false;
			String res = "";
			String failureMessage = "";
			Result result = null;
			int ingoreCount = 0;
			String caseFile = caseMethodBean.getCaseFile();
			String caseClassPackageFullName = caseMethodBean.getClassPackageFullName();
			String methodName = caseMethodBean.getMethodName();
			log.println("Case File:" + caseFile);
			Request req = caseMethodBean.getRequest();
			try {
				JUnitCore core = new JUnitCore();
				RunListener listener = new RunListener();
				core.addListener(listener);
				result = core.run(req);
				ingoreCount = result.getIgnoreCount();
			} catch (Exception ex) {
				ex.printStackTrace();
				this.log.println("[Execute Error] " + ex.getMessage());
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
			
			res = caseClassPackageFullName + " => " + methodName  + " : " + (isSucc ? "OK" : "NOK");
			long runTime = result == null ? 0 : result.getRunTime();
			this.log.println(res + " => Elapse Time:"  + runTime);
			if(failureMessage.length()>0) {
				res += Constants.LINE_SEPARATOR + failureMessage;
				this.log.println("Failure Message:" + Constants.LINE_SEPARATOR + failureMessage);
			}
			context.getFeedback().onTestCaseStopEvent(caseClassPackageFullName + "=>" +  methodName, isSucc, runTime, res, "local", false, false, ingoreCount==0 ? Constants.SKIP_TYPE_NO : Constants.SKIP_TYPE_BY_INVALID_UNITCASE , 0);
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
				Class<?> cls = Class.forName(caseClassNameWithoutExt);
				Method[] methods = cls.getDeclaredMethods();
				if(methods == null || methods.length <=0) continue;
				for(Method m:methods){
					String methodName = m.getName();
					boolean isTestAnnotationMethod = m.isAnnotationPresent(org.junit.Test.class);
					if(isTestAnnotationMethod || (methodName !=null && !methodName.equalsIgnoreCase("main") && methodName.toUpperCase().indexOf("TEST")>=0)){
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
