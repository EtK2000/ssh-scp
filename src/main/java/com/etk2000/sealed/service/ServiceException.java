package com.etk2000.sealed.service;

import java.io.IOException;

@SuppressWarnings("serial")
public class ServiceException extends IOException {
	public ServiceException(String reason) {
		super(reason);
	}

	@Override
	public synchronized Throwable fillInStackTrace() {
		return this;// skip the costly operation of building a traceback
	}
}