package com.etk2000.sealed.service.exec;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;

public interface ExecLog {
	static final float DARK = 0.502f;

	static enum LogColor {
		BRIGHT_BLACK("\u001B[1;30m", Color.getHSBColor(0, 0, DARK)), //
		BRIGHT_BLUE("\u001B[1;34m", Color.getHSBColor(0.667f, 1, 1)), //
		BRIGHT_CYAN("\u001B[1;36m", Color.getHSBColor(0.5f, 1, 1)), //
		BRIGHT_GREEN("\u001B[1;32m", Color.getHSBColor(0.333f, 1, 1)), //
		BRIGHT_MAGENTA("\u001B[1;35m", Color.getHSBColor(0.833f, 1, 1)), //
		BRIGHT_RED("\u001B[1;31m", Color.getHSBColor(0, 1, 1)), //
		BRIGHT_WHITE("\u001B[1;37m", Color.getHSBColor(0, 0, 1)), //
		BRIGHT_YELLOW("\u001B[1;33m", Color.getHSBColor(0.167f, 1, 1)), //
		DARK_BLACK("\u001B[30m", Color.getHSBColor(0, 0, 0)), //
		DARK_BLUE("\u001B[34m", Color.getHSBColor(0.667f, 1, DARK)), //
		DARK_CYAN("\u001B[36m", Color.getHSBColor(0.5f, 1, DARK)), //
		DARK_GREEN("\u001B[32m", Color.getHSBColor(0.333f, 1, DARK)), //
		DARK_MAGENTA("\u001B[35m", Color.getHSBColor(0.833f, 1, DARK)), //
		DARK_RED("\u001B[31m", Color.getHSBColor(0, 1, DARK)), //
		DARK_WHITE("\u001B[37m", Color.getHSBColor(0, 0, 0.753f)), //
		DARK_YELLOW("\u001B[33m", Color.getHSBColor(0.167f, 1, DARK)),//
		
		//
		
		RESET("\u001B[0m", null);

		private static final Map<String, LogColor> LOOKUP = new HashMap<>();

		static {
			for (LogColor value : values())
				LOOKUP.put(value.ansi, value);
		}

		public final String ansi;
		public final Color color;

		private LogColor(String ansi, Color color) {
			this.ansi = ansi;
			this.color = color;
		}
		
		@Override
		public String toString() {
			return ansi;
		}

		public static LogColor getANSIColor(String escapeSq, LogColor base, LogColor current) {
			return escapeSq.equals(RESET.ansi) ? base : LOOKUP.getOrDefault(escapeSq, current);
		}
	}

	void append(LogColor c, String s);
	void appendANSI(String s);
	void appendANSI(String s, LogColor base);
}