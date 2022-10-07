package com.etk2000.ssh_scp.api;

import java.io.IOException;

import com.google.gson.stream.JsonWriter;

public interface Service {
	void write(JsonWriter jw) throws IOException;
}