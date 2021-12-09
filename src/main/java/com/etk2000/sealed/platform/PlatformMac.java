package com.etk2000.sealed.platform;

import java.io.IOException;

import com.etk2000.sealed.config.Server;
import com.etk2000.sealed.util.Util;

class PlatformMac extends PlatformLinux {
	@Override
	protected boolean ensureToolsExistImpl() {
		String sshpass = Util.runForResult("which sshpass", false);
		if (sshpass.length() == 0) {
			// FIXME: maybe allow running without?
			System.err.println("FIXME: maybe allow running without?");
			Util.runForResult("brew install hudochenkov/sshpass/sshpass", true);
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
		runSSH(srv, newProcess ? "open -a iTerm " : "", newProcess);
	}
}