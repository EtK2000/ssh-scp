package com.etk2000.ssh_scp.ui.explorer;

import java.awt.BorderLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;

import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;

import com.etk2000.ssh_scp.Base;
import com.etk2000.ssh_scp.ui.DisabledGlassPane;
import com.etk2000.ssh_scp.ui.ProgressFrame;
import com.etk2000.ssh_scp.ui.TaskbarUtil;
import com.etk2000.ssh_scp.util.AbstractServer;
import com.etk2000.ssh_scp.util.ExplorerConnection;
import com.etk2000.ssh_scp.util.LongBiConsumer;

@SuppressWarnings("serial")
public class ExplorerFrame extends JFrame {
	private static final Object LOCK = new Object();
	private static int nextTransfererId;
	private static Process progress;

	private static LongBiConsumer newTransfer(JFrame frame) {
		synchronized (LOCK) {
			int transfererId = nextTransfererId++;

			// spawn a new thread/process to handle a progress window
			return (current, full) -> {
				TaskbarUtil.calculateTaskbarProgress(frame, current, full);
				
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

	public ExplorerFrame(JFrame parent, AbstractServer srv) throws IOException {
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		setLayout(new BorderLayout());
		setSize(800, 600);
		setTitle("Connection: " + srv.name() + " (" + srv.user() + '@' + srv.address() + ')');
		setLocationRelativeTo(parent);
		con = new ExplorerConnection(srv);

		addWindowStateListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				con.close();
			}
		});

		JProgressBar bar = new JProgressBar();

		DisabledGlassPane glassPane = new DisabledGlassPane();
		setGlassPane(glassPane);

		// setup the region to contain all objects in our cd
		ExplorerRemoteComponent remote = new ExplorerRemoteComponent(con, newTransfer(this), (transferred, totalSize) -> {
			TaskbarUtil.calculateTaskbarProgress(this, transferred, totalSize);

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

		ExplorerSouthPanel southPane = new ExplorerSouthPanel(this, con, remote);
		add(southPane, BorderLayout.SOUTH);

		// populate file list
		remote.setCdDisplay(southPane.cd);
		con.cd(con.cd(), remote.getModel(), southPane.cd);
	}
}