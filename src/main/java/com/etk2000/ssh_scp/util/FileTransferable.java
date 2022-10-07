package com.etk2000.ssh_scp.util;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import com.etk2000.ssh_scp.platform.Platform;
import com.etk2000.ssh_scp.ui.ExplorerObject;

public class FileTransferable implements Transferable {
	private static File getTempFile(String filename) {
		File res = new File(Platform.dirTmp(), filename);
		if (res.exists())
			Util.delete(res);
		return res;
	}

	private final DataFlavor[] flavors = { DataFlavor.javaFileListFlavor };
	private final List<String> filenames;
	private final List<File> files;
	private final BiConsumer<String, File> ensureFilesExist;

	/**
	 * A drag-and-drop object for transferring a file.
	 * 
	 * @param file file to transfer -- this file should already exist, otherwise it
	 *             may not be accepted by drag targets.
	 */
	public FileTransferable(List<ExplorerObject> objects, BiConsumer<String, File> ensureFilesExist) {
		this.filenames = objects.stream().map(eo -> eo.name).collect(Collectors.toList());
		this.files = Collections.unmodifiableList(filenames.stream().map(FileTransferable::getTempFile).collect(Collectors.toList()));
		this.ensureFilesExist = ensureFilesExist;
	}

	@Override
	public List<File> getTransferData(DataFlavor flavor) {
		if (!isDataFlavorSupported(flavor))
			return null;

		for (int i = 0; i < files.size(); ++i)
			ensureFilesExist.accept(filenames.get(i), files.get(i));
		return files;
	}

	@Override
	public DataFlavor[] getTransferDataFlavors() {
		return flavors;
	}

	@Override
	public boolean isDataFlavorSupported(DataFlavor flavor) {
		return DataFlavor.javaFileListFlavor.equals(flavor);
	}
}