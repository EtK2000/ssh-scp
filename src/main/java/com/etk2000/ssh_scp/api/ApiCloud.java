package com.etk2000.ssh_scp.api;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import com.etk2000.ssh_scp.util.Util;
import com.google.gson.stream.JsonWriter;

public class ApiCloud {
	private static final Map<String, ServiceCloud> CLOUDS = new HashMap<>();

	public static synchronized void clear() {
		Util.guardInternalAPI();
		CLOUDS.clear();
	}

	public static ServiceCloud get(String name) {
		return CLOUDS.get(name);
	}

	public static synchronized void register(String name, ServiceCloud cloud) {
		if (get(name) != null)
			throw new IllegalArgumentException("Already have a cloud by name '" + name + '\'');
		CLOUDS.put(name, cloud);
	}

	public static synchronized Stream<ServiceCloud> clouds() {
		return CLOUDS.values().stream();
	}

	public static synchronized void write(JsonWriter jw) throws IOException {
		if (CLOUDS.size() > 0) {
			jw.name("cloud").beginObject();
			{
				for (ServiceCloud cloud : CLOUDS.values())
					cloud.write(jw);
			}
			jw.endObject();
		}
	}
}