package com.github.calcifux.remoteupload.quarkus.core;

import com.github.calcifux.remoteupload.RemoteUpload;
import com.github.calcifux.remoteupload.UploadResult;
import com.github.calcifux.remoteupload.UploadTarget;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.io.InputStream;

/**
 * CDI-managed service that adapts the universal {@link RemoteUpload} API for
 * Quarkus / Helidon / OpenLiberty resources that receive bytes and push them
 * into an {@link UploadTarget}. Write-side twin of {@code RemoteDownloadJaxRsService}.
 *
 * <p>Depends only on Jakarta CDI 4 + MicroProfile Config 3, so it runs unchanged
 * on any compatible runtime. Configuration:
 *
 * <pre>{@code
 * remote-upload.checksum-algorithm=SHA-256   # optional; omit to skip checksums
 * }</pre>
 *
 * @since 1.0.0
 */
@Slf4j
@ApplicationScoped
public class RemoteUploadCdiService {

    private final String checksumAlgorithm;

    /**
     * @param checksumAlgorithm digest algorithm (e.g. {@code SHA-256}); blank/empty
     *                          disables per-upload checksums. Injected by CDI from the
     *                          {@code remote-upload.checksum-algorithm} config property.
     */
    @Inject
    public RemoteUploadCdiService(
            @ConfigProperty(name = "remote-upload.checksum-algorithm", defaultValue = "") String checksumAlgorithm) {
        this.checksumAlgorithm = checksumAlgorithm;
    }

    /**
     * Uploads from a stream of known length.
     *
     * @param target      the destination
     * @param body        the body stream (consumed and closed)
     * @param length      content length in bytes
     * @param contentType MIME type, or {@code null}
     * @return the upload result
     * @throws IOException if the upload fails
     */
    public UploadResult upload(UploadTarget target, InputStream body, long length, String contentType)
            throws IOException {
        UploadResult result = RemoteUpload.to(target)
                .body(body, length)
                .contentType(contentType)
                .checksum(algoOrNull())
                .upload();
        log.debug("[RemoteUploadCdiService] uploaded {} bytes -> {}", result.getBytesTransferred(), result.getKey());
        return result;
    }

    /**
     * Uploads an in-memory byte array.
     *
     * @param target      the destination
     * @param bytes       the body
     * @param contentType MIME type, or {@code null}
     * @return the upload result
     * @throws IOException if the upload fails
     */
    public UploadResult upload(UploadTarget target, byte[] bytes, String contentType) throws IOException {
        return RemoteUpload.to(target)
                .body(bytes)
                .contentType(contentType)
                .checksum(algoOrNull())
                .upload();
    }

    private String algoOrNull() {
        return (checksumAlgorithm == null || checksumAlgorithm.isBlank()) ? null : checksumAlgorithm;
    }
}
