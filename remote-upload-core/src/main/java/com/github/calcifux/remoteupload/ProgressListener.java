package com.github.calcifux.remoteupload;

/**
 * Callback invoked as the body is streamed into the destination.
 *
 * <p>Useful for surfacing progress to logs, metrics or live UIs. The total size
 * may be {@code null} when the caller did not supply a content length (chunked
 * uploads, unknown-length streams).
 *
 * <p>Wire it through the fluent API:
 *
 * <pre>{@code
 * RemoteUpload.to(target)
 *     .body(in, length)
 *     .onProgress((sent, total) -> {
 *         long pct = total != null ? (sent * 100L / total) : -1L;
 *         log.info("uploaded {} / {} bytes ({}%)", sent, total, pct);
 *     })
 *     .upload();
 * }</pre>
 *
 * @since 1.0.0
 */
@FunctionalInterface
public interface ProgressListener {

    /**
     * Invoked as bytes flow into the destination.
     *
     * @param bytesTransferred total bytes sent so far
     * @param totalBytes       total bytes expected, or {@code null} when unknown
     */
    void onProgress(long bytesTransferred, Long totalBytes);
}
