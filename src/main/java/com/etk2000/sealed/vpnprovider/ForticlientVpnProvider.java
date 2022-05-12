package com.etk2000.sealed.vpnprovider;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.swing.JOptionPane;

import com.etk2000.sealed.platform.Platform;
import com.etk2000.sealed.platform.Platform.PlatformType;
import com.etk2000.sealed.service.ServiceVPN;
import com.etk2000.sealed.util.HeadlessUtil;

public class ForticlientVpnProvider implements ServiceVPN {
	private static final Map<String, ForticlientVpnProvider> cache = new HashMap<>();

	public static ForticlientVpnProvider getFor(String id, String address, String user, String pass) throws IOException {
		final String key = id;
		ForticlientVpnProvider res = cache.get(key);
		if (res == null)
			cache.put(key, res = new ForticlientVpnProvider(address, user, pass));
		return res;
	}

	public final String address, pass, user;
	private boolean enabled;

	private ForticlientVpnProvider(String address, String user, String pass) {
		this.address = address;
		this.user = user;
		this.pass = pass;
	}

	@Override
	public String name() {
		return "forticlient";
	}

	@Override
	public void setEnabled(boolean enabled) {
		if (this.enabled != enabled) {
			if (Platform.TYPE == PlatformType.WINDOWS)
				HeadlessUtil.showMessageDialog(null, "Unable to activate/deactivate VPN on windows", "Error!", JOptionPane.ERROR_MESSAGE);

			/*else if (Platform.ensureInstalled(name())) {

				// FIXME: set state
				// FIXME: add/remove shutdown hook to disable VPN
				this.enabled = enabled;
			}*/

			else
				HeadlessUtil.showMessageDialog(null, "Unable to activate/deactivate VPN, please manually install '" + name() + '\'', "Error!", JOptionPane.ERROR_MESSAGE);
		}
	}
}