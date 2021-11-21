package com.etk2000.sealed.ui;

import java.util.List;

import javax.swing.JLabel;

import com.etk2000.sealed.service.ServiceExec;

@SuppressWarnings("serial")
class RelaunchTab extends JLabel {
	private final ServiceExec exec;
	private final MainFrame parent;
	private final List<String> vars;
	
	RelaunchTab(MainFrame parent, String text, ServiceExec exec, List<String> vars) {
		super(text);
		this.exec = exec;
		this.parent = parent;
		this.vars = vars;
	}
	
	void relaunch() {
		parent.run(exec, this, vars);
	}
}