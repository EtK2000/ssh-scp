package com.etk2000.ssh_scp.ui;

import java.awt.Cursor;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLabel;

class UIUtil {
	static JComponent createImageButton(Icon icon, Runnable onClick) {
		JLabel res = new JLabel(icon);
		res.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				onClick.run();
			}
		});
		res.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		return res;
	}

	private UIUtil() {
	}
}