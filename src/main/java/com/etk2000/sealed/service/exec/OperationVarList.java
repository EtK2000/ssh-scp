package com.etk2000.sealed.service.exec;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JOptionPane;
import javax.swing.JPanel;

import com.etk2000.sealed.service.ServiceException;
import com.etk2000.sealed.ui.MainFrame;
import com.etk2000.sealed.util.HeadlessUtil;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

class OperationVarList extends Operation {
	private final String[] execs, options;
	private final String title;
	private final byte var;

	// $$<var> <title...> [options...]
	OperationVarList(String name, JsonReader jr) throws IOException {
		int index = name.indexOf(' ');
		title = name.substring(index + 1);
		var = Byte.parseByte(name.substring(2, index));

		List<String> lExecs = new ArrayList<>(), lOptions = new ArrayList<>();

		// arrays are simple sets
		if (jr.peek() == JsonToken.BEGIN_ARRAY) {
			jr.beginArray();
			{
				while (jr.hasNext())
					lOptions.add(jr.nextString());
			}
			jr.endArray();
		}

		// otherwise, it can set subsequent vars
		else {
			jr.beginObject();
			{
				while (jr.hasNext()) {
					lOptions.add(jr.nextName());
					lExecs.add(jr.nextString());
				}
			}
			jr.endObject();
		}

		execs = lExecs.toArray(new String[0]);
		options = lOptions.toArray(new String[0]);
	}

	@Override
	void run(MainFrame parent, List<String> vars, JPanel output, boolean isRelaunch) throws IOException {
		if (isRelaunch && vars.size() > var)
			return;

		int selection = HeadlessUtil.showOptionDialog(parent, "Please select a value for '" + title + '\'', title, JOptionPane.CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE, null,
				options, null);

		if (selection == JOptionPane.CLOSED_OPTION)
			throw new ServiceException("value for '" + title + "' not selected");

		setVar(var, options[selection], vars);

		// if there's exec data associated, execute it
		if (execs.length != 0) {
			// FIXME: allow more powerful logic
			String exec = execs[selection];

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
	}
}