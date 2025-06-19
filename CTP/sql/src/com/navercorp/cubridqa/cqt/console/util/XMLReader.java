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
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.w3c.dom.Element;

public class XMLReader {

    private String filename;
    private File file;

    public XMLReader() {}

    public XMLReader(String filename) {
        super();
        this.filename = filename;
        this.file = new File(this.filename);
    }

    public XMLReader(File file) {

        this.file = file;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    /** @return */
    protected List parseList() {
        List list = new ArrayList();
        Element root = XMLDocument.getRootElement(this.file);
        list = XMLDocument.getAllElements(root);
        return list;
    }

    /** @return */
    protected Map parseMap() {
        Element root = XMLDocument.getRootElement(this.file);
        Map map = XMLDocument.getElementsMap(root);
        return map;
    }

    /**
     * @param root
     * @param tagName
     * @return
     */
    protected Map getAttributesOfElement(Element root, String tagName) {
        Element element = XMLDocument.getElementByTagName(root, tagName);
        return XMLDocument.getAttributesOfElement(element);
    }

    /**
     * @param root
     * @param tagName
     * @param attrName
     * @return
     */
    protected String getAttributeOfElement(Element root, String tagName, String attrName) {
        Element element = XMLDocument.getElementByTagName(root, tagName);
        return XMLDocument.getAttributeOfElement(element, attrName);
    }

    /**
     * @param root
     * @param tagName
     * @return
     */
    protected Map getKeyValuesOfElement(Element root, String tagName) {
        Element tag = XMLDocument.getElementByTagName(root, tagName);
        Map map = new HashMap();
        if (tag == null) return null;
        List list = XMLDocument.getAllElements(tag);
        for (Iterator iterator = list.iterator(); iterator.hasNext(); ) {
            Element element = (Element) iterator.next();
            String key = element.getNodeName();
            String value = element.getTextContent().trim();
            map.put(key, value);
        }
        return map;
    }
}
