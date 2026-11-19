/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.ide.ui;

import org.sikuli.ide.theme.OculixUi;
import org.sikuli.ide.theme.OculixFileIcons;
import com.formdev.flatlaf.util.UIScale;
import com.formdev.flatlaf.extras.FlatSVGIcon;
import net.miginfocom.swing.MigLayout;

import static org.sikuli.support.ide.SikuliIDEI18N._I;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Workspace explorer showing open scripts as visual cards.
 * Each card represents a script (.sikuli bundle or temp script).
 * Clicking a card activates the corresponding editor tab.
 * @author Julien Mer (julienmerconsulting)
 * @author Claude (Anthropic)
 * @since 3.0.3
 */
public class ScriptExplorer extends JPanel implements org.sikuli.ide.ThemeAware {

  private JPanel cardContainer;
  private JLabel titleLabel;
  private JLabel emptyLabel;
  private JScrollPane scrollPane;
  private final List<ScriptCard> cards = new ArrayList<>();
  private int activeIndex = -1;
  private String workspaceName;
  private File workspaceDirectory;
  private final WorkspaceFileTree fileTree = new WorkspaceFileTree();
  private JSplitPane explorerSplit;
  private JPanel openScriptsPanel;

  // Callbacks
  private ActionListener onCardSelected;
  private ActionListener onCardRenamed;

  public ScriptExplorer() {
    setLayout(new BorderLayout());
    setMinimumSize(UIScale.scale(new Dimension(160, 0)));
    setPreferredSize(UIScale.scale(new Dimension(224, 0)));
    // Match the sidebar's surface — paper-100 in OculiX Light, ink-800 in
    // OculiX Dark — so the workspace explorer reads as one continuous brand
    // surface with the sidebar instead of a stark white pane.
    setOpaque(true);
    setBackground(OculixUi.color("explorer.background", "Panel.background"));

    // Header
    JPanel header = new JPanel(new MigLayout("insets 12 14 12 14, fillx, wrap 2", "[grow][]", "[]8[]"));
    header.setOpaque(false);
    titleLabel = new JLabel(_I("workspaceTitle"));
    titleLabel.setFont(UIManager.getFont("defaultFont").deriveFont(Font.BOLD, 12f));
    titleLabel.setIconTextGap(UIScale.scale(8));
    header.add(titleLabel, "growx, wmin 0");
    collapseButton = new JButton("❮❮");
    collapseButton.setToolTipText(_I("explorerCollapseTip"));
    collapseButton.setFocusable(false);
    collapseButton.setMargin(new Insets(0, 6, 0, 6));
    collapseButton.setFont(UIManager.getFont("Label.font").deriveFont(11.0f));
    collapseButton.putClientProperty("JButton.buttonType", "toolBarButton");
    collapseButton.addActionListener(e -> {
      if (onCollapse != null) onCollapse.run();
    });
    header.add(collapseButton, "wrap");
    header.add(fileTree.getActionBar(), "growx, span 2");
    add(header, BorderLayout.NORTH);

    // Card container (scrollable)
    cardContainer = new OculixSidebar.ViewportWidthPanel(new MigLayout("wrap 1, insets 6 8 6 8, gap 8", "[fill, grow]", ""));
    cardContainer.setOpaque(false);

    scrollPane = new JScrollPane(cardContainer);
    scrollPane.setBorder(null);
    scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    scrollPane.getVerticalScrollBar().setUnitIncrement(12);
    scrollPane.setOpaque(false);
    scrollPane.getViewport().setOpaque(false);

    // Empty state
    emptyLabel = new JLabel("<html><center>No scripts open<br><br><span style='font-size:9px'>Use File &gt; New<br>to create a script</span></center></html>");
    emptyLabel.setFont(UIManager.getFont("defaultFont").deriveFont(11f));
    emptyLabel.setForeground(OculixUi.color("sidebar.mutedForeground", "Label.disabledForeground"));
    emptyLabel.setHorizontalAlignment(SwingConstants.CENTER);

    openScriptsPanel = new JPanel(new BorderLayout());
    openScriptsPanel.setOpaque(false);
    openScriptsPanel.add(emptyLabel, BorderLayout.CENTER);
    JLabel openTitle = new JLabel(_I("explorerOpenScripts"));
    openTitle.setBorder(BorderFactory.createEmptyBorder(0, UIScale.scale(10), UIScale.scale(4), 0));
    openScriptsPanel.add(openTitle, BorderLayout.NORTH);
    explorerSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, openScriptsPanel, fileTree);
    explorerSplit.setBorder(BorderFactory.createEmptyBorder());
    explorerSplit.setResizeWeight(0.3);
    explorerSplit.setDividerLocation(UIScale.scale(180));
    openScriptsPanel.setMinimumSize(new Dimension(0, UIScale.scale(60)));
    fileTree.setMinimumSize(new Dimension(0, UIScale.scale(100)));
    add(explorerSplit, BorderLayout.CENTER);
  }

  public void setOnCardSelected(ActionListener listener) {
    this.onCardSelected = listener;
  }

  public void setWorkspaceName(String name) {
    workspaceName = name;
    refreshHeader();
  }

  public void setWorkspaceDirectory(File directory) {
    workspaceDirectory = directory;
    refreshHeader();
  }

  public File getExplorerDirectory() { return fileTree.getDirectory(); }

  /** Read the folder again, for when a file was renamed or captured behind the tree's back. */
  public void refreshTree() { fileTree.refresh(); }

  private JButton collapseButton;
  private Runnable onCollapse;

  /** Told by the IDE what to do when the user asks to collapse the panel. */
  public void setOnCollapse(Runnable action) { this.onCollapse = action; }

  public void setOnSaveAs(Runnable action) { fileTree.setOnSaveAs(action); }

  public void setOnFileOpened(java.util.function.Consumer<File> listener) {
    fileTree.setOnOpen(listener);
  }

  public void setOnFileMoved(java.util.function.BiConsumer<java.nio.file.Path, java.nio.file.Path> listener) {
    fileTree.setOnMoved(listener);
  }

  /** Show the active script's folder; retain the workspace label when no script is selected. */
  private void refreshHeader() {
    String name = workspaceName;
    String path = null;
    boolean temporary = false;
    if (activeIndex >= 0 && activeIndex < cards.size()) {
      ScriptInfo info = cards.get(activeIndex).currentInfo;
      temporary = !info.saved;
      String folderPath = info.path;
      if (!temporary && folderPath != null && !folderPath.isEmpty()) {
        File folder = new File(folderPath);
        name = folder.getName();
        if (name.isEmpty()) name = folder.getPath();
        path = folderPath;
      }
    }
    if (temporary) name = _I("explorerUnsaved");
    File directory = path == null ? workspaceDirectory : new File(path);
    // Keep the complete explicitly opened workspace when its active script is in a subfolder.
    // An unrelated active script must never inherit that workspace.
    if (!temporary && directory != null && workspaceDirectory != null
        && directory.toPath().toAbsolutePath().normalize()
            .startsWith(workspaceDirectory.toPath().toAbsolutePath().normalize())) {
      directory = workspaceDirectory;
    }
    fileTree.setDirectory(temporary ? null : directory);
    fileTree.setSaveEnabled(temporary || fileTree.getDirectory() != null);
    titleLabel.setText(name != null ? name : _I("workspaceTitle"));
    titleLabel.setToolTipText(path);
    boolean hasWorkspace = !temporary && name != null && !name.trim().isEmpty();
    titleLabel.setIcon(!hasWorkspace ? null
        : name.trim().toLowerCase(java.util.Locale.ROOT).endsWith(".sikuli")
            ? OculixFileIcons.get("sikuli") : OculixFileIcons.folder(true));
  }

  public void setOnCardRenamed(ActionListener listener) {
    this.onCardRenamed = listener;
  }

  /**
   * Rebuilds the card list from the given script contexts.
   * Call this whenever tabs change.
   */
  public void updateScripts(List<ScriptInfo> scripts, int selectedIndex) {
    cards.clear();
    cardContainer.removeAll();

    if (scripts.isEmpty()) {
      openScriptsPanel.remove(scrollPane);
      openScriptsPanel.remove(emptyLabel);
      openScriptsPanel.add(emptyLabel, BorderLayout.CENTER);
      activeIndex = -1;
      refreshHeader();
      revalidate();
      repaint();
      return;
    }

    openScriptsPanel.remove(emptyLabel);
    openScriptsPanel.remove(scrollPane);

    for (int i = 0; i < scripts.size(); i++) {
      ScriptInfo info = scripts.get(i);
      ScriptCard card = new ScriptCard(info, i);
      cards.add(card);
      cardContainer.add(card, "growx");
    }

    setActiveCard(selectedIndex);

    openScriptsPanel.add(scrollPane, BorderLayout.CENTER);
    revalidate();
    repaint();
  }

  /**
   * Highlights the card at the given index.
   */
  public void setActiveCard(int index) {
    activeIndex = index;
    for (int i = 0; i < cards.size(); i++) {
      cards.get(i).setActive(i == index);
    }
    refreshHeader();
  }

  /**
   * Updates a single card's info (e.g. after save/rename).
   */
  public void updateCard(int index, ScriptInfo info) {
    if (index >= 0 && index < cards.size()) {
      cards.get(index).updateInfo(info);
      if (index == activeIndex) refreshHeader();
    }
  }

  // ── ScriptCard ──

  class ScriptCard extends JPanel {
    private final int index;
    private JLabel nameLabel;
    private JLabel infoLabel;
    private JLabel statusLabel;
    private boolean active = false;
    private ScriptInfo currentInfo;
    private Color hoverBg;
    private Color activeBg;
    private Color normalBg;

    ScriptCard(ScriptInfo info, int index) {
      this.index = index;
      setLayout(new MigLayout("insets 12 12 12 12, gap 6", "[grow, fill, 0::][]", "[]2[]"));
      setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

      normalBg = OculixUi.color("explorer.cardBackground", "Panel.background");
      hoverBg = OculixUi.color("explorer.hoverBackground", "List.selectionBackground");
      activeBg = OculixUi.color("explorer.selectedBackground", "List.selectionBackground");

      setOpaque(false);
      setBackground(normalBg);
      setBorder(BorderFactory.createEmptyBorder(1, 1, 1, 1));

      // Script icon + name
      nameLabel = new JLabel();
      nameLabel.setFont(UIManager.getFont("defaultFont").deriveFont(Font.BOLD, 12f));
      add(nameLabel, "growx");

      // Save status
      statusLabel = new JLabel();
      statusLabel.setFont(UIManager.getFont("small.font"));
      add(statusLabel, "wrap");

      // Image count
      infoLabel = new JLabel();
      infoLabel.setFont(UIManager.getFont("small.font"));
      infoLabel.setForeground(OculixUi.color("sidebar.mutedForeground", "Label.disabledForeground"));
      add(infoLabel, "span 2");

      updateInfo(info);

      // Right-click context menu
      JPopupMenu popup = new JPopupMenu();
      JMenuItem renameItem = new JMenuItem("Rename");
      renameItem.addActionListener(ev -> {
        if (onCardRenamed != null) {
          String newName = JOptionPane.showInputDialog(ScriptExplorer.this,
              "New name:", currentInfo.name);
          if (newName != null && !newName.trim().isEmpty()) {
            onCardRenamed.actionPerformed(
                new java.awt.event.ActionEvent(ScriptCard.this, index, newName.trim()));
          }
        }
      });
      popup.add(renameItem);

      addMouseListener(new MouseAdapter() {
        @Override
        public void mouseClicked(MouseEvent e) {
          if (SwingUtilities.isRightMouseButton(e)) {
            popup.show(ScriptCard.this, e.getX(), e.getY());
          } else if (onCardSelected != null) {
            onCardSelected.actionPerformed(
                new java.awt.event.ActionEvent(ScriptCard.this, index, "select"));
          }
        }

        @Override
        public void mouseEntered(MouseEvent e) {
          if (!active) {
            setBackground(hoverBg);
          }
        }

        @Override
        public void mouseExited(MouseEvent e) {
          setBackground(active ? activeBg : normalBg);
        }
      });
    }

    @Override protected void paintComponent(Graphics g) {
      Graphics2D g2 = (Graphics2D) g.create();
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      int arc = UIScale.scale(12);
      g2.setColor(getBackground());
      g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
      g2.setColor(OculixUi.color(active ? "explorer.selectedBorderColor" : "panel.borderColor", "Component.borderColor"));
      g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
      g2.dispose();
      super.paintComponent(g);
    }

    void updateInfo(ScriptInfo info) {
      currentInfo = info;
      nameLabel.setIcon(OculixFileIcons.forName(info.name));
      nameLabel.setIconTextGap(UIScale.scale(8));
      nameLabel.setText(info.name);
      nameLabel.setToolTipText(info.path);

      if (info.saved) {
        statusLabel.setIcon(new FlatSVGIcon("icons/menu/check.svg", 12, 12));
        statusLabel.setText("");
        statusLabel.setToolTipText("Saved");
      } else {
        statusLabel.setIcon(null);
        statusLabel.setText("*");
        statusLabel.setForeground(new Color(0xFF, 0xAA, 0x33));
        statusLabel.setToolTipText(_I("workspaceCardUnsaved"));
      }

      String imgText = info.imageCount + " " + (info.imageCount != 1
          ? _I("workspaceCardImagesPlural")
          : _I("workspaceCardImagesSingular"));
      infoLabel.setIcon(OculixFileIcons.get("image"));
      infoLabel.setIconTextGap(UIScale.scale(8));
      infoLabel.setText(imgText);
    }

    void setActive(boolean active) {
      this.active = active;
      setBackground(active ? activeBg : normalBg);
      if (active) {
        nameLabel.setForeground(UIManager.getColor("List.selectionForeground"));
        infoLabel.setForeground(UIManager.getColor("List.selectionForeground"));
      } else {
        nameLabel.setForeground(UIManager.getColor("Label.foreground"));
        infoLabel.setForeground(OculixUi.color("sidebar.mutedForeground", "Label.disabledForeground"));
      }
    }

    void refreshTheme() {
      normalBg = OculixUi.color("explorer.cardBackground", "Panel.background");
      hoverBg = OculixUi.color("explorer.hoverBackground", "List.selectionBackground");
      activeBg = OculixUi.color("explorer.selectedBackground", "List.selectionBackground");
      setBackground(active ? activeBg : normalBg);
      setBorder(BorderFactory.createEmptyBorder(1, 1, 1, 1));
      if (active) {
        nameLabel.setForeground(UIManager.getColor("List.selectionForeground"));
        infoLabel.setForeground(UIManager.getColor("List.selectionForeground"));
      } else {
        nameLabel.setForeground(UIManager.getColor("Label.foreground"));
        infoLabel.setForeground(OculixUi.color("sidebar.mutedForeground", "Label.disabledForeground"));
      }
    }
  }

  // ── ScriptInfo (data class) ──

  public static class ScriptInfo {
    public final String name;
    public final String path;
    public final int imageCount;
    public final boolean saved;

    public ScriptInfo(String name, String path, int imageCount, boolean saved) {
      this.name = name;
      this.path = path;
      this.imageCount = imageCount;
      this.saved = saved;
    }

    public static ScriptInfo fromFolder(String name, File folder, boolean isTemp) {
      int imgCount = countImages(folder);
      String path = folder != null && folder.exists() ? folder.getAbsolutePath() : "";
      return new ScriptInfo(name, path, imgCount, !isTemp);
    }

    private static int countImages(File folder) {
      if (folder == null || !folder.isDirectory()) return 0;
      try (java.util.stream.Stream<java.nio.file.Path> stream = java.nio.file.Files.walk(folder.toPath())) {
        return (int) stream.filter(java.nio.file.Files::isRegularFile)
            .map(p -> p.getFileName().toString().toLowerCase(java.util.Locale.ROOT))
            .filter(n -> n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg"))
            .count();
      } catch (java.io.IOException e) {
        return 0;
      }
    }
  }


  @Override
  public void beforeThemeChange() { }

  @Override
  public void afterThemeChange() {
    setBackground(OculixUi.color("explorer.background", "Panel.background"));
    fileTree.applyTheme();
    for (ScriptCard card : cards) {
      card.refreshTheme();
    }
    revalidate();
    repaint();
  }
}
