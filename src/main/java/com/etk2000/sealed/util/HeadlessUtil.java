package com.etk2000.sealed.util;

import java.awt.Component;
import java.awt.GraphicsEnvironment;
import java.util.NoSuchElementException;
import java.util.Scanner;

import javax.swing.Icon;
import javax.swing.JOptionPane;

import com.etk2000.sealed.service.exec.ExecLog.LogColor;

public class HeadlessUtil {
	private static Scanner in;

	static {
		if (GraphicsEnvironment.isHeadless())
			in = new Scanner(System.in);
	}

	public static int showConfirmDialog(Component parentComponent, String message, String title, int optionType) {
		if (!GraphicsEnvironment.isHeadless())
			return JOptionPane.showConfirmDialog(parentComponent, message, title, optionType);

		for (;;) {
			String choice;
			System.out.println(LogColor.BRIGHT_BLUE + message);
			switch (optionType) {

				// 0/y 1/n
				case JOptionPane.YES_NO_OPTION:
					System.out.println("0) yes\n1) no" + LogColor.RESET);
					choice = in.next().toLowerCase();
					if (choice.isEmpty())
						break;
					if (choice.charAt(0) == '0' || choice.charAt(0) == 'y')
						return JOptionPane.YES_OPTION;
					if (choice.charAt(0) == '1' || choice.charAt(0) == 'n')
						return JOptionPane.NO_OPTION;
					break;

				// 0/y 1/n 2/c
				case JOptionPane.YES_NO_CANCEL_OPTION:
					System.out.println("0) yes\n1) no\n2) cancel" + LogColor.RESET);
					choice = in.next().toLowerCase();
					if (choice.isEmpty())
						break;
					if (choice.charAt(0) == '0' || choice.charAt(0) == 'y')
						return JOptionPane.YES_OPTION;
					if (choice.charAt(0) == '1' || choice.charAt(0) == 'n')
						return JOptionPane.NO_OPTION;
					if (choice.charAt(0) == '2' || choice.charAt(0) == 'c')
						return JOptionPane.CANCEL_OPTION;
					break;

				// 0/o 1/c
				case JOptionPane.OK_CANCEL_OPTION:
					System.out.println("0) ok\n1) cancel" + LogColor.RESET);
					choice = in.next().toLowerCase();
					if (choice.isEmpty())
						break;
					if (choice.charAt(0) == '0' || choice.charAt(0) == 'o')
						return JOptionPane.OK_OPTION;
					if (choice.charAt(0) == '1' || choice.charAt(0) == 'c')
						return JOptionPane.CANCEL_OPTION;
					break;
				default:
					throw new UnsupportedOperationException("not implemented yet");
			}
		}
	}

	public static void showMessageDialog(Component parentComponent, String message, String title, int messageType) {
		if (!GraphicsEnvironment.isHeadless()) {
			JOptionPane.showMessageDialog(parentComponent, message, title, messageType);
			return;
		}

		(messageType == JOptionPane.ERROR_MESSAGE ? System.err : System.out).println(message);
	}

	public static int showOptionDialog(Component parentComponent, String message, String title, int optionType, int messageType, Icon icon, Object[] options, Object initialValue) {
		if (!GraphicsEnvironment.isHeadless())
			return JOptionPane.showOptionDialog(parentComponent, message, title, optionType, messageType, icon, options, initialValue);

		for (;;) {
			System.out.println(LogColor.BRIGHT_BLUE + "Please select a value for '" + title + '\'');
			for (int i = 0; i < options.length; i++)
				System.out.println(i + ") " + options[i]);
			System.out.print(LogColor.RESET);

			try {
				int index = Integer.parseInt(in.next());// safer than in.nextInt()
				if (index >= 0 && index < options.length)
					return index;

				System.err.println(LogColor.BRIGHT_RED + "index must be >= 0 and < " + options.length + LogColor.RESET);
			}
			catch (NumberFormatException e) {
				System.err.println(LogColor.BRIGHT_RED + "expected a numeric index" + LogColor.RESET);
			}
			catch (NoSuchElementException e) {
				return JOptionPane.CLOSED_OPTION;
			}
		}
	}
}