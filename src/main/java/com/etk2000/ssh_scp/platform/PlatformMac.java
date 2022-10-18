package com.etk2000.ssh_scp.platform;

import java.io.File;
import java.io.IOException;
import java.util.Scanner;

import com.etk2000.ssh_scp.util.AbstractServer;
import com.etk2000.ssh_scp.util.Util;

class PlatformMac extends PlatformLinux {
	private static final String[] HOMEBREW_PREFIX = { "/opt/homebrew/bin/", "/usr/local/bin/" };
	private static final String HOMEBREW = "brew";
	private static final String SSHPASS = "sshpass";
	private static final File iTerm = new File("/Applications/iTerm.app");

	private final String osascript, launchITerm, launchTerminal;

	PlatformMac() {
		osascript = Util.runForResult(false, "which", "osascript");
		if (osascript.length() == 0)// FIXME: deal with this state
			throw new IllegalStateException("osascript is required");

		try {
			try (Scanner s = new Scanner(Util.getResource("/launch_iTerm.applescript").openStream()).useDelimiter("\\Z")) {
				if (!s.hasNext())
					throw new IOException();
				launchITerm = s.next();
			}

			try (Scanner s = new Scanner(Util.getResource("/launch_Terminal.applescript").openStream()).useDelimiter("\\Z")) {
				if (!s.hasNext())
					throw new IOException();
				launchTerminal = s.next();
			}
		}
		catch (IOException e) {
			throw new IllegalStateException("jar appears corrupted");
		}
	}

	@Override
	protected boolean ensureToolsExistImpl() {
		String ssh = Util.runForResult(false, "which", "ssh");
		if (ssh.length() == 0) {
			System.err.println("no ssh");// FIXME: look into a workaround or something
			return false;
		}

		// ensure homebrew is installed
		String brewPrefix = null;
		for (String prefix : HOMEBREW_PREFIX) {
			if (new File(prefix + HOMEBREW).exists()) {
				brewPrefix = prefix;
				break;
			}
		}
		if (brewPrefix == null)
			return false;

		File sshpassFile = new File(brewPrefix + SSHPASS);
		if (!sshpassFile.exists()) {
			// FIXME: maybe allow running without?
			System.err.println("FIXME: maybe allow running without?");
			Util.runForResult(true, brewPrefix + HOMEBREW, "install", "hudochenkov/sshpass/sshpass");
		}

		// ensure sshpass was installed
		if (sshpassFile.exists()) {

			// setup the commands to execute using the script
			sshKey = new String[] { ssh, "-i", "${key}", "${remote}" };
			sshPass = new String[] { brewPrefix + SSHPASS, "-p", "${pass}", ssh, "${remote}" };

			return true;
		}
		return false;
	}

	@Override
	protected void runSSHImpl(AbstractServer srv, boolean newProcess) throws IllegalStateException, IOException {
		// on OSX, we need to send the command as a single argument
		if (newProcess)
			// FIXME: escape quotes, slashes, and anything else that can be problematic
			Util.run(osascript, "-e", (iTerm.isDirectory() ? launchITerm : launchTerminal).replace("${command}", String.join(" ", buildCommandSSH(srv, null))));
		else
			runSSH(srv, null, newProcess);
	}
}