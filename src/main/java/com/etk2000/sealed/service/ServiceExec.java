package com.etk2000.sealed.service;

import java.awt.Color;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.UIManager;

import com.etk2000.sealed.config.Config;
import com.etk2000.sealed.config.Server;
import com.etk2000.sealed.ui.JAnsiPane;
import com.etk2000.sealed.ui.MainFrame;
import com.etk2000.sealed.util.CommandConnection;
import com.etk2000.sealed.util.CommandConnection.ExecResult;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

public class ServiceExec {
	@SuppressWarnings("serial")
	private static class ServiceException extends IOException {
		ServiceException(String reason) {
			super(reason);
		}

		@Override
		public synchronized Throwable fillInStackTrace() {
			return this;// skip the costly operation of building a traceback
		}
	}

	public static final class Operation {
		private enum Type {
			exec, varList
		}

		private static final Color COMMAND = Color.BLUE, STDERR = Color.RED, STDOUT = Color.GRAY;

		// lookup vars, LOW: improve lookup via map or something
		private static void setVar(byte var, String val, List<String> vars) {
			if (var >= vars.size()) {
				while (var > vars.size())
					vars.add(null);
				vars.add(val);
			}
			else
				vars.set(var, val);
		}

		// lookup vars, LOW: improve lookup via map or something
		private static String varReplace(String str, List<String> vars) {
			String res = str;
			for (int var = vars.size() - 1; var >= 0; --var) {
				String val = vars.get(var);
				if (val != null)
					res = res.replace("$" + var, val);
			}
			return res;
		}

		final String name;
		final Type type;

		private final List<String> execs = new ArrayList<>(), options = new ArrayList<>();
		private final String title;
		private final byte var;

		Operation(JsonReader jr) throws IOException {
			name = jr.nextName();

			// is it a variable definition?
			if (name.startsWith("$$")) {
				type = Type.varList;// FIXME: support more types
				int index = name.indexOf(' ');
				title = name.substring(index + 1);
				var = Byte.parseByte(name.substring(2, index));

				// arrays are simple sets
				if (jr.peek() == JsonToken.BEGIN_ARRAY) {
					jr.beginArray();
					{
						while (jr.hasNext())
							options.add(jr.nextString());
					}
					jr.endArray();
				}

				// otherwise, it can set subsequent vars
				else {
					jr.beginObject();
					{
						while (jr.hasNext()) {
							options.add(jr.nextName());
							execs.add(jr.nextString());
						}
					}
					jr.endObject();
				}
			}

			// or just a simple execute
			else {
				type = Type.exec;
				title = null;
				var = 0;

				jr.beginArray();
				{
					while (jr.hasNext())
						execs.add(jr.nextString());
				}
				jr.endArray();
			}
		}

	
		void run(MainFrame parent, List<String> vars, JPanel output, boolean isRelaunch) throws IOException {
			switch (type) {
				case exec: {
					String serverName = varReplace(name, vars);
					Server server = Config.getServer(serverName);
					if (server == null)
						throw new ServiceException("server '" + serverName + "' not found");

					try (CommandConnection con = new CommandConnection(server)) {
						for (String exec : execs) {
							JAnsiPane log = new JAnsiPane(Color.GRAY);
							String command = varReplace(exec, vars);
							log.append(COMMAND, command + '\n');
							ExecResult res = con.exec(command);
							log.appendANSI(res.stdout.trim(), STDOUT);
							if (res.stderr.length() > 0)
								log.appendANSI('\n' + res.stderr.trim(), STDERR);

							// add in scroll if 10+ lines
							int lines = 0;
							String txt = log.getText();
							for (int i = 0; i != -1 && lines < 10;) {
								if ((i = txt.indexOf('\n', i + 1)) != -1)
									lines++;
							}
							output.add(lines >= 10 ? new JScrollPane(log) : log);
						}
					}
					break;
				}

				case varList: {
					if (isRelaunch && vars.size() > var)
						break;
					
					int selection = JOptionPane.showOptionDialog(parent, "Please select a value for '" + title + '\'', title, JOptionPane.CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE,
							null, options.toArray(new String[0]), null);

					if (selection == JOptionPane.CLOSED_OPTION)
						throw new ServiceException("value for '" + title + "' not selected");

					setVar(var, options.get(selection), vars);

					// if there's exec data associated, execute it
					if (execs.size() != 0) {
						// FIXME: allow more powerful logic
						String exec = execs.get(selection);

						if (exec.charAt(0) == '$') {
							int index = exec.indexOf(' ');
							if (index == -1)
								throw new ServiceException("invalid exec: " + exec);

							byte var = Byte.parseByte(exec.substring(1, index));
							index = exec.indexOf('=', index);
							if (index == -1)
								throw new ServiceException("invalid exec: " + exec);

							setVar(var, exec.substring(index + 1).trim(), vars);
						}
					}

					break;
				}
			}
		}
	}

	public final String name;
	final List<Operation> segments = new ArrayList<>();

	public ServiceExec(JsonReader jr) throws IOException {
		name = jr.nextName();

		jr.beginObject();
		{
			while (jr.hasNext())
				segments.add(new Operation(jr));
		}
		jr.endObject();
	}

	public List<String> run(MainFrame parent, JPanel output, List<String> relaunchVars) {
		List<String> vars = relaunchVars == null ? new ArrayList<>() : relaunchVars;
		for (Operation segment : segments) {
			try {
				segment.run(parent, vars, output, relaunchVars != null);
			}
			catch (IOException e) {
				JTextArea err = new JTextArea();
				err.setDisabledTextColor(Color.RED);
				err.setEnabled(false);
				err.setFont(UIManager.getFont("TextField.font"));
				output.add(err);

				if (e instanceof ServiceException)
					err.setText("Execution stopped at segment: " + segment + '\n' + e.getMessage());
				else
					err.setText("Execution failed for segment: " + segment + '\n' + e.getClass().getName() + ": " + e.getMessage());
				break;
			}
		}
		return vars;
	}
}