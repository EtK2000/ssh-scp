package com.etk2000.ssh_scp.api;

import java.io.IOException;

import com.etk2000.ssh_scp.util.CheckedFunction;
import com.google.gson.stream.JsonReader;

public interface ServiceFactory extends CheckedFunction<JsonReader, Service, IOException> {
}