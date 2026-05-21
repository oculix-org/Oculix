//<editor-fold desc="07 input">

/**
 * request user's input as one line of text <br>
 * with hidden = true: <br>
 * the dialog works as password input (input text hidden as bullets) <br>
 * take care to destroy the return value as soon as possible (internally the password is deleted on return)
 *
 * @param msg
 * @param preset
 * @param title
 * @param hidden
 * @return the text entered
 */
public static String input(String msg, String preset, String title, boolean hidden) {
  JFrame anchor = popLocation();
  String ret = "";
  if (!hidden) {
    if ("".equals(title)) {
      title = "Sikuli input request";
    }
    ret = (String) JOptionPane.showInputDialog(anchor, msg, title,
        JOptionPane.PLAIN_MESSAGE, null, null, preset);
  } else {
    preset = "";
    JTextArea tm = new JTextArea(msg);
    tm.setColumns(20);
    tm.setLineWrap(true);
    tm.setWrapStyleWord(true);
    tm.setEditable(false);
    tm.setBackground(new JLabel().getBackground());
    JPasswordField pw = new JPasswordField(preset);
    JPanel pnl = new JPanel();
    pnl.setLayout(new BoxLayout(pnl, BoxLayout.Y_AXIS));
    pnl.add(pw);
    pnl.add(Box.createVerticalStrut(10));
    pnl.add(tm);
    int retval = JOptionPane.showConfirmDialog(anchor, pnl, title, JOptionPane.OK_CANCEL_OPTION);
    if (0 == retval) {
      char[] pwc = pw.getPassword();
      for (int i = 0; i < pwc.length; i++) {
        ret = ret + pwc[i];
        pwc[i] = 0;
      }
    }
  }
  if (anchor != null) {
    anchor.dispose();
  }
  return ret;
}

public static String input(String msg, String title, boolean hidden) {
  return input(msg, "", title, hidden);
}

public static String input(String msg, boolean hidden) {
  return input(msg, "", "", hidden);
}

public static String input(String msg, String preset, String title) {
  return input(msg, preset, title, false);
}

public static String input(String msg, String preset) {
  return input(msg, preset, "", false);
}

public static String input(String msg) {
  return input(msg, "", "", false);
}
//</editor-fold>

//<editor-fold desc="08 inputText">
public static String inputText(String msg) {
  return inputText(msg, "", 0, 0, "");
}

public static String inputText(String msg, int lines, int width) {
  return inputText(msg, "", lines, width, "");
}

public static String inputText(String msg, int lines, int width, String text) {
  return inputText(msg, "", lines, width, text);
}

public static String inputText(String msg, String text) {
  return inputText(msg, "", 0, 0, text);
}

/**
 * Shows a dialog request to enter text in a multiline text field <br>
 * it has line wrapping on word bounds and a vertical scrollbar if needed
 *
 * @param msg   the message to display below the textfield
 * @param title the title for the dialog (default: SikuliX input request)
 * @param lines the maximum number of lines visible in the text field (default 9)
 * @param width the maximum number of characters visible in one line (default 20 letters m)
 * @param text  a preset text to show
 * @return The user's input including the line breaks.
 */
public static String inputText(String msg, String title, int lines, int width, String text) {
  width = Math.max(20, width);
  lines = Math.max(9, lines);
  if ("".equals(title)) {
    title = "SikuliX input request";
  }
  JTextArea ta = new JTextArea("");
  String fontname = "Dialog";
  int pluswidth = 1;
  if (Settings.InputFontMono) {
    fontname = "Monospaced";
    pluswidth = 3;
  }
  ta.setFont(new Font(fontname, Font.PLAIN, Math.max(14, Settings.InputFontSize)));
  int w = (width + pluswidth) * ta.getFontMetrics(ta.getFont()).charWidth('m');
  int h = (lines + 1) * ta.getFontMetrics(ta.getFont()).getHeight();
  ta.setText(text);
  ta.setLineWrap(true);
  ta.setWrapStyleWord(true);

  JScrollPane sp = new JScrollPane();
  sp.setViewportView(ta);
  sp.setPreferredSize(new Dimension(w, h));

  JTextArea tm = new JTextArea("");
  tm.setFont(new Font(fontname, Font.PLAIN, Math.max(14, Settings.InputFontSize)));
  tm.setColumns(width);
  tm.setText(msg);
  tm.setLineWrap(true);
  tm.setWrapStyleWord(true);
  tm.setEditable(false);
  tm.setBackground(new JLabel().getBackground());

  JPanel pnl = new JPanel();
  pnl.setLayout(new BoxLayout(pnl, BoxLayout.Y_AXIS));
  pnl.add(sp);
  pnl.add(Box.createVerticalStrut(10));
  pnl.add(tm);
  pnl.add(Box.createVerticalStrut(10));
  JFrame anchor = popLocation();
  int ret = JOptionPane.showConfirmDialog(anchor, pnl, title, JOptionPane.OK_CANCEL_OPTION);
  if (anchor != null) {
    anchor.dispose();
  }
  if (0 == ret) {
    return ta.getText();
  } else {
    return null;
  }
}
//</editor-fold>
