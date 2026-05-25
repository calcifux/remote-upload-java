package com.github.calcifux.remoteupload;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Fluent request that wraps an {@link UploadTarget} and pushes a body into it.
 * Write-side twin of {@code RemoteDownloadRequest}.
 *
 * <p>Supply the body with one of the {@code body(...)} overloads, decorate it with
 * content type / filename / metadata / progress / checksum, then call
 * {@link #upload()}.
 *
 * <p>{@link #upload()} consumes and <strong>closes</strong> the body stream. Build
 * a new request per upload — instances are not reusable.
 *
 * @since 1.0.0
 */
@Slf4j
@RequiredArgsConstructor
public class RemoteUploadRequest {

    private final UploadTarget target;
    private InputStream body;
    private Long contentLength;
    private String contentType;
    private String filename;
    private Map<String, String> metadata;
    private ProgressListener progressListener;
    private String checksumAlgorithm;

    /**
     * Sets the body from a stream of known length (the preferred form — cloud
     * targets like S3 single-PUT need the length).
     *
     * @param in     the body stream (consumed and closed by {@link #upload()})
     * @param length content length in bytes
     * @return this request, for chaining
     */
    public RemoteUploadRequest body(InputStream in, long length) {
        this.body = in;
        this.contentLength = length;
        return this;
    }

    /**
     * Sets the body from a stream of unknown length. Works with chunked HTTP; cloud
     * targets that require a length will buffer or reject — prefer
     * {@link #body(InputStream, long)} when the size is known.
     *
     * @param in the body stream (consumed and closed by {@link #upload()})
     * @return this request, for chaining
     */
    public RemoteUploadRequest body(InputStream in) {
        this.body = in;
        this.contentLength = null;
        return this;
    }

    /**
     * Sets the body from an in-memory byte array; the content length is exact.
     *
     * @param bytes the body bytes
     * @return this request, for chaining
     */
    public RemoteUploadRequest body(byte[] bytes) {
        this.body = new ByteArrayInputStream(bytes);
        this.contentLength = (long) bytes.length;
        return this;
    }

    /**
     * Sets the body from a file on disk. Infers the content length, and (unless
     * already set) the filename and content type.
     *
     * @param file the file to upload
     * @return this request, for chaining
     * @throws IOException if the file cannot be opened or its size read
     */
    public RemoteUploadRequest body(Path file) throws IOException {
        this.body = Files.newInputStream(file);
        this.contentLength = Files.size(file);
        if (this.filename == null) {
            this.filename = file.getFileName().toString();
        }
        if (this.contentType == null) {
            this.contentType = Files.probeContentType(file);
        }
        return this;
    }

    /** Sets the MIME type stored with the object. @return this request, for chaining */
    public RemoteUploadRequest contentType(String contentType) {
        this.contentType = contentType;
        return this;
    }

    /** Sets the suggested filename / object key tail. @return this request, for chaining */
    public RemoteUploadRequest filename(String filename) {
        this.filename = filename;
        return this;
    }

    /**
     * Replaces the user metadata with a defensive copy of the supplied map.
     *
     * @param metadata key/value metadata, or {@code null} to clear
     * @return this request, for chaining
     */
    public RemoteUploadRequest metadata(Map<String, String> metadata) {
        this.metadata = (metadata == null) ? null : new LinkedHashMap<>(metadata);
        return this;
    }

    /** Adds a single metadata entry. @return this request, for chaining */
    public RemoteUploadRequest metadata(String key, String value) {
        if (this.metadata == null) {
            this.metadata = new LinkedHashMap<>();
        }
        this.metadata.put(key, value);
        return this;
    }

    /**
     * Registers a progress callback fired as the destination reads the body.
     *
     * @param listener progress listener; pass {@code null} to disable
     * @return this request, for chaining
     */
    public RemoteUploadRequest onProgress(ProgressListener listener) {
        this.progressListener = listener;
        return this;
    }

    /**
     * Computes a checksum of the bytes streamed to the destination and exposes the
     * hex digest in the {@link UploadResult}. Common values: {@code "MD5"},
     * {@code "SHA-256"}.
     *
     * @param algorithm {@link java.security.MessageDigest} algorithm; {@code null} to disable
     * @return this request, for chaining
     */
    public RemoteUploadRequest checksum(String algorithm) {
        this.checksumAlgorithm = algorithm;
        return this;
    }

    /**
     * Streams the body into the target, then returns the provider result enriched
     * with the metered byte count, duration and optional checksum.
     *
     * @return the aggregated upload result
     * @throws IOException           if the destination cannot be written
     * @throws IllegalStateException if no body was supplied
     */
    public UploadResult upload() throws IOException {
        if (body == null) {
            throw new IllegalStateException("body is required; call body(...) before upload()");
        }
        Instant start = Instant.now();
        try (MeteredInputStream metered =
                     new MeteredInputStream(body, contentLength, progressListener, checksumAlgorithm)) {

            UploadContent content = UploadContent.builder()
                    .body(metered)
                    .contentLength(contentLength)
                    .contentType(contentType)
                    .filename(filename)
                    .metadata(metadata)
                    .build();

            UploadResult providerResult = target.upload(content);
            Duration elapsed = Duration.between(start, Instant.now());

            UploadResult result = providerResult.toBuilder()
                    .bytesTransferred(metered.bytesTransferred())
                    .duration(elapsed)
                    .checksumAlgorithm(checksumAlgorithm)
                    .checksumHex(metered.checksumHex())
                    .build();

            log.debug("[RemoteUploadRequest] upload done; key={} bytes={} duration={} checksum={}",
                    result.getKey(), result.getBytesTransferred(), result.getDuration(), result.getChecksumHex());
            return result;
        }
    }
}
