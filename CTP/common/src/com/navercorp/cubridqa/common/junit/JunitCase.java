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

import java.util.ArrayList;
import java.util.List;

/** One test case of a JUnit XML report: its outcome and the failed runs before the last one. */
public final class JunitCase {

    public enum Result {
        PASSED,
        FAILURE,
        ERROR,
        SKIPPED
    }

    final String classname;
    final String name;
    final String file;
    final long millis;
    final Result result;
    final String message;
    final String type;
    final String body;
    final List<JunitCase> reruns = new ArrayList<JunitCase>();

    /**
     * @param name the case path, e.g. {@code sql/_01_object/cases/t1.sql}; its directory becomes
     *     the classname, the nearest thing to the class a JUnit test method is in
     * @param body text of the result element; written as CDATA
     */
    public JunitCase(
            String name,
            String file,
            long millis,
            Result result,
            String message,
            String type,
            String body) {
        this.classname = directoryOf(name);
        this.name = name;
        this.file = file;
        this.millis = millis;
        this.result = result;
        this.message = message;
        this.type = type;
        this.body = body;
    }

    public static JunitCase passed(String name, String file, long millis) {
        return new JunitCase(name, file, millis, Result.PASSED, null, null, null);
    }

    /**
     * Adds a failed run of this case besides the one this case reports. Written as Surefire's
     * {@code flakyFailure}/{@code flakyError} when this case passed, {@code rerunFailure}/{@code
     * rerunError} when it did not.
     */
    public JunitCase addRerun(JunitCase run) {
        reruns.add(run);
        return this;
    }

    public Result getResult() {
        return result;
    }

    static String directoryOf(String path) {
        if (path == null) {
            return "";
        }
        int slash = path.lastIndexOf('/');
        return slash > 0 ? path.substring(0, slash) : path;
    }
}
