/**
 * Copyright (c) 2016, Search Solution Corporation. All rights reserved.
 */
package com.navercorp.cubridqa.scheduler.bisect;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

import com.navercorp.cubridqa.scheduler.common.Constants;
import com.navercorp.cubridqa.scheduler.common.HttpUtil;

import com.navercorp.cubridqa.scheduler.consumer.Configure;

public class BisectResultAggregator {
    
    private static Configure conf;
    
    public static void setConfiguration(Configure configuration) {
        conf = configuration;
    }
    
    /**
     * Aggregates bisect results from a finished message and writes them to file-based feedback
     */
    public static void aggregateAndPost(javax.jms.Message message) {
        try {
            // Extract message properties
            String commitFormer = message.getStringProperty(Constants.MSG_COMMIT_FORMER);
            String commitLatter = message.getStringProperty(Constants.MSG_COMMIT_LATTER);
            String buildType = message.getStringProperty(Constants.MSG_BUILD_TYPE);
            String callbackUrl = message.getStringProperty(Constants.MSG_CALLBACK_URL);
            String workerIp = message.getStringProperty(Constants.MSG_WORKER_IP);
            String originIp = message.getStringProperty(Constants.MSG_ORIGIN_IP);
            
            if (callbackUrl == null || callbackUrl.isEmpty()) {
                // Not a JSON callback workflow message, skip
                return;
            }
            
            System.out.println("Aggregating bisect results for " + commitFormer + " -> " + commitLatter);
            
            // Check if file-based feedback is configured
            String feedbackType = conf != null ? conf.getProperty("feedback_type", "database") : "database";
            
            if ("file".equals(feedbackType)) {
                handleFileFeedback(message, commitFormer, commitLatter, buildType, workerIp, originIp);
            } else {
                // Keep backward compatibility with HTTP POST
                handleHttpFeedback(message, commitFormer, commitLatter, workerIp, callbackUrl);
            }
            
        } catch (Exception e) {
            System.out.println("Failed to aggregate and post bisect results");
            e.printStackTrace();
        }
    }
    
    private static void handleFileFeedback(javax.jms.Message message, String commitFormer, 
                                         String commitLatter, String buildType, String workerIp, String originIp) {
        try {
            // Create unique main ID for this bisect session
            long mainId = System.currentTimeMillis();
            
            // Create result directory
            String resultDir = conf.getProperty("result_file_dir", "/tmp/cubrid-bisect-results");
            File dir = new File(resultDir);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            
            // Write main result file
            writeMainResultFile(dir, mainId, commitFormer, commitLatter, buildType, workerIp, originIp);
            
            // Write individual test result files
            writeTestResultFiles(dir, mainId, message);
            
            // Notify QAHome about the new bisect results
            notifyQAHome(mainId);
            
            System.out.println("File-based feedback completed for main_id: " + mainId);
            
        } catch (Exception e) {
            System.out.println("Failed to handle file feedback");
            e.printStackTrace();
        }
    }
    
    private static void writeMainResultFile(File dir, long mainId, String commitFormer, 
                                          String commitLatter, String buildType, String workerIp, String originIp) throws IOException {
        File mainFile = new File(dir, "bisect_main_" + mainId + ".dat");
        
        FileWriter writer = null;
        try {
            writer = new FileWriter(mainFile);
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            String timestamp = sdf.format(new Date());
            
            writer.write("MAIN_ID=" + mainId + "\n");
            writer.write("COMMIT_FORMER=" + commitFormer + "\n");
            writer.write("COMMIT_LATTER=" + commitLatter + "\n");
            writer.write("BUILD_TYPE=" + (buildType != null ? buildType : "debug") + "\n");
            writer.write("WORKER_IP=" + (workerIp != null ? workerIp : "") + "\n");
            writer.write("ORIGIN_IP=" + (originIp != null ? originIp : "") + "\n");
            writer.write("START_TIME=" + timestamp + "\n");
            writer.write("END_TIME=" + timestamp + "\n");
            writer.write("CATEGORY=bisect\n");
            writer.write("TEST_TYPE=shell\n");
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        
        System.out.println("Written main result file: " + mainFile.getAbsolutePath());
    }
    
    private static void writeTestResultFiles(File dir, long mainId, javax.jms.Message message) throws Exception {
        int resultCount = message.getIntProperty("BISECT_RESULT_COUNT");
        
        for (int i = 0; i < resultCount; i++) {
            String prefix = "BISECT_RESULT_" + i + "_";
            
            String name = message.getStringProperty(prefix + "NAME");
            String status = message.getStringProperty(prefix + "STATUS");
            String runtime = message.getStringProperty(prefix + "RUNTIME_MS");
            String firstBadCommit = message.getStringProperty(prefix + "FIRST_BAD_COMMIT");
            String author = message.getStringProperty(prefix + "AUTHOR");
            String error = message.getStringProperty(prefix + "ERROR");
            
            File testFile = new File(dir, "bisect_result_" + mainId + "_" + i + ".dat");
            
            FileWriter writer = null;
            try {
                writer = new FileWriter(testFile);
                writer.write("MAIN_ID=" + mainId + "\n");
                writer.write("TEST_NAME=" + name + "\n");
                writer.write("STATUS=" + status + "\n");
                writer.write("RUNTIME_MS=" + runtime + "\n");
                
                if ("found".equals(status) && firstBadCommit != null) {
                    writer.write("FIRST_BAD_COMMIT=" + firstBadCommit + "\n");
                    if (author != null) {
                        writer.write("COMMIT_AUTHOR=" + escapeString(author) + "\n");
                    }
                } else if ("error".equals(status) && error != null) {
                    writer.write("ERROR_MESSAGE=" + escapeString(error) + "\n");
                }
            } finally {
                if (writer != null) {
                    try {
                        writer.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
            
            System.out.println("Written test result file: " + testFile.getAbsolutePath());
        }
    }
    
    private static void notifyQAHome(long mainId) {
        try {
            String notificationUrl = conf.getProperty("feedback_notice_qahome_url", "");
            if (!notificationUrl.isEmpty()) {
                // Replace <MAINID> placeholder with actual main ID
                String finalUrl = notificationUrl.replace("<MAINID>", String.valueOf(mainId));
                
                System.out.println("Notifying QAHome: " + finalUrl);
                String response = HttpUtil.getHtmlSource(finalUrl);
                System.out.println("QAHome notification response: " + response);
            }
        } catch (Exception e) {
            System.out.println("Failed to notify QAHome: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void handleHttpFeedback(javax.jms.Message message, String commitFormer, 
                                         String commitLatter, String workerIp, String callbackUrl) throws Exception {
        // Keep original HTTP POST implementation for backward compatibility
        StringBuilder jsonBuilder = new StringBuilder();
        jsonBuilder.append("{\n");
        jsonBuilder.append("  \"commitFormer\": \"").append(commitFormer).append("\",\n");
        jsonBuilder.append("  \"commitLatter\": \"").append(commitLatter).append("\",\n");
        jsonBuilder.append("  \"workerIp\": \"").append(workerIp != null ? workerIp : "").append("\",\n");
        
        // Add timestamp
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        jsonBuilder.append("  \"generatedAt\": \"").append(sdf.format(new Date())).append("\",\n");
        
        // Add bisect results
        jsonBuilder.append("  \"tests\": [\n");
        
        int resultCount = message.getIntProperty("BISECT_RESULT_COUNT");
        for (int i = 0; i < resultCount; i++) {
            String prefix = "BISECT_RESULT_" + i + "_";
            
            String name = message.getStringProperty(prefix + "NAME");
            String status = message.getStringProperty(prefix + "STATUS");
            String runtime = message.getStringProperty(prefix + "RUNTIME_MS");
            String firstBadCommit = message.getStringProperty(prefix + "FIRST_BAD_COMMIT");
            String author = message.getStringProperty(prefix + "AUTHOR");
            String error = message.getStringProperty(prefix + "ERROR");
            
            jsonBuilder.append("    {\n");
            jsonBuilder.append("      \"name\": \"").append(name).append("\",\n");
            jsonBuilder.append("      \"status\": \"").append(status).append("\",\n");
            
            if (firstBadCommit != null) {
                jsonBuilder.append("      \"firstBadCommit\": \"").append(firstBadCommit).append("\",\n");
            }
            if (author != null) {
                jsonBuilder.append("      \"author\": \"").append(escapeJson(author)).append("\",\n");
            }
            if (error != null) {
                jsonBuilder.append("      \"error\": \"").append(escapeJson(error)).append("\",\n");
            }
            
            jsonBuilder.append("      \"runtimeMs\": ").append(runtime).append("\n");
            jsonBuilder.append("    }");
            
            if (i < resultCount - 1) {
                jsonBuilder.append(",");
            }
            jsonBuilder.append("\n");
        }
        
        jsonBuilder.append("  ]\n");
        jsonBuilder.append("}");
        
        String jsonResponse = jsonBuilder.toString();
        
        // POST the JSON response back to callback URL
        System.out.println("POSTing bisect results to " + callbackUrl);
        String response = HttpUtil.postJson(callbackUrl, jsonResponse);
        System.out.println("Callback response: " + response);
    }
    
    private static String escapeJson(String input) {
        if (input == null) return "";
        return input.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r")
                   .replace("\t", "\\t");
    }
    
    private static String escapeString(String input) {
        if (input == null) return "";
        return input.replace("\n", "\\n")
                   .replace("\r", "\\r")
                   .replace("\t", "\\t");
    }
}