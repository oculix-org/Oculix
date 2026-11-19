/*
 * Copyright (c) 2010-2020, sikuli.org, sikulix.com - MIT license
 */

package se.vidstige.jadb;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;

class Transport {

    private final Socket socket;
    private final OutputStream outputStream;
    private final InputStream inputStream;

    public Transport(Socket socket) throws IOException {
        this.socket = socket;
        this.outputStream = socket.getOutputStream();
        this.inputStream = socket.getInputStream();
    }

    /**
     * Bounds each blocking read on this transport's own socket. A read that waits longer throws
     * SocketTimeoutException and leaves the socket usable; 0 means wait forever.
     */
    public void setReadTimeout(int millis) throws SocketException {
        socket.setSoTimeout(millis);
    }

    public String readString() throws IOException {
        String encodedLength = readString(4);
        int length = Integer.parseInt(encodedLength, 16);
        return readString(length);
    }

    public void readResponseTo(OutputStream output) throws IOException {
        Stream.copy(inputStream, output);
    }

    public InputStream getInputStream() {
        return inputStream;
    }

    public void verifyResponse() throws IOException, JadbException {
        String response = readString(4);
        if (!"OKAY".equals(response)) {
            String error = readString();
            throw new JadbException("command failed: " + error);
        }
    }

    /**
     * Like {@link #verifyResponse()}, reading with {@link SliceReader}: a read timeout on this
     * socket does not fail the confirmation, interruption and the deadline do.
     */
    public void verifyResponse(long deadlineNanos) throws IOException, JadbException {
        String response = readString(4, deadlineNanos);
        if (!"OKAY".equals(response)) {
            int length = Integer.parseInt(readString(4, deadlineNanos), 16);
            throw new JadbException("command failed: " + readString(length, deadlineNanos));
        }
    }

    public String readString(int length) throws IOException {
        DataInput reader = new DataInputStream(inputStream);
        byte[] responseBuffer = new byte[length];
        reader.readFully(responseBuffer);
        return new String(responseBuffer, StandardCharsets.UTF_8);
    }

    private String readString(int length, long deadlineNanos) throws IOException {
        byte[] buffer = new byte[length];
        SliceReader.readFully(inputStream, buffer, deadlineNanos);
        return new String(buffer, StandardCharsets.UTF_8);
    }

    private String getCommandLength(String command) {
        return String.format("%04x", command.length());
    }

    public void send(String command) throws IOException {
        OutputStreamWriter writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8);
        writer.write(getCommandLength(command));
        writer.write(command);
        writer.flush();
    }

    public SyncTransport startSync() throws IOException, JadbException {
        send("sync:");
        verifyResponse();
        return new SyncTransport(outputStream, inputStream);
    }

    public void close() throws IOException {
        inputStream.close();
        outputStream.close();
    }
}
