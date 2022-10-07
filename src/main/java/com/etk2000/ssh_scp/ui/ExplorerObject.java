package com.etk2000.ssh_scp.ui;

public class ExplorerObject {
	public enum ObjectType {
		__tofind__, device, directory, file, link, other
	}

	public static final ExplorerObject CD_UP = new ExplorerObject(ObjectType.directory, "..");

	public static ExplorerObject find(String name) {
		return new ExplorerObject(ObjectType.__tofind__, name);
	}

	public final ObjectType type;
	public final String name;
	public Object extra;

	public ExplorerObject(ObjectType type, String name) {
		this.type = type;
		this.name = name;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof ExplorerObject) {
			ExplorerObject other = (ExplorerObject) obj;
			return name.equals(other.name) && (type == other.type || type == ObjectType.__tofind__ || other.type == ObjectType.__tofind__);
		}
		return false;
	}

	public boolean isDirectory() {
		return type == ObjectType.directory || (type == ObjectType.link && extra instanceof ExplorerObject && ((ExplorerObject) extra).type == ObjectType.directory);
	}

	@Override
	public String toString() {
		String res = name + " (" + type + ')';
		if (extra instanceof ExplorerObject)
			res += " -> " + extra;
		return res;
	}
}