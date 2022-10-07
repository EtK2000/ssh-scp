package com.etk2000.ssh_scp.platform;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileLock;
import java.util.Scanner;

import javax.swing.JOptionPane;

import com.etk2000.ssh_scp.util.AbstractServer;
import com.etk2000.ssh_scp.util.HeadlessUtil;
import com.etk2000.ssh_scp.util.Util;
import com.google.cloud.Tuple;

class PlatformMac extends PlatformLinux {
	private static final String[] HOMEBREW_PREFIX = { "/opt/homebrew/bin/", "/usr/local/bin/" };
	private static final String HOMEBREW = "brew";
	private static final String SSHPASS = "sshpass";

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

			// create osascript file and keep it locked for tamper proofing
			script.getParentFile().mkdirs();
			try {
				@SuppressWarnings("resource")
				FileOutputStream fos = new FileOutputStream(script);
				FileLock _lock = fos.getChannel().tryLock();
				if (_lock == null) {
					fos.close();
					HeadlessUtil.showMessageDialog(null, "Failed to lock " + script.getAbsolutePath(), "Error!", JOptionPane.ERROR_MESSAGE);
					return false;
				}
				lock = Tuple.of(fos, _lock);

				try (Scanner s = new Scanner(Util.getResource("/launch.applescript").openStream()).useDelimiter("\\Z")) {
					fos.write(s.next().getBytes(UTF_8));
					// LOW: if !s.hasNext() show error message of tampered JAR?
				}

				Runtime.getRuntime().addShutdownHook(new Thread(this::cleanup));

				// setup the commands to execute using the script
				sshKey = new String[] { ssh, "-i", "${key}", "${remote}" };
				sshPass = new String[] { brewPrefix + SSHPASS, "-p", "${pass}", ssh, "${remote}" };
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
	protected void runSSHImpl(AbstractServer srv, boolean newProcess) throws IllegalStateException, IOException {
		// on OSX, we need to send the command as a single argument
		if (newProcess)
			// FIXME: make this more efficient, i.e. don't generate new array here
			Util.run(Util.copyAndMerge(NEW_PROCESS_PREFIX, new String[] { String.join(" ", buildCommandSSH(srv, null)) }));
		else
			runSSH(srv, null, newProcess);
	}
}