package com.etk2000.sealed.keys;

import java.io.IOException;

import com.etk2000.sealed.config.Config;

public class AuthKey {
	public final String name;
	protected String path;
	
	public AuthKey(String name) throws IOException {
		AuthKey key = Config.getKeySupplier().getKey(name);
		if (key == null)
			throw new IOException("key '" + name + "' not found");
		
		this.name = name;
		this.path = key.path;
	}

	AuthKey(String name, String path) {
		this.name = name;
		this.path = path;
	}
	
	public String path() {
		return path;
	}
}