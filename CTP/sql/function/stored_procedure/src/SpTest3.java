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
import java.math.BigDecimal;
import java.sql.*;
import java.text.SimpleDateFormat;

public class SpTest3 {
    private static Date strToDate(String dateStr) {
        try {
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
            return new Date(format.parse(dateStr).getTime());
        } catch (Exception e) {

        }
        return null;
    }

    public static int typetestint0() {
        return 11;
    }

    public static int typetestint1() {
        return -2147483648;
    }

    public static int typetestint2() {
        return 0;
    }

    public static int typetestint3() {
        return 2147483647;
    }

    public static int typetestint4() {
        return -0;
    }

    public static int typetestint5() {
        return 2147483647;
    }

    public static Integer typetestinteger0() {
        Integer j = new Integer(11);
        return j;
    }

    public static Integer typetestinteger1() {
        Integer j = new Integer(-2147483648);
        return j;
    }

    public static Integer typetestinteger2() {
        Integer j = new Integer(0);
        return j;
    }

    public static Integer typetestinteger3() {
        Integer j = new Integer(2147483647);
        return j;
    }

    public static Integer typetestinteger4() {
        Integer j = new Integer(-0);
        return j;
    }

    public static String typeteststring1() {
        String temp = "";
        for (int i = 0; i < 10000; i++) temp = temp + "1234567890";
        return temp;
    }

    public static String typeteststring2() {
        String temp = "";
        for (int i = 0; i < 1; i++) temp = temp + "1234567890";
        return temp;
    }

    public static String typeteststring3() {
        String temp = "1234567890";
        return temp;
    }

    public static Date typetestdate1() {
        return strToDate("2002-01-01");
    }

    public static Date typetestdate2() {
        return strToDate("2002-13-01");
    }

    public static Date typetestdate3() {
        return strToDate("2002-13-40");
    }

    public static Date typetestdate4() {
        return strToDate("2002-01-01");
    }

    public static Time typetesttime1() {
        return Time.valueOf("12:12:12");
    }

    public static Time typetesttime2() {
        return Time.valueOf("0:12:12");
    }

    public static Time typetesttime3() {
        return Time.valueOf("25:12:12");
    }

    public static Time typetesttime4() {
        return Time.valueOf("12:67:12");
    }

    public static Time typetesttime5() {
        return Time.valueOf("12:12:80");
    }

    public static Timestamp typetesttimestamp1() {
        return Timestamp.valueOf("2004-12-12 01:01:01.1111");
    }

    public static Timestamp typetesttimestamp2() {
        return Timestamp.valueOf("5004-12-12 01:01:01.1111");
    }

    public static Timestamp typetesttimestamp3() {
        return Timestamp.valueOf("2005-04-12 01:01:01.1111");
    }

    public static Timestamp typetesttimestamp4() {
        return Timestamp.valueOf("2004-12-30 01:01:01.1111");
    }

    public static Timestamp typetesttimestamp5() {
        return Timestamp.valueOf("2004-12-12 25:01:01.1111");
    }

    public static Timestamp typetesttimestamp6() {
        return Timestamp.valueOf("2004-12-12 01:70:01.1111");
    }

    public static Timestamp typetesttimestamp7() {
        return Timestamp.valueOf("2004-12-12 01:01:77.1111");
    }

    public static Byte typetestbyte1() {
        Byte j = new Byte("1111111111111111111111111111111111111111111111111111111");
        return j;
    }

    public static Short typetestshort1() {
        Short j = new Short("32767");
        return j;
    }

    public static Short typetestshort2() {
        Short j = new Short("-32768");
        return j;
    }

    public static Short typetestshort3() {
        Short j = new Short("0");
        return j;
    }

    public static Short typetestshort4() {
        Short j = new Short("-0");
        return j;
    }

    public static Long typetestlong1() {
        Long j = new Long("12345");
        return j;
    }

    public static Long typetestlong2() {
        Long j = new Long("9223372036854775807");
        return j;
    }

    public static Long typetestlong3() {
        Long j = new Long("-9223372036854775808");
        return j;
    }

    public static Long typetestlong4() {
        Long j = new Long("0");
        return j;
    }

    public static Float typetestfloat1() {
        Float j = new Float("-0");
        return j;
    }

    public static Float typetestfloat2() {
        Float j = new Float("9223372036854775807");
        return j;
    }

    public static Float typetestfloat3() {
        Float j = new Float("-9223372036854775807");
        return j;
    }

    public static Float typetestfloat4() {
        Float j = new Float("12345");
        return j;
    }

    public static Double typetestdouble1() {
        Double j = new Double("0");
        return j;
    }

    public static Double typetestdouble2() {
        Double j = new Double("9223372036854775807");
        return j;
    }

    public static Double typetestdouble3() {
        Double j = new Double("-9223372036854775807");
        return j;
    }

    public static Double typetestdouble4() {
        Double j = new Double("0");
        return j;
    }

    public static BigDecimal typetestbigdecimal1() {
        BigDecimal j = new BigDecimal("0");
        return j;
    }

    public static BigDecimal typetestbigdecimal2() {
        BigDecimal j = new BigDecimal("9223372036854775807");
        return j;
    }

    public static BigDecimal typetestbigdecimal3() {
        BigDecimal j = new BigDecimal("-9223372036854775807");
        return j;
    }

    public static BigDecimal typetestbigdecimal4() {
        BigDecimal j = new BigDecimal("123");
        return j;
    }

    public static short typetestshort5() {
        short j = 11111;
        return j;
    }

    public static short typetestshort6() {
        short j = -11111;
        return j;
    }

    public static short typetestshort7() {
        short j = 11131;
        return j;
    }

    public static short typetestshort8() {
        short j = -12131;
        return j;
    }

    public static long typetestlong5() {
        long j = 12345;
        return j;
    }

    public static long typetestlong6() {
        long j = -2147483647;
        return j;
    }

    public static float typetestfloat5() {
        float j = 2147483647;
        return j;
    }

    public static float typetestfloat6() {
        float j = 0;
        return j;
    }

    public static float typetestfloat7() {
        float j = 12345;
        return j;
    }

    public static float typetestfloat8() {
        float j = -2147483647;
        return j;
    }

    public static double typetestdouble5() {
        double j = 0;
        return j;
    }

    public static double typetestdouble6() {
        double j = 2147483647;
        return j;
    }

    public static double typetestdouble7() {
        double j = -2147483647;
        return j;
    }

    public static double typetestdouble8() {
        double j = 11131313;
        return j;
    }
}
