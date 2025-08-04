/**
 * Copyright (c) 2016, Search Solution Corporation. All rights reserved.
 */
package com.navercorp.cubridqa.scheduler.bisect;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.jms.JMSException;
import javax.jms.MessageListener;
import javax.jms.TextMessage;

import com.navercorp.cubridqa.scheduler.common.Constants;

import com.navercorp.cubridqa.scheduler.consumer.Configure;


public class BisectWorker implements MessageListener {
    
    private Configure conf;
    private String cubridSrcDir;
    private String shellTcDir;
    
    public BisectWorker(Configure conf) {
        this.conf = conf;
        this.cubridSrcDir = conf.getProperty("cubrid.src.dir", "/home/cubrid/cubrid");
        this.shellTcDir = conf.getProperty("shell.tc.dir", "/home/cubrid/cubrid-testcases-private-ex");
        
        // Set configuration for result aggregator
        BisectResultAggregator.setConfiguration(conf);
    }    
    @Override
    public void onMessage(javax.jms.Message message) {
        try {
            if (message instanceof TextMessage) {
                TextMessage textMessage = (TextMessage) message;
                String messageText = textMessage.getText();
                
                // Check if this is a JSON bisect request
                if (!"JSON_BISECT_REQUEST".equals(messageText)) {
                    return;
                }
                
                // Extract properties from message
                String commitFormer = message.getStringProperty(Constants.MSG_COMMIT_FORMER);
                String commitLatter = message.getStringProperty(Constants.MSG_COMMIT_LATTER);
                String buildType = message.getStringProperty(Constants.MSG_BUILD_TYPE);
                String requestedWorkerIp = message.getStringProperty(Constants.MSG_WORKER_IP);
                String testList = message.getStringProperty(Constants.MSG_TEST_LIST);
                // String callbackUrl = message.getStringProperty(Constants.MSG_CALLBACK_URL); // Not used in bisect workflow
                
                // Check if this worker should handle the request (if workerIp is specified)
                if (requestedWorkerIp != null && !requestedWorkerIp.isEmpty()) {
                    String actualWorkerIp = InetAddress.getLocalHost().getHostAddress();
                    if (!requestedWorkerIp.equals(actualWorkerIp)) {
                        System.out.println("Request is for worker " + requestedWorkerIp + ", but this is " + actualWorkerIp + ". Skipping.");
                        return;
                    }
                }
                
                System.out.println("Processing bisect request: " + commitFormer + " -> " + commitLatter);
                
                // Run bisect for each test
                List<BisectResult> results = runBisectForTests(commitFormer, commitLatter, buildType, testList);
                
                // Add results to message for aggregation
                addResultsToMessage(message, results);
                
                // Add worker IP
                String workerIp = InetAddress.getLocalHost().getHostAddress();
                message.setStringProperty(Constants.MSG_WORKER_IP, workerIp);
                
                // Call ResultAggregator to POST results back
                BisectResultAggregator.aggregateAndPost(message);
            }
        } catch (Exception e) {
            System.out.println("Error processing bisect request: " + e.getMessage());
            e.printStackTrace();
        }
    }    
    private List<BisectResult> runBisectForTests(String commitFormer, String commitLatter, 
                                                  String buildType, String testList) {
        List<BisectResult> results = new ArrayList<BisectResult>();
        
        if (testList == null || testList.isEmpty()) {
            return results;
        }
        
        String[] tests = testList.split(",");
        for (String test : tests) {
            test = test.trim();
            System.out.println("Starting bisect for test: " + test);
            BisectResult result = runBisectForSingleTest(commitFormer, commitLatter, buildType, test);
            results.add(result);
        }
        
        return results;
    }
    
    private BisectResult runBisectForSingleTest(String commitFormer, String commitLatter, 
                                                String buildType, String testPath) {
        BisectResult result = new BisectResult();
        result.name = testPath;
        long startTime = System.currentTimeMillis();
        
        try {
            // Create judge script for this test
            File judgeScript = createJudgeScript(testPath, buildType);
            
            // Reset any previous bisect
            resetBisect();
            
            // Start bisect
            ProcessBuilder pb = new ProcessBuilder("git", "bisect", "start", commitLatter, commitFormer);
            pb.directory(new File(cubridSrcDir));
            Process p = pb.start();
            p.waitFor();
            
            // Run bisect
            pb = new ProcessBuilder("git", "bisect", "run", judgeScript.getAbsolutePath());
            pb.directory(new File(cubridSrcDir));
            p = pb.start();
                        
            // Read bisect output
            StringBuilder output = new StringBuilder();
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
                System.out.println("BISECT: " + line);
            }
            reader.close();
            p.waitFor();
            
            // Parse the result
            String bisectOutput = output.toString();
            if (bisectOutput.contains("is the first bad commit")) {
                result.status = "found";
                
                // Extract commit hash and author
                Pattern commitPattern = Pattern.compile("([0-9a-f]{40}) is the first bad commit");
                Matcher commitMatcher = commitPattern.matcher(bisectOutput);
                if (commitMatcher.find()) {
                    result.firstBadCommit = commitMatcher.group(1);
                }
                
                // Get commit author
                if (result.firstBadCommit != null) {
                    pb = new ProcessBuilder("git", "log", "-1", "--format=%an <%ae>", result.firstBadCommit);
                    pb.directory(new File(cubridSrcDir));
                    p = pb.start();
                    reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
                    result.author = reader.readLine();
                    reader.close();
                    p.waitFor();
                }
            } else {
                result.status = "error";
                result.errorMessage = "No bad commit found";
            }
            
            // Clean up
            resetBisect();
            judgeScript.delete();
            
        } catch (Exception e) {
            result.status = "error";
            result.errorMessage = e.getMessage();
            System.out.println("Error during bisect: " + e.getMessage());
            e.printStackTrace();
        }
        
        long endTime = System.currentTimeMillis();
        result.runtimeMs = endTime - startTime;
        
        return result;
    }
    
    private String getBuildArguments(String buildType) {
        // Get base build arguments from config, but override the mode based on buildType
        String baseBuildArg = conf.getProperty("cubrid.build.arg", "-g ninja -m debug build");
        
        if ("release".equalsIgnoreCase(buildType)) {
            // Replace debug with release mode
            return baseBuildArg.replaceAll("-m\\s+debug", "-m release");
        } else {
            // Default to debug mode  
            return baseBuildArg.replaceAll("-m\\s+release", "-m debug");
        }
    }
    
    private String getBuildDirectory(String buildType) {
        if ("release".equalsIgnoreCase(buildType)) {
            return conf.getProperty("cubrid.build.dir.release", "build_x86_64_release");
        } else {
            return conf.getProperty("cubrid.build.dir.debug", "build_x86_64_debug");
        }
    }
    
    private File createJudgeScript(String testPath, String buildType) throws Exception {
        // Extract test information
        String tcDir = shellTcDir + "/" + testPath.substring(0, testPath.lastIndexOf("/"));
        String tcScript = testPath.substring(testPath.lastIndexOf("/") + 1);
        String tcName = tcScript.replace(".sh", "");
        String tcResult = tcName + ".result";
        
        // Get build configuration based on buildType
        String buildArguments = getBuildArguments(buildType);
        String buildDirectory = getBuildDirectory(buildType);
        
        // Create judge script
        File judgeScript = File.createTempFile("judge_", ".sh");
        FileWriter writer = new FileWriter(judgeScript);
        
        writer.write("#!/bin/bash\n");
        writer.write("set -e  # exit immediately on error\n");
        writer.write("cd " + cubridSrcDir + "\n");
        writer.write("git submodule foreach git reset --hard HEAD\n");
        writer.write("git submodule update\n");
        writer.write("rm -rf cubridmanager/*  # temporary: currently, cubridmanager fails compilation on Rocky 8\n");
        writer.write("rm -rf " + buildDirectory + "  # for clean rebuild\n");
        writer.write("./build.sh " + buildArguments + "\n");
        writer.write("cd " + tcDir + "\n");
        writer.write("rm -f " + tcResult + "  # if any\n");
        writer.write("sh " + tcScript + " || true\n");
        writer.write("if [ ! -f " + tcResult + " ]; then\n");
        writer.write("    echo '- error: no result'\n");
        writer.write("    exit 128  # quit the bisect\n");
        writer.write("fi\n");
        writer.write("if grep -qw NOK " + tcResult + "; then\n");
        writer.write("    exit 1  # not OK\n");
        writer.write("else\n");
        writer.write("    exit 0  # OK\n");
        writer.write("fi\n");
        
        writer.close();
        judgeScript.setExecutable(true);
        
        return judgeScript;
    }    
    private void resetBisect() throws Exception {
        ProcessBuilder pb = new ProcessBuilder("git", "bisect", "reset");
        pb.directory(new File(cubridSrcDir));
        Process p = pb.start();
        p.waitFor();
        
        pb = new ProcessBuilder("git", "submodule", "foreach", "git", "reset", "--hard", "HEAD");
        pb.directory(new File(cubridSrcDir));
        p = pb.start();
        p.waitFor();
        
        pb = new ProcessBuilder("git", "submodule", "update");
        pb.directory(new File(cubridSrcDir));
        p = pb.start();
        p.waitFor();
    }
    
    private void addResultsToMessage(javax.jms.Message message, List<BisectResult> results) throws JMSException {
        for (int i = 0; i < results.size(); i++) {
            BisectResult result = results.get(i);
            String prefix = "BISECT_RESULT_" + i + "_";
            
            message.setStringProperty(prefix + "NAME", result.name);
            message.setStringProperty(prefix + "STATUS", result.status);
            message.setStringProperty(prefix + "RUNTIME_MS", String.valueOf(result.runtimeMs));
            
            if (result.firstBadCommit != null) {
                message.setStringProperty(prefix + "FIRST_BAD_COMMIT", result.firstBadCommit);
            }
            if (result.author != null) {
                message.setStringProperty(prefix + "AUTHOR", result.author);
            }
            if (result.errorMessage != null) {
                message.setStringProperty(prefix + "ERROR", result.errorMessage);
            }
        }
        message.setIntProperty("BISECT_RESULT_COUNT", results.size());
    }
    
    private static class BisectResult {
        String name;
        String status;
        String firstBadCommit;
        String author;
        String errorMessage;
        long runtimeMs;
    }
}