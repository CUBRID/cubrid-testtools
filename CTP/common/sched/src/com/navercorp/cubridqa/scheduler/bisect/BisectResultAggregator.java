/**
 * Copyright (c) 2016, Search Solution Corporation. All rights reserved.
 */
package com.navercorp.cubridqa.scheduler.bisect;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

import com.navercorp.cubridqa.scheduler.common.Constants;
import com.navercorp.cubridqa.scheduler.common.HttpUtil;
import com.navercorp.cubridqa.scheduler.common.Log;

public class BisectResultAggregator {
    
    /**
     * Aggregates bisect results from a finished message and POSTs them back to the callback URL
     */
    public static void aggregateAndPost(javax.jms.Message message) {
        try {
            // Extract message properties
            String commitFormer = message.getStringProperty(Constants.MSG_COMMIT_FORMER);
            String commitLatter = message.getStringProperty(Constants.MSG_COMMIT_LATTER);
            String callbackUrl = message.getStringProperty(Constants.MSG_CALLBACK_URL);
            String workerIp = message.getStringProperty(Constants.MSG_WORKER_IP);
            
            if (callbackUrl == null || callbackUrl.isEmpty()) {
                // Not a JSON callback workflow message, skip
                return;
            }
            
            Log.print("Aggregating bisect results for " + commitFormer + " -> " + commitLatter);
            
            // Build JSON response
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
            Log.print("POSTing bisect results to " + callbackUrl);
            Log.print("JSON payload: " + jsonResponse);
            
            String response = HttpUtil.postJson(callbackUrl, jsonResponse);
            Log.print("Callback response: " + response);
            
        } catch (Exception e) {
            Log.print("Failed to aggregate and post bisect results");
            e.printStackTrace();
        }
    }
    
    private static String escapeJson(String input) {
        if (input == null) return "";
        return input.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r")
                   .replace("\t", "\\t");
    }
}