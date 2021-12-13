package com.etk2000.sealed.platform;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Arrays;

import javax.swing.JFrame;

import com.etk2000.sealed.Base;
import com.etk2000.sealed.config.Server;
import com.etk2000.sealed.ui.ProgressFrame;
import com.etk2000.sealed.util.LongBiConsumer;
import com.etk2000.sealed.util.Util;

public abstract class Platform {
	private static final Platform instance;
	private static int nextTransfererId;
	private static Process progress;

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
	 * @param  array the array to apply replaces to
	 * @param  args  the find and replace args as (find, replace)*
	 * @return       array after modifications
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

	public static void runSSH(Server srv, boolean newProcess) throws IOException {
		instance.runSSHImpl(srv, newProcess);
	}

	public static void setupKeyPerms(String path) {
		instance.setupKeyPermsImpl(path);
	}

	public static LongBiConsumer updateProgress(JFrame frame) {
		return instance.updateProgressImpl(frame);
	}

	protected final File dir, dirKeys, dirTmp;
	protected String[] sshKey, sshPass;

	protected Platform(String container) {
		dir = new File(container, "sealed");
		dirKeys = new File(dir, "keys");
		dirTmp = new File(dir, "tmp");
	}

	protected String[] buildCommandSSH(Server srv, String[] prefix) {
		String[] command;
		final String remote = srv.user + '@' + srv.address();
		if (srv.pass != null)
			command = replace(Arrays.copyOf(sshPass, sshPass.length), "${pass}", srv.pass, "${remote}", remote);
		else
			command = replace(Arrays.copyOf(sshKey, sshKey.length), "${key}", srv.key.path(), "${remote}", remote);

		// apply prefix if specified
		if (prefix != null && prefix.length > 0) 
			command = Util.copyAndMerge(prefix, command);
		
		return command;
	}

	protected synchronized LongBiConsumer newTransfer(JFrame frame) {
		int transfererId = nextTransfererId++;

		// spawn a new thread/process to handle a progress window
		return (current, full) -> {
			synchronized (this) {
				if (progress == null) {
					try {
						File thisJar = new File(getClass().getProtectionDomain().getCodeSource().getLocation().toURI());

						// if running as a jar, just sub-process it
						if (thisJar.isFile())
							progress = Runtime.getRuntime().exec("java -jar \"" + thisJar.getPath() + "\" --progress");

						// otherwise, we gotta setup the classpath and run ourself...
						// LOW: setup classpath by loading pom.xml and concatenating with
						// (System.getProperty("user.home") + "/.m2")
						else
							progress = Runtime.getRuntime().exec("java -cp \"" + thisJar.getPath() + "\" " + Base.class.getName() + " --progress");

						Runtime.getRuntime().addShutdownHook(new Thread(progress::destroyForcibly));
					}
					catch (IOException | URISyntaxException e) {
						e.printStackTrace();
					}
				}

				// write the update to our child process, LOW: respawn process if dead?
				try {
					ProgressFrame.writeTo(progress, transfererId, current, full);
				}
				catch (IOException e) {
					e.printStackTrace();
				}
			}
		};
	}

	protected void runSSH(Server srv, String[] prefix, boolean newProcess) throws IOException {
		if (newProcess)
			Util.run(buildCommandSSH(srv, prefix));
		else
			Util.runForResult(true, buildCommandSSH(srv, prefix));
	}

	protected abstract boolean ensureToolsExistImpl();

	protected abstract void runSSHImpl(Server srv, boolean newProcess) throws IOException;

	protected abstract void setupKeyPermsImpl(String path);

	protected abstract LongBiConsumer updateProgressImpl(JFrame frame);
}