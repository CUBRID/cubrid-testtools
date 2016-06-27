package com.navercorp.cubridqa.shell.service;
import java.rmi.*;

public interface ShellService extends Remote {

	public String exec(String user, String pwd, String scripts) throws Exception;

	public String exec(String user, String pwd, String scripts, boolean pureWindows) throws Exception;
}
