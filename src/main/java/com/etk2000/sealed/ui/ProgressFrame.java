package com.etk2000.sealed.ui;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;

@SuppressWarnings("serial")
public class ProgressFrame extends JDialog {
	// "${int:transfererId} ${long:current} ${long:full}\n"
	public static void writeTo(Process child, int transfererId, long current, long full) throws IOException {
		if (child.isAlive()) {
			child.getOutputStream().write((transfererId + " " + current + " " + full + "\n").getBytes(UTF_8));
			child.getOutputStream().flush();
		}
	}

	// LOW: maybe avoid autoboxing?
	private final Map<Integer, JProgressBar> progress = new HashMap<>();
	private volatile boolean running;

	public ProgressFrame() {
		setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		setAlwaysOnTop(true);
		setUndecorated(true);
	}

	public void doUpdate() {
		running = true;
		
		// try-with-resource isn't really needed, but oh well :P
		try (Scanner s = new Scanner(System.in, UTF_8.name())) {
			while (running) {
				int transfererId = s.nextInt();
				long current = s.nextLong(), full = s.nextLong();
				SwingUtilities.invokeLater(() -> updateOrAddProgress(transfererId, current, full));
			}
		}

		// if we got here, close the window (should also kill the process)
		dispose();
	}

	private void updateOrAddProgress(int transfererId, long current, long full) {
		JProgressBar bar = progress.get(transfererId);

		// if done, either remove progress bar or do nothing
		if (current == full && full == -1) {
			if (bar != null) {
				progress.remove(transfererId);
				remove(bar);
				if (progress.size() == 0)
					setVisible(false);
			}
		}

		else {
			setVisible(true);

			int _current = (int) (full > Integer.MAX_VALUE ? ((double) Integer.MAX_VALUE / full) * current : current);
			int _full = (int) Math.min(Integer.MAX_VALUE, full);

			// add a new progress bar if needed
			if (bar == null) {
				progress.put(transfererId, bar = new JProgressBar(0, _full));
				add(bar);
				pack();
				setLocationRelativeTo(null);
			}

			// update value
			if (current == full)
				bar.setIndeterminate(true);
			else {
				bar.setIndeterminate(false);
				bar.setValue(_current);
			}
		}
	}
}