package com.etk2000.ssh_scp.util;

@FunctionalInterface
public interface ExceptableRunnable<E extends Exception> {
	void run() throws E;
}