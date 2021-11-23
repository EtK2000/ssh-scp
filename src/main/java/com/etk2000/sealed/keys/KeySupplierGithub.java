package com.etk2000.sealed.keys;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.awt.Desktop;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Scanner;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.etk2000.sealed.platform.Platform;
import com.etk2000.sealed.util.Util;
import com.google.api.client.auth.oauth2.AuthorizationCodeFlow;
import com.google.api.client.auth.oauth2.BearerToken;
import com.google.api.client.auth.oauth2.ClientParametersAuthentication;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;

class KeySupplierGithub extends KeySupplier {
	private static void downloadAndExtractRepo(String owner, String repo, String token) throws IOException {
		File zip = new File(Platform.dir(), "keys.tmp");
		Util.urlGET("https://api.github.com/repos/" + owner + '/' + repo + "/zipball", Collections.singletonMap("Authorization", "token " + token), zip);

		// extract the zip into a new empty directory
		Util.delete(Platform.dirKeys());
		try (ZipFile unzip = new ZipFile(zip)) {
			for (Enumeration<? extends ZipEntry> entries = unzip.entries(); entries.hasMoreElements();) {
				ZipEntry entry = entries.nextElement();
				if (!entry.isDirectory()) {
					File key = new File(Platform.dirKeys(), entry.getName().substring(entry.getName().lastIndexOf('/') + 1));
					try (FileOutputStream fos = new FileOutputStream(key)) {
						unzip.getInputStream(entry).transferTo(fos);
					}
					Platform.setupKeyPerms(key.getAbsolutePath());
				}
			}
		}
		finally {
			zip.delete();
		}
	}

	public KeySupplierGithub(String clientID, String clientSecret, String owner, String repo) throws IOException {
		File access = new File(Platform.dir(), ".access");

		// if we already have a token, try to fetch using it
		if (access.exists()) {
			try (Scanner s = new Scanner(new FileInputStream(access)).useDelimiter("\\Z")) {
				if (s.hasNext()) {
					downloadAndExtractRepo(owner, repo, s.next());
					return;
				}
			}
			catch (IOException e) {
				if (e instanceof UnknownHostException)
					throw e;
				e.printStackTrace();
			}
		}

		// if we have no token or our token erred out, get a new token
		AuthorizationCodeFlow flow = new AuthorizationCodeFlow.Builder(BearerToken.authorizationHeaderAccessMethod(), new NetHttpTransport(), GsonFactory.getDefaultInstance(),
				new GenericUrl("https://github.com/login/oauth/access_token"), new ClientParametersAuthentication(clientID, clientSecret), clientID,
				"https://github.com/login/oauth/authorize").setScopes(Collections.singletonList("repo")).build();

		String code;
		try (ServerSocket srv = new ServerSocket(1789, 10)) {
			String url = flow.newAuthorizationUrl().build();
			if (Desktop.getDesktop().isSupported(Desktop.Action.BROWSE))
				Desktop.getDesktop().browse(new URI(url));
			else
				System.out.println("please navigate your browser to: " + url);// FIXME: open popup

			while (true) {
				try (Socket client = srv.accept()) {
					try (Scanner s = new Scanner(client.getInputStream()).useDelimiter("\n")) {
						String request = s.hasNext() ? s.next() : "";
						if (request.startsWith("GET /?code=") && request.endsWith(" HTTP/1.1\r")) {
							code = request.substring(11, request.length() - 10);
							client.getOutputStream()
									.write("HTTP/1.1 200 OK\r\n\r\n<html>Success, you can close this tab!<script>window.open('','_self').close()</script></html>".getBytes(UTF_8));
							break;
						}
					}
				}
				catch (IOException e) {
				}
			}
		}
		catch (URISyntaxException e) {
			throw new IOException(e);
		}

		// fetch and save the token
		String token = flow.newTokenRequest(code).setRequestInitializer(request -> request.getHeaders().setAccept("application/json")).execute().getAccessToken();
		access.getParentFile().mkdirs();
		try (FileOutputStream fos = new FileOutputStream(access)) {
			fos.write(token.getBytes(UTF_8));
		}

		downloadAndExtractRepo(owner, repo, token);
	}

	@Override
	public AuthKey getKey(String name) {
		File key = new File(Platform.dirKeys(), name);
		return key.isFile() ? new AuthKey(name, key.getAbsolutePath()) : null;
	}
}