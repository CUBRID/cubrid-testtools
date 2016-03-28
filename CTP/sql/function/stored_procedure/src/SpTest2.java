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
import java.sql.*;
import java.math.BigDecimal;

public class SpTest2 {
	 public static Byte typetestbyte(String i) {
		 	Byte j = new Byte("111");
		    return j;
     }
	 public static Short typetestshort(String i) {
		 	Short j = new Short(i);
		    return j;
	 }
	 public static Integer typetestinteger(String i) {
		 	Integer j = new Integer(Integer.parseInt(i));
		    return j;
	 }
	 public static Long typetestlong(String i) {
		 	Long j = new Long(i);
		    return j;
	 }
	 public static Float typetestfloat(String i) {
		 	Float j = new Float(i);
		    return j;
	 }
	 public static Double typetestdouble(String i) {
		 	Double j = new Double(i);
		    return j;
	 }
	 public static BigDecimal typetestbigdecimal(String i) {
		 	BigDecimal j = new BigDecimal(i);
		    return j;
	 }
	 public static short typetestshort1(String i) {
		 	short j = 11;
		    return j;
	 }
	 public static long typetestlong1(String i) {
		 	long j = Integer.parseInt(i);
		    return j;
	 }
	 public static float typetestfloat1(String i) {
		    float j = Integer.parseInt(i);
		    return j;
	 } 
	 public static double typetestdouble1(String i) {
		    double j = Integer.parseInt(i);
		    return j;
	 }
}
