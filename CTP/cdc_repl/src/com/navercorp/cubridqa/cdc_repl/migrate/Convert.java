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
package com.navercorp.cubridqa.cdc_repl.migrate;

import java.io.File;

public class Convert {

	String rootFilename;
	int count = 0;

	public Convert(String rootFilename) {
		this.rootFilename = rootFilename;
		System.out.println("Convert Root:  " + this.rootFilename);
	}

	public static void main(String[] args) throws Exception {
		// Convert c = new Convert("D:\\_20_apricot_qa");
		Convert c = new Convert("./1016.sql");
		// Convert c = new Convert("./update_order_by_001.sql");
		// Convert c = new Convert("./test/sql");
		c.convert();
	}

	public void convert() throws Exception {
		travel(new File(rootFilename));
		System.out.println("Finished covert files: " + count);
	}

	private void travel(File testCaseFile) throws Exception {
		if (testCaseFile.isDirectory()) {
			File[] subList = testCaseFile.listFiles();
			for (File subFile : subList) {
				travel(subFile);
			}
		} else {
			if (testCaseFile.getName().toUpperCase().endsWith(".SQL")) {
				convert(testCaseFile);
			}
		}
	}

	private void convert(File f) throws Exception {

		if (count % 100 == 0) {
			System.out.println(count);
		}
		count++;
		SQLFileReader r = new SQLFileReader(f);
		try {
			r.convert();
		} finally {
			r.close();
		}
	}
}
