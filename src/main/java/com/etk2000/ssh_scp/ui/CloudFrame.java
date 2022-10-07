package com.etk2000.ssh_scp.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.font.TextAttribute;
import java.io.IOException;
import java.util.Map;

import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.UIManager;
import javax.swing.border.TitledBorder;

import com.etk2000.ssh_scp.api.ServiceCloud;
import com.etk2000.ssh_scp.cloudprovider.CloudServer.ServerState;
import com.etk2000.ssh_scp.platform.Platform;
import com.etk2000.ssh_scp.util.Util;

@SuppressWarnings("serial")
public class CloudFrame extends JFrame {
	private static final Color COLOR_OTHER = Color.ORANGE, COLOR_RUNNING = Color.GREEN.darker(), COLOR_STOPPED = new Color(255, 95, 95),
			COLOR_STOPPING = Color.PINK, COLOR_TERMINATED = Color.LIGHT_GRAY, COLOR_TERMINATING = Color.GRAY;
	private static final Font FONT_TERMINATED = UIManager.getFont("TitledBorder.font")
			.deriveFont(Map.of(TextAttribute.STRIKETHROUGH, TextAttribute.STRIKETHROUGH_ON));

	public CloudFrame(MainFrame parent, ServiceCloud cloud) {
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		setSize(800, 600);
		setTitle("Cloud: " + cloud.name());
		setLocationRelativeTo(parent);

		JPanel content = new JPanel(new GridLayout(0, 1));
		cloud.fetchServers().stream().sorted().forEachOrdered(server -> {
			Font titleFont = null;
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
				case stopping:
					titleColor = COLOR_STOPPING;
					break;
				case terminated:
					titleColor = COLOR_TERMINATED;
					titleFont = FONT_TERMINATED;
					break;
				case terminating:
					titleColor = COLOR_TERMINATING;
					titleFont = FONT_TERMINATED;// LOW: maybe have different font?
					break;
			}

			JPanel outter = new JPanel(new BorderLayout());
			outter.setBorder(new TitledBorder(null, server.name, TitledBorder.CENTER, TitledBorder.TOP, titleFont, titleColor));
			JPanel inner = new JPanel(new GridLayout(1, 0));

			inner.add(new JLabel(server.region));
			inner.add(new JLabel(server.type));

			JPanel operations = new JPanel(new FlowLayout());

			// allow connection attempts if we have the required info
			// FIXME: need username and possibly fingerprint
			if (server.state == ServerState.running && server.keyName != null && server.user != null) {
				operations.add(UIUtil.createImageButton(UIManager.getIcon("FileView.hardDriveIcon"), () -> {
					try {
						new ExplorerFrame(this, server).setVisible(true);
					}
					catch (IllegalStateException | IOException e) {
						e.printStackTrace(); // FIXME: log in UI
					}
				}));
				operations.add(UIUtil.createImageButton(new ImageIcon(Util.getResource("/terminal.png")), () -> {
					try {
						Platform.runSSH(server, true);
					}
					catch (IllegalStateException | IOException e) {
						e.printStackTrace(); // FIXME: log in UI
					}
				}));
			}

			inner.add(operations);
			outter.add(inner, BorderLayout.CENTER);
			content.add(outter);
		});

		if (content.getComponentCount() == 0)
			content.add(new JLabel("No instances founds in cloud"));

		setContentPane(new JScrollPane(content));
	}
}