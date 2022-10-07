package com.etk2000.ssh_scp.config;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.reflect.InvocationTargetException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;

import com.etk2000.ssh_scp.api.ApiCloud;
import com.etk2000.ssh_scp.api.ApiDynamicIP;
import com.etk2000.ssh_scp.api.ServiceLoader;
import com.etk2000.ssh_scp.keys.KeySupplier;
import com.etk2000.ssh_scp.keys.KeySupplierLocal;
import com.etk2000.ssh_scp.platform.Platform;
import com.etk2000.ssh_scp.service.ServiceExec;
import com.etk2000.ssh_scp.util.HeadlessUtil;
import com.etk2000.ssh_scp.util.Util;
import com.google.common.io.Files;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

public class Config {
	private static final String[] EMPTY_LSTR = {};
	private static final List<ServiceExec> execs = new ArrayList<>();
	private static final Map<String, Server> servers = new HashMap<>();
	private static KeySupplier keySupplier;

	@Deprecated
	// needs to be changed to IP blocks instead
	private static String[] officeIPs = EMPTY_LSTR;
	public static final File FILE = new File(Platform.dir(), "config.json");

	static {
		ServiceLoader.configureInternalServices(execs::addAll);
		ServiceLoader.loadExternalServices();
	}

	public static synchronized void delete() {
		FILE.delete();
	}

	public static boolean export(JFrame parent) {
		for (;;) {
			// FIXME: add a headless way to ask for file (ask for path in STDIN)
			JFileChooser jfc = new JFileChooser();
			jfc.setDialogTitle("Select Location To Export");
			jfc.setFileFilter(new FileNameExtensionFilter("JavaScript Object Notation (*.json)", "json"));
			jfc.setAcceptAllFileFilterUsed(false);
			if (jfc.showSaveDialog(parent) != JFileChooser.APPROVE_OPTION)
				return false;
			try {
				File file = jfc.getSelectedFile();
				save(file.getName().endsWith(".json") ? file : new File(file.getParentFile(), file.getName() + ".json"));
				return true;
			}
			catch (IllegalArgumentException | IllegalStateException | IOException e) {
				switch (HeadlessUtil.showConfirmDialog(null,
						"Error saving config, try a different location?\n" + e.getClass().getName() + ": " + e.getMessage(), "Try Again?",
						JOptionPane.YES_NO_OPTION)) {
					case JOptionPane.NO_OPTION:
						return false;
					case JOptionPane.YES_OPTION:
						break;
				}
			}
		}
	}

	public static synchronized void load(String file, JFrame parent) throws IOException {
		synchronized (servers) {
			if (FILE.isDirectory())
				Util.delete(FILE);

			// open file selection dialog if no config exists
			if (file == null || !FILE.exists()) {

				// FIXME: add a headless way to ask for file (ask for path in STDIN)
				try {
					Exception[] refE = new Exception[1];
					Runnable run = () -> {
						try {
							JFileChooser jfc = new JFileChooser();
							jfc.setDialogTitle("Select Config To Import");
							jfc.setFileFilter(new FileNameExtensionFilter("JavaScript Object Notation (*.json)", "json"));
							if (jfc.showOpenDialog(parent) != JFileChooser.APPROVE_OPTION)
								return;

							FILE.getParentFile().mkdirs();
							Files.copy(jfc.getSelectedFile(), FILE);
						}
						catch (Exception e) {
							refE[0] = e;
						}
					};

					if (SwingUtilities.isEventDispatchThread())
						run.run();
					else
						SwingUtilities.invokeAndWait(run);
					if (refE[0] != null)
						throw new IOException(refE[0]);
				}
				catch (InterruptedException | InvocationTargetException e) {
					throw new IOException(e);
				}
			}

			// wipe previous config
			ApiCloud.clear();
			ApiDynamicIP.clear();
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
								jr.beginObject();
								{
									while (jr.hasNext()) {
										String name = jr.nextName();
										if (servers.containsKey(name))
											HeadlessUtil.showMessageDialog(null,
													"Multiple servers by name '" + name + "'!\nOnly the last server will be available!", "Notice",
													JOptionPane.WARNING_MESSAGE);
										servers.put(name, new Server(name, jr));
									}
								}
								jr.endObject();
								break;
							}
							case "services": {
								jr.beginObject();
								{
									while (jr.hasNext())
										ServiceLoader.readService(jr.nextName(), jr);
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

	public static boolean loadWithRetry(String file, JFrame parent) {
		for (;;) {
			try {
				load(file, parent);
				return true;
			}
			catch (IllegalArgumentException | IllegalStateException | IOException e) {
				switch (HeadlessUtil.showConfirmDialog(null, "Error loading config, retry file?\n" + e.getClass().getName() + ": " + e.getMessage(),
						"Try Again?", JOptionPane.YES_NO_CANCEL_OPTION)) {
					case JOptionPane.NO_OPTION:
						delete();
						break;
					case JOptionPane.YES_OPTION:
						break;
					default:
						return false;
				}
			}
		}
	}

	public static synchronized Stream<ServiceExec> execs() {
		return execs.stream();
	}

	public static synchronized KeySupplier getKeySupplier() {
		return keySupplier != null ? keySupplier : KeySupplierLocal.instance;
	}

	public static Server getServer(String name) {
		synchronized (servers) {
			return servers.get(name);// TODO: can be w/o synchronized?
		}
	}

	@Deprecated
	// needs to be changed to IP blocks instead
	public static boolean isOfficeIP() {
		try {
			if (officeIPs.length > 0) {
				String ip = Util.urlGET("https://checkip.amazonaws.com/");
				return Arrays.binarySearch(officeIPs, ip) >= 0;
			}
		}
		catch (IOException e) {
			if (!(e instanceof UnknownHostException))
				e.printStackTrace();
		}
		return false;
	}

	public static synchronized void save(File file) throws IOException {
		synchronized (servers) {
			if (!file.exists())
				file.getParentFile().mkdirs();
			else if (file.isDirectory())
				Util.delete(file);

			try (JsonWriter jw = new JsonWriter(new OutputStreamWriter(new FileOutputStream(file), UTF_8))) {
				jw.setIndent("\t");

				jw.beginObject();
				{
					if (keySupplier != null) {
						jw.name("key_supplier").beginObject();
						{
							keySupplier.write(jw);
						}
						jw.endObject();
					}

					if (officeIPs.length > 0) {
						jw.name("office_ips").beginArray();
						{
							for (String ip : officeIPs)
								jw.value(ip);
						}
						jw.endArray();
					}

					if (servers.size() > 0) {
						jw.name("servers").beginObject();
						{
							for (Server server : servers.values())
								server.write(jw);
						}
						jw.endObject();
					}

					jw.name("services").beginObject();
					{
						ApiCloud.write(jw);
						ApiDynamicIP.write(jw);

						if (execs.size() > 0) {
							jw.name("exec").beginObject();
							{
								for (ServiceExec exec : execs)
									exec.write(jw);
							}
							jw.endObject();
						}

						// TODO: "jenkins" => ServiceJenkins::write(jw)
					}
					jw.endObject();
				}
				jw.endObject();
			}
		}
	}

	public static synchronized Stream<Server> servers() {
		synchronized (servers) {
			return servers.values().stream();
		}
	}
}