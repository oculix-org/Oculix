package org.sikuli.android;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sikuli.script.ScreenImage;
import se.vidstige.jadb.JadbConnection;

import java.awt.image.BufferedImage;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression guard: a 200 ms capture read slice must not become a 200 ms
 * failure deadline for ADB confirmations. Both capture confirmations are sent
 * as "OK", a deliberate 500 ms protocol delay, then "AY".
 *
 * Uses the real ADBScreen.startBySerial() and ADBScreen.capture(), including
 * JADB and OpenCV. The sole injection redirects ADBClient.jadb to loopback on
 * an ephemeral port, inside a dedicated JVM. No production changes, real ADB
 * server, device, executable, or extra dependency beyond JUnit are required.
 *
 * This successful-capture test complements ADBScreenCaptureCancelTest; it
 * does not replace it. The original unbounded implementation can also pass.
 * It does not test a global deadline, interruption or early image EOF.
 */
public class ADBScreenFragmentedConfirmationTest {
  @TempDir Path temporaryDirectory;

  @Test
  void captureSurvivesBothFragmentedConfirmationsWithoutResendingCommands() throws Exception {
    Path log = temporaryDirectory.resolve("fragmented-confirmations.log");
    String executable = System.getProperty("os.name").startsWith("Windows")
        ? "java.exe" : "java";
    String java = Paths.get(System.getProperty("java.home"), "bin", executable).toString();
    String classpath = System.getProperty("surefire.test.class.path",
        System.getProperty("java.class.path"));
    Process child = new ProcessBuilder(java, "-cp", classpath, Probe.class.getName())
        .redirectErrorStream(true).redirectOutput(log.toFile()).start();
    try {
      boolean ended = child.waitFor(40, TimeUnit.SECONDS);
      assertTrue(ended, () -> "Child exceeded 40 s.\n" + readLog(log));
      assertEquals(0, child.exitValue(), () -> "Isolated scenario failed.\n" + readLog(log));
      System.out.print(readLog(log));
    } finally {
      if (child.isAlive()) {
        child.destroyForcibly();
        if (!child.waitFor(5, TimeUnit.SECONDS)) {
          throw new AssertionError("Unable to terminate child JVM");
        }
      }
    }
  }

  private static String readLog(Path log) {
    try {
      return new String(Files.readAllBytes(log), StandardCharsets.UTF_8);
    } catch (IOException error) {
      return "Cannot read child log: " + error;
    }
  }

  /** Entry point of the isolated JVM, not a second JUnit test. */
  public static final class Probe {
    public static void main(String[] args) {
      try (Scenario scenario = new Scenario()) {
        scenario.verify();
      } catch (Throwable error) {
        error.printStackTrace(System.err);
        System.err.println("FAIL: fixture cleanup / child exit never counts as peer EOF proof.");
        System.exit(1);
        return;
      }
      System.out.println("PASS: two fragmented OKAY replies, exact pixels, no resends, peer EOF before cleanup.");
      System.exit(0);
    }
  }

  private static final class Scenario implements AutoCloseable {
    private static final String SERIAL = "oculix-fragment-test";
    private static final int WIDTH = 2, HEIGHT = 2;
    private static final long FRAGMENT_DELAY_MS = 500;
    private static final int[] RGB = {0x123456, 0xa1b2c3, 0xfe1020, 0x3070c0};

    private final Field connectionField;
    private final Object originalConnection;
    private final ServerSocket listener;
    private final CopyOnWriteArrayList<Socket> sockets = new CopyOnWriteArrayList<>();
    private final AtomicReference<Throwable> serverFailure = new AtomicReference<>();
    private final AtomicInteger captureSelections = new AtomicInteger();
    private final AtomicInteger captureCommands = new AtomicInteger();
    private final AtomicInteger fragmentedReplies = new AtomicInteger();
    private final CompletableFuture<Integer> peerRead = new CompletableFuture<>();
    private final Thread serverThread;
    private volatile boolean capturePhase;
    private volatile boolean cleaning;

    Scenario() throws Exception {
      connectionField = ADBClient.class.getDeclaredField("jadb");
      connectionField.setAccessible(true);
      originalConnection = connectionField.get(null);
      require(originalConnection == null, "Child already has an ADB connection");
      listener = new ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"));
      try {
        listener.setSoTimeout(15000);
        connectionField.set(null, new JadbConnection("127.0.0.1", listener.getLocalPort()));
      } catch (Exception error) {
        listener.close();
        throw error;
      }
      serverThread = new Thread(this::serve, "fragmented-adb-server");
      serverThread.setDaemon(true);
      serverThread.start();
    }

    void verify() throws Exception {
      origin(ADBScreen.class);
      origin(ADBDevice.class);
      origin(JadbConnection.class);
      System.out.println("Java: " + System.getProperty("java.version"));
      System.out.println("Fake ADB: 127.0.0.1:" + listener.getLocalPort());

      ADBScreen screen = ADBScreen.startBySerial(SERIAL);
      require(screen != null, "Device initialization failed; serverFailure=" + serverFailure.get());
      // Initialization services get immediate replies. Only the capture's
      // transport selection and screencap confirmations are fragmented.
      capturePhase = true;
      long started = System.nanoTime();
      ScreenImage captured;
      try {
        captured = screen.capture();
      } catch (Throwable error) {
        System.err.println("Capture failed; serverFailure=" + serverFailure.get());
        throw error;
      }
      long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
      require(captured != null, "Capture returned null");
      BufferedImage image = captured.getImage();
      require(image != null && image.getWidth() == WIDTH && image.getHeight() == HEIGHT,
          "Capture has wrong dimensions");
      for (int y = 0; y < HEIGHT; y++) {
        for (int x = 0; x < WIDTH; x++) {
          require((image.getRGB(x, y) & 0xffffff) == RGB[y * WIDTH + x],
              "Wrong RGB pixel at " + x + "," + y);
        }
      }
      int eof = peerRead.get(5, TimeUnit.SECONDS);
      require(serverFailure.get() == null, "Fake server failed: " + serverFailure.get());
      require(eof == -1, "Client sent unexpected data instead of closing: " + eof);
      require(captureSelections.get() == 1, "Capture selection was retried");
      require(captureCommands.get() == 1, "screencap was retried");
      require(fragmentedReplies.get() == 2, "Both confirmations must be fragmented");
      System.out.printf("Before cleanup: capture=2x2, pixels=exact, fragmentedReplies=%d, "
              + "captureSelections=%d, screencapCommands=%d, peerRead=%d, elapsedMs=%d%n",
          fragmentedReplies.get(), captureSelections.get(), captureCommands.get(), eof, elapsedMs);
    }

    private void serve() {
      try {
        while (!cleaning) {
          try (Socket socket = listener.accept()) {
            sockets.add(socket);
            if (cleaning) return;
            socket.setSoTimeout(15000);
            socket.setTcpNoDelay(true);
            handle(socket);
          }
        }
      } catch (Throwable error) {
        if (!cleaning) {
          serverFailure.compareAndSet(null, error);
          peerRead.completeExceptionally(error); // Exceptions are not EOF proof.
        }
      }
    }

    private void handle(Socket socket) throws Exception {
      DataInputStream in = new DataInputStream(socket.getInputStream());
      OutputStream out = socket.getOutputStream();
      String first = command(in);
      System.out.println("ADB <= " + first);
      if (!capturePhase && first.equals("host:devices")) {
        byte[] devices = (SERIAL + "\tdevice\n").getBytes(StandardCharsets.UTF_8);
        ascii(out, "OKAY" + String.format(Locale.ROOT, "%04x", devices.length));
        out.write(devices);
        out.flush();
        return;
      }
      require(first.equals("host:transport:" + SERIAL), "Unexpected service: " + first);
      boolean capture = capturePhase;
      if (capture) {
        require(captureSelections.incrementAndGet() == 1, "Repeated capture transport selection");
        fragmentedOkay(out, "transport selection");
      } else {
        ascii(out, "OKAY");
      }
      String request = command(in);
      System.out.println("ADB <= " + request);
      if (capture) {
        require(request.equals("exec:screencap"), "Unexpected capture command: " + request);
        require(captureCommands.incrementAndGet() == 1, "Repeated screencap command");
        fragmentedOkay(out, "screencap");
        out.write(rawImage());
        out.flush();
        // Keep the socket open: client must close it. A repeated command on
        // this connection produces a byte, not EOF, and fails the test.
        peerRead.complete(in.read());
      } else if (request.equals("shell:getprop")) {
        ascii(out, "OKAY[ro.build.version.release]: [10]\n[ro.product.model]: [Fragment fixture]\n");
      } else if (request.equals("shell:wm size") || request.equals("shell:wm 'size'")) {
        ascii(out, "OKAYPhysical size: 2x2\n");
      } else if (request.equals("shell:dumpsys display") || request.equals("shell:dumpsys 'display'")) {
        ascii(out, "OKAYmDefaultViewport deviceWidth=2 deviceHeight=2\n"
            + "mDefaultViewport deviceWidth=2 deviceHeight=2\n");
      } else {
        throw new AssertionError("Unexpected initialization command: " + request);
      }
    }

    private void fragmentedOkay(OutputStream out, String phase) throws Exception {
      ascii(out, "OK");
      long started = System.nanoTime();
      long gap = TimeUnit.MILLISECONDS.toNanos(FRAGMENT_DELAY_MS);
      long remaining;
      // Deliberate protocol delay, not synchronization by an arbitrary sleep.
      while ((remaining = gap - (System.nanoTime() - started)) > 0) {
        TimeUnit.NANOSECONDS.sleep(remaining);
      }
      ascii(out, "AY");
      fragmentedReplies.incrementAndGet();
      System.out.printf("ADB => %s: OK + %d ms gap + AY%n", phase,
          TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
    }

    @Override public void close() throws Exception {
      cleaning = true;
      try {
        listener.close();
      } finally {
        try {
          for (Socket socket : sockets) socket.close();
        } finally {
          serverThread.interrupt();
          try {
            serverThread.join(2000);
          } finally {
            connectionField.set(null, originalConnection);
          }
        }
      }
      require(!serverThread.isAlive(), "Fake server survived fixture cleanup");
    }

    private static byte[] rawImage() {
      ByteBuffer raw = ByteBuffer.allocate(12 + WIDTH * HEIGHT * 4).order(ByteOrder.LITTLE_ENDIAN);
      raw.putInt(WIDTH).putInt(HEIGHT).putInt(1);
      for (int rgb : RGB) {
        raw.put((byte) (rgb >>> 16)).put((byte) (rgb >>> 8)).put((byte) rgb).put((byte) 255);
      }
      return raw.array();
    }

    private static String command(DataInputStream in) throws IOException {
      byte[] size = new byte[4];
      in.readFully(size);
      int length = Integer.parseInt(new String(size, StandardCharsets.US_ASCII), 16);
      require(length > 0 && length <= 4096, "Invalid command length: " + length);
      byte[] bytes = new byte[length];
      in.readFully(bytes);
      return new String(bytes, StandardCharsets.UTF_8);
    }

    private static void ascii(OutputStream out, String text) throws IOException {
      out.write(text.getBytes(StandardCharsets.US_ASCII));
      out.flush();
    }

    private static void origin(Class<?> type) {
      System.out.println(type.getName() + " loaded from: "
          + type.getProtectionDomain().getCodeSource().getLocation());
    }
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }
}
