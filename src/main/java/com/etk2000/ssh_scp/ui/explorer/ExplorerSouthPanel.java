package com.etk2000.ssh_scp.ui.explorer;

import java.awt.BorderLayout;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTextField;

import com.etk2000.ssh_scp.util.ExplorerConnection;

@SuppressWarnings("serial")
class ExplorerSouthPanel extends JPanel {
	final JTextField cd;
	final JCheckBox compress;
	
	ExplorerSouthPanel(JFrame frame, ExplorerConnection con, ExplorerRemoteComponent remote) {
		super(new BorderLayout());
		cd = new JTextField();

		cd.addKeyListener(new KeyAdapter() {
			@Override
			public void keyTyped(KeyEvent e) {
				if (e.getKeyChar() == '\n') {
					// if the directory exists cd to it
					con.cd(cd.getText(), remote.getModel(), cd);
				}
			}
		});
		add(cd, BorderLayout.CENTER);
		
		compress = new JCheckBox("Compress Folders"); 
		add(compress, BorderLayout.EAST);
	}
}