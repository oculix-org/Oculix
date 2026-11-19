/*
 * Copyright (c) 2010-2020, sikuli.org, sikulix.com - MIT license
 */
package org.sikuli.ide;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import com.formdev.flatlaf.util.UIScale;
import org.sikuli.ide.theme.OculixUi;
import org.sikuli.ide.ui.ConsoleToolbarLayout;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.PrintStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.*;
import javax.swing.text.*;
import javax.swing.text.ParagraphView;
import javax.swing.text.html.*;

import org.sikuli.basics.Debug;
import org.sikuli.support.runner.Runner;
import org.sikuli.support.runner.IRunner;

import static org.sikuli.support.ide.SikuliIDEI18N._I;
//
// A simple Java Console for your application (Swing version)
// Requires Java 1.1.5 or higher
//
// Disclaimer the use of this source is at your own risk.
//
// Permision to use and distribute into your own applications
//
// RJHM van den Bergh , rvdb@comweb.nl


public class EditorConsolePane extends JPanel implements Runnable, ThemeAware {

  private static final String me = "EditorConsolePane: ";
  //static boolean ENABLE_IO_REDIRECT = true;

  private int NUM_PIPES;
  private JTextPane textArea;
  private JScrollPane scrollPane;
  private Thread[] reader;
  private boolean quit;
  private PipedInputStream[] pin;
  private JPopupMenu popup;
  // Raw line buffer kept alongside the htmlized scrollback so afterThemeChange
  // can re-render every existing line under the new palette. Without this the
  // scrollback would freeze in the colors it had at insertion time.
  // It also backs the live filter (rebuildView) and the "Save…" toolbar action.
  private final java.util.List<String> rawLines = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
  // Toolbar state : live filter term (lower-cased on read), autoscroll flag,
  // and a reference to the filter input so rebuildView can re-render without
  // touching the UI thread from background log threads.
  private volatile String filterTerm = "";
  private volatile boolean autoScroll = true;
  private JTextField filterField;
  private JPanel toolbar;
  private JCheckBox autoScrollCb;
  private JButton saveBtn;
  private JButton copyBtn;
  private JButton clearBtn;
  private JButton reportBtn;
  private JMenuItem clearMenuItem;
  Thread errorThrower; // just for testing (Throws an Exception at this Console)

  class PopupListener extends MouseAdapter {
    JPopupMenu popup;

    PopupListener(JPopupMenu popupMenu) {
      popup = popupMenu;
    }

    public void mousePressed(MouseEvent e) {
      maybeShowPopup(e);
    }

    public void mouseReleased(MouseEvent e) {
      maybeShowPopup(e);
    }

    private void maybeShowPopup(MouseEvent e) {
      if (e.isPopupTrigger()) {
        popup.show(e.getComponent(), e.getX(), e.getY());
      }
    }
  }

  public EditorConsolePane() {
    super();
  }

  public void init(boolean SHOULD_WRAP_LINE) {
    textArea = new JTextPane();
    HTMLEditorKit kit;
    if (SHOULD_WRAP_LINE) {
      kit = editorKitWithLineWrap();
    } else {
      kit = new HTMLEditorKit();
    }
    textArea.setEditorKit(kit);
    textArea.setTransferHandler(new JTextPaneHTMLTransferHandler());
    textArea.setEditable(false);
    textArea.setMargin(UIScale.scale(new Insets(10, 14, 12, 14)));

    setLayout(new BorderLayout());

    // ── Console toolbar : filter, autoscroll, save, copy, clear ──
    // West side  : live filter + autoscroll toggle (read-affecting actions).
    // East side  : Save / Copy / Clear (one-shot actions).
    // The filter is live and case-insensitive : every keystroke rebuilds the
    // view from rawLines keeping only lines that contain the term. Emptying
    // the filter restores the full scrollback. New log lines arriving while a
    // filter is active are also filtered before being appended.

    toolbar = new JPanel(new ConsoleToolbarLayout());
    toolbar.setBorder(BorderFactory.createEmptyBorder(UIScale.scale(8), UIScale.scale(12),
            UIScale.scale(8), UIScale.scale(12)));
    // A wrapped toolbar must report its new height to BorderLayout after a resize.
    toolbar.addComponentListener(new java.awt.event.ComponentAdapter() {
      @Override public void componentResized(java.awt.event.ComponentEvent e) {
        revalidate();
      }
    });
    filterField = new JTextField(20);
    filterField.putClientProperty("FlatLaf.styleClass", "consoleFilter");
    filterField.putClientProperty("JTextField.leadingIcon",
        new FlatSVGIcon("icons/menu/magnifier.svg", 16, 16));
    filterField.putClientProperty("JTextField.placeholderText", _I("consoleFilterPlaceholder"));
    filterField.putClientProperty("JTextField.showClearButton", true);
    filterField.setToolTipText(_I("consoleFilterTooltip"));
    filterField.getAccessibleContext().setAccessibleName(_I("consoleFilterPlaceholder"));
    filterField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
      @Override public void insertUpdate(javax.swing.event.DocumentEvent e)  { onChange(); }
      @Override public void removeUpdate(javax.swing.event.DocumentEvent e)  { onChange(); }
      @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { onChange(); }
      private void onChange() {
        filterTerm = filterField.getText() == null ? "" : filterField.getText().trim();
        rebuildView();
      }
    });
    toolbar.add(filterField);

    autoScrollCb = new JCheckBox(_I("consoleAutoScroll"), true);
    autoScrollCb.setOpaque(false);
    autoScrollCb.setToolTipText("Auto-scroll to the latest line as new logs arrive");
    autoScrollCb.addActionListener(e -> autoScroll = autoScrollCb.isSelected());
    toolbar.add(autoScrollCb);


    saveBtn = new JButton(_I("consoleSave"), new FlatSVGIcon("icons/menu/floppy.svg", 16, 16));
    saveBtn.setToolTipText("Save the entire log to a .txt file (UTF-8)");
    saveBtn.addActionListener(e -> {
      JFileChooser fc = new JFileChooser();
      fc.setSelectedFile(new java.io.File("oculix-log-" + System.currentTimeMillis() + ".txt"));
      if (fc.showSaveDialog(EditorConsolePane.this) == JFileChooser.APPROVE_OPTION) {
        try (java.io.PrintWriter pw = new java.io.PrintWriter(fc.getSelectedFile(), "UTF-8")) {
          synchronized (rawLines) {
            for (String line : rawLines) pw.println(line);
          }
        } catch (Exception ex) {
          JOptionPane.showMessageDialog(EditorConsolePane.this,
              "Save failed: " + ex.getMessage(), "OculiX log", JOptionPane.WARNING_MESSAGE);
        }
      }
    });
    saveBtn.putClientProperty("FlatLaf.styleClass", "consoleAction");
    toolbar.add(saveBtn);

    copyBtn = new JButton(_I("consoleCopyAll"), new FlatSVGIcon("icons/menu/copy.svg", 16, 16));
    copyBtn.setToolTipText("Copy entire log to clipboard (plain text)");
    copyBtn.addActionListener(e -> {
      try {
        String plain = textArea.getDocument().getText(0, textArea.getDocument().getLength());
        StringSelection sel = new StringSelection(plain);
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(sel, sel);
      } catch (BadLocationException ble) {
        StringSelection sel = new StringSelection(textArea.getText());
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(sel, sel);
      }
    });
    copyBtn.putClientProperty("FlatLaf.styleClass", "consoleAction");
    toolbar.add(copyBtn);

    clearBtn = new JButton(_I("consoleClear"), new FlatSVGIcon("icons/menu/clear.svg", 16, 16));
    clearBtn.setToolTipText("Clear log buffer and view");
    clearBtn.addActionListener(e -> {
      textArea.setText("");
      rawLines.clear();
    });
    clearBtn.putClientProperty("FlatLaf.styleClass", "consoleAction");
    toolbar.add(clearBtn);

    reportBtn = new JButton(_I("consoleReportBug"), new FlatSVGIcon("icons/menu/ladybug.svg", 16, 16));
    reportBtn.setToolTipText("Copy log to clipboard and open the OculiX bug report form on GitHub");
    reportBtn.addActionListener(e -> askBeforeReportingBug());
    reportBtn.putClientProperty("FlatLaf.styleClass", "consoleAction");
    toolbar.add(reportBtn);

    add(toolbar, BorderLayout.NORTH);

    scrollPane = new JScrollPane(textArea);
    scrollPane.setBorder(BorderFactory.createEmptyBorder());
    add(scrollPane, BorderLayout.CENTER);
    applyThemeColors();

    //Create the popup menu.
    popup = new JPopupMenu();
    clearMenuItem = new JMenuItem(_I("editorConsoleCtxClear"));
    // Add ActionListener that clears the textArea
    clearMenuItem.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        clear();
      }
    });
    popup.add(clearMenuItem);

    //Add listener to components that can bring up popup menus.
    MouseListener popupListener = new PopupListener(popup);
    textArea.addMouseListener(popupListener);
  }

  /**
   * Re-reads every toolbar and context-menu label from the active locale.
   * Called by the IDE after a live language change, like the tray.
   */
  public void relabel() {
    if (toolbar == null) {
      return;
    }
    filterField.putClientProperty("JTextField.placeholderText", _I("consoleFilterPlaceholder"));
    filterField.setToolTipText(_I("consoleFilterTooltip"));
    filterField.getAccessibleContext().setAccessibleName(_I("consoleFilterPlaceholder"));
    autoScrollCb.setText(_I("consoleAutoScroll"));
    saveBtn.setText(_I("consoleSave"));
    copyBtn.setText(_I("consoleCopyAll"));
    clearBtn.setText(_I("consoleClear"));
    reportBtn.setText(_I("consoleReportBug"));
    clearMenuItem.setText(_I("editorConsoleCtxClear"));
    toolbar.revalidate();
    toolbar.repaint();
  }

  private static final String BUG_REPORT_URL = "https://github.com/oculix-org/Oculix/issues/new?template=bug_report.yml";

  /**
   * Explains what "Report bug…" is about to do and waits for an explicit OK.
   * Cancel, ESC and a click anywhere outside the dialog leave the IDE exactly
   * as it was: nothing copied, no browser opened.
   */
  private void askBeforeReportingBug() {
    java.awt.Image geckoRaw = new ImageIcon(getClass().getResource("/icons/gecko_cyclope.png")).getImage();
    ImageIcon gecko = new ImageIcon(geckoRaw.getScaledInstance(48, 48, java.awt.Image.SCALE_SMOOTH));
    JOptionPane pane = new JOptionPane(
        "Your log will be copied to the clipboard and the OculiX bug report form\n"
            + "will open in your browser.\n\n"
            + "Paste the log into the \"Logs / console output\" field,\n"
            + "fill in the other sections, then submit.\n\n"
            + "Thanks for helping us improve OculiX.",
        JOptionPane.INFORMATION_MESSAGE, JOptionPane.OK_CANCEL_OPTION, gecko);
    JDialog dialog = pane.createDialog(this, "Report a bug");
    dialog.setModal(false);
    dialog.addWindowFocusListener(new WindowAdapter() {
      @Override
      public void windowLostFocus(WindowEvent w) {
        dialog.dispose();
      }
    });
    pane.addPropertyChangeListener(JOptionPane.VALUE_PROPERTY, ev -> {
      boolean ok = Integer.valueOf(JOptionPane.OK_OPTION).equals(pane.getValue());
      dialog.dispose();
      if (ok) {
        reportBug();
      }
    });
    dialog.setVisible(true);
  }

  private void reportBug() {
    String plain;
    try {
      plain = textArea.getDocument().getText(0, textArea.getDocument().getLength());
    } catch (BadLocationException ble) {
      plain = textArea.getText();
    }
    StringSelection sel = new StringSelection(plain);
    Toolkit.getDefaultToolkit().getSystemClipboard().setContents(sel, sel);
    try {
      Desktop.getDesktop().browse(java.net.URI.create(BUG_REPORT_URL));
    } catch (Exception ex) {
      JOptionPane.showMessageDialog(this,
          "Could not open the browser. Please go to:\n" + BUG_REPORT_URL,
          "Report a bug", JOptionPane.WARNING_MESSAGE);
    }
  }

  /** Theme defaults also colour existing scrollback when afterThemeChange rebuilds it. */
  private void applyThemeColors() {
    Color bg = OculixUi.color("console.background", "TextPane.background");
    if (textArea != null) {
      textArea.setBackground(bg);
      textArea.setForeground(OculixUi.color("console.foreground", "TextPane.foreground"));
      textArea.setCaretColor(OculixUi.color("editor.caretForeground", "TextPane.caretForeground"));
      textArea.setSelectionColor(OculixUi.color("editor.selectionBackground", "TextPane.selectionBackground"));
      textArea.setSelectedTextColor(OculixUi.color("editor.selectionForeground", "TextPane.selectionForeground"));
    }
    setBackground(bg);
    if (toolbar != null) toolbar.setBackground(OculixUi.color("console.toolbarBackground", "Panel.background"));
    if (scrollPane != null) scrollPane.getViewport().setBackground(bg);
  }

  /**
   * Rebuilds the textArea from rawLines, keeping only lines that contain the
   * current {@link #filterTerm} (case-insensitive). Empty filter restores the
   * full scrollback. Called on every keystroke in the filter field via a
   * {@code DocumentListener}; safe to call from the EDT only (touches Swing).
   */
  private void rebuildView() {
    if (textArea == null) return;
    String term = filterTerm.toLowerCase(java.util.Locale.ROOT);
    textArea.setText("");
    synchronized (rawLines) {
      for (String line : rawLines) {
        if (term.isEmpty() || line.toLowerCase(java.util.Locale.ROOT).contains(term)) {
          appendMsg(htmlize(line));
        }
      }
    }
  }

  @Override
  public void beforeThemeChange() {
    // No state to tear down — the swap happens entirely in afterThemeChange().
  }

  @Override
  public void afterThemeChange() {
    applyThemeColors();
    // Keep the active filter and reading position while recolouring the log.
    int position = scrollPane.getVerticalScrollBar().getValue();
    rebuildView();
    SwingUtilities.invokeLater(() -> {
      if (autoScroll) scrollPane.getVerticalScrollBar().setValue(scrollPane.getVerticalScrollBar().getMaximum());
      else scrollPane.getVerticalScrollBar().setValue(position);
    });
    revalidate();
    repaint();
  }

  private HTMLEditorKit editorKitWithLineWrap() {
    HTMLEditorKit kit = new HTMLEditorKit() {
      @Override
      public ViewFactory getViewFactory() {
        return new HTMLFactory() {
          public View create(Element e) {
            View v = super.create(e);
            if (v instanceof InlineView) {
              return new InlineView(e) {
                public int getBreakWeight(int axis, float pos, float len) {
                  return GoodBreakWeight;
                }

                public View breakView(int axis, int p0, float pos, float len) {
                  if (axis == View.X_AXIS) {
                    checkPainter();
                    int p1 = getGlyphPainter().getBoundedPosition(this, p0, pos, len);
                    if (p0 == getStartOffset() && p1 == getEndOffset()) {
                      return this;
                    }
                    return createFragment(p0, p1);
                  }
                  return this;
                }
              };
            } else if (v instanceof ParagraphView) {
              return new ParagraphView(e) {
                protected SizeRequirements calculateMinorAxisRequirements(int axis, SizeRequirements r) {
                  if (r == null) {
                    r = new SizeRequirements();
                  }
                  float pref = layoutPool.getPreferredSpan(axis);
                  float min = layoutPool.getMinimumSpan(axis);
                  // Don't include insets, Box.getXXXSpan will include them.
                  r.minimum = (int) min;
                  r.preferred = Math.max(r.minimum, (int) pref);
                  r.maximum = Integer.MAX_VALUE;
                  r.alignment = 0.5f;
                  return r;
                }
              };
            }
            return v;
          }
        };
      }
    };
    return kit;
  }

  public void initRedirect() {
    Debug.log(3, "EditorConsolePane: starting redirection to message area");
    int npipes = 2;
    NUM_PIPES = npipes; //npipes * Runner.getRunners().size() + npipes;
    pin = new PipedInputStream[NUM_PIPES];
    reader = new Thread[NUM_PIPES];
    for (int i = 0; i < NUM_PIPES; i++) {
      pin[i] = new PipedInputStream();
    }

    try {
/*
      int irunner = 1;
      for (IRunner srunner : Runner.getRunners()) {
        Debug.log(3, "EditorConsolePane: redirection for %s", srunner.getName());

        PipedOutputStream pout = new PipedOutputStream(pin[irunner * npipes]);
        PrintStream psout = new PrintStream(pout, true);

        PipedOutputStream perr = new PipedOutputStream(pin[irunner * npipes + 1]);
        PrintStream pserr = new PrintStream(perr, true);

        srunner.redirect(psout, pserr);

        quit = false; // signals the Threads that they should exit
        // Starting two seperate threads to read from the PipedInputStreams
        for (int i = irunner * npipes; i < irunner * npipes + npipes; i++) {
          reader[i] = new Thread(EditorConsolePane.this);
          reader[i].setDaemon(true);
          reader[i].start();
        }
        irunner++;
      }
*/

      // redirect System IO to IDE message area
      PipedOutputStream oout = new PipedOutputStream(pin[0]);
      PrintStream ops = new PrintStream(oout, true);
      System.setOut(ops);
      reader[0] = new Thread(EditorConsolePane.this);
      reader[0].setDaemon(true);
      reader[0].start();

      PipedOutputStream eout = new PipedOutputStream(pin[1]);
      PrintStream eps = new PrintStream(eout, true);
      System.setErr(eps);

      reader[1] = new Thread(EditorConsolePane.this);
      reader[1].setDaemon(true);
      reader[1].start();

      // Now that System.out / System.err point at the Messages pane,
      // forward the same streams into each script runner so embedded
      // interpreters (notably Jython's PythonInterpreter, which captured
      // the original System.out at construction time before this redirect
      // ran) emit `print` / `puts` into the Messages pane too (#272).
      // Passing null/null asks AbstractRunner to fall back to the current
      // System.out / System.err — i.e. the pipes installed just above.
      for (IRunner srunner : Runner.getRunners()) {
        srunner.redirect(null, null);
      }
    } catch (IOException e1) {
      Debug.log(-1, "Redirecting System IO failed", e1.getMessage());
    }
  }

  private void appendMsg(String msg) {
    HTMLDocument doc = (HTMLDocument) textArea.getDocument();
    HTMLEditorKit kit = (HTMLEditorKit) textArea.getEditorKit();
    try {
      kit.insertHTML(doc, doc.getLength(), msg, 0, 0, null);
    } catch (Exception e) {
      Debug.error(me + "Problem appending text to message area!\n%s", e.getMessage());
    }
  }

  /*
   public synchronized void windowClosed(WindowEvent evt)
   {
   quit=true;
   this.notifyAll(); // stop all threads
   try { reader.join(1000);pin.close();   } catch (Exception e){}
   try { reader2.join(1000);pin2.close(); } catch (Exception e){}
   System.exit(0);
   }

   public synchronized void windowClosing(WindowEvent evt)
   {
   frame.setVisible(false); // default behaviour of JFrame
   frame.dispose();
   }
   */
  static final String lineSep = System.getProperty("line.separator");

  public final static String CSS_Colors =
      ".normal{ color: #BBBBBB; }"
          + ".debug { color:#C0A000; }"
          + ".info  { color: #6CB6FF; }"
          + ".log   { color: #3DDBA4; }"
          + ".error { color: #FF6B6B; }";

  private String htmlize(String msg) {
    StringBuilder sb = new StringBuilder();
    Pattern patMsgCat = Pattern.compile("\\[(.+?)\\].*");
    msg = msg.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;");

    String normal = OculixUi.hex("console.foreground", "TextPane.foreground");
    String error = OculixUi.hex("console.errorForeground", "TextPane.foreground");
    String debug = OculixUi.hex("console.debugForeground", "TextPane.foreground");
    String log = OculixUi.hex("console.logForeground", "TextPane.foreground");
    String info = OculixUi.hex("console.infoForeground", "TextPane.foreground");

    String color = "color: " + normal + ";";

    for (String line : msg.split(lineSep)) {
      Matcher m = patMsgCat.matcher(line);
      if (m.matches()) {
        String logType = m.group(1).toLowerCase();
        if (logType.contains("error")) color = "color: " + error + ";";
        else if (logType.contains("debug")) color = "color: " + debug + ";";
        else if (logType.contains("log")) color = "color: " + log + ";";
        else if (logType.contains("info")) color = "color: " + info + ";";
      }
      String font = "font-family:monospace; font-size: " + UIScale.scale(13) + "pt;";
      int margin = UIScale.scale(2);
      line = String.format("<pre style=\"margin: %d; %s %s\">%s</pre>", margin, font, color, line);
      sb.append(line);
    }
    return sb.toString();
  }

  @Override
  public synchronized void run() {
    for (int i = 0; i < NUM_PIPES; i++) {
      while (Thread.currentThread() == reader[i]) {
        try {
          this.wait(100);
        } catch (InterruptedException ie) {
        }

        int availableToRead = 0;
        try {
          availableToRead = pin[i].available();
        } catch (IOException e) {
        }
        if (availableToRead != 0) {
          String input = null;
          try {
            input = this.readLine(pin[i]);
          } catch (IOException e) {
          }
          if (null != input) {
            final String finalInput = input;
            EventQueue.invokeLater(() -> {
              synchronized (textArea) {
                rawLines.add(finalInput);
                // Respect the live filter : only render the new line if it
                // matches (or no filter is active). The line still lands in
                // rawLines so it reappears when the user clears the filter.
                String term = filterTerm.toLowerCase(java.util.Locale.ROOT);
                if (term.isEmpty() || finalInput.toLowerCase(java.util.Locale.ROOT).contains(term)) {
                  appendMsg(htmlize(finalInput));
                }
                int textLen = textArea.getDocument().getLength();
                // Auto-scroll only when the toggle is on, so users can pause
                // the bottom-anchor to read a stack trace that just scrolled by.
                if (textLen > 0 && autoScroll) {
                  int textPosEnd = textLen - 1;
                  int rowStart;
                  try {
                    rowStart = Math.max(0, Utilities.getRowStart(textArea, textPosEnd));
                  } catch (Exception e) {
                    rowStart = textPosEnd;
                  }
                  textArea.setCaretPosition(rowStart);
                }
              }
            });
          }
        }
        if (quit) {
          return;
        }
      }
    }
  }

  public synchronized String readLine(PipedInputStream in) throws IOException {
    String input = "";
    do {
      int available = in.available();
      if (available == 0) {
        break;
      }
      byte b[] = new byte[available];
      in.read(b);
      input = input + new String(b, 0, b.length);
    } while (!input.endsWith("\n") && !input.endsWith("\r\n") && !quit);
    return input;
  }

  public void clear() {
    textArea.setText("");
    rawLines.clear();
  }
}

class JTextPaneHTMLTransferHandler extends TransferHandler {
  private static final String me = "EditorConsolePane: ";

  public JTextPaneHTMLTransferHandler() {
  }

  @Override
  public void exportToClipboard(JComponent comp, Clipboard clip, int action) {
    super.exportToClipboard(comp, clip, action);
  }

  @Override
  public int getSourceActions(JComponent c) {
    return COPY_OR_MOVE;
  }

  @Override
  protected Transferable createTransferable(JComponent c) {
    JTextPane aTextPane = (JTextPane) c;

    HTMLEditorKit kit = ((HTMLEditorKit) aTextPane.getEditorKit());
    StyledDocument sdoc = aTextPane.getStyledDocument();
    int sel_start = aTextPane.getSelectionStart();
    int sel_end = aTextPane.getSelectionEnd();

    int i = sel_start;
    StringBuilder output = new StringBuilder();
    while (i < sel_end) {
      Element e = sdoc.getCharacterElement(i);
      Object nameAttr = e.getAttributes().getAttribute(StyleConstants.NameAttribute);
      int start = e.getStartOffset(), end = e.getEndOffset();
      if (nameAttr == HTML.Tag.BR) {
        output.append("\n");
      } else if (nameAttr == HTML.Tag.CONTENT) {
        if (start < sel_start) {
          start = sel_start;
        }
        if (end > sel_end) {
          end = sel_end;
        }
        try {
          String str = sdoc.getText(start, end - start);
          output.append(str);
        } catch (BadLocationException ble) {
          Debug.error(me + "Copy-paste problem!\n%s", ble.getMessage());
        }
      }
      i = end;
    }
    return new StringSelection(output.toString());
  }
}
