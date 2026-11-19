package org.sikuli.vnc;

import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Test-only RFB 3.8 server on 127.0.0.1 that records the input a client sends.
 * <p>
 * It accepts the TCP-only probe {@code VNCScreen.canConnect()} opens first, then one real
 * session: security type None, a 32 bpp true-colour ServerInit, and an empty answer to each
 * non-incremental FramebufferUpdateRequest. Incremental requests are left unanswered, as a real
 * server leaves them while nothing changes; answering them too made client and server ping-pong
 * about 100,000 requests a second. Every KeyEvent and PointerEvent is recorded in arrival order.
 * <p>
 * The first connection is assumed to be that probe. {@code canConnect()} skips the probe when a
 * VNCScreen for the same address is still registered, so every test must {@code stop()} its
 * screen (they do, in {@code finally}); otherwise this server waits out its read timeout.
 * <p>
 * The security-type count and list go out in ONE write: tigervnc-java-oculix 2.0.4 can spin
 * forever when they arrive in separate segments (tigervnc-java-oculix#16), and that must never
 * be what a test here measures. Every blocking read is bounded by a socket timeout.
 */
final class FakeRfbServer implements Closeable {

  /** One recorded client input event. */
  static final class Event {
    final boolean key;
    final boolean down;     // key events
    final int keysym;       // key events
    final int buttonMask;   // pointer events

    private Event(boolean key, boolean down, int keysym, int buttonMask) {
      this.key = key;
      this.down = down;
      this.keysym = keysym;
      this.buttonMask = buttonMask;
    }

    @Override
    public String toString() {
      return key ? String.format("KEY %s 0x%04x", down ? "down" : "up", keysym)
          : String.format("PTR mask=%d", buttonMask);
    }
  }

  private static final int WIDTH = 640, HEIGHT = 480;
  private static final int READ_TIMEOUT_MS = 15_000;

  private final ServerSocket listener;
  private final Thread thread;
  private final List<Event> events = new ArrayList<>();
  private volatile Throwable failure;
  private volatile Socket session;

  FakeRfbServer() throws IOException {
    listener = new ServerSocket(0, 2, InetAddress.getByName("127.0.0.1"));
    listener.setSoTimeout(READ_TIMEOUT_MS);
    thread = new Thread(this::serve, "fake-rfb-server");
    thread.setDaemon(true);
    thread.start();
  }

  int port() {
    return listener.getLocalPort();
  }

  /** VNCScreen's start-up wait when nothing has changed it (VNCScreen.startUpWait, no getter). */
  private static final int DEFAULT_STARTUP_WAIT = 3;

  /**
   * Starts a VNCScreen on this server. VNCScreen.start() waits its static start-up time (3 s by
   * default) after connecting, which a fake server answering at once does not need. The wait is
   * set to 0 for this one call and restored right after, so a failing test cannot leak it.
   */
  VNCScreen connect() {
    VNCScreen.startUp(0);
    try {
      return VNCScreen.start("127.0.0.1", port(), "", 5, 0);
    } finally {
      VNCScreen.startUp(DEFAULT_STARTUP_WAIT);
    }
  }

  /** A snapshot of the events recorded so far. */
  synchronized List<Event> events() {
    return new ArrayList<>(events);
  }

  /** Key events only, as recorded. */
  List<Event> keyEvents() {
    List<Event> out = new ArrayList<>();
    for (Event e : events()) {
      if (e.key) out.add(e);
    }
    return out;
  }

  /** Pointer button masks only, in order. */
  List<Integer> pointerMasks() {
    List<Integer> out = new ArrayList<>();
    for (Event e : events()) {
      if (!e.key) out.add(e.buttonMask);
    }
    return out;
  }

  /**
   * Waits until at least {@code count} events have been recorded, or the timeout passes.
   * Returns the snapshot either way; the caller asserts on it.
   */
  List<Event> awaitEvents(int count, long timeoutMs) throws InterruptedException {
    return await(null, count, timeoutMs);
  }

  /** As {@link #awaitEvents}, counting key events only. */
  List<Event> awaitKeyEvents(int count, long timeoutMs) throws InterruptedException {
    await(Boolean.TRUE, count, timeoutMs);
    return keyEvents();
  }

  /** As {@link #awaitEvents}, counting pointer events only. */
  List<Integer> awaitPointerMasks(int count, long timeoutMs) throws InterruptedException {
    await(Boolean.FALSE, count, timeoutMs);
    return pointerMasks();
  }

  private synchronized int count(Boolean key) {
    if (key == null) return events.size();
    int n = 0;
    for (Event e : events) {
      if (e.key == key) n++;
    }
    return n;
  }

  private List<Event> await(Boolean key, int count, long timeoutMs) throws InterruptedException {
    long end = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
    synchronized (this) {
      while (count(key) < count && failure == null) {
        long left = TimeUnit.NANOSECONDS.toMillis(end - System.nanoTime());
        if (left <= 0) break;
        wait(left);
      }
    }
    return events();
  }

  Throwable failure() {
    return failure;
  }

  private synchronized void record(Event e) {
    events.add(e);
    notifyAll();
  }

  private void serve() {
    try {
      // VNCScreen.canConnect() opens and closes a TCP-only probe first: no RFB on it.
      try (Socket probe = listener.accept()) {
        probe.setSoTimeout(READ_TIMEOUT_MS);
        if (probe.getInputStream().read() != -1) {
          throw new IOException("the TCP probe sent data");
        }
      }
      try (Socket socket = listener.accept()) {
        session = socket;
        socket.setSoTimeout(READ_TIMEOUT_MS);
        socket.setTcpNoDelay(true);
        DataInputStream in = new DataInputStream(socket.getInputStream());
        DataOutputStream out = new DataOutputStream(socket.getOutputStream());

        out.write("RFB 003.008\n".getBytes(StandardCharsets.US_ASCII));
        out.flush();
        in.readFully(new byte[12]);
        out.write(new byte[] {1, 1}); // count and list in ONE write: security type None
        out.flush();
        if (in.readUnsignedByte() != 1) throw new IOException("client did not choose None");
        out.writeInt(0); // SecurityResult OK
        out.flush();
        in.readUnsignedByte(); // ClientInit (shared flag)

        out.writeShort(WIDTH);
        out.writeShort(HEIGHT);
        // PIXEL_FORMAT: 32 bpp, depth 24, little-endian, true colour, max 255, shifts 16/8/0
        out.write(new byte[] {32, 24, 0, 1});
        out.writeShort(255);
        out.writeShort(255);
        out.writeShort(255);
        out.write(new byte[] {16, 8, 0, 0, 0, 0});
        byte[] name = "fake".getBytes(StandardCharsets.US_ASCII);
        out.writeInt(name.length);
        out.write(name);
        out.flush();

        while (true) {
          int type = in.readUnsignedByte();
          switch (type) {
            case 0: // SetPixelFormat
              in.readFully(new byte[19]);
              break;
            case 2: { // SetEncodings
              in.readUnsignedByte();
              int n = in.readUnsignedShort();
              in.readFully(new byte[4 * n]);
              break;
            }
            case 3: { // FramebufferUpdateRequest: incremental flag, then x, y, w, h
              boolean incremental = in.readUnsignedByte() != 0;
              in.readFully(new byte[8]);
              if (!incremental) { // nothing ever changes here: only a full request gets an answer
                synchronized (out) {
                  out.write(new byte[] {0, 0, 0, 0}); // empty FramebufferUpdate
                  out.flush();
                }
              }
              break;
            }
            case 4: { // KeyEvent
              boolean down = in.readUnsignedByte() != 0;
              in.readUnsignedShort();
              record(new Event(true, down, in.readInt(), 0));
              break;
            }
            case 5: { // PointerEvent
              int mask = in.readUnsignedByte();
              in.readUnsignedShort();
              in.readUnsignedShort();
              record(new Event(false, false, 0, mask));
              break;
            }
            case 6: { // ClientCutText
              in.readFully(new byte[3]);
              int len = in.readInt();
              in.readFully(new byte[Math.abs(len)]);
              break;
            }
            case 150: // EnableContinuousUpdates
              in.readFully(new byte[9]);
              break;
            case 248: { // ClientFence
              byte[] head = new byte[8];
              in.readFully(head);
              in.readFully(new byte[head[7] & 0xFF]);
              break;
            }
            default:
              throw new IOException("unexpected client message type " + type);
          }
        }
      }
    } catch (java.io.EOFException e) {
      // the client disconnected (VNCScreen.stop()): the normal end of a session
    } catch (IOException e) {
      if (!listener.isClosed()) {
        failure = e; // an error after close() is the normal end of a test, not a failure
      }
    } catch (Throwable t) {
      failure = t;
    } finally {
      synchronized (this) {
        notifyAll();
      }
    }
  }

  @Override
  public void close() throws IOException {
    listener.close();
    Socket s = session;
    if (s != null) s.close();
    try {
      thread.join(5_000);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
