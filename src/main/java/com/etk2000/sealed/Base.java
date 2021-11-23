package com.etk2000.sealed;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;

import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.UIManager;

import org.slf4j.LoggerFactory;

import com.etk2000.sealed.config.Config;
import com.etk2000.sealed.platform.Platform;
import com.etk2000.sealed.ui.MainFrame;

public class Base {
	private static boolean loadConfig() throws IOException {
		for (;;) {
			try {
				Config.load();
				return true;
			}
			catch (IOException e) {
				switch (JOptionPane.showConfirmDialog(null, "Error loading config, retry file?\n" + e.getClass().getName() + ": " + e.getMessage(), "Try Again?",
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
		shutupSLF4J();

		try {
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		}
		catch (Exception e) {
			e.printStackTrace();
		}

		// if this process was summoned as a progress indicator, function as one
		for (String arg : args) {
			if (arg.equals("\0progress")) {
				// FIXME: implement
				new JFrame("FIXME: implement").setVisible(true);
				return;
			}
		}

		if (!loadConfig())
			return;

		if (!Platform.ensureToolsExist())
			return;// FIXME: show error dialog

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