package com.etk2000.sealed.service.exec;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JPanel;
import javax.swing.JScrollPane;

import com.etk2000.sealed.config.Config;
import com.etk2000.sealed.config.Server;
import com.etk2000.sealed.headless.ConsoleExecLog;
import com.etk2000.sealed.service.ServiceException;
import com.etk2000.sealed.service.exec.ExecLog.LogColor;
import com.etk2000.sealed.ui.JAnsiPane;
import com.etk2000.sealed.ui.MainFrame;
import com.etk2000.sealed.util.CommandConnection;
import com.etk2000.sealed.util.CommandConnection.ExecResult;
import com.google.gson.stream.JsonReader;

public class OperationExec extends Operation {
	private final List<String> execs = new ArrayList<>();
	private final String name;

	// <server> [commands...]
	OperationExec(String name, JsonReader jr) throws IOException {
		this.name = name;
		jr.beginArray();
		{
			while (jr.hasNext())
				execs.add(jr.nextString());
		}
		jr.endArray();
	}

	@Override
	void run(MainFrame parent, List<String> vars, JPanel output, boolean isRelaunch) throws IOException {
		String serverName = varReplace(name, vars);
		Server server = Config.getServer(serverName);
		if (server == null)
			throw new ServiceException("server '" + serverName + "' not found");

		try (CommandConnection con = new CommandConnection(server)) {
			for (String exec : execs) {
				// if first char is '@' don't re-echo the command in the log
				boolean noEcho = exec.charAt(0) == '@';
				if (noEcho)
					exec = exec.substring(1);

				ExecLog log = output != null ? new JAnsiPane(STDOUT) : new ConsoleExecLog();
				String command = varReplace(exec, vars);

				if (!noEcho)
					log.append(COMMAND, command + '\n');

				ExecResult res = con.exec(command);
				log.appendANSI(res.stdout.replaceFirst("\\s++$", ""), STDOUT);
				if (res.stderr.length() > 0)
					log.appendANSI('\n' + res.stderr.replaceFirst("\\s++$", ""), STDERR);

				// add to UI if specified
				if (output != null) {
					JAnsiPane pane = (JAnsiPane) log;
					
					// add in scroll if 10+ lines
					int lines = 0;
					String txt = pane.getText();
					for (int i = 0; i != -1 && lines < 10;) {
						if ((i = txt.indexOf('\n', i + 1)) != -1)
							lines++;
					}
					output.add(lines >= 10 ? new JScrollPane(pane) : pane);
				}
				
				// otherwise, do nothing as the log has already output everything
				else
					System.out.print(LogColor.RESET);
			}
		}
	}
}