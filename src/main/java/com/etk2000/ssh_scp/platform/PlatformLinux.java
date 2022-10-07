package com.etk2000.ssh_scp.platform;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermissions;

import com.etk2000.ssh_scp.util.AbstractServer;
import com.etk2000.ssh_scp.util.Util;

class PlatformLinux extends Platform {
	private static final String[] NEW_PROCESS_PREFIX = { "x-terminal-emulator", "-e" };

	PlatformLinux() {
		super(System.getProperty("user.home"));
	}

	@Override
	protected boolean ensureToolsExistImpl() {
		String ssh = Util.runForResult(false, "which", "ssh");
		if (ssh.length() == 0) {
			System.err.println("no ssh");// FIXME: look into a workaround or something
			return false;
		}

		String sshpass = Util.runForResult(false, "which", "sshpass");
		if (sshpass.length() == 0) {
			// FIXME: maybe allow running without?
			System.err.println("FIXME: maybe allow running without?");
			// FIXME: sudo apt-get install sshpass
			System.err.println("FIXME: sudo apt-get install sshpass");
			sshpass = Util.runForResult(false, "which", "sshpass");
		}

		// ensure sshpass was installed
		if (sshpass.length() > 0) {
			sshKey = new String[] { ssh, "-i", "${key}", "${remote}" };
			sshPass = new String[] { sshpass, "-p", "${pass}", ssh, "${remote}" };
			return true;
		}
		return false;
	}

	@Override
	protected void runSSHImpl(AbstractServer srv, boolean newProcess) throws IllegalStateException, IOException {
		runSSH(srv, newProcess ? NEW_PROCESS_PREFIX : null, newProcess);
	}

	@Override
	protected void setupKeyPermsImpl(String path) {
		// set 600 perms
		try {
			Files.setPosixFilePermissions(Paths.get(path), PosixFilePermissions.fromString("rw-------"));
		}
		catch (IOException e) {
			e.printStackTrace();
		}
	}
}