/*
 * Copyright (c) 2010-2020, sikuli.org, sikulix.com - MIT license
 */

package org.sikuli.util;

import java.awt.EventQueue;
import java.awt.FileDialog;
import java.awt.Frame;
import java.io.File;
//import java.io.FilenameFilter;
import java.util.ResourceBundle;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileFilter;
import javax.swing.filechooser.FileNameExtensionFilter;

import org.apache.commons.io.FilenameUtils;
import org.sikuli.basics.Debug;
import org.sikuli.basics.PreferencesUser;
import org.sikuli.basics.Settings;

public class SikulixFileChooser {
  static final int FILES = JFileChooser.FILES_ONLY;
  static final int DIRS = JFileChooser.DIRECTORIES_ONLY;
  static final int DIRSANDFILES = JFileChooser.FILES_AND_DIRECTORIES;
  static final int SAVE = FileDialog.SAVE;
  static final int LOAD = FileDialog.LOAD;

  private Frame parentFrame;

  public SikulixFileChooser(Frame parentFrame) {
    this.parentFrame = parentFrame;
  }

  /**
   * Public default-directory resolver shared with any other chooser the IDE
   * spins up (Workspace dialogs, RecorderImagePicker, etc.). Same fallback
   * chain as the internal {@link #getLastDir()}: stored {@code LAST_OPEN_DIR}
   * pref → JVM working dir → user home. Returns a valid {@link File} that
   * callers can pass to {@code JFileChooser.setCurrentDirectory(...)} without
   * having to re-implement the cascade.
   */
  public static File resolveDefaultDir() {
    String stored = PreferencesUser.get().get("LAST_OPEN_DIR", "");
    if (!stored.isEmpty() && new File(stored).isDirectory()) {
      return new File(stored);
    }
    String jarDir = System.getProperty("user.dir", "");
    if (!jarDir.isEmpty() && new File(jarDir).isDirectory()) {
      return new File(jarDir);
    }
    return new File(System.getProperty("user.home", ""));
  }

  /**
   * Public companion to {@link #resolveDefaultDir()}: persists the parent
   * directory of {@code chosen} (or {@code chosen} itself if it is already a
   * directory) into the {@code LAST_OPEN_DIR} preference. Every chooser in
   * the IDE — script open/save, workspace new/open, recorder image picker —
   * should call this on a successful selection so the next dialog anywhere
   * in the IDE lands at the same place. Without this, the dark-mode build
   * had inconsistent behaviour: SikulixFileChooser persisted, but raw
   * JFileChooser sites in WorkspaceDialog / openExistingWorkspace did not.
   */
  public static void persistLastDir(File chosen) {
    if (chosen == null) return;
    File dir = chosen.isDirectory() ? chosen : chosen.getParentFile();
    if (dir == null || !dir.isDirectory()) return;
    PreferencesUser.get().put("LAST_OPEN_DIR", dir.getAbsolutePath());
  }

  private String getLastDir() {
    // Order of fallback when no preference is recorded yet (fresh install,
    // never opened/saved a file in this session):
    //   1. PreferencesUser "LAST_OPEN_DIR" — set on every successful open / save
    //   2. user.dir — the JVM's working directory, i.e. where the OculiX jar
    //      was launched from (Windows: typical user expectation for first
    //      file chooser)
    //   3. user.home — last-resort cross-platform anchor
    // Returning "" leaves it to JFileChooser's default which on Windows is
    // %USERPROFILE%\Documents and on macOS may pick an arbitrary location.
    String stored = PreferencesUser.get().get("LAST_OPEN_DIR", "");
    if (!stored.isEmpty() && new java.io.File(stored).isDirectory()) {
      return stored;
    }
    String jarDir = System.getProperty("user.dir", "");
    if (!jarDir.isEmpty() && new java.io.File(jarDir).isDirectory()) {
      return jarDir;
    }
    return System.getProperty("user.home", "");
  }

  //TODO implement according to SX.doPop
  private boolean fromPopFile = false;

  public File open() {
    File selectedFile = show("Open a file or folder", LOAD, DIRSANDFILES);
    return selectedFile;
  }

  public File open(String title) {
    //fromPopFile = true;
    File selectedFile = show(title, LOAD, DIRSANDFILES);
    return selectedFile;
  }

  public File save() {
    File selectedFile = show("Save a File or Folder", SAVE, DIRSANDFILES);
    return selectedFile;
  }

  public File saveAs(String extension, boolean isBundle) {
    File selectedFile;
    if (isBundle)
      selectedFile = show("Save as .sikuli, folder or file", SAVE, DIRSANDFILES
            , new SXFilter("as folder.sikuli", SXFilter.SIKULI)
            , new SXFilter("as file", extension)
            , new SXFilter("as plain folder", SXFilter.FOLDER)
             );
    else {
      selectedFile = show("Save as .sikuli, folder or file", SAVE, DIRSANDFILES
          , new SXFilter("as folder.sikuli", SXFilter.SIKULI)
          , new SXFilter("as file", extension)
      );
    }
    return selectedFile;
  }

  public File export() {
    String title = "Export packed as .skl or .zip";
    File ret = show(title, SAVE, FILES);
    return ret;
  }

  public File loadImage() {
    File ret = show(tr("Load Image File", "fileChooserLoadImageTitle"), LOAD, FILES,
            new FileNameExtensionFilter(
                tr("Image files (jpg, png)", "fileChooserImageFilesFilter"),
                "jpg", "jpeg", "png"));
    return ret;
  }

  /**
   * i18n lookup with safe English fallback. Reads the IDE resource bundle
   * lazily — when this class runs in pure-API headless context (no IDE
   * bundle on classpath), the fallback English text is used. When loaded
   * from inside the IDE, the user's locale-resolved bundle wins.
   */
  private static String tr(String fallback, String key) {
    try {
      ResourceBundle rb = ResourceBundle.getBundle("i18n/IDE",
          PreferencesUser.get().getLocale());
      return rb.getString(key);
    } catch (Exception e) {
      return fallback;
    }
  }

  private File show(final String title, final int mode, final int theSelectionMode, Object... filters) {
    Debug.log(3, "SikulixFileChooser: %s at %s", title.split(" ")[0], getLastDir());
    File fileChosen;
    final Object[] genericFilters = filters;
    final Object[] result = new Object[]{null, null};
    if (fromPopFile) {
      try {
        EventQueue.invokeAndWait(new Runnable() {
          @Override
          public void run() {
            processDialog(theSelectionMode, getLastDir(), title, mode, genericFilters, result);
          }
        });
      } catch (Exception e) {
      }
    } else {
      processDialog(theSelectionMode, getLastDir(), title, mode, filters, result);
      if (filters.length == 0) {
        result[1] = new SXFilter("", SXFilter.GENERIC);
      }
    }
    if (null != result[0]) {
      fileChosen = (File) result[0];
      // Use the shared persistLastDir helper, NOT fileChosen.getParent()
      // directly. Difference: for a .sikuli BUNDLE pick, fileChosen is the
      // bundle directory itself — getParent() returns the bundle's PARENT,
      // i.e. one level too high. The helper checks isDirectory() and stores
      // the right path in either case (file → parent, dir → self). Without
      // this, opening a .sikuli bundle made the next dialog land one level
      // up — symptom: user opens a bundle in C:\Users\DELL\foo.sikuli and
      // the next File ▸ Open lands at C:\Users\DELL.
      persistLastDir(fileChosen);
      if (result[1] != null) {
        if (result[1].getClass().equals(SXFilter.class)) {
          SXFilter filter = (SXFilter) result[1];
          if (filter.isType(SXFilter.GENERIC)) {
            if (fileChosen.getName().equals(fileChosen.getParentFile().getName())) {
              fileChosen = fileChosen.getParentFile();
            }
          } else if (filter.isType(SXFilter.SIKULI)) {
            if (FilenameUtils.getExtension(fileChosen.getName()).equals("")) {
              fileChosen = new File(fileChosen.getAbsolutePath() + ".sikuli");
            } else if (!FilenameUtils.getExtension(fileChosen.getName()).equals("sikuli")) {
              fileChosen = new File(FilenameUtils.removeExtension(
                      fileChosen.getAbsolutePath()) + ".sikuli");
            }
          } else if (filter.isType(SXFilter.FOLDER)) {
            fileChosen = new File(FilenameUtils.removeExtension(fileChosen.getAbsolutePath()));
          } else if (filter.isType(SXFilter.FILE)) {
            fileChosen = new File(FilenameUtils.removeExtension(
                    fileChosen.getAbsolutePath()) + filter.getExtension());
          } else {
            return null;
          }
        }
      }
      return fileChosen;
    } else {
      Debug.log(-1, "SikulixFileChooser: action cancelled or did not work");
      return null;
    }
  }

  private void processDialog(int selectionMode, String last_dir, String title, int mode, Object[] filters,
                             Object[] result) {
    JFileChooser fchooser = new JFileChooser();
    File fileChoosen = null;
    FileFilter filterChoosen = null;
    if (!last_dir.isEmpty()) {
      fchooser.setCurrentDirectory(new File(last_dir));
    }
    fchooser.setSelectedFile(null);
    fchooser.setDialogTitle(title);
    String btnApprove = "Select";
    if (fromPopFile) {
      fchooser.setFileSelectionMode(DIRSANDFILES);
      fchooser.setAcceptAllFileFilterUsed(true);
    } else {
      fchooser.setFileSelectionMode(selectionMode);
      if (mode == FileDialog.SAVE) {
        fchooser.setDialogType(JFileChooser.SAVE_DIALOG);
        btnApprove = "Save";
      }
      if (filters.length == 0) {
        fchooser.setAcceptAllFileFilterUsed(true);
      } else {
        fchooser.setAcceptAllFileFilterUsed(false);
        for (Object filter : filters) {
          fchooser.setFileFilter((FileFilter) filter);
        }
      }
    }
    if (Settings.isMac()) {
      fchooser.putClientProperty("JFileChooser.packageIsTraversable", "always");
    }
    int dialogResponse = fchooser.showDialog(parentFrame, btnApprove);
    if (dialogResponse != JFileChooser.APPROVE_OPTION) {
      fileChoosen = null;
    } else {
      fileChoosen = fchooser.getSelectedFile();
    }
    result[0] = fileChoosen;
    if (filters.length > 0) {
      result[1] = fchooser.getFileFilter();
    }
  }

  class SXFilter extends FileFilter {

    public static final String SIKULI = "s";
    public static final String FOLDER = "d";
    public static final String FILE = "f";
    public static final String GENERIC = "g";

    private String type, description, extension;

    public SXFilter(String description, String type) {
      this.type = type;
      if (type != SIKULI && type != FOLDER && type != GENERIC) {
        extension = type;
        this.type = FILE;
      }
      this.description = description;
    }

    @Override
    public boolean accept(File f) {
      return true;
    }

    @Override
    public String getDescription() {
      return description;
    }

    public boolean isType(String type) {
      return this.type.equals(type);
    }

    public String getExtension() {
      return "." + extension;
    }
  }
}
