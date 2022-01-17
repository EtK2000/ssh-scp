package com.etk2000.sealed.cloudprovider;

public class CloudServer implements Comparable<CloudServer> {
	public static enum ServerState {
		other, running, stopped;
		
		// LOW: maybe do this logic in the specific cloud providers..?
		public static ServerState from(String state) {
			switch (state.toLowerCase()) {
				case "running":
					return running;
				case "stopped":
				case "terminated":
					return stopped;
			}
			
			return other;
		}
	}
	
	public final String name, region, type;
	public final ServerState state;
	
	CloudServer(String region, String name, String type, String state) {
		this.region = region;
		this.name = name;
		this.type = type;
		this.state = ServerState.from(state);
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
}