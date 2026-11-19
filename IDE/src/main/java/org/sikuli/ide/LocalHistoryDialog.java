/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.ide;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import static org.sikuli.support.ide.SikuliIDEI18N._I;

/** Lists the kept versions of one script, shows the selected one, and hands it back on Restore. */
final class LocalHistoryDialog extends JDialog {

  private final JList<LocalHistory.Version> list;
  private final JTextArea preview = new JTextArea();
  private final JButton restore = new JButton(_I("historyRestore"));

  private LocalHistoryDialog(Frame owner, String scriptName, List<LocalHistory.Version> versions,
                             Consumer<String> onRestore) {
    super(owner, _I("historyTitle", scriptName), true);
    DateTimeFormatter format = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
        .withLocale(Locale.getDefault()).withZone(ZoneId.systemDefault());

    list = new JList<>(versions.toArray(new LocalHistory.Version[0]));
    list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    list.setCellRenderer(new DefaultListCellRenderer() {
      @Override
      public Component getListCellRendererComponent(JList<?> l, Object value, int index,
                                                    boolean selected, boolean focused) {
        super.getListCellRendererComponent(l, value, index, selected, focused);
        setText(format.format(((LocalHistory.Version) value).time));
        return this;
      }
    });
    list.addListSelectionListener(e -> {
      if (!e.getValueIsAdjusting()) show(list.getSelectedValue());
    });

    preview.setEditable(false);
    preview.setFont(new Font(Font.MONOSPACED, Font.PLAIN, UIManager.getFont("defaultFont").getSize()));

    JScrollPane left = new JScrollPane(list);
    left.setPreferredSize(new Dimension(220, 0));
    JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, new JScrollPane(preview));
    split.setResizeWeight(0);

    restore.setEnabled(false);
    restore.addActionListener(e -> {
      onRestore.accept(preview.getText());
      dispose();
    });
    JButton close = new JButton(_I("extBtnClose"));
    close.addActionListener(e -> dispose());
    JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
    buttons.add(restore);
    buttons.add(close);

    JPanel content = new JPanel(new BorderLayout(0, 8));
    content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
    if (versions.isEmpty()) {
      content.add(new JLabel(_I("historyEmpty", scriptName)), BorderLayout.CENTER);
    } else {
      content.add(split, BorderLayout.CENTER);
    }
    content.add(buttons, BorderLayout.SOUTH);
    setContentPane(content);
    getRootPane().registerKeyboardAction(e -> dispose(),
        KeyStroke.getKeyStroke("ESCAPE"), JComponent.WHEN_IN_FOCUSED_WINDOW);
    setSize(820, 520);
    setLocationRelativeTo(owner);
    if (!versions.isEmpty()) list.setSelectedIndex(0);
  }

  private void show(LocalHistory.Version version) {
    if (version == null) {
      preview.setText("");
      restore.setEnabled(false);
      return;
    }
    try {
      preview.setText(new String(Files.readAllBytes(version.copy), StandardCharsets.UTF_8));
      preview.setCaretPosition(0);
      restore.setEnabled(true);
    } catch (IOException e) {
      preview.setText(e.getMessage());
      restore.setEnabled(false);
    }
  }

  static void open(Frame owner, String scriptName, List<LocalHistory.Version> versions, Consumer<String> onRestore) {
    new LocalHistoryDialog(owner, scriptName, versions, onRestore).setVisible(true);
  }
}
