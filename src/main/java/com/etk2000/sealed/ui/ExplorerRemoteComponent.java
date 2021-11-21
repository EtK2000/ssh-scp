package com.etk2000.sealed.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragSource;
import java.awt.dnd.DragSourceAdapter;
import java.awt.dnd.DragSourceDropEvent;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.util.List;

import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JFileChooser;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JTextField;
import javax.swing.ListCellRenderer;
import javax.swing.ListModel;

import com.etk2000.sealed.platform.Platform;
import com.etk2000.sealed.ui.ExplorerObject.ObjectType;
import com.etk2000.sealed.util.ExplorerConnection;
import com.etk2000.sealed.util.FileTransferable;
import com.etk2000.sealed.util.LongBiConsumer;
import com.etk2000.sealed.util.Util;

@SuppressWarnings("serial")
class ExplorerRemoteComponent extends JList<ExplorerObject> {
	private static final Color DARK_CYAN = Color.CYAN.darker(), DARK_GREEN = Color.GREEN.darker(), DARK_YELLOW = Color.YELLOW.darker();

	private boolean isDragSource;

	ExplorerRemoteComponent(ExplorerConnection con, JTextField cd, LongBiConsumer updateProgress) {
		super(new DefaultListModel<>());
		setCellRenderer(new ListCellRenderer<ExplorerObject>() {
			private final JFileChooser dummy = new JFileChooser();

			@Override
			public Component getListCellRendererComponent(JList<? extends ExplorerObject> list, ExplorerObject value, int index, boolean isSelected, boolean cellHasFocus) {
				DefaultListCellRenderer res = (DefaultListCellRenderer) new DefaultListCellRenderer().getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
				res.setText(value.name);

				// load associated type icon if existent
				res.setIcon(dummy.getIcon(new File(value.isDirectory() ? "." : "file")));

				// color based off type
				switch (value.type) {
					case __tofind__:
						throw new IllegalArgumentException("something went wrong");
					case device:
						res.setForeground(DARK_YELLOW);
						break;
					case directory:
						res.setForeground(Color.BLUE);
						break;
					case file:
						res.setForeground(DARK_GREEN);
						break;
					case link:
						res.setForeground(DARK_CYAN);
						break;
					case other:
						break;
				}
				return res;
			}
		});

		addKeyListener(new KeyAdapter() {
			@Override
			public void keyTyped(KeyEvent e) {
				switch (e.getKeyChar()) {
					case KeyEvent.VK_BACK_SPACE:
						onEnterItem(ExplorerObject.CD_UP, con, cd);
						break;
					case KeyEvent.VK_DELETE:
						for (ExplorerObject obj : getSelectedValuesList())
							onDeleteItem(obj, con);
						break;
					case KeyEvent.VK_ENTER:
						// FIXME: if multiple directories selected open new windows for them
						// TODO: shift click/enter open in new window?
						// if ((e.getModifiersEx() & InputEvent.SHIFT_DOWN_MASK) != 0)
						onEnterItem(getSelectedValue(), con, cd);
						break;
				}
			}
		});

		// allow double clicking object to cd to it if directory or download it
		// otherwise
		addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				if (e.getClickCount() == 2) {

					// Double-click detected
					int index = locationToIndex(e.getPoint());
					if (index != -1 && getCellBounds(index, index).contains(e.getPoint()))
						onEnterItem(getModel().get(index), con, cd);
				}
			}
		});

		DragSource dndSrc = new DragSource();

		// allow DnD unto to upload files
		setDropTarget(new DropTarget() {
			@Override
			public synchronized void dragEnter(DropTargetDragEvent dtde) {
				if (isDragSource || !dtde.getTransferable().isDataFlavorSupported(DataFlavor.javaFileListFlavor))
					dtde.rejectDrag();
			}

			@SuppressWarnings("unchecked")
			@Override
			public synchronized void drop(DropTargetDropEvent dtde) {
				try {
					dtde.acceptDrop(DnDConstants.ACTION_COPY);
					List<File> files = (List<File>) dtde.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
					for (File file : files)
						con.upload(file, updateProgress, getModel());
					// LOW: doesn't need to freeze UI, can actually show progress

					// select the newly uploaded files
					clearSelection();// JIC
					int[] indencies = files.stream().mapToInt(f -> getModel().indexOf(ExplorerObject.find(f.getName()))).filter(i -> i >= 0).toArray();
					setSelectedIndices(indencies);
				}
				catch (Exception e) {
					e.printStackTrace();
				}
			}
		});

		// allow DnD from to download files, FIXME: allow multiple
		dndSrc.createDefaultDragGestureRecognizer(this, DnDConstants.ACTION_COPY, dge -> {
			if (getSelectedIndex() == -1 || getSelectedValue().name.equals(".."))
				return;

			isDragSource = true;
			dndSrc.startDrag(dge, DragSource.DefaultCopyDrop, new FileTransferable(getSelectedValuesList(), (filename, file) -> {
				if (!file.exists()) {
					try {
						con.download(filename, file, updateProgress);
					}
					catch (IOException e) {
						e.printStackTrace();
					}
				}
			}), new DragSourceAdapter() {
				@Override
				public void dragDropEnd(DragSourceDropEvent dsde) {
					isDragSource = false;
					Util.delete(Platform.dirTmp());
				}
			});
		});
	}

	@Override
	public DefaultListModel<ExplorerObject> getModel() {
		return (DefaultListModel<ExplorerObject>) super.getModel();
	}

	@Override
	public void setModel(ListModel<ExplorerObject> model) {
		throw new IllegalAccessError("model cannot be modified");
	}

	private void onDeleteItem(ExplorerObject obj, ExplorerConnection con) {
		if (obj == null)
			return;

		// FIXME: add cancel option if deleting multiple files
		if (JOptionPane.YES_OPTION == JOptionPane.showConfirmDialog(null,
				"Are you sure you would like to delete " + obj.name + (obj.type == ObjectType.directory ? " and its contents?" : "?"), "Confirm delete",
				JOptionPane.YES_NO_OPTION)) {

			List<ExplorerObject> selected = getSelectedValuesList();
			if (!con.delete(obj, getModel()))
				JOptionPane.showMessageDialog(null, "Failed to delete file");
			else {
				// select the remaining files
				clearSelection();// JIC
				int[] indencies = selected.stream().mapToInt(getModel()::indexOf).filter(i -> i >= 0).toArray();
				setSelectedIndices(indencies);
			}
		}
	}

	private void onEnterItem(ExplorerObject obj, ExplorerConnection con, JTextField cd) {
		if (obj == null)
			return;

		// if the directory exists cd to it
		if (obj.type == ObjectType.directory)
			con.cdSubdir(obj.name, getModel(), cd);

		// follow direct links, TODO: work with following deeper links
		else if (obj.type == ObjectType.link && obj.extra instanceof ExplorerObject && ((ExplorerObject) obj.extra).type == ObjectType.directory)
			con.cd(((ExplorerObject) obj.extra).name, getModel(), cd);
	}
}