/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */

package se.vidstige.jadb;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;

/**
 * Reads on a socket that has a short read timeout. The timeout only gives control back to check
 * interruption and the deadline: bytes already received are kept and the read resumes.
 */
public final class SliceReader {

    private SliceReader() {
    }

    /**
     * Fills the whole buffer.
     *
     * @param deadlineNanos absolute {@link System#nanoTime()} value after which reading stops
     * @throws InterruptedIOException the calling thread is interrupted; its interrupt status stays set
     * @throws EOFException the stream ends before the buffer is full
     * @throws IOException the deadline passes, or the socket fails
     */
    public static void readFully(InputStream in, byte[] buffer, long deadlineNanos) throws IOException {
        int done = 0;
        while (done < buffer.length) {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedIOException(String.format("interrupted after %d of %d bytes",
                    done, buffer.length));
            }
            if (System.nanoTime() - deadlineNanos > 0) {
                throw new IOException(String.format("deadline passed after %d of %d bytes",
                    done, buffer.length));
            }
            int n;
            try {
                n = in.read(buffer, done, buffer.length - done);
            } catch (SocketTimeoutException slice) {
                continue;
            }
            if (n < 0) {
                throw new EOFException(String.format("stream ended after %d of %d bytes",
                    done, buffer.length));
            }
            done += n;
        }
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedIOException(String.format("interrupted while reading the last %d bytes",
                buffer.length));
        }
        if (System.nanoTime() - deadlineNanos > 0) {
            throw new IOException(String.format("deadline passed while reading the last %d bytes",
                buffer.length));
        }
    }
}
