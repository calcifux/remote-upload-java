package com.github.calcifux.remoteupload;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Internal {@link InputStream} decorator that instruments the body as the target
 * SDK reads it: counts bytes, optionally computes a digest, and fires a
 * {@link ProgressListener}.
 *
 * <p>Upload-side twin of {@code remote-download}'s {@code StreamWriter}: downloads
 * pull bytes in a copy loop we control, but uploads are pulled by the provider
 * SDK, so we instrument the stream the SDK reads instead of running our own loop.
 *
 * <p>Progress is reported on bulk {@code read(byte[], ...)} calls (how SDKs read),
 * never on single-byte {@link #read()} — that would be far too chatty.
 *
 * @since 1.0.0
 */
final class MeteredInputStream extends FilterInputStream {

    private final Long totalBytes;
    private final ProgressListener listener;
    private final MessageDigest digest;
    private long count;

    MeteredInputStream(InputStream in,
                       Long totalBytes,
                       ProgressListener listener,
                       String digestAlgorithm) throws IOException {
        super(in);
        this.totalBytes = totalBytes;
        this.listener = listener;
        if (digestAlgorithm != null && !digestAlgorithm.isBlank()) {
            try {
                this.digest = MessageDigest.getInstance(digestAlgorithm);
            } catch (NoSuchAlgorithmException e) {
                throw new IOException("Unsupported digest algorithm: " + digestAlgorithm, e);
            }
        } else {
            this.digest = null;
        }
    }

    @Override
    public int read() throws IOException {
        int b = super.read();
        if (b != -1) {
            count++;
            if (digest != null) {
                digest.update((byte) b);
            }
        }
        return b;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        int n = super.read(b, off, len);
        if (n > 0) {
            count += n;
            if (digest != null) {
                digest.update(b, off, n);
            }
            if (listener != null) {
                listener.onProgress(count, totalBytes);
            }
        }
        return n;
    }

    /** @return total bytes read through this stream so far. */
    long bytesTransferred() {
        return count;
    }

    /** @return the lower-case hex digest, or {@code null} if no algorithm was set. */
    String checksumHex() {
        if (digest == null) {
            return null;
        }
        return bytesToHex(digest.digest());
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
