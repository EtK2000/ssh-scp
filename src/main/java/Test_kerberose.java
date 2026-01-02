

import java.io.IOException;
import java.security.Principal;
import java.util.Map;

import javax.security.auth.Subject;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.auth.login.AppConfigurationEntry;
import javax.security.auth.login.Configuration;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;
import javax.security.auth.login.AppConfigurationEntry.LoginModuleControlFlag;

import org.ietf.jgss.GSSException;
import org.ietf.jgss.GSSManager;
import org.ietf.jgss.Oid;

import com.etk2000.ssh_scp.config.Server;
import com.google.common.collect.ImmutableMap;

import net.schmizz.sshj.SSHClient;

public class Test_kerberose {
	public static SSHClient test(Server srv) throws GSSException, IOException, LoginException {
		LoginContext loginContext = new LoginContext("", null, new CallbackHandler() {
			@Override
			public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException {
				for (Callback callback : callbacks) {
					if (callback instanceof PasswordCallback) {
						System.out.println(((PasswordCallback) callback).getPrompt());
						// ((PasswordCallback)callback).setPassword();
					}
				}
			}
		}, new Configuration() {
			@Override
			public AppConfigurationEntry[] getAppConfigurationEntry(String name) {
				ImmutableMap.Builder<String, String> options = ImmutableMap.builder();
				options.put("refreshKrb5Config", "true");
				options.put("useTicketCache", "true");
				// options.put("doNotPrompt", "true");
				return new AppConfigurationEntry[] {
						new AppConfigurationEntry("com.sun.security.auth.module.Krb5LoginModule", LoginModuleControlFlag.REQUIRED, options.build()) };
			}
		});
		loginContext.login();
		Subject subject = loginContext.getSubject();
		Principal clientPrincipal = subject.getPrincipals().iterator().next();
		GSSManager gssm = GSSManager.getInstance();
		/*
		 * GSSCredential clientCredential = Subject.doAs(subject, () ->
		 * gssm.createCredential(gssm.createName(clientPrincipal.getName(), "eytan"),
		 * DEFAULT_LIFETIME, KERBEROS_OID, INITIATE_ONLY));
		 */

		SSHClient client = new SSHClient();

		if (srv.fingerprint != null && !srv.fingerprint.isEmpty())
			client.addHostKeyVerifier(srv.fingerprint);

		try {
			client.setConnectTimeout(10_000);
			client.connect(srv.address());

			client.authGssApiWithMic("USERNAME", new LoginContext("", null, null, new Configuration() {
				@Override
				public AppConfigurationEntry[] getAppConfigurationEntry(String name) {
					return new AppConfigurationEntry[] { new AppConfigurationEntry("com.sun.security.auth.module.Krb5LoginModule",
							AppConfigurationEntry.LoginModuleControlFlag.REQUIRED, Map.of(//
									// "doNotPrompt", "true", //
									"refreshKrb5Config", "true", //
									"useTicketCache", "true" //
							)) };
				}
			}), new Oid("1.2.840.113554.1.2.2"));
			client.useCompression();
			return client;
		}
		catch (IOException e) {
			client.close();
			throw e;
		}
	}
}