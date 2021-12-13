package com.etk2000.sealed.platform;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileLock;

import com.etk2000.sealed.config.Server;
import com.etk2000.sealed.util.Util;
import com.google.cloud.Tuple;

class PlatformMac extends PlatformLinux {
	private static final String OSASCRIPT = "#!/usr/bin/osascript\n" + //
			"on run argv\n" + //
			" tell application \"iTerm2\"\n" + //
			"  set newWindow to (create window with default profile command argv)\n" + //
			" end tell\n" + //
			"end run";

	private final String[] NEW_PROCESS_PREFIX;
	private final File script;
	private Tuple<FileOutputStream, FileLock> lock;

	PlatformMac() {
		script = new File(dir, "launch.applescript");

		String osascript = Util.runForResult(false, "which", "osascript");
		if (osascript.length() == 0)// FIXME: deal with this state
			throw new IllegalStateException("osascript is required");

		NEW_PROCESS_PREFIX = new String[] { osascript, script.getAbsolutePath() };
	}

	private void cleanup() {
		try {
			lock.y().release();
		}
		catch (IOException e) {
			e.printStackTrace();
		}
		try {
			lock.x().close();
		}
		catch (IOException e) {
			e.printStackTrace();
		}
		lock = null;
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
			Util.runForResult(true, "brew", "install", "hudochenkov/sshpass/sshpass");
			sshpass = Util.runForResult(false, "which", "sshpass");
		}

		// ensure sshpass was installed
		if (sshpass.length() > 0) {

			// create osascript file and keep it locked for tamper proofing
			script.getParentFile().mkdirs();
			try {
				@SuppressWarnings("resource")
				FileOutputStream fos = new FileOutputStream(script);
				FileLock _lock = fos.getChannel().tryLock();
				if (_lock == null) {
					fos.close();
					System.err.println("failed to lock " + script.getAbsolutePath());
					return false;
				}
				lock = Tuple.of(fos, _lock);
				fos.write(OSASCRIPT.getBytes(UTF_8));

				Runtime.getRuntime().addShutdownHook(new Thread(this::cleanup));

				// setup the commands to execute usinf the script
				sshKey = new String[] { ssh, "-i", "${key}", "${remote}" };
				sshPass = new String[] { sshpass, "-p", "${pass}", ssh, "${remote}" };
			}
			catch (IOException e) {
				e.printStackTrace();
				return false;
			}
			return true;
		}
		return false;
	}

	@Override
	protected void runSSHImpl(Server srv, boolean newProcess) throws IOException {
		if (newProcess)
			// FIXME: make this more efficient, i.e. don't generate new array here
			Util.run(Util.copyAndMerge(NEW_PROCESS_PREFIX, new String[] { String.join(" ", buildCommandSSH(srv, null)) }));
		else
			runSSH(srv, null, newProcess);
	}
}