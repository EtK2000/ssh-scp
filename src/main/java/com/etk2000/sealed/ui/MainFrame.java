package com.etk2000.sealed.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.ItemSelectable;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButton;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.border.TitledBorder;

import com.etk2000.sealed.config.Config;
import com.etk2000.sealed.config.Server;
import com.etk2000.sealed.config.Server.AreaAccess;
import com.etk2000.sealed.platform.Platform;
import com.etk2000.sealed.service.ServiceCloud;
import com.etk2000.sealed.service.exec.ServiceExec;

@SuppressWarnings("serial")
public class MainFrame extends JFrame {
	private static final Border BORDER_NO_TABS = BorderFactory.createEmptyBorder(10, 10, 0, 10), BORDER_TABS = BorderFactory.createEmptyBorder(0, 10, 0, 10);

	private final ThreadAccessStatus threadAccessCheck = new ThreadAccessStatus();
	private final JTabbedPane center;
	private final JPanel north, south;
	private JComponent focusMode;

	public MainFrame() {
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		setLayout(new BorderLayout());
		setSize(800, 600);
		setTitle(System.getProperty("apple.awt.application.name"));
		setLocationRelativeTo(null);

		// setup the options in the south area of the window
		south = new JPanel(new FlowLayout());
		ButtonGroup openType = new ButtonGroup();
		JRadioButton explorer = new JRadioButton("File Explorer (SCP)");
		south.add(explorer);
		openType.add(explorer);
		JRadioButton ssh = new JRadioButton("Secure SHell (SSH)");
		south.add(ssh);
		openType.add(ssh);
		ssh.setSelected(true);
		add(south, BorderLayout.SOUTH);

		// setup tabs for outputs
		JTabbedPane tabs = center = new JTabbedPane() {
			@Override
			public void removeTabAt(int index) {
				super.removeTabAt(index);
				if (getTabCount() == 0 && focusMode == this)
					toggleFocusedMode(null);
			}
		};
		tabs.setBorder(BORDER_NO_TABS);
		tabs.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				final int index = tabs.getUI().tabForCoordinate(tabs, e.getX(), e.getY());
				if (index != -1) {

					// left double click toggle maximize
					if (SwingUtilities.isLeftMouseButton(e)) {
						if (e.getClickCount() == 2)
							toggleFocusedMode(tabs);
					}

					// mouse wheel close
					else if (SwingUtilities.isMiddleMouseButton(e))
						tabs.removeTabAt(index);

					// right click popup
					else if (SwingUtilities.isRightMouseButton(e)) {
						// FIXME: have popup as a final field
						final JPopupMenu popupMenu = new JPopupMenu();

						// add "Relaunch" if this tab can be relaunched
						final Component tab = tabs.getTabComponentAt(index);
						if (tab != null && tab instanceof RelaunchTab) {
							final JMenuItem relaunch = new JMenuItem("Relaunch");
							relaunch.addActionListener(x -> ((RelaunchTab) tab).relaunch());
							popupMenu.add(relaunch);
						}

						// add "Relaunch All" if any tabs other than this can be relaunched
						for (int i = 0; i < tabs.getTabCount(); i++) {
							if (i != index && tabs.getTabComponentAt(i) instanceof RelaunchTab) {
								final JMenuItem relaunchAll = new JMenuItem("Relaunch All");
								relaunchAll.addActionListener(x -> {
									for (int _i = 0; _i < tabs.getTabCount(); _i++) {
										Component _tab = tabs.getTabComponentAt(_i);
										if (_tab instanceof RelaunchTab)
											((RelaunchTab) _tab).relaunch();
									}
								});
								popupMenu.add(relaunchAll);
								break;
							}
						}

						// add "Close" because all tabs can be closed
						final JMenuItem close = new JMenuItem("Close");
						close.addActionListener(x -> tabs.removeTabAt(index));
						popupMenu.add(close);

						// add "Close All" if there are any other tabs
						if (tabs.getTabCount() > 1) {
							final JMenuItem closeAll = new JMenuItem("Close all");
							closeAll.addActionListener(x -> tabs.removeAll());
							popupMenu.add(closeAll);
						}

						final Rectangle tabBounds = tabs.getBoundsAt(index);
						popupMenu.show(tabs, tabBounds.x, tabBounds.y + tabBounds.height);
					}

					if (tabs.getTabCount() == 0)
						tabs.setBorder(BORDER_NO_TABS);
				}
			}
		});
		add(tabs, BorderLayout.CENTER);

		// setup the connections list in the north of the window
		north = new JPanel(new GridBagLayout());
		GridBagConstraints c = new GridBagConstraints();
		c.gridx = c.gridy = 0;
		{
			// group all servers by category and then sort alphabetically
			Config.servers().collect(Collectors.groupingBy(server -> server.category)).entrySet().stream().sorted((a, b) -> a.getKey().compareTo(b.getKey())).forEach(e -> {
				JPanel currentPanel = new JPanel(new GridLayout(1, 0));
				currentPanel.setBorder(new TitledBorder(null, e.getKey(), TitledBorder.CENTER, TitledBorder.TOP));
				for (Server srv : e.getValue()) {
					JButton btn = new JButton(srv.name);
					btn.addActionListener(x -> connect(srv, ssh.isSelected()));
					currentPanel.add(btn);

					if (srv.access != AreaAccess.anywhere) {
						threadAccessCheck.addCallback(access -> {
							boolean hasAccess = (srv.access != AreaAccess.vpn && access == AreaAccess.office) || access == AreaAccess.vpn;
							btn.setEnabled(hasAccess);
							btn.setToolTipText(hasAccess ? null : srv.access == AreaAccess.vpn ? "requires VPN" : "requires VPN or office");
						});
					}
				}
				north.add(currentPanel, c);
				c.gridy++;
			});

			// add clouds
			JPanel clouds = new JPanel(new FlowLayout());
			clouds.setBorder(new TitledBorder(null, "Clouds", TitledBorder.CENTER, TitledBorder.TOP));
			Config.clouds().sorted(Comparator.comparing(ServiceCloud::name)).forEach(cloud -> {
				JButton btn = new JButton(cloud.name());
				btn.addActionListener(x -> showCloud(cloud));
				clouds.add(btn);
			});
			if (clouds.getComponentCount() > 0) {
				north.add(clouds, c);
				c.gridy++;
			}

			// add execs
			JPanel execs = new JPanel(new FlowLayout());
			execs.setBorder(new TitledBorder(null, "Execs", TitledBorder.CENTER, TitledBorder.TOP));
			Config.execs().sorted((a, b) -> a.name.compareTo(b.name)).forEach(exec -> {
				JButton btn = new JButton(exec.name);
				btn.addActionListener(x -> run(exec, null, null));

				// FIXME: check server access before
				/*
				 * if (srv.access != AreaAccess.anywhere) { threadAccessCheck.addCallback(access
				 * -> { boolean hasAccess = (srv.access != AreaAccess.vpn && access ==
				 * AreaAccess.office) || access == AreaAccess.vpn; btn.setEnabled(hasAccess);
				 * btn.setToolTipText(hasAccess ? null : srv.access == AreaAccess.vpn ?
				 * "requires VPN" : "requires VPN or office"); }); }
				 */
				execs.add(btn);
			});
			if (execs.getComponentCount() > 0) {
				north.add(execs, c);
				c.gridy++;
			}
		}

		add(north, BorderLayout.NORTH);
	}

	private void connect(Server srv, boolean ssh) {
		setEnabled(this, false, false);// disable clicking until new window opens
		new Thread(() -> {
			try {
				if (ssh)
					Platform.runSSH(srv, true);
				else if (srv.proxy() == null)
					new ExplorerFrame(this, srv).setVisible(true);
				else// FIXME: SCP via proxy isn't support yet :(
					logError("Error in SCP", "SCP via proxy isn't support yet :(");
			}
			catch (IllegalStateException e) {
				logError("Error in " + (ssh ? "SSH" : "SCP"), e.getMessage());
			}
			catch (IOException e) {
				logException("Error in " + (ssh ? "SSH" : "SCP"), e);
			}
			finally {// restore clicking
				setEnabled(this, true, true);
			}
		}).start();
	}

	private void logError(String title, String error) {
		JTextArea err = new JTextArea();
		err.setForeground(Color.RED);
		err.setEditable(false);
		err.setFont(UIManager.getFont("TextField.font"));

		err.setText(error);
		center.add(title, err);
	}

	private void logException(String title, Exception e) {
		JTextArea err = new JTextArea();
		err.setForeground(Color.RED);
		err.setEditable(false);
		err.setFont(UIManager.getFont("TextField.font"));

		err.setText("Caught exception:\n" + e.getClass().getName() + ": " + e.getMessage());
		e.printStackTrace();
		center.add(title, err);
	}

	void run(ServiceExec exec, RelaunchTab tab, List<String> relaunchVars) {
		JPanel loading = new JPanel(new BorderLayout());
		loading.add(new JLabel(new ImageIcon(getClass().getResource("/ajax-loader.gif"))), BorderLayout.CENTER);

		{
			int index = center.indexOfTabComponent(tab);

			// if it's a relaunch, update the existing tab
			if (tab != null && index != -1) {
				center.setComponentAt(index, loading);
				center.setTabComponentAt(index, null);
				center.setTitleAt(index, exec.name + " - in progress");
			}

			// otherwise, add a new tab
			else
				center.add(exec.name + " - in progress", loading);
		}

		center.setBorder(BORDER_TABS);

		// process in a background thread
		new Thread(() -> {
			JPanel out = new JPanel();
			out.setLayout(new BoxLayout(out, BoxLayout.Y_AXIS));
			List<String> vars = exec.run(this, out, relaunchVars);
			String varsCat = vars.size() > 0 ? vars.stream().collect(Collectors.joining(", ", " (", ")")) : "";

			// run on UI thread bcs reasons
			SwingUtilities.invokeLater(() -> {
				int index = center.indexOfComponent(loading);
				String title = exec.name + varsCat;

				// modify the loading tab
				if (index != -1) {
					center.setComponentAt(index, out);// FIXME: new JScrollPane(out)
					center.setTitleAt(index, title);
				}

				// or if it was closed, open a new tab
				else {
					center.add(title, out);// FIXME: new JScrollPane(out)
					index = center.indexOfComponent(out);
				}

				center.setSelectedIndex(index);
				center.setTabComponentAt(index, new RelaunchTab(this, title, exec, vars));
			});
		}).start();
	}

	private void setEnabled(Container container, boolean enabled, boolean poll) {
		for (Component comp : container.getComponents()) {
			if (comp instanceof ItemSelectable)
				comp.setEnabled(enabled);
			else if (comp instanceof Container)
				setEnabled((Container) comp, enabled, false);
			else
				comp.setEnabled(enabled);
		}

		//
		if (poll)
			threadAccessCheck.pollNow();
	}

	@Override
	public void setVisible(boolean b) {
		if (b)
			threadAccessCheck.start();
		else
			threadAccessCheck.kill();
		super.setVisible(b);
	}

	private void showCloud(ServiceCloud cloud) {
		setEnabled(this, false, false);// disable clicking until new window opens
		new Thread(() -> {
			try {
				new CloudFrame(this, cloud).setVisible(true);
			}
			finally {// restore clicking
				setEnabled(this, true, true);
			}
		}).start();
	}

	private void toggleFocusedMode(JComponent c) {
		if (c == null || c == focusMode) {
			if (c != null)
				remove(c);
			focusMode = null;
			add(north, BorderLayout.NORTH);
			add(center, BorderLayout.CENTER);
			add(south, BorderLayout.SOUTH);
		}
		else {
			remove(north);
			remove(center);
			remove(south);
			add(focusMode = c, BorderLayout.CENTER);
		}
		revalidate();
	}
}