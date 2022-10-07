package com.etk2000.ssh_scp.service;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.URLEncoder;

import com.etk2000.ssh_scp.util.Util;
import com.google.gson.stream.JsonReader;;

public class ServiceJenkins {
	private static final String NEEDLE_HREF = "<a href=\"", NEEDLE_STATUS = "class=\"healthReport\"";

	private final String pass, url, user;

	public ServiceJenkins(JsonReader jr) throws IOException {
		jr.beginObject();
		{
			String pass = null, url = null, user = null;
			while (jr.hasNext()) {
				switch (jr.nextName()) {
					case "pass":
						pass = jr.nextString();
						break;
					case "url":
						url = jr.nextString();
						break;
					case "user":
						user = jr.nextString();
						break;
					default:
						jr.skipValue();
						break;
				}
			}

			if (pass == null || url == null || user == null)
				throw new IOException("missing field(s) for service of type 'jenkins'");
			this.pass = pass;
			this.url = url;
			this.user = user;
		}
		jr.endObject();
	}

	public boolean interact() {
		CookieHandler old = CookieHandler.getDefault();
		try {
			CookieHandler.setDefault(new CookieManager());

			// get the required sessions cookie, will return HTTP 403 on success
			try {
				Util.urlGET(url);
			}
			catch (IOException e) {
				if (!e.getMessage().contains(" 403 ")) {
					e.printStackTrace();
					return false;
				}
			}

			try {
				String resp = Util.urlPOST(url + "/j_spring_security_check",
						"j_username=" + URLEncoder.encode(user, UTF_8.name()) + "&j_password=" + URLEncoder.encode(pass, UTF_8.name()) + "&from=%2F&Submit=Sign+in");
				System.out.println(resp);

				// parse jobs
				for (int index = resp.indexOf(NEEDLE_STATUS); index != -1; index = resp.indexOf(NEEDLE_STATUS, index)) {
					index = resp.indexOf(NEEDLE_HREF, index) + NEEDLE_HREF.length();
					System.out.println(resp.substring(index, resp.indexOf('"', index)));
				}

				return true;
			}
			catch (IOException e) {
				e.printStackTrace();
				return false;
			}
		}
		finally {
			CookieHandler.setDefault(old);
		}
	}
}