package com.etk2000.sealed.platform;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermissions;

import com.etk2000.sealed.keys.AuthKey;
import com.etk2000.sealed.util.Util;

class PlatformLinux extends Platform {
	PlatformLinux() {
		super(System.getProperty("user.home"));
	}

	@Override
	protected boolean ensureToolsExistImpl() {
		String sshpass = Util.runForResult("which sshpass");
		if (sshpass.length() == 0) {
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
	protected void runSSHImpl(AuthKey key, String remote) {
		runSSH(key, remote, "");
	}

	@Override
	protected void runSSHImpl(String pass, String remote) {
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
}