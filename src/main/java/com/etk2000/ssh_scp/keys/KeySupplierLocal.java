package com.etk2000.ssh_scp.keys;

import java.io.File;
import java.io.IOException;

import com.etk2000.ssh_scp.platform.Platform;
import com.google.gson.stream.JsonWriter;

public class KeySupplierLocal implements KeySupplier {
	public static final KeySupplierLocal instance = new KeySupplierLocal();
	
	private KeySupplierLocal() {
	}
	
	@Override
	public AuthKey getKey(String name) {
		File key = new File(Platform.dir().getParent() + "/.ssh", name);
		return key.isFile() ? new AuthKey(name, key.getAbsolutePath()) : null;
	}

	@Override
	public void write(JsonWriter jw) throws IOException {
		throw new IllegalAccessError("should never get here");
	}
}