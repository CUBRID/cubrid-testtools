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

import com.navercorp.cubridqa.common.junit.JunitCase.Result;
import com.sun.xml.txw2.output.IndentingXMLStreamWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

/**
 * Writes a {@link JunitSuite} and its cases as JUnit XML.
 *
 * <p>Each case goes to the file as it is added, so a run that dies still leaves the cases it
 * finished, and no case is held in memory. {@link #close} writes the file again with the suite's
 * counts and time: the counts are attributes of the start tags, known only at the end.
 *
 * <p>Readers depend on the form: gha-ci's collect awk takes the case name from {@code name="} after
 * a space and the case log from the result element's CDATA; CircleCI splits shell tests by the
 * {@code file} attribute. Keep the {@code testcase} attribute names and order.
 */
public final class JunitXml {

    private final File file;
    private final JunitSuite suite;
    private final FileOutputStream out;
    private final XMLStreamWriter w;
    private final long casesStart;
    private int tests;
    private int failures;
    private int errors;
    private int skipped;

    public JunitXml(File file, JunitSuite suite) throws IOException, XMLStreamException {
        this.file = file;
        this.suite = suite;
        out = new FileOutputStream(file);
        w = open(out);
        w.writeStartElement("testsuites");
        writeSuiteStart(w, false);
        w.flush();
        casesStart = out.getChannel().position();
    }

    public synchronized void add(JunitCase c) throws XMLStreamException {
        writeCase(w, c);
        w.flush();
        tests++;
        if (c.result == Result.FAILURE) {
            failures++;
        } else if (c.result == Result.ERROR) {
            errors++;
        } else if (c.result == Result.SKIPPED) {
            skipped++;
        }
    }

    /**
     * Writes the report again with the counts, through a temporary file, so a reader never sees
     * half of it. The cases are copied as written: the same writer indents them at the same depth.
     */
    public synchronized void close() throws IOException, XMLStreamException {
        try {
            w.close();
        } finally {
            out.close();
        }
        File tmp = new File(file.getPath() + ".tmp");
        FileOutputStream to = new FileOutputStream(tmp);
        boolean written = false;
        try {
            XMLStreamWriter f = open(to);
            f.writeStartElement("testsuites");
            writeCounts(f);
            writeSuiteStart(f, true);
            f.flush();
            copyCases(to);
            f.writeEndElement();
            f.writeEndElement();
            f.writeEndDocument();
            f.close();
            written = true;
        } finally {
            to.close();
            if (!written) {
                tmp.delete();
            }
        }
        Files.move(
                tmp.toPath(),
                file.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE);
    }

    /** Closes without a report: the half-written file is deleted. */
    public synchronized void discard() {
        try {
            w.close();
        } catch (XMLStreamException ignore) {
        }
        try {
            out.close();
        } catch (IOException ignore) {
        }
        file.delete();
    }

    private void copyCases(OutputStream to) throws IOException {
        InputStream in = new FileInputStream(file);
        try {
            long skip = casesStart;
            while (skip > 0) {
                long n = in.skip(skip);
                if (n <= 0) {
                    throw new IOException("cannot skip to the cases of " + file);
                }
                skip -= n;
            }
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) {
                to.write(buf, 0, n);
            }
        } finally {
            in.close();
        }
    }

    private static XMLStreamWriter open(OutputStream out) throws XMLStreamException {
        XMLStreamWriter w =
                new IndentingXMLStreamWriter(
                        XMLOutputFactory.newInstance().createXMLStreamWriter(out, "UTF-8"));
        w.writeStartDocument("UTF-8", "1.0");
        return w;
    }

    private void writeCounts(XMLStreamWriter w) throws XMLStreamException {
        w.writeAttribute("tests", String.valueOf(tests));
        w.writeAttribute("failures", String.valueOf(failures));
        w.writeAttribute("errors", String.valueOf(errors));
        w.writeAttribute("skipped", String.valueOf(skipped));
        w.writeAttribute("time", seconds(suite.millis));
    }

    private void writeSuiteStart(XMLStreamWriter w, boolean complete) throws XMLStreamException {
        w.writeStartElement("testsuite");
        w.writeAttribute("name", suite.name);
        if (complete) {
            writeCounts(w);
        }
        w.writeAttribute("timestamp", timestamp(suite.startMillis));
        w.writeAttribute("hostname", suite.hostname);
        w.writeStartElement("properties");
        for (Map.Entry<String, String> p : suite.properties.entrySet()) {
            w.writeEmptyElement("property");
            w.writeAttribute("name", p.getKey());
            w.writeAttribute("value", p.getValue());
        }
        w.writeEndElement();
    }

    private static void writeCase(XMLStreamWriter w, JunitCase c) throws XMLStreamException {
        w.writeStartElement("testcase");
        w.writeAttribute("classname", c.classname);
        w.writeAttribute("name", c.name);
        if (c.file != null) {
            w.writeAttribute("file", c.file);
        }
        w.writeAttribute("time", seconds(c.millis));
        if (c.result != Result.PASSED) {
            w.writeStartElement(c.result.name().toLowerCase(Locale.ROOT));
            writeMessageAndType(w, c);
            writeCData(w, c.body);
            w.writeEndElement();
        }
        String prefix = c.result == Result.PASSED ? "flaky" : "rerun";
        for (JunitCase run : c.reruns) {
            w.writeStartElement(prefix + (run.result == Result.ERROR ? "Error" : "Failure"));
            writeMessageAndType(w, run);
            w.writeStartElement("stackTrace");
            writeCData(w, run.body);
            w.writeEndElement();
            w.writeEndElement();
        }
        w.writeEndElement();
    }

    private static void writeMessageAndType(XMLStreamWriter w, JunitCase c)
            throws XMLStreamException {
        if (c.message != null) {
            w.writeAttribute("message", c.message);
        }
        if (c.type != null) {
            w.writeAttribute("type", c.type);
        }
    }

    private static void writeCData(XMLStreamWriter w, String text) throws XMLStreamException {
        if (text != null && text.length() > 0) {
            w.writeCData(cdataSafe(text));
        }
    }

    /**
     * XML 1.0 allows TAB and LF but no other C0 control character, so those become '?'. CR goes:
     * written back it would come out as {@code &#xD;}. A {@code ]]>} would end the CDATA, so it is
     * split across two sections.
     */
    static String cdataSafe(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\r') {
                continue;
            }
            sb.append(c <= '\u001F' && c != '\t' && c != '\n' ? '?' : c);
        }
        return sb.toString().replace("]]>", "]]]]><![CDATA[>");
    }

    private static String seconds(long millis) {
        return String.format(Locale.ROOT, "%.3f", millis / 1000.0);
    }

    /** Ant's form: GMT, no zone suffix. */
    private static String timestamp(long millis) {
        SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ROOT);
        iso.setTimeZone(TimeZone.getTimeZone("GMT"));
        return iso.format(new Date(millis));
    }
}
