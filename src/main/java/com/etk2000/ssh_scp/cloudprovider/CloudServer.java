package com.etk2000.ssh_scp.cloudprovider;

import java.io.IOException;

import com.etk2000.ssh_scp.config.Server;
import com.etk2000.ssh_scp.keys.AuthKey;
import com.etk2000.ssh_scp.util.AbstractServer;

public class CloudServer implements AbstractServer, Comparable<CloudServer> {
	public static enum ServerState {
		other, running, stopped, stopping, terminated, terminating;

		// LOW: maybe do this logic in the specific cloud providers..?
		public static ServerState from(String state) {
			switch (state.toLowerCase()) {
				case "running":
					return running;
				case "shutting-down":
				case "stopping":
					return stopping;
				case "stopped":
					return stopped;
				case "terminated":
					return terminated;
				case "terminating":
					return terminating;
			}
			
			// AWS has a "pending" state
			return other;
		}
	}

	public final String address, keyName, name, region, type, user;
	public final ServerState state;

	CloudServer(String address, String region, String name, String type, String state, String keyName, String user) {
		this.address = address;
		this.region = region;
		this.name = name;
		this.type = type;
		this.state = ServerState.from(state);
		this.keyName = keyName;
		this.user = user;
	}

	@Override
	public String address() {
		return address;// TODO: deal
	}

	@Override
	public int compareTo(CloudServer other) {
		if (state != other.state)
			return state.ordinal() - other.state.ordinal();
		int res = name.compareToIgnoreCase(other.name);
		if (res != 0)
			return res;
		res = region.compareToIgnoreCase(other.region);
		if (res != 0)
			return res;
		return type.compareToIgnoreCase(other.type);
	}

	@Override
	public String name() {
		return name;
	}

	@Override
	public String fingerprint() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public AuthKey key() {
		if (keyName == null)
			return null;
		
		try {
			return new AuthKey(keyName.endsWith(".pem") ? keyName : (keyName + ".pem"));
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public String pass() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Server proxy() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String user() {
		return user;
	}
}