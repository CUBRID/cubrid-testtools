/**
 * Copyright 2016 CUBRID Corporation
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
import java.io.*;

public class SpTest9 {
    public static String testblob() {
        Connection conn = null;
        Blob blob = null;
        String result = null;

        try {
                conn = DriverManager.getConnection("jdbc:default:connection:", "", "");
                try {
                        blob = conn.createBlob();
                } catch (SQLException e) {
                        if (e.getCause() instanceof UnsupportedOperationException) {
                                result = "UnsupportedOperationException";
                        }
                }

        } catch (SQLException e) {
        } finally {
                if (conn != null) try { conn.close(); } catch (Exception e) {}
                return result;
        }
    }

    public static String testblob1() {
        Connection conn = null;
        PreparedStatement pstmt = null;
        Statement stmt = null;
        String result = null;

        try {
                conn = DriverManager.getConnection("jdbc:default:connection:", "", "");
                stmt = conn.createStatement();
                stmt.execute("drop table if exists test");
                stmt.execute("create table test (a blob)");

                pstmt = conn.prepareStatement("insert into test values (?)");
                try {
                        pstmt.setBlob(1, new ByteArrayInputStream("abcd".getBytes()));
                } catch (SQLException e) {
                        if (e.getCause() instanceof UnsupportedOperationException) {
                                result = "UnsupportedOperationException";
                        }
                }
        } catch (SQLException e) {
        } finally {
                if (stmt != null) try { stmt.execute("drop table if exists test");stmt.close(); } catch (Exception e) {}
                if (pstmt != null) try { pstmt.close(); } catch (Exception e) {}
                if (conn != null) try { conn.close(); } catch (Exception e) {}
                return result;
        }

    }

    public static String testblob2() {
        Connection conn = null;
        Statement stmt = null;
        String result = null;
        ResultSet rs = null;

        try {
                conn = DriverManager.getConnection("jdbc:default:connection:", "", "");
                stmt = conn.createStatement();
                stmt.execute("drop table if exists test");
                stmt.execute("create table test (a blob)");
		stmt.execute("insert into test values (CHAR_TO_BLOB('0123456'))");
                rs = stmt.executeQuery("select * from test");
                rs.next();
                try {
                        Blob blob = rs.getBlob("a");
                } catch (SQLException e) {
                        if (e.getCause() instanceof UnsupportedOperationException) {
                                result = "UnsupportedOperationException";
                        }
                }
        } catch (SQLException e) {
        } finally {
                if (stmt != null) try { stmt.execute("drop table if exists test"); stmt.close(); } catch (Exception e) {}
                if (conn != null) try { conn.close(); } catch (Exception e) {}
                return result;
        }

    }

    public static String testclob() {
        Connection conn = null;
        Clob clob = null;
        String result = null;

        try {
                conn = DriverManager.getConnection("jdbc:default:connection:", "", "");
                try {
                        clob = conn.createClob();
                } catch (SQLException e) {
                        if (e.getCause() instanceof UnsupportedOperationException) {
                                result = "UnsupportedOperationException";
                        }
                }
        } catch (SQLException e) {
        } finally {
                if (conn != null) try { conn.close(); } catch (Exception e) {}
                return result;
        }
    }

    public static String testclob1() {
        Connection conn = null;
        PreparedStatement pstmt = null;
        Statement stmt = null;
        String result = null;

        try {
                conn = DriverManager.getConnection("jdbc:default:connection:", "", "");
                stmt = conn.createStatement();
                stmt.execute("drop table if exists test");
                stmt.execute("create table test (a clob)");

                pstmt = conn.prepareStatement("insert into test values (?)");
                try {
                        pstmt.setClob(1, new StringReader("abcd"));
                } catch (SQLException e) {
                        if (e.getCause() instanceof UnsupportedOperationException) {
                                result = "UnsupportedOperationException";
                        }
                }
        } catch (SQLException e) {
        } finally {
                if (stmt != null) try { stmt.execute("drop table if exists test");stmt.close(); } catch (Exception e) {}
                if (pstmt != null) try { pstmt.close(); } catch (Exception e) {}
                if (conn != null) try { conn.close(); } catch (Exception e) {}
                return result;
        }

    }

    public static String testclob2() {
        Connection conn = null;
        Statement stmt = null;
        String result = null;
        ResultSet rs = null;

        try {
                conn = DriverManager.getConnection("jdbc:default:connection:", "", "");
                stmt = conn.createStatement();
                stmt.execute("drop table if exists test");
                stmt.execute("create table test (a clob)");
                stmt.execute("insert into test values (CHAR_TO_CLOB('0123456'))");
                rs = stmt.executeQuery("select * from test");
                rs.next();
                try {
                        Clob clob = rs.getClob("a");
                } catch (SQLException e) {
                        if (e.getCause() instanceof UnsupportedOperationException) {
                                result = "UnsupportedOperationException";
                        }
                }
        } catch (SQLException e) {
        } finally {
                if (stmt != null) try { stmt.execute("drop table if exists test"); stmt.close(); } catch (Exception e) {}
                if (conn != null) try { conn.close(); } catch (Exception e) {}
                return result;
        }

    }

    public static String testresultset() {
        Connection conn = null;
        Statement stmt = null, other_stmt = null;
        ResultSet res = null;
        String result = null;

        try {
                conn = DriverManager.getConnection("jdbc:default:connection:", "", "");
                stmt = conn.createStatement();
                res = stmt.executeQuery("select * from ttbl");
                res.next();
                try {
                        res.last();
                } catch (Exception e) {
                        result = "non-scrollable";
                }

                try {
                        res.updateInt(1,10);
                } catch (Exception e) {
                        result += " non-updatable";
                }

                other_stmt = conn.createStatement();
                other_stmt.execute("update ttbl set id = id + 100");
                conn.commit();

                if (res.getInt(1) == 1) {
                        result += " non-sensitive";
                }
        } catch (SQLException e) {

        } finally {
                if (stmt != null) try { stmt.close(); } catch (Exception e) {}
                if (other_stmt != null) try { other_stmt.close(); } catch (Exception e) {}
                if (conn != null) try { conn.close(); } catch (Exception e) {}
                return result;
        }
   }

   public static String testcatalog() {
        Connection conn = null;
        String result = null;

        try {
                conn = DriverManager.getConnection("jdbc:default:connection:", "", "");
                try {
                        conn.setCatalog("basic");
                        result = conn.getCatalog();
                } catch (SQLException e) {
                        result = e.getMessage();
                }

        } catch (SQLException e) {
        } finally {
                if (conn != null) try { conn.close(); } catch (Exception e) {}
                return result;
        }
    }

    public static String testtransactionisolation() {
        Connection conn = null;
        String result = null;

        try {
                conn = DriverManager.getConnection("jdbc:default:connection:", "", "");
                try {
                        conn.setTransactionIsolation(4);
                        result = String.valueOf(conn.getTransactionIsolation());
                } catch (SQLException e) {
                        result = e.getMessage();
                }
        } catch (SQLException e) {
        } finally {
                if (conn != null) try { conn.close(); } catch (Exception e) {}
                return result;
        }

    }

    public static String testautocommit() {
        Connection conn = null;
        String result = null;

        try {
                conn = DriverManager.getConnection("jdbc:default:connection:", "", "");
                try {
                        conn.setAutoCommit(true);
                        result = String.valueOf(conn.getAutoCommit());
                } catch (SQLException e) {
                        result = e.getMessage();
                }
        } catch (SQLException e) {
        } finally {
                if (conn != null) try { conn.close(); } catch (Exception e) {}
                return result;
        }

    }
}
