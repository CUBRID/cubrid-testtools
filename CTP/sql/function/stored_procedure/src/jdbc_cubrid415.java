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
import java.util.*;
import java.sql.*;
import cubrid.jdbc.driver.*;
import cubrid.sql.*;

public class jdbc_cubrid415 {
	
	public static String main1(String query)  {
        Connection conn = null;
        Statement stmt = null;
        String ret="";
        
        try {
        	  Class.forName("cubrid.jdbc.driver.CUBRIDDriver");
              conn = DriverManager.getConnection("jdbc:default:connection:","","");

              conn.setAutoCommit (false) ;
              stmt = conn.createStatement();
	          ResultSet rs= stmt.executeQuery(query);
		
	          ResultSetMetaData rsmd = rs.getMetaData();
	          int numberofColumn = rsmd.getColumnCount();
		
	          for(int i=1; i<=numberofColumn; i++ ) {
		         String ColumnName=rsmd.getColumnName(i);
		         ret = ret + ColumnName + "|";
	          }
		
	          while (rs.next ()) {
		        for(int j=1; j<=numberofColumn; j++ ) {
			       ret = ret + rs.getObject(j) + "|";
		        }
	          }

              stmt.close();
              conn.close();
        } catch ( SQLException e ) {
			//e.printStackTrace();
        } catch ( Exception e ) {
			//e.printStackTrace();
        } 
        return ret;
	}
	
	public static void main2(String query)  {
        Connection conn = null;
        Statement stmt = null;
        String ret="";
        
        try {
        	  Class.forName("cubrid.jdbc.driver.CUBRIDDriver");
              conn = DriverManager.getConnection("jdbc:default:connection:","","");

              conn.setAutoCommit (false) ;
              stmt = conn.createStatement();
	          stmt.executeUpdate(query);

              stmt.close();
              conn.close();
        } catch ( SQLException e ) {
			//e.printStackTrace();
        } catch ( Exception e ) {
			//e.printStackTrace();
        } 
	}
	
	public static void main3_c(String query)  {
        Connection conn = null;
        Statement stmt = null;
        String ret="";
        
        try {
        	  Class.forName("cubrid.jdbc.driver.CUBRIDDriver");
              conn = DriverManager.getConnection("jdbc:default:connection:","","");

              conn.setAutoCommit (false) ;
              stmt = conn.createStatement();
	          stmt.executeUpdate(query);
	          
	          conn.commit();

              stmt.close();
              conn.close();
        } catch ( SQLException e ) {
			//e.printStackTrace();
        } catch ( Exception e ) {
			//e.printStackTrace();
        } 
	}
	
	public static void main3_r(String query)  {
        Connection conn = null;
        Statement stmt = null;
        String ret="";
        
        try {
        	  Class.forName("cubrid.jdbc.driver.CUBRIDDriver");
              conn = DriverManager.getConnection("jdbc:default:connection:","","");

              conn.setAutoCommit (false) ;
              stmt = conn.createStatement();
	          stmt.executeUpdate(query);
	          
	          conn.rollback();

              stmt.close();
              conn.close();
        } catch ( SQLException e ) {
			//e.printStackTrace();
        } catch ( Exception e ) {
			//e.printStackTrace();
        } 
	}
	
	public static void main3_autocommiton(String query)  {
        Connection conn = null;
        Statement stmt = null;
        String ret="";
        
        try {
        	  Class.forName("cubrid.jdbc.driver.CUBRIDDriver");
              conn = DriverManager.getConnection("jdbc:default:connection:","","");

              conn.setAutoCommit (true) ;
              stmt = conn.createStatement();
	          stmt.executeUpdate(query);

              stmt.close();
              conn.close();
        } catch ( SQLException e ) {
			//e.printStackTrace();
        } catch ( Exception e ) {
			//e.printStackTrace();
        } 
	}
	
	public static String testresult (String query) {
		String ret = "";
		try {
			Class.forName("cubrid.jdbc.driver.CUBRIDDriver"); 
			Connection con = DriverManager.getConnection("jdbc:default:connection:"); 

			if (con instanceof CUBRIDConnection) {
				((CUBRIDConnection)con).setCharset("euc_kr");
			}
	
			CallableStatement cstmt = con.prepareCall("? = CALL testResultSet(?)"); 
			cstmt.registerOutParameter(1, Types.JAVA_OBJECT); 
			cstmt.registerOutParameter(2, Types.VARCHAR); 
			cstmt.setObject(2, query);
			cstmt.execute(); 
			ResultSet rs = (ResultSet) cstmt.getObject(1); 
			rs = (ResultSet) cstmt.getObject(1); 
	        ResultSetMetaData rsmd = rs.getMetaData();
	        int numberofColumn = rsmd.getColumnCount();
	        
	        for(int i=1; i<=numberofColumn; i++ ) {
			         String ColumnName=rsmd.getColumnName(i);
			         ret = ret + ColumnName + "|";
		    }
			
		    while (rs.next ()) {
			        for(int j=1; j<=numberofColumn; j++ ) {
				       ret = ret + rs.getObject(j) + "|";
			        }
		    }
			rs.close(); 
		} catch (Exception e){
			//e.printStackTrace(); 
		}
		return ret;
	}

	public static ResultSet testResultSet(String sql) { 
		try { 
			Class.forName("cubrid.jdbc.driver.CUBRIDDriver"); 
			Connection con = DriverManager.getConnection("jdbc:default:connection:"); 

			if (con instanceof CUBRIDConnection) {
				((CUBRIDConnection)con).setCharset("euc_kr");
			}
			
			String query = sql; 
			Statement stmt = con.createStatement(); 
			ResultSet rs = stmt.executeQuery(query);
			if (rs instanceof CUBRIDResultSet) {
				((CUBRIDResultSet)rs).setReturnable();
			}
			return rs; 
		} catch (Exception e) { 
			//e.printStackTrace(); 
		} 
		return null; 
	} 
	
	public static String testresult1 () {
		String ret = "";
		try {
			Class.forName("cubrid.jdbc.driver.CUBRIDDriver"); 
			Connection con = DriverManager.getConnection("jdbc:default:connection:"); 
			if (con instanceof CUBRIDConnection) {
				((CUBRIDConnection)con).setCharset("euc_kr");
			}

			CallableStatement cstmt = con.prepareCall("? = CALL testResultSet1()");
			cstmt.registerOutParameter(1, Types.JAVA_OBJECT); 
			cstmt.execute(); 
			ResultSet rs = (ResultSet) cstmt.getObject(1); 
	
			while(rs.next()) { 
				ret = ret + rs.getString(1)+ " || "; 
				ret = ret + rs.getString(2)+ " || "; 
			} 
			rs.close(); 

		} catch (Exception e){
			//e.printStackTrace(); 
		}
		return ret;
	}

	public static ResultSet testResultSet1() { 
		try { 
			Class.forName("cubrid.jdbc.driver.CUBRIDDriver"); 
			Connection con = DriverManager.getConnection("jdbc:default:connection:"); 

			if (con instanceof CUBRIDConnection) {
				((CUBRIDConnection)con).setCharset("euc_kr");
			}
			String query = "select * from kor order by id,name"; 
			Statement stmt = con.createStatement(); 
			ResultSet rs = stmt.executeQuery(query);
			if (rs instanceof CUBRIDResultSet) {
				((CUBRIDResultSet)rs).setReturnable();
			}
			return rs;
		} catch (Exception e) { 
			//e.printStackTrace(); 
		} 
                return null;
	} 
	
	public static String testresult10 () {
		String ret = "";
		try {
			Class.forName("cubrid.jdbc.driver.CUBRIDDriver"); 
			Connection con = DriverManager.getConnection("jdbc:default:connection:"); 
			if (con instanceof CUBRIDConnection) {
				((CUBRIDConnection)con).setCharset("euc_kr");
			}
			
			CallableStatement cstmt = con.prepareCall("? = CALL testResultSet10()"); 
			cstmt.registerOutParameter(1, Types.JAVA_OBJECT); 
			cstmt.execute(); 
			ResultSet rs = (ResultSet) cstmt.getObject(1); 
			
			while(rs.next()) { 
				ret = ret + rs.getString(1)+ " || "; 
				ret = ret + rs.getString(2)+ " || "; 
			} 
			rs.close(); 
		} catch (Exception e){
			//e.printStackTrace(); 
		}
		return ret;
	}

	public static ResultSet testResultSet10() { 
		try { 
			Class.forName("cubrid.jdbc.driver.CUBRIDDriver"); 
			Connection con = DriverManager.getConnection("jdbc:default:connection:"); 
			if (con instanceof CUBRIDConnection) {
				((CUBRIDConnection)con).setCharset("euc_kr");
			}

			String query = "select * from kor order by id,name"; 
			Statement stmt = con.createStatement(); 
			ResultSet rs = stmt.executeQuery(query); 
			return rs; 
		} catch (Exception e) { 
			//e.printStackTrace(); 
		} 
		return null; 
	} 
 
}
