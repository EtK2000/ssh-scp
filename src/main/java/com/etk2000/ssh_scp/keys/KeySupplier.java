package com.etk2000.ssh_scp.keys;

import java.io.IOException;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

public interface KeySupplier {
	public static KeySupplier read(String type, JsonReader jr) throws IOException {
		KeySupplier res;

		jr.beginObject();
		{
			switch (type) {
				case "github": {
					String clientID = null, clientSecret = null, repoName = null, repoOwner = null;

					while (jr.hasNext()) {
						switch (jr.nextName()) {
							case "client_id":
								clientID = jr.nextString();
								break;
							case "client_secret":
								clientSecret = jr.nextString();
								break;
							case "repo_name":
								repoName = jr.nextString();
								break;
							case "repo_owner":
								repoOwner = jr.nextString();
								break;
							default:
								jr.skipValue();
								break;
						}
					}
					if (clientID == null || clientSecret == null || repoName == null || repoOwner == null)
						throw new IOException("missing field(s) for KeySupplier of type 'github'");
					
					res = new KeySupplierGithub(clientID, clientSecret, repoOwner, repoName);
					break;
				}
				default:
					throw new IOException("invalid KeySupplier type, got '" + type + '\'');

			}
		}
		jr.endObject();
		return res;
	}

	AuthKey getKey(String name);
	
	void write(JsonWriter jw) throws IOException;
}