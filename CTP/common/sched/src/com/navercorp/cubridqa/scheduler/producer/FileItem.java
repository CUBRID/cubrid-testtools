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
package com.navercorp.cubridqa.scheduler.producer;

import java.io.File;


import com.navercorp.cubridqa.scheduler.common.CommonUtils;

public class FileItem {

	boolean isExact;
	String fileFM, prefix, suffix;
	File file;

	public FileItem(String fileFM) {
		this.fileFM = fileFM;
		isExact = true;
	}

	public FileItem(String prefix, String suffix) {
		this.prefix = prefix;
		this.suffix = suffix;
		isExact = false;
	}
	
	public String getFilePattern() {
		if(isExact) {
			return this.fileFM;
		} else {
			return this.prefix + "|" + this.suffix;
		}
	}

	public String getActualFilename(File buildFileRoot, String actualBuildId) {
		if (isExact) {
			return CommonUtils.replace(fileFM, "{1}", actualBuildId);
		} else {
			File[] subList = buildFileRoot.listFiles();
			String fn;
			for (File f : subList) {
				fn = f.getName();
				if(match(actualBuildId, fn)) {
					return fn;					
				}
			}
		}
		return null;
	}

	public boolean match(String actualBuildId, String filename) {
		if (isExact) {
			return CommonUtils.replace(fileFM, "{1}", actualBuildId).equals(filename);
		} else {
			String prefix1 = CommonUtils.replace(this.prefix, "{1}", actualBuildId);
			String suffix1 = CommonUtils.replace(this.suffix, "{1}", actualBuildId);
			return filename.startsWith(prefix1) && filename.endsWith(suffix1);
		}
	}

	public void setExactFile(File file) {
		this.file = file;
	}

	public File getExactFile() {
		return this.file;
	}
	
	@Override
	public String toString() {
		if(this.file == null) {
			return fileFM;
		} else {
			return this.file.getAbsolutePath();			
		}
	}
}
