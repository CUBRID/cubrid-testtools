/**
 * Copyright (c) 2016, Search Solution Corporation. All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without 
 * modification, are permitted provided that the following conditions are met:
 * 
 *   * Redistributions of source code must retain the above copyright notice, 
 *     this list of conditions and the following disclaimer.
 * 
 *   * Redistributions in binary form must reproduce the above copyright 
 *     notice, this list of conditions and the following disclaimer in 
 *     the documentation and/or other materials provided with the distribution.
 * 
 *   * Neither the name of the copyright holder nor the names of its contributors may be used to endorse or promote products 
 *     derived from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, 
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE 
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, 
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR 
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE 
 * USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE. 
 */
package com.navercorp.cubridqa.common;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Set;

import org.ini4j.Config;
import org.ini4j.Ini;
import org.ini4j.InvalidFileFormatException;
import org.ini4j.Wini;

@SuppressWarnings("unchecked")
public class IniData {

	private final static String ctpHome = CommonUtils.getEnvInFile("CTP_HOME");
	private final static String userHome = CommonUtils.getEnvInFile("HOME");

	private File file;
	Ini ini;
	private HashMap<String, Section> sectionMap;

	public IniData(String fileName) throws InvalidFileFormatException, IOException {
		this(new File(fileName.trim()));
	}

	public IniData(File file) throws InvalidFileFormatException, IOException {
		this.file = file;
		this.sectionMap = new HashMap<String, Section>();
		init();
	}

	public String get(String sectionName, String key) {
		if (sectionName == null || sectionName.trim().equals(""))
			sectionName = "?";
		Section section = this.sectionMap.get(sectionName);
		if (section == null) {
			return null;
		} else {
			return section.get(key);
		}
	}

	public String getAndTrans(String sectionName, String key) {
		return translateValue(get(sectionName, key));
	}

	public Section getSection(String sectionName) {
		if (sectionName == null || sectionName.trim().equals(""))
			sectionName = "?";
		return this.sectionMap.get(sectionName);
	}

	public void put(String sectionName, String key, String value, boolean store) throws IOException {
		if (sectionName == null || sectionName.trim().equals(""))
			sectionName = "?";
		if (store) {
			Ini.Section internalSection = (Ini.Section) this.ini.get(sectionName);
			if (internalSection == null) {
				internalSection = this.ini.add(sectionName);
			}
			internalSection.add(key, value);
			this.ini.store();
		}

		Section section = this.sectionMap.get(sectionName);
		if (section == null) {
			section = new Section(sectionName);
			this.sectionMap.put(sectionName, section);
		}
		section.put(key, value);
	}

	public void remove(String sectionName, boolean store) throws IOException {
		Section section = this.sectionMap.get(sectionName);
		HashMap<String, String> data = section.getData();
		Set<String> set = data.keySet();
		Iterator<String> it = set.iterator();
		ArrayList<String> keys = new ArrayList<String>();
		while (it.hasNext()) {
			keys.add(it.next());
		}
		for (String key : keys) {
			remove(sectionName, key, store);
		}
	}

	public void remove(String sectionName, String key, boolean store) throws IOException {
		if (sectionName == null || sectionName.trim().equals(""))
			sectionName = "?";
		if (store) {
			Ini.Section internalSection = (Ini.Section) this.ini.get(sectionName);
			this.ini.remove(sectionName, key);
			this.ini.store();
		}

		Section section = this.sectionMap.get(sectionName);
		if (section == null) {
			section = new Section(sectionName);
			this.sectionMap.put(sectionName, section);
		}
		section.remove(key);
	}

	public String getFilename() throws IOException {
		return this.file.getCanonicalPath();
	}

	public void saveAs(String newFilename, String oldPath, String newPath) throws IOException {
		File newFile = new File(newFilename);
		if (newFile.exists())
			newFile.delete();
		newFile.createNewFile();

		Ini ini = new Wini();
		Config conf = ini.getConfig();
		conf.setPathSeparator('/');

		ini.setFile(newFile);
		ini.load();

		Set set = sectionMap.keySet();

		Iterator<String> it = (Iterator<String>) set.iterator();

		String sectionName, newSectionName;
		Section section;
		Ini.Section internalSection;
		while (it.hasNext()) {
			sectionName = it.next();
			section = sectionMap.get(sectionName);

			if (!sectionName.equals("?") && !sectionName.startsWith(oldPath)) {
				continue;
			}

			if (sectionName.equals("?")) {
				newSectionName = "common";
			} else {
				newSectionName = sectionName.replaceFirst(oldPath, newPath);
			}

			internalSection = ini.add(newSectionName);

			internalSection.putAll(section.getData());
		}
		ini.store();
	}

	public String toString() {
		StringBuffer result = new StringBuffer();

		Set<Entry<String, Ini.Section>> allEntries = ini.entrySet();
		for (Entry<String, Ini.Section> entry : allEntries) {
			String secKey = entry.getKey();
			Ini.Section sec = entry.getValue();

			result.append("[").append(secKey).append("]").append('\n');

			Set<Entry<String, String>> subEntries = sec.entrySet();
			for (Entry<String, String> pair : subEntries) {
				result.append(pair.getKey()).append("=").append(pair.getValue()).append('\n');
			}

			result.append('\n');
		}
		return result.toString();
	}

	private void init() throws InvalidFileFormatException, IOException {
		this.ini = new Wini();
		Config conf = ini.getConfig();
		conf.setPathSeparator('/');

		ini.setFile(file);
		ini.load();

		Section section;

		Set<Entry<String, Ini.Section>> allEntries = ini.entrySet();
		for (Entry<String, Ini.Section> entry : allEntries) {
			String secKey = entry.getKey();
			Ini.Section sec = entry.getValue();

			Set<Entry<String, String>> subEntries = sec.entrySet();
			for (Entry<String, String> pair : subEntries) {
				if (this.sectionMap.containsKey(secKey) == false) {
					section = new Section(secKey);
					this.sectionMap.put(secKey, section);
				} else {
					section = this.sectionMap.get(secKey);
				}
				section.put(pair.getKey(), pair.getValue());
			}
		}
	}

	public static String translateValue(String value) {
		if (value == null)
			return value;

		if (userHome != null) {
			value = CommonUtils.replace(value, "${HOME}", userHome);
		}

		if (ctpHome != null) {
			value = CommonUtils.replace(value, "${CTP_HOME}", ctpHome);
		}
		return value;
	}

	public class Section {
		private String name;
		private HashMap<String, String> pairs;

		public Section(String name) {
			this.name = name;
			this.pairs = new HashMap<String, String>();
		}

		public String getName() {
			return this.name;
		}

		public void put(String key, String value) {
			if (key != null) {
				this.pairs.put(key, value);
			}
		}

		public String get(String key) {
			return this.pairs.get(key);
		}

		public String getAndTrans(String key) {
			return IniData.translateValue(get(key));
		}

		public void remove(String key) {
			this.pairs.remove(key);
		}

		public HashMap<String, String> getData() {
			return pairs;
		}
	}

}
