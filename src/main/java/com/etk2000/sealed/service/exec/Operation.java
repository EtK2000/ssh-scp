package com.etk2000.sealed.service.exec;

import java.io.IOException;
import java.util.List;

import javax.swing.JPanel;

import com.etk2000.sealed.service.exec.ExecLog.LogColor;
import com.etk2000.sealed.ui.MainFrame;
import com.google.gson.stream.JsonReader;

public abstract class Operation {
	protected static final LogColor COMMAND = LogColor.BRIGHT_BLUE, STDERR = LogColor.BRIGHT_RED, STDOUT = LogColor.BRIGHT_BLACK;

	static Operation read(JsonReader jr) throws IOException {
		String name = jr.nextName();

		// is it a variable definition?
		if (name.startsWith("$$")) {
			int index = name.indexOf(' ');

			// $$<var> <title...>
			if (index != -1) 
				return new OperationVarList(name, jr);

			// $$<var>?$<query_var>
			else if ((index = name.indexOf('?')) != -1)
				return new OperationVarQuery(name, jr);
			
			// unimplemented and/or invalid type
			else
				throw new IOException("invalid operation, got '" + name + '\'');
		}

		// or just a simple execute
		return new OperationExec(name, jr);
	}
	
	// lookup vars, LOW: improve lookup via map or something
	protected static void setVar(byte var, String val, List<String> vars) {
		if (var >= vars.size()) {
			while (var > vars.size())
				vars.add(null);
			vars.add(val);
		}
		else
			vars.set(var, val);
	}

	// lookup vars, LOW: improve lookup via map or something
	protected static String varReplace(String str, List<String> vars) {
		String res = str;
		for (int var = vars.size() - 1; var >= 0; --var) {
			String val = vars.get(var);
			if (val != null)
				res = res.replace("$" + var, val);
		}
		return res;
	}

	abstract void run(MainFrame parent, List<String> vars, JPanel output, boolean isRelaunch) throws IOException;
}