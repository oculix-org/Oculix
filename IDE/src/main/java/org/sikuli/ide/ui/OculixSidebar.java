/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.ide.ui;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import static org.sikuli.support.ide.SikuliIDEI18N._I;

/**
 * Vertical sidebar replacing the classic JMenuBar + JToolBar.
 * All actions are accessed via popup submenus for consistency.
 */
public class OculixSidebar extends JPanel {

  private boolean collapsed = false;
  private int collapsedWidth = 50;

  // Navigation items (all with submenus)
  private SidebarItem navFile;
  private SidebarItem navEdit;
  private SidebarItem navRun;
  private SidebarItem navTools;
  private SidebarItem navHelp;

  // Theme toggle
  private SidebarItem btnTheme;
  private boolean isDark = true;

  // Version label
  private JLabel versionLabel;

  // Panels for layout sections
  private JPanel mainPanel;
  private JPanel footerPanel;

  public OculixSidebar() {
    setLayout(new BorderLayout());
    setPreferredSize(new Dimension(180, 0));
    setMinimumSize(new Dimension(180, 0));
    putClientProperty(FlatClientProperties.STYLE, "background: darken(@background, 3%)");

    mainPanel = new JPanel(new MigLayout("wrap 1, insets 8, gap 0", "[fill, grow]", ""));
    mainPanel.setOpaque(false);

    footerPanel = new JPanel(new MigLayout("wrap 1, insets 8, gap 2", "[fill, grow]", ""));
    footerPanel.setOpaque(false);

    add(mainPanel, BorderLayout.NORTH);
    add(footerPanel, BorderLayout.SOUTH);

    addResizeHandle();
  }

  /**
   * Initializes the sidebar with logo and all navigation items.
   * All items open popup submenus — no direct action buttons.
   */
  public void initNavItems() {
    // Logo
    JLabel logo = new JLabel("OculiX");
    logo.setFont(UIManager.getFont("h3.font"));
    logo.setHorizontalAlignment(SwingConstants.CENTER);
    logo.setBorder(BorderFactory.createEmptyBorder(8, 0, 8, 0));
    mainPanel.add(logo);

    mainPanel.add(new JSeparator(), "growx, gaptop 4, gapbottom 8");

    // File
    navFile = new SidebarItem(_I("menuFile") + "  \u25B8",
        loadIcon("/icons/insert-image-icon.png", 16));
    navFile.setMnemonic(java.awt.event.KeyEvent.VK_F);
    mainPanel.add(navFile);

    // Edit
    navEdit = new SidebarItem(_I("menuEdit") + "  \u25B8", null);
    navEdit.setMnemonic(java.awt.event.KeyEvent.VK_E);
    mainPanel.add(navEdit);

    // Run
    navRun = new SidebarItem(_I("menuRun") + "  \u25B8",
        loadIcon("/icons/run_big_green.png", 16));
    navRun.setMnemonic(java.awt.event.KeyEvent.VK_R);
    mainPanel.add(navRun);

    mainPanel.add(new JSeparator(), "growx, gaptop 8, gapbottom 8");

    // Tools (Capture, Record, Extensions)
    navTools = new SidebarItem(_I("menuTool") + "  \u25B8",
        loadIcon("/icons/capture-small.png", 16));
    navTools.setMnemonic(java.awt.event.KeyEvent.VK_T);
    mainPanel.add(navTools);

    // Help
    navHelp = new SidebarItem(_I("menuHelp") + "  \u25B8", null);
    navHelp.setMnemonic(java.awt.event.KeyEvent.VK_H);
    mainPanel.add(navHelp);
  }

  /**
   * Wires navigation items to popup submenus.
   */
  public void initNavigation(SidebarSubmenu fileSub, SidebarSubmenu editSub,
                              SidebarSubmenu runSub, SidebarSubmenu toolsSub,
                              SidebarSubmenu helpSub) {
    navFile.addActionListener(e -> fileSub.showBelow(navFile));
    navEdit.addActionListener(e -> editSub.showBelow(navEdit));
    navRun.addActionListener(e -> runSub.showBelow(navRun));
    navTools.addActionListener(e -> toolsSub.showBelow(navTools));
    navHelp.addActionListener(e -> helpSub.showBelow(navHelp));
  }

  /**
   * Initializes the footer with theme toggle and version label.
   */
  public void initFooter(String version, ActionListener themeAction) {
    footerPanel.add(new JSeparator(), "growx, gapbottom 4");

    btnTheme = new SidebarItem("\u263C  Dark / Light", null, e -> {
      toggleTheme();
      if (themeAction != null) {
        themeAction.actionPerformed(e);
      }
    });
    footerPanel.add(btnTheme);

    versionLabel = new JLabel("v" + version);
    versionLabel.setFont(UIManager.getFont("small.font"));
    versionLabel.setHorizontalAlignment(SwingConstants.CENTER);
    versionLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
    footerPanel.add(versionLabel);
  }

  private void toggleTheme() {
    isDark = !isDark;
    try {
      if (isDark) {
        FlatDarkLaf.setup();
      } else {
        FlatLightLaf.setup();
      }
      FlatLaf.updateUI();
    } catch (Exception ex) {
      // Fallback: ignore theme switch failure
    }
  }

  public boolean isDarkTheme() {
    return isDark;
  }

  /**
   * Toggle between collapsed (icons only) and expanded (icons + labels) mode.
   */
  public void toggleCollapsed() {
    collapsed = !collapsed;

    for (Component c : mainPanel.getComponents()) {
      if (c instanceof SidebarItem) {
        ((SidebarItem) c).setCollapsed(collapsed);
      }
      if (c instanceof JLabel && !(c instanceof SidebarItem)) {
        c.setVisible(!collapsed);
      }
    }
    for (Component c : footerPanel.getComponents()) {
      if (c instanceof SidebarItem) {
        ((SidebarItem) c).setCollapsed(collapsed);
      }
    }
    versionLabel.setVisible(!collapsed);

    revalidate();
    repaint();
  }

  public boolean isCollapsed() {
    return collapsed;
  }

  private void addResizeHandle() {
    addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        if (e.getClickCount() == 2) {
          toggleCollapsed();
        }
      }
    });
  }

  private ImageIcon loadIcon(String path, int size) {
    try {
      java.net.URL url = getClass().getResource(path);
      if (url != null) {
        ImageIcon icon = new ImageIcon(url);
        Image img = icon.getImage().getScaledInstance(size, size, Image.SCALE_SMOOTH);
        return new ImageIcon(img);
      }
    } catch (Exception e) {
      // ignore, return null
    }
    return null;
  }
}
