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

import com.navercorp.cubridqa.cqt.console.bean.CaseResult;
import com.navercorp.cubridqa.cqt.console.bean.Test;
import com.navercorp.cubridqa.cqt.console.bo.ConsoleBO;
import java.io.File;
import java.util.Date;

public abstract class ErrorInterruptUtil {

    /**
     * @Title: isCaseRunError @Description:Determine whether the tool need stop.
     *
     * @param @param bo
     * @param @param caseFile
     * @param @return
     * @return boolean
     * @throws
     */
    @SuppressWarnings("deprecation")
    public static boolean isCaseRunError(ConsoleBO bo, String caseFile) {
        if (!ErrorInterrupt.ERROR_INTER) {
            return false;
        }

        Test test = bo.getTest();
        CaseResult caseResult = test.getCaseResultFromMap(caseFile);
        boolean runResult = caseResult.isSuccessFul();
        if (!runResult) {
            bo.getProcessMonitor().setCompleteFile(bo.getProcessMonitor().getAllFile());
            String resultDir = caseResult.getResultDir();
            Date now = new Date();
            String month = "m" + (now.getMonth() + 1);
            String realResult = realResult(resultDir, month);
            ErrorInterrupt.DELETE_RESULT_PATH = realResult;
            return true;
        }
        return false;
    }

    private static String realResult(String resultDir, String month) {
        try {
            File now = new File(resultDir);
            File parent = now.getParentFile();
            if (parent.getAbsolutePath().endsWith(month)) {
                return resultDir;
            } else {
                return realResult(parent.getAbsolutePath(), month);
            }
        } catch (Throwable t) {
            return null;
        }
    }
}
