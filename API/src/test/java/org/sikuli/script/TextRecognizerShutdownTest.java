package org.sikuli.script;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sikuli.basics.Settings;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * What a script sees when it reads text and then ends: no report from Tesseract about
 * sessions left open, and no crash when the program ends while a reading is still
 * running. Every scenario runs in a separate JVM, because the report is only written
 * while the process terminates and a native crash must not take the test runner down.
 */
class TextRecognizerShutdownTest {

  static final String UNAVAILABLE = "OCR-UNAVAILABLE";
  static final String LEAK = "WARNING! LEAK!";

  @TempDir Path dir;

  @Test void noLeakAtExit() throws Exception {
    String log = runChild(OneReading.class, 60);
    assertTrue(log.contains(OneReading.DONE), () -> "the script did not read its text\n" + log);
    assertFalse(log.contains(LEAK), () -> "Tesseract reported sessions still open at exit\n" + log);
  }

  @Test void noLeakAfterThreeThreads() throws Exception {
    String log = runChild(ThreeThreads.class, 90);
    assertTrue(log.contains(ThreeThreads.DONE + " 3/3"), () -> "a thread read the wrong text\n" + log);
    assertFalse(log.contains(LEAK), () -> "Tesseract reported sessions still open at exit\n" + log);
  }

  @Test void exitDuringReading() throws Exception {
    String log = runChild(ExitDuringReading.class, 60);
    assertTrue(log.contains(ExitDuringReading.EXITING), () -> "the script never reached its exit\n" + log);
    try (Stream<Path> files = Files.list(dir)) {
      assertFalse(files.anyMatch(f -> f.getFileName().toString().startsWith("hs_err_pid")),
          () -> "the JVM crashed while ending\n" + log);
    }
  }

  /**
   * Runs {@code main} in a child JVM whose working directory is the test folder, waits
   * for it, and returns what it printed. Skips the test when Tesseract cannot load here;
   * fails when the child does not end in time or ends with a non-zero code.
   */
  private String runChild(Class<?> main, int seconds) throws Exception {
    Path output = dir.resolve(main.getSimpleName() + ".log");
    String executable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
    String java = Paths.get(System.getProperty("java.home"), "bin", executable).toString();
    String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
    Process child = new ProcessBuilder(java, "-cp", classpath, main.getName())
        .directory(dir.toFile())
        .redirectErrorStream(true)
        .redirectOutput(output.toFile())
        .start();
    int exit;
    try {
      assertTrue(child.waitFor(seconds, TimeUnit.SECONDS),
          () -> "the script still runs after " + seconds + " s\n" + read(output));
      exit = child.exitValue();
    } finally {
      child.destroyForcibly();
    }
    String log = read(output);
    assumeFalse(log.contains(UNAVAILABLE), () -> "Tesseract natives unavailable here\n" + log);
    assertEquals(0, exit, () -> "the script ended with code " + exit + "\n" + log);
    return log;
  }

  private static String read(Path file) {
    try {
      return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
    } catch (Exception e) {
      return "cannot read child output: " + e;
    }
  }

  /** An image with {@code text} in black on white, large enough for a clean reading. */
  static BufferedImage textImage(String text) {
    BufferedImage image = new BufferedImage(560, 90, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = image.createGraphics();
    try {
      g.setColor(Color.WHITE);
      g.fillRect(0, 0, image.getWidth(), image.getHeight());
      g.setColor(Color.BLACK);
      g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
      g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 36));
      g.drawString(text, 20, 58);
    } finally {
      g.dispose();
    }
    return image;
  }

  /** Reads {@code text} once; prints the unavailable marker and returns null when Tesseract cannot load. */
  static String readOrGiveUp(String text) {
    try {
      return OCR.readText(textImage(text));
    } catch (Throwable e) {
      System.out.println(UNAVAILABLE + " " + e);
      return null;
    }
  }

  /** A script that reads one text and ends. */
  public static final class OneReading {
    static final String DONE = "OCR-DONE:";

    public static void main(String[] args) {
      Settings.OcrLanguage = "eng";
      String text = readOrGiveUp("Hello OculiX 2026");
      if (text != null) {
        System.out.println(DONE + " " + text);
      }
    }
  }

  /** A script whose three threads each read their own text, then the script ends. */
  public static final class ThreeThreads {
    static final String DONE = "THREADS-DONE:";
    static final String[] TEXTS = {"Hello OculiX 2026", "Gecko Region 4100", "Pattern Match 0815"};

    public static void main(String[] args) throws Exception {
      Settings.OcrLanguage = "eng";
      String[] read = new String[TEXTS.length];
      Thread[] threads = new Thread[TEXTS.length];
      for (int i = 0; i < TEXTS.length; i++) {
        int n = i;
        threads[i] = new Thread(() -> read[n] = readOrGiveUp(TEXTS[n]), "reader-" + i);
        threads[i].start();
      }
      for (Thread thread : threads) {
        thread.join();
      }
      int right = 0;
      for (int i = 0; i < TEXTS.length; i++) {
        if (read[i] == null) {
          return;
        }
        System.out.println(threads[i].getName() + ": " + read[i]);
        if (read[i].contains(TEXTS[i])) {
          right++;
        }
      }
      System.out.println(DONE + " " + right + "/" + TEXTS.length);
    }
  }

  /** A script that calls System.exit while another of its threads keeps reading text. */
  public static final class ExitDuringReading {
    static final String EXITING = "EXITING";

    public static void main(String[] args) throws Exception {
      Settings.OcrLanguage = "eng";
      CountDownLatch firstReading = new CountDownLatch(1);
      Thread reader = new Thread(() -> {
        while (true) {
          if (readOrGiveUp("Hello OculiX 2026") == null) {
            System.exit(0);
          }
          firstReading.countDown();
        }
      }, "reader");
      reader.setDaemon(true);
      reader.start();
      firstReading.await();
      Thread.sleep(150);
      System.out.println(EXITING);
      System.exit(0);
    }
  }
}
