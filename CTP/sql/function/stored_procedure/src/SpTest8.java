import java.sql.*;
import java.util.*;
public class SpTest8 {
	public static String SP() {
		String          URL = null;
		Connection      conn = null;
		PreparedStatement       pstmt = null;
		ResultSet       rs = null;
		String          sql = null;
		try {
			Class.forName("cubrid.jdbc.driver.CUBRIDDriver");
			conn = DriverManager.getConnection("jdbc:default:connection:");
			pstmt = conn.prepareStatement("select class_name from db_class");
			rs = pstmt.executeQuery();
			if (rs.next()) {
				sql = rs.getString("class_name");
			}
			rs.close();
			pstmt.close();
			conn.close();
		} catch (Exception e) {
			//e.printStackTrace();                
		} finally {
			try{
				if (rs != null) rs.close();
				if (pstmt != null) pstmt.close();
				if (conn != null) conn.close();
			} catch (Exception e) {
				//e.printStackTrace();
			}
			return sql;
		}
	}

	public static String SP2() {
		String          URL = null;
		Connection      conn = null;
		PreparedStatement       pstmt = null;
		ResultSet       rs = null;
		String          sql = null;
		try {
			Class.forName("cubrid.jdbc.driver.CUBRIDDriver");
			conn = DriverManager.getConnection("jdbc:default:connection:");
			pstmt = conn.prepareStatement("select class_name from db_class");
			rs = pstmt.executeQuery();
			if (rs.next()) {
				sql = rs.getString("class_name");
			}
			rs.close();
			pstmt.close();
			conn.close();
		} catch (Exception e) {
			//e.printStackTrace();
		} finally {
			try {
				if (rs != null) rs.close();
				if (pstmt != null) pstmt.close();
				if (conn != null) conn.close();
				if (conn != null) conn.close();
				if (conn != null) conn.close();
			} catch (Exception e) {
				//e.printStackTrace();
			}
			return sql;
		}
	}
}
