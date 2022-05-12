package com.etk2000.sealed.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.etk2000.sealed.vpnprovider.ForticlientVpnProvider;
import com.google.gson.stream.JsonReader;

public interface ServiceVPN {
	public static List<ServiceVPN> read(String type, JsonReader jr) throws IOException {
		List<ServiceVPN> res = new ArrayList<>();
		jr.beginObject();
		{
			switch (type) {
				case "forticlient": {

					while (jr.hasNext()) {
						String id = jr.nextName();
						String address = null, pass = null, user = null;
						jr.beginObject();
						{
							while (jr.hasNext()) {
								switch (jr.nextName()) {
									case "address":
										address = jr.nextString();
										break;
									case "pass":
										pass = jr.nextString();
										break;
									case "user":
										user = jr.nextString();
										break;
									default:
										jr.skipValue();
										break;
								}
							}
						}
						jr.endObject();
						
						if (address == null || pass == null || user == null)
							throw new IOException("missing field(s) for VPN of type 'forticlient'");
						res.add(ForticlientVpnProvider.getFor(id, address, user, pass));
					}
					break;
				}
				default:
					throw new IOException("invalid VPN type, got '" + type + '\'');

			}
		}
		jr.endObject();
		return res;
	}

	String name();

	void setEnabled(boolean enabled);
}