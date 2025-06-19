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

import com.thoughtworks.xstream.annotations.XStreamAlias;

@XStreamAlias(value = "system")
public class SystemModel {
    private String systemIp;

    private String loginId;

    private String loginPasswd;

    private String rootPasswd;

    private boolean useMonitor;

    private String cubridHome;

    private String jdbcPath;

    private boolean queryPlan;

    private boolean cpResultToRemoteHost;

    private boolean errorMessage;

    private String remoteHost;

    private String remoteHostUser;
    private String remoteHostPasswd;
    private String remoteHostTargetDir;

    public String getSystemIp() {
        return systemIp;
    }

    public void setSystemIp(String systemIp) {
        this.systemIp = systemIp;
    }

    public String getLoginId() {
        return loginId;
    }

    public void setLoginId(String loginId) {
        this.loginId = loginId;
    }

    public String getLoginPasswd() {
        return loginPasswd;
    }

    public void setLoginPasswd(String loginPasswd) {
        this.loginPasswd = loginPasswd;
    }

    public String getRootPasswd() {
        return rootPasswd;
    }

    public void setRootPasswd(String rootPasswd) {
        this.rootPasswd = rootPasswd;
    }

    public String getCubridHome() {
        return cubridHome;
    }

    public void setCubridHome(String cubridHome) {
        this.cubridHome = cubridHome;
    }

    public boolean isUseMonitor() {
        return useMonitor;
    }

    public void setUseMonitor(boolean useMonitor) {
        this.useMonitor = useMonitor;
    }

    public String getJdbcPath() {
        return jdbcPath;
    }

    public void setJdbcPath(String jdbcPath) {
        this.jdbcPath = jdbcPath;
    }

    public boolean isQueryPlan() {
        return queryPlan;
    }

    public void setQueryPlan(boolean queryPlan) {
        this.queryPlan = queryPlan;
    }

    public String getRemoteHost() {
        return remoteHost;
    }

    public void setRemoteHost(String remoteHost) {
        this.remoteHost = remoteHost;
    }

    public String getRemoteHostUser() {
        return remoteHostUser;
    }

    public void setRemoteHostUser(String remoteHostUser) {
        this.remoteHostUser = remoteHostUser;
    }

    public String getRemoteHostPasswd() {
        return remoteHostPasswd;
    }

    public void setRemoteHostPasswd(String remoteHostPasswd) {
        this.remoteHostPasswd = remoteHostPasswd;
    }

    public String getRemoteHostTargetDir() {
        return remoteHostTargetDir;
    }

    public void setRemoteHostTargetDir(String remoteHostTargetDir) {
        this.remoteHostTargetDir = remoteHostTargetDir;
    }

    public boolean isCpResultToRemoteHost() {
        return cpResultToRemoteHost;
    }

    public void setCpResultToRemoteHost(boolean cpResultToRemoteHost) {
        this.cpResultToRemoteHost = cpResultToRemoteHost;
    }

    public boolean isErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(boolean errorMessage) {
        this.errorMessage = errorMessage;
    }
}
