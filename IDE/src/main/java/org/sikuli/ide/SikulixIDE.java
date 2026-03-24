/*
 * Copyright (c) 2010-2021, sikuli.org, sikulix.com - MIT license
 */
package org.sikuli.ide;

import org.sikuli.basics.*;
import org.sikuli.support.FileManager;
import org.sikuli.support.ide.*;
import org.sikuli.script.*;
import org.sikuli.support.ide.Runner;
import org.sikuli.support.runner.TextRunner;
import org.sikuli.support.Commons;
import org.sikuli.support.RunTime;
import org.sikuli.support.gui.SXDialog;
import org.sikuli.util.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.URL;
import java.util.List;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class SikulixIDE extends JFrame {

  static final String me = "IDE: ";

  private static void log(String message, Object... args) {
    Debug.logx(3, me + message, args);
  }

  private static void trace(String message, Object... args) {
    Debug.logx(4, me + message, args);
  }

  private static void error(String message, Object... args) {
    Debug.logx(-1, me + message, args);
  }

  private static void fatal(String message, Object... args) {
    Debug.logx(-1, me + "FATAL: " + message, args);
  }

  private static void todo(String message, Object... args) {
    Debug.logx(-1, me + "TODO: " + message, args);
  }

  //<editor-fold desc="00 IDE instance">
  static final SikulixIDE sikulixIDE = new SikulixIDE();

  static PreferencesUser prefs;

  final IDEFileManager fileManager = new IDEFileManager(this);
  final IDERunManager runManager = new IDERunManager(this);
  final IDEWindowManager windowManager = new IDEWindowManager(this);

  private SikulixIDE() {
    prefs = PreferencesUser.get();
    if (prefs.getUserType() < 0) {
      prefs.setIdeSession("");
      prefs.setDefaults();
    }
  }

  public static SikulixIDE get() {
    if (sikulixIDE == null) {
      throw new SikuliXception("SikulixIDE:get(): instance should not be null");
    }
    return sikulixIDE;
  }

  static Rectangle ideWindowRect = null;

  public static Rectangle getWindowRect() {
    Dimension windowSize = prefs.getIdeSize();
    Point windowLocation = prefs.getIdeLocation();
    if (windowSize.width < 700) {
      windowSize.width = 800;
    }
    if (windowSize.height < 500) {
      windowSize.height = 600;
    }
    return new Rectangle(windowLocation, windowSize);
  }

  public static Point getWindowCenter() {
    return new Point((int) getWindowRect().getCenterX(), (int) getWindowRect().getCenterY());
  }

  public static Point getWindowTop() {
    Rectangle rect = getWindowRect();
    int x = rect.x + rect.width / 2;
    int y = rect.y + 30;
    return new Point(x, y);
  }

  static JFrame ideWindow = null;

  public static void setWindow() {
    if (ideWindow == null) {
      ideWindow = sikulixIDE;
    }
  }

  public void setIDETitle(String title) {
    ideWindow.setTitle(title);
  }

  public static void doShow() {
    showAgain();
  }

  public static boolean notHidden() {
    return ideWindow.isVisible();
  }

  public static void  doHide() {
    doHide(0.5f);
  }

  public static void doHide(float waitTime) {
    ideWindow.setVisible(false);
    RunTime.pause(waitTime);
  }

  static void showAgain() {
    ideWindow.setVisible(true);
    sikulixIDE.getActiveContext().focus();
  }

  //TODO showAfterStart to be revised
  static String _I(String key, Object... args) {
    try {
      return SikuliIDEI18N._I(key, args);
    } catch (Exception e) {
      log("[I18N] " + key);
      return key;
    }
  }

  static ImageIcon getIconResource(String name) {
    URL url = SikulixIDE.class.getResource(name);
    if (url == null) {
      Debug.error("IDE: Could not load \"" + name + "\" icon");
      return null;
    }
    return new ImageIcon(url);
  }
  //</editor-fold>

  //<editor-fold desc="01 startup / quit">
  static AtomicBoolean ideIsReady = new AtomicBoolean(false);

  protected static void start() {

    ideWindowRect = getWindowRect();

    IDEDesktopSupport.init();
    IDESupport.initIDESupport();
    if (!Commons.hasOption(CommandArgsEnum.CONSOLE)) {
      get().windowManager.setMessages(new EditorConsolePane());
    }
    IDESupport.initRunners();
    Commons.startLog(1, "IDESupport ready --- GUI start (%4.1f)", Commons.getSinceStart());

    sikulixIDE.startGUI();
  }

  boolean ideIsQuitting = false;

  private boolean closeIDE() {
    ideIsQuitting = true;
    if (!doBeforeQuit()) {
      return false;
    }
    ideIsQuitting = false;
    return true;
  }

  private boolean doBeforeQuit() {
    if (checkDirtyPanes()) {
      int answer = askForSaveAll("Quit");
      if (answer == SXDialog.DECISION_CANCEL) {
        return false;
      }
      if (answer == SXDialog.DECISION_ACCEPT) {
        return saveSession(DO_SAVE_ALL);
      }
      log("Quit: without saving anything");
    }
    return saveSession(DO_SAVE_NOTHING);
  }

  int DO_SAVE_ALL = 1;
  int DO_SAVE_NOTHING = -1;

  private int askForSaveAll(String typ) {
    String message = "Some scripts are not saved yet!";
    String title = SikuliIDEI18N._I("dlgAskCloseTab");
    String ignore = typ + " immediately";
    String accept = "Save all and " + typ;
    return SXDialog.askForDecision(sikulixIDE, title, message, ignore, accept);
  }

  public boolean terminate() {
    log("Quit requested");
    if (closeIDE()) {
      RunTime.terminate(0, "");
    }
    log("Quit: cancelled or did not work");
    return false;
  }
  //</editor-fold>

  //<editor-fold desc="02 init IDE">
  private void startGUI() {
    setWindow();

    windowManager.installCaptureHotkey();
    runManager.installStopHotkey();

    ideWindow.setSize(ideWindowRect.getSize());
    ideWindow.setLocation(ideWindowRect.getLocation());

    Debug.log("IDE: Adding components to window");
    initMenuBars(ideWindow);
    final Container ideContainer = ideWindow.getContentPane();
    ideContainer.setLayout(new BorderLayout());

    windowManager.buildLayout(ideContainer);

    _status = new SikuliIDEStatusBar();
    ideContainer.add(_status, BorderLayout.SOUTH);

    Debug.log("IDE: Putting all together - before layout");
    ideContainer.doLayout();

    Debug.log("IDE: Putting all together - after layout");
    ideWindow.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);

    ideWindow.addWindowListener(new WindowAdapter() {
      @Override
      public void windowClosing(WindowEvent e) {
        terminate();
      }
    });

    ideWindow.addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent e) {
        PreferencesUser.get().setIdeSize(ideWindow.getSize());
      }

      @Override
      public void componentMoved(ComponentEvent e) {
        PreferencesUser.get().setIdeLocation(ideWindow.getLocation());
      }
    });
    ToolTipManager.sharedInstance().setDismissDelay(30000);

    createEmptyScriptContext();
    EditorConsolePane msgs = windowManager.getMessages();
    if (msgs != null) {
      msgs.initRedirect();
    }
    Debug.log("IDE: Putting all together - Restore last Session");
    restoreSession();

    windowManager.initShortcutKeys();
    ideIsReady.set(true);
    Commons.startLog(3, "IDE ready: on Java %d (%4.1f sec)", Commons.getJavaVersion(), Commons.getSinceStart());
  }

  public static void showAfterStart() {
    IDEWindowManager.showAfterStart(sikulixIDE, sikulixIDE.windowManager);
  }

  static SikuliIDEStatusBar getStatusbar() {
    return sikulixIDE._status;
  }

  private SikuliIDEStatusBar _status;

  CloseableTabbedPane getTabs() {
    return windowManager.getTabs();
  }
  //</editor-fold>

  //<editor-fold desc="03 PaneContext">
  List<PaneContext> contexts = new ArrayList<>();
  List<PaneContext> contextsClosed = new ArrayList<>();
  String tempName = "sxtemp";
  int tempIndex = 1;

  PaneContext lastContext = null;

  PaneContext getActiveContext() {
    final int ix = getTabs().getSelectedIndex();
    if (ix < 0) {
      fatal("PaneContext: no context available"); //TODO possible?
    }
    return contexts.get(ix);
  }

  PaneContext setActiveContext(int pos) {
    getTabs().setSelectedIndex(pos);
    PaneContext context = contexts.get(pos);
    showContext(context);
    return context;
  }

  PaneContext getContextAt(int ix) {
    return contexts.get(ix);
  }

  void switchContext(int ix) {
    if (ix < 0) {
      return;
    }
    if (ix >= contexts.size()) {
      RunTime.terminate(999, "IDE: switchPane: invalid tab index: %d (valid: 0 .. %d)",
              ix, contexts.size() - 1);
    }
    PaneContext context = contexts.get(ix);
    PaneContext previous = lastContext;
    lastContext = context;

    if (null != previous && previous.isDirty()) {
      previous.save();
    }
    showContext(context);
  }

  private void showContext(PaneContext context) {
    setIDETitle(context.getFile().getAbsolutePath());
    ImagePath.setBundleFolder(context.getFolder());

    getStatusbar().setType(context.getType());

    setShowThumbsCheckState(getActiveContext().getShowThumbs());

    final EditorPane editorPane = context.getPane();
    int dot = editorPane.getCaret().getDot();
    editorPane.setCaretPosition(dot);
    updateUndoRedoStates();

    if (context.isText()) {
      collapseMessageArea();
    } else {
      uncollapseMessageArea();
    }
  }

  void createEmptyScriptContext() {
    final PaneContext context = new PaneContext();
    context.setRunner(IDESupport.getDefaultRunner());
    context.setFile();
    context.create();
  }

  void createEmptyTextContext() {
    final PaneContext context = new PaneContext();
    context.setRunner(Runner.getRunner(TextRunner.class));
    context.setFile();
    context.create();
  }

  void createFileContext(File file) {
    final int pos = alreadyOpen(file);
    if (pos >= 0) {
      setActiveContext(pos);
      log("PaneContext: alreadyopen: %s", file);
      return;
    }
    final PaneContext context = new PaneContext();
    context.setFile(file);
    context.setRunner();
    if (!context.isValid()) {
      log("PaneContext: open not posssible: %s", file);
      return;
    }
    context.pos = contexts.size();
    context.create();
    context.doShowThumbs();
    context.notDirty();
  }

  int alreadyOpen(File file) {
    for (PaneContext context : contexts) {
      File folderOrFile = context.file;
      if (context.isBundle(file)) {
        folderOrFile = context.folder;
      }
      if (file.equals(folderOrFile)) {
        return context.pos;
      }
    }
    return -1;
  }

  public File selectFileToOpen() {
    return fileManager.selectFileToOpen();
  }

  public File selectFileForSave(PaneContext context) {
    return fileManager.selectFileForSave(context);
  }

  boolean checkDirtyPanes() {
    return fileManager.checkDirtyPanes();
  }

  // PaneContext extracted to PaneContext.java

  EditorPane getCurrentCodePane() {
    return getActiveContext().getPane();
  }

  EditorPane getPaneAtIndex(int index) {
    return getContextAt(index).getPane();
  }

  public void exportAsZip() {
    fileManager.exportAsZip();
  }

  public void reparseOnRenameImage(String oldName, String newName, boolean fileOverWritten) {
    fileManager.reparseOnRenameImage(oldName, newName, fileOverWritten);
  }

  public String getImageNameFromLine() {
    return fileManager.getImageNameFromLine();
  }

  String getLineTextAtCaret() {
    return fileManager.getLineTextAtCaret();
  }
//</editor-fold>

  //<editor-fold defaultstate="collapsed" desc="04 save / restore session">
  private boolean saveSession(int saveAction) {
    return fileManager.saveSession(saveAction);
  }

  private List<File> restoreSession() {
    return fileManager.restoreSession();
  }
//</editor-fold>

  //<editor-fold desc="07 menu helpers">
  public void showAbout() {
    new SXDialog("sxideabout", SikulixIDE.getWindowTop(), SXDialog.POSITION.TOP).run();
  }

  public boolean quit() {
    return terminate();
  }

  public void showPreferencesWindow() {
    var pwin = new PreferencesWin();
    pwin.setAlwaysOnTop(true);
    pwin.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
    pwin.setVisible(true);
  }

  void openSpecial() {
    log("Open Special requested");
//    Map<String, String> specialFiles = new Hashtable<>();
//    specialFiles.put("1 SikuliX Global Options", Commons.getOptions().getOptionsFile());
//    File extensionsFile = ExtensionManager.getExtensionsFile();
//    specialFiles.put("2 SikuliX Extensions Options", extensionsFile.getAbsolutePath());
//    File sitesTxt = ExtensionManager.getSitesTxt();
//    specialFiles.put("3 SikuliX Additional Sites", sitesTxt.getAbsolutePath());
//    String[] defaults = new String[specialFiles.size()];
//    defaults[0] = Options.getOptionsFileDefault();
//    defaults[1] = ExtensionManager.getExtensionsFileDefault();
//    defaults[2] = ExtensionManager.getSitesTxtDefault();
//    String msg = "";
//    int num = 1;
//    String[] files = new String[specialFiles.size()];
//    for (String specialFile : specialFiles.keySet()) {
//      files[num - 1] = specialFiles.get(specialFile).trim();
//      msg += specialFile + "\n";
//      num++;
//    }
//    msg += "\n" + "Enter a number to select a file";
//    String answer = SX.input(msg, "Edit a special SikuliX file", false, 10);
//    if (null != answer && !answer.isEmpty()) {
//      try {
//        num = Integer.parseInt(answer.substring(0, 1));
//        if (num > 0 && num <= specialFiles.size()) {
//          String file = files[num - 1];
//          if (!new File(file).exists()) {
//            FileManager.writeStringToFile(defaults[num - 1], file);
//          }
//          log( "Open Special: should load: %s", file);
//          newTabWithContent(file);
//        }
//      } catch (NumberFormatException e) {
//      }
//    }
  }
//</editor-fold>

  //<editor-fold desc="10 Init Menus">
  final IDEMenuManager menuManager = new IDEMenuManager(this);

  void initMenuBars(JFrame frame) {
    menuManager.initMenuBars(frame);
  }

  JMenu getFileMenu() {
    return menuManager.getFileMenu();
  }

  JMenu getRunMenu() {
    return menuManager.getRunMenu();
  }

  IDEMenuManager.FileAction getFileAction(int tabIndex) {
    return menuManager.getFileAction(tabIndex);
  }

  void updateUndoRedoStates() {
    menuManager.updateUndoRedoStates();
  }

  void setShowThumbsCheckState(boolean state) {
    menuManager.setShowThumbsCheckState(state);
  }
  //</editor-fold>

  //</editor-fold>

  //<editor-fold defaultstate="collapsed" desc="20 Init ToolBar Buttons — delegated to IDEWindowManager">
  IDEWindowManager.ButtonRecord getBtnRecord() {
    return windowManager.getBtnRecord();
  }
//</editor-fold>

  //<editor-fold desc="21 Init Run Buttons — delegated to IDERunManager">
  void addErrorMark(int line) {
    runManager.addErrorMark(line);
  }

  void resetErrorMark() {
    runManager.resetErrorMark();
  }
  //</editor-fold>

  //<editor-fold desc="30 MsgArea — delegated to IDEWindowManager">
  void clearMessageArea() {
    windowManager.clearMessageArea();
  }

  void collapseMessageArea() {
    windowManager.collapseMessageArea();
  }

  void uncollapseMessageArea() {
    windowManager.uncollapseMessageArea();
  }

  public EditorConsolePane getConsole() {
    return windowManager.getConsole();
  }
  //</editor-fold>

  //<editor-fold defaultstate="collapsed" desc="25 Init ShortCuts HotKeys — delegated">
  void removeCaptureHotkey() {
    windowManager.removeCaptureHotkey();
  }

  void installCaptureHotkey() {
    windowManager.installCaptureHotkey();
  }

  void onStopRunning() {
    runManager.onStopRunning();
  }
  //</editor-fold>
}
