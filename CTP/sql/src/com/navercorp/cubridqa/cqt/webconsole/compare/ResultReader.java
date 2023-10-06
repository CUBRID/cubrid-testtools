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
package com.navercorp.cubridqa.cqt.webconsole.compare;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;

public class ResultReader {

    BufferedReader reader1;

    boolean isEOF = false;

    public static final String SPLIT = "===================================================";

    public ResultReader(String filename1) throws Exception {

        File file = new File(filename1);
        if (file.exists()) {
            FileInputStream fis = new FileInputStream(filename1);
            InputStreamReader fsr = new InputStreamReader(fis, "UTF-8");
            this.reader1 = new BufferedReader(fsr);

            ArrayList<String> result = getNextBlockText();
            if (result.size() > 0) {
                throw new Exception("wrong format for " + filename1);
            }
        } else {
            isEOF = true;
        }
    }

    public ArrayList<String> getNextBlockText() throws IOException {

        if (isEOF) return null;

        ArrayList<String> result = new ArrayList<String>();

        String line;
        while (true) {
            line = reader1.readLine();
            if (line == null) {
                isEOF = true;
                break;
            } else if (line.startsWith(SPLIT)) {
                break;
            }
            result.add(line);
        }
        return result;
    }
}
