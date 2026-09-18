/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.ide.ui;

import org.sikuli.basics.Debug;
import org.sikuli.basics.Settings;
import org.sikuli.ide.theme.OculixColors;
import org.sikuli.support.RunPulse;

import java.awt.AWTException;
import java.awt.CheckboxMenuItem;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Graphics2D;
import java.awt.Menu;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.RenderingHints;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.image.BufferedImage;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.sikuli.support.ide.SikuliIDEI18N._I;

/**
 * A coloured dot in the system notification area that tells whether a
 * script is running, searching, or has stopped beating, while the IDE
 * window itself is hidden behind the application under test. Its menu
 * gives the everyday IDE actions without bringing the window back.
 */
public final class TrayIndicator {

  /** What the tray can ask the IDE to do. */
  public interface Actions {
    void showIde();
    void runScript();
    void stopScript();
    void quit();
    void newWorkspace();
    void openWorkspace();
    boolean isDark();
    void applyTheme(boolean dark);
    Locale currentLocale();
    void changeLocale(Locale locale);
  }

  private final TrayIcon icon;
  private final TrayState state;
  private final Actions actions;
  private final Map<TrayState.State, BufferedImage> images = new EnumMap<>(TrayState.State.class);
  private final ScheduledExecutorService watchdog;
  private MenuItem stopItem;
  private TrayState.State shown;
  private boolean stuckReported;
  private boolean hiddenReported;

  private TrayIndicator(TrayIcon icon, TrayState state, Actions actions, ScheduledExecutorService watchdog) {
    this.icon = icon;
    this.state = state;
    this.actions = actions;
    this.watchdog = watchdog;
  }

  /**
   * Puts the icon in the notification area and starts the watchdog.
   * Returns null where the desktop has no notification area (GNOME
   * without an extension, headless), in which case the IDE runs as before.
   */
  public static TrayIndicator install(Actions actions) {
    if (!SystemTray.isSupported()) {
      Debug.log(3, "tray: not supported on this desktop");
      return null;
    }
    TrayState state = new TrayState(System::currentTimeMillis, Settings.TrayStuckSeconds * 1000L);
    TrayIcon icon = new TrayIcon(new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB));
    icon.setImageAutoSize(false);
    ScheduledExecutorService watchdog = Executors.newSingleThreadScheduledExecutor(r -> {
      Thread t = new Thread(r, "oculix-tray-watchdog");
      t.setDaemon(true);
      return t;
    });
    TrayIndicator tray = new TrayIndicator(icon, state, actions, watchdog);
    tray.paintImages(SystemTray.getSystemTray().getTrayIconSize());
    icon.addActionListener(e -> actions.showIde());
    tray.relabel();
    try {
      SystemTray.getSystemTray().add(icon);
    } catch (AWTException e) {
      Debug.log(3, "tray: could not add the icon: %s", e.getMessage());
      watchdog.shutdownNow();
      return null;
    }
    watchdog.scheduleAtFixedRate(tray::tick, 1, 1, TimeUnit.SECONDS);
    return tray;
  }

  public void runStarted(String script) {
    RunPulse.beat();
    state.runStarted(script);
    stuckReported = false;
    tick();
  }

  public void runEnded() {
    state.runEnded();
    tick();
  }

  /** The IDE window just went away into the tray; says so once per session. */
  public void windowHidden() {
    if (!hiddenReported) {
      hiddenReported = true;
      icon.displayMessage("OculiX", _I("trayStillRunning"), TrayIcon.MessageType.INFO);
    }
  }

  /** Builds the menu again in the current IDE language, theme and locale. */
  public void relabel() {
    icon.setPopupMenu(buildMenu());
    shown = null;
    tick();
  }

  public void remove() {
    watchdog.shutdownNow();
    SystemTray.getSystemTray().remove(icon);
  }

  private PopupMenu buildMenu() {
    PopupMenu menu = new PopupMenu();
    menu.add(item(_I("trayShowIde"), actions::showIde));
    menu.addSeparator();
    menu.add(item(_I("trayRunScript"), actions::runScript));
    stopItem = item(_I("trayStopScript"), actions::stopScript);
    stopItem.setEnabled(state.isRunning());
    menu.add(stopItem);
    menu.addSeparator();
    Menu workspace = new Menu(_I("trayWorkspace"));
    workspace.add(item(_I("trayNewWorkspace"), actions::newWorkspace));
    workspace.add(item(_I("trayOpenWorkspace"), actions::openWorkspace));
    menu.add(workspace);
    menu.add(themeMenu());
    menu.add(languageMenu());
    menu.addSeparator();
    menu.add(item(_I("trayQuit"), actions::quit));
    return menu;
  }

  private Menu themeMenu() {
    Menu theme = new Menu(_I("trayTheme"));
    boolean dark = actions.isDark();
    theme.add(choice(_I("trayThemeDark"), dark, () -> actions.applyTheme(true)));
    theme.add(choice(_I("trayThemeLight"), !dark, () -> actions.applyTheme(false)));
    return theme;
  }

  private Menu languageMenu() {
    Menu language = new Menu(_I("trayLanguage"));
    Locale current = actions.currentLocale();
    for (Map.Entry<String, String[]> region : OculixSidebar.LanguagePicker.regions().entrySet()) {
      Menu sub = new Menu(_I(region.getKey()));
      for (Locale loc : OculixSidebar.LanguagePicker.localesOf(region.getValue())) {
        boolean active = OculixSidebar.LanguagePicker.sameLocale(loc, current);
        sub.add(choice(OculixSidebar.LanguagePicker.displayLabel(loc), active, () -> actions.changeLocale(loc)));
      }
      if (sub.getItemCount() > 0) {
        language.add(sub);
      }
    }
    return language;
  }

  private static MenuItem item(String label, Runnable action) {
    MenuItem item = new MenuItem(label);
    item.addActionListener(e -> action.run());
    return item;
  }

  private static CheckboxMenuItem choice(String label, boolean checked, Runnable action) {
    CheckboxMenuItem item = new CheckboxMenuItem(label, checked);
    item.addItemListener(e -> {
      item.setState(checked);
      if (!checked) {
        action.run();
      }
    });
    return item;
  }

  private void tick() {
    TrayState.State now = state.state(RunPulse.lastBeat(), RunPulse.isSearching());
    long silent = state.silentSeconds(RunPulse.lastBeat());
    EventQueue.invokeLater(() -> {
      if (now != shown || now == TrayState.State.STUCK) {
        icon.setImage(images.get(now));
        icon.setToolTip(tooltip(now, silent));
        shown = now;
      }
      if (now == TrayState.State.STUCK && !stuckReported) {
        stuckReported = true;
        icon.displayMessage("OculiX", tooltip(now, silent), TrayIcon.MessageType.WARNING);
      }
      if (stopItem != null) {
        stopItem.setEnabled(state.isRunning());
      }
    });
  }

  private String tooltip(TrayState.State s, long silent) {
    switch (s) {
      case RUNNING:
        return _I("trayRunning", state.script());
      case SEARCHING:
        return _I("traySearching", state.script());
      case STUCK:
        return _I("trayStuck", state.script(), silent);
      default:
        return _I("trayIdle");
    }
  }

  /** The gecko head at rest, and the same head with a coloured badge per state. */
  private void paintImages(Dimension size) {
    int w = Math.max(16, size.width);
    int h = Math.max(16, size.height);
    BufferedImage gecko = gecko(w, h);
    images.put(TrayState.State.IDLE, gecko);
    images.put(TrayState.State.RUNNING, badged(gecko, OculixColors.OX_LIME_400));
    images.put(TrayState.State.SEARCHING, badged(gecko, OculixColors.OX_CYAN_500));
    images.put(TrayState.State.STUCK, badged(gecko, new Color(0xFF, 0x3B, 0x3B)));
  }

  private static BufferedImage gecko(int w, int h) {
    BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = img.createGraphics();
    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    try {
      java.awt.Image head = javax.imageio.ImageIO.read(TrayIndicator.class.getResource("/icons/tray/gecko.png"));
      g.drawImage(head, 0, 0, w, h, null);
    } catch (Exception e) {
      Debug.log(3, "tray: gecko icon missing, drawing a dot: %s", e.getMessage());
      g.setColor(new Color(0x8A, 0x8F, 0x98));
      g.fillOval(1, 1, w - 2, h - 2);
    }
    g.dispose();
    return img;
  }

  private static BufferedImage badged(BufferedImage base, Color color) {
    int w = base.getWidth();
    int h = base.getHeight();
    BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = img.createGraphics();
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g.drawImage(base, 0, 0, null);
    int d = Math.max(6, w * 7 / 16);
    int x = w - d;
    int y = h - d;
    g.setColor(new Color(0x0B, 0x10, 0x26));
    g.fillOval(x - 1, y - 1, d + 2, d + 2);
    g.setColor(color);
    g.fillOval(x, y, d, d);
    g.dispose();
    return img;
  }
}
