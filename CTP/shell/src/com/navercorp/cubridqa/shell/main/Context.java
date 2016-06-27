package com.navercorp.cubridqa.shell.main;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.Map.Entry;

import com.navercorp.cubridqa.shell.common.CommonUtils;

public class Context {

	Properties config;

	boolean isContinueMode = false;

	ArrayList<String> envList;

	String cubridPackageUrl;
	
	private boolean cleanTestCase = false; 
	
	private String filename;
	
	boolean isWindows = false;
	
	Feedback feedback;
	
	String build;
	
	String version;
	
	boolean isNewBuildNumberSystem = true;
	
	int total_scenario;
	
	String testCaseSkipKey;
	
	boolean isSaveCorefile = false;
	
	Date startDate;
	
	String bigSpaceDir;
	
	int maxRetryCount;
          
	String defaultDbCharset;

	boolean enableCheckDiskSpace = false;

	String mailNoticeTo;

	boolean enableSaveNormalErrorLog = false;


    
    String msgId;
    
    Map<String, String> envMap = null;

	public Context(String filename) throws IOException {
		this.filename = filename;
		this.startDate = new java.util.Date();
		this.envMap = new HashMap<String, String>();
		reload();
	}
	
	public void reload() throws IOException{
		this.config = CommonUtils.getPropertiesWithPriority(filename);
		this.envList = initEnvList(config);
		
		this.cleanTestCase = getProperty("main.testcase.clean", "false").equalsIgnoreCase("true");
		this.isWindows = getProperty("main.testing.platform", "linux").equalsIgnoreCase("windows");
		
		this.testCaseSkipKey = getProperty("main.testcase.skip_key", "").trim().toUpperCase();
		if(this.testCaseSkipKey.equals("")) {
			this.testCaseSkipKey = null;
		}
                 		
		this.isSaveCorefile = getProperty("main.testing.savecorefile", "FALSE").trim().toUpperCase().equals("TRUE");
		this.bigSpaceDir = getProperty("main.testing.bigspace_dir", "").trim();
		this.maxRetryCount = Integer.parseInt(getProperty("max.retry.count", "0").trim());
        this.defaultDbCharset = getProperty("main.testing.default_charset", "en_US").trim();
       
		this.enableCheckDiskSpace = getProperty("main.testing.enable_check_disk_space", "FALSE").trim().toUpperCase().equals("TRUE");
		this.mailNoticeTo = getProperty("main.testing.bigspace_dir", "fanzq@nhn.com").trim();
		this.enableSaveNormalErrorLog = getProperty("main.testing.do_save_normal_error_log", "FALSE").trim().toUpperCase().equals("TRUE");        
      
		this.isContinueMode = com.navercorp.cubridqa.common.CommonUtils.convertBoolean(getProperty("main.mode.continue", "false"), false);
		this.cubridPackageUrl = getProperty("main.testbuild.url");

		// to get msg id from environment variable
		putEnvVriableIntoMapByKey("MSG_ID");
      }

	public static ArrayList<String> initEnvList(Properties config) {
		ArrayList<String> resultList = new ArrayList<String>();
		Set<Object> set = config.keySet();
		Iterator<Object> it = set.iterator();
		String key;
		while (it.hasNext()) {
			key = (String) it.next();
			if (key.startsWith("env.") && key.endsWith(".ssh.host")) {
				resultList.add(key.substring(4, key.indexOf(".ssh.host")));
			}
		}
		return resultList;
	}

	
	public String getProperty(String key, String defaultValue) {
		return this.config.getProperty(key, defaultValue);
	}
	
	public ArrayList<String> getRelatedHosts(String envId) {
		String[] relates = getProperty("env." + envId + ".ssh.host.related", "").split(",");
		ArrayList<String> list = new ArrayList<String>();
		String h;
		for (int i = 0; i < relates.length; i++) {
			h = relates[i].trim();
			if (h.equals(""))
				continue;
			list.add(h);
		}
		return list;
	}
	
	public String getProperty(String key) {
		return getProperty(key, null);
	}

	public ArrayList<String> getEnvList() {
		return this.envList;
	}

	public boolean isContinueMode() {
		return isContinueMode;
	}

	public void setContinueMode(boolean isContinueMode) {
		this.isContinueMode = isContinueMode;
	}

	public String getTestCaseRoot() {
		return getProperty("main.testcase.root", "").trim();
	}
	
	public String getTestCaseBranch() {
		return getProperty("main.testcase.branch_git", "").trim();
	}
	
	public String getTestCaseWorkspace() {
		String ws = getProperty("main.testcase.workspace", "").trim();
		if (ws.equals("")) {
			return getTestCaseRoot();
		} else {
			return ws;
		}
	}

	public void setCubridPackageUrl(String cubridPackageUrl) {
		this.cubridPackageUrl = cubridPackageUrl;
	}

	public String getCubridPackageUrl() {
		return this.cubridPackageUrl;
	}
	
	public boolean getCleanTestCase(){
		return cleanTestCase;
	}
	
	public String getSVNUserInfo(){
		String user = getProperty("main.svn.user", "");
		String pwd = getProperty("main.svn.pwd", "");
		if (user.trim().equals("") || pwd.trim().equals(""))
			return "";
		return " --username " + user + " --password " + pwd + " --non-interactive "; 
	}
	
	public String getGitUserName(){
		return getProperty("main.git.user", "").trim();
	}
	
	public String getGitUserEmail(){
		return getProperty("main.git.email", "").trim();
	}
	
	public String getGitUserPwd(){
		return getProperty("main.git.pwd", "").trim();
	}
	
	public boolean isScenarioInGit(){
		String git = getProperty ("main.testcase.branch_git").trim();
		boolean isGit = git != null && git.length() > 0;
		return isGit;
	}
	
	private void putMKeyEnvVaribleIntoMap(){
		Map map = System.getenv();  
		Iterator it = map.entrySet().iterator();  
		while(it.hasNext())  
		{  
		    Entry entry = (Entry)it.next(); 
		    if(entry.getKey()!=null && entry.getKey().toString().indexOf("MKEY") != -1)
		    {
		    	envMap.put(entry.getKey().toString(), entry.getValue().toString());
		    }
		} 
		
	}
	
	private void putEnvVriableIntoMapByKey(String key)
	{
		if(key == null || key.length() <= 0)
			return ;
		
		String val = System.getenv(key);
		envMap.put(key, val);
		
	}
	
	public boolean isWindows () {
		 return this.isWindows;
	}
	
	public void setFeedback(Feedback feedback) {
		this.feedback = feedback;
	}
	
	public Feedback getFeedback() {
		return this.feedback;
	}
	
	public String getFeedbackDbUrl(){
		String host = getProperty("feedback.db.host", "");
		String port = getProperty("feedback.db.port", "");
		String dbname = getProperty("feedback.db.name", "");
		
		String url = "jdbc:cubrid:" + host + ":" + port + ":" + dbname + ":::";
		
		return url;
	}
	
	public String getFeedbackDbUser(){
		String user = getProperty("feedback.db.user", "");
		
		return user;
	}
	
	public String getFeedbackDbPwd(){
		String pwd = getProperty("feedback.db.pwd", "");
		
		return pwd;
	}
	
	public void setTestBuild(String build) {
		this.build = build;
	}

	public String getTestBuild() {
		return this.build;
	}
	
	public void setIsNewBuildNumberSystem(boolean isNewBuildNumberSystem) {
		this.isNewBuildNumberSystem = isNewBuildNumberSystem;
	}

	public boolean getIsNewBuildNumberSystem() {
		return this.isNewBuildNumberSystem;
	}
	
	public void setVersion(String version) {
		this.version = version;
	}

	public String getVersion() {
		return this.version;
	}
	
	public Properties getProperties(){
		return this.config;
	}
	
	public String getTestCaseSkipKey() {
		return this.testCaseSkipKey;
	}

	public boolean isSaveCorefile() {
		return this.isSaveCorefile;
	}
	
	public Date getStartDate() {
		return this.startDate;
	}
	
	public String getBigSpaceDir() {
		return bigSpaceDir;
	}
	
	public int getMaxRetryCount() {
		return maxRetryCount;
	}

	public void setMaxRetryCount(int maxRetryCount) {
		this.maxRetryCount = maxRetryCount;
	}

        public String getDefaultDbcharset() {
		return defaultDbCharset;
	}

	public String getMsgId() {
		return msgId;
	}

	public void setMsgId(String msgId) {
		this.msgId = msgId;
	}
	public boolean getEnableCheckDiskSpace() {
		return this.enableCheckDiskSpace;
	}
    
        public String getMailNoticeTo() {
		return mailNoticeTo;
	}
    
        public boolean getEnableSaveNormalErrorLog() {
		return enableSaveNormalErrorLog ;
	}

}
