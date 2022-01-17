package com.etk2000.sealed.cloudprovider;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import com.etk2000.sealed.config.Config;
import com.etk2000.sealed.config.Server;
import com.etk2000.sealed.platform.Platform;
import com.etk2000.sealed.service.ServiceDynamicIP;
import com.etk2000.sealed.service.ServiceCloud;
import com.google.api.gax.core.CredentialsProvider;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.Tuple;
import com.google.cloud.compute.v1.Instance;
import com.google.cloud.compute.v1.InstancesClient;
import com.google.cloud.compute.v1.InstancesScopedList;
import com.google.cloud.compute.v1.InstancesSettings;
import com.google.cloud.compute.v1.NetworkInterface;
import com.google.common.base.Predicates;

public class GoogleCloudProvider implements ServiceDynamicIP, ServiceCloud {
	private static final Map<String, GoogleCloudProvider> cache = new HashMap<>();

	public static GoogleCloudProvider getFor(String id, String projectID) throws IOException {
		final String key = id + '\0' + projectID;
		GoogleCloudProvider res = cache.get(key);
		if (res == null)
			cache.put(key, res = new GoogleCloudProvider(id, projectID));
		return res;
	}

	private static CloudServer asCloudServer(Tuple<String, Instance> zoneInstance) {
		Instance inst = zoneInstance.y();
		String type = inst.getMachineType();
		type = type.substring(type.lastIndexOf('/') + 1);
		String zone = zoneInstance.x();
		zone = zone.substring(zone.lastIndexOf('/') + 1);
		return new CloudServer(zone, inst.getName(), type, inst.getStatus());
	}

	private static Tuple<Server, String> asServer(Tuple<String, Instance> zoneInstance) {
		Server server = Config.getServer(zoneInstance.y().getName());
		if (server == null)
			return null;

		// search through all network interfaces for an external [NAT] IP
		for (NetworkInterface netInt : zoneInstance.y().getNetworkInterfacesList()) {
			if (netInt.getAccessConfigsCount() > 0 && !netInt.getAccessConfigs(0).getNatIP().isEmpty())
				return Tuple.of(server, netInt.getAccessConfigs(0).getNatIP());
		}

		// if no IP was found, set it to null
		return Tuple.of(server, null);
	}

	private final String projectID;
	private final CredentialsProvider provider;

	private GoogleCloudProvider(String id, String projectID) throws IOException {
		try (FileInputStream fis = new FileInputStream(new File(Platform.dirKeys(), id + ".json"))) {
			GoogleCredentials creds = GoogleCredentials.fromStream(fis).createScoped("https://www.googleapis.com/auth/cloud-platform");
			provider = () -> creds;
		}
		this.projectID = projectID;
	}

	private List<Tuple<String, Instance>> getAllServers() {
		List<Tuple<String, Instance>> res = new ArrayList<>();
		try {
			try (InstancesClient instances = InstancesClient.create(InstancesSettings.newBuilder().setCredentialsProvider(provider).build())) {
				for (Entry<String, InstancesScopedList> zone : instances.aggregatedList(projectID).iterateAll())
					zone.getValue().getInstancesList().forEach(instance -> res.add(Tuple.of(zone.getKey(), instance)));
			}
		}
		catch (IOException e) {
			e.printStackTrace();
		}
		return res;
	}

	@Override
	public List<Tuple<Server, String>> fetchIPs() {
		return getAllServers().stream().map(GoogleCloudProvider::asServer).filter(Predicates.notNull()).collect(Collectors.toList());
	}

	@Override
	public List<CloudServer> fetchServers() {
		return getAllServers().stream().map(GoogleCloudProvider::asCloudServer).collect(Collectors.toList());
	}
	
	@Override
	public String name() {
		return "Google";
	}
}