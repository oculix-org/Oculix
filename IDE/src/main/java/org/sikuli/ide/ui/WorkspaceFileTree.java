/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.ide.ui;

import org.sikuli.ide.theme.OculixFileIcons;
import org.sikuli.ide.theme.OculixUi;
import com.formdev.flatlaf.util.UIScale;
import javax.swing.*;
import javax.swing.tree.*;
import javax.swing.event.*;
import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.event.*;
import java.io.*;
import java.nio.file.*;
import java.util.List;
import java.util.*;
import java.util.function.*;
import static org.sikuli.support.ide.SikuliIDEI18N._I;

/** Lazily lists real directories. File mutations and editor notification share the EDT. */
public final class WorkspaceFileTree extends JPanel {
  private static final DataFlavor INTERNAL_FILE = new DataFlavor(
      DataFlavor.javaJVMLocalObjectMimeType + ";class=java.lang.Object", "OculiX file");
  private final DefaultTreeModel model = new DefaultTreeModel(null, true);
  private final JTree tree = new JTree(model);
  private final JLabel status = new JLabel(" ");
  private final JButton newFolder = new JButton(_I("explorerNewFolder"), OculixFileIcons.folder(false));
  private final JButton saveAs = new JButton(_I("explorerSaveAs"));
  private JPanel actionBar;
  private Runnable onSaveAs;
  private Path rootDirectory;
  private Path pendingSelection;
  private final Set<Path> expanded = new HashSet<>();
  private Consumer<File> onOpen;
  private BiConsumer<Path, Path> onMoved;

  public WorkspaceFileTree() {
    super(new BorderLayout(0, UIScale.scale(4)));
    setBorder(BorderFactory.createEmptyBorder(UIScale.scale(6), UIScale.scale(8), UIScale.scale(6), UIScale.scale(8)));
    tree.setRootVisible(true);
    tree.setShowsRootHandles(true);
    tree.setRowHeight(UIScale.scale(28));
    tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
    tree.setCellRenderer(new FileRenderer());
    ToolTipManager.sharedInstance().registerComponent(tree);
    tree.addTreeWillExpandListener(new TreeWillExpandListener() {
      @Override public void treeWillExpand(TreeExpansionEvent e) { load((FileNode)e.getPath().getLastPathComponent()); }
      @Override public void treeWillCollapse(TreeExpansionEvent e) { }
    });
    tree.addMouseListener(new MouseAdapter() {
      @Override public void mouseClicked(MouseEvent e) {
        if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 2
            && tree.getPathForLocation(e.getX(), e.getY()) != null) openSelected();
      }
      @Override public void mousePressed(MouseEvent e) { popup(e); }
      @Override public void mouseReleased(MouseEvent e) { popup(e); }
    });
    bind(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "open", this::openSelected);
    bind(KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0), "refresh", this::refresh);
    bind(KeyStroke.getKeyStroke(KeyEvent.VK_N, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK), "mkdir", this::promptNewFolder);
    tree.setDropMode(DropMode.ON);
    tree.setTransferHandler(new FileTransferHandler());
    if (!GraphicsEnvironment.isHeadless()) tree.setDragEnabled(true);
    newFolder.addActionListener(e -> promptNewFolder());
    newFolder.putClientProperty("FlatLaf.styleClass", "consoleAction");
    newFolder.setEnabled(false);
    newFolder.setToolTipText(_I("explorerNewFolderHint"));
    JPanel actions = new JPanel(new GridLayout(0, 1, 0, UIScale.scale(4)));
    actions.setOpaque(false);
    saveAs.putClientProperty("FlatLaf.styleClass", "consoleAction");
    saveAs.addActionListener(e -> { if (onSaveAs != null) onSaveAs.run(); });
    saveAs.setEnabled(false);
    actions.add(saveAs);
    actions.add(newFolder);
    actionBar = actions;
    JScrollPane scroll = new JScrollPane(tree);
    scroll.setBorder(BorderFactory.createEmptyBorder());
    add(scroll, BorderLayout.CENTER);
    status.setFont(UIManager.getFont("small.font"));
    add(status, BorderLayout.SOUTH);
    applyTheme();
  }

  /** The Save as and New folder buttons, so the explorer can show them under its own title. */
  public JComponent getActionBar() {
    return actionBar;
  }

  public void setOnSaveAs(Runnable action) { onSaveAs = action; }
  public void setSaveEnabled(boolean enabled) { saveAs.setEnabled(enabled); }
  public File getDirectory() { return rootDirectory == null ? null : rootDirectory.toFile(); }

  public void setOnOpen(Consumer<File> action) { onOpen = action; }
  public void setOnMoved(BiConsumer<Path, Path> action) { onMoved = action; }

  /** Keep the tree anchored when an opened script belongs to one of its subfolders. */
  public void setDirectory(File directory) {
    Path next = directory == null ? null : directory.toPath().toAbsolutePath().normalize();
    if (Objects.equals(next, rootDirectory)) return;
    if (next != null && rootDirectory != null && next.startsWith(rootDirectory)) return;
    rootDirectory = next;
    expanded.clear();
    pendingSelection = null;
    reload();
  }

  public void refresh() {
    if (rootDirectory == null) return;
    rememberExpanded();
    FileNode selected = selectedNode();
    pendingSelection = selected == null ? null : selected.path;
    reload();
  }

  private void rememberExpanded() {
    expanded.clear();
    Object root = model.getRoot();
    if (root == null) return;
    Enumeration<TreePath> paths = tree.getExpandedDescendants(new TreePath(root));
    if (paths != null) while (paths.hasMoreElements()) {
      expanded.add(((FileNode)paths.nextElement().getLastPathComponent()).path);
    }
  }

  private void reload() {
    status.setText(rootDirectory == null ? _I("explorerSaveFirst") : " ");
    status.setToolTipText(null);
    newFolder.setEnabled(rootDirectory != null);
    FileNode root = rootDirectory == null ? null : new FileNode(rootDirectory, true);
    model.setRoot(root);
    if (root != null) load(root);
  }

  private void load(FileNode node) {
    if (!node.directory || node.loaded || node.loading) return;
    node.loading = true;
    status.setText(_I("explorerLoading"));
    new SwingWorker<List<FileNode>, Void>() {
      @Override protected List<FileNode> doInBackground() throws IOException {
        List<FileNode> children = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(node.path)) {
          for (Path child : stream) {
            // A link is displayed, but never recursively followed into another tree or a cycle.
            children.add(new FileNode(child, Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)));
          }
        }
        children.sort(Comparator.comparing((FileNode n) -> !n.directory)
            .thenComparing(n -> n.toString(), String.CASE_INSENSITIVE_ORDER).thenComparing(FileNode::toString));
        return children;
      }
      @Override protected void done() {
        node.loading = false;
        if (node.getRoot() != model.getRoot()) return; // A later tab switch/reload superseded this result.
        try {
          List<FileNode> children = get();
          node.removeAllChildren();
          for (FileNode child : children) node.add(child);
          node.loaded = true;
          model.nodeStructureChanged(node);
          TreePath nodePath = new TreePath(node.getPath());
          tree.expandPath(nodePath);
          for (FileNode child : children) {
            TreePath childPath = new TreePath(child.getPath());
            if (child.directory && (expanded.contains(child.path)
                || pendingSelection != null && pendingSelection.startsWith(child.path))) tree.expandPath(childPath);
            if (child.path.equals(pendingSelection)) {
              tree.setSelectionPath(childPath);
              tree.scrollPathToVisible(childPath);
            }
          }
          status.setText(" ");
          status.setToolTipText(null);
        } catch (Exception ex) {
          status.setText(_I("explorerReadFailed"));
          status.setToolTipText(node.path + ": " + causeMessage(ex));
        }
      }
    }.execute();
  }

  private void bind(KeyStroke key, String name, Runnable action) {
    tree.getInputMap().put(key, name);
    tree.getActionMap().put(name, new AbstractAction() {
      @Override public void actionPerformed(ActionEvent e) { action.run(); }
    });
  }

  private FileNode selectedNode() {
    Object value = tree.getLastSelectedPathComponent();
    return value instanceof FileNode ? (FileNode)value : null;
  }

  private Path selectedDirectory() {
    FileNode node = selectedNode();
    return node == null ? rootDirectory : node.directory ? node.path : node.path.getParent();
  }

  private void openSelected() {
    FileNode node = selectedNode();
    if (node != null && !node.directory && onOpen != null) onOpen.accept(node.path.toFile());
  }

  private void popup(MouseEvent e) {
    if (!e.isPopupTrigger()) return;
    TreePath hit = tree.getPathForLocation(e.getX(), e.getY());
    if (hit == null) tree.clearSelection(); else tree.setSelectionPath(hit);
    FileNode node = selectedNode();
    JPopupMenu menu = new JPopupMenu();
    JMenuItem create = new JMenuItem(_I("explorerNewFolder"), OculixFileIcons.folder(false));
    create.setEnabled(rootDirectory != null);
    create.addActionListener(event -> promptNewFolder());
    menu.add(create);
    JMenuItem move = new JMenuItem(_I("explorerMoveTo"));
    move.setEnabled(node != null && !node.path.equals(rootDirectory));
    move.addActionListener(event -> promptMove());
    menu.add(move);
    JMenuItem refresh = new JMenuItem(_I("explorerRefresh"));
    refresh.addActionListener(event -> refresh());
    menu.add(refresh);
    menu.show(tree, e.getX(), e.getY());
  }

  private void promptNewFolder() {
    Path parent = selectedDirectory();
    if (parent == null) return;
    String name = JOptionPane.showInputDialog(this, _I("explorerFolderPrompt", parent.toString()),
        _I("explorerNewFolder"), JOptionPane.PLAIN_MESSAGE);
    if (name == null) return;
    try {
      Path created = createDirectory(parent, name);
      rememberExpanded();
      pendingSelection = created;
      expanded.add(parent);
      reload();
    } catch (IOException | IllegalArgumentException ex) { showError(ex); }
  }

  private void promptMove() {
    FileNode node = selectedNode();
    if (node == null || node.path.equals(rootDirectory)) return;
    JFileChooser chooser = new JFileChooser(selectedDirectory().toFile());
    chooser.setDialogTitle(_I("explorerMoveTo"));
    chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
    chooser.setAcceptAllFileFilterUsed(false);
    if (chooser.showDialog(this, _I("explorerMove")) == JFileChooser.APPROVE_OPTION) {
      performMove(node.path, chooser.getSelectedFile().toPath());
    }
  }

  private boolean performMove(Path source, Path parent) {
    try {
      // Notify synchronously before another EDT action can save an open buffer to its old path.
      Path moved = moveEntry(source, parent);
      if (onMoved != null) onMoved.accept(source, moved);
      rememberExpanded();
      expanded.add(parent.toAbsolutePath().normalize());
      pendingSelection = moved;
      reload();
      return true;
    } catch (IOException | IllegalArgumentException ex) { showError(ex); return false; }
  }

  /** Create exactly one child; no implicit ancestors and no overwriting an existing entry. */
  static Path createDirectory(Path parent, String name) throws IOException {
    if (name == null || name.isBlank() || name.equals(".") || name.equals("..")
        || name.indexOf('/') >= 0 || name.indexOf('\\') >= 0 || name.indexOf(':') >= 0) {
      throw new IllegalArgumentException(_I("explorerInvalidFolderName"));
    }
    Path child = Path.of(name);
    if (child.isAbsolute() || child.getNameCount() != 1) throw new IllegalArgumentException(_I("explorerInvalidFolderName"));
    return Files.createDirectory(parent.toAbsolutePath().normalize().resolve(child));
  }

  /** Default Files.move semantics reject collisions. A directory cannot be moved inside itself. */
  static Path moveEntry(Path source, Path parent) throws IOException {
    Path from = source.toAbsolutePath().normalize();
    Path directory = parent.toAbsolutePath().normalize();
    if (!Files.isDirectory(directory)) throw new NotDirectoryException(directory.toString());
    Path target = directory.resolve(from.getFileName());
    if (from.equals(target)) throw new FileAlreadyExistsException(target.toString());
    if (Files.isDirectory(from, LinkOption.NOFOLLOW_LINKS) && directory.toRealPath().startsWith(from.toRealPath())) {
      throw new IOException(_I("explorerMoveIntoSelf"));
    }
    // The precheck also covers dangling links, and Files.move rechecks the destination.
    if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) throw new FileAlreadyExistsException(target.toString());
    return Files.move(from, target);
  }

  private void showError(Exception ex) {
    String message = ex instanceof FileAlreadyExistsException ? _I("explorerAlreadyExists", ex.getMessage())
        : ex instanceof AccessDeniedException ? _I("explorerAccessDenied", ex.getMessage()) : causeMessage(ex);
    JOptionPane.showMessageDialog(this, message, _I("explorerOperationFailed"), JOptionPane.ERROR_MESSAGE);
  }

  private static String causeMessage(Throwable ex) {
    Throwable cause = ex.getCause() == null ? ex : ex.getCause();
    return cause.getMessage() == null ? cause.toString() : cause.getMessage();
  }

  public void applyTheme() {
    Color bg = OculixUi.color("explorer.background", "Tree.background");
    setBackground(bg);
    tree.setBackground(bg);
    status.setForeground(OculixUi.color("sidebar.mutedForeground", "Label.disabledForeground"));
    tree.repaint();
  }

  private static final class FileNode extends DefaultMutableTreeNode {
    final Path path;
    final boolean directory;
    boolean loaded, loading;
    FileNode(Path path, boolean directory) { super(path, directory); this.path = path; this.directory = directory; }
    @Override public String toString() { return path.getFileName() == null ? path.toString() : path.getFileName().toString(); }
  }

  private static final class FileRenderer extends DefaultTreeCellRenderer {
    @Override public Component getTreeCellRendererComponent(JTree t, Object value, boolean selected,
        boolean expanded, boolean leaf, int row, boolean focused) {
      super.getTreeCellRendererComponent(t, value, selected, expanded, leaf, row, focused);
      FileNode node = (FileNode)value;
      setIcon(node.directory ? node.toString().toLowerCase(Locale.ROOT).endsWith(".sikuli")
          ? OculixFileIcons.get("sikuli") : OculixFileIcons.folder(expanded) : OculixFileIcons.forName(node.toString()));
      setToolTipText(node.path.toString());
      setIconTextGap(UIScale.scale(6));
      return this;
    }
  }

  private final class FileTransferHandler extends TransferHandler {
    @Override public int getSourceActions(JComponent c) { return MOVE; }
    @Override protected Transferable createTransferable(JComponent c) {
      FileNode selected = selectedNode();
      return selected == null || selected.path.equals(rootDirectory) ? null : new FileTransfer(selected.path);
    }
    @Override public boolean canImport(TransferSupport support) {
      if (!support.isDrop()) return false;
      FileTransfer transfer = transfer(support.getTransferable());
      if (transfer == null) return false;
      if (transfer.owner != WorkspaceFileTree.this) return false;
      TreePath path = ((JTree.DropLocation)support.getDropLocation()).getPath();
      FileNode node = path == null ? null : (FileNode)path.getLastPathComponent();
      return node != null && node.directory && !node.path.startsWith(transfer.source)
          && !node.path.equals(transfer.source.getParent());
    }
    @Override public boolean importData(TransferSupport support) {
      if (!canImport(support)) return false;
      FileNode target = (FileNode)((JTree.DropLocation)support.getDropLocation()).getPath().getLastPathComponent();
      return performMove(transfer(support.getTransferable()).source, target.path);
    }
    private FileTransfer transfer(Transferable value) {
      try {
        if (!value.isDataFlavorSupported(INTERNAL_FILE)) return null;
        Object data = value.getTransferData(INTERNAL_FILE);
        return data instanceof FileTransfer ? (FileTransfer)data : null;
      } catch (UnsupportedFlavorException | IOException ex) { return null; }
    }
    // No exportDone deletion: Files.move already performs the whole operation.
  }

  private final class FileTransfer implements Transferable {
    final WorkspaceFileTree owner = WorkspaceFileTree.this;
    final Path source;
    FileTransfer(Path source) { this.source = source; }
    @Override public DataFlavor[] getTransferDataFlavors() { return new DataFlavor[]{INTERNAL_FILE}; }
    @Override public boolean isDataFlavorSupported(DataFlavor flavor) { return INTERNAL_FILE.equals(flavor); }
    @Override public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
      if (!isDataFlavorSupported(flavor)) throw new UnsupportedFlavorException(flavor);
      return this;
    }
  }
}
