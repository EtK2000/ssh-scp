package com.etk2000.ssh_scp.headless;

import com.etk2000.ssh_scp.service.exec.ExecLog;

public class ConsoleExecLog implements ExecLog {
	@Override
	public void append(LogColor c, String s) {
		System.out.println(c + s);
	}

	@Override
	public void appendANSI(String s) {
		System.out.println(s);
	}

	@Override
	public void appendANSI(String s, LogColor base) {
		System.out.println(base + s);
	}
}