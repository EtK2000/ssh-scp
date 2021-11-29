package com.etk2000.sealed.platform;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermissions;

import javax.swing.JFrame;

import com.etk2000.sealed.keys.AuthKey;
import com.etk2000.sealed.ui.ProgressFrame;
import com.etk2000.sealed.util.LongBiConsumer;
import com.etk2000.sealed.util.Util;

class PlatformLinux extends Platform {
	private Process progress;
	private int nextTransfererId;

	PlatformLinux() {
		super(System.getProperty("user.home"));
	}

	@Override
	protected boolean ensureToolsExistImpl() {
		String sshpass = Util.runForResult("which sshpass");
		if (sshpass.length() == 0) {
			// FIXME: maybe allow running without?
			System.err.println("FIXME: maybe allow running without?");
			// FIXME: sudo apt-get install sshpass
			System.err.println("FIXME: sudo apt-get install sshpass");
			sshpass = Util.runForResult("which sshpass");
		}

		// ensure sshpass was installed
		if (sshpass.length() > 0) {
			sshKey = sshpass + " -i ${key} ${remote}";
			sshPass = sshpass + " -p ${pass} ssh ${remote}";
			return true;
		}
		return false;
	}

	@Override
	protected void runSSHImpl(AuthKey key, String remote) throws IOException {
		runSSH(key, remote, "");
	}

	@Override
	protected void runSSHImpl(String pass, String remote) throws IOException {
		runSSH(pass, remote, "");
	}

	@Override
	protected void setupKeyPermsImpl(String path) {
		// set 700 perms
		try {
			Files.setPosixFilePermissions(Paths.get(path), PosixFilePermissions.fromString("rwx------"));
		}
		catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	protected synchronized LongBiConsumer updateProgressImpl(JFrame frame) {
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
}