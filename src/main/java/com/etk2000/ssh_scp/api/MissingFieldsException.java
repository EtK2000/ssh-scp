package com.etk2000.ssh_scp.api;

import java.io.IOException;

@SuppressWarnings("serial")
public class MissingFieldsException extends IOException {
	public MissingFieldsException() {
		// FIXME: detect instead of reallocating
	}

	public MissingFieldsException(String service) {
		super("missing field(s) for service of type '" + service + '\'');
	}

	@Override
	public synchronized Throwable fillInStackTrace() {
		return this;
	}
}