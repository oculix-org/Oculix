package org.oculix.report;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.oculix.report.junit5.OculixJUnit5Extension;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

/**
 * The reporter seen by someone who has never heard of OculiX: a JUnit 5 suite with the
 * extension, run in its own JVM whose class path holds no OculiX API, as {@code mvn test}
 * would run it on their machine. The verdict is read in the HTML they would open.
 */
class ReporterWithoutOculixTest {

  @TempDir Path dir;

  @Test void aSuiteWithoutOculixGetsItsReport() throws Exception {
    Map<String, String> outcomes = runUserSuite(PassAndFail.class);
    assertEquals("passed", outcomes.get(testName(PassAndFail.class, "checksTheTitle")), () -> "report: " + outcomes);
    assertEquals("failed", outcomes.get(testName(PassAndFail.class, "checksTheCart")), () -> "report: " + outcomes);
  }

  @Test void anUnmetAssumptionIsReportedAsSkipped() throws Exception {
    Map<String, String> outcomes = runUserSuite(UnmetAssumption.class);
    assertEquals("skipped", outcomes.get(testName(UnmetAssumption.class, "onlyOnStaging")), () -> "report: " + outcomes);
  }

  /**
   * The name the report gives a test: JUnit's display name of the class (its binary
   * name without the package, so a nested class keeps its {@code $}), then the method.
   */
  private static String testName(Class<?> suite, String method) {
    String binary = suite.getName();
    return binary.substring(binary.lastIndexOf('.') + 1) + " › " + method + "()";
  }

  /**
   * Runs {@code suite} in a child JVM without the OculiX API on its class path and
   * returns, for each test of the HTML report, its outcome badge keyed by test name.
   */
  private Map<String, String> runUserSuite(Class<?> suite) throws Exception {
    Path report = dir.resolve("report.html");
    Path output = dir.resolve("child.log");
    String executable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
    String java = Paths.get(System.getProperty("java.home"), "bin", executable).toString();
    Process child = new ProcessBuilder(java, "-cp", classPathWithoutOculix(),
        "-Doculix.report.out=" + report, UserRun.class.getName(), suite.getName())
        .directory(dir.toFile())
        .redirectErrorStream(true)
        .redirectOutput(output.toFile())
        .start();
    try {
      assertTrue(child.waitFor(60, TimeUnit.SECONDS), () -> "the suite still runs after 60 s\n" + read(output));
    } finally {
      child.destroyForcibly();
    }
    String log = read(output);
    assertFalse(log.contains(UserRun.OCULIX_PRESENT), () -> "the OculiX API leaked into the class path\n" + log);
    assertTrue(Files.exists(report), () -> "no report was written\n" + log);
    Map<String, String> outcomes = new LinkedHashMap<>();
    Matcher m = TEST_ROW.matcher(read(report));
    while (m.find()) {
      outcomes.put(m.group(2), m.group(1));
    }
    return outcomes;
  }

  private static final Pattern TEST_ROW =
      Pattern.compile("<article class=\"test\"[^>]*data-outcome=\"(\\w+)\"[^>]*data-name=\"([^\"]*)\"");

  /** The test class path, minus every entry that carries the OculiX API. */
  private static String classPathWithoutOculix() {
    String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
    List<String> kept = new ArrayList<>();
    for (String entry : classpath.split(File.pathSeparator)) {
      String path = entry.replace('\\', '/');
      if (path.contains("oculixapi") || path.endsWith("/API/target/classes")) {
        continue;
      }
      kept.add(entry);
    }
    return String.join(File.pathSeparator, kept);
  }

  private static String read(Path file) {
    try {
      return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
    } catch (Exception e) {
      return "cannot read " + file + ": " + e;
    }
  }

  /** Entry point of the child JVM: runs the user's suite with the JUnit Platform, like a build would. */
  public static final class UserRun {
    static final String OCULIX_PRESENT = "OCULIX-API-PRESENT";

    public static void main(String[] args) throws Exception {
      try {
        Class.forName("org.sikuli.script.Screen");
        System.out.println(OCULIX_PRESENT);
      } catch (ClassNotFoundException expected) {
        // The user of this scenario has no OculiX at all.
      }
      LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
          .selectors(selectClass(Class.forName(args[0])))
          .build();
      Launcher launcher = LauncherFactory.create();
      launcher.execute(request);
    }
  }

  /** A user's suite: one check that holds, one that does not. */
  @ExtendWith(OculixJUnit5Extension.class)
  static class PassAndFail {
    @Test void checksTheTitle() {
      assertEquals("Swag Labs", "Swag Labs");
    }

    @Test void checksTheCart() {
      assertEquals(2, 1, "items in the cart");
    }
  }

  /** A user's suite whose only test runs on staging and is skipped elsewhere. */
  @ExtendWith(OculixJUnit5Extension.class)
  static class UnmetAssumption {
    @Test void onlyOnStaging() {
      assumeTrue("staging".equals(System.getenv("ENV")), "runs on staging only");
    }
  }
}
