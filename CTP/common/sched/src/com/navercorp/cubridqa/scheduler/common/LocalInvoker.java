/**
 * Copyright (c) 2016, Search Solution Corporation. All rights reserved.
 */
package com.navercorp.cubridqa.scheduler.common;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class LocalInvoker {
    
    /**
     * Execute a shell command and return the exit code
     */
    public int run(String command) throws Exception {
        Process process = Runtime.getRuntime().exec(new String[]{"sh", "-c", command});
        
        // Read output
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String line;
        while ((line = reader.readLine()) != null) {
            System.out.println(line);
        }
        reader.close();
        
        // Read error output
        reader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
        while ((line = reader.readLine()) != null) {
            System.out.println("ERROR: " + line);
        }
        reader.close();
        
        // Wait for process to complete
        return process.waitFor();
    }
}