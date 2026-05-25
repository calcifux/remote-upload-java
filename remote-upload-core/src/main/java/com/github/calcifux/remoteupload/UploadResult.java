package com.github.calcifux.remoteupload;

import lombok.Builder;
import lombok.Getter;

import java.time.Duration;
import java.util.Optional;

/**
 * Outcome of a {@link RemoteUploadRequest#upload()} call. Couples the numeric
 * transfer stats (bytes, duration, optional checksum) computed by the library
 * with the provider-specific identifiers (key, location, ETag, version id)
 * returned by the {@link UploadTarget}.
 *
 * <p>The target builds it with what it knows; {@link RemoteUploadRequest} enriches
 * it (via {@code toBuilder()}) with the metered byte count, duration and checksum.
 *
 * @since 1.0.0
 */
@Getter
@Builder(toBuilder = true)
public class UploadResult {

    /** Object key / remote path the bytes were written to. */
    private final String key;

    /** Fully-qualified location (URL / URI) when the provider exposes one, else {@code null}. */
    private final String location;

    /** Provider ETag (S3/Azure) when available, else {@code null}. */
    private final String etag;

    /** Provider version id when versioning is enabled, else {@code null}. */
    private final String versionId;

    /** Total number of bytes streamed to the destination. */
    private final long bytesTransferred;

    /** Wall-clock time spent on the upload. */
    private final Duration duration;

    /** Content type stored with the object, or {@code null}. */
    private final String contentType;

    /** Checksum algorithm computed over the body, or {@code null} if none was requested. */
    private final String checksumAlgorithm;

    /** Hex checksum digest, or {@code null} if none was requested. */
    private final String checksumHex;

    /** @return the remote location if the provider exposed one. */
    public Optional<String> location() {
        return Optional.ofNullable(location);
    }

    /** @return the provider ETag if available. */
    public Optional<String> etag() {
        return Optional.ofNullable(etag);
    }

    /** @return the provider version id if available. */
    public Optional<String> versionId() {
        return Optional.ofNullable(versionId);
    }

    /** @return the checksum hex digest if one was computed. */
    public Optional<String> checksum() {
        return Optional.ofNullable(checksumHex);
    }

    /** @return effective throughput in bytes per second; zero if duration is zero/null. */
    public long bytesPerSecond() {
        if (duration == null) return 0L;
        long millis = duration.toMillis();
        if (millis <= 0) return 0L;
        return (bytesTransferred * 1000L) / millis;
    }
}
