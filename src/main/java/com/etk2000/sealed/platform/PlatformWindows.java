package com.etk2000.sealed.platform;

import java.awt.Taskbar;
import java.awt.Taskbar.State;
import java.io.File;
import java.io.IOException;

import javax.swing.JFrame;

import com.etk2000.sealed.config.Server;
import com.etk2000.sealed.util.LongBiConsumer;
import com.etk2000.sealed.util.Util;

class PlatformWindows extends Platform {
	private static final String[] NEW_PROCESS_PREFIX_CMD = { "cmd", "/c", "start" };
	private static final String[] NEW_PROCESS_PREFIX_WT = { "wt", "nt" };

	PlatformWindows() {
		super(System.getenv("APPDATA"));
	}

	@Override
	protected boolean ensureToolsExistImpl() {
		// use SSHPASS via WSL for SSH with password, and SSH via WSL if no openSSH,
		// or fallback to PuTTY if neither exist
		File openssh = new File(System.getenv("WINDIR") + "/System32/OpenSSH/ssh.exe");
		if (openssh.exists())
			sshKey = new String[] { openssh.getAbsolutePath(), "-i", "${key}", "${remote}" };

		// TODO: fetch paths from result of which?
		System.err.println("TODO: fetch paths from result of which?");
		File wslFile = new File(System.getenv("WINDIR") + "/System32/wsl.exe");
		if (wslFile.exists()) {
			final String wsl = wslFile.getAbsolutePath();
			String sshpass = Util.runForResult(false, wsl, "which", "sshpass");
			if (sshpass.length() == 0) {
				// TODO: let user know what's being installed
				Util.runForResult(false, "cmd", "/c", "start", "/WAIT", wsl, "sudo", "apt-get", "install", "sshpass");
				sshpass = Util.runForResult(false, wsl, "which", "sshpass");
			}

			// ensure sshpass was installed
			if (sshpass.length() > 0) {
				// FIXME: fix ssh location
				sshPass = new String[] { wsl, sshpass, "-p", "${pass}", "ssh", "${remote}" };
			}

			// if we don't have ssh, see if we have it via wsl
			if (sshKey == null) {
				String ssh = Util.runForResult(false, wsl, "which", "ssh");
				if (ssh.length() == 0) {
					// TODO: let user know what's being installed
					Util.runForResult(false, "cmd", "/c", "start", "/WAIT", wsl, "echo", "sudo", "apt-get", "install", "ssh");
					ssh = Util.runForResult(false, wsl, "which", "ssh");
				}

				// check if ssh was installed
				if (ssh.length() > 0) {
					// FIXME: fix key and ssh location
					System.err.println("FIXME: fix key and ssh location");
					sshKey = new String[] { wsl, "ssh", "-i", "${key}", "${remote}" };
				}

				// LOW: fallback to sshpass instead?
			}
		}

		// don't fallback to PuTTY for keys due to different key format
		if (sshKey == null)
			return false;

		// skip PuTTY if we can
		if (sshPass != null)
			return true;

		File putty = new File(dir, "putty.exe");
		if (!putty.exists() && !Util.download("https://the.earth.li/~sgtatham/putty/latest/w64/putty.exe", putty))
			return false;

		if (sshPass == null)
			sshPass = new String[] { putty.getAbsolutePath(), "-pw", "${pass}", "${remote}" };

		return true;
	}

	// attempt to run in Windows Terminal, but fallback to cmd
	@Override
	protected void runSSHImpl(Server srv, boolean newProcess) throws IllegalStateException, IOException {
		try {
			runSSH(srv, newProcess ? NEW_PROCESS_PREFIX_WT : null, newProcess);
		}
		catch (IOException e) {
			if (e.getMessage().contains("cannot find the file"))
				runSSH(srv, newProcess ? NEW_PROCESS_PREFIX_CMD : null, newProcess);
			else
				throw e;
		}
	}

	@Override
	protected void setupKeyPermsImpl(String path) {
		// set 700 perms
		Util.runForResult(false, "icacls", path, "/c", "/t", "/Inheritance:d");
		Util.runForResult(false, "icacls", path, "/c", "/t", "/Grant", System.getProperty("user.name") + ":F");
		Util.runForResult(false, "icacls", path, "/c", "/t", "/Remove:g", "Authenticated Users", "BUILTIN\\Administrators", "BUILTIN", "Everyone", "System", "Users");
	}

	@Override
	protected LongBiConsumer updateProgressImpl(JFrame frame) {
		LongBiConsumer ui = newTransfer(frame);
		Taskbar taskbar = Taskbar.getTaskbar();
		return (current, full) -> {
			ui.accept(current, full);
			if (current == full)
				taskbar.setWindowProgressState(frame, full == -1 ? State.OFF : State.INDETERMINATE);
			else {
				taskbar.setWindowProgressState(frame, State.NORMAL);
				taskbar.setWindowProgressValue(frame, (int) ((double) current / full * 100));
			}
		};
	}
}