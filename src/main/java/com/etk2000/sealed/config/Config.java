package com.etk2000.sealed.config;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;

import com.etk2000.sealed.dynamic_ip.DynamicIP;
import com.etk2000.sealed.keys.KeySupplier;
import com.etk2000.sealed.platform.Platform;
import com.etk2000.sealed.service.ServiceJenkins;
import com.etk2000.sealed.service.exec.ServiceExec;
import com.etk2000.sealed.util.Util;
import com.google.common.io.Files;
import com.google.gson.stream.JsonReader;

public class Config {
	private static final String[] EMPTY_LSTR = {};
	private static final File FILE = new File(Platform.dir(), "config.json");

	private static final List<DynamicIP> dynamicIPs = new ArrayList<>();
	private static final List<ServiceExec> execs = new ArrayList<>();
	private static final List<Server> servers = new ArrayList<>();
	private static KeySupplier keySupplier;
	private static String[] officeIPs = EMPTY_LSTR;
	
	public static synchronized void delete() {
		FILE.delete();
	}

	public static synchronized void load() throws IOException {
		if (FILE.isDirectory())
			Util.delete(FILE);

		// open file selection dialog if no config exists
		if (!FILE.exists()) {
			JFileChooser jfc = new JFileChooser();
			jfc.setDialogTitle("Select Config To Import");
			jfc.setFileFilter(new FileNameExtensionFilter("JavaScript Object Notation (*.json)", "json"));
			if (jfc.showOpenDialog(null) != JFileChooser.APPROVE_OPTION)
				return;

			FILE.getParentFile().mkdirs();
			Files.copy(jfc.getSelectedFile(), FILE);
		}
		
		// wipe previous config
		dynamicIPs.clear();
		execs.clear();
		servers.clear();
		keySupplier = null;
		officeIPs = EMPTY_LSTR;

		// attempt to load the config
		try (JsonReader jr = new JsonReader(new InputStreamReader(new FileInputStream(FILE), UTF_8))) {
			jr.beginObject();
			{
				while (jr.hasNext()) {
					switch (jr.nextName()) {
						case "key_supplier": {
							jr.beginObject();
							{
								keySupplier = KeySupplier.read(jr.nextName(), jr);
							}
							jr.endObject();
							break;
						}
						case "office_ips": {
							jr.beginArray();
							{
								List<String> ips = new ArrayList<>();
								while (jr.hasNext())
									ips.add(jr.nextString());
								officeIPs = ips.toArray(EMPTY_LSTR);
							}
							jr.endArray();
							break;
						}
						case "servers": {
							servers.clear();
							jr.beginObject();
							{
								while (jr.hasNext())
									servers.add(new Server(jr.nextName(), jr));
							}
							jr.endObject();
							break;
						}
						case "services": {
							jr.beginObject();
							{
								while (jr.hasNext()) {
									String name = jr.nextName();
									switch (name) {
										case "dynamic_ip": {
											dynamicIPs.clear();
											jr.beginObject();
											{
												while (jr.hasNext())
													dynamicIPs.add(DynamicIP.read(jr.nextName(), jr));
											}
											jr.endObject();
											break;
										}
										case "exec": {
											jr.beginObject();
											{
												while (jr.hasNext())
													execs.add(new ServiceExec(jr));
											}
											jr.endObject();
											break;
										}
										case "jenkins": {
											new ServiceJenkins(jr).interact();
											System.out.println("^ JENKINS TEST ^");
											break;
										}
										default:
											System.err.println("no service by name '" + name + '\'');
											jr.skipValue();
											break;
									}
								}
							}
							jr.endObject();
							break;
						}
						default:
							jr.skipValue();
							break;
					}
				}
			}
			jr.endObject();
		}
	}

	public static synchronized Stream<ServiceExec> execs() {
		return execs.stream();
	}

	public static synchronized KeySupplier getKeySupplier() {
		return keySupplier;
	}

	public static synchronized Server getServer(String name) {
		for (Server server : servers) {
			if (server.name.equals(name))
				return server;
		}
		return null;
	}

	public static synchronized boolean isOfficeIP(String ip) {
		return Arrays.binarySearch(officeIPs, ip) >= 0;
	}

	public static synchronized Stream<Server> servers() {
		return servers.stream();
	}

	public static synchronized void updateDynamicIPs() {
		dynamicIPs.forEach(dip -> dip.fetch((server, ip) -> server.address = ip));
	}
}