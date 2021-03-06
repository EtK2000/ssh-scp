package com.etk2000.sealed.config;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;

import com.etk2000.sealed.keys.KeySupplier;
import com.etk2000.sealed.platform.Platform;
import com.etk2000.sealed.service.ServiceCloud;
import com.etk2000.sealed.service.ServiceDynamicIP;
import com.etk2000.sealed.service.ServiceJenkins;
import com.etk2000.sealed.service.exec.ServiceExec;
import com.etk2000.sealed.util.Util;
import com.google.cloud.Tuple;
import com.google.common.io.Files;
import com.google.gson.stream.JsonReader;

public class Config {
	private static final String[] EMPTY_LSTR = {};
	private static final File FILE = new File(Platform.dir(), "config.json");

	private static final List<ServiceCloud> clouds = new ArrayList<>();
	private static final List<ServiceDynamicIP> dynamicIPs = new ArrayList<>();
	private static final List<ServiceExec> execs = new ArrayList<>();
	private static final List<Server> servers = new ArrayList<>();
	private static KeySupplier keySupplier;
	private static String[] officeIPs = EMPTY_LSTR;

	public static synchronized void delete() {
		FILE.delete();
	}

	public static synchronized void load(String file) throws IOException {
		synchronized (servers) {
			if (FILE.isDirectory())
				Util.delete(FILE);
			
			if (file != null) {
				File f = new File(file);
				if (f.exists()) {
					FILE.getParentFile().mkdirs();
					Files.copy(f, FILE);
				}
				else
					System.err.println("failed to find supplied config '" + f.getAbsolutePath() + '\'');
			}

			// open file selection dialog if no config exists
			if (!FILE.exists()) {
				
				// FIXME: add a headless way to ask for file (ask for path in stdin)
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
											case "cloud": {
												clouds.clear();
												jr.beginObject();
												{
													while (jr.hasNext())
														clouds.add(ServiceCloud.read(jr.nextName(), jr));
												}
												jr.endObject();
												break;
											}
											case "dynamic_ip": {
												dynamicIPs.clear();
												jr.beginObject();
												{
													while (jr.hasNext())
														dynamicIPs.add(ServiceDynamicIP.read(jr.nextName(), jr));
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
												new ServiceJenkins(jr);// .interact();
												// System.out.println("^ JENKINS TEST ^");
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
	}

	public static synchronized Stream<ServiceCloud> clouds() {
		return clouds.stream();
	}

	public static synchronized Stream<ServiceExec> execs() {
		return execs.stream();
	}

	public static synchronized KeySupplier getKeySupplier() {
		return keySupplier;
	}

	public static Server getServer(String name) {
		synchronized (servers) {
			for (Server server : servers) {
				if (server.name.equals(name))
					return server;
			}
			return null;
		}
	}

	public static synchronized boolean isOfficeIP(String ip) {
		return Arrays.binarySearch(officeIPs, ip) >= 0;
	}

	public static synchronized Stream<Server> servers() {
		synchronized (servers) {
			return servers.stream();
		}
	}

	public static synchronized void updateDynamicIPs() {
		// FIXME: have some kind of visual indicator
		
		List<Tuple<Server, String>> newIPs = Collections.synchronizedList(new ArrayList<>());
		List<Thread> ts = new ArrayList<>(dynamicIPs.size());
		for (ServiceDynamicIP dynamicIP : dynamicIPs) {
			// FIXME: figure out how to deal with conflicts
			Thread t = new Thread(() -> newIPs.addAll(dynamicIP.fetchIPs()));
			t.start();
			ts.add(t);
		}

		// wait for lookup to complete
		ts.forEach(t -> Util.ignoreInterrupt(t::join));

		for (Tuple<Server, String> e : newIPs) {
			e.x().address = e.y();
			System.out.println("updated IP for '" + e.x().name + '\'');
		}
	}
}