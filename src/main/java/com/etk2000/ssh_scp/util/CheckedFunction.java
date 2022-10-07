package com.etk2000.ssh_scp.util;

@FunctionalInterface
public interface CheckedFunction<T, R, E extends Exception> {
   R apply(T t) throws E;
}