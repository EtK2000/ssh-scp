package com.etk2000.sealed.util;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.DefaultListModel;
import javax.swing.JTextField;

import com.etk2000.sealed.config.Server;
import com.etk2000.sealed.ui.ExplorerObject;
import com.etk2000.sealed.ui.ExplorerObject.ObjectType;
import com.google.cloud.Tuple;

import net.schmizz.sshj.common.StreamCopier.Listener;
import net.schmizz.sshj.xfer.TransferListener;
import net.schmizz.sshj.xfer.scp.SCPFileTransfer;

public class ExplorerConnection extends CommandConnection {
	private static final Pattern GET_FILENAME = Pattern.compile("((\\\\\\s)*[^\\s]*)+$"), GET_LINKNAME = Pattern.compile("((\\\\\\s)*[^\\s]*)+(?=\\s->)");

	private static String humanReadable(String path) {
		return path.replace("\\", "");
	}

	private static String unixReadable(String path) {
		String res = path.replace('\\', '/').replace(" ", "\\ ").replace(";", "\\;").replace("//", "/");
		if (res.charAt(res.length() - 1) == '/' && res.length() > 1)
			return res.substring(0, res.length() - 1);// remove trailing slashes
		return res;
	}

	private final String home;
	private final List<ExplorerObject> objects = new ArrayList<>();
	private String cd = "~";

	public ExplorerConnection(Server srv) throws IOException {
		super(srv);

		try {
			home = exec("echo $HOME").stdout.trim();
		}
		catch (IOException e) {
			client.close();
			throw e;
		}
	}

	public String cd() {
		return humanReadable(cd);
	}

	public void cd(String newDir, DefaultListModel<ExplorerObject> uiModel, JTextField cdOut) {
		if (newDir.length() == 0) {
			cdOut.setText(cd());
			return;
		}

		String dir = unixReadable(newDir);
		try {
			ExecResult result = exec("ls -a -b -g " + dir + " && realpath " + dir + "/.");

			String[] res = result.stderr.length() == 0 ? result.stdout.split("\n") : null;
			if (res != null) {
				String newCd = unixReadable(res[res.length - 1]);

				// follow links, LOW: this can stack overflow, esp. with looped links
				if (res[0].contains(" -> "))
					cd(newCd, uiModel, cdOut);

				// otherwise, update
				else {
					// update cd
					cd = newCd.startsWith(home) ? '~' + newCd.substring(home.length()) : newCd;
					cdOut.setText(cd());

					// 2 to skip count and ".", -1 to skip cd
					parseLS(uiModel, res, 2, res.length - 1);
				}
			}
			else
				cdOut.setText(cd());
		}
		catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void cdSubdir(String subdir, DefaultListModel<ExplorerObject> uiModel, JTextField cdOut) {
		cd(cd() + '/' + subdir, uiModel, cdOut);
	}

	public void download(String filename, File dst, LongBiConsumer progressCallback) throws IOException {
		SCPFileTransfer transfer = client.newSCPFileTransfer();
		transfer.setTransferListener(new TransferListener() {
			@Override
			public Listener file(String name, long size) {
				return transferred -> progressCallback.accept(transferred, size);
			}

			@Override
			public TransferListener directory(String name) {
				return this;
			}
		});
		transfer.download((cd.charAt(0) == '~' ? home + cd.substring(1) : cd) + '/' + unixReadable(filename), dst.getAbsolutePath());
		progressCallback.accept(-1, -1);
	}

	public boolean delete(ExplorerObject obj, DefaultListModel<ExplorerObject> uiModel) {
		try {
			ExecResult result = exec("rm " + (obj.type == ObjectType.directory ? "-r " : "") + cd + '/' + unixReadable(obj.name) + " && ls -a -b -g " + cd);

			if (result.stderr.length() == 0) {
				String[] res = result.stderr.length() == 0 ? result.stdout.split("\n") : null;
				if (res != null) {
					// 2 to skip count and "."
					parseLS(uiModel, res, 2, res.length);
					return true;
				}
			}
		}
		catch (IOException e) {
			e.printStackTrace();
		}
		return false;
	}

	private ExplorerObject parseLineLS(String line) {
		Matcher m = GET_FILENAME.matcher(line);
		if (m.find()) {
			switch (line.charAt(0)) {
				case '-':
					return new ExplorerObject(ObjectType.file, humanReadable(m.group()));

				case 'b':
				case 'c':
					return new ExplorerObject(ObjectType.device, humanReadable(m.group()));

				case 'd':
					return new ExplorerObject(ObjectType.directory, humanReadable(m.group()));

				case 'l': {
					String dst = humanReadable(m.group());

					m = GET_LINKNAME.matcher(line);
					if (m.find()) {
						String src = humanReadable(m.group());

						if (dst.charAt(0) != '/' && dst.charAt(0) != '~')
							dst = cd + '/' + dst;

						ExplorerObject obj = new ExplorerObject(ObjectType.link, src);
						obj.extra = Tuple.of(obj, dst);
						return obj;
					}

					return new ExplorerObject(ObjectType.link, dst);
				}
				default:
					System.out.println(line);
					return new ExplorerObject(ObjectType.other, "UNKNOWN: " + humanReadable(m.group()));
			}
		}

		System.out.println(line);
		return null;
	}

	@SuppressWarnings("unchecked")
	private void parseLS(DefaultListModel<ExplorerObject> uiModel, String[] lines, int indexFirst, int indexLast) {
		objects.clear();

		// 2 to skip count and "."
		List<Tuple<ExplorerObject, String>> linkLookup = new ArrayList<>();
		for (int i = indexFirst; i < indexLast; i++) {
			ExplorerObject obj = parseLineLS(lines[i]);
			if (obj != null) {
				objects.add(obj);
				if (obj.type == ObjectType.link && obj.extra instanceof Tuple) {
					linkLookup.add((Tuple<ExplorerObject, String>) obj.extra);
					obj.extra = null;
				}
			}
		}

		// lookup everything in a single command
		if (linkLookup.size() > 0) {
			StringBuilder command = new StringBuilder();
			for (Tuple<ExplorerObject, String> link : linkLookup)
				command.append("ls -b -d -g ").append(link.y()).append(" && ");
			command.setLength(command.length() - 4);
			try {
				ExecResult result = exec(command.toString());

				// TODO: attempt to find a workaround
				if (result.stderr.length() > 0)
					throw new IOException("cannot parse links: " + result.stderr);

				String[] stdoutLines = result.stdout.split("\n");
				for (int i = 0; i < linkLookup.size(); i++)
					linkLookup.get(i).x().extra = parseLineLS(stdoutLines[i]);
				// LOW: look into recursive parsing, i.e. if dst is link as well
			}
			catch (IOException e) {
				e.printStackTrace();
			}
		}

		uiModel.clear();
		objects.forEach(uiModel::addElement);
	}

	public void upload(File file, LongBiConsumer progressCallback, DefaultListModel<ExplorerObject> uiModel) throws IOException {
		SCPFileTransfer transfer = client.newSCPFileTransfer();
		transfer.setTransferListener(new TransferListener() {
			@Override
			public Listener file(String name, long size) {
				return transferred -> progressCallback.accept(transferred, size);
			}

			@Override
			public TransferListener directory(String name) {
				return this;
			}
		});
		transfer.upload(file.getAbsolutePath(), cd.charAt(0) == '~' ? home + cd().substring(1) : cd());
		progressCallback.accept(-1, -1);

		// update directory contents
		try {
			ExecResult result = exec("ls -a -b -g " + cd);

			String[] res = result.stderr.length() == 0 ? result.stdout.split("\n") : null;
			if (res != null)// 2 to skip count and "."
				parseLS(uiModel, res, 2, res.length);
		}
		catch (IOException e) {
			e.printStackTrace();
		}
	}
}