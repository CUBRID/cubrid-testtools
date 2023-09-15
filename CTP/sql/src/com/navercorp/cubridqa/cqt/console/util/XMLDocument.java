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
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class XMLDocument {
    /**
     * @param filename
     * @return
     */
    public static Element getRootElement(String filename) {
        try {
            DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docBuilderFactory.newDocumentBuilder();
            Document doc = docBuilder.parse(new File(filename));
            doc.getDocumentElement().normalize();
            Element root = doc.getDocumentElement();
            return root;
        } catch (SAXException e) {

            e.printStackTrace();
        } catch (IOException e) {

            e.printStackTrace();
        } catch (ParserConfigurationException e) {

            e.printStackTrace();
        }
        return null;
    }

    /**
     * @param file
     * @return
     */
    public static Element getRootElement(File file) {
        try {
            DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docBuilderFactory.newDocumentBuilder();
            Document doc = docBuilder.parse(file);
            doc.getDocumentElement().normalize();
            Element root = doc.getDocumentElement();
            return root;
        } catch (SAXException e) {

            e.printStackTrace();
        } catch (IOException e) {

            e.printStackTrace();
        } catch (ParserConfigurationException e) {

            e.printStackTrace();
        }
        return null;
    }

    /**
     * Get Element By Tag Name
     *
     * @param root
     * @param tagName
     * @return
     */
    public static Element getElementByTagName(Element root, String tagName) {
        NodeList temp_nodes = root.getElementsByTagName(tagName);
        if ((temp_nodes != null) && (temp_nodes.getLength() > 0)) {
            Node temp_node = temp_nodes.item(0);
            if (temp_node.getNodeType() == Node.ELEMENT_NODE) return (Element) temp_node;
        }
        return null;
    }

    /**
     * @param element
     * @param tagName
     * @return
     */
    public static String getValueOfElement(Element root, String tagName) {
        Element temp_element = getElementByTagName(root, tagName);
        if (temp_element == null) return "";
        return temp_element.getTextContent().trim();
    }

    /**
     * @param element
     * @return
     */
    public static Map getAttributesOfElement(Element element) {
        Map map = new HashMap();
        NamedNodeMap attributes = element.getAttributes();
        if (attributes != null) {
            for (int i = 0; i < attributes.getLength(); i++) {
                Node item = attributes.item(i);
                if (item.getNodeType() == Node.ATTRIBUTE_NODE) {
                    map.put(item.getNodeName(), item.getNodeValue());
                }
            }
        }

        return map;
    }

    /**
     * @param element
     * @param attrName
     * @return
     */
    public static String getAttributeOfElement(Element element, String attrName) {
        NamedNodeMap attributes = element.getAttributes();
        if (attributes != null) {
            return attributes.getNamedItem(attrName).getNodeValue();
        }
        return null;
    }

    /**
     * @param root
     * @param tagName
     * @return
     */
    public static List getElementsByTagName(Element root, String tagName) {
        List list = new ArrayList();
        NodeList temp_nodes = root.getElementsByTagName(tagName);
        if ((temp_nodes != null) && (temp_nodes.getLength() > 0)) {
            for (int i = 0; i < temp_nodes.getLength(); i++) {
                Node temp_node = temp_nodes.item(i);
                if (temp_node.getNodeType() == Node.ELEMENT_NODE) list.add(temp_node);
            }
        }
        return list;
    }

    /**
     * @param root
     * @return
     */
    public static List getAllElements(Element root) {
        List list = new ArrayList();
        NodeList temp_nodes = root.getChildNodes();
        if ((temp_nodes != null) && (temp_nodes.getLength() > 0)) {
            for (int i = 0; i < temp_nodes.getLength(); i++) {
                Node temp_node = temp_nodes.item(i);
                if (temp_node.getNodeType() == Node.ELEMENT_NODE) list.add(temp_node);
            }
        }
        return list;
    }

    public static Map getElementsMap(Element root) {
        Map map = new HashMap();
        NodeList temp_nodes = root.getChildNodes();
        if ((temp_nodes != null) && (temp_nodes.getLength() > 0)) {
            for (int i = 0; i < temp_nodes.getLength(); i++) {
                Node temp_node = temp_nodes.item(i);

                if (temp_node.getNodeType() == Node.ELEMENT_NODE) {

                    Element temp_element = (Element) temp_node;
                    if (temp_element.getChildNodes().getLength() > 1) {
                        Map subMap = XMLDocument.getElementsMap((Element) temp_node);
                        map.put(temp_node.getNodeName(), subMap);
                    } else {
                        map.put(temp_node.getNodeName(), temp_node.getTextContent().trim());
                    }
                }
            }
        }
        return map;
    }
}
