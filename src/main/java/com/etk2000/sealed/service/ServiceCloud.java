package com.etk2000.sealed.service;

import java.io.IOException;
import java.util.List;

import com.etk2000.sealed.cloudprovider.AWSCloudProvider;
import com.etk2000.sealed.cloudprovider.CloudServer;
import com.etk2000.sealed.cloudprovider.GoogleCloudProvider;
import com.google.gson.stream.JsonReader;

public interface ServiceCloud {
	public static ServiceCloud read(String type, JsonReader jr) throws IOException {
		ServiceCloud res;
		jr.beginObject();
		{
			switch (type) {
				case "aws": {
					String id = null;

					while (jr.hasNext()) {
						switch (jr.nextName()) {
							case "id":
								id = jr.nextString();
								break;
							default:
								jr.skipValue();
								break;
						}
					}
					if (id == null)
						throw new IOException("missing field(s) for Cloud of type 'aws'");
					
					res = AWSCloudProvider.getFor(id);
					break;
				}
				case "google": {
					String id = null, projectID = null;

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
					if (id == null || projectID == null)
						throw new IOException("missing field(s) for Cloud of type 'google'");
					
					res = GoogleCloudProvider.getFor(id, projectID);
					break;
				}
				default:
					throw new IOException("invalid Cloud type, got '" + type + '\'');

			}
		}
		jr.endObject();
		return res;
	}
	
	List<CloudServer> fetchServers();
	
	String name();
}