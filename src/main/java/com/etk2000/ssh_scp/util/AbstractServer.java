package com.etk2000.ssh_scp.util;

import com.etk2000.ssh_scp.config.Server;
import com.etk2000.ssh_scp.keys.AuthKey;

public interface AbstractServer {
	String address();

	String fingerprint();

	AuthKey key();

	String name();

	String pass();

	Server proxy();

	String user();
}