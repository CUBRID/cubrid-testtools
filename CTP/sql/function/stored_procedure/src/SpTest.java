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
import java.sql.*;

public class SpTest {

    public static int testInt10() {
        return 10;
    }

    public static void testInt11(int i[]) {
        i[0] = i[0] + 10;
    }

    public static int testInt(int i) {
        return i + 1;
    }

    /*
     create class dual ( id int) method  class func(int , int) int
     function func file '$CUBRID_DATABASES/subway/java/method.o';
     void func(DB_OBJECT *obj, DB_VALUE *ret, DB_VALUE *val1, DB_VALUE *val2) {
    int i, j;
    i = db_get_int(val);
    j = db_get_int(val);
    db_make_int(ret, i+j);
     }
     */

    public static int testInt(int i, int j) {
        return i + j;
    }

    public static String testInt_2(int i, int j) {
        return i + j + "";
    }

    public static int testInt_1(int i) {
        return i * 1000000;
    }

    public static void testInt_3(int i[]) {
        i[0] = i[0] * 1000000;
    }

    public static int testInt_2(int i) {
        return i * 100;
    }

    public static int testInt(int i, String j) {
        return i + Integer.parseInt(j);
    }

    public static int testInt(int i, float j, java.lang.String k) {

        int a = i + (new Float(j)).intValue() + Integer.parseInt(k);

        return a;
    }

    public static float testFloat(float f) {
        return f + 1;
    }

    public static double testDouble(double d) {
        return d + 1;
    }

    public static String testChar(String c) {
        return c + 1;
    }

    public static String testString(String s) {
        return s = null;
    }

    public static int testArray(int[] a) {
        return a.length;
    }

    public static java.sql.Date testDate(java.sql.Date d) {
        return d;
    }

    public static java.sql.Time testTime(java.sql.Time d) {
        return d;
    }

    public static java.sql.Timestamp testTimestamp(java.sql.Timestamp d) {
        return d;
    }

    public static String Hello(String name) {
        return "Hello, " + name;
    }

    public static int IssueCount(String ip, String port, String dbname, String user, String table) {
        Connection con = null;
        try {

            int i;
            Class.forName("cubrid.jdbc.driver.CUBRIDDriver");
            con =
                    DriverManager.getConnection(
                            "jdbc:cubrid:" + ip + ":" + port + ":" + dbname + ":::", user, "");
            String query = "select count(*) from " + table;
            PreparedStatement pstmt = con.prepareStatement(query);
            ResultSet rs = pstmt.executeQuery();
            rs.next();
            i = rs.getInt(1);
            con.close();
            return i;

        } catch (Exception e) {
            try {
                con.close();
            } catch (Exception ee) {
            }
            //    e.printStackTrace();
        } finally {

        }
        return 0;
    }

    public static int IssueCount1(
            String ip, String port, String dbname, String user, String table) {
        try {
            Class.forName("cubrid.jdbc.driver.CUBRIDDriver");
            Connection conn = DriverManager.getConnection("jdbc:default:connection:", "", "");
            String query = "select count(*) from " + table;
            PreparedStatement pstmt = conn.prepareStatement(query);
            ResultSet rs = pstmt.executeQuery();
            rs.next();
            return rs.getInt(1);
        } catch (Exception e) {
            //	      e.printStackTrace();
        }
        return 0;
    }

    public static int testJdbcCall() {
        int r = 0;
        try {
            Connection con = DriverManager.getConnection("jdbc:default:connection:");

            CallableStatement cstmt = con.prepareCall("? = CALL test_outmode(?)");
            cstmt.registerOutParameter(1, Types.INTEGER);
            cstmt.setInt(2, 1);
            cstmt.registerOutParameter(2, Types.INTEGER);
            cstmt.executeUpdate();
            r = cstmt.getInt(2);
        } catch (Exception e) {
            //	  e.printStackTrace();
        }
        return r;
    }

    public static int testOutMode(int[] a) {
        a[0] = 123;
        return a.length;
    }
}
