package com.etk2000.sealed.platform;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermissions;

import javax.swing.JFrame;

import com.etk2000.sealed.config.Server;
import com.etk2000.sealed.util.LongBiConsumer;
import com.etk2000.sealed.util.Util;

class PlatformLinux extends Platform {
	PlatformLinux() {
		super(System.getProperty("user.home"));
	}

	@Override
	protected boolean ensureToolsExistImpl() {
		String sshpass = Util.runForResult("which sshpass", false);
		if (sshpass.length() == 0) {
			// FIXME: maybe allow running without?
			System.err.println("FIXME: maybe allow running without?");
			// FIXME: sudo apt-get install sshpass
			System.err.println("FIXME: sudo apt-get install sshpass");
			sshpass = Util.runForResult("which sshpass", false);
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
	protected void runSSHImpl(Server srv, boolean newProcess) throws IOException {
		runSSH(srv, newProcess ? "x-terminal-emulator -e " : "", newProcess);
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
	protected LongBiConsumer updateProgressImpl(JFrame frame) {
		return newTransfer(frame);
	}
}