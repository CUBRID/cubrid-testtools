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
import java.util.Calendar;


public class SpTest5 {
	
  public static void ptestint1(int i) {
		i = i+ 1;
  }
  public static void ptestint2(int i []) {
		    i[0] =  i[0] + 10;
  }
  public static void ptestint3(int i []) {
	    i[0] =  i[0] + 10;
	    i[1] =  i[1] + 10;
	    i[2] =  i[2] + 10;
	    i[3] =  i[3] + 10;
	    i[4] =  i[4] + 10;
  }
  public static void ptestint4(int i []) {
  }
  public static void ptestint5(int a[], int b[], int c[], int d[], int e[]) {
	  a[0]++;b[0]++;c[0]++;d[0]++;
	  e[0] = a[0]+b[0]+c[0]+d[0];
  }
  
  public static void pteststring0(String a[], String b[], String c[], String d[], String e[]) {
	  a[0] = a[0] + "append";
	  b[0] = b[0] + "append";
	  c[0] = c[0] + "append";
	  d[0] = d[0] + "append";
	  e[0] = e[0] + "append";
  }
  
  public static void pteststring1(String i) {
		i = i+ "append";
  }
  public static void pteststring2(String i []) {
		    i[0] =  i[0] + "append";
  }
  public static void ptestdate1(Date i) {
	  int yy, mm, dd, hh, mi, ss;
	  Calendar c = Calendar.getInstance();
	  
	  c.setTime(i);
	  yy = c.get(Calendar.YEAR) + 1;
	  mm = c.get(Calendar.MONTH) + 2;
	  dd = c.get(Calendar.DAY_OF_MONTH) ;
	  hh = c.get(Calendar.HOUR) + 1;
	  mi = c.get(Calendar.MINUTE);
	  ss = c.get(Calendar.SECOND);
	    
	 i= Date.valueOf(String.format("%04d", yy) + "-" + String.format("%02d", mm) + "-" + String.format("%02d", dd));
	  //i = Date.valueOf(yy + "-" + mm + "-" + dd );

  }
  
  public static void ptestdate11(Date i[]) {
	  int yy, mm, dd, hh, mi, ss;
	  Calendar c = Calendar.getInstance();
	  
	  c.setTime(i[0]);
	  yy = c.get(Calendar.YEAR) + 1;
	  mm = c.get(Calendar.MONTH) + 2;
	  dd = c.get(Calendar.DAY_OF_MONTH) ;
	  hh = c.get(Calendar.HOUR) + 1;
	  mi = c.get(Calendar.MINUTE);
	  ss = c.get(Calendar.SECOND);
	 i[0]= Date.valueOf(String.format("%04d", yy) + "-" + String.format("%02d", mm) + "-" + String.format("%02d", dd+11));
  }
  public static void ptestdate2(Date i []) {
	  int yy, mm, dd, hh, mi, ss;
	  Calendar c = Calendar.getInstance();

	  c.setTime(i[0]);
	  
	  yy = c.get(Calendar.YEAR) + 1; 	  
	  mm = c.get(Calendar.MONTH) + 2;        
	  dd = c.get(Calendar.DAY_OF_MONTH) ;
	  hh = c.get(Calendar.HOUR) + 1;        
	  mi = c.get(Calendar.MINUTE);
	  ss = c.get(Calendar.SECOND);
	 i[0]= Date.valueOf(String.format("%04d", yy) + "-" + String.format("%02d", mm) + "-" + String.format("%02d", dd));
	  
	 // i[0] = Date.valueOf(yy + "-" + mm + "-" + dd );
  }
  
  public static void ptesttime1(Time i) {
	  int yy, mm, dd, hh, mi, ss;
	  Calendar c = Calendar.getInstance();
	  
	  c.setTime(i);
	  yy = c.get(Calendar.YEAR) + 1;
	  mm = c.get(Calendar.MONTH) + 2;
	  dd = c.get(Calendar.DAY_OF_MONTH) ;
	  hh = c.get(Calendar.HOUR) + 1;
	  mi = c.get(Calendar.MINUTE);
	  ss = c.get(Calendar.SECOND);
	  
	  i = Time.valueOf(hh + ":" + mi + ":" + ss);

  }
  public static void ptesttime2(Time i []) {
	  int yy, mm, dd, hh, mi, ss;
	  Calendar c = Calendar.getInstance();
	  
	  c.setTime(i[0]);
	  yy = c.get(Calendar.YEAR) + 1;
	  mm = c.get(Calendar.MONTH) + 2;
	  dd = c.get(Calendar.DAY_OF_MONTH) ;
	  hh = c.get(Calendar.HOUR) + 1;
	  mi = c.get(Calendar.MINUTE);
	  ss = c.get(Calendar.SECOND);
	  
	  i[0] = Time.valueOf(hh + "-" + mi + "-" + ss);
  }
  public static void ptesttime3(Time i []) {
	  int yy, mm, dd, hh, mi, ss;
	  Calendar c = Calendar.getInstance();
	  
	  c.setTime(i[0]);
	  yy = c.get(Calendar.YEAR) + 1;
	  mm = c.get(Calendar.MONTH) + 2;
	  dd = c.get(Calendar.DAY_OF_MONTH) ;
	  hh = c.get(Calendar.HOUR) + 1;
	  mi = c.get(Calendar.MINUTE);
	  ss = c.get(Calendar.SECOND);
	  
	  i[0] = Time.valueOf(hh + ":" + mi + ":" + ss);
  }
  
  public static void ptesttimestamp1(Timestamp i) {
	  int yy, mm, dd, hh, mi, ss;
	  Calendar c = Calendar.getInstance();
	  
	  c.setTime(i);
	  yy = c.get(Calendar.YEAR) + 1;
	  mm = c.get(Calendar.MONTH) + 2;
	  dd = c.get(Calendar.DAY_OF_MONTH) ;
	  hh = c.get(Calendar.HOUR) + 1;
	  mi = c.get(Calendar.MINUTE);
	  ss = c.get(Calendar.SECOND);
	  
//	  i = Timestamp.valueOf(yy +  "-" + mm + "-" + dd + " " + hh + ":" + mi + ":" + ss + ".1");
	  i = Timestamp.valueOf(String.format("%04d-%02d-%02d %02d:%02d:%02d.1", yy, mm, dd, hh, mi, ss));
  }
  public static void ptesttimestamp2(Timestamp i []) {
	  int yy, mm, dd, hh, mi, ss;
	  Calendar c = Calendar.getInstance();
	  c.setTime(i[0]);
	  yy = c.get(Calendar.YEAR) + 1;
	  mm = c.get(Calendar.MONTH) +2;
	  dd = c.get(Calendar.DAY_OF_MONTH) ;
	  hh = c.get(Calendar.HOUR) + 1;
	  mi = c.get(Calendar.MINUTE);
	  ss = c.get(Calendar.SECOND);
//	  i[0] = Timestamp.valueOf(yy +  "-" + mm + "-" + dd + " " + hh + ":" + mi + ":" + ss + ".1");
	  i[0] = Timestamp.valueOf(String.format("%04d-%02d-%02d %02d:%02d:%02d.1", yy, mm, dd, hh, mi, ss));
  }
  
  public static void ptestinteger1(Integer i) {
      int j = i.intValue();
      j++;

      i = new Integer(j);
  }
  public static void ptestinteger2(Integer i []) {
      int j = i[0].intValue();
      j++;

      i[0] = new Integer(j);
  }
  
  public static void ptestinteger3(Integer i []) {
      int j = i[0].intValue();
      j++;

      i[0] = null;
  }
}
