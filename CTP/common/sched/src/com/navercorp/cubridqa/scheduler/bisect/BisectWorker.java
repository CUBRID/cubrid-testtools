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
import com.navercorp.cubridqa.scheduler.common.Log;
import com.navercorp.cubridqa.scheduler.consumer.Configure;
import com.navercorp.cubridqa.scheduler.consumer.ResultAggregator;

public class BisectWorker implements MessageListener {
    
    private Configure conf;
    private String cubridSrcDir;
    private String shellTcDir;
    private String cubridBuildArg;
    private String cubridBuildDir;
    
    public BisectWorker(Configure conf) {
        this.conf = conf;
        this.cubridSrcDir = conf.getProperty("cubrid.src.dir", "/home/cubrid/cubrid");
        this.shellTcDir = conf.getProperty("shell.tc.dir", "/home/cubrid/cubrid-testcases-private-ex");
        this.cubridBuildArg = conf.getProperty("cubrid.build.arg", "-g ninja -m debug build");
        this.cubridBuildDir = conf.getProperty("cubrid.build.dir", "build_x86_64_debug");
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
                String testList = message.getStringProperty(Constants.MSG_TEST_LIST);
                String callbackUrl = message.getStringProperty(Constants.MSG_CALLBACK_URL);
                
                Log.print("Processing bisect request: " + commitFormer + " -> " + commitLatter);
                
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
            Log.print("Error processing bisect request: " + e.getMessage());
            e.printStackTrace();
        }
    }    
    private List<BisectResult> runBisectForTests(String commitFormer, String commitLatter, 
                                                  String buildType, String testList) {
        List<BisectResult> results = new ArrayList<>();
        
        if (testList == null || testList.isEmpty()) {
            return results;
        }
        
        String[] tests = testList.split(",");
        for (String test : tests) {
            test = test.trim();
            Log.print("Starting bisect for test: " + test);
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
                Log.print("BISECT: " + line);
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
            Log.print("Error during bisect: " + e.getMessage());
            e.printStackTrace();
        }
        
        long endTime = System.currentTimeMillis();
        result.runtimeMs = endTime - startTime;
        
        return result;
    }    
    private File createJudgeScript(String testPath, String buildType) throws Exception {
        // Extract test information
        String tcDir = shellTcDir + "/" + testPath.substring(0, testPath.lastIndexOf("/"));
        String tcScript = testPath.substring(testPath.lastIndexOf("/") + 1);
        String tcName = tcScript.replace(".sh", "");
        String tcResult = tcName + ".result";
        
        // Create judge script
        File judgeScript = File.createTempFile("judge_", ".sh");
        FileWriter writer = new FileWriter(judgeScript);
        
        writer.write("#!/bin/bash\n");
        writer.write("set -e  # exit immediately on error\n");
        writer.write("cd " + cubridSrcDir + "\n");
        writer.write("git submodule foreach git reset --hard HEAD\n");
        writer.write("git submodule update\n");
        writer.write("rm -rf cubridmanager/*  # temporary: currently, cubridmanager fails compilation on Rocky 8\n");
        writer.write("rm -rf " + cubridBuildDir + "  # for clean rebuild\n");
        writer.write("./build.sh " + cubridBuildArg + "\n");
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