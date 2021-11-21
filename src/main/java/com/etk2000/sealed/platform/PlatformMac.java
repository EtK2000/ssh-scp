package com.etk2000.sealed.platform;

import java.io.File;
import java.io.IOException;
import java.util.Collections;

import com.etk2000.sealed.util.Util;

class PlatformMac extends PlatformLinux {
	@Override
	protected boolean ensureToolsExistImpl() {
		String sshpass = Util.runForResult("which sshpass");
		if (sshpass.length() == 0) {
			File rb = new File(dirTmp, "sshpass.rb");
			try {
				Util.urlGET("https://raw.githubusercontent.com/kadwanev/bigboybrew/master/Library/Formula/sshpass.rb", Collections.EMPTY_MAP, rb);
				System.out.println(Util.runForResult("brew install \"" + rb.getAbsolutePath() + '"'));
				rb.delete();
				sshpass = Util.runForResult("which sshpass");
			}
			catch (IOException e) {
				e.printStackTrace();
				return false;
			}
		}

		// ensure sshpass was installed
		if (sshpass.length() > 0) {
			sshKey = sshpass + " -i ${key} ${remote}";
			sshPass = sshpass + " -p ${pass} ssh ${remote}";
			return true;
		}
		return false;
	}
}