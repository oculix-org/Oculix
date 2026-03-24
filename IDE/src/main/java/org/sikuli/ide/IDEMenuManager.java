/*
 * Copyright (c) 2010-2021, sikuli.org, sikulix.com - MIT license
 */
package org.sikuli.ide;

import org.sikuli.basics.Debug;
import org.sikuli.basics.PreferencesUser;
import org.sikuli.basics.Settings;
import org.sikuli.script.SX;
import org.sikuli.script.Sikulix;
import org.sikuli.support.Commons;
import org.sikuli.support.FileManager;
import org.sikuli.support.devices.IScreen;
import org.sikuli.support.gui.SXDialog;
import org.sikuli.support.ide.AutoUpdater;
import org.sikuli.support.ide.IDEDesktopSupport;

import javax.swing.*;
import javax.swing.text.DefaultEditorKit;
import javax.swing.text.Element;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;
import javax.swing.undo.UndoManager;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;
import java.io.File;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Method;
import java.net.URLDecoder;
import java.security.CodeSource;
import java.util.*;

class IDEMenuManager {

  private static final String me = "IDE: ";

  private static void log(String message, Object... args) {
    Debug.logx(3, me + message, args);
  }

  private final SikulixIDE ide;

  IDEMenuManager(SikulixIDE ide) {
    this.ide = ide;
  }

  private JMenuBar _menuBar = new JMenuBar();
  private JMenu _fileMenu = null;
  private JMenu _editMenu = null;
  private UndoAction _undoAction = null;
  private RedoAction _redoAction = null;
  private JMenu _runMenu = null;
  private JMenu _viewMenu = null;
  private JMenu _toolMenu = null;
  private JMenu _helpMenu = null;
  private String findText = "";
  private JCheckBoxMenuItem chkShowThumbs;

  private static JMenu recentMenu = null;
  private static Map<String, String> recentProjects = new HashMap<>();
  private static java.util.List<String> recentProjectsMenu = new ArrayList<>();
  private static int recentMax = 10;
  private static int recentMaxMax = recentMax + 10;

  private static Boolean newBuildAvailable = null;
  private static String newBuildStamp = "";

  void initMenuBars(JFrame frame) {
    try {
      initFileMenu();
      initEditMenu();
      initRunMenu();
      initViewMenu();
      initToolMenu();
      initHelpMenu();
    } catch (NoSuchMethodException e) {
      log("Problem when initializing menues\nError: %s", e.getMessage());
    }

    _menuBar.add(_fileMenu);
    _menuBar.add(_editMenu);
    _menuBar.add(_runMenu);
    _menuBar.add(_viewMenu);
    _menuBar.add(_toolMenu);
    _menuBar.add(_helpMenu);
    frame.setJMenuBar(_menuBar);
  }

  JMenu getFileMenu() {
    return _fileMenu;
  }

  JMenu getRunMenu() {
    return _runMenu;
  }

  FileAction getFileAction(int tabIndex) {
    return new FileAction(tabIndex);
  }

  void updateUndoRedoStates() {
    _undoAction.updateUndoState();
    _redoAction.updateRedoState();
  }

  void setShowThumbsCheckState(boolean state) {
    if (chkShowThumbs != null) {
      chkShowThumbs.setState(state);
    }
  }

  JMenuItem createMenuItem(JMenuItem item, KeyStroke shortcut, ActionListener listener) {
    if (shortcut != null) {
      item.setAccelerator(shortcut);
    }
    item.addActionListener(listener);
    return item;
  }

  JMenuItem createMenuItem(String name, KeyStroke shortcut, ActionListener listener) {
    var item = new JMenuItem(name);
    return createMenuItem(item, shortcut, listener);
  }

  class MenuAction implements ActionListener {

    Method actMethod = null;
    String action;

    MenuAction() {
    }

    MenuAction(String item) throws NoSuchMethodException {
      Class[] paramsWithEvent = new Class[1];
      try {
        paramsWithEvent[0] = Class.forName("java.awt.event.ActionEvent");
        actMethod = this.getClass().getMethod(item, paramsWithEvent);
        action = item;
      } catch (ClassNotFoundException cnfe) {
        log("Can't find menu action: " + cnfe);
      }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      if (actMethod != null) {
        try {
          log("MenuAction." + action);
          Object[] params = new Object[1];
          params[0] = e;
          actMethod.invoke(this, params);
        } catch (Exception ex) {
          log("Problem when trying to invoke menu action %s\nError: %s",
              action, ex.getMessage());
        }
      }
    }
  }

  private void initFileMenu() throws NoSuchMethodException {
    _fileMenu = new JMenu(SikulixIDE._I("menuFile"));
    JMenuItem jmi;
    int scMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
    _fileMenu.setMnemonic(java.awt.event.KeyEvent.VK_F);

    if (IDEDesktopSupport.showAbout) {
      _fileMenu.add(createMenuItem("About SikuliX",
          KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_A, scMask),
          new FileAction(FileAction.ABOUT)));
      _fileMenu.addSeparator();
    }

    _fileMenu.add(createMenuItem(SikulixIDE._I("menuFileNew"),
        KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_N, scMask),
        new FileAction(FileAction.NEW)));

    jmi = _fileMenu.add(createMenuItem(SikulixIDE._I("menuFileOpen"),
        KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_O, scMask),
        new FileAction(FileAction.OPEN)));
    jmi.setName("OPEN");

    recentMenu = new JMenu(SikulixIDE._I("menuRecent"));

    if (Settings.experimental) {
      _fileMenu.add(recentMenu);
    }

    jmi = _fileMenu.add(createMenuItem(SikulixIDE._I("menuFileSave"),
        KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_S, scMask),
        new FileAction(FileAction.SAVE)));
    jmi.setName("SAVE");

    jmi = _fileMenu.add(createMenuItem(SikulixIDE._I("menuFileSaveAs"),
        KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_S,
            InputEvent.SHIFT_DOWN_MASK | scMask),
        new FileAction(FileAction.SAVE_AS)));
    jmi.setName("SAVE_AS");

    _fileMenu.add(createMenuItem(SikulixIDE._I("menuFileExport"),
        KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_E,
            InputEvent.SHIFT_DOWN_MASK | scMask),
        new FileAction(FileAction.EXPORT)));

    _fileMenu.add(createMenuItem("Export as jar",
        KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_J, scMask),
        new FileAction(FileAction.ASJAR)));

    _fileMenu.add(createMenuItem("Export as runnable jar",
        KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_J,
            InputEvent.SHIFT_DOWN_MASK | scMask),
        new FileAction(FileAction.ASRUNJAR)));

    jmi = _fileMenu.add(createMenuItem(SikulixIDE._I("menuFileCloseTab"),
        KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_W, scMask),
        new FileAction(FileAction.CLOSE_TAB)));
    jmi.setName("CLOSE_TAB");

    if (IDEDesktopSupport.showPrefs) {
      _fileMenu.addSeparator();
      _fileMenu.add(createMenuItem(SikulixIDE._I("menuFilePreferences"),
          KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_P, scMask),
          new FileAction(FileAction.PREFERENCES)));
    }

    _fileMenu.addSeparator();
    _fileMenu.add(createMenuItem("Open Special Files",
        KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_O, InputEvent.ALT_DOWN_MASK | scMask),
        new FileAction(FileAction.OPEN_SPECIAL)));

    if (IDEDesktopSupport.showQuit) {
      _fileMenu.addSeparator();
      _fileMenu.add(createMenuItem(SikulixIDE._I("menuFileQuit"),
          null, new FileAction(FileAction.QUIT)));
    }
  }

  class FileAction extends MenuAction {

    static final String ABOUT = "doAbout";
    static final String NEW = "doNew";
    static final String OPEN = "doOpen";
    static final String RECENT = "doRecent";
    static final String SAVE = "doSave";
    static final String SAVE_AS = "doSaveAs";
    static final String SAVE_ALL = "doSaveAll";
    static final String EXPORT = "doExport";
    static final String ASJAR = "doAsJar";
    static final String ASRUNJAR = "doAsRunJar";
    static final String CLOSE_TAB = "doCloseTab";
    static final String PREFERENCES = "doPreferences";
    static final String QUIT = "doQuit";
    static final String ENTRY = "doRecent";
    static final String OPEN_SPECIAL = "doOpenSpecial";

    FileAction(String item) throws NoSuchMethodException {
      super(item);
    }

    FileAction(int tabIndex) {
      super();
      targetTab = tabIndex;
    }

    private int targetTab = -1;

    public void doNew(ActionEvent ae) {
      ide.createEmptyScriptContext();
    }

    public void doOpen(ActionEvent ae) {
      final File file = ide.selectFileToOpen();
      ide.createFileContext(file);
    }

    public void doRecent(ActionEvent ae) {
      log(ae.getActionCommand());
    }

    class OpenRecent extends MenuAction {

      OpenRecent() {
        super();
      }

      void openRecent(ActionEvent ae) {
        log(ae.getActionCommand());
      }
    }

    public void doSave(ActionEvent ae) {
      ide.getActiveContext().save();
    }

    public void doSaveAs(ActionEvent ae) {
      ide.getActiveContext().saveAs();
    }

    public void doExport(ActionEvent ae) {
      ide.exportAsZip();
    }

    public void doAsJar(ActionEvent ae) {
      EditorPane codePane = ide.getCurrentCodePane();
      String orgName = codePane.getCurrentShortFilename();
      log("doAsJar requested: %s", orgName);
      if (codePane.isDirty()) {
        Sikulix.popError("Please save script before!", "Export as jar");
      } else {
        File fScript = codePane.saveAndGetCurrentFile();
        List<String> options = new ArrayList<>();
        options.add("plain");
        options.add(fScript.getParentFile().getAbsolutePath());
        String fpJar = FileManager.makeScriptjar(options);
        if (null != fpJar) {
          Sikulix.popup(fpJar, "Export as jar ...");
        } else {
          Sikulix.popError("did not work for: " + orgName, "Export as jar");
        }
      }
    }

    public void doAsRunJar(ActionEvent ae) {
      EditorPane codePane = ide.getCurrentCodePane();
      String orgName = codePane.getCurrentShortFilename();
      log("doAsRunJar requested: %s", orgName);
      if (codePane.isDirty()) {
        Sikulix.popError("Please save script before!", "Export as runnable jar");
      } else {
        File fScript = codePane.saveAndGetCurrentFile();
        List<String> options = new ArrayList<>();
        options.add(fScript.getParentFile().getAbsolutePath());
        String fpJar = FileManager.makeScriptjar(options);
        if (null != fpJar) {
          Sikulix.popup(fpJar, "Export as runnable jar ...");
        } else {
          Sikulix.popError("did not work for: " + orgName, "Export as runnable jar");
        }
      }
    }

    public void doCloseTab(ActionEvent ae) {
      ide.getActiveContext().close();
    }

    public void doAbout(ActionEvent ae) {
      new SXDialog("sxideabout", SikulixIDE.getWindowTop(), SXDialog.POSITION.TOP).run();
    }

    public void doPreferences(ActionEvent ae) {
      ide.showPreferencesWindow();
    }

    public void doOpenSpecial(ActionEvent ae) {
      (new Thread() {
        @Override
        public void run() {
          ide.openSpecial();
        }
      }).start();
    }

    public void doQuit(ActionEvent ae) {
      log("Quit requested");
      ide.terminate();
      log("Quit: cancelled or did not work");
    }
  }

  private void initEditMenu() throws NoSuchMethodException {
    int scMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMask();

    _editMenu = new JMenu(SikulixIDE._I("menuEdit"));
    _editMenu.setMnemonic(java.awt.event.KeyEvent.VK_E);

    _undoAction = new UndoAction();
    JMenuItem undoItem = _editMenu.add(_undoAction);
    undoItem.setAccelerator(
        KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_Z, scMask));

    _redoAction = new RedoAction();
    JMenuItem redoItem = _editMenu.add(_redoAction);
    redoItem.setAccelerator(
        KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_Z, scMask | InputEvent.SHIFT_DOWN_MASK));

    _editMenu.addSeparator();
    _editMenu.add(createMenuItem(SikulixIDE._I("menuEditCopy"),
        KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_C, scMask),
        new EditAction(EditAction.COPY)));
    _editMenu.add(createMenuItem("Copy line",
        KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_C, scMask | InputEvent.SHIFT_DOWN_MASK),
        new EditAction(EditAction.COPY)));
    _editMenu.add(createMenuItem(SikulixIDE._I("menuEditCut"),
        KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_X, scMask),
        new EditAction(EditAction.CUT)));
    _editMenu.add(createMenuItem("Cut line",
        KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_X, scMask | InputEvent.SHIFT_DOWN_MASK),
        new EditAction(EditAction.CUT)));
    _editMenu.add(createMenuItem(SikulixIDE._I("menuEditPaste"),
        KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_V, scMask),
        new EditAction(EditAction.PASTE)));
    _editMenu.add(createMenuItem(SikulixIDE._I("menuEditSelectAll"),
        KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_A, scMask),
        new EditAction(EditAction.SELECT_ALL)));

    _editMenu.addSeparator();
    var findMenu = new JMenu(SikulixIDE._I("menuFind"));
    findMenu.setMnemonic(KeyEvent.VK_F);
    findMenu.add(createMenuItem(SikulixIDE._I("menuFindFind"),
        KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F, scMask),
        new FindAction(FindAction.FIND)));
    findMenu.add(createMenuItem(SikulixIDE._I("menuFindFindNext"),
        KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_G, scMask),
        new FindAction(FindAction.FIND_NEXT)));
    findMenu.add(createMenuItem(SikulixIDE._I("menuFindFindPrev"),
        KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_G, scMask | InputEvent.SHIFT_MASK),
        new FindAction(FindAction.FIND_PREV)));
    _editMenu.add(findMenu);

    _editMenu.addSeparator();
    _editMenu.add(createMenuItem(SikulixIDE._I("menuEditIndent"),
        KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_TAB, 0),
        new EditAction(EditAction.INDENT)));
    _editMenu.add(createMenuItem(SikulixIDE._I("menuEditUnIndent"),
        KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_TAB, InputEvent.SHIFT_MASK),
        new EditAction(EditAction.UNINDENT)));
  }

  class EditAction extends MenuAction {

    static final String CUT = "doCut";
    static final String COPY = "doCopy";
    static final String PASTE = "doPaste";
    static final String SELECT_ALL = "doSelectAll";
    static final String INDENT = "doIndent";
    static final String UNINDENT = "doUnindent";

    EditAction() {
      super();
    }

    EditAction(String item) throws NoSuchMethodException {
      super(item);
    }

    private void performEditorAction(String action, ActionEvent ae) {
      EditorPane pane = ide.getCurrentCodePane();
      pane.getActionMap().get(action).actionPerformed(ae);
    }

    private void selectLine() {
      EditorPane pane = ide.getCurrentCodePane();
      Element lineAtCaret = pane.getLineAtCaret(-1);
      pane.select(lineAtCaret.getStartOffset(), lineAtCaret.getEndOffset());
    }

    public void doCut(ActionEvent ae) {
      if (ide.getCurrentCodePane().getSelectedText() == null) {
        selectLine();
      }
      performEditorAction(DefaultEditorKit.cutAction, ae);
    }

    public void doCopy(ActionEvent ae) {
      if (ide.getCurrentCodePane().getSelectedText() == null) {
        selectLine();
      }
      performEditorAction(DefaultEditorKit.copyAction, ae);
    }

    public void doPaste(ActionEvent ae) {
      performEditorAction(DefaultEditorKit.pasteAction, ae);
    }

    public void doSelectAll(ActionEvent ae) {
      performEditorAction(DefaultEditorKit.selectAllAction, ae);
    }

    public void doIndent(ActionEvent ae) {
      EditorPane pane = ide.getCurrentCodePane();
      (new SikuliEditorKit.InsertTabAction()).actionPerformed(pane);
    }

    public void doUnindent(ActionEvent ae) {
      EditorPane pane = ide.getCurrentCodePane();
      (new SikuliEditorKit.DeindentAction()).actionPerformed(pane);
    }
  }

  class FindAction extends MenuAction {

    static final String FIND = "doFind";
    static final String FIND_NEXT = "doFindNext";
    static final String FIND_PREV = "doFindPrev";

    FindAction() {
      super();
    }

    FindAction(String item) throws NoSuchMethodException {
      super(item);
    }

    public void doFind(ActionEvent ae) {
      findText = Sikulix.input(
          "Enter text to be searched (case sensitive)\n" +
              "Start with ! to search case insensitive\n",
          findText, "SikuliX IDE -- Find");
      if (null == findText) {
        return;
      }
      if (findText.isEmpty()) {
        Debug.error("Find(%s): search text is empty", findText);
        return;
      }
      if (!findStr(findText)) {
        Debug.error("Find(%s): not found", findText);
      }
    }

    public void doFindNext(ActionEvent ae) {
      if (!findText.isEmpty()) {
        findNext(findText);
      }
    }

    public void doFindPrev(ActionEvent ae) {
      if (!findText.isEmpty()) {
        findPrev(findText);
      }
    }

    private boolean _find(String str, int begin, boolean forward) {
      if (str == "!") {
        return false;
      }
      EditorPane codePane = ide.getCurrentCodePane();
      int pos = codePane.search(str, begin, forward);
      log("find \"" + str + "\" at " + begin + ", found: " + pos);
      return pos >= 0;
    }

    boolean findStr(String str) {
      if (ide.getCurrentCodePane() != null) {
        return _find(str, ide.getCurrentCodePane().getCaretPosition(), true);
      }
      return false;
    }

    boolean findPrev(String str) {
      if (ide.getCurrentCodePane() != null) {
        return _find(str, ide.getCurrentCodePane().getCaretPosition(), false);
      }
      return false;
    }

    boolean findNext(String str) {
      if (ide.getCurrentCodePane() != null) {
        return _find(str,
            ide.getCurrentCodePane().getCaretPosition() + str.length(),
            true);
      }
      return false;
    }
  }

  class UndoAction extends AbstractAction {

    UndoAction() {
      super(SikulixIDE._I("menuEditUndo"));
      setEnabled(false);
    }

    void updateUndoState() {
      final PaneContext activePane = ide.getActiveContext();
      EditorPane pane = activePane.getPane();
      if (pane != null) {
        final UndoManager manager = pane.getUndoRedo().getUndoManager();
        if (manager.canUndo()) {
          setEnabled(true);
        }
      } else {
        setEnabled(false);
      }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      final PaneContext activePane = ide.getActiveContext();
      EditorPane pane = activePane.getPane();
      if (pane != null) {
        try {
          pane.getUndoRedo().getUndoManager().undo();
        } catch (CannotUndoException ex) {
        }
        updateUndoState();
        _redoAction.updateRedoState();
      }
    }
  }

  class RedoAction extends AbstractAction {

    RedoAction() {
      super(SikulixIDE._I("menuEditRedo"));
      setEnabled(false);
    }

    protected void updateRedoState() {
      final PaneContext activePane = ide.getActiveContext();
      EditorPane pane = activePane.getPane();
      if (pane != null) {
        if (pane.getUndoRedo().getUndoManager().canRedo()) {
          setEnabled(true);
        }
      } else {
        setEnabled(false);
      }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      final PaneContext activePane = ide.getActiveContext();
      EditorPane pane = activePane.getPane();
      if (pane != null) {
        try {
          pane.getUndoRedo().getUndoManager().redo();
        } catch (CannotRedoException ex) {
        }
        updateRedoState();
        _undoAction.updateUndoState();
      }
    }
  }

  private void initRunMenu() throws NoSuchMethodException {
    int scMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMask();
    _runMenu = new JMenu(SikulixIDE._I("menuRun"));
    _runMenu.setMnemonic(java.awt.event.KeyEvent.VK_R);
    _runMenu.add(createMenuItem(SikulixIDE._I("menuRunRun"),
        KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_R, scMask),
        new RunAction(RunAction.RUN)));
    _runMenu.add(createMenuItem(SikulixIDE._I("menuRunRunAndShowActions"),
        KeyStroke.getKeyStroke(KeyEvent.VK_R,
            InputEvent.ALT_DOWN_MASK | scMask),
        new RunAction(RunAction.RUN_SHOW_ACTIONS)));
    _runMenu.add(createMenuItem("Run selection",
        KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_R,
            InputEvent.SHIFT_DOWN_MASK | scMask),
        new RunAction(RunAction.RUN_SELECTION)));
  }

  class RunAction extends MenuAction {

    static final String RUN = "runNormal";
    static final String RUN_SHOW_ACTIONS = "runShowActions";
    static final String RUN_SELECTION = "runSelection";

    RunAction() {
      super();
    }

    RunAction(String item) throws NoSuchMethodException {
      super(item);
    }

    public void runNormal(ActionEvent ae) {
      ide.runManager.getBtnRun().runCurrentScript();
    }

    public void runShowActions(ActionEvent ae) {
      ide.runManager.getBtnRunSlow().runCurrentScript();
    }

    public void runSelection(ActionEvent ae) {
      ide.getCurrentCodePane().runSelection();
    }
  }

  private void initViewMenu() throws NoSuchMethodException {
    int scMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMask();
    _viewMenu = new JMenu(SikulixIDE._I("menuView"));
    _viewMenu.setMnemonic(java.awt.event.KeyEvent.VK_V);

    boolean prefMorePlainText = PreferencesUser.get().getPrefMorePlainText();

    chkShowThumbs = new JCheckBoxMenuItem(SikulixIDE._I("menuViewShowThumbs"), !prefMorePlainText);
    _viewMenu.add(createMenuItem(chkShowThumbs,
        KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_T, scMask),
        new ViewAction(ViewAction.SHOW_THUMBS)));
  }

  class ViewAction extends MenuAction {

    static final String SHOW_THUMBS = "toggleShowThumbs";

    ViewAction() {
      super();
    }

    ViewAction(String item) throws NoSuchMethodException {
      super(item);
    }

    private long lastWhen = -1;

    public void toggleShowThumbs(ActionEvent ae) {
      if (Commons.runningMac()) {
        if (lastWhen < 0) {
          lastWhen = new Date().getTime();
        } else {
          long delay = new Date().getTime() - lastWhen;
          lastWhen = -1;
          if (delay < 500) {
            JCheckBoxMenuItem source = (JCheckBoxMenuItem) ae.getSource();
            source.setState(!source.getState());
            return;
          }
        }
      }
      boolean showThumbsState = chkShowThumbs.getState();
      ide.getActiveContext().setShowThumbs(showThumbsState);
      ide.getActiveContext().reparse();
    }
  }

  private void initToolMenu() throws NoSuchMethodException {
    _toolMenu = new JMenu(SikulixIDE._I("menuTool"));
    _toolMenu.setMnemonic(java.awt.event.KeyEvent.VK_T);

    _toolMenu.add(createMenuItem(SikulixIDE._I("menuToolExtensions"),
        null,
        new ToolAction(ToolAction.EXTENSIONS)));

    _toolMenu.add(createMenuItem("Pack Jar with Jython",
        null,
        new ToolAction(ToolAction.JARWITHJYTHON)));

    _toolMenu.add(createMenuItem("Pack Jar with Jython",
        null,
        new ToolAction(ToolAction.JARWITHJYTHON)));

    _toolMenu.add(createMenuItem(SikulixIDE._I("menuToolAndroid"),
        null,
        new ToolAction(ToolAction.ANDROID)));
  }

  class ToolAction extends MenuAction {

    static final String EXTENSIONS = "extensions";
    static final String JARWITHJYTHON = "jarWithJython";
    static final String ANDROID = "android";

    ToolAction() {
      super();
    }

    ToolAction(String item) throws NoSuchMethodException {
      super(item);
    }

    public void extensions(ActionEvent ae) {
      showExtensions();
    }

    public void jarWithJython(ActionEvent ae) {
      if (SX.popAsk("*** You should know what you are doing! ***\n\n" +
          "This may take a while. Wait for success popup!" +
          "\nClick Yes to start.", "Creating jar file")) {
        (new Thread() {
          @Override
          public void run() {
            makeJarWithJython();
          }
        }).start();
      }
    }

    public void android(ActionEvent ae) {
      androidSupport();
    }
  }

  private void showExtensions() {
    ExtensionManager.show();
  }

  private void makeJarWithJython() {
    String ideJarName = getRunningJar(SikulixIDE.class);
    if (ideJarName.isEmpty()) {
      log("makeJarWithJython: JAR containing IDE not available");
      return;
    }
    if (ideJarName.endsWith("/classes/")) {
      String version = Commons.getSXVersionShort();
      String name = "sikulixide-" + version + "-complete.jar";
      ideJarName = new File(new File(ideJarName).getParentFile(), name).getAbsolutePath();
    }
    String jythonJarName = "";
    try {
      jythonJarName = getRunningJar(Class.forName("org.python.util.jython"));
    } catch (ClassNotFoundException e) {
      log("makeJarWithJython: Jar containing Jython not available");
      return;
    }
    String targetJar = new File(Commons.getAppDataStore(), "sikulixjython.jar").getAbsolutePath();
    String[] jars = new String[]{ideJarName, jythonJarName};
    SikulixIDE.getStatusbar().setMessage(String.format("Creating SikuliX with Jython: %s", targetJar));
    if (FileManager.buildJar(targetJar, jars, null, null, null)) {
      String msg = String.format("Created SikuliX with Jython: %s", targetJar);
      log(msg);
      SX.popup(msg.replace(": ", "\n"));
    } else {
      String msg = String.format("Create SikuliX with Jython not possible: %s", targetJar);
      log(msg);
      SX.popError(msg.replace(": ", "\n"));
    }
    SikulixIDE.getStatusbar().resetMessage();
  }

  private String getRunningJar(Class clazz) {
    String jarName = "";
    CodeSource codeSrc = clazz.getProtectionDomain().getCodeSource();
    if (codeSrc != null && codeSrc.getLocation() != null) {
      try {
        jarName = codeSrc.getLocation().getPath();
        jarName = URLDecoder.decode(jarName, "utf8");
      } catch (UnsupportedEncodingException e) {
        log("URLDecoder: not possible: " + jarName);
        jarName = "";
      }
    }
    return jarName;
  }


  private void androidSupport() {
//    final ADBScreen aScr = new ADBScreen();
//    String title = "Android Support - !!EXPERIMENTAL!!";
//    if (aScr.isValid()) {
//      String warn = "Device found: " + aScr.getDeviceDescription() + "\n\n" +
//          "click Check: a short test is run with the device\n" +
//          "click Default...: set device as default screen for capture\n" +
//          "click Cancel: capture is reset to local screen\n" +
//          "\nBE PREPARED: Feature is experimental - no guarantee ;-)";
//      String[] options = new String[3];
//      options[WARNING_DO_NOTHING] = "Check";
//      options[WARNING_ACCEPTED] = "Default Android";
//      options[WARNING_CANCEL] = "Cancel";
//      int ret = JOptionPane.showOptionDialog(this, warn, title, 0, JOptionPane.WARNING_MESSAGE, null, options, options[2]);
//      if (ret == WARNING_CANCEL || ret == JOptionPane.CLOSED_OPTION) {
//        defaultScreen = null;
//        return;
//      }
//      if (ret == WARNING_DO_NOTHING) {
//        SikulixIDE.hideIDE();
//        Thread test = new Thread() {
//          @Override
//          public void run() {
//            androidSupportTest(aScr);
//          }
//        };
//        test.start();
//      } else if (ret == WARNING_ACCEPTED) {
//        defaultScreen = aScr;
//        return;
//      }
//    } else if (!ADBClient.isAdbAvailable) {
//      Sikulix.popError("Package adb seems not to be available.\nIt must be installed for Android support.", title);
//    } else {
//      Sikulix.popError("No android device attached", title);
//    }
  }

  private void androidSupportTest(IScreen aScr) {
//    ADBTest.ideTest(aScr);
//    ADBScreen.stop();
    SikulixIDE.doShow();
  }

  private void initHelpMenu() throws NoSuchMethodException {
    _helpMenu = new JMenu(SikulixIDE._I("menuHelp"));
    _helpMenu.setMnemonic(java.awt.event.KeyEvent.VK_H);

    _helpMenu.add(createMenuItem(SikulixIDE._I("menuHelpQuickStart"),
        null, new HelpAction(HelpAction.QUICK_START)));
    _helpMenu.addSeparator();

    _helpMenu.add(createMenuItem(SikulixIDE._I("menuHelpGuide"),
        null, new HelpAction(HelpAction.OPEN_DOC)));
    _helpMenu.add(createMenuItem(SikulixIDE._I("menuHelpFAQ"),
        null, new HelpAction(HelpAction.OPEN_FAQ)));
    _helpMenu.add(createMenuItem(SikulixIDE._I("menuHelpAsk"),
        null, new HelpAction(HelpAction.OPEN_ASK)));
    _helpMenu.add(createMenuItem(SikulixIDE._I("menuHelpBugReport"),
        null, new HelpAction(HelpAction.OPEN_BUG_REPORT)));

    _helpMenu.addSeparator();
    _helpMenu.add(createMenuItem(SikulixIDE._I("menuHelpHomepage"),
        null, new HelpAction(HelpAction.OPEN_HOMEPAGE)));
  }

  private void lookUpdate() {
    newBuildAvailable = null;
    String token = "This version was built at ";
    String httpDownload = "#https://raiman.github.io/SikuliX1/downloads.html";
    String pageDownload = FileManager.downloadURLtoString(httpDownload);
    if (!pageDownload.isEmpty()) {
      newBuildAvailable = false;
    }
    int locStamp = pageDownload.indexOf(token);
    if (locStamp > 0) {
      locStamp += token.length();
      String latestBuildFull = pageDownload.substring(locStamp, locStamp + 16);
      String latestBuild = latestBuildFull.replaceAll("-", "").replace("_", "").replace(":", "");
      String actualBuild = Commons.getSxBuildStamp();
      try {
        long lb = Long.parseLong(latestBuild);
        long ab = Long.parseLong(actualBuild);
        if (lb > ab) {
          newBuildAvailable = true;
          newBuildStamp = latestBuildFull;
        }
        log("latest build: %s this build: %s (newer: %s)", latestBuild, actualBuild, newBuildAvailable);
      } catch (NumberFormatException e) {
        log("check for new build: stamps not readable");
      }
    }
  }

  class HelpAction extends MenuAction {

    static final String CHECK_UPDATE = "doCheckUpdate";
    static final String QUICK_START = "openQuickStart";
    static final String OPEN_DOC = "openDoc";
    static final String OPEN_GUIDE = "openTutor";
    static final String OPEN_FAQ = "openFAQ";
    static final String OPEN_ASK = "openAsk";
    static final String OPEN_BUG_REPORT = "openBugReport";
    static final String OPEN_TRANSLATION = "openTranslation";
    static final String OPEN_HOMEPAGE = "openHomepage";
    static final String OPEN_DOWNLOADS = "openDownloads";

    HelpAction() {
      super();
    }

    HelpAction(String item) throws NoSuchMethodException {
      super(item);
    }

    public void openQuickStart(ActionEvent ae) {
      FileManager.openURL("http://sikulix.com/quickstart/");
    }

    public void openDoc(ActionEvent ae) {
      FileManager.openURL("http://sikulix-2014.readthedocs.org/en/latest/index.html");
    }

    public void openTutor(ActionEvent ae) {
      FileManager.openURL("http://www.sikuli.org/videos.html");
    }

    public void openFAQ(ActionEvent ae) {
      FileManager.openURL("https://answers.launchpad.net/sikuli/+faqs");
    }

    public void openAsk(ActionEvent ae) {
      String title = "SikuliX - Ask a question";
      String msg = """
              If you want to ask a question about SikuliX
              %s

              please do the following:
              - after having clicked yes
                 the page on Launchpad should open in your browser.
              - You should first check using Launchpad's search funktion,
                 wether similar questions have already been asked.
              - If you decide to ask a new question,
                 try to enter a short but speaking title
              - In a new questions's text field first paste using ctrl/cmd-v
                 which should enter the SikuliX version/system/java info
                 that was internally stored in the clipboard before

              If you do not want to ask a question now: click No""";
      askBugOrAnswer(msg, title, "https://answers.launchpad.net/sikuli");
    }

    public void openBugReport(ActionEvent ae) {
      String title = "SikuliX - Report a bug";
      String msg = """
              If you want to report a bug for SikuliX
              %s

              please do the following:
              - after having clicked yes
                 the page on Launchpad should open in your browser
              - fill in a short but speaking bug title and create the bug
              - in the bug's text field first paste using ctrl/cmd-v
                 which should enter the SikuliX version/system/java info
                 that was internally stored in the clipboard before

              If you do not want to report a bug now: click No""";
      askBugOrAnswer(msg, title, "https://bugs.launchpad.net/sikuli/+filebug");
    }

    private void askBugOrAnswer(String msg, String title, String url) {
      String si = Commons.getSystemInfo();
      System.out.println(si);
      msg = String.format(msg, si);
      if (Sikulix.popAsk(msg, title)) {
        Clipboard clb = Toolkit.getDefaultToolkit().getSystemClipboard();
        StringSelection sic = new StringSelection(si.toString());
        clb.setContents(sic, sic);
        FileManager.openURL(url);
      }
    }

    public void openTranslation(ActionEvent ae) {
      FileManager.openURL("https://translations.launchpad.net/sikuli/sikuli-x/+translations");
    }

    public void openHomepage(ActionEvent ae) {
      FileManager.openURL("http://sikulix.com");
    }

    public void openDownloads(ActionEvent ae) {
      FileManager.openURL("https://raiman.github.io/SikuliX1/downloads.html");
    }

    public void doCheckUpdate(ActionEvent ae) {
      if (!checkUpdate(false)) {
        lookUpdate();
        int msgType = newBuildAvailable != null ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.ERROR_MESSAGE;
        String updMsg = newBuildAvailable != null ? (newBuildAvailable ?
            SikulixIDE._I("msgUpdate") + ": " + newBuildStamp :
            SikulixIDE._I("msgNoUpdate")) : SikulixIDE._I("msgUpdateError");
        JOptionPane.showMessageDialog(null, updMsg,
            Commons.getSXVersionIDE(), msgType);
      }
    }

    boolean checkUpdate(boolean isAutoCheck) {
      String ver = "";
      String details;
      var au = new AutoUpdater();
      PreferencesUser pref = PreferencesUser.get();
      log("being asked to check update");
      int whatUpdate = au.checkUpdate();
      if (whatUpdate >= AutoUpdater.SOMEBETA) {
        whatUpdate -= AutoUpdater.SOMEBETA;
      }
      if (whatUpdate > 0) {
        if (whatUpdate == AutoUpdater.BETA) {
          ver = au.getBeta();
          details = au.getBetaDetails();
        } else {
          ver = au.getVersion();
          details = au.getDetails();
        }
        if (isAutoCheck && pref.getLastSeenUpdate().equals(ver)) {
          return false;
        }
        au.showUpdateFrame(SikulixIDE._I("dlgUpdateAvailable", ver), details, whatUpdate);
        PreferencesUser.get().setLastSeenUpdate(ver);
        return true;
      }
      return false;
    }
  }

  private static IScreen defaultScreen = null;

  static IScreen getDefaultScreen() {
    return defaultScreen;
  }
}
