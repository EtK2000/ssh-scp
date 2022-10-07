package com.etk2000.ssh_scp.api;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import com.etk2000.ssh_scp.cloudprovider.ServiceAWS;
import com.etk2000.ssh_scp.cloudprovider.ServiceGoogleCloud;
import com.etk2000.ssh_scp.platform.Platform;
import com.etk2000.ssh_scp.service.ServiceExec;
import com.etk2000.ssh_scp.util.Util;
import com.google.cloud.Tuple;
import com.google.gson.stream.JsonReader;

public class ServiceLoader {
	private static final Map<String, Tuple<ServiceFactory, Consumer<? extends List<? extends Service>>>> INTERNAL_SERVICES = new HashMap<>(),
			EXTERNAL_SERVICES = new HashMap<>();
	private static ServiceClassLoader classLoader;
	private static boolean loadingExternalServices;

	public static synchronized void configureInternalServices(Consumer<List<ServiceExec>> onReadServiceExecs) {
		Util.guardInternalAPI();

		INTERNAL_SERVICES.clear();
		INTERNAL_SERVICES.put("aws", Tuple.of(ServiceAWS::read, null));
		INTERNAL_SERVICES.put("exec", Tuple.of(ServiceExec::new, onReadServiceExecs));
		INTERNAL_SERVICES.put("google", Tuple.of(ServiceGoogleCloud::read, null));

		/*
		 * case "jenkins": { new ServiceJenkins(jr);// .interact(); //
		 * System.out.println("^ JENKINS TEST ^");
		 */
	}

	private static boolean isValidPlugin(Class<?> clazz) {
		try {
			return !Modifier.isAbstract(clazz.getModifiers()) && !clazz.isInterface()//
					&& AbstractPlugin.class.isAssignableFrom(clazz) && Modifier.isPublic(clazz.getConstructor().getModifiers());
		}
		catch (ReflectiveOperationException e) {
			return false;
		}
	}

	// comb through files in supplied JAR and return <? extends AbstractPlugin>
	private static List<String> getJarPlugins(File file) throws IOException {
		List<String> res = new ArrayList<>();

		try (JarFile jar = new JarFile(file); ServiceClassLoader tmpClassLoader = new ServiceClassLoader(file)) {
			for (Enumeration<JarEntry> entries = jar.entries(); entries.hasMoreElements();) {
				JarEntry entry = entries.nextElement();
				if (entry.getName().endsWith(".class")) {
					try {
						String classFile = entry.getName().replace('/', '.');
						classFile = classFile.substring(0, classFile.length() - 6);// remove ".class"
						if (isValidPlugin(tmpClassLoader.loadClass(classFile)))
							res.add(classFile);
					}
					catch (NoClassDefFoundError e) {// ignore issues with classes not being able to load
					}
					catch (Exception e) {
						e.printStackTrace();// FIXME: handle
					}
				}
			}
		}

		return res;
	}

	public static synchronized void loadExternalServices() {
		Util.guardInternalAPI();
		EXTERNAL_SERVICES.clear();

		if (classLoader != null) {
			try {
				classLoader.close();
			}
			catch (IOException e) {
				e.printStackTrace();
			}
		}

		classLoader = new ServiceClassLoader();
		List<String> plugins = new ArrayList<>();

		// attempt to load all JAR files in ${dir}/services/
		// and dynamically find all services in them
		for (File file : Platform.dirServices().listFiles()) {
			if (file.isFile() && file.getName().toLowerCase().endsWith(".jar")) {
				try {
					List<String> pluginPaths = getJarPlugins(file);
					if (pluginPaths.size() > 0) {
						classLoader.addURL(file.toURI().toURL());
						plugins.addAll(pluginPaths);
					}
				}
				catch (IOException e) {
					e.printStackTrace();
				}
			}
		}

		// attempt to dynamically load all services
		try {
			loadingExternalServices = true;
			for (String pluginClass : plugins) {
				Class<?> clazz = classLoader.loadClass(pluginClass);
				AbstractPlugin plugin = (AbstractPlugin) clazz.getConstructor().newInstance();
				plugin.onLoad();
			}
		}
		catch (ReflectiveOperationException e) {
			e.printStackTrace();
		}
		finally {
			loadingExternalServices = false;
		}
	}

	@SuppressWarnings("unchecked")
	public static synchronized void readService(String name, JsonReader jr) throws IOException {

		// prefer internal for security reasons
		Tuple<ServiceFactory, Consumer<? extends List<? extends Service>>> serviceT = INTERNAL_SERVICES.get(name);
		if (serviceT == null) {
			serviceT = EXTERNAL_SERVICES.get(name);
			if (serviceT == null)
				throw new IllegalArgumentException("no service by name '" + name + '\'');
		}

		// if found, create the service instances and notify listener if supplied
		List<Service> res = new ArrayList<>();
		jr.beginArray();
		{
			try {
				while (jr.hasNext())
					res.add(serviceT.x().apply(jr));
			}
			catch (MissingFieldsException e) {
				// FIXME: detect instead of reallocating
				throw new MissingFieldsException(name);
			}
		}
		jr.endArray();
		if (serviceT.y() != null)
			((Consumer<List<Service>>) serviceT.y()).accept(res);
	}

	public static synchronized void registerExternalService(String name, ServiceFactory factory) {
		registerExternalService(name, factory, null);
	}

	public static synchronized void registerExternalService(String name, ServiceFactory factory, Consumer<List<Service>> onServiceRead) {
		if (!loadingExternalServices)
			throw new IllegalStateException("cannot currently register an external service");

		// FIXME: deal with collisions?
		EXTERNAL_SERVICES.put(name, Tuple.of(factory, onServiceRead));
	}
}

class ServiceClassLoader extends URLClassLoader {
	ServiceClassLoader() {
		super(new URL[] {}, ServiceClassLoader.class.getClassLoader());
	}

	ServiceClassLoader(File file) throws MalformedURLException {
		super(new URL[] { file.toURI().toURL() }, ServiceClassLoader.class.getClassLoader());
	}

	@Override
	public void addURL(URL url) {
		super.addURL(url);
	}
}