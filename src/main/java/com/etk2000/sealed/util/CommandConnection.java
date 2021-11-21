package com.etk2000.sealed.util;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;

import com.etk2000.sealed.config.Server;

import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.connection.channel.direct.Session.Command;

public class CommandConnection implements Closeable {
	public static class ExecResult {
		public final String stdout, stderr;

		ExecResult(String stdout, String stderr) {
			this.stdout = stdout;
			this.stderr = stderr;
		}
	}

	protected final SSHClient client;

	public CommandConnection(Server srv) throws IOException {
		client = new SSHClient();

		if (!srv.fingerprint.isEmpty())
			client.addHostKeyVerifier(srv.fingerprint);

		try {
			client.setConnectTimeout(10_000);
			client.connect(srv.address());

			if (srv.pass != null)
				client.authPassword(srv.user, srv.pass);
			else
				client.authPublickey(srv.user, srv.key.path());

			client.useCompression();
		}
		catch (IOException e) {
			client.close();
			throw e;
		}
	}

	@Override
	final public void close() {
		try {
			client.close();
		}
		catch (IOException e) {
			e.printStackTrace();
		}
	}

	final public synchronized ExecResult exec(String command) throws IOException {
		try (Command cmd = client.startSession().exec(command)) {
			ByteArrayOutputStream stdout = new ByteArrayOutputStream(cmd.getLocalMaxPacketSize());
			cmd.getInputStream().transferTo(stdout);
			ByteArrayOutputStream stderr = new ByteArrayOutputStream(cmd.getLocalMaxPacketSize());
			cmd.getErrorStream().transferTo(stderr);

			return new ExecResult(stdout.toString(UTF_8.name()), stderr.toString(UTF_8.name()));
		}
	}

	/*final public synchronized ExecResult execDontWait(String command) throws IOException {
		try (Command cmd = client.startSession().exec(command)) {
			ByteArrayOutputStream stdout = new ByteArrayOutputStream(cmd.getLocalMaxPacketSize());
			ByteArrayOutputStream stderr = new ByteArrayOutputStream(cmd.getLocalMaxPacketSize());
			
			cmd.
			
			cmd.getInputStream().transferTo(stdout);
			cmd.getErrorStream().transferTo(stderr);

			return new ExecResult(stdout.toString(UTF_8.name()), stderr.toString(UTF_8.name()));
		}
	}*/
}