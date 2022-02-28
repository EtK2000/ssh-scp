package com.etk2000.sealed.ui;

import java.awt.BorderLayout;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;

import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextField;

import com.etk2000.sealed.Base;
import com.etk2000.sealed.config.Server;
import com.etk2000.sealed.util.ExplorerConnection;
import com.etk2000.sealed.util.LongBiConsumer;

@SuppressWarnings("serial")
class ExplorerFrame extends JFrame {
	private static final Object LOCK = new Object();
	private static int nextTransfererId;
	private static Process progress;

	private static LongBiConsumer newTransfer(JFrame frame) {
		synchronized (LOCK) {
			int transfererId = nextTransfererId++;

			// spawn a new thread/process to handle a progress window
			return (current, full) -> {
				Util.calculateTaskbarProgress(frame, current, full);
				
				synchronized (LOCK) {
					if (progress == null) {
						try {
							File thisJar = new File(Base.class.getProtectionDomain().getCodeSource().getLocation().toURI());

							// if running as a jar, just sub-process it
							if (thisJar.isFile())
								progress = Runtime.getRuntime().exec("java -jar \"" + thisJar.getPath() + "\" --progress");

							// otherwise, we gotta setup the classpath and run ourself...
							// LOW: setup classpath by loading pom.xml and concatenating with
							// (System.getProperty("user.home") + "/.m2")
							else
								progress = Runtime.getRuntime().exec("java -cp \"" + thisJar.getPath() + "\" " + Base.class.getName() + " --progress");

							Runtime.getRuntime().addShutdownHook(new Thread(progress::destroyForcibly));
						}
						catch (IOException | URISyntaxException e) {
							e.printStackTrace();
						}
					}

					// write the update to our child process, LOW: respawn process if dead?
					try {
						ProgressFrame.writeTo(progress, transfererId, current, full);
					}
					catch (IOException e) {
						e.printStackTrace();
					}
				}
			};
		}
	}

	private final ExplorerConnection con;

	ExplorerFrame(MainFrame parent, Server srv) throws IOException {
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		setLayout(new BorderLayout());
		setSize(800, 600);
		setTitle("Connection: " + srv.name + " (" + srv.user + '@' + srv.address() + ')');
		setLocationRelativeTo(parent);
		con = new ExplorerConnection(srv);

		addWindowStateListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				con.close();
			}
		});

		DisabledGlassPane glassPane = new DisabledGlassPane();
		setGlassPane(glassPane);
		JProgressBar bar = new JProgressBar();

		JTextField cd = new JTextField();

		// setup the region to contain all objects in our cd
		ExplorerRemoteComponent remote = new ExplorerRemoteComponent(con, cd, newTransfer(this), (transferred, totalSize) -> {
			Util.calculateTaskbarProgress(this, transferred, totalSize);

			if (transferred == totalSize && totalSize == -1)
				glassPane.unShow();

			else {
				glassPane.show(bar);// LOW: only call show(bar) if needed?

				// update value
				if (transferred == totalSize)
					bar.setIndeterminate(true);
				else {
					int _current = (int) (totalSize > Integer.MAX_VALUE ? ((double) Integer.MAX_VALUE / totalSize) * transferred : transferred);
					int _full = (int) Math.min(Integer.MAX_VALUE, totalSize);
					
					bar.setIndeterminate(false);
					bar.setMaximum(_full);
					bar.setValue(_current);
				}
			}
		});

		JScrollPane remoteScroll = new JScrollPane(remote);
		remoteScroll.setBorder(BorderFactory.createTitledBorder("Remote"));
		add(remoteScroll, BorderLayout.CENTER);

		cd.addKeyListener(new KeyAdapter() {
			@Override
			public void keyTyped(KeyEvent e) {
				if (e.getKeyChar() == '\n') {
					// if the directory exists cd to it
					con.cd(cd.getText(), remote.getModel(), cd);
				}
			}
		});
		add(cd, BorderLayout.SOUTH);

		// populate file list
		con.cd(con.cd(), remote.getModel(), cd);
	}
}