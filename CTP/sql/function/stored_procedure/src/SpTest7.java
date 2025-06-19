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

public class SpTest7 {
    public static int typetestint(int x) {
        return x;
    }

    public static Integer typetestinteger(Integer x) {
        int y = x.intValue();
        Integer z = new Integer(y);
        return z;
    }

    public static String typeteststring(String x) {
        return x;
    }

    public static Short typetestshort(Short x) {
        String y = x.toString();
        Short j = new Short(y);
        return j;
    }

    public static Long typetestlong(Long x) {
        String y = x.toString();
        Long j = new Long(y);
        return j;
    }

    public static Float typetestfloat(Float x) {
        String y = x.toString();
        Float j = new Float(y);
        return j;
    }

    public static Double typetestdouble(Double x) {
        String y = x.toString();
        Double j = new Double(y);
        return j;
    }

    public static BigDecimal typetestbigdecimal(BigDecimal x) {
        String y = x.toString();
        BigDecimal j = new BigDecimal(y);
        return j;
    }

    public static short typetestshort1(short x) {
        return x;
    }

    public static long typetestlong1(long x) {
        return x;
    }

    public static float typetestfloat1(float x) {
        return x;
    }

    public static double typetestdouble1(double x) {
        return x;
    }

    public static void ptypetestint(int[] x) {
        x[0] = x[0] + 1;
    }

    public static void ptypetestinteger(Integer[] x) {
        int y = x[0].intValue() + 1;
        x[0] = new Integer(y);
    }

    public static void ptypeteststring(String[] x) {
        x[0] = x[0] + "1";
    }

    public static void ptypetestshort(Short[] x) {
        int y = x[0].intValue();
        y++;
        Short j = new Short(Integer.toString(y));
        x[0] = j;
    }

    public static void ptypetestlong(Long[] x) {
        int y = x[0].intValue();
        y++;
        Long j = new Long(Integer.toString(y));
        x[0] = j;
    }

    public static void ptypetestfloat(Float[] x) {
        int y = x[0].intValue();
        y++;
        Float j = new Float(Integer.toString(y));
        x[0] = j;
    }

    public static void ptypetestdouble(Double[] x) {
        int y = x[0].intValue();
        y++;
        Double j = new Double(Integer.toString(y));
        x[0] = j;
    }

    public static void ptypetestbigdecimal(BigDecimal[] x) {
        int y = x[0].intValue();
        y++;
        BigDecimal j = new BigDecimal(Integer.toString(y));
        x[0] = j;
    }

    public static void ptypetestshort1(short[] x) {
        x[0]++;
    }

    public static void ptypetestlong1(long[] x) {
        x[0]++;
    }

    public static void ptypetestfloat1(float[] x) {
        x[0]++;
    }

    public static void ptypetestdouble1(double[] x) {
        x[0]++;
    }
}
