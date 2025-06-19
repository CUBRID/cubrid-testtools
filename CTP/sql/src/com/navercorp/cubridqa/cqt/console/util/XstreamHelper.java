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
import com.navercorp.cubridqa.cqt.console.bean.DefTestDB;
import com.navercorp.cubridqa.cqt.console.bean.SummaryInfo;
import com.navercorp.cubridqa.cqt.console.bean.SystemModel;
import com.navercorp.cubridqa.cqt.model.Case;
import com.navercorp.cubridqa.cqt.model.Resource;
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.annotations.Annotations;
import com.thoughtworks.xstream.io.xml.DomDriver;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;

public class XstreamHelper {

    private static XStream xs;

    private static XStream xs2;

    private static XStream xs3;

    static {
        xs = new XStream(new DomDriver());
        // zmc 2008-7-31 add
        Annotations.configureAliases(xs, Resource.class);
        Annotations.configureAliases(xs, Case.class);
        Annotations.configureAliases(xs, SummaryInfo.class);
        Annotations.configureAliases(xs, SystemModel.class);
        Annotations.configureAliases(xs, DefTestDB.class);
        Annotations.configureAliases(xs, CaseResult.class);

        xs2 = new XStream(new DomDriver());
        // zmc 2008-1-09 add
        // zmc 2008-7-31 add
        Annotations.configureAliases(xs2, Resource.class);
        Annotations.configureAliases(xs2, Case.class);
        Annotations.configureAliases(xs2, SummaryInfo.class);
        Annotations.configureAliases(xs2, SystemModel.class);

        Annotations.configureAliases(xs2, DefTestDB.class);
        Annotations.configureAliases(xs2, CaseResult.class);

        xs3 = new XStream(new DomDriver());
    }

    /**
     * 转换javabean为xml
     *
     * @param obj
     * @return
     */
    public static String toXml(Object obj) {
        return xs.toXML(obj);
    }

    /**
     * 转化xml为javabean
     *
     * @param path xml文档路径
     * @return
     */
    public static Object fromXml(String path) {
        try {
            FileInputStream inputStream = new FileInputStream(path);
            return xs2.fromXML(inputStream);
        } catch (FileNotFoundException e) {
        }
        return null;
    }

    public static void save(Object object, String filePath) {
        FileOutputStream ops;
        try {
            ops = new FileOutputStream(new File(filePath));
            xs.toXML(object, ops);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }
}
