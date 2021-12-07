package com.etk2000.sealed.util;

@FunctionalInterface
public interface ExceptableRunnable<E extends Exception> {
	void run() throws E;
}