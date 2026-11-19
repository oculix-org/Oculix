/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.ide.ui;

import net.miginfocom.swing.MigLayout;
import org.sikuli.basics.PreferencesUser;
import org.sikuli.ide.theme.OculixColors;
import org.sikuli.ide.theme.OculixFonts;
import org.sikuli.support.Commons;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.font.TextAttribute;
import java.util.HashMap;
import java.util.Map;

import static org.sikuli.support.ide.SikuliIDEI18N._I;

/**
 * Welcome panel displayed when no script is open.
 *
 * <p>Layout (top → bottom, all left-aligned in a centered column):
 * <ol>
 *   <li>Eyebrow kicker — VISUAL AUTOMATION · v3.0.3-rc4 in JetBrains Mono cyan</li>
 *   <li>Hero quote — RaiMan's original SikuliX1 description, in Fraunces italic</li>
 *   <li>Attribution line — "— RaiMan, SikuliX1" in small italic</li>
 *   <li>OculiX-adds box — what this fork brings on top (VNC, Modern Recorder,
 *       bundled natives), framed as "extension", not "replacement"</li>
 *   <li>Primary CTAs — New script (cyan) + Open script (ghost) + shortcuts</li>
 *   <li>Secondary grid — workspace + recorder + capture, mono shortcut hints</li>
 *   <li>Footer — version · license · external links</li>
 * </ol>
 *
 * <p>Background: subtle radial gradient haze (violet upper-left, cyan upper-right)
 * painted in {@link #paintComponent(Graphics)}. Cached to a BufferedImage on
 * resize so we don't repaint the gradient on every event.
 * @author Julien Mer (julienmerconsulting)
 * @author Claude (Anthropic)
 * @since 3.0.3
 */
public class WelcomeTab extends JPanel {

  private final ActionListener onNew;
  private final ActionListener onOpen;
  private final ActionListener onNewWorkspace;
  private final ActionListener onOpenWorkspace;

  private java.awt.image.BufferedImage hazeCache;
  private java.awt.image.BufferedImage geckoCache;
  private String geckoCacheLocale;
  private static final java.util.Map<String, java.awt.image.BufferedImage> geckoSources = new java.util.HashMap<>();

  public WelcomeTab(ActionListener onNew, ActionListener onOpen,
                    ActionListener onNewWorkspace, ActionListener onOpenWorkspace) {
    this.onNew = onNew;
    this.onOpen = onOpen;
    this.onNewWorkspace = onNewWorkspace;
    this.onOpenWorkspace = onOpenWorkspace;
    // The content column scrolls in the middle when the window is too short
    // for it; the footer docks at the bottom and stays visible whatever the
    // height, its links spread over the full window width.
    setLayout(new BorderLayout());
    setOpaque(true);
    // Welcome is the brand surface in both modes — always navy with cyan /
    // violet haze + white text, but a touch lighter in OculiX Light so the
    // Welcome reads as "the IDE's hero, brand-tinted" rather than "stuck in
    // dark mode". Dark mode → ink-900 (deepest). Light mode → ink-700
    // (lighter navy, still dark enough for white hero text).
    setBackground(welcomeBg());
    buildUI();
  }

  /** Pick the Welcome bg based on the active LaF — see comment in ctor. */
  private static Color welcomeBg() {
    String name = UIManager.getLookAndFeel().getName();
    boolean dark = name != null && name.toLowerCase(java.util.Locale.ROOT).contains("dark");
    return dark ? OculixColors.OX_INK_900 : OculixColors.OX_INK_700;
  }

  private void buildUI() {
    // Centered column of content with a max width for readability
    JPanel column = new JPanel(new MigLayout(
        "wrap 1, insets 28 36 20 36, gap 0", "[grow, fill, 580!]", ""));
    column.setOpaque(false);

    // ── Eyebrow ──
    // Source key has the brand prefix in CAPS already; .toUpperCase() is a
    // safety net for locales where Google Translate broke the case (some
    // ALL-CAPS prefixes return mixed case after the round-trip).
    JLabel eyebrow = new JLabel(_I("welcomeEyebrow", Commons.getSXVersionShort()).toUpperCase());
    eyebrow.setFont(applyTracking(OculixFonts.mono(11), 0.20f).deriveFont(Font.BOLD));
    eyebrow.setForeground(OculixColors.OX_CYAN_500);
    column.add(eyebrow, "gapbottom 14");

    // ── Hero quote ──
    // JTextArea with line-wrap : the box sizes the text (column's MigLayout
    // imposes the width via `growx`, JTextArea wraps inside whatever it gets).
    // No HTML width hint, no per-locale `<br>` calibration — robust for any
    // language. The brand color hard-coded so the hero stays light even on
    // the OculiX Light theme (Welcome is brand-locked navy + light text).
    JTextArea heroArea = new JTextArea(_I("welcomeHero"));
    heroArea.setLineWrap(true);
    heroArea.setWrapStyleWord(true);
    heroArea.setEditable(false);
    heroArea.setFocusable(false);
    heroArea.setOpaque(false);
    heroArea.setBorder(null);
    heroArea.setFont(OculixFonts.uiBold(36));
    heroArea.setForeground(OculixColors.OX_INK_100);
    column.add(heroArea, "growx, gapbottom 10");

    // Same pattern as Hero : JTextArea wraps to the width MigLayout grants.
    // The earlier `<div style='width:540px'>` was 32 px wider than the column
    // content area (580 − 36×2 = 508) so the last word on the first line slid
    // silently off-canvas (RaiMan diagnosed in #403 as "part of text invisible
    // at line end"). Width-hint pattern dropped entirely : the box sizes the
    // text, not the reverse.
    JTextArea bodyArea = new JTextArea(_I("welcomeBody"));
    bodyArea.setLineWrap(true);
    bodyArea.setWrapStyleWord(true);
    bodyArea.setEditable(false);
    bodyArea.setFocusable(false);
    bodyArea.setOpaque(false);
    bodyArea.setBorder(null);
    bodyArea.setFont(OculixFonts.ui(13));
    bodyArea.setForeground(OculixColors.OX_INK_200);
    column.add(bodyArea, "growx, gapbottom 4");

    JLabel attribution = new JLabel(_I("welcomeAttribution"));
    attribution.setFont(OculixFonts.ui(11).deriveFont(Font.ITALIC));
    attribution.setForeground(OculixColors.OX_INK_300);
    column.add(attribution, "gapbottom 18");

    // ── OculiX-adds box ──
    column.add(new OculixAddsBox(), "growx, gapbottom 18");

    // ── Primary CTAs ──
    // Leading glyph stays in code (visual rhythm, locale-independent).
    // Only the action label is translated.
    JPanel primaryCtas = new JPanel(new MigLayout("insets 0, gap 10", "[]10[]push"));
    primaryCtas.setOpaque(false);
    primaryCtas.add(new HeroButton("+  " + _I("welcomeBtnNewScript"), "Ctrl+N", true, onNew));
    primaryCtas.add(new HeroButton(_I("welcomeBtnOpenScript"), "Ctrl+O", false, onOpen));
    column.add(primaryCtas, "gapbottom 10");

    // ── Workspace buttons, same ghost style as "Open script" ──
    JPanel secondary = new JPanel(new MigLayout("insets 0, gap 10", "[]10[]push"));
    secondary.setOpaque(false);
    secondary.add(new HeroButton(_I("welcomeBtnNewWorkspace"), "Ctrl+Shift+N", false, onNewWorkspace));
    secondary.add(new HeroButton(_I("welcomeBtnOpenWorkspace"), "Ctrl+Shift+O", false, onOpenWorkspace));
    column.add(secondary, "gapbottom 18");

    // ── Footer ──
    // "v3.0.x" + "MIT" + "github.com/oculix-org" stay un-translated (version
    // string, license code, URL display); "Docs", "Release notes" go through _I().
    // The items are centred as a group: version + license + links read
    // horizontally in the middle of the screen.
    JPanel footer = new JPanel(new MigLayout("insets 0, gap 12, alignx center"));
    footer.setOpaque(false);
    footer.add(footerText("v" + Commons.getSXVersionShort()));
    footer.add(footerSep());
    footer.add(footerText("MIT"));
    footer.add(footerSep());
    footer.add(footerLink(_I("welcomeFooterDocs"), "https://github.com/oculix-org/Oculix/wiki"));
    footer.add(footerLink(_I("welcomeFooterReleaseNotes"), "https://github.com/oculix-org/Oculix/releases"));
    footer.add(footerLink(_I("welcomeFooterReportTranslation"), buildReportTranslationUrl()));
    footer.add(footerLink("github.com/oculix-org", "https://github.com/oculix-org/Oculix"));

    // The column sits centred, horizontally and vertically, while there is
    // room; once the window is shorter than the column, the middle scrolls.
    ScrollableContent content = new ScrollableContent(new MigLayout("fillx, wrap 1", "[center]", "push[]push"));
    content.setOpaque(false);
    content.add(column);
    JScrollPane scroll = new JScrollPane(content);
    scroll.setBorder(null);
    scroll.setOpaque(false);
    scroll.getViewport().setOpaque(false);
    scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    scroll.getVerticalScrollBar().setUnitIncrement(16);
    add(scroll, BorderLayout.CENTER);
    footer.setBorder(BorderFactory.createEmptyBorder(8, 36, 20, 36));
    add(footer, BorderLayout.SOUTH);
  }

  /**
   * A panel that fills the viewport while it is smaller than it, so its
   * content stays centred, and scrolls only once it grows past it.
   */
  private static final class ScrollableContent extends JPanel implements Scrollable {
    ScrollableContent(LayoutManager layout) {
      super(layout);
    }

    @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
    @Override public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return 16; }
    @Override public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return Math.max(16, r.height - 16); }
    @Override public boolean getScrollableTracksViewportWidth() { return true; }

    @Override
    public boolean getScrollableTracksViewportHeight() {
      Container viewport = getParent();
      return viewport != null && viewport.getHeight() > getPreferredSize().height;
    }
  }

  // ── Footer helpers ──────────────────────────────────────────────

  private static JLabel footerText(String s) {
    JLabel l = new JLabel(s);
    l.setFont(OculixFonts.mono(11));
    l.setForeground(OculixColors.OX_INK_200);
    return l;
  }

  private static JLabel footerSep() {
    JLabel l = new JLabel("·");
    l.setFont(OculixFonts.mono(11));
    l.setForeground(OculixColors.OX_INK_300);
    return l;
  }

  private static JLabel footerLink(String label, String url) {
    JLabel l = new JLabel(label);
    l.setFont(OculixFonts.mono(11));
    l.setForeground(OculixColors.OX_CYAN_300);
    l.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    l.addMouseListener(new MouseAdapter() {
      @Override public void mouseClicked(MouseEvent e) {
        try { Desktop.getDesktop().browse(java.net.URI.create(url)); } catch (Exception ignored) {}
      }
    });
    return l;
  }

  private static Font applyTracking(Font base, float tracking) {
    Map<TextAttribute, Object> attrs = new HashMap<>();
    attrs.put(TextAttribute.TRACKING, tracking);
    return base.deriveFont(attrs);
  }

  /**
   * Build a pre-filled GitHub "new issue" URL for the i18n review tracker.
   * The user's current locale tag (e.g. {@code de}, {@code zh_CN}) is
   * embedded in the title so maintainers can filter by language without
   * opening every issue.
   *
   * <p>Title + body are kept in English on purpose, even though the
   * footer link label is translated: the maintainer triages all i18n
   * issues from a single language (English), so a German reporter
   * filing in German would slow the loop. The body is a guided template
   * the reporter fills in (key + current value + suggested value +
   * context).
   */
  /** The IDE locale as a bundle tag: {@code fr}, {@code zh_CN}, {@code pt_BR}. */
  private static String currentLocaleTag() {
    java.util.Locale locale;
    try {
      locale = PreferencesUser.get().getLocale();
    } catch (Exception ex) {
      locale = java.util.Locale.ENGLISH;
    }
    String localeTag = locale.getLanguage();
    if (locale.getCountry() != null && !locale.getCountry().isEmpty()) {
      localeTag = localeTag + "_" + locale.getCountry();
    }
    return localeTag;
  }

  private static String buildReportTranslationUrl() {
    String localeTag = currentLocaleTag();
    // Open the dedicated translation_issue.yml form template (defined in
    // .github/ISSUE_TEMPLATE/) instead of a free-form issue. The form's
    // 'locale' and 'oculix-version' inputs are pre-populated via query
    // params using their YAML 'id' values, so the user only has to type
    // the actual correction(s).
    String title = "[i18n] Translation issue in " + localeTag;
    String base = "https://github.com/oculix-org/Oculix/issues/new";
    java.nio.charset.Charset utf8 = java.nio.charset.StandardCharsets.UTF_8;
    return base
        + "?template=" + java.net.URLEncoder.encode("translation_issue.yml", utf8)
        + "&title="          + java.net.URLEncoder.encode(title, utf8)
        + "&locale="         + java.net.URLEncoder.encode(localeTag, utf8)
        + "&oculix-version=" + java.net.URLEncoder.encode(Commons.getSXVersionShort(), utf8);
  }

  // ── Background haze paint ────────────────────────────────────────

  @Override
  protected void paintComponent(Graphics g) {
    super.paintComponent(g);
    int w = getWidth();
    int h = getHeight();
    if (w <= 0 || h <= 0) return;
    String localeTag = currentLocaleTag();
    if (hazeCache == null || hazeCache.getWidth() != w || hazeCache.getHeight() != h
        || !localeTag.equals(geckoCacheLocale)) {
      hazeCache = renderHaze(w, h);
      geckoCache = renderGecko(w, h, localeTag);
      geckoCacheLocale = localeTag;
    }
    g.drawImage(hazeCache, 0, 0, null);
    if (geckoCache != null) {
      g.drawImage(geckoCache, 0, 0, null);
    }
  }

  /**
   * The gecko of a locale, loaded once per JVM: {@code /icons/gecko/<locale>.png}
   * (the gecko in that language's costume), else the language alone, else the
   * plain hero gecko.
   */
  private static java.awt.image.BufferedImage geckoFor(String localeTag) {
    synchronized (geckoSources) {
      if (geckoSources.containsKey(localeTag)) {
        return geckoSources.get(localeTag);
      }
      java.awt.image.BufferedImage found = null;
      String language = localeTag.contains("_") ? localeTag.substring(0, localeTag.indexOf('_')) : localeTag;
      for (String path : new String[]{"/icons/gecko/" + localeTag + ".png", "/icons/gecko/" + language + ".png",
          "/icons/gecko_cyclope_hero.png"}) {
        try {
          java.net.URL url = WelcomeTab.class.getResource(path);
          if (url != null) {
            found = javax.imageio.ImageIO.read(url);
            if (found != null) break;
          }
        } catch (Exception ignored) {
          // next candidate
        }
      }
      geckoSources.put(localeTag, found);
      return found;
    }
  }

  /**
   * Composes the locale's gecko on the right edge of the panel at 40% alpha:
   * present without competing with the hero text. Cached at panel size and
   * locale, regenerated on resize or language change.
   */
  private java.awt.image.BufferedImage renderGecko(int w, int h, String localeTag) {
    java.awt.image.BufferedImage geckoSource = geckoFor(localeTag);
    if (geckoSource == null) return null;

    java.awt.image.BufferedImage out = new java.awt.image.BufferedImage(
        w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
    Graphics2D g2 = out.createGraphics();
    g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

    // Target ~58% of panel height, capped at 520px so it doesn't scream on
    // big screens. Positioned slightly past the centered text column so it
    // reads as part of the same composition rather than glued to the edge.
    // Gecko fixed at 360 px — independent of WelcomeTab height. The previous
    // `h * 0.58` made the gecko grow when the SplitPane gave the WelcomeTab
    // more vertical room (e.g. footer-friendly 70/30 ratios), which then
    // overlapped the text column. Fixed size keeps the mascot stable
    // regardless of the IDE's vertical split, matching the visual baseline
    // at the previous 60/40 split.
    int targetH = 360;
    double scale = (double) targetH / geckoSource.getHeight();
    int targetW = (int) (geckoSource.getWidth() * scale);
    // Centered column is 580px wide; place the gecko's center near 78% of
    // panel width so its left edge sits right after the text block.
    int x = (int) (w * 0.78) - targetW / 2;
    int y = (h - targetH) / 2;

    g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.40f));
    g2.drawImage(geckoSource, x, y, targetW, targetH, null);
    g2.dispose();
    return out;
  }

  private static java.awt.image.BufferedImage renderHaze(int w, int h) {
    java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(
        w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
    Graphics2D g2 = img.createGraphics();
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

    // Violet haze upper-left
    g2.setPaint(new RadialGradientPaint(
        new Point(w / 4, h / 4),
        Math.max(w, h) * 0.55f,
        new float[]{0f, 1f},
        new Color[]{
            OculixColors.withAlpha(OculixColors.OX_VIOLET_500, 70),
            OculixColors.withAlpha(OculixColors.OX_VIOLET_500, 0)
        }));
    g2.fillRect(0, 0, w, h);

    // Cyan haze upper-right
    g2.setPaint(new RadialGradientPaint(
        new Point((int) (w * 0.78), h / 4),
        Math.max(w, h) * 0.5f,
        new float[]{0f, 1f},
        new Color[]{
            OculixColors.withAlpha(OculixColors.OX_CYAN_500, 60),
            OculixColors.withAlpha(OculixColors.OX_CYAN_500, 0)
        }));
    g2.fillRect(0, 0, w, h);

    g2.dispose();
    return img;
  }

  // ── Inner: OculiX-adds box ──────────────────────────────────────

  private static class OculixAddsBox extends JPanel {
    OculixAddsBox() {
      super(new MigLayout("wrap 1, insets 16 18 16 18, gap 6", "[grow, fill]", ""));
      setOpaque(false);

      JLabel header = new JLabel(_I("welcomeAddsHeader").toUpperCase());
      header.setFont(applyTracking(OculixFonts.mono(10), 0.18f).deriveFont(Font.BOLD));
      header.setForeground(OculixColors.OX_INK_300);
      add(header, "gapbottom 6");

      add(bullet(_I("welcomeAddsVncTitle"),      _I("welcomeAddsVncDesc")));
      add(bullet(_I("welcomeAddsRecorderTitle"), _I("welcomeAddsRecorderDesc")));
      add(bullet(_I("welcomeAddsOcrTitle"),      _I("welcomeAddsOcrDesc")));
    }

    private static JComponent bullet(String title, String desc) {
      JPanel row = new JPanel(new MigLayout("insets 0, gap 8", "[12!][grow, fill]", ""));
      row.setOpaque(false);
      JLabel dot = new JLabel("•");
      dot.setFont(OculixFonts.uiBold(13));
      dot.setForeground(OculixColors.OX_CYAN_500);
      row.add(dot, "aligny top");
      JLabel text = new JLabel("<html><b style='color:#E6EAFB'>" + title + "</b>"
          + " <span style='color:#B9C2E8'>— " + desc + "</span></html>");
      text.setFont(OculixFonts.ui(12));
      row.add(text, "growx");
      return row;
    }

    @Override
    protected void paintComponent(Graphics g) {
      Graphics2D g2 = (Graphics2D) g.create();
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      int w = getWidth();
      int h = getHeight();
      int arc = 10;
      g2.setColor(OculixColors.withAlpha(OculixColors.OX_INK_700, 110));
      g2.fillRoundRect(0, 0, w - 1, h - 1, arc, arc);
      g2.setColor(OculixColors.withAlpha(OculixColors.OX_INK_500, 200));
      g2.setStroke(new BasicStroke(1f));
      g2.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);
      g2.dispose();
      super.paintComponent(g);
    }
  }

  // ── Inner: HeroButton (primary CTA) ─────────────────────────────

  private static class HeroButton extends JPanel {
    private final boolean primary;
    private boolean hover;

    HeroButton(String label, String shortcut, boolean primary, ActionListener action) {
      super(new MigLayout("insets 10 16 10 16, gap 12", "[]push[]"));
      this.primary = primary;
      setOpaque(false);
      setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

      JLabel l = new JLabel(label);
      l.setFont(OculixFonts.uiBold(13));
      l.setForeground(primary ? new Color(0x00131F) : OculixColors.OX_INK_100);
      add(l);

      JLabel s = new JLabel(shortcut);
      s.setFont(OculixFonts.mono(10));
      s.setForeground(primary ? OculixColors.withAlpha(new Color(0x00131F), 180) : OculixColors.OX_INK_200);
      add(s);

      addMouseListener(new MouseAdapter() {
        @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
        @Override public void mouseExited(MouseEvent e)  { hover = false; repaint(); }
        @Override public void mouseClicked(MouseEvent e) {
          if (action != null) action.actionPerformed(null);
        }
      });
    }

    @Override
    protected void paintComponent(Graphics g) {
      Graphics2D g2 = (Graphics2D) g.create();
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      int w = getWidth();
      int h = getHeight();
      int arc = h;
      if (primary) {
        g2.setColor(hover ? OculixColors.OX_CYAN_300 : OculixColors.OX_CYAN_500);
        g2.fillRoundRect(0, 0, w - 1, h - 1, arc, arc);
        // Subtle glow
        for (int i = 0; i < 3; i++) {
          g2.setColor(OculixColors.withAlpha(OculixColors.OX_CYAN_500, 40 - i * 12));
          g2.setStroke(new BasicStroke(1f + i * 0.6f));
          g2.drawRoundRect(-i, -i, w - 1 + 2 * i, h - 1 + 2 * i, arc + 2 * i, arc + 2 * i);
        }
      } else {
        g2.setColor(hover
            ? OculixColors.withAlpha(OculixColors.OX_INK_500, 80)
            : OculixColors.withAlpha(OculixColors.OX_INK_700, 60));
        g2.fillRoundRect(0, 0, w - 1, h - 1, arc, arc);
        g2.setColor(OculixColors.withAlpha(OculixColors.OX_INK_500, 200));
        g2.setStroke(new BasicStroke(1f));
        g2.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);
      }
      g2.dispose();
      super.paintComponent(g);
    }
  }

}
