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
/** */
package com.navercorp.cubridqa.cqt.console.util;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.w3c.dom.Element;

public class DatabaseXMLReader extends XMLReader {
    private List list = new ArrayList();

    public DatabaseXMLReader(String filename) {
        super(filename);
    }

    public DatabaseXMLReader(File file) {
        super(file);
    }

    /** */
    public List parse() {
        List tlist = super.parseList();

        for (Iterator iterator = tlist.iterator(); iterator.hasNext(); ) {
            Element element = (Element) iterator.next();
            DatabaseInfo db = new DatabaseInfo();
            db.setDbName(XMLDocument.getValueOfElement(element, "name"));
            db.setUser(XMLDocument.getValueOfElement(element, "user"));
            db.setPassword(XMLDocument.getValueOfElement(element, "password"));
            this.list.add(db);
        }

        return this.list;
    }

    public Map parseMap() {
        Map map = super.parseMap();
        return map;
    }

    /**
     * @param dbName
     * @return
     */
    public DatabaseInfo getDatabaseInfo(String dbName) {
        for (Iterator iterator = this.list.iterator(); iterator.hasNext(); ) {
            DatabaseInfo db = (DatabaseInfo) iterator.next();
            if (db.getDbName().equals(dbName)) return db;
        }
        return null;
    }
}
