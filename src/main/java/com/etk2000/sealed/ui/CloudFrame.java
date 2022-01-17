package com.etk2000.sealed.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridLayout;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.border.TitledBorder;

import com.etk2000.sealed.service.ServiceCloud;

@SuppressWarnings("serial")
public class CloudFrame extends JFrame {
	private static final Color COLOR_OTHER = Color.PINK, COLOR_RUNNING = Color.GREEN.darker(), COLOR_STOPPED = Color.LIGHT_GRAY;

	public CloudFrame(MainFrame parent, ServiceCloud cloud) {
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		setSize(800, 600);
		setTitle("Cloud: " + cloud.name());
		setLocationRelativeTo(parent);

		JPanel content = new JPanel(new GridLayout(0, 1));
		cloud.fetchServers().stream().sorted().forEachOrdered(server -> {
			Color titleColor = null;
			switch (server.state) {
				case other:
					titleColor = COLOR_OTHER;
					break;
				case running:
					titleColor = COLOR_RUNNING;
					break;
				case stopped:
					titleColor = COLOR_STOPPED;
					break;
			}

			JPanel outter = new JPanel(new BorderLayout());
			outter.setBorder(new TitledBorder(null, server.name, TitledBorder.CENTER, TitledBorder.TOP, null, titleColor));
			JPanel inner = new JPanel(new GridLayout(1, 0));

			inner.add(new JLabel(server.region));
			inner.add(new JLabel(server.type));
			outter.add(inner, BorderLayout.CENTER);
			content.add(outter);
		});
		setContentPane(new JScrollPane(content));
	}
}