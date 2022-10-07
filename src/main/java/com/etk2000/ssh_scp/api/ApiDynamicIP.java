package com.etk2000.ssh_scp.api;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.etk2000.ssh_scp.config.Server;
import com.etk2000.ssh_scp.util.Util;
import com.google.cloud.Tuple;
import com.google.gson.stream.JsonWriter;

public class ApiDynamicIP {
	private static final Map<String, ServiceDynamicIP> DYNAMIC_IPS = new HashMap<>();

	public static synchronized void clear() {
		Util.guardInternalAPI();
		DYNAMIC_IPS.clear();
	}

	public static ServiceDynamicIP get(String name) {
		return DYNAMIC_IPS.get(name);
	}

	public static synchronized void register(String name, ServiceDynamicIP dynamicIP) {
		Util.guardInternalAPI();
		if (get(name) != null)
			throw new IllegalArgumentException("Already have a dynamic IP provider by name '" + name + '\'');
		DYNAMIC_IPS.put(name, dynamicIP);
	}

	public static synchronized void updateDynamicIPs() {
		Util.guardInternalAPI();
		
		// FIXME: have some kind of visual indicator

		List<Tuple<Server, String>> newIPs = Collections.synchronizedList(new ArrayList<>());
		List<Thread> ts = new ArrayList<>(DYNAMIC_IPS.size());
		for (ServiceDynamicIP dynamicIP : DYNAMIC_IPS.values()) {
			// FIXME: figure out how to deal with conflicts
			Thread t = new Thread(() -> newIPs.addAll(dynamicIP.fetchIPs()));
			t.start();
			ts.add(t);
		}

		// wait for lookups to complete
		ts.forEach(t -> Util.ignoreInterrupt(t::join));

		for (Tuple<Server, String> e : newIPs) {
			if (e.x().isDynamicIP) {
				e.x().setAddress(e.y());
				System.out.println("updated IP for '" + e.x().name + '\'');
			}
			// LOW: if found by not dynamic warn or something?
		}
	}

	public static synchronized void write(JsonWriter jw) throws IOException {
		if (DYNAMIC_IPS.size() > 0) {
			jw.name("dynamic_ip").beginObject();
			{
				for (ServiceDynamicIP dIP : DYNAMIC_IPS.values())
					dIP.write(jw);
			}
			jw.endObject();
		}
	}
}