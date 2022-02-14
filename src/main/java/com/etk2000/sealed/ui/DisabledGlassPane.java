package com.etk2000.sealed.ui;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseMotionAdapter;

import javax.swing.JComponent;
import javax.swing.UIManager;

/*
 *  Simple implementation of a Glass Pane that will capture and ignore all
 *  events as well paint the glass pane to give the frame a "disabled" look.
 *
 *  The background color of the glass pane should use a color with an
 *  alpha value to create the disabled look.
 */
@SuppressWarnings("serial")
public class DisabledGlassPane extends JComponent implements KeyListener {
	public DisabledGlassPane() {
		setOpaque(false);
		Color base = UIManager.getColor("inactiveCaptionBorder");
		Color background = new Color(base.getRed(), base.getGreen(), base.getBlue(), 128);
		setBackground(background);
		setLayout(new GridBagLayout());

		addKeyListener(this);
		addMouseListener(new MouseAdapter() {
		});
		addMouseMotionListener(new MouseMotionAdapter() {
		});
		setFocusTraversalKeysEnabled(false);
	}

	/*
	 * The component is transparent but we want to paint the background to give it
	 * the disabled look.
	 */
	@Override
	protected void paintComponent(Graphics g) {
		g.setColor(getBackground());
		Dimension sz = getSize();
		g.fillRect(0, 0, sz.width, sz.height);
	}

	@Override
	public void keyPressed(KeyEvent e) {
		e.consume();
	}

	@Override
	public void keyReleased(KeyEvent e) {
		e.consume();
	}

	@Override
	public void keyTyped(KeyEvent e) {
	}

	public void show(JComponent component) {
		add(component, new GridBagConstraints());
		setVisible(true);
		setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
		requestFocusInWindow();
	}

	public void unShow() {
		setCursor(null);
		setVisible(false);
	}
}