package com.etk2000.sealed.ui;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.awt.Color;
import java.awt.Desktop;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;

import javax.swing.JTextPane;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyleContext;

@SuppressWarnings("serial")
public class JAnsiPane extends JTextPane {
	private static boolean isValidHTTP(char c) {
		try {
			return !Character.isWhitespace(c) && (c == ':' || c == '/' || c == '?' || c == '=' || c == '&' || URLEncoder.encode(Character.toString(c), UTF_8.name()).length() == 1);
		}
		catch (UnsupportedEncodingException e) {
			e.printStackTrace();
			return false;
		}
	}

	private static final float DARK = 0.502f;

	private static final Color BRIGHT_BLACK = Color.getHSBColor(0, 0, DARK);
	private static final Color BRIGHT_BLUE = Color.getHSBColor(0.667f, 1, 1);
	private static final Color BRIGHT_CYAN = Color.getHSBColor(0.5f, 1, 1);
	private static final Color BRIGHT_GREEN = Color.getHSBColor(0.333f, 1, 1);
	private static final Color BRIGHT_MAGENTA = Color.getHSBColor(0.833f, 1, 1);
	private static final Color BRIGHT_RED = Color.getHSBColor(0, 1, 1);
	private static final Color BRIGHT_WHITE = Color.getHSBColor(0, 0, 1);
	private static final Color BRIGHT_YELLOW = Color.getHSBColor(0.167f, 1, 1);
	private static final Color DARK_BLACK = Color.getHSBColor(0, 0, 0);
	private static final Color DARK_BLUE = Color.getHSBColor(0.667f, 1, DARK);
	private static final Color DARK_CYAN = Color.getHSBColor(0.5f, 1, DARK);
	private static final Color DARK_GREEN = Color.getHSBColor(0.333f, 1, DARK);
	private static final Color DARK_MAGENTA = Color.getHSBColor(0.833f, 1, DARK);
	private static final Color DARK_RED = Color.getHSBColor(0, 1, DARK);
	private static final Color DARK_WHITE = Color.getHSBColor(0, 0, 0.753f);
	private static final Color DARK_YELLOW = Color.getHSBColor(0.167f, 1, DARK);

	private Color base, current;
	private String remaining = "";

	public JAnsiPane(Color initial) {
		base = current = initial;
		super.setEditable(false);

		// allow double clicking to open URLs
		addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				try {
					if (e.getClickCount() == 2) {
						final int startOffset = viewToModel2D(new Point(e.getX(), e.getY()));
						if (startOffset == -1 || startOffset >= getDocument().getLength())
							return;
						
						final String text = getDocument().getText(0, getDocument().getLength());

						// get whole URL
						int end = startOffset, start = startOffset;
						while (isValidHTTP(text.charAt(start)))
							start--;
						while (isValidHTTP(text.charAt(end)))
							end++;

						if (start < end) {
							String sub = text.substring(++start, end);

							// ensure protocol is either HTTP or HTTPS and if so, open
							if (sub.startsWith("http://") || sub.startsWith("https://")) {
								select(start, end);
								try {
									if (Desktop.getDesktop().isSupported(Desktop.Action.BROWSE))
										Desktop.getDesktop().browse(new URI(sub));
								}
								catch (IOException | URISyntaxException ex) {
									ex.printStackTrace();
								}
							}
						}
					}
				}
				catch (BadLocationException ex) {
					ex.printStackTrace();
				}
			}
		});
	}

	public void append(Color c, String s) {
		try {
			getDocument().insertString(getDocument().getLength(), s, StyleContext.getDefaultStyleContext().addAttribute(SimpleAttributeSet.EMPTY, StyleConstants.Foreground, c));
		}
		catch (BadLocationException e) {
			e.printStackTrace();// should never get here
		}
	}

	public void appendANSI(String s, Color base) {
		final Color oldBase = this.base, oldCurrent = current;
		this.base = current = base;
		try {
			appendANSI(s);
		}
		finally {
			this.base = oldBase;
			current = oldCurrent;
		}
	}

	public void appendANSI(String s) {
		int aPos = 0, aIndex, mIndex;
		String str = remaining + s;
		remaining = "";

		if (str.length() > 0) {

			// if no escape, just append the text
			if ((aIndex = str.indexOf('\u001B')) == -1) {
				append(current, str);
				return;
			}

			// if escape isn't at the start, append until escape
			if (aIndex > 0) {
				append(current, str.substring(0, aIndex));
				aPos = aIndex;// the beginning of the first escape sequence
			}

			// loop until we have appended all the text
			for (;;) {

				// FIXME: if the escape sequence isn't full
				if ((mIndex = str.indexOf('m', aPos)) == -1) {
					remaining = str.substring(aPos, str.length());
					break;
				}

				// FIXME: add support for other escapes (background, underline, clear, etc)
				current = getANSIColor(str.substring(aPos, mIndex + 1), current);
				aPos = mIndex + 1;
// now we have the color, send text that is in that color (up to next escape)

				// if that was the last sequence, add the remaining text and finish
				if ((aIndex = str.indexOf('\u001B', aPos)) == -1) {
					append(current, str.substring(aPos, str.length()));
					break;
				}

				// there are more escape sequences, append until the next one
				append(current, str.substring(aPos, aIndex));
				aPos = aIndex;
			}
		}
	}

	public Color getANSIColor(String escapeSq, Color current) {
		switch (escapeSq) {
			case "\u001B[30m":
				return DARK_BLACK;
			case "\u001B[31m":
				return DARK_RED;
			case "\u001B[32m":
				return DARK_GREEN;
			case "\u001B[33m":
				return DARK_YELLOW;
			case "\u001B[34m":
				return DARK_BLUE;
			case "\u001B[35m":
				return DARK_MAGENTA;
			case "\u001B[36m":
				return DARK_CYAN;
			case "\u001B[37m":
				return DARK_WHITE;
			case "\u001B[0;30m":
				return DARK_BLACK;
			case "\u001B[0;31m":
				return DARK_RED;
			case "\u001B[0;32m":
				return DARK_GREEN;
			case "\u001B[0;33m":
				return DARK_YELLOW;
			case "\u001B[0;34m":
				return DARK_BLUE;
			case "\u001B[0;35m":
				return DARK_MAGENTA;
			case "\u001B[0;36m":
				return DARK_CYAN;
			case "\u001B[0;37m":
				return DARK_WHITE;
			case "\u001B[1;30m":
				return BRIGHT_BLACK;
			case "\u001B[1;31m":
				return BRIGHT_RED;
			case "\u001B[1;32m":
				return BRIGHT_GREEN;
			case "\u001B[1;33m":
				return BRIGHT_YELLOW;
			case "\u001B[1;34m":
				return BRIGHT_BLUE;
			case "\u001B[1;35m":
				return BRIGHT_MAGENTA;
			case "\u001B[1;36m":
				return BRIGHT_CYAN;
			case "\u001B[1;37m":
				return BRIGHT_WHITE;
			case "\u001B[0m":
				return base;
			default:
				return current;
		}
	}

	@Override
	public void setEditable(boolean b) {
	}// NOP due to super() calling it
}