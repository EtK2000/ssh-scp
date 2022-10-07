package com.etk2000.ssh_scp.util;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.security.PublicKey;
import java.util.List;

import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.connection.channel.direct.Session.Command;
import net.schmizz.sshj.transport.verification.HostKeyVerifier;

public class CommandConnection implements Closeable {
	public static class ExecResult {
		public final String stdout, stderr;

		ExecResult(String stdout, String stderr) {
			this.stdout = stdout;
			this.stderr = stderr;
		}
	}

	private static final HostKeyVerifier SKIP_FINGERPRINT = new HostKeyVerifier() {
		@Override
		public boolean verify(String hostname, int port, PublicKey key) {
			return true;
		}
		
		@Override
		public List<String> findExistingAlgorithms(String hostname, int port) {
			return null;
		}
	};

	private final AbstractServer srv;
	private SSHClient client;

	public CommandConnection(AbstractServer srv) throws IOException {
		this.srv = srv;
		connect();
	}

	protected SSHClient client() {
		return client;
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

	protected void connect() throws IOException {
		if (client != null)
			close();

		client = new SSHClient();

		if (srv.fingerprint() == null)
			client.addHostKeyVerifier(SKIP_FINGERPRINT);// FIXME: ask for verification
		else
			client.addHostKeyVerifier(srv.fingerprint());

		try {
			client.setConnectTimeout(10_000);
			client.connect(srv.address());

			if (srv.pass() != null)
				client.authPassword(srv.user(), srv.pass());
			else
				client.authPublickey(srv.user(), srv.key().path());

			client.useCompression();
		}
		catch (IOException e) {
			client.close();
			throw e;
		}

	}

	final public synchronized ExecResult exec(String command, int timeout) throws IllegalStateException, IOException {
		int prevTO = 0;
		if (timeout > 0) {
			prevTO = client.getConnection().getTimeoutMs();
			client.getConnection().setTimeoutMs(timeout);
		}

		try (Command cmd = client.startSession().exec(command)) {
			ByteArrayOutputStream stdout = new ByteArrayOutputStream(cmd.getLocalMaxPacketSize());
			cmd.getInputStream().transferTo(stdout);
			ByteArrayOutputStream stderr = new ByteArrayOutputStream(cmd.getLocalMaxPacketSize());
			cmd.getErrorStream().transferTo(stderr);

			return new ExecResult(stdout.toString(UTF_8.name()), stderr.toString(UTF_8.name()));
		}
		finally {
			if (timeout > 0)
				client.getConnection().setTimeoutMs(prevTO);
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