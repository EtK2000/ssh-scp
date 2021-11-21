package com.etk2000.sealed.dynamic_ip;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Map.Entry;
import java.util.function.BiConsumer;

import com.etk2000.sealed.config.Config;
import com.etk2000.sealed.config.Server;
import com.etk2000.sealed.platform.Platform;
import com.google.api.gax.core.CredentialsProvider;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.compute.v1.Instance;
import com.google.cloud.compute.v1.InstancesClient;
import com.google.cloud.compute.v1.InstancesScopedList;
import com.google.cloud.compute.v1.InstancesSettings;
import com.google.cloud.compute.v1.NetworkInterface;

class GoogleCloudLookupIP extends DynamicIP {
	private final String projectID;
	private final CredentialsProvider provider;

	GoogleCloudLookupIP(String id, String projectID) throws IOException {
		try (FileInputStream fis = new FileInputStream(new File(Platform.dirKeys(), id + ".json"))) {
			GoogleCredentials creds = GoogleCredentials.fromStream(fis).createScoped("https://www.googleapis.com/auth/cloud-platform");
			provider = () -> creds;
		}
		this.projectID = projectID;
	}

	@Override
	public void fetch(BiConsumer<Server, String> onFound) {
		try {
			try (InstancesClient instances = InstancesClient.create(InstancesSettings.newBuilder().setCredentialsProvider(provider).build())) {
				for (Entry<String, InstancesScopedList> zone : instances.aggregatedList(projectID).iterateAll()) {
					if (zone.getValue().getInstancesCount() > 0) {
						for (Instance instance : zone.getValue().getInstancesList()) {
							Server server = Config.getServer(instance.getName());
							if (server == null)
								continue;

							// search through all network interfaces for an external [NAT] IP
							boolean found = false;
							for (NetworkInterface netInt : instance.getNetworkInterfacesList()) {
								if (netInt.getAccessConfigsCount() > 0 && !netInt.getAccessConfigs(0).getNatIP().isEmpty()) {
									found = true;
									onFound.accept(server, netInt.getAccessConfigs(0).getNatIP());
									break;
								}
							}

							// if no IP was found, set it to null
							if (!found)
								onFound.accept(server, null);

							System.out.println("updated: " + instance.getName() + " (" + instance.getStatus() + ')');
						}
					}
				}
			}
		}
		catch (IOException e) {
			e.printStackTrace();
		}
	}
}