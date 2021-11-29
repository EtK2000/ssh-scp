package com.etk2000.sealed.platform;

import java.awt.Taskbar;
import java.awt.Taskbar.State;
import java.io.File;
import java.io.IOException;

import javax.swing.JFrame;

import com.etk2000.sealed.keys.AuthKey;
import com.etk2000.sealed.util.LongBiConsumer;
import com.etk2000.sealed.util.Util;

class PlatformWindows extends Platform {
	PlatformWindows() {
		super(System.getenv("APPDATA"));
	}

	@Override
	protected boolean ensureToolsExistImpl() {
		// use SSHPASS via WSL for SSH with password, and SSH via WSL if no openSSH,
		// or fallback to PuTTY if neither exist
		File openssh = new File(System.getenv("WINDIR") + "/System32/OpenSSH/ssh.exe");
		if (openssh.exists())
			sshKey = openssh.getAbsolutePath() + " -i ${key} ${remote}";

		// TODO: fetch paths from result of which?
		System.err.println("TODO: fetch paths from result of which?");
		File wslFile = new File(System.getenv("WINDIR") + "/System32/wsl.exe");
		if (wslFile.exists()) {
			final String wsl = '"' + wslFile.getAbsolutePath() + '"';
			String sshpass = Util.runForResult(wsl + " which sshpass");
			if (sshpass.length() == 0) {
				Util.runForResult("cmd /c start /WAIT \"\" " + wsl + " sudo apt-get install sshpass");
				sshpass = Util.runForResult(wsl + " which sshpass");
			}

			// ensure sshpass was installed
			if (sshpass.length() > 0)
				sshPass = wsl + " sshpass -p ${pass} ssh ${remote}";

			// if we don't have ssh, see if we have it via wsl
			if (sshKey == null) {
				String ssh = Util.runForResult(wsl + " which ssh");
				if (ssh.length() == 0) {
					Util.runForResult("cmd /c start /WAIT \"\" " + wsl + " sudo apt-get install ssh");
					ssh = Util.runForResult(wsl + " which ssh");
				}

				// check if ssh was installed
				if (ssh.length() > 0) {
					// FIXME: fix key location
					System.err.println("FIXME: fix key location");
					sshKey = wsl + " ssh -i ${key} ${remote}";
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
			sshPass = putty.getAbsolutePath() + " -pw ${pass} ${remote}";

		return true;
	}

	// attempt to run in Windows Terminal, but fallback to cmd
	@Override
	protected void runSSHImpl(AuthKey key, String remote) throws IOException {
		try {
			runSSH(key, remote, "wt nt ");
		}
		catch (IOException e) {
			if (e.getMessage().contains("cannot find the file"))
				runSSH(key, remote, "cmd /c start \"\" ");
			else
				throw e;
		}
	}

	// attempt to run in Windows Terminal, but fallback to cmd
	@Override
	protected void runSSHImpl(String pass, String remote) throws IOException {
		try {
			runSSH(pass, remote, "wt nt ");
		}
		catch (IOException e) {
			if (e.getMessage().contains("cannot find the file"))
				runSSH(pass, remote, "cmd /c start \"\" ");
			else
				throw e;
		}
	}

	@Override
	protected void setupKeyPermsImpl(String path) {
		// set 700 perms
		Util.runForResult("icacls \"" + path + "\" /c /t /Inheritance:d");
		Util.runForResult("icacls \"" + path + "\" /c /t /Grant " + System.getProperty("user.name") + ":F");
		Util.runForResult("icacls \"" + path + "\" /c /t /Remove:g \"Authenticated Users\" BUILTIN\\Administrators BUILTIN Everyone System Users");
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