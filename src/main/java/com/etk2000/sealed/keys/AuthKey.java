package com.etk2000.sealed.keys;

import com.etk2000.sealed.config.Config;

public class AuthKey {
	public final String name;
	protected String path;
	
	public AuthKey(String name) {
		this.name = name;
		path = Config.getKeySupplier().getKey(name).path;
	}

	AuthKey(String name, String path) {
		this.name = name;
		this.path = path;
	}
	
	public String path() {
		return path;
	}
}