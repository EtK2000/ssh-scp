package com.etk2000.ssh_scp.platform;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import com.etk2000.ssh_scp.config.Server;
import com.etk2000.ssh_scp.util.AbstractServer;
import com.etk2000.ssh_scp.util.Util;

public abstract class Platform {
	private static final String[] SSH_KEY = new String[] { "ssh", "-i", "${key}", "${remote}" };
	private static final String[] SSH_PASS = new String[] { "sshpass", "-p", "${pass}", "ssh", "${remote}" };
	private static final Platform instance;

	static {
		String os = System.getProperty("os.name").toLowerCase();
		if (os.contains("win"))
			instance = new PlatformWindows();
		else if (os.contains("mac"))
			instance = new PlatformMac();
		else
			instance = new PlatformLinux();
	}

	public static File dir() {
		instance.dir.mkdirs();
		return instance.dir;
	}

	public static File dirKeys() {
		instance.dirKeys.mkdirs();
		return instance.dirKeys;
	}

	public static File dirServices() {
		instance.dirServices.mkdirs();
		return instance.dirServices;
	}

	public static File dirTmp() {
		instance.dirTmp.mkdirs();
		return instance.dirTmp;
	}

	public static boolean ensureToolsExist() {
		// ensure we have a folder for our files
		if (!instance.dir.exists())
			instance.dir.mkdirs();
		return instance.ensureToolsExistImpl();
	}

	public static Platform instance() {
		return instance;
	}

	/**
	 * 
	 * @param array the array to apply replaces to
	 * @param args  the find and replace args as (find, replace)*
	 * @return array after modifications
	 */
	private static String[] replace(String[] array, String... args) {
		if (args.length == 0 || args.length % 2 != 0)
			throw new IllegalArgumentException("invalid number of find-replace args, got " + args.length);

		for (int i = 0, j; i < args.length; i += 2) {
			for (j = 0; j < array.length; ++j)
				array[j] = array[j].replace(args[i], args[i + 1]);
		}

		return array;
	}

	/**
	 * @param srv        the server for our attempted connection
	 * @param newProcess whether or not to open a new window/process
	 * @throws IllegalStateException if the server is inaccessible
	 * @throws IOException           if the command fails
	 */
	public static void runSSH(AbstractServer srv, boolean newProcess) throws IllegalStateException, IOException {
		instance.runSSHImpl(srv, newProcess);
	}

	public static void setupKeyPerms(String path) {
		instance.setupKeyPermsImpl(path);
	}

	protected final File dir, dirKeys, dirServices, dirTmp;
	protected String[] sshKey, sshPass;

	protected Platform(String container) {
		dir = new File(container, ".ssh-scp");
		dirKeys = new File(dir, "keys");
		dirServices = new File(dir, "services");
		dirTmp = new File(dir, "tmp");
	}

	/**
	 * @param  srv                   the server for our attempted connection
	 * @param  prefix                the prefix to prepend to the command
	 * @return                       the command to execute
	 * @throws IllegalStateException if the server is inaccessible
	 */
	protected String[] buildCommandSSH(AbstractServer srv, String[] prefix) throws IllegalStateException {
		// FIXME: check $(srv.proxy()) and if it isn't null use it
		String address = srv.address();
		if (address == null)
			throw new IllegalStateException("no valid IP found for server '" + srv.name() + '\'');

		Server proxy = srv.proxy();

		String[] command;
		final String remote = srv.user() + '@' + address;
		if (srv.pass() != null) {
			String[] base = proxy == null ? instance.sshPass : SSH_PASS;
			command = replace(Arrays.copyOf(base, base.length), "${pass}", srv.pass(), "${remote}", remote);
		}
		else {
			String[] base = proxy == null ? instance.sshKey : SSH_KEY;
			command = replace(Arrays.copyOf(base, base.length), "${key}", srv.key().path(), "${remote}", remote);
		}

		// apply proxy if specified
		if (proxy != null) {
			if (proxy.proxy() != null) // FIXME: SCP via proxy isn't support yet :(
				throw new IllegalStateException("proxy nesting not supported");

			String[] proxyCommand = buildCommandSSH(proxy, prefix);
			String[] res = Arrays.copyOf(proxyCommand, proxyCommand.length + 2);
			res[proxyCommand.length] = "-t";
			res[proxyCommand.length + 1] = '"' + String.join("\" \"", command) + '"';
			command = res;
		}

		// apply prefix if specified, will be applied to proxy instead if existent
		else if (prefix != null && prefix.length > 0)
			command = Util.copyAndMerge(prefix, command);

		return command;
	}

	protected void runSSH(AbstractServer srv, String[] prefix, boolean newProcess) throws IllegalStateException, IOException {
		if (newProcess)
			Util.run(buildCommandSSH(srv, prefix));
		else
			Util.runForResult(true, buildCommandSSH(srv, prefix));
	}

	protected abstract boolean ensureToolsExistImpl();

	protected abstract void runSSHImpl(AbstractServer srv, boolean newProcess) throws IllegalStateException, IOException;

	protected abstract void setupKeyPermsImpl(String path);
}