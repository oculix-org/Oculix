/*
 * Copyright (c) 2010-2021, sikuli.org, sikulix.com - MIT license
 * VNC Client for SikuliX - Raw encoding support for TigerVNC 1.6 / RFB 3.7
 */
package com.sikulix.vnc;

import com.sikulix.util.SikuliLogger;

import java.awt.image.BufferedImage;
import java.io.*;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

/**
 * VNC client implementing RFB protocol with Raw encoding.
 *
 * Designed for TigerVNC 1.6.0 on SUSE 12 / RFB 3.7.
 * Forces Raw encoding (0) instead of Tight (7) which produces
 * corrupted images (horizontal band at top of screen).
 *
 * This class does NOT extend CConnection from TigerVNC.
 * It implements the RFB protocol directly for maximum control.
 */
public class VNCClient {

    // RFB encoding constants
    public static final int ENCODING_RAW = 0;
    public static final int ENCODING_COPYRECT = 1;
    public static final int ENCODING_RRE = 2;
    public static final int ENCODING_HEXTILE = 5;
    public static final int ENCODING_TIGHT = 7;
    public static final int ENCODING_ZRLE = 16;

    // RFB message types (server to client)
    private static final int MSG_FRAMEBUFFER_UPDATE = 0;
    private static final int MSG_SET_COLOUR_MAP = 1;
    private static final int MSG_BELL = 2;
    private static final int MSG_SERVER_CUT_TEXT = 3;

    // RFB message types (client to server)
    private static final int MSG_SET_PIXEL_FORMAT = 0;
    private static final int MSG_SET_ENCODINGS = 2;
    private static final int MSG_FRAMEBUFFER_UPDATE_REQUEST = 3;
    private static final int MSG_KEY_EVENT = 4;
    private static final int MSG_POINTER_EVENT = 5;
    private static final int MSG_CLIENT_CUT_TEXT = 6;

    // RFB security types
    private static final int SECURITY_NONE = 1;
    private static final int SECURITY_VNC_AUTH = 2;

    private final int currentEncoding = ENCODING_RAW; // Raw = 0, works on TigerVNC 1.6 / RFB 3.7

    private Socket socket;
    private DataInputStream in;
    private DataOutputStream out;

    private int framebufferWidth;
    private int framebufferHeight;
    private int bitsPerPixel;
    private int depth;
    private boolean bigEndian;
    private boolean trueColour;
    private int redMax, greenMax, blueMax;
    private int redShift, greenShift, blueShift;
    private String serverName;

    private int[] framebuffer; // ARGB pixel data
    private final Object framebufferLock = new Object();
    private volatile CountDownLatch refreshLatch;
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private Thread readerThread;

    private String host;
    private int port;

    /**
     * Connect to a VNC server.
     *
     * @param host     VNC server hostname or IP
     * @param port     VNC server port (typically 5900)
     * @param password VNC password (null for no authentication)
     * @param connectionTimeout connection timeout in seconds
     * @param timeout  read timeout in milliseconds
     * @return the connected VNCClient
     * @throws IOException if connection fails
     */
    public static VNCClient connect(String host, int port, String password,
                                     int connectionTimeout, int timeout) throws IOException {
        VNCClient client = new VNCClient();
        client.host = host;
        client.port = port;
        client.doConnect(host, port, password, connectionTimeout * 1000, timeout);
        return client;
    }

    private VNCClient() {
    }

    private void doConnect(String host, int port, String password,
                           int connectionTimeoutMs, int readTimeoutMs) throws IOException {
        SikuliLogger.info("[VNC] Connecting to " + host + ":" + port
            + " (encoding=Raw, timeout=" + connectionTimeoutMs + "ms)");

        socket = new Socket();
        socket.connect(new java.net.InetSocketAddress(host, port), connectionTimeoutMs);
        socket.setSoTimeout(readTimeoutMs);

        in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
        out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));

        // RFB handshake
        negotiateProtocolVersion();
        handleSecurity(password);
        clientInit();
        serverInit();

        // Set pixel format to 32bpp BGRA (standard for BufferedImage TYPE_INT_ARGB)
        setPixelFormat();

        // Set encoding to Raw
        setEncodings();

        // Initialize framebuffer
        synchronized (framebufferLock) {
            framebuffer = new int[framebufferWidth * framebufferHeight];
        }

        connected.set(true);

        // Request initial framebuffer
        requestFramebufferUpdate(false);

        // Start reader thread
        readerThread = new Thread(this::readLoop, "VNCClient-reader");
        readerThread.setDaemon(true);
        readerThread.start();

        SikuliLogger.info("[VNC] Connected: " + serverName
            + " (" + framebufferWidth + "x" + framebufferHeight + ")");
    }

    // =========================================================================
    // RFB PROTOCOL
    // =========================================================================

    private void negotiateProtocolVersion() throws IOException {
        byte[] serverVersion = new byte[12];
        in.readFully(serverVersion);
        String versionStr = new String(serverVersion, StandardCharsets.US_ASCII);
        SikuliLogger.debug("[VNC] Server version: " + versionStr.trim());

        // Respond with RFB 3.7 (compatible with TigerVNC 1.6)
        String clientVersion = "RFB 003.007\n";
        out.write(clientVersion.getBytes(StandardCharsets.US_ASCII));
        out.flush();
    }

    private void handleSecurity(String password) throws IOException {
        // RFB 3.7: server sends number of security types, then list
        int numTypes = in.readUnsignedByte();
        if (numTypes == 0) {
            int reasonLen = in.readInt();
            byte[] reasonBytes = new byte[reasonLen];
            in.readFully(reasonBytes);
            throw new IOException("VNC connection refused: " + new String(reasonBytes, StandardCharsets.UTF_8));
        }

        byte[] types = new byte[numTypes];
        in.readFully(types);

        // Choose security type
        int chosenType = -1;
        for (byte t : types) {
            int type = t & 0xFF;
            if (password != null && !password.isEmpty() && type == SECURITY_VNC_AUTH) {
                chosenType = SECURITY_VNC_AUTH;
                break;
            }
            if (type == SECURITY_NONE) {
                chosenType = SECURITY_NONE;
            }
        }

        if (chosenType == -1) {
            if (password != null && !password.isEmpty()) {
                // Fallback to VNC auth if available
                for (byte t : types) {
                    if ((t & 0xFF) == SECURITY_VNC_AUTH) {
                        chosenType = SECURITY_VNC_AUTH;
                        break;
                    }
                }
            }
            if (chosenType == -1) {
                throw new IOException("No supported security type found");
            }
        }

        // Send chosen type
        out.writeByte(chosenType);
        out.flush();

        if (chosenType == SECURITY_VNC_AUTH) {
            handleVNCAuth(password);
        }

        // Read security result (RFB 3.7+)
        int result = in.readInt();
        if (result != 0) {
            // Try to read reason (RFB 3.8+, may not be present in 3.7)
            throw new IOException("VNC authentication failed (result=" + result + ")");
        }

        SikuliLogger.debug("[VNC] Authentication successful");
    }

    private void handleVNCAuth(String password) throws IOException {
        // Read 16-byte challenge
        byte[] challenge = new byte[16];
        in.readFully(challenge);

        // DES encrypt with password
        byte[] key = new byte[8];
        byte[] pwBytes = (password != null) ? password.getBytes(StandardCharsets.UTF_8) : new byte[0];
        System.arraycopy(pwBytes, 0, key, 0, Math.min(pwBytes.length, 8));

        // VNC uses a reversed bit order for DES key
        byte[] reversedKey = new byte[8];
        for (int i = 0; i < 8; i++) {
            reversedKey[i] = reverseBits(key[i]);
        }

        byte[] response;
        try {
            Cipher cipher = Cipher.getInstance("DES/ECB/NoPadding");
            SecretKeySpec keySpec = new SecretKeySpec(reversedKey, "DES");
            cipher.init(Cipher.ENCRYPT_MODE, keySpec);
            response = cipher.doFinal(challenge);
        } catch (Exception e) {
            throw new IOException("DES encryption failed: " + e.getMessage(), e);
        }

        out.write(response);
        out.flush();
    }

    private static byte reverseBits(byte b) {
        int n = b & 0xFF;
        int reversed = 0;
        for (int i = 0; i < 8; i++) {
            reversed = (reversed << 1) | (n & 1);
            n >>= 1;
        }
        return (byte) reversed;
    }

    private void clientInit() throws IOException {
        // Shared flag = 1 (allow other clients)
        out.writeByte(1);
        out.flush();
    }

    private void serverInit() throws IOException {
        framebufferWidth = in.readUnsignedShort();
        framebufferHeight = in.readUnsignedShort();

        // Pixel format
        bitsPerPixel = in.readUnsignedByte();
        depth = in.readUnsignedByte();
        bigEndian = in.readUnsignedByte() != 0;
        trueColour = in.readUnsignedByte() != 0;
        redMax = in.readUnsignedShort();
        greenMax = in.readUnsignedShort();
        blueMax = in.readUnsignedShort();
        redShift = in.readUnsignedByte();
        greenShift = in.readUnsignedByte();
        blueShift = in.readUnsignedByte();

        // Padding (3 bytes)
        in.readFully(new byte[3]);

        // Server name
        int nameLen = in.readInt();
        byte[] nameBytes = new byte[nameLen];
        in.readFully(nameBytes);
        serverName = new String(nameBytes, StandardCharsets.UTF_8);

        SikuliLogger.debug("[VNC] Server: " + serverName + " " + framebufferWidth + "x" + framebufferHeight
            + " " + bitsPerPixel + "bpp depth=" + depth + " bigEndian=" + bigEndian);
    }

    private void setPixelFormat() throws IOException {
        // Set 32bpp BGRA format for easy BufferedImage conversion
        out.writeByte(MSG_SET_PIXEL_FORMAT);
        out.writeByte(0); out.writeByte(0); out.writeByte(0); // padding

        out.writeByte(32);  // bits per pixel
        out.writeByte(24);  // depth
        out.writeByte(0);   // big-endian = false (little-endian)
        out.writeByte(1);   // true-colour

        out.writeShort(255); // red-max
        out.writeShort(255); // green-max
        out.writeShort(255); // blue-max
        out.writeByte(16);   // red-shift
        out.writeByte(8);    // green-shift
        out.writeByte(0);    // blue-shift

        out.writeByte(0); out.writeByte(0); out.writeByte(0); // padding
        out.flush();

        // Update local state
        bitsPerPixel = 32;
        depth = 24;
        bigEndian = false;
        trueColour = true;
        redMax = 255; greenMax = 255; blueMax = 255;
        redShift = 16; greenShift = 8; blueShift = 0;
    }

    private void setEncodings() throws IOException {
        out.writeByte(MSG_SET_ENCODINGS);
        out.writeByte(0); // padding
        out.writeShort(1); // number of encodings
        out.writeInt(currentEncoding); // Raw = 0
        out.flush();

        SikuliLogger.info("[VNC] Encoding set to Raw (0)");
    }

    /**
     * Request a framebuffer update from the server.
     *
     * @param incremental true for incremental update, false for full
     */
    public void requestFramebufferUpdate(boolean incremental) throws IOException {
        synchronized (out) {
            out.writeByte(MSG_FRAMEBUFFER_UPDATE_REQUEST);
            out.writeByte(incremental ? 1 : 0);
            out.writeShort(0); // x
            out.writeShort(0); // y
            out.writeShort(framebufferWidth);
            out.writeShort(framebufferHeight);
            out.flush();
        }
    }

    /**
     * Request a full framebuffer refresh.
     */
    public void refreshFramebuffer() {
        try {
            requestFramebufferUpdate(false);
        } catch (IOException e) {
            SikuliLogger.error("[VNC] Failed to request framebuffer update: " + e.getMessage());
        }
    }

    /**
     * Request a framebuffer refresh and wait for completion.
     *
     * @param timeoutMs maximum time to wait in milliseconds
     * @throws InterruptedException if interrupted while waiting
     */
    public void refreshFramebufferSync(long timeoutMs) throws InterruptedException {
        this.refreshLatch = new CountDownLatch(1);
        refreshFramebuffer();
        this.refreshLatch.await(timeoutMs, TimeUnit.MILLISECONDS);
        this.refreshLatch = null;
    }

    // =========================================================================
    // READER THREAD
    // =========================================================================

    private void readLoop() {
        try {
            while (connected.get() && !Thread.currentThread().isInterrupted()) {
                int msgType = in.readUnsignedByte();
                switch (msgType) {
                    case MSG_FRAMEBUFFER_UPDATE:
                        handleFramebufferUpdate();
                        break;
                    case MSG_SET_COLOUR_MAP:
                        handleSetColourMap();
                        break;
                    case MSG_BELL:
                        // Bell - ignore
                        break;
                    case MSG_SERVER_CUT_TEXT:
                        handleServerCutText();
                        break;
                    default:
                        SikuliLogger.warn("[VNC] Unknown message type: " + msgType);
                        break;
                }
            }
        } catch (IOException e) {
            if (connected.get()) {
                SikuliLogger.error("[VNC] Reader thread error: " + e.getMessage());
            }
        } finally {
            connected.set(false);
        }
    }

    private void handleFramebufferUpdate() throws IOException {
        in.readByte(); // padding
        int numRects = in.readUnsignedShort();

        for (int i = 0; i < numRects; i++) {
            int rx = in.readUnsignedShort();
            int ry = in.readUnsignedShort();
            int rw = in.readUnsignedShort();
            int rh = in.readUnsignedShort();
            int encoding = in.readInt();

            switch (encoding) {
                case ENCODING_RAW:
                    handleRawRect(rx, ry, rw, rh);
                    break;
                case ENCODING_COPYRECT:
                    handleCopyRect(rx, ry, rw, rh);
                    break;
                default:
                    throw new IOException("Unsupported encoding: " + encoding);
            }
        }

        framebufferUpdateEnd();

        // Request next update
        try {
            requestFramebufferUpdate(true);
        } catch (IOException e) {
            SikuliLogger.warn("[VNC] Failed to request incremental update: " + e.getMessage());
        }
    }

    private void handleRawRect(int rx, int ry, int rw, int rh) throws IOException {
        int bytesPerPixel = bitsPerPixel / 8; // 4 for 32bpp
        byte[] data = new byte[rw * rh * bytesPerPixel];
        in.readFully(data);

        synchronized (framebufferLock) {
            for (int row = 0; row < rh; row++) {
                for (int col = 0; col < rw; col++) {
                    int srcIdx = (row * rw + col) * bytesPerPixel;
                    int dstIdx = (ry + row) * framebufferWidth + (rx + col);

                    if (dstIdx >= 0 && dstIdx < framebuffer.length) {
                        int pixel;
                        if (bytesPerPixel == 4) {
                            if (bigEndian) {
                                pixel = ((data[srcIdx] & 0xFF) << 24)
                                    | ((data[srcIdx + 1] & 0xFF) << 16)
                                    | ((data[srcIdx + 2] & 0xFF) << 8)
                                    | (data[srcIdx + 3] & 0xFF);
                            } else {
                                // Little-endian: BGRA -> ARGB
                                int b = data[srcIdx] & 0xFF;
                                int g = data[srcIdx + 1] & 0xFF;
                                int r = data[srcIdx + 2] & 0xFF;
                                // 4th byte is padding, force alpha to 0xFF
                                pixel = 0xFF000000 | (r << 16) | (g << 8) | b;
                            }
                        } else {
                            // 3 bytes per pixel
                            pixel = 0xFF000000
                                | ((data[srcIdx + 2] & 0xFF) << 16)
                                | ((data[srcIdx + 1] & 0xFF) << 8)
                                | (data[srcIdx] & 0xFF);
                        }
                        framebuffer[dstIdx] = pixel;
                    }
                }
            }
        }
    }

    private void handleCopyRect(int rx, int ry, int rw, int rh) throws IOException {
        int srcX = in.readUnsignedShort();
        int srcY = in.readUnsignedShort();

        synchronized (framebufferLock) {
            int[] temp = new int[rw * rh];
            for (int row = 0; row < rh; row++) {
                int srcOffset = (srcY + row) * framebufferWidth + srcX;
                System.arraycopy(framebuffer, srcOffset, temp, row * rw, rw);
            }
            for (int row = 0; row < rh; row++) {
                int dstOffset = (ry + row) * framebufferWidth + rx;
                System.arraycopy(temp, row * rw, framebuffer, dstOffset, rw);
            }
        }
    }

    private void handleSetColourMap() throws IOException {
        in.readByte(); // padding
        in.readUnsignedShort(); // first-colour
        int numColours = in.readUnsignedShort();
        // Skip colour map entries (6 bytes each: r, g, b as 16-bit)
        in.readFully(new byte[numColours * 6]);
    }

    private void handleServerCutText() throws IOException {
        in.readFully(new byte[3]); // padding
        int len = in.readInt();
        if (len > 0) {
            in.readFully(new byte[len]);
        }
    }

    /**
     * Called when a framebuffer update is complete.
     * Triggers the refresh latch if one is pending.
     */
    public void framebufferUpdateEnd() {
        CountDownLatch latch = this.refreshLatch;
        if (latch != null) {
            latch.countDown();
        }
    }

    // =========================================================================
    // FRAMEBUFFER ACCESS
    // =========================================================================

    /**
     * Get the entire framebuffer as a BufferedImage.
     *
     * @return the current framebuffer image
     */
    public BufferedImage getFrameBuffer() {
        return getFrameBuffer(0, 0, framebufferWidth, framebufferHeight);
    }

    /**
     * Get a sub-region of the framebuffer as a BufferedImage.
     *
     * @param x      x coordinate
     * @param y      y coordinate
     * @param width  width
     * @param height height
     * @return the framebuffer sub-region image
     */
    public BufferedImage getFrameBuffer(int x, int y, int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        synchronized (framebufferLock) {
            for (int row = 0; row < height; row++) {
                int srcOffset = (y + row) * framebufferWidth + x;
                if (srcOffset >= 0 && srcOffset + width <= framebuffer.length) {
                    image.setRGB(0, row, width, 1, framebuffer, srcOffset, framebufferWidth);
                }
            }
        }
        return image;
    }

    // =========================================================================
    // INPUT EVENTS
    // =========================================================================

    /**
     * Send a key event to the VNC server.
     *
     * @param keysym X11 keysym
     * @param down   true for key press, false for key release
     */
    public void sendKeyEvent(int keysym, boolean down) throws IOException {
        synchronized (out) {
            out.writeByte(MSG_KEY_EVENT);
            out.writeByte(down ? 1 : 0);
            out.writeByte(0); out.writeByte(0); // padding
            out.writeInt(keysym);
            out.flush();
        }
    }

    /**
     * Send a pointer (mouse) event to the VNC server.
     *
     * @param x          x coordinate
     * @param y          y coordinate
     * @param buttonMask button mask (bit 0 = left, bit 1 = middle, bit 2 = right,
     *                   bit 3 = scroll up, bit 4 = scroll down)
     */
    public void sendPointerEvent(int x, int y, int buttonMask) throws IOException {
        synchronized (out) {
            out.writeByte(MSG_POINTER_EVENT);
            out.writeByte(buttonMask);
            out.writeShort(x);
            out.writeShort(y);
            out.flush();
        }
    }

    /**
     * Send a mouse click at given coordinates.
     *
     * @param x x coordinate
     * @param y y coordinate
     */
    public void mouseClick(int x, int y) throws IOException {
        sendPointerEvent(x, y, 1); // button down
        try { Thread.sleep(50); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        sendPointerEvent(x, y, 0); // button up
    }

    /**
     * Send a mouse double-click at given coordinates.
     *
     * @param x x coordinate
     * @param y y coordinate
     */
    public void mouseDoubleClick(int x, int y) throws IOException {
        mouseClick(x, y);
        try { Thread.sleep(80); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        mouseClick(x, y);
    }

    /**
     * Send a right-click at given coordinates.
     *
     * @param x x coordinate
     * @param y y coordinate
     */
    public void mouseRightClick(int x, int y) throws IOException {
        sendPointerEvent(x, y, 4); // right button down
        try { Thread.sleep(50); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        sendPointerEvent(x, y, 0); // button up
    }

    /**
     * Send mouse wheel scroll.
     *
     * @param x      x coordinate
     * @param y      y coordinate
     * @param amount scroll amount (positive = down, negative = up)
     */
    public void mouseWheel(int x, int y, int amount) throws IOException {
        int button = amount > 0 ? 8 : 16; // bit 3 = scroll up, bit 4 = scroll down
        int steps = Math.abs(amount);
        for (int i = 0; i < steps; i++) {
            sendPointerEvent(x, y, button);
            sendPointerEvent(x, y, 0);
            try { Thread.sleep(20); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
    }

    /**
     * Type a string by sending key events.
     *
     * @param text text to type
     */
    public void typeText(String text) throws IOException {
        for (char c : text.toCharArray()) {
            int keysym = charToKeysym(c);
            if (keysym != 0) {
                sendKeyEvent(keysym, true);
                sendKeyEvent(keysym, false);
                try { Thread.sleep(10); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
        }
    }

    /**
     * Send a special key press and release.
     *
     * @param keysym X11 keysym for the special key
     */
    public void sendKey(int keysym) throws IOException {
        sendKeyEvent(keysym, true);
        try { Thread.sleep(30); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        sendKeyEvent(keysym, false);
    }

    private static int charToKeysym(char c) {
        if (c >= 0x20 && c <= 0x7E) {
            return c; // ASCII printable chars map directly
        }
        switch (c) {
            case '\n': return 0xFF0D; // Return
            case '\r': return 0xFF0D;
            case '\t': return 0xFF09; // Tab
            case '\b': return 0xFF08; // BackSpace
            case 0x1B: return 0xFF1B; // Escape
            default:
                if (c >= 0x00A0 && c <= 0x00FF) {
                    return c; // Latin-1 supplement
                }
                return c | 0x01000000; // Unicode
        }
    }

    // =========================================================================
    // CONNECTION MANAGEMENT
    // =========================================================================

    /**
     * Check if the client is connected.
     */
    public boolean isConnected() {
        return connected.get() && socket != null && socket.isConnected() && !socket.isClosed();
    }

    /**
     * Get the framebuffer width.
     */
    public int getFramebufferWidth() {
        return framebufferWidth;
    }

    /**
     * Get the framebuffer height.
     */
    public int getFramebufferHeight() {
        return framebufferHeight;
    }

    /**
     * Get the server name.
     */
    public String getServerName() {
        return serverName;
    }

    /**
     * Disconnect from the VNC server.
     */
    public void close() {
        connected.set(false);
        if (readerThread != null) {
            readerThread.interrupt();
        }
        try {
            if (out != null) out.close();
        } catch (IOException ignored) {}
        try {
            if (in != null) in.close();
        } catch (IOException ignored) {}
        try {
            if (socket != null) socket.close();
        } catch (IOException ignored) {}
        SikuliLogger.info("[VNC] Disconnected from " + host + ":" + port);
    }

    // X11 keysym constants for common special keys
    public static final int XK_Return    = 0xFF0D;
    public static final int XK_Escape    = 0xFF1B;
    public static final int XK_Tab       = 0xFF09;
    public static final int XK_BackSpace = 0xFF08;
    public static final int XK_Delete    = 0xFFFF;
    public static final int XK_Home      = 0xFF50;
    public static final int XK_End       = 0xFF57;
    public static final int XK_Page_Up   = 0xFF55;
    public static final int XK_Page_Down = 0xFF56;
    public static final int XK_Up        = 0xFF52;
    public static final int XK_Down      = 0xFF54;
    public static final int XK_Left      = 0xFF51;
    public static final int XK_Right     = 0xFF53;
    public static final int XK_F1        = 0xFFBE;
    public static final int XK_Shift_L   = 0xFFE1;
    public static final int XK_Control_L = 0xFFE3;
    public static final int XK_Alt_L     = 0xFFE9;
}
