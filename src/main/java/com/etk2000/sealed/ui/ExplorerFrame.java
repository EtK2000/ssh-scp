package com.etk2000.sealed.ui;

import java.awt.BorderLayout;
import java.awt.Color;
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
import com.etk2000.sealed.platform.Platform;
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

		// setup the region to contain all objects in our cd
		ExplorerRemoteComponent remote = new ExplorerRemoteComponent(con, cd, Platform.updateProgress(this));

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