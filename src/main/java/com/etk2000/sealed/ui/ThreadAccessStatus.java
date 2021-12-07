package com.etk2000.sealed.ui;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.etk2000.sealed.config.Server.AreaAccess;
import com.etk2000.sealed.util.Util;

class ThreadAccessStatus extends Thread {
	private static AreaAccess getAccess() {
		if (Util.isUsingVPN())
			return AreaAccess.vpn;
		
		try {
			if (Util.isInOffice())
				return AreaAccess.office;
		}
		catch (IOException e) {
			if (e instanceof UnknownHostException)
				;// FIXME: return no Internet
			else
				e.printStackTrace();
		}

		return AreaAccess.anywhere;
	}

	private final List<Consumer<AreaAccess>> callbacks = new ArrayList<>();
	private volatile boolean running = false;
	private volatile AreaAccess currentAccess;

	ThreadAccessStatus() {
		setDaemon(true);
	}

	synchronized void addCallback(Consumer<AreaAccess> callback) {
		callbacks.add(callback);
		callback.accept(currentAccess);
	}

	synchronized void kill() {
		running = false;
	}

	synchronized void removeCallback(Consumer<AreaAccess> callback) {
		callbacks.remove(callback);
	}

	@Override
	public void run() {
		updateCallbacks(currentAccess = getAccess());

		while (running) {
			try {
				Thread.sleep(60_000);
			}
			catch (InterruptedException e) {
				e.printStackTrace();
			}

			AreaAccess newState = getAccess();
			if (newState != currentAccess)
				updateCallbacks(currentAccess = newState);
		}
	}

	@Override
	public synchronized void start() {
		running = true;
		super.start();
	}

	private synchronized void updateCallbacks(AreaAccess access) {
		callbacks.forEach(callback -> callback.accept(access));
	}
}