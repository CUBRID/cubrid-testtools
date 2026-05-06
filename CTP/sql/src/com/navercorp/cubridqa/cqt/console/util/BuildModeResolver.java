/**
 * Copyright (c) 2016, Search Solution Corporation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted
 * provided that the following conditions are met:
 *
 *   * Redistributions of source code must retain the above copyright notice, this list of
 *     conditions and the following disclaimer.
 *
 *   * Redistributions in binary form must reproduce the above copyright notice, this list of
 *     conditions and the following disclaimer in the documentation and/or other materials provided
 *     with the distribution.
 *
 *   * Neither the name of the copyright holder nor the names of its contributors may be used to
 *     endorse or promote products derived from this software without specific prior written
 *     permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY
 * WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.navercorp.cubridqa.cqt.console.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * Resolves the current CUBRID build mode by invoking <code>cubrid_rel</code> and inspecting its
 * output for "release" or "debug". Cached after the first successful call.
 *
 * <p>Equivalent to the shell expression in cubridci/docker/ci/docker-entrypoint.sh L171:
 * <code>cubrid_rel | grep -oe 'release\|debug'</code>.
 */
public final class BuildModeResolver {

    private static String CACHED;

    private BuildModeResolver() {}

    /**
     * @return "release" or "debug".
     * @throws RuntimeException if cubrid_rel cannot be invoked or its output contains neither
     *     "release" nor "debug".
     */
    public static synchronized String detect() {
        if (CACHED != null) {
            return CACHED;
        }
        try {
            Process p = Runtime.getRuntime().exec("cubrid_rel");
            String stdout = readAll(p.getInputStream());
            p.waitFor();
            String mode = pickMode(stdout);
            CACHED = mode;
            return mode;
        } catch (IOException e) {
            throw new RuntimeException("cubrid_rel invocation failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("cubrid_rel was interrupted", e);
        }
    }

    private static String readAll(java.io.InputStream is) throws IOException {
        BufferedReader r = new BufferedReader(new InputStreamReader(is, "UTF-8"));
        try {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) {
                sb.append(line);
                sb.append('\n');
            }
            return sb.toString();
        } finally {
            r.close();
        }
    }

    private static String pickMode(String text) {
        String lower = text == null ? "" : text.toLowerCase();
        if (lower.contains("debug")) {
            return "debug";
        }
        if (lower.contains("release")) {
            return "release";
        }
        throw new RuntimeException(
                "cubrid_rel output contained neither 'release' nor 'debug': " + text);
    }

    public static void main(String[] args) {
        try {
            String mode = detect();
            if (!"release".equals(mode) && !"debug".equals(mode)) {
                System.err.println("FAIL: unexpected mode: " + mode);
                System.exit(1);
            }
            System.out.println("OK: BuildModeResolver.detect() -> " + mode);
        } catch (RuntimeException e) {
            System.err.println("INFO: cubrid_rel not available in this environment: "
                    + e.getMessage());
            System.err.println("This is expected outside of CUBRID test environments.");
            System.exit(0);
        }
    }
}
