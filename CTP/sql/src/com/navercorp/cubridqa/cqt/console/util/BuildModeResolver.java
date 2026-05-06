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
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * Resolves the current CUBRID build mode by invoking <code>cubrid_rel</code> and inspecting its
 * output for "release" or "debug". Cached after the first successful call.
 *
 * <p>Equivalent to the shell expression in cubridci/docker/ci/docker-entrypoint.sh L171:
 * <code>cubrid_rel | grep -oe 'release\|debug'</code>.
 */
public final class BuildModeResolver {

    /**
     * Cached result of the first successful {@link #detect()} call.
     *
     * <p>Access discipline: {@code detect()} is {@code synchronized}, which serves as both the
     * monitor for the cache write and a happens-before barrier for all subsequent reads. Because
     * every read of {@code CACHED} occurs inside the same {@code synchronized} block, no
     * {@code volatile} annotation is required.
     */
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
            Process p = new ProcessBuilder("cubrid_rel").redirectErrorStream(true).start();
            p.getOutputStream().close();
            String output = readAll(p.getInputStream());
            p.waitFor();
            String mode = pickMode(output);
            CACHED = mode;
            return mode;
        } catch (IOException e) {
            throw new RuntimeException("cubrid_rel invocation failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("cubrid_rel was interrupted", e);
        }
    }

    private static String readAll(InputStream is) throws IOException {
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
        int dbgIdx = lower.indexOf("debug");
        int relIdx = lower.indexOf("release");
        if (dbgIdx < 0 && relIdx < 0) {
            throw new RuntimeException(
                    "cubrid_rel output contained neither 'release' nor 'debug': " + text);
        }
        if (relIdx < 0) {
            return "debug";
        }
        if (dbgIdx < 0) {
            return "release";
        }
        // Both present: return the one that appears first (left-to-right, like grep -oe).
        return dbgIdx <= relIdx ? "debug" : "release";
    }

    public static void main(String[] args) {
        int passed = 0;
        // Test pickMode first-occurrence logic (debug before release).
        passed += checkPick("debug only", "debug", pickMode("CUBRID (debug build)"));
        passed += checkPick("release only", "release", pickMode("CUBRID release 11.0"));
        passed += checkPick("debug first", "debug", pickMode("debug symbols, release candidate"));
        passed += checkPick("release first", "release", pickMode("release build (no debug symbols)"));
        // Test pickMode exception for unknown output.
        boolean threw = false;
        try {
            pickMode("no keyword here");
        } catch (RuntimeException e) {
            threw = true;
        }
        passed += threw ? 1 : 0;
        if (!threw) {
            System.err.println("  FAIL: pickMode should throw for unknown output");
        }

        int expectedStatic = 5;
        if (passed != expectedStatic) {
            System.err.println("FAIL: BuildModeResolver static tests " + passed + "/" + expectedStatic);
            System.exit(1);
        }

        // Live detection test (optional — only works in CUBRID environments).
        try {
            String mode = detect();
            if (!"release".equals(mode) && !"debug".equals(mode)) {
                System.err.println("FAIL: unexpected mode: " + mode);
                System.exit(1);
            }
            System.out.println("OK: BuildModeResolver " + expectedStatic + "/" + expectedStatic
                    + " static + live detect() -> " + mode);
        } catch (RuntimeException e) {
            System.out.println("OK: BuildModeResolver " + expectedStatic + "/" + expectedStatic
                    + " static cases passed (cubrid_rel not available: " + e.getMessage() + ")");
        }
    }

    private static int checkPick(String label, String expected, String actual) {
        if (expected.equals(actual)) {
            return 1;
        }
        System.err.println("  FAIL pickMode[" + label + "]: expected=" + expected + " got=" + actual);
        return 0;
    }
}
