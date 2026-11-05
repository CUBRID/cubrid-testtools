/**
 * Copyright (c) 2016, Search Solution Corporation. All rights reserved.
 *
 * <p>Redistribution and use in source and binary forms, with or without modification, are permitted
 * provided that the following conditions are met:
 *
 * <p>* Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * <p>* Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided with
 * the distribution.
 *
 * <p>* Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written permission.
 *
 * <p>THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY
 * WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.navercorp.cubridqa.common.junit;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.LinkedHashMap;
import java.util.Map;

public final class JunitSuite {

    final String name;
    final long startMillis;
    final String hostname;
    long millis;
    final Map<String, String> properties = new LinkedHashMap<String, String>();

    public JunitSuite(String name, long startMillis) {
        this.name = name;
        this.startMillis = startMillis;
        this.hostname = localHostname();
    }

    public void setMillis(long millis) {
        this.millis = millis;
    }

    public void addProperty(String name, String value) {
        if (value != null && value.trim().length() > 0) {
            properties.put(name, value.trim());
        }
    }

    // No InetAddress.getLocalHost(): it asks DNS, and stalls when the name does not resolve.
    private static String localHostname() {
        String name = System.getenv("HOSTNAME");
        if (isEmpty(name)) {
            name = firstLine("/proc/sys/kernel/hostname");
        }
        if (isEmpty(name)) {
            name = System.getenv("COMPUTERNAME");
        }
        // the windyroad JUnit schema asks for "localhost" when the name cannot be found
        return isEmpty(name) ? "localhost" : name.trim();
    }

    private static String firstLine(String path) {
        try {
            BufferedReader r = new BufferedReader(new FileReader(path));
            try {
                return r.readLine();
            } finally {
                r.close();
            }
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean isEmpty(String s) {
        return s == null || s.trim().length() == 0;
    }
}
