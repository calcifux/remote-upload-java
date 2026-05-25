package com.github.calcifux.remoteupload.quarkus;

import com.github.calcifux.remoteupload.RemoteUpload;
import com.github.calcifux.remoteupload.UploadResult;
import com.github.calcifux.remoteupload.UploadTarget;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;

/**
 * Static, Laravel-style facade for pushing bytes into an {@link UploadTarget}
 * from a JAX-RS resource on any CDI runtime (Quarkus, Helidon, OpenLiberty).
 * Complements the injectable
 * {@link com.github.calcifux.remoteupload.quarkus.core.RemoteUploadCdiService}.
 *
 * <pre>{@code
 * // Static facade
 * UploadResult r = Uploads.upload(target, inputStream, length, "image/jpeg");
 *
 * // Injected bean (checksum driven by application.properties)
 * @Inject RemoteUploadCdiService uploads;
 * UploadResult save(byte[] bytes, UploadTarget t) throws IOException {
 *     return uploads.upload(t, bytes, "application/pdf");
 * }
 * }</pre>
 *
 * @since 1.0.0
 */
@Slf4j
public final class Uploads {

    private Uploads() {
        // Utility class — no instances.
    }

    /** Uploads from a stream of known length. */
    public static UploadResult upload(UploadTarget target, InputStream body, long length, String contentType)
            throws IOException {
        UploadResult result = RemoteUpload.to(target).body(body, length).contentType(contentType).upload();
        log.debug("[Uploads] uploaded {} bytes", result.getBytesTransferred());
        return result;
    }

    /** Uploads an in-memory byte array. */
    public static UploadResult upload(UploadTarget target, byte[] bytes, String contentType) throws IOException {
        return RemoteUpload.to(target).body(bytes).contentType(contentType).upload();
    }
}
