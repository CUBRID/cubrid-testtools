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

import java.io.IOException;

import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;

public class ShareMemory {

	private String fname;
	private int size = 1024;

	public ShareMemory(String fname, int size) {
		this.fname = fname;
		this.size = size;
	}

	public boolean wait(String expectedValue, int times) throws Exception {
		String value;
		for (int i = 0; i < times; i++) {
			value = read();
			if (value != null && value.equals(expectedValue)) {
				return true;
			}
			Thread.sleep(10);
		}
		return false;
	}

	public String read() throws IOException {
		RandomAccessFile raf = null;
		FileChannel fc = null;
		MappedByteBuffer mbb = null;
		String value;
		byte[] data = new byte[size];

		try {
			raf = new RandomAccessFile(fname, "rw");
			fc = raf.getChannel();
			mbb = fc.map(MapMode.READ_WRITE, 0, size);
			mbb.get(data);
			value = new String(data);
		} finally {
			try {
				raf.close();
			} catch (Exception e) {
			}
			try {
				fc.close();
			} catch (Exception e) {
			}
		}
		return value.trim();
	}

	public void write(String value) throws IOException {
		RandomAccessFile raf = null;
		FileChannel fc = null;
		MappedByteBuffer mbb = null;

		try {
			raf = new RandomAccessFile(fname, "rw");
			fc = raf.getChannel();
			mbb = fc.map(MapMode.READ_WRITE, 0, size);
			byte[] bytes = new byte[size];
			byte[] data = value.getBytes();
			for (int i = 0; i < data.length; i++) {
				bytes[i] = data[i];
			}
			mbb.put(bytes);

		} finally {
			try {
				raf.close();
			} catch (Exception e) {
			}
			try {
				fc.close();
			} catch (Exception e) {
			}
		}
	}
}
