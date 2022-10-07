package com.etk2000.ssh_scp.config;

import java.io.IOException;

import com.etk2000.ssh_scp.api.ApiDynamicIP;
import com.etk2000.ssh_scp.keys.AuthKey;
import com.etk2000.ssh_scp.util.AbstractServer;
import com.etk2000.ssh_scp.util.Util;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

public class Server implements AbstractServer {
	private static final Object LOCK_IP_UPDATE = new Object();

	public static enum AreaAccess {
		anywhere, office, vpn;

		static AreaAccess def() {
			return anywhere;
		}
	}

	private final String proxy;
	private String address;
	public final AreaAccess access;
	public final String category;
	public final String fingerprint, name, user, pass;
	public final boolean isDynamicIP;
	public final AuthKey key;

	Server(String name, String category, AreaAccess access, String proxy, String address, String user, AuthKey key, String fingerprint) {
		this.name = name;
		this.category = category;
		this.access = access;
		this.proxy = proxy;
		this.address = address;
		this.user = user;
		this.key = key;
		this.fingerprint = fingerprint;
		isDynamicIP = address == null;
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
		isDynamicIP = address == null;
		key = null;
	}

	public Server(String name, JsonReader jr) throws IOException {
		this.name = name;
		jr.beginObject();
		{
			AreaAccess _access = AreaAccess.def();
			String _category = "", _fingerprint = null, _key = null, _pass = null, _proxy = null, _user = null;

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
			isDynamicIP = address == null;
		}
		jr.endObject();
	}

	// FIXME: also update when last update was X mins ago
	// FIXME: update when connection fails
	@Override
	public String address() {
		if (address == null) {
			synchronized (LOCK_IP_UPDATE) {
				if (address == null)
					ApiDynamicIP.updateDynamicIPs();
			}
		}
		return address;
	}

	@Override
	public String fingerprint() {
		return fingerprint;
	}

	@Override
	public AuthKey key() {
		return key;
	}

	@Override
	public String name() {
		return name;
	}

	@Override
	public String pass() {
		return pass;
	}

	@Override
	public Server proxy() {
		return Config.getServer(proxy);// LOW: cache?
	}

	public void setAddress(String address) {
		Util.guardInternalAPI();
		this.address = address;
	}

	@Override
	public String user() {
		return user;
	}

	public void write(JsonWriter jw) throws IOException {
		jw.name(name).beginObject();
		{
			if (access != AreaAccess.def())
				jw.name("access").value(access.name());

			if (!isDynamicIP)
				jw.name("address").value(address);

			if (category.length() > 0)
				jw.name("category").value(category);

			if (fingerprint != null)
				jw.name("fingerprint").value(fingerprint);

			if (key != null)
				jw.name("key").value(key.name);

			if (pass != null)
				jw.name("pass").value(pass);

			if (proxy != null)
				jw.name("proxy").value(proxy);

			if (user != null)
				jw.name("user").value(user);
		}
		jw.endObject();
	}
}