package com.etk2000.ssh_scp.api;

import java.util.List;

import com.etk2000.ssh_scp.config.Server;
import com.google.cloud.Tuple;

public interface ServiceDynamicIP extends Service {
	List<Tuple<Server, String>> fetchIPs();
}