package com.etk2000.sealed.ui;

import static java.nio.charset.StandardCharsets.UTF_8;

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

import com.etk2000.sealed.service.exec.ExecLog;

@SuppressWarnings("serial")
public class JAnsiPane extends JTextPane implements ExecLog {
	private static boolean isValidHTTP(char c) {
		try {
			return !Character.isWhitespace(c) && (c == ':' || c == '/' || c == '?' || c == '=' || c == '&' || URLEncoder.encode(Character.toString(c), UTF_8.name()).length() == 1);
		}
		catch (UnsupportedEncodingException e) {
			e.printStackTrace();
			return false;
		}
	}

	private LogColor base, current;
	private String remaining = "";

	public JAnsiPane(LogColor initial) {
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

	@Override
	public void append(LogColor c, String s) {
		try {
			getDocument().insertString(getDocument().getLength(), s, StyleContext.getDefaultStyleContext().addAttribute(SimpleAttributeSet.EMPTY, StyleConstants.Foreground, c.color));
		}
		catch (BadLocationException e) {
			e.printStackTrace();// should never get here
		}
	}

	@Override
	public void appendANSI(String s, LogColor base) {
		final LogColor oldBase = this.base, oldCurrent = current;
		this.base = current = base;
		try {
			appendANSI(s);
		}
		finally {
			this.base = oldBase;
			current = oldCurrent;
		}
	}

	@Override
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
				current = LogColor.getANSIColor(str.substring(aPos, mIndex + 1), base, current);
				aPos = mIndex + 1;

				// if that was the last sequence, add the remaining text and finish
				if ((aIndex = str.indexOf('\u001B', aPos)) == -1) {
					append(current, str.substring(aPos, str.length()));
					break;
				}

				// there are more escape sequences, so append until the next one
				append(current, str.substring(aPos, aIndex));
				aPos = aIndex;
			}
		}
	}

	@Override
	public void setEditable(boolean b) {
	}// NOP due to super() calling it
}