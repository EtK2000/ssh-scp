package com.etk2000.ssh_scp.cloudprovider;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import com.etk2000.ssh_scp.api.ApiCloud;
import com.etk2000.ssh_scp.api.ApiDynamicIP;
import com.etk2000.ssh_scp.api.MissingFieldsException;
import com.etk2000.ssh_scp.api.ServiceDynamicCloud;
import com.etk2000.ssh_scp.config.Config;
import com.etk2000.ssh_scp.config.Server;
import com.etk2000.ssh_scp.platform.Platform;
import com.google.api.gax.core.CredentialsProvider;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.Tuple;
import com.google.cloud.compute.v1.Instance;
import com.google.cloud.compute.v1.InstancesClient;
import com.google.cloud.compute.v1.InstancesScopedList;
import com.google.cloud.compute.v1.InstancesSettings;
import com.google.cloud.compute.v1.NetworkInterface;
import com.google.common.base.Predicates;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

public class ServiceGoogleCloud implements ServiceDynamicCloud {
	private static CloudServer asCloudServer(Tuple<String, Instance> zoneInstance) {
		Instance inst = zoneInstance.y();
		String type = inst.getMachineType();
		type = type.substring(type.lastIndexOf('/') + 1);
		String zone = zoneInstance.x();
		zone = zone.substring(zone.lastIndexOf('/') + 1);
		
		// FIXME: look into fetching keys and user
		return new CloudServer(lookupIP(inst), zone, inst.getName(), type, inst.getStatus(), null, null);
	}

	private static Tuple<Server, String> asServer(Tuple<String, Instance> zoneInstance) {
		Server server = Config.getServer(zoneInstance.y().getName());
		if (server == null)
			return null;

		// if no IP was found, set it to null
		return Tuple.of(server, lookupIP(zoneInstance.y()));
	}
	

	// search through all network interfaces for an external [NAT] IP
	private static String lookupIP(Instance inst) {
		for (NetworkInterface netInt : inst.getNetworkInterfacesList()) {
			if (netInt.getAccessConfigsCount() > 0 && !netInt.getAccessConfigs(0).getNatIP().isEmpty())
				return netInt.getAccessConfigs(0).getNatIP();
		}
		return null;
	}

	public static ServiceGoogleCloud read(JsonReader jr) throws IOException {
		String id = null, projectID = null;

		jr.beginObject();
		{
			while (jr.hasNext()) {
				switch (jr.nextName()) {
					case "id":
						id = jr.nextString();
						break;
					case "project_id":
						projectID = jr.nextString();
						break;
					default:
						jr.skipValue();
						break;
				}
			}
		}
		jr.endObject();
		if (id == null || projectID == null)
			throw new MissingFieldsException();

		// FIXME: prevent duplicates via key, for example:
		// final String key = id + '\0' + projectID;
		ServiceGoogleCloud res = new ServiceGoogleCloud(id, projectID);
		ApiCloud.register(res.name(), res);
		ApiDynamicIP.register(res.name(), res);
		return res;
	}

	private final String id, projectID;
	private final CredentialsProvider provider;

	private ServiceGoogleCloud(String id, String projectID) throws IOException {
		this.id = id;
		this.projectID = projectID;

		try (FileInputStream fis = new FileInputStream(new File(Platform.dirKeys(), id + ".json"))) {
			GoogleCredentials creds = GoogleCredentials.fromStream(fis).createScoped("https://www.googleapis.com/auth/cloud-platform");
			provider = () -> creds;
		}
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
		return getAllServers().stream().map(ServiceGoogleCloud::asServer).filter(Predicates.notNull()).collect(Collectors.toList());
	}

	@Override
	public List<CloudServer> fetchServers() {
		return getAllServers().stream().map(ServiceGoogleCloud::asCloudServer).collect(Collectors.toList());
	}

	@Override
	public String name() {
		return "Google";
	}

	@Override
	public void write(JsonWriter jw) throws IOException {
		jw.name(name().toLowerCase()).beginObject();
		{
			jw.name("id").value(id);
			jw.name("project_id").value(projectID);
		}
		jw.endObject();
	}
}