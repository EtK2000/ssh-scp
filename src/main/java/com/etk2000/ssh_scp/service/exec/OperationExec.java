package com.etk2000.ssh_scp.service.exec;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JPanel;
import javax.swing.JScrollPane;

import com.etk2000.ssh_scp.config.Config;
import com.etk2000.ssh_scp.config.Server;
import com.etk2000.ssh_scp.headless.ConsoleExecLog;
import com.etk2000.ssh_scp.service.ServiceException;
import com.etk2000.ssh_scp.service.exec.ExecLog.LogColor;
import com.etk2000.ssh_scp.ui.JAnsiPane;
import com.etk2000.ssh_scp.ui.MainFrame;
import com.etk2000.ssh_scp.util.CommandConnection;
import com.etk2000.ssh_scp.util.CommandConnection.ExecResult;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

public class OperationExec extends Operation {
	private static final int TIMEOUT_EXEC = 30_000;
	
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
	public void run(MainFrame parent, List<String> vars, JPanel output, boolean isRelaunch) throws IOException {
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

				ExecResult res = con.exec(command, TIMEOUT_EXEC);
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
							++lines;
					}
					output.add(lines >= 10 ? new JScrollPane(pane) : pane);
				}
				
				// otherwise, do nothing as the log has already output everything
				else
					System.out.print(LogColor.RESET);
			}
		}
	}
	
	@Override
	public void write(JsonWriter jw) throws IOException {
		jw.name(name).beginArray();
		{
			for (String exec : execs)
				jw.value(exec);
		}
		jw.endArray();
	}
}