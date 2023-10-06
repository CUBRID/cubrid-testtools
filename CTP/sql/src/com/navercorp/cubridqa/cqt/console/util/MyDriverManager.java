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
package com.navercorp.cubridqa.cqt.console.util;

import com.navercorp.cubridqa.cqt.console.ConsoleAgent;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.Driver;
import java.sql.SQLException;
import java.util.Properties;

public class MyDriverManager {

    private static Connection connection;

    private static URLClassLoader classLoader = null;

    private static Class driverClass = null;

    private static Driver driver = null;

    private static Properties info = new Properties();

    private static String filePath = "";

    private static String version = null;

    /**
     * load the jdbc driver specified in the configuration file and get the connection .
     *
     * @param drivers
     * @param dbUrl
     * @param user
     * @param password
     * @return
     */
    public static Connection giveConnection(
            String drivers, String dbUrl, String user, String password) {
        try {
            driverClass = Class.forName(drivers, true, getURLClassLoader());
            driver = null;
            driver = (Driver) driverClass.newInstance();

            if (user != null) {
                info.put("user", user);
            }
            if (password != null) {
                info.put("password", password);
            }
            connection = driver.connect(dbUrl, info);
            DatabaseMetaData metaData = connection.getMetaData();
            version = metaData.getDatabaseProductVersion();
        } catch (SQLException e) {
            e.printStackTrace();
            ConsoleAgent.addMessage(e.getMessage());
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            ConsoleAgent.addMessage("-10002" + System.getProperty("line.separator"));
        } catch (InstantiationException e) {
            e.printStackTrace();
            ConsoleAgent.addMessage("-10002" + System.getProperty("line.separator"));
        } catch (IllegalAccessException e) {
            e.printStackTrace();
            ConsoleAgent.addMessage("-10002" + System.getProperty("line.separator"));
        } catch (Exception e) {
            e.printStackTrace();
            ConsoleAgent.addMessage("cubrid exception" + System.getProperty("line.separator"));
        }
        return connection;
    }

    public static String getDatabaseVersion() {
        return version;
    }

    /**
     * get the url classloader through jdbc path .
     *
     * @return
     */
    public static URLClassLoader getURLClassLoader() {
        try {
            String path = EnvGetter.getenv("CUBRID") + File.separator + "jdbc/cubrid_jdbc.jar";
            File file = new File(path);
            @SuppressWarnings("deprecation")
            URL url = file.toURL();
            if (!filePath.equals(path)) {
                classLoader = null;
                classLoader = new URLClassLoader(new URL[] {url}, null);
                filePath = path;
            }
        } catch (MalformedURLException e) {
            ConsoleAgent.addMessage("-10002" + System.getProperty("line.separator"));
            e.printStackTrace();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return classLoader;
    }
}
