package com.etk2000.sealed;

import java.awt.GraphicsEnvironment;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;

import javax.swing.JOptionPane;
import javax.swing.UIManager;

import org.slf4j.LoggerFactory;

import com.etk2000.sealed.config.Config;
import com.etk2000.sealed.config.Server;
import com.etk2000.sealed.platform.Platform;
import com.etk2000.sealed.service.exec.ServiceExec;
import com.etk2000.sealed.ui.MainFrame;
import com.etk2000.sealed.ui.ProgressFrame;
import com.etk2000.sealed.util.HeadlessUtil;

public class Base {
	private static boolean loadConfig(String file) throws IOException {
		for (;;) {
			try {
				Config.load(file);
				return true;
			}
			catch (IOException e) {
				switch (HeadlessUtil.showConfirmDialog(null, "Error loading config, retry file?\n" + e.getClass().getName() + ": " + e.getMessage(), "Try Again?",
						JOptionPane.YES_NO_CANCEL_OPTION)) {
					case JOptionPane.NO_OPTION:
						Config.delete();
						break;
					case JOptionPane.YES_OPTION:
						break;
					default:
						return false;
				}
			}
		}
	}

	public static void main(String[] args) throws IOException {
		try {
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		}
		catch (Exception e) {
			e.printStackTrace();
		}

		String configFile = null, service = null, sshEndpoint = null;
		for (int i = 0; i < args.length; i++) {
			switch (args[i]) {

				// if a config parameter was specified, load it
				case "-c":
				case "--config":
					if (i + 1 >= args.length) {
						System.err.println("argument " + args[i] + " lacks a parameter");
						return;
					}
					configFile = args[++i];
					break;

				// if this process was summoned as a progress indicator, function as one
				case "--progress":
					new ProgressFrame().doUpdate();
					return;

				// if a service was specified, execute it
				case "-s":
				case "--service":
					if (i + 1 >= args.length) {
						System.err.println("argument " + args[i] + " lacks a parameter");
						return;
					}
					service = args[++i];
					break;

				// if an SSH endpoint was specified, connect to it
				case "--ssh":
					if (i + 1 >= args.length) {
						System.err.println("argument " + args[i] + " lacks a parameter");
						return;
					}
					sshEndpoint = args[++i];
					break;
			}
		}

		shutupSLF4J();
		if (!loadConfig(configFile))
			return;

		if (!Platform.ensureToolsExist())
			return;// FIXME: show error dialog

		// run service if specified
		if (service != null) {
			String toExec = service;
			ServiceExec[] execs = Config.execs().filter(exec -> exec.name.equals(toExec)).toArray(ServiceExec[]::new);
			if (execs.length == 0) {
				System.err.println("no service found by name '" + toExec + '\'');
				return;
			}
			for (ServiceExec exec : execs)
				exec.run(null, null, null);
		}

		// SSH to endpoint if specified
		else if (sshEndpoint != null) {
			Server srv = Config.getServer(sshEndpoint);
			if (srv == null) {
				System.err.println("no server found by name '" + sshEndpoint + '\'');
				return;
			}

			Platform.runSSH(srv, false);
		}

		// run in headless mode if no UI
		else if (GraphicsEnvironment.isHeadless())
			System.err.println("FIXME: implement some console based UI");// FIXME:

		// otherwise, run normally
		else
			new MainFrame().setVisible(true);
	}

	private static void shutupSLF4J() {
		final PrintStream err = System.err;
		System.setErr(new PrintStream(new ByteArrayOutputStream()));
		try {
			LoggerFactory.getLogger("").error("");
		}
		finally {
			System.setErr(err);
		}
	}
}