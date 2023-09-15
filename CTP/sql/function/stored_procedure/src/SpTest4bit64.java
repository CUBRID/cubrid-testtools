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
import java.util.Calendar;

/*
 *
CREATE   Procedure  parseDatetime(i in string) as language java name 'SpTest4bit64.parseDatetime(java.lang.String)' return DATETIME;
select time'1:1:1 am' into x from db_root;
call ptesttime2(x) ;
call ptesttime2(time'1:1:1 am');
select x from db_root;
drop procedure ptesttime2;
 */
public class SpTest4bit64 {
    public static void main(String[] args) {
        Timestamp dt = Timestamp.valueOf("2009-03-01 12:30:45.999");
        addMonths(dt, 2);

        System.out.println(dt);
    }

    public static Timestamp parseDatetime(String str) {
        Timestamp dt = Timestamp.valueOf(str);
        return dt;
    }

    public static Timestamp newDatetime(int yy, int mm, int dd, int hh, int mi, int ss, int ms) {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.YEAR, yy);
        c.set(Calendar.MONTH, mm - 1);
        c.set(Calendar.DAY_OF_MONTH, dd);
        c.set(Calendar.HOUR_OF_DAY, hh);
        c.set(Calendar.MINUTE, mi);
        c.set(Calendar.SECOND, ss);
        c.set(Calendar.MILLISECOND, ms);

        Timestamp dt = new Timestamp(c.getTime().getTime());

        return dt;
    }

    public static Timestamp addMonths(Timestamp dt, int n) {
        Calendar c = Calendar.getInstance();
        c.setTime(dt);

        c.add(Calendar.MONTH, n);

        dt.setTime(c.getTime().getTime());
        return dt;
    }

    public static void incrementMonth(Timestamp[] dt) {
        Calendar c = Calendar.getInstance();
        c.setTime(dt[0]);

        c.add(Calendar.MONTH, 1);

        dt[0].setTime(c.getTime().getTime());
    }

    public static long parseBigint(String str) {
        long v = Long.parseLong(str);
        return v;
    }

    public static long addBigint(long v1, long v2) {
        return v1 + v2;
    }

    public static void incrementBigint(long[] v) {
        v[0] = v[0] + 1;
    }
}
