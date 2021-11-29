package com.etk2000.sealed.platform;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;

import javax.swing.JFrame;

import com.etk2000.sealed.keys.AuthKey;
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

	public static void runSSH(AuthKey key, String remote) throws IOException {
		instance.runSSHImpl(key, remote);
	}

	public static void runSSH(String pass, String remote) throws IOException {
		instance.runSSHImpl(pass, remote);
	}

	public static void setupKeyPerms(String path) {
		instance.setupKeyPermsImpl(path);
	}

	public static LongBiConsumer updateProgress(JFrame frame) {
		return instance.updateProgressImpl(frame);
	}

	protected final File dir, dirKeys, dirTmp;
	protected String sshKey, sshPass;

	protected Platform(String container) {
		dir = new File(container, "sealed");
		dirKeys = new File(dir, "keys");
		dirTmp = new File(dir, "tmp");
	}
	
	protected synchronized LongBiConsumer newTransfer(JFrame frame) {
		int transfererId = nextTransfererId++;
		
		// spawn a new thread/process to handle a progress window
		return (current, full) -> {
			synchronized (this) {
				if (progress == null) {
					try {
						progress = Runtime.getRuntime()
								.exec("java -jar \"" + new File(getClass().getProtectionDomain().getCodeSource().getLocation().toURI()).getPath() + "\" --progress");
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

	protected void runSSH(AuthKey key, String remote, String prefix) throws IOException {
		Util.run(prefix + sshKey.replace("${key}", '"' + key.path() + '"').replace("${remote}", remote));
	}

	protected void runSSH(String pass, String remote, String prefix) throws IOException {
		Util.run(prefix + sshPass.replace("${pass}", pass).replace("${remote}", remote));
	}

	protected abstract boolean ensureToolsExistImpl();
	protected abstract void runSSHImpl(AuthKey key, String remote) throws IOException;
	protected abstract void runSSHImpl(String pass, String remote) throws IOException;
	protected abstract void setupKeyPermsImpl(String path);
	protected abstract LongBiConsumer updateProgressImpl(JFrame frame);
}