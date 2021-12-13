package com.etk2000.sealed.util;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Array;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Scanner;

import javax.net.ssl.HttpsURLConnection;

import com.etk2000.sealed.config.Config;

public class Util {
	@SuppressWarnings("unchecked")
	public static <T> T[] copyAndMerge(T[] a, T[] b) {
		if (a.length == 0 || b.length == 0)
			throw new IllegalArgumentException("both arrays must have elements");
		
		T[] res = (T[]) Array.newInstance(a.getClass().getComponentType(), a.length + b.length);
		System.arraycopy(a, 0, res, 0, a.length);
		System.arraycopy(b, 0, res, a.length, b.length);
		return res;
	}
	
	public static void delete(File f) {
		if (f.isFile())
			f.delete();
		else if (f.isDirectory()) {
			for (File child : f.listFiles())
				delete(child);
			f.delete();
		}
	}

	public static boolean download(String url, File dst) {
		try (ReadableByteChannel rbc = Channels.newChannel(new URL(url).openStream())) {
			try (FileOutputStream fos = new FileOutputStream(dst)) {
				fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
				return true;
			}
		}
		catch (IOException e) {
			e.printStackTrace();
			return false;
		}
	}

	public static boolean isInOffice() throws IOException {
		return Config.isOfficeIP(Util.urlGET("https://checkip.amazonaws.com/"));
	}

	public static boolean isUsingVPN() {
		try {
			for (NetworkInterface nint : Collections.list(NetworkInterface.getNetworkInterfaces())) {
				if (nint.getDisplayName().toLowerCase().contains("fortinet")) {
					for (InetAddress addr : Collections.list(nint.getInetAddresses())) {
						if (addr.getHostAddress().indexOf('.') != -1)
							return true;
					}
				}
			}
		}
		catch (SocketException e) {
			e.printStackTrace();
		}
		return false;
	}

	@SafeVarargs
	public static void run(String... command) throws IOException {
		if (command.length == 0)
			return;// TODO: err out
		new ProcessBuilder(command).start();
	}

	@SafeVarargs
	public static String runForResult(boolean inheritIO, String... command) {
		if (command.length == 0)
			return null;// TODO: err out
		
		try {
			// if IO is to be inherited, set that up
			if (inheritIO) {
				new ProcessBuilder(command).inheritIO().start().waitFor();
				return null;
			}
			
			// otherwise, run normally
			Process p = new ProcessBuilder(command).start();
			p.waitFor();
			try (Scanner s = new Scanner(p.getInputStream()).useDelimiter("\\Z")) {
				return s.hasNext() ? s.next() : "";
			}
		}
		catch (InterruptedException | IOException e) {
			e.printStackTrace();
			return null;
		}
	}

	public static void ignoreInterrupt(ExceptableRunnable<InterruptedException> r) {
		try {
			r.run();
		}
		catch (InterruptedException e) {
		}
	}

	public static String urlGET(String url) throws IOException {
		try (Scanner s = new Scanner(new URL(url).openStream()).useDelimiter("\\Z")) {
			return s.hasNext() ? s.next() : "";
		}
	}

	public static void urlGET(String url, Map<String, String> extraHeaders, File output) throws IOException {
		HttpsURLConnection con = (HttpsURLConnection) new URL(url).openConnection();
		extraHeaders.forEach(con::addRequestProperty);
		try (FileOutputStream fos = new FileOutputStream(output)) {
			con.getInputStream().transferTo(fos);
		}
	}

	public static String urlPOST(String url, String data) throws IOException {
		HttpsURLConnection con = (HttpsURLConnection) new URL(url).openConnection();
		con.setDoOutput(true);
		con.getOutputStream().write(data.getBytes(UTF_8));

		try (Scanner s = new Scanner(con.getInputStream()).useDelimiter("\\Z")) {
			return s.hasNext() ? s.next() : "";
		}
	}
}