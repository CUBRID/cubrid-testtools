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
package com.navercorp.cubridqa.cqt.model;

import java.io.File;
import java.util.StringTokenizer;

import com.navercorp.cubridqa.cqt.console.util.XstreamHelper;
import com.thoughtworks.xstream.annotations.XStreamAlias;

@XStreamAlias(value = "resource")
public class Resource {

	protected String id;

	protected String name;

	public String path;

	public Resource() {
	}

	public static Resource getInstance(String absPath) {
		Resource instance = new Resource();
		if (absPath != null && !absPath.equals("")) {
			instance = (Resource) XstreamHelper.fromXml(absPath);
		}
		return instance;
	}

	public Resource(String absPath) {
		setName(absPath.replaceAll("\\\\", "/"));
	}

	public String getFileName() {
		return name.substring(name.lastIndexOf("/") + 1);
	}

	public String getRealName() {
		StringTokenizer stringTokenizer = new StringTokenizer(name, ".");
		String string = "";
		if (stringTokenizer.hasMoreTokens()) {
			string = stringTokenizer.nextToken();
		}
		return string.substring(string.lastIndexOf("/") + 1, string.length());
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getPath() {
		return path;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public void setPath(String path) {
		this.path = path;
	}

	public boolean equals(Object obj) {
		if (obj instanceof Case) {
			Case c = (Case) obj;
			return name != null && name.equals(c.getName());
		}
		return false;
	}

	public File getFile() {
		return new File(name);
	}

	@Override
	public int hashCode() {
		return name.hashCode();
	}

	public String getTITLE() {
		return "Resource";
	}

}
