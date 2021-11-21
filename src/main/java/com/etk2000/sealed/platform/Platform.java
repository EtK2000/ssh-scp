package com.etk2000.sealed.platform;

import java.io.File;

import com.etk2000.sealed.keys.AuthKey;
import com.etk2000.sealed.util.Util;

public abstract class Platform {
	private static final Platform instance;

	static {
		String os = System.getProperty("os.name").toLowerCase();
		if (os.contains("win"))
			instance = new PlatformWindows();
		else if (os.contains("mac"))
			instance = new PlatformMac();
		else
			instance = new PlatformLinux();
	}
	
	public static File dir() {
		return instance.dir;
	}
	
	public static File dirKeys() {
		return instance.dirKeys;
	}
	
	public static File dirTmp() {
		return instance.dirTmp;
	}

	public static boolean ensureToolsExist() {
		// ensure we have a folder for our files
		if (!instance.dir.exists())
			instance.dir.mkdirs();
		return instance.ensureToolsExistImpl();
	}
	
	public static Platform instance() {
		return instance;
	}
	
	public static void runSSH(AuthKey key, String remote) {
		instance.runSSHImpl(key, remote);
	}
	
	public static void runSSH(String pass, String remote) {
		instance.runSSHImpl(pass, remote);
	}
	
	public static void setupKeyPerms(String path) {
		instance.setupKeyPermsImpl(path);
	}
	
	protected final File dir, dirKeys, dirTmp;
	protected String sshKey, sshPass;
	
	protected Platform(String container) {
		dir = new File(container, "sealed");
		dirKeys = new File(dir, "keys");
		dirTmp = new File(dir, "tmp");
	}

	protected boolean runSSH(AuthKey key, String remote, String prefix) {
		return Util.runForResult(prefix + sshKey.replace("${key}", '"' + key.path() + '"')//
				.replace("${remote}", remote)) != null;
	}

	protected boolean runSSH(String pass, String remote, String prefix) {
		return Util.runForResult(prefix + sshPass.replace("${pass}", pass).replace("${remote}", remote)) != null;
	}
	
	protected abstract boolean ensureToolsExistImpl();
	protected abstract void runSSHImpl(AuthKey key, String remote);
	protected abstract void runSSHImpl(String pass, String remote);
	protected abstract void setupKeyPermsImpl(String path);
}