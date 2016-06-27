package com.navercorp.cubridqa.shell.main;

import java.util.ArrayList;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.navercorp.cubridqa.shell.common.CommonUtils;
import com.navercorp.cubridqa.shell.common.Constants;
import com.navercorp.cubridqa.shell.common.Log;

public class Main {
	
	public static void exec(String configFilename) throws Exception {
		
		Properties system = System.getProperties();
		system.setProperty("sun.rmi.transport.connectionTimeout", "10000000");
		//system.setProperty("sun.rmi.transport.tcp.responseTimeout", "0");

		Context context = new Context(configFilename);

		String cubridPackageUrl = context.getCubridPackageUrl();
		
		System.out.println("Continue Mode: " + context.isContinueMode());
		System.out.println("Test Build: " + cubridPackageUrl);
		
		//get test build number
		String build = null;
		Pattern pattern = Pattern.compile("\\d+\\.\\d+\\.\\d+\\.\\d+");
		Matcher matcher = pattern.matcher(cubridPackageUrl);
		while (matcher.find()) {
			build = matcher.group();
		}

		if (CommonUtils.isNewBuildNumberSystem(build)) {
			String buildId;

			int p1 = cubridPackageUrl.lastIndexOf(build);
			int p2 = cubridPackageUrl.indexOf("-", p1 + build.length() + 1);

			if (p2 == -1) {
				p2 = cubridPackageUrl.indexOf(".", p1 + build.length() + 1);
			}

			buildId = p2 == -1 ? cubridPackageUrl.substring(p1) : cubridPackageUrl.substring(p1, p2);
			context.setTestBuild(buildId);
			context.setIsNewBuildNumberSystem(true);
		} else {
			context.setTestBuild(build);
			context.setIsNewBuildNumberSystem(false);
		}

		System.out.println("Build Number: " + context.getTestBuild());
		
		//get version (64bit or 32bit)
		String version = null;
		int idx1 = cubridPackageUrl.indexOf("_64");
		int idx2 = cubridPackageUrl.indexOf("x64");
		int idx3 = cubridPackageUrl.indexOf("ppc64"); //AIX BUILD. CUBRID-8.4.4.0136-AIX-ppc64.sh
		
		if (idx1 >= 0 || idx2 >= 0 || idx3 >=0){
			version = "64bits";
			System.out.println("Test Version: " + version);
		} else {
			version = "32bits";
			System.out.println("Test Version: " + version);
		}
		context.setVersion(version);
		
		ArrayList<String> envList = context.getEnvList();
		System.out.println("Available Env: " + envList);

		if (context.getEnvList().size() == 0) {
			throw new Exception("Not found any environment instance to test on it.");
		}

		Properties props = context.getProperties();
		Set set = props.keySet();
		Log contextSnapshot = new Log(CommonUtils.concatFile(Constants.DIR_CONF, "main_snapshot.properties"), true, false);
		for(Object key: set) {
			contextSnapshot.println(key + "=" + props.getProperty((String)key) );
		}
		contextSnapshot.println("AUTO_TEST_VERSION=" + context.getTestBuild());
		contextSnapshot.println("AUTO_TEST_BITS=" + context.getVersion() );
		contextSnapshot.close();

		TestFactory factory = new TestFactory(context);
		factory.execute();
	}
}
