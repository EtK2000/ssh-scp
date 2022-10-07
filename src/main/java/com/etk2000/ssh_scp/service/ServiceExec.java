package com.etk2000.ssh_scp.service;

import java.awt.Color;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.UIManager;

import com.etk2000.ssh_scp.api.Service;
import com.etk2000.ssh_scp.service.exec.ExecLog.LogColor;
import com.etk2000.ssh_scp.service.exec.Operation;
import com.etk2000.ssh_scp.ui.MainFrame;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

public class ServiceExec implements Service {
	public final String name;
	final List<Operation> segments = new ArrayList<>();

	public ServiceExec(JsonReader jr) throws IOException {
		jr.beginObject();
		{
			name = jr.nextName();
			jr.beginObject();
			{
				while (jr.hasNext())
					segments.add(Operation.read(jr));
			}
			jr.endObject();
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
				if (output != null) {
					JTextArea err = new JTextArea();
					err.setForeground(Color.RED);
					err.setEditable(false);
					err.setFont(UIManager.getFont("TextField.font"));
					output.add(err);

					if (e instanceof ServiceException)
						err.setText("Execution stopped at segment: " + segment + '\n' + e.getMessage());
					else
						err.setText("Execution failed for segment: " + segment + '\n' + e.getClass().getName() + ": " + e.getMessage());
				}
				else {
					if (e instanceof ServiceException)
						System.err.println(LogColor.BRIGHT_RED + "Execution stopped at segment: " + segment + '\n' + e.getMessage() + LogColor.RESET);
					else
						System.err.println(LogColor.BRIGHT_RED + "Execution failed for segment: " + segment + '\n' + e.getClass().getName() + ": "
								+ e.getMessage() + LogColor.RESET);
				}
				break;
			}
		}
		return vars;
	}

	@Override
	public void write(JsonWriter jw) throws IOException {
		jw.name(name).beginObject();
		{
			for (Operation segment : segments)
				segment.write(jw);
		}
		jw.endObject();
	}
}