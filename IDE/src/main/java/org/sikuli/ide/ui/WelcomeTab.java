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
  private static java.awt.image.BufferedImage geckoSource;

  public WelcomeTab(ActionListener onNew, ActionListener onOpen,
                    ActionListener onNewWorkspace, ActionListener onOpenWorkspace) {
    this.onNew = onNew;
    this.onOpen = onOpen;
    this.onNewWorkspace = onNewWorkspace;
    this.onOpenWorkspace = onOpenWorkspace;
    setLayout(new MigLayout("fill, wrap 1", "[center]", "push[]push"));
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

    // ── Hero quote (RaiMan's words from the SikuliX1 README) ──
    // Sans-serif bold, "Raiman style 2010" — utilitarian, no flourish.
    // The translated value embeds the <br> from the bundle (preserved
    // through translation by translate-bundles.py's HTML sentinel).
    String hero = "<html><div style='line-height:1.05'>" + _I("welcomeHero") + "</div></html>";
    JLabel heroLabel = new JLabel(hero);
    heroLabel.setFont(OculixFonts.uiBold(36));
    // Hardcoded brand color — Welcome is brand-locked, so the hero stays light
    // even when the user picks the OculiX Light theme (otherwise the Light
    // theme's UIManager.Label.foreground = dark navy = invisible on our navy bg).
    heroLabel.setForeground(OculixColors.OX_INK_100);
    column.add(heroLabel, "gapbottom 10");

    String body = "<html><div style='width:540px; line-height:1.5'>" + _I("welcomeBody") + "</div></html>";
    JLabel bodyLabel = new JLabel(body);
    bodyLabel.setFont(OculixFonts.ui(13));
    bodyLabel.setForeground(OculixColors.OX_INK_200);
    column.add(bodyLabel, "gapbottom 4");

    JLabel attribution = new JLabel(_I("welcomeAttribution"));
    attribution.setFont(OculixFonts.ui(11).deriveFont(Font.ITALIC));
    attribution.setForeground(OculixColors.OX_INK_400);
    column.add(attribution, "gapbottom 18");

    // ── OculiX-adds box ──
    column.add(new OculixAddsBox(), "growx, gapbottom 18");

    // ── Primary CTAs ──
    // Leading glyph stays in code (visual rhythm, locale-independent).
    // Only the action label is translated.
    JPanel primaryCtas = new JPanel(new MigLayout("insets 0, gap 10", "[]10[]push"));
    primaryCtas.setOpaque(false);
    primaryCtas.add(new HeroButton("+  " + _I("welcomeBtnNewScript"), "Ctrl+N", true, onNew));
    primaryCtas.add(new HeroButton("↗  " + _I("welcomeBtnOpenScript"), "Ctrl+O", false, onOpen));
    column.add(primaryCtas, "gapbottom 10");

    // ── Secondary grid ──
    JPanel secondary = new JPanel(new MigLayout("insets 0, wrap 2, gap 4 18", "[grow, fill][grow, fill]", ""));
    secondary.setOpaque(false);
    secondary.add(new SecondaryRow("⊞  " + _I("welcomeBtnNewWorkspace"), "Ctrl+Shift+N", onNewWorkspace));
    secondary.add(new SecondaryRow("↓  " + _I("welcomeBtnOpenWorkspace"), "Ctrl+Shift+O", onOpenWorkspace));
    column.add(secondary, "gapbottom 18");

    // ── Footer ──
    // "v3.0.x" + "MIT" + "github.com/oculix-org" stay un-translated (version
    // string, license code, URL display). "fork of SikuliX1", "Docs",
    // "Release notes" go through _I().
    JPanel footer = new JPanel(new MigLayout("insets 0, gap 12", "[]12[]12[]push[]12[]12[]"));
    footer.setOpaque(false);
    footer.add(footerText("v" + Commons.getSXVersionShort()));
    footer.add(footerSep());
    footer.add(footerText("MIT"));
    footer.add(footerSep());
    footer.add(footerText(_I("welcomeFooterFork")));
    footer.add(footerLink(_I("welcomeFooterDocs"), "https://github.com/oculix-org/Oculix/wiki"));
    footer.add(footerLink(_I("welcomeFooterReleaseNotes"), "https://github.com/oculix-org/Oculix/releases"));
    footer.add(footerLink(_I("welcomeFooterReportTranslation"), buildReportTranslationUrl()));
    footer.add(footerLink("github.com/oculix-org", "https://github.com/oculix-org/Oculix"));
    column.add(footer, "growx");

    add(column);
  }

  // ── Footer helpers ──────────────────────────────────────────────

  private static JLabel footerText(String s) {
    JLabel l = new JLabel(s);
    l.setFont(OculixFonts.mono(11));
    l.setForeground(OculixColors.OX_INK_400);
    return l;
  }

  private static JLabel footerSep() {
    JLabel l = new JLabel("·");
    l.setFont(OculixFonts.mono(11));
    l.setForeground(OculixColors.OX_INK_500);
    return l;
  }

  private static JLabel footerLink(String label, String url) {
    JLabel l = new JLabel(label + " ↗");
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
  private static String buildReportTranslationUrl() {
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
    String title = "[i18n] Translation issue in " + localeTag;
    String body =
        "**Locale:** `" + localeTag + "`\n" +
        "**OculiX version:** `" + Commons.getSXVersionShort() + "`\n\n" +
        "**Affected key(s):**\n" +
        "```\n<paste the key, e.g. welcomeBtnNewScript>\n```\n\n" +
        "**Current value:**\n" +
        "```\n<paste what OculiX currently shows>\n```\n\n" +
        "**Suggested value:**\n" +
        "```\n<your improved translation>\n```\n\n" +
        "**Context (optional):**\n" +
        "_e.g. label in the sidebar, button on the Welcome tab, error popup, ..._\n\n" +
        "Thanks for helping make OculiX better in your language! 🦎";
    String base = "https://github.com/oculix-org/Oculix/issues/new";
    java.nio.charset.Charset utf8 = java.nio.charset.StandardCharsets.UTF_8;
    return base
        + "?labels=" + java.net.URLEncoder.encode("i18n-Languages", utf8)
        + "&title="  + java.net.URLEncoder.encode(title, utf8)
        + "&body="   + java.net.URLEncoder.encode(body, utf8);
  }

  // ── Background haze paint ────────────────────────────────────────

  @Override
  protected void paintComponent(Graphics g) {
    super.paintComponent(g);
    int w = getWidth();
    int h = getHeight();
    if (w <= 0 || h <= 0) return;
    if (hazeCache == null || hazeCache.getWidth() != w || hazeCache.getHeight() != h) {
      hazeCache = renderHaze(w, h);
      geckoCache = renderGecko(w, h);
    }
    g.drawImage(hazeCache, 0, 0, null);
    if (geckoCache != null) {
      g.drawImage(geckoCache, 0, 0, null);
    }
  }

  /**
   * Loads the gecko mascot once per JVM, then composes it on the right edge
   * of the panel at 28% alpha — fills the empty space without competing
   * with the hero text. Cached at panel size, regenerated only on resize.
   */
  private java.awt.image.BufferedImage renderGecko(int w, int h) {
    if (geckoSource == null) {
      try {
        java.net.URL url = WelcomeTab.class.getResource("/icons/gecko_cyclope_hero.png");
        if (url != null) geckoSource = javax.imageio.ImageIO.read(url);
      } catch (Exception ignored) {
        return null;
      }
    }
    if (geckoSource == null) return null;

    java.awt.image.BufferedImage out = new java.awt.image.BufferedImage(
        w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
    Graphics2D g2 = out.createGraphics();
    g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

    // Target ~58% of panel height, capped at 520px so it doesn't scream on
    // big screens. Positioned slightly past the centered text column so it
    // reads as part of the same composition rather than glued to the edge.
    int targetH = Math.min((int) (h * 0.58), 520);
    double scale = (double) targetH / geckoSource.getHeight();
    int targetW = (int) (geckoSource.getWidth() * scale);
    // Centered column is 580px wide; place the gecko's center near 78% of
    // panel width so its left edge sits right after the text block.
    int x = (int) (w * 0.78) - targetW / 2;
    int y = (h - targetH) / 2;

    g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.28f));
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
      s.setForeground(primary ? OculixColors.withAlpha(new Color(0x00131F), 180) : OculixColors.OX_INK_400);
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

  // ── Inner: Secondary action row ─────────────────────────────────

  private static class SecondaryRow extends JPanel {
    private boolean hover;

    SecondaryRow(String label, String shortcut, ActionListener action) {
      super(new MigLayout("insets 8 12 8 12, gap 10", "[]push[]"));
      setOpaque(false);
      setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

      JLabel l = new JLabel(label);
      l.setFont(OculixFonts.ui(13));
      l.setForeground(OculixColors.OX_INK_100);
      add(l);

      JLabel s = new JLabel(shortcut);
      s.setFont(OculixFonts.mono(10));
      s.setForeground(OculixColors.OX_INK_400);
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
      if (hover) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(OculixColors.withAlpha(OculixColors.OX_INK_700, 120));
        g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
        g2.dispose();
      }
      super.paintComponent(g);
    }
  }
}
