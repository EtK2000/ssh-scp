package com.etk2000.ssh_scp.ui;

import java.awt.Taskbar;
import java.awt.Taskbar.Feature;
import java.awt.Taskbar.State;
import java.awt.Window;

public class TaskbarUtil {
	private static final Taskbar TASKBAR = Taskbar.isTaskbarSupported() ? Taskbar.getTaskbar() : null;

	public static void calculateTaskbarProgress(Window w, long current, long total) {
		if (current == total)
			setTaskbarProgressState(w, total == -1 ? State.OFF : State.INDETERMINATE);

		else {
			int _current = (int) (total > Integer.MAX_VALUE ? ((double) Integer.MAX_VALUE / total) * current : current);
			int _full = (int) Math.min(Integer.MAX_VALUE, total);
			setTaskbarProgress(w, (int) ((double) _current / _full * 100));
		}
	}

	public static void setTaskbarProgress(Window w, int progress) {
		if (TASKBAR == null)
			return;

		if (TASKBAR.isSupported(Feature.PROGRESS_STATE_WINDOW))
			TASKBAR.setWindowProgressState(w, State.NORMAL);
		if (TASKBAR.isSupported(Feature.PROGRESS_VALUE_WINDOW))
			TASKBAR.setWindowProgressValue(w, progress);
		else if (TASKBAR.isSupported(Feature.PROGRESS_VALUE))
			TASKBAR.setProgressValue(progress);
	}

	public static void setTaskbarProgressState(Window w, State state) {
		if (TASKBAR == null)
			return;

		if (TASKBAR.isSupported(Feature.PROGRESS_STATE_WINDOW))
			TASKBAR.setWindowProgressState(w, state);
		else if (state == State.OFF)
			TASKBAR.setProgressValue(-1);
	}

	private TaskbarUtil() {
	}
}