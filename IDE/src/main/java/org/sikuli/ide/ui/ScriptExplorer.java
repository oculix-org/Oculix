/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.ide.ui;

import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.Arrays;
import java.util.Comparator;

/**
 * File explorer panel showing the contents of the active .sikuli script bundle.
 * Displays script files and captured images in a tree view.
 */
public class ScriptExplorer extends JPanel {

  private JTree fileTree;
  private DefaultTreeModel treeModel;
  private DefaultMutableTreeNode rootNode;
  private JLabel emptyLabel;
  private JScrollPane scrollPane;
  private File currentDir;

  public ScriptExplorer() {
    setLayout(new BorderLayout());
    setMinimumSize(new Dimension(140, 0));
    setPreferredSize(new Dimension(180, 0));

    // Header
    JPanel header = new JPanel(new MigLayout("insets 4 8 4 8, fill", "[grow]", "[]"));
    header.setOpaque(false);
    JLabel title = new JLabel("Explorer");
    title.setFont(UIManager.getFont("defaultFont").deriveFont(Font.BOLD, 11f));
    header.add(title);
    add(header, BorderLayout.NORTH);

    // Tree
    rootNode = new DefaultMutableTreeNode("No script");
    treeModel = new DefaultTreeModel(rootNode);
    fileTree = new JTree(treeModel);
    fileTree.setRootVisible(true);
    fileTree.setShowsRootHandles(true);
    fileTree.setCellRenderer(new FileTreeCellRenderer());
    fileTree.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 4));

    fileTree.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        if (e.getClickCount() == 2) {
          DefaultMutableTreeNode node = (DefaultMutableTreeNode) fileTree.getLastSelectedPathComponent();
          if (node != null && node.getUserObject() instanceof FileNode) {
            FileNode fn = (FileNode) node.getUserObject();
            if (fn.file.getName().endsWith(".png")) {
              openImage(fn.file);
            }
          }
        }
      }
    });

    scrollPane = new JScrollPane(fileTree);
    scrollPane.setBorder(null);

    // Empty state
    emptyLabel = new JLabel("<html><center>Open or create a script<br>to browse files</center></html>");
    emptyLabel.setFont(UIManager.getFont("small.font"));
    emptyLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
    emptyLabel.setHorizontalAlignment(SwingConstants.CENTER);

    add(emptyLabel, BorderLayout.CENTER);
  }

  /**
   * Updates the explorer to show the contents of the given script directory.
   * Pass null to show the empty state.
   */
  public void setScriptDirectory(File dir) {
    this.currentDir = dir;

    if (dir == null) {
      showEmpty();
      return;
    }

    // Remove both to avoid stacking
    remove(emptyLabel);
    remove(scrollPane);

    if (!dir.exists()) {
      showEmpty();
      return;
    }

    rootNode.removeAllChildren();
    rootNode.setUserObject(dir.getName());

    File[] files = dir.listFiles();
    if (files != null) {
      // Sort: .py first, then .png, then rest
      Arrays.sort(files, Comparator
          .comparingInt((File f) -> f.getName().endsWith(".py") ? 0 : f.getName().endsWith(".png") ? 1 : 2)
          .thenComparing(File::getName));

      int imageCount = 0;
      DefaultMutableTreeNode imagesNode = new DefaultMutableTreeNode("Images");

      for (File file : files) {
        if (file.isDirectory()) continue; // skip subdirectories
        if (file.getName().endsWith(".png")) {
          imagesNode.add(new DefaultMutableTreeNode(new FileNode(file)));
          imageCount++;
        } else {
          rootNode.add(new DefaultMutableTreeNode(new FileNode(file)));
        }
      }

      if (imageCount > 0) {
        imagesNode.setUserObject("Images (" + imageCount + ")");
        rootNode.add(imagesNode);
      }
    }

    treeModel.reload();
    expandAll();

    add(scrollPane, BorderLayout.CENTER);
    revalidate();
    repaint();
  }

  private void showEmpty() {
    remove(scrollPane);
    remove(emptyLabel);
    add(emptyLabel, BorderLayout.CENTER);
    revalidate();
    repaint();
  }

  public void refresh() {
    if (currentDir != null) {
      setScriptDirectory(currentDir);
    }
  }

  private void expandAll() {
    for (int i = 0; i < fileTree.getRowCount(); i++) {
      fileTree.expandRow(i);
    }
  }

  private void openImage(File imageFile) {
    try {
      Desktop.getDesktop().open(imageFile);
    } catch (Exception e) {
      // Fallback: ignore
    }
  }

  // ── Inner classes ──

  static class FileNode {
    final File file;

    FileNode(File file) {
      this.file = file;
    }

    @Override
    public String toString() {
      return file.getName();
    }
  }

  static class FileTreeCellRenderer extends DefaultTreeCellRenderer {
    @Override
    public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel,
                                                   boolean expanded, boolean leaf, int row, boolean hasFocus) {
      super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);

      if (value instanceof DefaultMutableTreeNode) {
        Object obj = ((DefaultMutableTreeNode) value).getUserObject();
        if (obj instanceof FileNode) {
          FileNode fn = (FileNode) obj;
          String name = fn.file.getName();
          if (name.endsWith(".py")) {
            setText("\uD83D\uDCC4 " + name);
          } else if (name.endsWith(".png")) {
            setText("\uD83D\uDDBC " + name);
          } else if (name.endsWith(".html") || name.endsWith(".json")) {
            setText("\uD83D\uDCC3 " + name);
          } else {
            setText("\uD83D\uDCCE " + name);
          }
        } else if (obj instanceof String) {
          String s = (String) obj;
          if (s.startsWith("Images")) {
            setText("\uD83D\uDCC2 " + s);
          }
        }
      }
      setOpaque(false);
      return this;
    }
  }
}
