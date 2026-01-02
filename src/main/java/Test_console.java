import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Collections;
import java.util.concurrent.ArrayBlockingQueue;

import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.text.BadLocationException;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyledDocument;

import com.etk2000.ssh_scp.config.Config;
import com.etk2000.ssh_scp.config.Server;

import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.common.LoggerFactory;
import net.schmizz.sshj.common.StreamCopier;
import net.schmizz.sshj.connection.channel.direct.PTYMode;
import net.schmizz.sshj.connection.channel.direct.Session;
import net.schmizz.sshj.connection.channel.direct.Session.Shell;

@SuppressWarnings("serial")
class Console extends JTextPane {
	private final DocOutputStream out;
	private final PrintStream pout;
	private final DocInputStream in;
	private final JFrame frame;
	private final StyledDocument doc;

	Console() throws IOException {
		setPreferredSize(new Dimension(600, 500));
		doc = this.getStyledDocument();
		out = new DocOutputStream(doc, this);
		pout = new PrintStream(out);
		in = new DocInputStream();
		this.addKeyListener(in);
		setFont(new Font("Courier New", Font.PLAIN, 12));
		// setEditorKit(new HTMLEditorKit());
		frame = new JFrame("Console");
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.add(new JScrollPane(this));
		frame.pack();
		frame.setVisible(true);

		try (SSHClient ssh = new SSHClient()) {
			Server srv = Config.getServer("deeplearning-6-vm");
			ssh.addHostKeyVerifier(srv.fingerprint);
			ssh.connect(srv.address());

			if (srv.pass != null)
				ssh.authPassword(srv.user, srv.pass);
			else
				ssh.authPublickey(srv.user, srv.key.path());

			try (Session session = ssh.startSession()) {
				// {PTYMode.ECHO, 0} disables echoing of input to output
				session.allocatePTY("vt100", 120, 120, 0, 0, Collections.singletonMap(PTYMode.ECHO, 0));

				final Shell shell = session.startShell();

				new StreamCopier(shell.getInputStream(), getOut(), LoggerFactory.DEFAULT).bufSize(shell.getLocalMaxPacketSize()).spawn("stdout");

				new StreamCopier(shell.getErrorStream(), getOut(), LoggerFactory.DEFAULT).bufSize(shell.getLocalMaxPacketSize()).spawn("stderr");

				new StreamCopier(getIn(), shell.getOutputStream(), LoggerFactory.DEFAULT).bufSize(shell.getRemoteMaxPacketSize()).copy();
			}
		}
	}

	public InputStream getIn() {
		return in;
	}

	public PrintStream getOut() {
		return pout;
	}

	private static class DocOutputStream extends OutputStream {
		private final MutableAttributeSet cur = new SimpleAttributeSet();
		private final StyledDocument doc;
		private final JTextPane pane;

		public DocOutputStream(StyledDocument doc, JTextPane pane) {
			this.doc = doc;
			this.pane = pane;
		}

		@Override
		public void write(int b) throws IOException {
			try {
				doc.insertString(doc.getLength(), Character.toString((char) b), cur);
				pane.setCaretPosition(doc.getLength());
			}
			catch (BadLocationException e) {
				e.printStackTrace();
			}
		}
	}

	private static class DocInputStream extends InputStream implements KeyListener {
		private final ArrayBlockingQueue<Integer> queue = new ArrayBlockingQueue<>(1024);

		@Override
		public int read() throws IOException {
			try {
				Integer i = queue.take();
				if (i != null)
					return i;
			}
			catch (InterruptedException e) {
				e.printStackTrace();
			}
			return -1;
		}

		@Override
		public int read(byte[] b, int off, int len) throws IOException {
			if (b == null) {
				throw new NullPointerException();
			}
			else if (off < 0 || len < 0 || len > b.length - off) {
				throw new IndexOutOfBoundsException();
			}
			else if (len == 0) {
				return 0;
			}
			int c = read();
			if (c == -1) {
				return -1;
			}
			b[off] = (byte) c;

			int i = 1;
			try {
				for (; i < len && available() > 0; ++i) {
					c = read();
					if (c == -1) {
						break;
					}
					b[off + i] = (byte) c;
				}
			}
			catch (IOException ee) {
			}
			return i;
		}

		@Override
		public int available() {
			return queue.size();
		}

		@Override
		public void keyTyped(KeyEvent e) {
			int c = e.getKeyChar();
			try {
				queue.put(c);
			}
			catch (InterruptedException ex) {
				ex.printStackTrace();
			}
		}

		@Override
		public void keyPressed(KeyEvent e) {
		}

		@Override
		public void keyReleased(KeyEvent e) {
		}
	}

	public static void main(String[] args) throws Exception {
		Config.load(null, null);
		new Console();
	}
}