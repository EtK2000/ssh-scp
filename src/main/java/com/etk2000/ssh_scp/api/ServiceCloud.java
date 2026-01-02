package com.etk2000.ssh_scp.api;

import java.util.List;

import com.etk2000.ssh_scp.cloudprovider.CloudServer;

public interface ServiceCloud extends Service {
	List<CloudServer> fetchServers(boolean fetchIPs);

	String name();
}