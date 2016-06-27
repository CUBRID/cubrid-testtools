package com.navercorp.cubridqa.shell.common;


public class WinShellInput extends ShellInput{
	
	public WinShellInput() {
		super(null, null, true);
	}
	
	public WinShellInput(String cmd) {
		super(cmd, null, true);
	}
}
