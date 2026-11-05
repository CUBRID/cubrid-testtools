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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

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
    private String body;
    private File bodyFile;
    final List<JunitCase> reruns = new ArrayList<JunitCase>();

    public JunitCase(
            String name,
            String file,
            long millis,
            Result result,
            String message,
            String type,
            String body) {
        // a case's directory is the nearest thing to the class a JUnit test method is in
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

    public static JunitCase skipped(String name, String file, String message) {
        return new JunitCase(name, file, 0, Result.SKIPPED, message, null, null);
    }

    public void addRerun(JunitCase run) {
        reruns.add(run);
    }

    /** So a run kept until its case is written holds no log in memory. */
    public void moveBodyTo(File dir) throws IOException {
        if (body == null || body.length() == 0) {
            return;
        }
        dir.mkdirs();
        File f = File.createTempFile("body", ".txt", dir);
        try {
            Files.write(f.toPath(), body.getBytes("UTF-8"));
        } catch (IOException e) {
            f.delete();
            throw e;
        }
        bodyFile = f;
        body = null;
    }

    // Throwing here would drop the case from the report, and a failed case must stay listed.
    String body() {
        if (bodyFile == null) {
            return body;
        }
        try {
            return new String(Files.readAllBytes(bodyFile.toPath()), "UTF-8");
        } catch (IOException e) {
            return "(the log kept in " + bodyFile + " could not be read: " + e + ")";
        }
    }

    public void deleteBodyFiles() {
        if (bodyFile != null) {
            bodyFile.delete();
        }
        for (JunitCase run : reruns) {
            run.deleteBodyFiles();
        }
    }

    static String directoryOf(String path) {
        if (path == null) {
            return "";
        }
        int slash = path.lastIndexOf('/');
        return slash > 0 ? path.substring(0, slash) : path;
    }
}
