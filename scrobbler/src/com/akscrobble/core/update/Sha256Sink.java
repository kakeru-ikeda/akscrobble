package com.akscrobble.core.update;

import java.io.IOException;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class Sha256Sink extends OutputStream {
    private static final char[] HEX = "0123456789abcdef".toCharArray();
    private final OutputStream delegate;
    private final MessageDigest digest;
    private long count;
    private boolean finalized;
    private String cachedHex;

    public Sha256Sink(OutputStream delegate) {
        if (delegate == null) throw new IllegalArgumentException("delegate is null");
        this.delegate = delegate;
        try { this.digest = MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }

    @Override public void write(int value) throws IOException {
        ensureWritable();
        delegate.write(value);
        digest.update((byte) value);
        count++;
    }

    @Override public void write(byte[] bytes, int offset, int length) throws IOException {
        ensureWritable();
        if (bytes == null) throw new NullPointerException("bytes");
        if (offset < 0 || length < 0 || length > bytes.length - offset) throw new IndexOutOfBoundsException();
        delegate.write(bytes, offset, length);
        digest.update(bytes, offset, length);
        count += length;
    }

    public long bytesWritten() { return count; }

    public String hexDigest() {
        if (!finalized) {
            byte[] bytes = digest.digest();
            StringBuilder out = new StringBuilder(64);
            for (byte b : bytes) { int n = b & 255; out.append(HEX[n >>> 4]).append(HEX[n & 15]); }
            cachedHex = out.toString();
            finalized = true;
        }
        return cachedHex;
    }

    @Override public void flush() throws IOException { delegate.flush(); }
    @Override public void close() throws IOException { delegate.close(); }

    private void ensureWritable() { if (finalized) throw new IllegalStateException("digest finalized"); }
}
