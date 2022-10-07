package com.etk2000.ssh_scp.service.exec;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.swing.JPanel;

import com.etk2000.ssh_scp.config.Config;
import com.etk2000.ssh_scp.service.ServiceException;
import com.etk2000.ssh_scp.ui.MainFrame;
import com.etk2000.ssh_scp.util.Util;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

class OperationVarQuery extends Operation {
	private final List<String> options = new ArrayList<>();
	private final Map<String, String> optionsMap = new HashMap<>();
	private final String query;
	private final byte var;

	OperationVarQuery(String name, JsonReader jr) throws IOException {
		int index = name.indexOf('?');
		query = name.substring(index + 1);// can be a user var, or a hardcoded var
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

		// otherwise, it's result<=>value pairs
		else {
			jr.beginObject();
			{
				while (jr.hasNext())
					optionsMap.put(jr.nextName(), jr.nextString());
			}
			jr.endObject();
		}
	}

	@Override
	public void run(MainFrame parent, List<String> vars, JPanel output, boolean isRelaunch) throws IOException {
		// this function doesn't require user interaction,
		// so we can and should requery on relaunch

		String myVal = null, uVal = null;
		byte val = -1;

		// check if query is a hardcoded var
		switch (query.toLowerCase()) {
			case "$office":// = is in office address list
				val = (byte) (Config.isOfficeIP() ? 1 : 0);
				break;

			case "$vpn":// = is VPN connected
				val = (byte) (Util.isUsingVPN() ? 1 : 0);
				break;
		}

		// otherwise, it's a user var
		if (val == -1) {
			try {
				byte lookup = Byte.parseByte(query);
				if (lookup >= vars.size() || vars.get(lookup) == null)
					throw new ServiceException("variable undefined, got '" + query + '\'');

				// great, the value was loaded...
				uVal = vars.get(lookup);
			}
			catch (NumberFormatException e) {
				throw new ServiceException("invalid variable, got '" + query + '\'');
			}
		}

		// if the options were index based, parse [if needed] and lookup
		if (options.size() > 0) {
			if (uVal != null) {
				try {
					val = Byte.parseByte(uVal);
				}
				catch (NumberFormatException e) {
					throw new ServiceException("query type mismatch, expected number got '" + uVal + '\'');
				}
			}
			if (val != -1)
				myVal = options.size() > val ? options.get(val) : null;
		}
		
		// otherwise, just lookup in the map
		else {
			if (val != -1)
				uVal = Byte.toString(val);
			myVal = optionsMap.get(uVal);
		}
		
		setVar(var, myVal, vars);
	}

	@Override
	public void write(JsonWriter jw) throws IOException {
		jw.name("$$" + var + "?$" + query);

		// arrays are simple sets
		if (optionsMap.size() == 0) {
			jw.beginArray();
			{
				for (String options : options)
					jw.value(options);
			}
			jw.endArray();
		}

		// otherwise, it's result<=>value pairs
		else {
			jw.beginObject();
			{
				for (Entry<String, String> e : optionsMap.entrySet())
					jw.name(e.getKey()).value(e.getValue());
			}
			jw.endObject();
		}
	}
}