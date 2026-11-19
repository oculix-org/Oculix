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
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration contract for interrupting ADBScreen.capture(): terminate within
 * two seconds, return no image, and release the capture transport before cleanup.
 * Each child first verifies a complete capture, including every pixel.
 *
 * One fresh JVM per fault isolates both static ADB state and uninterruptible
 * threads. Only ADBClient.jadb is injected: discovery, initialization, capture,
 * JADB framing and OpenCV conversion remain real production code.
 * No real adb executable, server (5037), device or IDE is used.
 *
 * An exception or a null result can represent failure; Error, NPE and invalid
 * image construction are rejected. This does not prescribe a new exception API.
 * It tests Thread.interrupt(), NOT the IDE Stop action or a future cancel API.
 */
public class ADBScreenCaptureCancelTest {
  @TempDir Path temporaryDirectory;

  @Test
  void interruptWhileWaitingForTheRawHeader() throws Exception {
    runIsolated("NO_HEADER");
  }

  @Test
  void interruptWhileWaitingForTheRemainingPixels() throws Exception {
    runIsolated("PARTIAL_PIXELS");
  }

  private void runIsolated(String fault) throws Exception {
    Path log = temporaryDirectory.resolve(fault + ".log");
    String executable = System.getProperty("os.name").startsWith("Windows")
        ? "java.exe" : "java";
    String java = Paths.get(System.getProperty("java.home"), "bin", executable).toString();
    String classpath = System.getProperty("surefire.test.class.path",
        System.getProperty("java.class.path"));
    Process child = new ProcessBuilder(java, "-cp", classpath, Probe.class.getName(), fault)
        .redirectErrorStream(true).redirectOutput(log.toFile()).start();
    try {
      boolean ended = child.waitFor(40, TimeUnit.SECONDS);
      assertTrue(ended, () -> "Child exceeded 40 s (setup or scenario).\n" + readLog(log));
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

  public static final class Probe {
    public static void main(String[] args) {
      try (Scenario scenario = new Scenario(Fault.valueOf(args[0]))) {
        scenario.verify();
      } catch (Throwable error) {
        error.printStackTrace(System.err);
        System.err.println("FAIL: child exit / forced cleanup is NOT proof of client cleanup.");
        System.exit(1);
        return;
      }
      System.out.println("PASS: interruption and peer EOF verified before fixture cleanup.");
      System.exit(0);
    }
  }

  private enum Fault { NO_HEADER, PARTIAL_PIXELS }

  private static final class Scenario implements AutoCloseable {
    private static final String SERIAL = "oculix-test-only";
    private static final int WIDTH = 2, HEIGHT = 2;
    private static final int[] RGB = {0x123456, 0xa1b2c3, 0xfe1020, 0x3070c0};
    private final Fault fault;
    private final ServerSocket listener;
    private final AtomicReference<Socket> accepted = new AtomicReference<>();
    private final AtomicReference<Throwable> serverFailure = new AtomicReference<>();
    private final CompletableFuture<Void> healthyEof = new CompletableFuture<>();
    private final CompletableFuture<Void> faultDelivered = new CompletableFuture<>();
    private final CompletableFuture<Integer> peerRead = new CompletableFuture<>();
    private final Thread serverThread;
    private final Field connectionField;
    private final Object originalConnection;
    private volatile boolean cleaning;
    private Thread captureThread;
    private volatile ScreenImage result;
    private volatile Throwable captureFailure;
    private volatile boolean captureReturned;
    private int captures;

    Scenario(Fault fault) throws Exception {
      this.fault = fault;
      // Resolve the test seam before opening resources. Fail on API drift.
      connectionField = ADBClient.class.getDeclaredField("jadb");
      connectionField.setAccessible(true);
      originalConnection = connectionField.get(null);
      require(originalConnection == null, "Child unexpectedly already has an ADB connection");
      listener = new ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"));
      try {
        listener.setSoTimeout(15000);
        connectionField.set(null, new JadbConnection("127.0.0.1", listener.getLocalPort()));
      } catch (Exception error) {
        listener.close();
        throw error;
      }
      serverThread = new Thread(this::serve, "t2-fake-adb");
      serverThread.setDaemon(true);
      serverThread.start();
    }

    void verify() throws Exception {
      origin(ADBScreen.class);
      origin(ADBDevice.class);
      origin(JadbConnection.class);
      System.out.println("Java: " + System.getProperty("java.version"));
      System.out.println("Scenario: " + fault + ", fake ADB: 127.0.0.1:" + listener.getLocalPort());

      ADBScreen screen = ADBScreen.startBySerial(SERIAL);
      require(screen != null, "startBySerial returned null; serverFailure=" + serverFailure.get());
      BufferedImage good = screen.capture().getImage();
      require(good != null && good.getWidth() == WIDTH && good.getHeight() == HEIGHT,
          "Healthy capture has wrong dimensions");
      for (int y = 0; y < HEIGHT; y++) {
        for (int x = 0; x < WIDTH; x++) {
          require((good.getRGB(x, y) & 0xffffff) == RGB[y * WIDTH + x],
              "Healthy capture has wrong pixel at " + x + "," + y);
        }
      }
      healthyEof.get(5, TimeUnit.SECONDS);
      System.out.println("CONTROL PASS: 2x2 capture, exact RGB pixels, capture transport EOF.");

      captureThread = new Thread(() -> {
        try {
          result = screen.capture();
          captureReturned = true;
        } catch (Throwable error) {
          captureFailure = error;
        }
      }, "t2-api-capture");
      captureThread.setDaemon(true);
      captureThread.start();
      faultDelivered.get(5, TimeUnit.SECONDS);
      require(serverFailure.get() == null, "Fake server failed: " + serverFailure.get());
      require(captureThread.isAlive(), "Capture ended before interruption: " + captureFailure);

      // The peer has delivered the fault, not merely accepted TCP. Interruption
      // may precede the next read: cancellation must also honor a pending flag.
      captureThread.interrupt();
      captureThread.join(2000);
      boolean terminated = !captureThread.isAlive();
      Integer peer = null;
      Throwable peerProblem = null;
      try {
        peer = peerRead.get(2, TimeUnit.SECONDS);
      } catch (Exception error) {
        peerProblem = error;
      }
      System.out.printf("Before cleanup: terminatedWithin2s=%s, returned=%s, image=%s, "
              + "captureFailure=%s, peerRead=%s, peerFailure=%s, serverFailure=%s%n",
          terminated, captureReturned, result != null, captureFailure,
          peer, peerProblem, serverFailure.get());
      String stack = Arrays.stream(captureThread.getStackTrace())
          .map(frame -> "  at " + frame).collect(Collectors.joining("\n"));
      require(terminated, "ADBScreen.capture still running 2 s after interrupt:\n" + stack);
      require(serverFailure.get() == null, "Fake server failed: " + serverFailure.get());
      require(peerProblem == null && Integer.valueOf(-1).equals(peer),
          "Client did not release transport before cleanup: " + peerProblem + ", read=" + peer);
      require(result == null, "Cancelled capture returned an image (possibly incomplete)");
      require(captureFailure == null || captureFailure instanceof Exception,
          "Capture ended with Error: " + captureFailure);
      require(!(captureFailure instanceof NullPointerException)
              && !(captureFailure instanceof IllegalArgumentException)
              && !(captureFailure instanceof IndexOutOfBoundsException),
          "Cancellation caused a secondary programming/image error: " + captureFailure);
      require(captureFailure != null || captureReturned, "Capture has no recorded outcome");
    }

    private void serve() {
      try {
        while (!cleaning) {
          try (Socket socket = listener.accept()) {
            accepted.set(socket);
            socket.setSoTimeout(15000); // Longer than both observation windows.
            handle(socket);
          } finally {
            accepted.set(null);
          }
        }
      } catch (Throwable error) {
        if (!cleaning) {
          serverFailure.compareAndSet(null, error);
          healthyEof.completeExceptionally(error);
          faultDelivered.completeExceptionally(error);
          peerRead.completeExceptionally(error); // Never misreport error as EOF.
        }
      }
    }

    private void handle(Socket socket) throws Exception {
      DataInputStream in = new DataInputStream(socket.getInputStream());
      OutputStream out = socket.getOutputStream();
      String command = command(in);
      System.out.println("ADB <= " + command);
      if (command.equals("host:devices")) {
        ascii(out, "OKAY");
        byte[] devices = (SERIAL + "\tdevice\n").getBytes(StandardCharsets.UTF_8);
        ascii(out, String.format("%04x", devices.length));
        out.write(devices);
        out.flush();
        return;
      }
      require(command.equals("host:transport:" + SERIAL), "Unexpected ADB service: " + command);
      ascii(out, "OKAY");
      command = command(in);
      System.out.println("ADB <= " + command);
      if (command.equals("shell:getprop")) {
        ascii(out, "OKAY[ro.build.version.release]: [10]\n[ro.product.model]: [T2 fixture]\n");
      } else if (command.equals("shell:wm 'size'") || command.equals("shell:wm size")) {
        ascii(out, "OKAYPhysical size: 2x2\n");
      } else if (command.equals("shell:dumpsys 'display'") || command.equals("shell:dumpsys display")) {
        // Older API revisions use this fallback, including a double find().
        ascii(out, "OKAYmDefaultViewport deviceWidth=2 deviceHeight=2\n"
            + "mDefaultViewport deviceWidth=2 deviceHeight=2\n");
      } else if (command.equals("exec:screencap")) {
        ascii(out, "OKAY");
        captures++;
        require(captures <= 2, "Unexpected third capture / retry");
        byte[] raw = rawImage();
        if (captures == 1) {
          out.write(raw);
          out.flush();
          require(in.read() == -1, "Healthy client sent data instead of closing capture transport");
          healthyEof.complete(null);
        } else {
          if (fault == Fault.PARTIAL_PIXELS) {
            out.write(raw, 0, 12 + 5); // Valid header + one pixel + next pixel's R.
            out.flush();
          }
          faultDelivered.complete(null);
          // Keep the transport OPEN. Only the client may provide the EOF proof.
          peerRead.complete(in.read());
        }
      } else {
        throw new AssertionError("Unexpected command; adapt fixture to actual API: " + command);
      }
    }

    @Override public void close() throws Exception {
      cleaning = true; // All evidence has already been collected.
      listener.close();
      Socket socket = accepted.get();
      if (socket != null) socket.close();
      if (captureThread != null) captureThread.interrupt();
      serverThread.join(2000);
      if (captureThread != null) captureThread.join(2000);
      connectionField.set(null, originalConnection);
      require(!serverThread.isAlive(), "Fake server survived fixture cleanup");
      require(captureThread == null || !captureThread.isAlive(),
          "Capture survived fixture cleanup; only child JVM exit will stop it");
    }

    private static byte[] rawImage() {
      ByteBuffer raw = ByteBuffer.allocate(12 + WIDTH * HEIGHT * 4).order(ByteOrder.LITTLE_ENDIAN);
      raw.putInt(WIDTH).putInt(HEIGHT).putInt(1); // screencap RGBA_8888, legacy 12-byte header.
      for (int rgb : RGB) {
        raw.put((byte) (rgb >>> 16)).put((byte) (rgb >>> 8)).put((byte) rgb).put((byte) 255);
      }
      return raw.array();
    }

    private static String command(DataInputStream in) throws IOException {
      byte[] size = new byte[4];
      in.readFully(size);
      int length = Integer.parseInt(new String(size, StandardCharsets.US_ASCII), 16);
      require(length > 0 && length <= 4096, "Invalid ADB command length: " + length);
      byte[] command = new byte[length];
      in.readFully(command);
      return new String(command, StandardCharsets.UTF_8);
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
