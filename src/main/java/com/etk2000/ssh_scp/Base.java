package com.etk2000.ssh_scp;

import java.awt.GraphicsEnvironment;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;

import javax.swing.UIManager;

import org.slf4j.LoggerFactory;

import com.etk2000.ssh_scp.config.Config;
import com.etk2000.ssh_scp.config.Server;
import com.etk2000.ssh_scp.platform.Platform;
import com.etk2000.ssh_scp.service.ServiceExec;
import com.etk2000.ssh_scp.ui.MainFrame;
import com.etk2000.ssh_scp.ui.ProgressFrame;

public class Base {
	public static void main(String[] args) throws IOException {
		/*UIManagerDefaults.main(null);
		if (1 == 1)
			return;//*/
		
		
		System.setProperty("apple.awt.application.name", "SSH and SCP Utility");
		try {
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		}
		catch (Exception e) {
			e.printStackTrace();
		}

		String configFile = Config.FILE.getAbsolutePath(), service = null, sshEndpoint = null;
		for (int i = 0; i < args.length; ++i) {
			switch (args[i]) {

			// FIXME: add an argument to force running in headless mode

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
		if (!Config.loadWithRetry(configFile, null))
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