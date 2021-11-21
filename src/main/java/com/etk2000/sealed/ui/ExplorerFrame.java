package com.etk2000.sealed.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Taskbar;
import java.awt.Taskbar.State;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;

import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTextField;

import com.etk2000.sealed.config.Server;
import com.etk2000.sealed.util.ExplorerConnection;

@SuppressWarnings("serial")
class ExplorerFrame extends JFrame {
	private final ExplorerConnection con;

	ExplorerFrame(MainFrame parent, Server srv) throws IOException {
		setBackground(Color.BLACK);
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

		JTextField cd = new JTextField();

		// setup upload/download progress bar
		Taskbar taskbar = Taskbar.getTaskbar();
		// setup the region to contain all objects in our cd
		ExplorerRemoteComponent remote = new ExplorerRemoteComponent(con, cd, (current, full) -> {
			if (current == full)
				taskbar.setWindowProgressState(this, full == -1 ? State.OFF : State.INDETERMINATE);
			else {
				taskbar.setWindowProgressState(this, State.NORMAL);
				taskbar.setWindowProgressValue(this, (int) ((double) current / full * 100));
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