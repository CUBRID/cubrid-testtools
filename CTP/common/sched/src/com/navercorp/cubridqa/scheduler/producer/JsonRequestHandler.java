/**
 * Copyright (c) 2016, Search Solution Corporation. All rights reserved.
 */
package com.navercorp.cubridqa.scheduler.producer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;


import com.navercorp.cubridqa.scheduler.common.Constants;

import com.navercorp.cubridqa.scheduler.common.Message;
import com.navercorp.cubridqa.scheduler.common.SendMessage;

public class JsonRequestHandler extends Thread {
    
    private Configure conf;
    private int port;
    private ServerSocket serverSocket;
    private boolean running = true;
    
    public JsonRequestHandler(Configure conf, int port) {
        this.conf = conf;
        this.port = port;
    }
    
    public void stopServer() {
        running = false;
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    @Override
    public void run() {
        try {
            serverSocket = new ServerSocket(port);
            System.out.println("JSON Request Handler listening on port " + port);
            
            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    handleRequest(clientSocket);
                } catch (IOException e) {
                    if (running) {
                        e.printStackTrace();
                    }
                }
            }
        } catch (IOException e) {
            System.out.println("Failed to start JSON Request Handler on port " + port);
            e.printStackTrace();
        }
    }    
    private void handleRequest(Socket clientSocket) {
        BufferedReader reader = null;
        OutputStreamWriter writer = null;
        try {
            reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            writer = new OutputStreamWriter(clientSocket.getOutputStream());
            
            // StringBuilder requestBuilder = new StringBuilder(); // Not used
            String line;
            int contentLength = 0;
            
            // Read headers
            while ((line = reader.readLine()) != null && !line.isEmpty()) {
                if (line.startsWith("Content-Length:")) {
                    contentLength = Integer.parseInt(line.substring(15).trim());
                }
            }
            
            // Read JSON body
            if (contentLength > 0) {
                char[] buffer = new char[contentLength];
                reader.read(buffer, 0, contentLength);
                String jsonBody = new String(buffer);
                
                // Parse JSON manually (simple parser for our specific format)
                QAHomeRequest request = parseJson(jsonBody);
                
                if (request != null) {
                    System.out.println("Received QAHome JSON request for commits: " + request.commitFormer + " -> " + request.commitLatter);
                    
                    // Create and send message to build.request queue
                    createAndSendMessage(request);
                    
                    // Send HTTP response
                    String response = "HTTP/1.1 200 OK\r\n" +
                                    "Content-Type: text/plain\r\n" +
                                    "Content-Length: 2\r\n" +
                                    "\r\n" +
                                    "OK";
                    writer.write(response);
                    writer.flush();
                } else {
                    // Send error response
                    String response = "HTTP/1.1 400 Bad Request\r\n" +
                                    "Content-Type: text/plain\r\n" +
                                    "Content-Length: 12\r\n" +
                                    "\r\n" +
                                    "Invalid JSON";
                    writer.write(response);
                    writer.flush();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            try {
                clientSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }    
    private QAHomeRequest parseJson(String json) {
        try {
            QAHomeRequest request = new QAHomeRequest();
            
            // Simple JSON parser for our specific format
            json = json.trim();
            if (json.startsWith("{") && json.endsWith("}")) {
                json = json.substring(1, json.length() - 1);
                
                String[] pairs = json.split(",(?=\\s*\"[^\"]+\"\\s*:)");
                for (String pair : pairs) {
                    String[] keyValue = pair.split(":", 2);
                    if (keyValue.length == 2) {
                        String key = keyValue[0].trim().replaceAll("\"", "");
                        String value = keyValue[1].trim();
                        
                        if (value.startsWith("\"") && value.endsWith("\"")) {
                            value = value.substring(1, value.length() - 1);
                        }
                        
                        switch (key) {
                            case "commitHash":
                                request.commitHash = value;
                                break;
                            case "commitFormer":
                                request.commitFormer = value;
                                break;
                            case "commitLatter":
                                request.commitLatter = value;
                                break;
                            case "buildType":
                                request.buildType = value;
                                break;
                            case "workerIp":
                                request.workerIp = value;
                                break;
                            case "callbackUrl":
                                request.callbackUrl = value;
                                break;
                            case "originIp":
                                request.originIp = value;
                                break;
                            case "tests":
                                // Parse array - simple implementation for ["test1", "test2"]
                                if (value.startsWith("[") && value.endsWith("]")) {
                                    value = value.substring(1, value.length() - 1);
                                    String[] tests = value.split(",");
                                    request.tests = new String[tests.length];
                                    for (int i = 0; i < tests.length; i++) {
                                        request.tests[i] = tests[i].trim().replaceAll("\"", "");
                                    }
                                }
                                break;
                        }
                    }
                }
            }
            
            return request.isValid() ? request : null;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }    
    private void createAndSendMessage(QAHomeRequest request) {
        try {
            // Create message for bisect.request queue
            Message msg = new Message("bisect.request", "JSON_BISECT_REQUEST");
            
            // Set message properties
            msg.setProperty(Constants.MSG_MSGID, msg.getMsgId());
            msg.setProperty(Constants.MSG_COMMIT_FORMER, request.commitFormer);
            msg.setProperty(Constants.MSG_COMMIT_LATTER, request.commitLatter);
            msg.setProperty(Constants.MSG_BUILD_TYPE, request.buildType);
            msg.setProperty(Constants.MSG_WORKER_IP, request.workerIp != null ? request.workerIp : "");
            msg.setProperty(Constants.MSG_CALLBACK_URL, request.callbackUrl);
            msg.setProperty(Constants.MSG_ORIGIN_IP, request.originIp);
            
            // Convert tests array to comma-separated string
            if (request.tests != null && request.tests.length > 0) {
                msg.setProperty(Constants.MSG_TEST_LIST, String.join(",", request.tests));
            }
            
            // Send message
            msg.setPriority(4); // Priority 4
            SendMessage sendMsg = new SendMessage(conf.getProperties());
            sendMsg.addMessage(msg);
            sendMsg.send();
            
        } catch (Exception e) {
            System.out.println("Failed to send message to bisect.request queue");
            e.printStackTrace();
        }
    }
    
    // Inner class for request data
    private static class QAHomeRequest {
        String commitHash;
        String commitFormer;
        String commitLatter;
        String buildType = "debug"; // default
        String workerIp;
        String[] tests;
        String callbackUrl;
        String originIp;
        
        boolean isValid() {
            // For bisect workflow
            if (commitFormer != null && commitLatter != null) {
                return !commitFormer.isEmpty() && !commitLatter.isEmpty() &&
                       workerIp != null && !workerIp.isEmpty() &&
                       callbackUrl != null && !callbackUrl.isEmpty() &&
                       tests != null && tests.length > 0;
            }
            // For regular workflow (backward compatibility)
            return commitHash != null && !commitHash.isEmpty() &&
                   callbackUrl != null && !callbackUrl.isEmpty() &&
                   tests != null && tests.length > 0;
        }
    }
}