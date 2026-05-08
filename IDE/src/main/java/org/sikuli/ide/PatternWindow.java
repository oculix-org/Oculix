/*
 * Copyright (c) 2010-2020, sikuli.org, sikulix.com - MIT license
 */
package org.sikuli.ide;

import org.apache.commons.io.FilenameUtils;
import org.sikuli.basics.Debug;
import org.sikuli.script.Location;
import org.sikuli.script.Screen;
import org.sikuli.script.ScreenImage;
import org.sikuli.support.FileManager;
import org.sikuli.support.devices.ScreenUnion;
import org.sikuli.support.ide.SikuliIDEI18N;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class PatternWindow extends JFrame {

	//<editor-fold desc="000 instance">
	private static final String me = "PatternWindow: ";
	private PatternPaneScreenshot _screenshot;
	private PatternPaneTargetOffset _tarOffsetPane;
	private JTabbedPane tabPane;
	private JPanel paneTarget, panePreview;
	private JLabel[] msgApplied;
	private int tabSequence = 0;
	private static final int tabMax = 3;
	private ScreenImage _simg;
	private ScreenImage savedScreenImage;
	private boolean previewShowsCurrentScreen = false;
	private boolean dirty;
  private EditorPane currentPane;
  Dimension pDim;

  static String _I(String key, Object... args) {
		return SikuliIDEI18N._I(key, args);
	}

	SikulixIDE sikulixide = SikulixIDE.get();
	SikulixIDE.PaneContext context;

	EditorImageButton imgBtn;
	String imgFile;
	String imgName;

	public PatternWindow(Object imgBtn) {
		this.imgBtn = (EditorImageButton) imgBtn;
		init();
	}

	private void init() {
		setTitle(_I("winPatternSettings"));
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		context = sikulixide.getActiveContext();

		currentPane = context.getPane();

		imgFile = imgBtn.getFilename();
		imgName = FilenameUtils.getName(imgFile);

		_simg = null;
		if (currentPane.hasScreenshotImage(imgName)) {
			ScreenImage screenshotImage = currentPane.getScreenshotImage(imgName);
			if (null != screenshotImage) {
				_simg = savedScreenImage = screenshotImage;
			}
		}
		if (_simg == null) {
		  _simg = takeScreenshot();
		}

		Container cp = getContentPane();
		cp.setLayout(new BorderLayout());
    GraphicsConfiguration gc = getGraphicsConfiguration();
    int pOff = 50;
    Point pLoc  = new Point(pOff, pOff);
    if (gc == null) {
      pDim = new Dimension(900, 700);
    } else {
      pDim = getGraphicsConfiguration().getBounds().getSize();
      pDim.width = (int) ((pDim.width - 2 * pOff) * 0.95);
      pDim.height = (int) ((pDim.height - 2 * pOff) * 0.95);

      // optimize window size to aspect ratio of screenshot
      double ratio = (double) _simg.getRect().getWidth() / _simg.getRect().getHeight();
      pDim.width = Math.min((int) ((pDim.height - PatternPaneScreenshot.BOTTOM_MARGIN) * ratio), pDim.width);

      // center window
      pLoc = getGraphicsConfiguration().getBounds().getLocation();
      int offsetX = (getGraphicsConfiguration().getBounds().getSize().width - pDim.width) / 2;
      int offsetY = (getGraphicsConfiguration().getBounds().getSize().height - pDim.height) / 2;
      pLoc.translate(offsetX, offsetY);
    }
    setPreferredSize(pDim);

    tabPane = new JTabbedPane();
		msgApplied = new JLabel[tabMax];

		tabSequence = 0;
		msgApplied[tabSequence] = new JLabel();
    setMessageApplied(tabSequence, false);

  	tabSequence++;
		msgApplied[tabSequence] = new JLabel();
    setMessageApplied(tabSequence, false);
		panePreview = createPreviewPanel();
		tabPane.addTab(_I("tabMatchingPreview"), panePreview);

		tabSequence++;
		msgApplied[tabSequence] = new JLabel();
    setMessageApplied(tabSequence, false);
		paneTarget = createTargetPanel();
		tabPane.addTab(_I("tabTargetOffset"), paneTarget);

		cp.add(tabPane, BorderLayout.CENTER);
		cp.add(createButtons(), BorderLayout.SOUTH);
		cp.doLayout();
		pack();
		try {
			// numMatches must be >= 0 — filterMatches() short-circuits on negative
			// values and never populates _showMatches, leaving the preview stuck on
			// its loading spinner. Use the cap to "show all matches up to MAX".
			_screenshot.setParameters(imgBtn.getFilename(), false, 0.7,
					PatternPaneScreenshot.MAX_NUM_MATCHING);
		} catch (Exception e) {
      Debug.error(me + "Problem while setting up pattern pane\n%s", e.getMessage());
		}
		// Apply the active LaF (OculiX dark/light) to the freshly-built tree.
		// PatternWindow is a transient JFrame, so it is not in the global theme
		// dispatch list — we materialise the current LaF on opening and leave
		// theme-toggle responsiveness to a follow-up cycle.
		SwingUtilities.updateComponentTreeUI(this);
		setDirty(false);
    setLocation(pLoc);
		setVisible(true);
	}

	private JComponent createButtons() {
		JPanel pane = new JPanel(new GridBagLayout());
		JButton btnOK = new JButton(_I("ok"));
		btnOK.addActionListener(new ActionOK(this));
		JButton btnApply = new JButton(_I("apply"));
		btnApply.addActionListener(new ActionApply(this));
		final JButton btnCancel = new JButton(_I("cancel"));
		btnCancel.addActionListener(new ActionCancel(this));
		GridBagConstraints c = new GridBagConstraints();
		c.gridy = 3;
		c.gridx = 0;
		c.insets = new Insets(5, 0, 10, 0);
		c.anchor = GridBagConstraints.LAST_LINE_END;
		pane.add(btnOK, c);
		c.gridx = 1;
		pane.add(btnApply, c);
		c.gridx = 2;
		pane.add(btnCancel, c);
		this.addWindowListener(new WindowAdapter() {
			public void windowClosing(WindowEvent we) {
				btnCancel.doClick();
			}
		});
		KeyStroke escapeKeyStroke =
				KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0, false);
		this.getRootPane().
				getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).
				put(escapeKeyStroke, "ESCAPE");
		this.getRootPane().getActionMap().put("ESCAPE",
				new AbstractAction() {
					public void actionPerformed(ActionEvent e) {
						btnCancel.doClick();
					}
				});
		return pane;
	}

	public void close() {
		setVisible(false);
	}
	//</editor-fold>

	//<editor-fold desc="010 preview panel">
	private static final String SAVED_SCREEN_LABEL_TEXT = "Matches on saved screen";
	private static final String SAVED_SCREEN_BUTTON_TEXT = "Switch to current screen";
	private static final String CURRENT_SCREEN_LABEL_TEXT = "Matches on current screen";
  private static final String CURRENT_SCREEN_BUTTON_TEXT = "Switch to saved screen";
  private static final String REFRESH_BUTTON_TEXT = "Refresh current screen";

	private JPanel createPreviewSwitchScreenPanel() {
	  JPanel p = new JPanel();
    JLabel switchLabel = new JLabel(SAVED_SCREEN_LABEL_TEXT);
    JButton switchButton = new JButton(SAVED_SCREEN_BUTTON_TEXT);
    JButton refreshCurrentScreenButton = new JButton(REFRESH_BUTTON_TEXT);
    refreshCurrentScreenButton.setVisible(false);
    refreshCurrentScreenButton.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        _screenshot.setScreenImage(takeScreenshot(), pDim);
        _screenshot.reloadImage();
      }
    });

    p.add(switchLabel);
    p.add(switchButton);
    p.add(refreshCurrentScreenButton);

    switchButton.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        if (previewShowsCurrentScreen) {
          previewShowsCurrentScreen = false;
          _screenshot.setScreenImage(_simg, pDim);
          _screenshot.reloadImage();
          switchLabel.setText(SAVED_SCREEN_LABEL_TEXT);
          switchButton.setText(SAVED_SCREEN_BUTTON_TEXT);
          refreshCurrentScreenButton.setVisible(false);
        } else {
          previewShowsCurrentScreen = true;
          _screenshot.setScreenImage(takeScreenshot(), pDim);
          _screenshot.reloadImage();
          switchLabel.setText(CURRENT_SCREEN_LABEL_TEXT);
          switchButton.setText(CURRENT_SCREEN_BUTTON_TEXT);
          refreshCurrentScreenButton.setVisible(true);
        }
      }
    });

    return p;
	}

	private ScreenImage takeScreenshot() {
		SikulixIDE ide = SikulixIDE.get();
		ide.setVisible(false);
		this.setVisible(false);
		try {
			Thread.sleep(500);
		} catch (Exception e) {
		}
		ScreenImage img = (new ScreenUnion()).getScreen().capture();
		SikulixIDE.showAgain();
		this.setVisible(true);
		this.requestFocus();
		return img;
	}

	private JPanel createPreviewPanel() {
		JPanel p = new JPanel();
		p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
		_screenshot = new PatternPaneScreenshot(_simg, pDim, msgApplied[tabSequence]);
    createMarginBox(p, _screenshot);

    if(savedScreenImage != null) {
      p.add(createPreviewSwitchScreenPanel());
      p.add(Box.createVerticalStrut(20));
    }else {
    	//TODO add reload button even if no screenshot
		  p.add(Box.createVerticalStrut(5));
    }
		p.add(_screenshot.createControls());
//		p.add(Box.createVerticalStrut(5));
//		p.add(msgApplied[tabSequence]);
		p.doLayout();
		return p;
	}

	private void createMarginBox(Container c, Component comp) {
		c.add(Box.createVerticalStrut(10));
		Box lrMargins = Box.createHorizontalBox();
		lrMargins.add(Box.createHorizontalStrut(10));
		lrMargins.add(comp);
		lrMargins.add(Box.createHorizontalStrut(10));
		c.add(lrMargins);
		c.add(Box.createVerticalStrut(10));
	}
	//</editor-fold>

	//<editor-fold desc="020 target panel">
	private JPanel createTargetPanel() {
		JPanel p = new JPanel();
		p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
		_tarOffsetPane = new PatternPaneTargetOffset(
						_simg, imgBtn.getFilename(), new Location(0,0), pDim, msgApplied[tabSequence]);
		createMarginBox(p, _tarOffsetPane);
		p.add(Box.createVerticalStrut(5));
		p.add(_tarOffsetPane.createControls());
		p.doLayout();
		return p;
	}

	public void setTargetOffset(Location offset) {
		if (offset != null) {
			_tarOffsetPane.setTarget(offset.x, offset.y);
		}
	}
	//</editor-fold>

	//<editor-fold desc="actions OK/APPLY/CANCEL,  dirty ">
	// Renaming is handled in a dedicated SXDialog window (see
	// SXDialogPaneImage.rename), so this dialog only persists the pattern's
	// matching parameters (similarity, num matches, target offset) back to the
	// EditorImageButton. File rename / on-disk image rewrite is intentionally
	// not part of the Optimize flow anymore.
	private void actionPerformedUpdates(Window _parent) {
		boolean tempDirty = isDirty();

		addDirty(imgBtn.setParameters(
						_screenshot.isExact(), _screenshot.getSimilarity(),
						_screenshot.getNumMatches()));
		addDirty(imgBtn.setTargetOffset(_tarOffsetPane.getTargetOffset()));
		if (isDirty() || tempDirty) {
			Debug.log(3, "Preview: update: " + imgBtn.toString());
			int i = tabPane.getSelectedIndex();
			setMessageApplied(i, true);
			imgBtn.repaint();
		}
	}

	public void setMessageApplied(int i, boolean flag) {
		if (flag) {
			for (JLabel m : msgApplied) {
				m.setText("     (changed)");
			}
		} else {
			msgApplied[i].setText("     (          )");
		}
	}

	class ActionOK implements ActionListener {

		private Window _parent;

		public ActionOK(JFrame parent) {
			_parent = parent;
		}

		@Override
		public void actionPerformed(ActionEvent e) {
			actionPerformedUpdates(_parent);
			close();
			_parent.dispose();
			currentPane.setDirty(setDirty(false));
		}
	}

	class ActionApply implements ActionListener {

		private Window _parent;

		public ActionApply(Window parent) {
			_parent = parent;
		}

		@Override
		public void actionPerformed(ActionEvent e) {
			actionPerformedUpdates(_parent);
			Component sel = tabPane.getSelectedComponent();
			if (sel != null) sel.transferFocus();
		}
	}

	class ActionCancel implements ActionListener {

		private Window _parent;

		public ActionCancel(Window parent) {
			_parent = parent;
		}

		@Override
		public void actionPerformed(ActionEvent e) {
			if (isDirty()) {
				imgBtn.resetParameters();
			}
			close();
		}
	}

	protected boolean isDirty() {
		return dirty;
	}

	private boolean setDirty(boolean flag) {
		boolean xDirty = dirty;
		dirty = flag;
		return xDirty;
	}

	protected void addDirty(boolean flag) {
		dirty |= flag;
	}
	//</editor-fold>
}
