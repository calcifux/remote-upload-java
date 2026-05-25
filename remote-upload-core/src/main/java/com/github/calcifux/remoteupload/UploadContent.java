package com.github.calcifux.remoteupload;

import lombok.Builder;
import lombok.Getter;

import java.io.InputStream;
import java.util.Map;
import java.util.Optional;

/**
 * The payload handed to an {@link UploadTarget}: the live body stream together
 * with the metadata the destination may persist (content type, length, suggested
 * filename, arbitrary key/value metadata). Write-side twin of {@code RemoteContent}.
 *
 * <p>The {@link #body} is owned by the caller ({@link RemoteUploadRequest}), which
 * wraps it for metering/checksum and closes it after the upload. Targets read it
 * but must not close it.
 *
 * @since 1.0.0
 */
@Getter
@Builder
public class UploadContent {

    /** Live body to stream into the destination. Read by the target, closed by the caller. */
    private final InputStream body;

    /**
     * Content length in bytes, or {@code null} when unknown. Some targets (S3
     * single PUT) require it; others (chunked HTTP) can stream without it.
     */
    private final Long contentLength;

    /** MIME type to store with the object, or {@code null}. */
    private final String contentType;

    /** Suggested filename / object key tail, or {@code null}. */
    private final String filename;

    /** Arbitrary user metadata to attach to the object, or {@code null}. */
    private final Map<String, String> metadata;

    /** @return the content length if known. */
    public Optional<Long> contentLength() {
        return Optional.ofNullable(contentLength);
    }

    /** @return the content type if set. */
    public Optional<String> contentType() {
        return Optional.ofNullable(contentType);
    }

    /** @return the suggested filename if set. */
    public Optional<String> filename() {
        return Optional.ofNullable(filename);
    }

    /** @return the user metadata, never {@code null} (empty when none was supplied). */
    public Map<String, String> metadata() {
        return metadata != null ? metadata : Map.of();
    }
}
