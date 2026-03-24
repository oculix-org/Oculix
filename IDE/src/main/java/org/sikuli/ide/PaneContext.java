/*
 * Copyright (c) 2010-2021, sikuli.org, sikulix.com - MIT license
 */
package org.sikuli.ide;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.sikuli.basics.Debug;
import org.sikuli.script.ImagePath;
import org.sikuli.support.Commons;
import org.sikuli.support.ide.IIDESupport;
import org.sikuli.support.ide.IDESupport;
import org.sikuli.support.ide.Runner;
import org.sikuli.support.runner.IRunner;
import org.sikuli.support.runner.InvalidRunner;
import org.sikuli.support.runner.TextRunner;
import org.sikuli.support.gui.SXDialog;
import org.sikuli.util.IButton;

import javax.swing.text.BadLocationException;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class PaneContext {

  private static final String me = "IDE: ";

  private static void log(String message, Object... args) {
    Debug.logx(3, me + message, args);
  }

  private static void fatal(String message, Object... args) {
    Debug.logx(-1, me + "FATAL: " + message, args);
  }

  private static void todo(String message, Object... args) {
    Debug.logx(-1, me + "TODO: " + message, args);
  }

  private static void error(String message, Object... args) {
    Debug.logx(-1, me + message, args);
  }

  File folder;
  File imageFolder;
  File file;
  String name;
  String ext;

  IRunner runner = null;
  IIDESupport support;
  String type;

  EditorPane pane;
  boolean showThumbs; //TODO
  int pos = 0;

  boolean dirty = false;
  boolean temp = false;

  PaneContext() {
  }

  public void focus() {
    if (isText()) {
      SikulixIDE.get().collapseMessageArea();
    }
    pane.requestFocusInWindow();
  }

  public EditorPane getPane() {
    return pane;
  }

  public boolean isValid() {
    return file.exists() && runner != null;
  }

  public boolean isText() {
    return type.equals(TextRunner.TYPE);
  }

  public boolean isTemp() {
    return temp;
  }

  public boolean hasContent() {
    return !pane.getText().isEmpty();
  }

  public IRunner getRunner() {
    return runner;
  }

  void setRunner(IRunner _runner) {
    runner = _runner;
    type = runner.getType();
    ext = runner.getDefaultExtension();
    support = IDESupport.get(type);
  }

  void setRunner() {
    if (ext.isEmpty()) {
      runner = IDESupport.getDefaultRunner();
      ext = runner.getDefaultExtension();
      file = new File(file.getAbsolutePath() + "." + ext);
    } else {
      final IRunner _runner = Runner.getRunner(file.getAbsolutePath());
      if (!(_runner instanceof InvalidRunner)) {
        runner = _runner;
      } else {
        runner = new TextRunner();
      }
    }
    type = runner.getType();
    support = IDESupport.get(type);
  }

  public IIDESupport getSupport() {
    return support;
  }

  public String getType() {
    return type;
  }

  public File getFolder() {
    return folder;
  }

  public File getFile() {
    return file;
  }

  public String getFileName() {
    return name + "." + ext;
  }

  void setFile() {
    var ide = SikulixIDE.get();
    name = ide.tempName + ide.tempIndex++;
    folder = new File(Commons.getIDETemp(), name);
    folder.mkdirs();
    file = new File(folder, name + "." + ext);
    if (file.exists()) {
      file.delete();
    }
    try {
      file.createNewFile();
    } catch (IOException e) {
      fatal("PaneContext: setFile: create not possible: %s", file); //TODO
    }
    imageFolder = folder;
    temp = true;
    log("PaneContext: setFile: %s", file);
  }

  boolean setFile(File _file) {
    if (_file == null) {
      return false;
    }
    File _folder;
    String path = _file.getAbsolutePath();
    String _ext = FilenameUtils.getExtension(path);
    String _name = FilenameUtils.getBaseName(path);
    if (_file.exists()) {
      if (_file.isDirectory()) {
        _folder = _file;
        _file = Runner.getScriptFile(_folder);
        if (_file != null) {
          _ext = FilenameUtils.getExtension(_file.getPath());
        } else {
          _ext = IDESupport.getDefaultRunner().getDefaultExtension();
          try {
            _file.createNewFile();
          } catch (IOException e) {
            fatal("PaneContext: setFile: create not possible: %s", file); //TODO
            _file = null;
          }
        }
      } else {
        _folder = _file.getParentFile();
      }
    } else {
      if (_ext.isEmpty() || _ext.equals("sikuli")) {
        _file.mkdirs();
        _folder = _file;
        _file = new File(_folder, _name);
        _ext = "";
      } else {
        _folder = _file.getParentFile();
        _folder.mkdirs();
      }
      try {
        _file.createNewFile();
      } catch (IOException e) {
        fatal("PaneContext: setFile: create not possible: %s", file); //TODO
        _file = null;
      }
    }
    if (_file == null) {
      return false;
    }
    file = _file;
    ext = _ext;
    name = _name;
    folder = _folder;
    imageFolder = folder;
    log("PaneContext: setFile: %s", file);
    return true;
  }

  public String getExt() {
    return ext;
  }

  public boolean isBundle() {
    return folder.getAbsolutePath().endsWith(".sikuli");
  }

  public boolean isBundle(File file) {
    return file.getAbsolutePath().endsWith(".sikuli") || FilenameUtils.getExtension(file.getName()).isEmpty();
  }

  public File getImageFolder() {
    return imageFolder;
  }

  public void setImageFolder(File folder) {
    if (folder == null) {
      fatal("PaneContext: setImageFolder: is null (ignored)"); //TODO
      return;
    }
    if (!folder.exists()) {
      folder.mkdirs();
    }
    if (folder.exists()) {
      imageFolder = folder;
      ImagePath.setBundleFolder(imageFolder);
    } else {
      fatal("PaneContext: setImageFolder: create not possible: %s", folder); //TODO
    }
  }

  public File getScreenshotFolder() {
    return new File(imageFolder, ImagePath.SCREENSHOT_DIRECTORY);
  }

  public File getScreenshotFile(String name) {
    if (!FilenameUtils.getExtension(name).equals("png")) {
      name += ".png";
    }
    File shot = new File(name);
    if (!shot.isAbsolute()) {
      shot = new File(getScreenshotFolder(), name);
    }
    if (shot.exists()) {
      return shot;
    }
    return null;
  }

  public File getScreenshotFile(File shot) {
    if (shot.exists()) {
      return shot;
    }
    return null;
  }

  public boolean getShowThumbs() {
    return showThumbs;
  }

  public void setShowThumbs(boolean state) {
    showThumbs = state;
  }

  int getLineStart(int lineNumber) {
    try {
      return pane.getLineStartOffset(lineNumber);
    } catch (BadLocationException e) {
      return -1;
    }
  }

  void create() {
    var ide = SikulixIDE.get();
    int lastPos = -1;
    if (ide.contexts.size() > 0) {
      lastPos = ide.getActiveContext().pos;
    }
    showThumbs = !PreferencesUser.get().getPrefMorePlainText();
    pane = new EditorPane(this);
    ide.contexts.add(pos, this);
    ide.getTabs().addNewTab(name, pane.getScrollPane(), pos);
    ide.getTabs().setSelectedIndex(pos);
    pane.makeReady();
    if (load()) {
      pane.requestFocus();
    } else {
      if (lastPos >= 0) {
        ide.getTabs().remove(pos);
        ide.getTabs().setSelectedIndex(lastPos);
        ide.contexts.remove(pos);
      } else {
        ide.getTabs().remove(0);
        fatal("PaneContext: create: start tab failed"); //TODO possible?
      }
    }
    resetPos();
  }

  public boolean close() {
    return doClose(false);
  }

  public boolean closeSilent() {
    return doClose(true);
  }

  boolean doClose(boolean discard) {
    var ide = SikulixIDE.get();
    boolean shouldSave = true;
    if (isDirty() || (isTemp() && !discard)) {
      if (isTemp()) {
        String msg = String.format("%s: content not yet saved!", getFileName());
        final int answer = SXDialog.askForDecision(ide, "Closing Tab", msg,
                "Discard", "Save");
        if (answer == SXDialog.DECISION_CANCEL) {
          return false;
        }
        if (answer == SXDialog.DECISION_ACCEPT) {
          File fileSaved = ide.selectFileForSave(this);
          if (fileSaved != null) {
            setFile(fileSaved);
          } else {
            return false;
          }
        } else {
          shouldSave = false;
        }
      }
      if (shouldSave && !discard) {
        cleanBundle(); //TODO
        save();
      }
      notDirty();
    }
    int closedPos = pos;
    ide.getTabs().remove(pos);
    ide.contexts.remove(pos);
    pos = -1;
    ide.contextsClosed.add(0, this);
    if (ide.ideIsQuitting) {
      resetPos();
      if (ide.contexts.size() > 0) {
        ide.setActiveContext(ide.contexts.size() - 1);
      }
    } else {
      if (resetPos() == 0) {
        ide.createEmptyScriptContext();
      } else {
        ide.setActiveContext(Math.min(closedPos, ide.contexts.size() - 1));
      }
    }
    return true;
  }

  private int resetPos() {
    var ide = SikulixIDE.get();
    int n = 0;
    for (PaneContext context : ide.contexts) {
      context.pos = n++;
    }
    return n;
  }

  private void cleanBundle() { //TODO consolidate with FileManager and Settings
    log("cleanBundle: %s", getName());
    String[] text = pane.getText().split("\n");
    List<Map<String, Object>> images = collectImages(text);
    Set<String> foundImages = new HashSet<>();
    for (Map<String, Object> image : images) {
      File imgFile = (File) image.get(IButton.FILE);
      String name = imgFile.getName();
      foundImages.add(name);
    }
    deleteNotUsedImages(getImageFolder(), foundImages);
    deleteNotUsedScreenshots(getScreenshotFolder(), foundImages);
    log("cleanBundle finished: %s", getName());
  }

  private String getName() {
    return name;
  }

  public void deleteNotUsedImages(File scriptFolder, Set<String> usedImages) {
    if (!scriptFolder.isDirectory()) {
      return;
    }
    File[] files = scriptFolder.listFiles((dir, name) -> {
      if ((name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg"))) {
        if (!name.startsWith("_")) {
          return true;
        }
      }
      return false;
    });
    if (files == null || files.length == 0) {
      return;
    }
    for (File image : files) {
      if (!usedImages.contains(image.getName())) {
        image.delete(); //TODO make a backup??
      }
    }
  }

  public void deleteNotUsedScreenshots(File screenshotsDir, Set<String> usedImages) {
    if (screenshotsDir.exists()) {
      File[] files = screenshotsDir.listFiles();
      if (files == null || files.length == 0) {
        return;
      }
      for (File screenshot : files) {
        if (!usedImages.contains(screenshot.getName())) {
          screenshot.delete();
        }
      }
    }
  }

  public boolean isDirty() {
    return dirty;
  }

  public void setDirty() {
    if (!dirty) {
      dirty = true;
      setFileTabTitleDirty(pos, dirty);
    }
  }

  public void notDirty() {
    if (dirty) {
      dirty = false;
      setFileTabTitleDirty(pos, dirty);
    }
  }

  void setFileTabTitleDirty(int pos, boolean isDirty) {
    var tabs = SikulixIDE.get().getTabs();
    String title = tabs.getTitleAt(pos);
    if (!isDirty && title.startsWith("*")) {
      title = title.substring(1);
      tabs.setTitleAt(pos, title);
    } else if (isDirty && !title.startsWith("*")) {
      title = "*" + title;
      tabs.setTitleAt(pos, title);
    }
  }

  public boolean saveAs() {
    var ide = SikulixIDE.get();
    boolean shouldSave = true;
    if (!isTemp() && isDirty()) {
      todo("PaneContext: saveAs: ask: discard or save changes"); //TODO
      String msg = String.format("%s: save changes?", file.getName());
      final int answer = SXDialog.askForDecision(ide, "Saving Tab", msg,
              "Do not save", "Save");
      if (answer == SXDialog.DECISION_CANCEL) {
        return false;
      }
      if (answer == SXDialog.DECISION_IGNORE) {
        shouldSave = false;
        notDirty();
      }
    }
    if (shouldSave) {
      if (!save()) {
        return false;
      }
    }
    boolean success = true;
    File file;
    while (true) {
      file = ide.selectFileForSave(this);
      if (file == null) {
        return false;
      }
      final int pos = ide.alreadyOpen(file);
      if (pos >= 0) {
        log("PaneContext: alreadyopen: %s", file);
        String msg = String.format("%s: is currently open!", file.getName());
        final int answer = SXDialog.askForDecision(ide, "Saving Tab", msg,
                "Overwrite", "Try again");
        if (answer == SXDialog.DECISION_CANCEL) {
          return false;
        }
        if (answer == SXDialog.DECISION_ACCEPT) {
          continue;
        }
        ide.contexts.get(pos).close();
        ide.setActiveContext(this.pos);
      }
      break;
    }
    final PaneContext newContext = new PaneContext();
    if (newContext.setFile(file)) {
      newContext.file.delete();
      try {
        copyContent(this, newContext, isBundle(file));
      } catch (IOException e) {
        success = false;
      }
    }
    if (success) {
      newContext.setRunner();
      newContext.pos = this.pos;
      newContext.pane = pane;
      newContext.showThumbs = showThumbs;
      ide.contexts.set(this.pos, newContext);
      ide.contextsClosed.add(0, this);
      ide.getTabs().setTitleAt(this.pos, newContext.name);
      ide.setIDETitle(newContext.file.getAbsolutePath());
    }
    return success;
  }

  private void copyContent(PaneContext currentContext, PaneContext newContext, boolean asBundle) throws
          IOException {
    if (asBundle) {
      FileUtils.copyDirectory(currentContext.folder, newContext.folder);
      final String oldName = currentContext.file.getName();
      final String newName = FilenameUtils.getBaseName(newContext.file.getName());
      final String ext = "." + FilenameUtils.getExtension(oldName);
      new File(newContext.folder, oldName).renameTo(new File(newContext.folder, newName + ext));
    } else {
      FileUtils.copyFile(currentContext.file, newContext.file);
    }
  }

  public boolean save() {
    String msg = "";
    boolean success = true;
    try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
      pane.write(bw);
      notDirty();
    } catch (IOException e) {
      msg = String.format(" did not work: %s", e.getMessage());
      success = false;
    }
    log("PaneContext: save: %s%s", name, msg);
    return success;
  }

  public boolean load() {
    return load(file);
  }

  public boolean load(File file) {
    try (InputStreamReader isr = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
      pane.loadContent(isr);
    } catch (Exception ex) {
      log("PaneContext: loadFile: %s ERROR(%s)", file, ex.getMessage());
      return false;
    }
    return true;
  }

  public boolean load(String content) {
    InputStreamReader isr;
    try {
      isr = new InputStreamReader(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)),
              StandardCharsets.UTF_8);
      pane.loadContent(isr);
    } catch (Exception ex) {
      log("PaneContext: loadString: ERROR(%s)", ex.getMessage());
      return false;
    }
    return true;
  }

  void reparse() {
    pane.saveCaretPosition();
    if (getShowThumbs()) {
      doShowThumbs();
    } else {
      resetContentToText();
    }
    pane.restoreCaretPosition();
  }

  private void doShowThumbs() {
    if (getShowThumbs()) {
      String[] text = pane.getText().split("\n");
      List<Map<String, Object>> images = collectImages(text);
      List<Map<String, Object>> patterns = patternMatcher(images, text);
      if (images.size() > 0 || patterns.size() > 0) {
        for (Map<String, Object> item : images) {
          final File imgFile = (File) item.get(IButton.FILE);
          //TODO make it optional? _image as thumb
          if (imgFile.getName().startsWith("_")) {
            continue;
          }
          final EditorImageButton button = new EditorImageButton(item);
          int itemStart = getLineStart((Integer) item.get(IButton.LINE)) + (Integer) item.get(IButton.LOFF);
          int itemEnd = itemStart + ((String) item.get(IButton.TEXT)).length();
          pane.select(itemStart, itemEnd);
          pane.insertComponent(button);
        }
        pane.setCaretPosition(0);
      }
      log("ImageButtons: images(%d) patterns(%d)", images.size(), patterns.size());
    }
  }

  private List<Map<String, Object>> collectImages(String[] text) {
    List<Map<String, Object>> images = new ArrayList<>();
    if (text.length > 0) {
      images = imageMatcher(images, text, Pattern.compile(".*?(\".*?\")"));
      images = imageMatcher(images, text, Pattern.compile(".*?('.*?')"));
    }
    return images;
  }

  private List<Map<String, Object>> patternMatcher(List<Map<String, Object>> images, String[] text) {
    List<Map<String, Object>> patterns = new ArrayList<>();
    for (Map<String, Object> match : images) {
      //TODO patternMatcher
    }
    return patterns;
  }

  private List<Map<String, Object>> imageMatcher(List<Map<String, Object>> images, String[] text, Pattern pat) {
    int lnNbr = 0;
    for (String line : text) {
      line = line.strip();
      Matcher matcher = pat.matcher(line);
      if (line.contains("\"\"\"") || line.contains("'''")) {
        continue;
      }
      while (matcher.find()) {
        String match = matcher.group(1);
        if (match != null) {
          int start = matcher.start(1);
          String imgName = match.substring(1, match.length() - 1);
          final File imgFile = imageExists(imgName);
          if (imgFile != null) {
            Map<String, Object> options = new HashMap<>();
            options.put(IButton.TEXT, match);
            options.put(IButton.LINE, lnNbr);
            options.put(IButton.LOFF, start);
            options.put(IButton.FILE, imgFile);
            images.add(options);
          }
        }
      }
      lnNbr++;
    }
    return images;
  }

  private File imageExists(String imgName) {
    String orgName = imgName;
    imgName = FilenameUtils.normalizeNoEndSeparator(imgName, true);
    imgName = imgName.replaceAll("//", "/");
    String ext;
    try {
      ext = FilenameUtils.getExtension(imgName);
    } catch (Exception e) {
      return null;
    }
    File folder = getImageFolder();
    if (ext.isEmpty()) {
      ext = "png";
      imgName += ".png";
    }
    if ("png;jpg;jpeg;".contains(ext + ";")) {
      File imgFile = new File(imgName);
      if (!imgFile.isAbsolute()) {
        imgFile = new File(folder, imgName);
      }
      if (imgFile.exists()) {
        log("%s (%s)", orgName, imgFile);
        return imgFile;
      }
    }
    return null;
  }

  public void insertImageButton(File imgFile) {
    final EditorImageButton button = new EditorImageButton(imgFile);
    pane.insertComponent(button);
  }

  public boolean resetContentToText() {
    InputStreamReader isr;
    try {
      isr = new InputStreamReader(new ByteArrayInputStream(
              pane.getText().getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8);
      pane.read(new BufferedReader(isr), null);
    } catch (Exception ex) {
      error("readContent: from String (%s)", ex.getMessage());
      return false;
    }
    return true;
  }
}
