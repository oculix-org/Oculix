/*
 * Copyright (c) 2010-2021, sikuli.org, sikulix.com - MIT license
 */

package org.sikuli.support.gui;

import org.sikuli.support.Commons;
import org.sikuli.support.devices.ScreenDevice;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SXDialog extends JFrame {

  public SXDialog() {}

  private SXDialog(Object fake) {
    frame = this;
    setResizable(false);
    setUndecorated(true);
    pane = getContentPane();
    pane.setLayout(null);
    setMargin(10);
    addKeyListener(new KeyAdapter() {
      @Override
      public void keyReleased(KeyEvent e) {
        if (checkKey(e, KEYS.ESC)) {
          Commons.trace("global key listener fired - closing window without saving state");
          close();
        }
      }
    });
  }

  public SXDialog(String res) {
    this(res, ScreenDevice.primary().getCenter(), POSITION.CENTER);
  }

  public SXDialog(String res, Point where) {
    this(res, where, POSITION.TOPLEFT);
  }

  public SXDialog(String res, Point where, Object... parms) {
    this(res, where, POSITION.TOPLEFT, parms);
  }

  public SXDialog(String res, Point where, POSITION pos, Object... parms) {
    this(new Object());

    if (parms.length > 0) {
      String[] keys = new String[0];
      if (parms[0] instanceof String[]) {
        keys = (String[]) parms[0];
        parms[0] = null;
      }
      setOptions(parms, keys);
    }

    if (!res.contains(".") && !res.endsWith(".txt")) {
      res += ".txt";
    }
    Class clazz = SXDialog.class;
    if (!res.startsWith("/")) {
      if (res.startsWith("sx")) {
        if (res.startsWith("sxide")) {
          try {
            clazz = Class.forName("org.sikuli.ide.SikulixIDE");
          } catch (ClassNotFoundException e) {
          }
        }
        res = "/org/sikuli/support/gui/" + res;
      }
    } else {
      Commons.trace("not implemented: res = %s", res);
    }
    final String text = Commons.copyResourceToString(res, clazz);
    if (!text.isEmpty()) {
      valid = true;
      this.pos = pos;
      this.where = where;
      textToItems(text);
      packBoxes(pane, dialogLines);
    } else {
      Commons.debug("SXDialog: setup: empty or not found: %s", res);
    }
  }

  private Object[] orgOptions = null;

  private Map<String, Object> options = new HashMap<>();

  public Map<String, Object> setOptions(Object[] parms) {
   return setOptions(parms, new String[0]);
  }

  public Map<String, Object> setOptions(Object[] parms, String[] keys) {
    if (orgOptions == null) {
      orgOptions = parms;
      int ix = 0;
      for (Object parm : orgOptions) {
        if (parm == null) {
          continue;
        }
        String key = "parm" + ix;
        if (ix < keys.length) {
          key = keys[ix];
        }
        options.put(key, parm);
        ix++;
      }
    }
    return options;
  }

  public Map<String, Object> getOptions() {
    return options;
  }

  private boolean valid = false;

  boolean isOK() {
    return valid;
  }

  private Point where;
  private POSITION pos;

  public void where(Point where) {
    this.where = where;
  }

  public void run() {
    if (isOK()) {
      popup(runAt());
      startAutoClose();
    }
  }

  /** Where the dialog goes for the current position mode, computed from the anchor without moving it. */
  Point runAt() {
    Point at = new Point(where);
    if (pos.equals(POSITION.TOP)) {
      at.x -= finalSize.width / 2;
    } else if (pos.equals(POSITION.CENTER)) {
      at.x -= finalSize.width / 2;
      at.y -= finalSize.height / 2;
    }
    return at;
  }

  private javax.swing.Timer autoCloser = null;

  /** Arms the auto-close delay on the event thread, replacing any delay still running. */
  void startAutoClose() {
    stopAutoClose();
    if (autoClose > 0) {
      autoCloser = new javax.swing.Timer(autoClose * 1000, e -> closeCancel());
      autoCloser.setRepeats(false);
      autoCloser.start();
    }
  }

  private void stopAutoClose() {
    if (autoCloser != null) {
      autoCloser.stop();
      autoCloser = null;
    }
  }

  boolean autoCloseArmed() {
    return autoCloser != null && autoCloser.isRunning();
  }

  public void run(Point whereNow) {
    pos = POSITION.CENTER;
    where = whereNow;
    run();
  }

  public void reRun() {
    setVisible(false);
    boxStack = new ArrayList<>();
    refNames = new HashMap<>();
    pane.removeAll();
    for (BasicItem itm : dialogLines) {
      itm.reset();
    }
    packBoxes(pane, dialogLines);
    run();
  }

  //region 03 global constants
  JFrame frame;
  Container pane;

  public enum POSITION {TOP, TOPLEFT, TOPRIGHT, CENTER}

  enum STATE {ON, OFF}

  enum FEATURE {ERROR, CLOSE, BACKTOIDE, LINK, IMAGE, ICON, ACTION, OPTION, BUTTON, HTML, PREFIX, TEXT, BOX, BOXH, BOXV, ROW, COL}

  final static String FEATURE_PLAIN = "#plain";
  final static String FEATURE_TEXT = "*text";
  final static String FEATURE_BOXEND = "##boxend##";

  static String FEATURES = "";
  static int FEATURE_MAXLEN = 0;
  static int FEATURE_MINLEN = Integer.MAX_VALUE;

  static {
    for (FEATURE feat : FEATURE.values()) {
      FEATURE_MAXLEN = Math.max(FEATURE_MAXLEN, feat.toString().length());
      FEATURE_MINLEN = Math.min(FEATURE_MINLEN, feat.toString().length());
      FEATURES += (feat + ";").toLowerCase();
    }
  }

  String getFeature(String line) {
    String feature;
    int posItemSep = line.indexOf(itemSep);
    if (posItemSep >= FEATURE_MINLEN) {
      feature = line.substring(0, posItemSep);
      String lineType = feature.substring(0, 1);
      if (lineTypes.contains(lineType)) {
        feature = feature.substring(1);
      } else {
        lineType = lineTypeSame;
      }
      if (feature.indexOf("_") >= FEATURE_MINLEN) {
        refName = feature.substring(feature.indexOf("_") + 1);
        feature = feature.substring(0, feature.indexOf("_"));
      }
      if (feature.length() <= FEATURE_MAXLEN) {
        feature = feature.toLowerCase();
        if (!(";" + FEATURES).contains(";" + feature + ";")) {
          Commons.trace("possible invalid feature - taken as text: %s", line);
          return FEATURE_PLAIN;
        }
      } else {
        return FEATURE_PLAIN;
      }
      return lineType + feature;
    }
    return FEATURE_PLAIN;
  }

  static boolean isFeat(String option, FEATURE token) {
    option = option.toUpperCase();
    String tok = token.toString();
    return option.equals(tok);
  }

  /** The feature a validated line name stands for; anything unknown reads as plain text. */
  static FEATURE featureOf(String feature) {
    try {
      return FEATURE.valueOf(feature.toUpperCase());
    } catch (IllegalArgumentException e) {
      return FEATURE.TEXT;
    }
  }

  enum TEXT {TEXT, HTML}

  enum OPT {SIZE, MARGIN, PADDING, CENTER, FONT, BORDER, WAIT}

  enum ITEMTYPE {SINGLE, LEFT, RIGHT, CENTER, OUT, BOTTOM, SAME}
  //endregion

  //region 04 global handler
  String globalPrefix = "";

  public enum KEYS {ESC, ENTER, SPACE, ANY}

  static Map<KEYS, Integer> keyMap = new HashMap<>();

  static {
    keyMap.put(KEYS.ESC, KeyEvent.VK_ESCAPE);
    keyMap.put(KEYS.ENTER, KeyEvent.VK_ENTER);
    keyMap.put(KEYS.SPACE, KeyEvent.VK_SPACE);
    keyMap.put(KEYS.ANY, -1);
  }

  boolean checkKey(KeyEvent e, KEYS key) {
    final int actualKey = e.getKeyCode();
    if (key.equals(KEYS.ANY)) {
      return true;
    }
    return actualKey == keyMap.get(key);
  }

  class ItemAction implements Runnable {
    String actionName = "standard_click_action";
    Object options;
    BasicItem item;

    public ItemAction(BasicItem item, Object... options) {
      this.item = item;
      if (options.length > 0) {
        if (options[0] instanceof String) {
          actionName = (String) options[0];
        }
        if (options.length > 1) {
          this.options = options[1];
        }
      }
    }

    @Override
    public void run() {
    }
  }

  ButtonItem OK_BUTTON = null;
  ButtonItem APPLY_BUTTON = null;
  ButtonItem CANCEL_BUTTON = null;

  ItemAction standardItemAction(BasicItem item) {
    return new ItemAction(item) {
      @Override
      public void run() {
        String title = item.title();
        if (item instanceof ButtonItem) {
          switch (title) {
            case "OK":
              closeOk();
              return;
            case "APPLY":
              apply();
              return;
            case "CANCEL":
              closeCancel();
              return;
          }
        }
        Commons.trace("Button: %s: running action: %s", title, actionName);
      }
    };
  }

  void globalButtons(ButtonItem button) {
    switch (button.title()) {
      case "OK":
        OK_BUTTON = button;
        return;
      case "APPLY":
        APPLY_BUTTON = button;
        return;
      case "CANCEL":
        CANCEL_BUTTON = button;
        return;
    }


  }
  //endregion

  //region 05 global features
  public Color SXRED = new Color(0x00, 0xA8, 0x9D, 208);
  public Color SXLBLBUTTON = new Color(241, 230, 206);
  public Color SXLBLSELECTED = new Color(167, 192, 220);
  public Color BACKGROUNDCOLOR = Color.WHITE;

  public String fontName = Font.SANS_SERIF;

  Color borderColor = SXRED;
  int border = 3;

  void setBorder(int dim) {
    if (dim > -1) {
      border = dim;
    }
  }

  void setBorder(Color color) {
    borderColor = color;
  }

  void setBorder(int dim, Color color) {
    border = dim;
    borderColor = color;
  }

  private void setBorderColor(String parm) {
    final Field[] fields = Color.class.getDeclaredFields();
    for (Field field : fields) {
      if (parm.equalsIgnoreCase(field.getName())) {
        try {
          setBorder((Color) field.get(null));
        } catch (IllegalAccessException e) {
          e.printStackTrace();
        }
      }
    }
  }

  Dimension finalSize = null;

  private int maxW = (int) (1024 * 0.8);
  private int maxH = maxW / 4 * 3;

  void setDialogSize(int w, int h) {
    if (maxW > -1) {
      maxW = w;
    }
    if (maxH > -1) {
      maxH = h;
    }
  }

  Dimension getDialogSize() {
    return new Dimension(maxW, maxH);
  }

  Rectangle margin = new Rectangle(0, 0, 0, 0);

  void setMargin(int val) {
    margin = new Rectangle(val, val, val, val);
  }

  int spaceAfter = 10;

  void setSpaceAfter(int val) {
    if (val > -1) {
      spaceAfter = val;
    }
  }

  enum ALIGN {NOTSET, LEFT, CENTER, RIGHT, TOP, BOTTOM}

  ALIGN stdAlign = ALIGN.NOTSET;

  void setAlign(ALIGN type) {
    stdAlign = type;
  }

  int autoClose = -1;

  void setAutoClose(int val) {
    if (val > 0) {
      autoClose = val;
    }
  }

  public Map<String, Object> globalStore = new HashMap<>();
  //endregion

  //region 14 show/hide/destroy
  void popup() {
    popup(null);
  }

  void popup(Point where) {
    if (finalSize.width > 0) {
      pane.setBackground(BACKGROUNDCOLOR);
      ((JComponent) pane).setBorder(BorderFactory.createLineBorder(borderColor, border));
      setSize(finalSize);
      if (where != null) {
        setLocation(where);
      }
      setAlwaysOnTop(true);
      setVisible(true);
      if (APPLY_BUTTON != null) {
        APPLY_BUTTON.comp().requestFocusInWindow();
      }
    } else {
      Commons.trace("pane empty");
    }
  }

  void close() {
    stopAutoClose();
    setVisible(false);
  }

  public void closeCancel() {
    Commons.trace("ESC or Button CANCEL: closing dialog without saving anything");
    close();
  }

  void closeOk() {
    apply();
    Commons.trace("ENTER or Button OK: closing dialog");
    close();
  }

  void apply() {
    Commons.trace("SPACE or Button APPLY: saving changed state");
  }
  //endregion

  //region 10 text to items
  static String lineTypeSingle = "#";
  static String lineTypeLeft = "-";
  static String lineTypeCenter = "|";
  static String lineTypeRight = "+";
  static String lineTypeSame = "*";
  static String lineTypes = "*#+|-./";
  static String lineTypeNext = "/";
  static String lineTypeLine = "---";
  static String lineTypeRect = "###";
  static String lineTypeBreakLine = "===";
  static String itemSep = ";";

  boolean isLineItem(String line) {
    return line.startsWith(lineTypeLine);
  }

  boolean isRectItem(String line) {
    return line.startsWith(lineTypeRect);
  }

  boolean isBreakLine(String line) {
    return line.startsWith(lineTypeBreakLine);
  }

  boolean notLineItem(String line) {
    return !isLineItem(line) && !isRectItem(line) && !isBreakLine(line);
  }

  List<String> textLines = new ArrayList<>();

  ITEMTYPE lastItemType = ITEMTYPE.LEFT;
  BasicItem lastItem = null;

  String evalBoxes(String textline) {
    String checkStart = (textline + " ").substring(0, 2);
    String checkEnd = (textline).substring(textline.length() - 1);
    boolean startsWith = false;
    boolean endsWith = false;
    if (checkStart.contains("[")) {
      startsWith = true;
    }
    if (checkEnd.contains("]")) {
      endsWith = true;
    }
    if (!startsWith && !endsWith) {
      return textline;
    }
    String line = " " + textline + " ";
    String[] parts = line.split("\\[");
    int lenp = parts.length;
    if (lenp < 2 && !endsWith) {
      return textline;
    }
    if (startsWith) {
      String post;
      String col;
      String pres = "-|+";
      String pre = parts[0].strip();
      for (int n = 1; n < lenp; n++) {
        if (!pre.isEmpty()) {
          pre = pre.substring(0, 1);
          if (!pres.contains(pre)) {
            pre = " ";
          }
        }
        col = "";
        String contentLine = "";
        post = parts[n].strip();
        if (post.length() > 0 && post.substring(0, 1).equalsIgnoreCase("v")) {
          col = "v";
          contentLine = (post + " ").substring(1).strip();
        } else {
          contentLine = post;
        }
        String boxLine = pre + "box" + col + ";";
        textLines.add(boxLine);
        if (!contentLine.isEmpty()) {
          pre = evalBoxesEnd(contentLine);
        }
      }
    } else {
      evalBoxesEnd(textline);
    }
    return "";
  }

  String evalBoxesEnd(String line) {
    String pre = "";
    String[] partsEnd = (line + " ").split("\\]");
    if (partsEnd.length > 0) {
      for (int ne = 0; ne < partsEnd.length - 1; ne++) {
        textLines.add(partsEnd[ne]);
        textLines.add("#");
      }
      if (!partsEnd[partsEnd.length - 1].strip().isEmpty()) {
        pre = partsEnd[partsEnd.length - 1].strip();
      }
    }
    return pre;
  }

  String refName = "";

  void textToItems(String text) {

    lastItemType = ITEMTYPE.LEFT;

    boolean lineContinue = false;
    String finalTextLine = "";


    for (String textLine : text.split("\n")) {
      textLine = textLine.strip();
      if (textLine.isEmpty() || textLine.startsWith("//")) {
        continue;
      }
      if (!lineContinue) {
        finalTextLine = textLine;
      } else {
        finalTextLine += textLine;
      }
      if (finalTextLine.endsWith(lineTypeNext)) {
        lineContinue = true;
        finalTextLine = finalTextLine.substring(0, finalTextLine.length() - 1).strip();
        continue;
      }
      lineContinue = false;
      finalTextLine = evalBoxes(finalTextLine);
      if (finalTextLine.equals("##")) {
        break;
      }
      if (!finalTextLine.isEmpty()) {
        textLines.add(finalTextLine);
      }
    }

    if (textLines.get(0).startsWith("#global")) {
      getGlobals(textLines.get(0));
      textLines.remove(0);
    }

    for (String line : textLines) {
      if (line.isEmpty()) {
        continue;
      }
      refName = "";
      String orgLine = line;
      TEXT isText = null;
      BasicItem item = null;
      String[] options = null;
      String feature = "";
      String lineType = "-";

      //TODO Commons.trace(line);
      if (line.contains("{")) {
        line = replaceVariables(line);
      }

      if (notLineItem(line)) {
        feature = getFeature(line);
        if (isFeat(feature, FEATURE.ERROR)) {
          continue;
        }
        if (feature.equals(FEATURE_PLAIN)) {
          feature = FEATURE_TEXT;
          String first = line.substring(0, 1);
          if (lineTypes.contains(first)) {
            if (line.length() == 1) {
              line = " ";
            } else {
              line = line.substring(1);
              feature = first + feature.substring(1);
            }
          }
          if (line.startsWith("_")) {
            int ix = line.indexOf(" ");
            if (ix > -1) {
              refName = line.substring(1, ix);
              line = line.substring(ix + 1);
            }
          }
          line = feature + ";" + line;
        }

        lineType = feature.substring(0, 1);
        feature = feature.substring(1);
      }

      String title = "";
      if (lineTypes.contains(lineType)) {

        if (!notLineItem(line)) {
          Integer number = null;
          if (line.length() > 3) {
            number = getNumber(line.substring(3).strip());
          }
          if (isLineItem(line)) { // FEAT.LINE
            if (number != null) {
              item = new LineItem().stroke(number);
            } else {
              item = new LineItem();
            }
          } else if (isRectItem(line)) {
            if (number != null) {
              item = new RectItem().stroke(number);
            } else {
              item = new RectItem();
            }
          }
        } else {
          options = line.split(itemSep);

          if (options.length > 1) {
            title = options[1].strip();
          }
          String[] itemOptions = new String[0];
          if (options.length > 2) {
            itemOptions = new String[options.length - 2];
            for (int n = 2; n < options.length; n++) {
              itemOptions[n - 2] = options[n];
            }
          }

          switch (featureOf(feature)) {
            case BOX:
            case BOXV:
            case BOXH:
            case ROW:
            case COL:
              item = new BoxItem(feature, title);
              break;

            case LINK: {
              String[] parts = title.split("\\|");
              String url = parts[0].strip();
              String urlText = url;
              if (parts.length > 1) {
                urlText = parts[0].strip();
                url = parts[1].strip();
              }
              item = new LinkItem(urlText, url);
              break;
            }

            case IMAGE:
              if (title.isEmpty()) {
                break;
              }
              if (title.startsWith("__")) {
                item = new ImageItem((BufferedImage) getOptions().get(title.substring(2)));
              } else if (new File(title).exists()) {
                try {
                  item = new ImageItem(new URL("file:" + title));
                } catch (MalformedURLException e) {
                  item = null;
                }
              } else {
                item = new ImageItem(this.getClass().getResource(title));
              }
              break;

            case ICON:
              if (!title.isEmpty() && itemOptions.length > 0) {
                item = new IconItem(this.getClass().getResource(title), itemOptions);
                options = null;
              }
              break;

            case CLOSE:
              item = new TextItem(title.isEmpty() ? "CANCEL: ESC or click" : title);
              item.setActive();
              break;

            case BACKTOIDE:
              item = new TextItem(title.isEmpty() ? "CANCEL: ESC or click" : title);
              item.setBackToIDE();
              break;

            case ACTION:
              if (itemOptions.length > 0) {
                item = new ActionItem(title, itemOptions);
                item.refName(refName);
              }
              break;

            case OPTION:
              if (itemOptions.length > 0) {
                item = new OptionItem(title, itemOptions);
              }
              break;

            case BUTTON:
              if (title.isEmpty()) {
                append(new BoxItem(FEATURE.ROW.name(), "stdbuttons"), lineType);
                append(new ButtonItem("CANCEL"));
                append(new ButtonItem("APPLY"));
                append(new ButtonItem("OK"));
                append(new TextItem(FEATURE_BOXEND));
                continue;
              }
              item = new ButtonItem(title, itemOptions);
              break;

            case HTML:
              isText = TEXT.HTML;
              break;

            case TEXT:
              isText = TEXT.TEXT;
              break;

            case PREFIX:
              globalPrefix = title;
              continue;

            default:
              break;
          }
        }
        if (isText != null) {
          if (isText.equals(TEXT.HTML)) {
            item = new HtmlItem(options[1]);
          } else if (isText.equals(TEXT.TEXT)) {
            if (orgLine.equals("#")) {
              item = new TextItem(FEATURE_BOXEND);
            } else {
              item = new TextItem(options[1]);
            }
          }
        }

        if (item instanceof ImageItem && ((ImageItem) item).get() == null) {
          Commons.error("SXDialog: image not found: %s", title);
          item = null;
        }
        if (item == null) {
          Commons.error("SXDialog: line ignored: %s", orgLine);
          continue;
        }
        if (options != null && options.length > 2) {
          applyOptions(item, options);
        }

        item.refName(refName);
        append(item, lineType);
      }
    }
  }

  void append(BasicItem item) {
    append(item, "*");
  }

  void append(BasicItem item, String lineType) {
    if (item != null) {
      ITEMTYPE itemType = ITEMTYPE.SAME;
      if (lineType.equals(lineTypeLeft)) {
        itemType = ITEMTYPE.LEFT;
      } else if (lineType.equals(lineTypeRight)) {
        itemType = ITEMTYPE.RIGHT;
      } else if (lineType.equals(lineTypeCenter)) {
        itemType = ITEMTYPE.CENTER;
      } else if (lineType.equals(lineTypeSingle)) {
        if (item.isBox()) {
          itemType = ITEMTYPE.OUT;
        } else {
          itemType = ITEMTYPE.LEFT;
        }
      }
      if (itemType.equals(ITEMTYPE.SAME)) {
        if (lastItem == null || lastItem.isBoxEnd() || lastItem.isBoxBreak()) {
          itemType = ITEMTYPE.LEFT;
        } else {
          itemType = lastItemType;
        }
      }
      lastItemType = itemType;
      item.itemType(itemType);
      dialogLines.add(item);
    }
  }

  void getGlobals(String line) {
    String[] options = line.split(itemSep);
    if (!options[0].equals("#globals") || options.length == 1) {
      Commons.trace("#globals; empty or invalid: %s", line);
      return;
    }
    for (int n = 1; n < options.length; n++) {
      String[] parms = options[n].strip().split(" ");
      String option = parms[0].toLowerCase();
      int parm1 = getNumber(parms, 1);
      int parm2 = getNumber(parms, 2);
      if (isOption(option, OPT.SIZE)) {
        setDialogSize(parm1, parm2);
      } else if (isOption(option, OPT.MARGIN)) {
        setMargin(parm1);
      } else if (isOption(option, OPT.PADDING)) {
        setSpaceAfter(parm1);
      } else if (isOption(option, OPT.CENTER)) {
        setAlign(ALIGN.CENTER);
      } else if (isOption(option, OPT.FONT)) {
        setFontSize(parm1);
      } else if (isOption(option, OPT.WAIT)) {
        setAutoClose(parm1);
      } else if (isOption(option, OPT.BORDER)) {
        for (int pn = 1; pn < parms.length; pn++) {
          String parm = parms[pn];
          Integer num = getNumber(parm);
          if (num == null) {
            setBorderColor(parm);
          } else {
            setBorder(num);
          }
        }
      }
    }
  }

  /**
   * Expands every {name} on the line. A value that brings braces of its own is expanded
   * in turn, up to ten times; a cycle or the limit leaves the line as written and says so.
   */
  String replaceVariables(String text) {
    final String original = text;
    final List<String> seen = new ArrayList<>();
    for (int pass = 0; pass < 10; pass++) {
      int[] replaced = new int[1];
      String expanded = expandOnce(text, replaced);
      if (replaced[0] == 0 || !expanded.contains("{")) {
        return expanded;
      }
      if (expanded.equals(text) || seen.contains(expanded)) {
        Commons.error("SXDialog: variable cycle: %s", original);
        return original;
      }
      seen.add(expanded);
      text = expanded;
    }
    Commons.error("SXDialog: variable expansion limit (10) reached: %s", original);
    return original;
  }

  /** One left-to-right pass over the {name} pairs; a brace without a partner stays as it is. */
  private String expandOnce(String text, int[] replaced) {
    StringBuilder out = new StringBuilder();
    int from = 0;
    while (true) {
      int start = text.indexOf("{", from);
      if (start < 0) {
        break;
      }
      int end = text.indexOf("}", start);
      if (end < 0) {
        break;
      }
      int inner = text.indexOf("{", start + 1);
      if (inner > -1 && inner < end) {
        out.append(text, from, inner);
        from = inner;
        continue;
      }
      out.append(text, from, start).append(getVariable(text.substring(start + 1, end)));
      replaced[0]++;
      from = end + 1;
    }
    out.append(text.substring(from));
    return out.toString();
  }

  String getVariable(String var) {
    if (var.equals("sxversion")) {
      return Commons.getSXVersion();
    }
    if (var.equals("javaversion")) {
      return "" + Commons.getJavaVersion();
    }
    final int isSpecial = var.indexOf("_");
    String special = "";
    if (isSpecial != -1) {
      special = var.substring(0, isSpecial);
      var = var.substring(isSpecial + 1);
    }
    Object value = options.get(var) == null ? "" : options.get(var);
    if (special.equals("sximagefile")) {
      final File imgFile = (File) value;
      return imgFile.getParentFile().getName() + "  /  " + imgFile.getName();
    } else if (value instanceof BufferedImage) {
      return "__" + var;
    }
    return value.toString();
  }

  boolean isOption(String option, OPT token) {
    return option.substring(0, 1).equals(token.toString().substring(0, 1).toLowerCase());
  }

  Integer getNumber(String text) {
    try {
      return Integer.parseInt(text);
    } catch (NumberFormatException e) {
      return null;
    }
  }

  Integer getNumber(String[] parms, int ix) {
    if (ix < parms.length) {
      Integer num = getNumber(parms[ix]);
      if (num != null) {
        return num;
      }
    }
    return -1;
  }

  void applyOptions(BasicItem item, String[] options) {
    if (item != null && options.length > 2) {
      for (int n = 2; n < options.length; n++) {
        String[] parms = options[n].strip().split(" ");
        String option = parms[0].strip().toLowerCase();
        int parm1 = getNumber(parms, 1);
        int parm2 = getNumber(parms, 2);
        if (option.startsWith("r") && parms.length > 1) {
          item.resize(parm1, parm2);
        } else if (option.startsWith("f")) {
          item.fontSize(parm1);
        } else if (option.startsWith("b")) {
          item.bold();
        } else if (option.startsWith("c")) {
          item.align(ALIGN.CENTER);
        }
      }
    }
  }
  //endregion

  //region 12 top-down pack items
  List<BasicItem> dialogLines = new ArrayList<>();

  BoxItem paneBox;
  BoxItem currentBox;
  List<BoxItem> boxStack = new ArrayList<>();
  BasicItem item;
  Map<String, BasicItem> refNames = new HashMap<>();

  String cb() {
    return currentBox.toStringAll();
  }

  String pb() {
    return paneBox.toStringAll();
  }

  String it() {
    return item.toString();
  }

  void packBoxes(Container pane, List<BasicItem> allBoxesAndItems) {

    for (BasicItem itm : allBoxesAndItems) {
      Commons.trace("%s", itm);
      if (!itm.refName().isEmpty()) {
        refNames.put(itm.refName, itm);
      }
    }

    paneBox = new BoxItem("boxv", "sxpanebox");
    paneBox.asPane();
    currentBox = paneBox;

    for (BasicItem itm : allBoxesAndItems) {
      item = itm;
      if (itm.isBoxEnd()) {
        if (boxStack.size() > 0) {
          currentBox.adjust();
          currentBox = boxStack.remove(boxStack.size() - 1);
          currentBox.adjustSize();
        }
        continue;
      }

      if (itm instanceof RectItem) {
        itm.create(currentBox);
      } else {
        itm.create();
      }

      if (itm.isOut()) {
        currentBox.adjustSize();
      }
      currentBox.add(itm);
      if (itm.isBox()) {
        boxStack.add(currentBox);
        currentBox = (BoxItem) itm;
        continue;
      }
    }

    paneBox.adjust();
    paneBox.adjustSize();
    //finalSize = paneBox.makeReady();
    finalSize = paneBox.dim();
    if (margin.x > 0 || margin.y > 0) {
      for (BasicItem itm : allBoxesAndItems) {
        itm.addMargin(margin.getLocation());
      }
    }
    if (Commons.isTrace()) {
      Commons.trace("%s", paneBox.toStringAll());
    }
    finalSize.width += margin.x + margin.width;
    finalSize.height += margin.y + margin.height;

    for (BasicItem item : allBoxesAndItems) {
      if (item.hasComp()) {
        pane.add(item.finalComp());
        item.addListeners();
      }
    }
    pane.setSize(finalSize);
  }
  //endregion

  public BasicItem getItem(String refName) {
    return refNames.get(refName);
  }

  /** Greys out, or restores, every icon line that runs this command; takes effect at the next reRun(). */
  public void enable(String command, boolean on) {
    for (BasicItem itm : dialogLines) {
      if (itm instanceof IconItem && ((IconItem) itm).command.equals(command)) {
        ((IconItem) itm).enable(on);
      }
    }
  }

  /** Removes every action or icon line that runs this command; takes effect at the next reRun(). */
  public void drop(String command) {
    dialogLines.removeIf(itm ->
        (itm instanceof IconItem && ((IconItem) itm).command.equals(command))
            || (itm instanceof ActionItem && command.equals(((ActionItem) itm).command)));
  }

  public void setText(String refName, String text) {
    final BasicItem item = refNames.get(refName);
    if (item != null && item.isValid()) {
      item.setText(text);
    }
  }

  public String getText(String refName) {
    final BasicItem item = refNames.get(refName);
    if (item != null && item.isValid()) {
      return item.title();
    }
    return null;
  }

  public void toggleText(String feature, String opt1, String opt2) {
    final String current = getText(feature);
    if (current != null) {
      if (current.equals(opt1)) setText(feature, opt2);
      else setText(feature, opt1);
    }
  }

  //region 50 BasicItem
  abstract class BasicItem {

    BasicItem() {
      alignment = stdAlign;
    }

    public String toString() {
      String clazz = this.getClass().getSimpleName();
      String title = title();
      title = title.length() > 20 ? title.substring(0, 20) + "..." : title;
      String str3 = itemType().toString().substring(0, 1);
      if (this instanceof BoxItem) {
        str3 += ((BoxItem) this).col ? "V" : "";
      }
      return String.format("%s[\"%s\" %s [%d,%d %dx%d]]", clazz, title, str3,
          x, y, width, height);
    }

    ITEMTYPE itemType = ITEMTYPE.LEFT;

    public ITEMTYPE itemType() {
      return itemType;
    }

    public void itemType(ITEMTYPE itemtype) {
      this.itemType = itemtype;
    }

    String refName = "";

    public void refName(String refName) {
      this.refName = refName;
    }

    public String refName() {
      return refName;
    }

    public boolean isBox() {
      return this instanceof BoxItem;
    }

    public boolean isBoxEnd() {
      return title.equals(FEATURE_BOXEND);
    }

    public boolean isBoxBreak() { //TODO
      return false;
      //return title.equals(FEATURE_BOXBREAK);
    }

    public boolean hasComp() {
      return !isBox() && !isBoxEnd() && !isBoxBreak();
    }

    boolean isLine() {
      return this instanceof LineItem;
    }

    boolean isRect() {
      return this instanceof RectItem;
    }

    boolean inPane() {
      return ((BoxItem) parent).isPane();
    }

    public void adjust(boolean col, int boxW, int boxH) {
      int off = 0;
      if (isLine()) {
        fill(col, boxW, boxH);
        return;
      }
      if (isRect()) {
        fill(boxW, boxH);
        return;
      }
      if (col) {
        if (alignCenter()) {
          off = (boxW - width) / 2;
        } else if (alignRightBottom()) {
          off = boxW - width;
        }
        x += off;
      } else {
        if (alignCenter()) {
          off = (boxH - height) / 2;
        } else if (alignRightBottom()) {
          off = boxH - height;
        }
        y += off;
      }
    }

    public BasicItem parent() {
      return parent;
    }

    public BasicItem parent(BasicItem parent) {
      this.parent = parent;
      return this;
    }

    BasicItem parent = null;

    //region Component
    String title = this.getClass().getSimpleName();

    String title() {
      return title;
    }

    void title(String text) {
      title = text;
    }

    void setText(String text) {
      title = text;
    }

    boolean isValid() {
      return comp != null;
    }

    private JComponent comp = null;

    JComponent comp(JComponent comp) {
      this.comp = comp;
      return comp;
    }

    JComponent comp() {
      return comp;
    }

    BasicItem create() {
      return this;
    }

    BasicItem create(BoxItem box) {
      return this;
    }

    void fill(boolean direction, int w, int h) {
    }

    void fill(int w, int h) {
    }

    JComponent finalComp() {
      comp.setBounds(rect());
      return comp;
    }

    JComponent panel = null;
    //endregion

    //region Alignment
    ALIGN alignment;

    public BasicItem align(ALIGN type) {
      alignment = type;
      return this;
    }

    public boolean alignCenter() {
      if (comp == null) {
        return false;
      }
      if (itemType.equals(ITEMTYPE.CENTER)) {
        return true;
      }
      if (alignment.equals(ALIGN.NOTSET)) {
        alignment = stdAlign;
      }
      return alignment.equals(ALIGN.CENTER);
    }

    public boolean alignRightBottom() {
      if (comp == null) {
        return false;
      }
      if (itemType.equals(ITEMTYPE.RIGHT) || itemType.equals(ITEMTYPE.BOTTOM)) {
        return true;
      }
      if (alignment.equals(ALIGN.NOTSET)) {
        alignment = stdAlign;
      }
      return alignment.equals(ALIGN.RIGHT) || alignment.equals(ALIGN.BOTTOM);
    }

    boolean isOut() { //TODO
      return false;
      //return this instanceof BoxItem && itemType.equals(ITEMTYPE.OUT);
    }
    //endregion

    //region Location Size
    void reset() {
      x = 0;
      y = 0;
      width = 0;
      height = 0;
    }

    BasicItem resize(int width) {
      return this;
    }

    BasicItem resize(int width, int height) {
      if (height < 0) {
        resize(width);
      }
      return this;
    }

    int x = 0;
    int y = 0;

    void pos(int x, int y) {
      this.x = x;
      this.y = y;
    }

    void pos(Point where) {
      x = where.x;
      y = where.y;
    }

    Point pos() {
      return new Point(x, y);
    }

    void addMargin(Point margin) {
      x += margin.x;
      y += margin.y;
    }

    int width = 0;
    int height = 0;

    void setH(int val) {
      height = val;
    }

    void setW(int val) {
      width = val;
    }

    Dimension dim() {
      return new Dimension(width, height);
    }

    void dim(int width, int height) {
      this.width = width;
      this.height = height;
    }

    void dim(Dimension size) {
      width = size.width;
      height = size.height;
    }

    void rect(Rectangle bounds) {
      pos(bounds.getLocation());
      dim(bounds.getSize());
    }

    void rect(Point where, Dimension dim) {
      pos(where);
      dim(dim);
    }

    Rectangle rect() {
      return new Rectangle(x, y, width, height);
    }
    //endregion

    //region Listener
    private boolean active = false;

    boolean active() {
      return active;
    }

    BasicItem setActive() {
      active = true;
      return this;
    }

    BasicItem setBackToIDE() {
      active = true;
      backToIDE = true;
      return this;
    }

    private boolean backToIDE = false;

    ItemAction itemAction = null;

    void itemAction(ItemAction action) {
      itemAction = action;
    }

    void addListeners() {
      if (active()) {
        if (comp instanceof JLabel) {
          if (this instanceof ActionItem || this instanceof LinkItem) {
            comp.setOpaque(true);
            comp.setBackground(BACKGROUNDCOLOR);
          } else if (this instanceof TextItem) {
            comp.setOpaque(true);
            setBackground(SXLBLBUTTON);
            comp.setBackground(getBackground());
          }
        }
        comp.addMouseListener(new ItemMouseAdapter(this));
        comp.addKeyListener(new ItemKeyAdapter(this));
      }
    }

    class ItemMouseAdapter extends MouseAdapter {
      BasicItem item;

      ItemMouseAdapter(BasicItem item) {
        this.item = item;
      }

      @Override
      public void mouseClicked(MouseEvent e) {
        item.mouseClick(e);
      }

      @Override
      public void mouseEntered(MouseEvent e) {
        item.mouseEnter(e);
      }

      @Override
      public void mouseExited(MouseEvent e) {
        item.mouseExit(e);
      }
    }

    void mouseClick(MouseEvent e) {
      if (itemAction != null) {
        itemAction.run();
      } else {
        if (backToIDE) {
          runBackToIDE();
        }
        closeCancel();
      }
    }

    void mouseEnter(MouseEvent e) {
      comp().setBackground(getSelected());
    }

    void mouseExit(MouseEvent e) {
      comp().setBackground(getBackground());
    }

    class ItemKeyAdapter extends KeyAdapter {
      BasicItem item;

      ItemKeyAdapter(BasicItem item) {
        this.item = item;
      }

      @Override
      public void keyReleased(KeyEvent e) {
        keyRelease(e);
      }
    }

    void keyRelease(KeyEvent e) {
      if (checkKey(e, KEYS.ESC)) {
        closeCancel();
      } else if (checkKey(e, KEYS.ENTER)) {
        closeOk();
      } else if (checkKey(e, KEYS.SPACE)) {
        apply();
      }
    }
    //endregion

    //region Decoration
    Color background = BACKGROUNDCOLOR;

    public Color getBackground() {
      return background;
    }

    void setBackground(Color color) {
      background = color;
    }

    Color selected = SXLBLSELECTED;

    public Color getSelected() {
      return selected;
    }

    void setSelected(Color color) {
      selected = color;
    }

    boolean underline = false;

    BasicItem underline() {
      underline = true;
      return this;
    }

    BasicItem fontSize(int size) {
      if (size > -1) {
        fontSize = size;
      }
      return this;
    }

    int fontSize = stdFontSize;

    BasicItem bold() {
      fontBold = Font.BOLD;
      return this;
    }

    int fontBold = 0;
    //endregion

  }
//endregion

  //region 501 BoxItem
  class BoxItem extends BasicItem {

    RectItem rect = null;

    //region 10 adjust
    void add(BasicItem item) {
      item.parent(this);
      if (!item.isRect()) {
        evalItem(item);
        items.add(item);
      } else {
        rect = (RectItem) item;
      }
    }

    void evalItem(BasicItem item) {
      final Dimension iDim = item.dim();
      if (items.size() == 0) {
        item.pos(pos());
        dim(iDim);
        return;
      }
      final Point bPos = pos();
      final Dimension bDim = dim();
      final Dimension newDim = new Dimension();
      int x, y;
      if (col) {
        x = bPos.x;
        y = bPos.y + bDim.height + spaceAfter;
        newDim.width = Math.max(bDim.width, iDim.width);
        newDim.height = bDim.height + iDim.height + spaceAfter;
      } else {
        x = bPos.x + bDim.width + spaceAfter;
        y = bPos.y;
        newDim.width = bDim.width + iDim.width + spaceAfter;
        newDim.height = Math.max(bDim.height, iDim.height);
      }
      dim(newDim);
      if (item.isLine()) {
        item.setW(newDim.width);
      }
      item.pos(x, y);
    }

    void adjust() {
      if (items.size() == 0) {
        return;
      }
      if (rect != null) {
        items.add(rect);
      }
      final Dimension bDim = dim();
      for (BasicItem item : items) {
        if (!item.isBoxEnd() && !item.isBoxBreak()) {
          item.adjust(col, bDim.width, bDim.height);
        }
      }
    }

    void adjustSize() {
      if (items.size() == 0) {
        return;
      }
      int boxW = 0, boxH = 0;
      for (BasicItem item : items) {
        if (!item.isBoxEnd() && !item.isBoxBreak()) {
          final Point iPos = item.pos();
          final Dimension iDim = item.dim();
          if (col) {
            if (item.isOut()) {
              boxW = Math.max(boxW, iPos.x + iDim.width);
              boxH = Math.max(boxH, iDim.height + spaceAfter);
            } else {
              boxW = Math.max(boxW, iDim.width);
              boxH += iDim.height + spaceAfter;
            }
          } else {
            if (item.isOut()) {
              boxW = Math.max(boxW, iDim.width);
              boxH = Math.max(boxH, iPos.y + iDim.height + spaceAfter);
            } else {
              boxW += iDim.width + spaceAfter;
              boxH = Math.max(boxH, iDim.height);
            }
          }
        }
      }
      if (col) {
        dim(boxW, boxH - spaceAfter);
      } else {
        dim(boxW - spaceAfter, boxH);
      }
    }

    /** A box starts a rebuild empty: its children are added again by packBoxes(). */
    @Override
    void reset() {
      super.reset();
      items.clear();
      rect = null;
    }
    //endregion

    //region 20 other
    public String toStringAll() {
      StringBuilder out = new StringBuilder("\n").append(super.toString())
          .append(col ? " COL" : " ROW").append(" (").append(items.size()).append(")\n");
      String prefix = isPane() ? "" : " - ";
      for (BasicItem item : items) {
        out.append(prefix).append(item.isBox() ? ((BoxItem) item).toStringAll() : item).append("\n");
      }
      return out.toString();
    }

    BoxItem(String type, String title) {
      this.title = title;
      if (type.equals("boxv")) {
        col = true;
      }
    }

    boolean col = false;

    boolean pane = false;

    boolean isPane() {
      return pane;
    }

    void asPane() {
      pane = true;
    }

    List<BasicItem> items = new ArrayList<>();

    BasicItem get(int ix) {
      if (ix >= 0) {
        int range = items.size();
        if (ix < range) {
          return items.get(ix);
        }
      }
      return new NullItem();
    }
    //endregion
  }

  //endregion

  //region 502 NullItem
  class NullItem extends BasicItem {

  }
  //endregion

  //region 51 LineItem
  class LineItem extends BasicItem {

    LineItem() {
      title("");
    }

    LineItem stroke(int stroke) {
      this.stroke = stroke;
      return this;
    }

    LineItem color(Color color) {
      this.color = color;
      return this;
    }

    int stdStroke = 1;
    int stroke = 0;
    Color color = SXRED;

    class Line extends JComponent {

      boolean hori;

      Line(boolean col) {
        hori = col;
      }

      public void paint(Graphics g) {
        Graphics2D g2 = (Graphics2D) g;
        g2.setStroke(new BasicStroke(stroke));
        g2.setColor(color);
        if (hori) {
          g2.draw(new Line2D.Float(0, 0, width, 0));
        } else
          g2.draw(new Line2D.Float(0, 0, 0, height));
      }
    }

    void fill(boolean horizontal, int lenW, int lenH) {
      if (horizontal) {
        width = lenW;
        height = stroke;
      } else {
        width = stroke;
        height = lenH;
      }
      Line line = new Line(horizontal);
      line.setPreferredSize(dim());
      comp(line); //Line
    }

    BasicItem create() {
      if (stroke == 0) {
        stroke = stdStroke;
      }
      height = stroke;
      width = stroke;
      return this;
    }
  }
  //endregion

  //region 511 Rectangle
  class RectItem extends BasicItem {

    RectItem() {
      title("");
    }

    RectItem stroke(int stroke) {
      this.stroke = stroke;
      return this;
    }

    RectItem color(Color color) {
      this.color = color;
      return this;
    }

    int stdStroke = 1;
    int stroke = 0;
    Color color = SXRED;

    class Rect extends JComponent {

      Rectangle rect;

      Rect(Rectangle rect) {
        this.rect = rect;
      }

      public void paint(Graphics g) {
        Graphics2D g2 = (Graphics2D) g;
        g2.setStroke(new BasicStroke(stroke));
        g2.setColor(color);
        g2.draw(new Rectangle2D.Float(rect.x, rect.y, rect.width, rect.height));
      }
    }

    void fill(int w, int h) {
      if (box != null) {
        pos(box.pos());
        dim(box.dim());
        Rect rect = new Rect(new Rectangle(box.rect()));
        rect.setPreferredSize(box.dim());
        comp(rect); //Rect
      }
    }

    BoxItem box = null;

    BasicItem create(BoxItem box) {
      if (stroke == 0) {
        stroke = stdStroke;
      }
      this.box = box;
      return this;
    }
  }
  //endregion

  //region 52 TextItem
  private int stdFontSize = 14;

  void setFontSize(int val) {
    if (val > -1) {
      stdFontSize = val;
    }
  }

  class TextItem extends BasicItem {

    TextItem() {
    }

    TextItem(String aText) {
      title(aText);
    }

    BasicItem create() {
      JLabel lblText = new JLabel(title());
      Font font = new Font(fontName, fontBold, fontSize);
      Rectangle2D textLen = lblText.getFontMetrics(font).getStringBounds(title(), getGraphics());
      if (textLen.getWidth() > maxW) {
        fontSize = (int) (fontSize * maxW / textLen.getWidth());
        font = new Font(fontName, fontBold, fontSize);
        textLen = lblText.getFontMetrics(font).getStringBounds(title(), getGraphics());
      }
      if (underline) {
        lblText = new UnderlinedLabel(title(), font);
      }
      lblText.setFont(font);
      lblText.setForeground(SXRED);
      Rectangle r = new Rectangle(0, 0, (int) textLen.getWidth(), (int) textLen.getHeight());
      lblText.setBounds(r);
      dim(r.getSize());
      comp(lblText);
      return this;
    }

    class UnderlinedLabel extends JLabel {
      public UnderlinedLabel(String text, Font textFont) {
        super(text);
        setFont(textFont);
      }

      public void paint(Graphics g) {
        Rectangle r;
        super.paint(g);
        Graphics2D g2d = (Graphics2D) g;
        g2d.setStroke(new BasicStroke(1));
        r = g2d.getClipBounds();
        int height = r.height - getFontMetrics(getFont()).getDescent() + 3;
        int width = getFontMetrics(getFont()).stringWidth(getText());
        g2d.drawLine(0, height, width, height);
      }
    }

    void setText(String text) {
      super.setText(text);
      ((JLabel) comp()).setText(text);
    }
  }

  class HtmlItem extends TextItem {

    HtmlItem(String text) {
      super(text);
    }

    BasicItem create() {
      super.create();
      JLabel lblHtml = (JLabel) comp();
      lblHtml.setText("<html>" + title().strip().replace("|", "<br>"));
      dim(lblHtml.getPreferredSize());
      return this;
    }
  }
//endregion

  //region 521 Linkitem
  class LinkItem extends TextItem {
    String aLink = "https://sikulix.github.io";

    LinkItem(String text, String link) {
      title(text);
      if (!text.equals(link)) {
        aLink = link.strip();
      }
      underline();
      setActive();
    }

    void mouseClick(MouseEvent e) {
      close();
      Commons.browse(aLink);
    }
  }

//endregion

  //region 522 ActionItem
  class ActionItem extends TextItem {
    String aAction = "";
    String command = null;
    String what = "";
    String[] parms = null;

    ActionItem() {
    }

    ActionItem(String text, String[] options) {
      title(text.strip());
      aAction = options[0].strip();
      setActive();
      bold();
      setBackground(BACKGROUNDCOLOR);
      getCommand();
    }

    void getCommand() {
      if (aAction.strip().isEmpty()) {
        return;
      }
      final String[] parts = aAction.split(" ");
      command = parts[0].strip();
      if (refName.isEmpty()) {
        refName = command;
        refNames.put(refName, this);
      }
      if (command.startsWith("show")) {
        if (parts.length > 1) {
          what = parts[1].strip();
        } else {
          Commons.error("ActionItem: show: no dialog");
        }
      } else {
        parms = parts;
      }
    }

    @Override
    void mouseClick(MouseEvent e) {
      if (command != null) {
        if (command.equals("show")) {
          new SXDialog(what).run();
        } else {
          runCommand(command, aAction, e);
        }
      }
    }
  }

  /**
   * Calls the public no-arg method of this dialog named by the command, as an
   * action or icon line asks; a missing or crashing method is reported in a popup.
   */
  void runCommand(String command, String label, MouseEvent e) {
    Point where = isShowing() ? getLocationOnScreen() : e.getLocationOnScreen();
    String error = "";
    Exception exception = null;
    try {
      Method method = getClass().getMethod(command, (Class<?>[]) null);
      method.invoke(this, (Object[]) null);
    } catch (NoSuchMethodException noSuchMethodException) {
      error = "not implemented";
      exception = noSuchMethodException;
    } catch (InvocationTargetException invocationTargetException) {
      invocationTargetException.printStackTrace();
      error = "Action crashed";
      exception = invocationTargetException;
    } catch (IllegalAccessException illegalAccessException) {
      error = "Action crashed";
    }
    if (!error.isEmpty()) {
      new SXDialog("sxideerror", where, new String[]{error, label}).run(e.getLocationOnScreen());
      Commons.error("SXDialog: %s: %s (%s)", error, label, exception);
    }
  }
//endregion

  //region 523 OptionItem
  class OptionItem extends ActionItem {
    String aOption = "";
    STATE state = STATE.OFF;
    STATE startState = STATE.OFF;
    String title;

    void setStartState(JLabel lbl) {
      if (startState.equals(STATE.OFF)) {
        lbl.setText("( ) " + title);
        state = STATE.OFF;
      } else {
        lbl.setText("(X) " + title);
        state = STATE.ON;
      }
    }

    OptionItem(String text, String[] options) {
      //TODO OptionItem
//      String action = options.length > 2 ? options[2] : "";
//      STATE state = options.length > 3 ? (options[2].toLowerCase().contains("on") ? STATE.ON : STATE.OFF) : STATE.OFF;
//      this.state = state;
//      aOption = option;
      title = text;
      title("(X) " + text);
      if (state.equals(STATE.ON)) {
        startState = STATE.ON;
      }
      setActive();
      bold();
      setBackground(BACKGROUNDCOLOR);
    }

    BasicItem create() {
      super.create();
      setStartState((JLabel) comp());
      return this;
    }

    @Override
    void mouseClick(MouseEvent e) {
      if (state.equals(STATE.ON)) {
        ((JLabel) comp()).setText("( ) " + title);
        state = STATE.OFF;
      } else {
        ((JLabel) comp()).setText("(X) " + title);
        state = STATE.ON;
      }
    }
  }
//endregion

  //region 524 ButtonItem
  class ButtonItem extends BasicItem {
    ButtonItem(String text) {
      this(text, new String[0]);
    }

    ButtonItem(String text, String[] options) {
      title = text;
      if (options.length > 0)
        this.itemAction(new ItemAction(this, options[0].strip()));
      setActive();
      bold();
    }

    BasicItem create() {
      if (itemAction == null) {
        itemAction = standardItemAction(this);
      }
      JButton button = new JButton(title());
      button.setForeground(SXRED);
      dim(button.getPreferredSize());
      comp(button); //Button
      return this;
    }

    void mouseEnter(MouseEvent e) {
      JButton button = (JButton) comp();
      if (frame.isActive()) {
        button.setForeground(Color.WHITE);
        button.setSelected(true);
      }
    }

    void mouseExit(MouseEvent e) {
      JButton button = (JButton) comp();
      button.setForeground(SXRED);
      button.setSelected(false);
    }
  }

  BasicItem[] buttonItems(String text, String[] options) {
    String[] parts = null;
    parts = text.split("\\|");
    ButtonItem[] buttonItems = new ButtonItem[parts.length];
    int ix = 0;
    for (String part : parts) {
      String buttonTyp = part.strip();
      ButtonItem buttonItem = new ButtonItem(buttonTyp);
      globalButtons(buttonItem);
      buttonItems[ix] = buttonItem;
      ix++;
    }
    return buttonItems;
  }
//endregion

  //region 53 ImageItem
  /** The image fitted to the window's width, or to its height less 50 px when that is the tighter side. */
  public BufferedImage adjustTo(Rectangle where, BufferedImage img) {
    double factor = where.getWidth() / img.getWidth();
    final double whereH = where.getHeight() - 50;
    if (img.getHeight() * factor > whereH) {
      factor = whereH / img.getHeight();
    }
    return new ImageItem(img).resize((int) Math.round(img.getWidth() * factor),
        (int) Math.round(img.getHeight() * factor)).get();
  }
  class ImageItem extends BasicItem {
    BufferedImage img = null;

    ImageItem() {
      Commons.error("ImageItem: no image given");
    }

    ImageItem(URL url) {
      if (url == null) {
        return;
      }
      try {
        img = ImageIO.read(url);
      } catch (IOException e) {
        Commons.error("ImageItem: %s", url);
      }
    }

    ImageItem(BufferedImage img) {
      this.img = img;
    }

    public BufferedImage get() {
      return img;
    }

    public void set(BufferedImage img) {
      this.img = img;
    }

    //TODO Image resize
    public ImageItem resize(int width) {
      double factor = width / (double) img.getWidth();
      int height = (int) (img.getHeight() * factor);
      img = resizeImage(img, width, height);
      return this;
    }

    public ImageItem resize(int width, int height) {
      if (img == null || (width < 1 && height < 1)) {
        return this;
      }
      if (height < 0) {
        return resize(width);
      }
      if (width > 0 && height > 0) {
        img = resizeImage(img, width, height);
        return this;
      } else if (width > 0) {
        img = resizeImage(img, width, img.getHeight());
        return this;
      } else if (height > 0) {
        img = resizeImage(img, img.getWidth(), height);
        return this;
      }
      return this;
    }

    public ImageItem resize(double factor) {
      if (img == null || !(factor > 0)) {
        return this;
      }
      img = resizeImage(img, (int) (img.getWidth() * factor), (int) (img.getHeight() * factor));
      return this;
    }

    private BufferedImage resizeImage(BufferedImage img, int width, int height) {
      return Commons.resizeImage(img, width, height);
    }

    BasicItem create() {
      JLabel lblimg = new JLabel();
      if (img != null) {
        lblimg.setIcon(new ImageIcon(img));
        dim(new Dimension(img.getWidth(), img.getHeight()));
      }
      comp(lblimg); //Image
      return this;
    }

    void mouseEnter(MouseEvent e) {
      comp().setBorder(BorderFactory.createLineBorder(SXRED, 3));
    }

    void mouseExit(MouseEvent e) {
      comp().setBorder(null);
    }

  }
//endregion

  //region 531 ImageLink
  class ImageLink extends ImageItem {
    String aLink = "https://sikulix.github.io";

    ImageLink(URL url) {
      super(url);
      setActive();
    }

    ImageLink(URL url, String link) {
      this(url);
      aLink = link;
    }

    @Override
    void mouseClick(MouseEvent e) {
      close();
      Commons.browse(aLink);
    }
  }
//endregion

  //region 532 IconItem
  /**
   * An image that runs a method of the dialog when clicked, as an action line
   * does with a text. Template line: {@code #icon; /path/to/icon.png; method; tooltip}.
   */
  class IconItem extends ImageItem {
    String command;
    String tip = null;
    boolean enabled = true;

    IconItem(URL url, String[] options) {
      super(url);
      command = options[0].strip();
      if (options.length > 1) {
        tip = options[1].strip();
      }
      setActive();
    }

    /** A disabled icon is drawn faded and ignores the mouse. */
    void enable(boolean on) {
      enabled = on;
    }

    @Override
    boolean active() {
      return enabled && super.active();
    }

    BasicItem create() {
      JLabel lblimg = new JLabel();
      if (img != null) {
        lblimg.setIcon(new ImageIcon(enabled ? img : faded()));
        dim(new Dimension(img.getWidth(), img.getHeight()));
      }
      if (tip != null) {
        lblimg.setToolTipText(tip);
      }
      comp(lblimg);
      return this;
    }

    private BufferedImage faded() {
      BufferedImage out = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB);
      Graphics2D g = out.createGraphics();
      g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.3f));
      g.drawImage(img, 0, 0, null);
      g.dispose();
      return out;
    }

    @Override
    void mouseClick(MouseEvent e) {
      runCommand(command, tip == null ? command : tip, e);
    }

    @Override
    void mouseEnter(MouseEvent e) {
      comp().setOpaque(true);
      comp().setBackground(getSelected());
    }

    @Override
    void mouseExit(MouseEvent e) {
      comp().setOpaque(false);
      comp().setBackground(getBackground());
    }
  }
//endregion

  public static final int DECISION_ACCEPT = 1;
  public static final int DECISION_IGNORE = 0;
  public static final int DECISION_CANCEL = -1;

  public static int askForDecision(JFrame where, String title, String message, String ignore, String accept) {
//TODO I18N
    final int WARNING_CANCEL = 2;
    final int WARNING_ACCEPTED = 1;
    final int WARNING_DO_NOTHING = 0;
    String[] options;
    int ret;
    options = new String[3];
    options[WARNING_DO_NOTHING] = ignore;
    options[WARNING_ACCEPTED] = accept;
    options[WARNING_CANCEL] = "Cancel";
    ret = JOptionPane.showOptionDialog(where, message, title, JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE,
        null, options, options[options.length - 1]);
    if (ret == WARNING_CANCEL || ret == JOptionPane.CLOSED_OPTION) {
      return -1;
    }
    return ret;
  }

  public void runBackToIDE() {
    // is overwritten in SXDialogIDE
  }
}
