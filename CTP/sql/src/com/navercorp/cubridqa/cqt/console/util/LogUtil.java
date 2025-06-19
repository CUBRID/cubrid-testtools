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
package com.navercorp.cubridqa.cqt.console.util;

import java.text.SimpleDateFormat;
import java.util.Date;

public class LogUtil {

    /**
     * write the message to file .
     *
     * @param logId
     * @param message
     */
    public static void log(String logId, String message) {
        if (logId == null || message == null) {
            return;
        }

        String date = "";
        SimpleDateFormat sdf = new SimpleDateFormat("dd HH:mm:ss");
        try {
            date = sdf.format(new Date());
        } catch (Exception e) {
        }

        message = "[" + date + "]: " + message + System.getProperty("line.separator");
        FileUtil.writeToFile(logId + ".log", message, true);
    }

    /*
     * write "" to the file .
     */
    public static void clearLog(String logId) {
        if (logId == null) {
            return;
        }
        FileUtil.writeToFile(logId + ".log", "");
    }

    /**
     * get the trace back message of exception.
     *
     * @param e
     * @return
     */
    public static String getExceptionMessage(Exception e) {
        if (e == null) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        sb.append(e.toString() + System.getProperty("line.separator"));
        StackTraceElement[] elems = e.getStackTrace();
        for (int i = 0; i < elems.length; i++) {
            sb.append(elems[i].toString() + System.getProperty("line.separator"));
        }

        return sb.toString();
    }
}
