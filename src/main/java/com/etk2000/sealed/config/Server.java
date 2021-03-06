package com.etk2000.sealed.config;

import java.io.IOException;

import com.etk2000.sealed.keys.AuthKey;
import com.google.gson.stream.JsonReader;

public class Server {
	private static final Object LOCK_IP_UPDATE = new Object();

	public static enum AreaAccess {
		anywhere, office, vpn
	}

	String address;
	private final String proxy;
	public final String category;
	public final String fingerprint, name, user, pass;
	public final AuthKey key;
	public final AreaAccess access;

	Server(String name, String category, AreaAccess access, String proxy, String address, String user, AuthKey key, String fingerprint) {
		this.name = name;
		this.category = category;
		this.access = access;
		this.proxy = proxy;
		this.address = address;
		this.user = user;
		this.key = key;
		this.fingerprint = fingerprint;
		pass = null;
	}

	Server(String name, String category, AreaAccess access, String proxy, String address, String user, String pass, String fingerprint) {
		this.name = name;
		this.category = category;
		this.access = access;
		this.proxy = proxy;
		this.address = address;
		this.user = user;
		this.pass = pass;
		this.fingerprint = fingerprint;
		key = null;
	}

	public Server(String name, JsonReader jr) throws IOException {
		this.name = name;
		jr.beginObject();
		{
			AreaAccess _access = AreaAccess.anywhere;
			String _category = null, _fingerprint = null, _key = null, _pass = null, _proxy = null, _user = null;

			while (jr.hasNext()) {
				switch (jr.nextName()) {
					case "access":
						_access = AreaAccess.valueOf(jr.nextString());
						break;
					case "address":
						address = jr.nextString();
						break;
					case "category":
						_category = jr.nextString();
						break;
					case "fingerprint":
						_fingerprint = jr.nextString();
						break;
					case "key":
						_key = jr.nextString();
						break;
					case "pass":
						_pass = jr.nextString();
						break;
					case "proxy":
						_proxy = jr.nextString();
						break;
					case "user":
						_user = jr.nextString();
						break;
					default:
						jr.skipValue();
						break;
				}
			}
			
			if ((_key == null && _pass == null) || (_key != null && _pass != null))
				throw new IllegalArgumentException("one of \"key\" or \"pass\" should be specified in server \"" + name + '"');

			access = _access;
			category = _category;
			fingerprint = _fingerprint;
			key = _key != null ? new AuthKey(_key) : null;
			proxy = _proxy;
			pass = _pass;
			user = _user;
		}
		jr.endObject();
	}

	// FIXME: also update when last update was X mins ago, update when connation fails
	public String address() {
		if (address == null) {
			synchronized (LOCK_IP_UPDATE) {
				if (address == null)
					Config.updateDynamicIPs();
			}
		}
		return address;
	}
	
	public Server proxy() {
		return Config.getServer(proxy);// LOW: cache?
	}
}