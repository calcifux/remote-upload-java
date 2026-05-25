package com.github.calcifux.remoteupload.spring;

import com.github.calcifux.remoteupload.RemoteUpload;
import com.github.calcifux.remoteupload.UploadResult;
import com.github.calcifux.remoteupload.UploadTarget;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

/**
 * Static, Laravel-style facade for pushing bytes into an {@link UploadTarget}
 * from a Spring MVC controller. Complements the injectable
 * {@link com.github.calcifux.remoteupload.spring.core.RemoteUploadService} that the
 * auto-configuration registers.
 *
 * <pre>{@code
 * // Static facade (no DI)
 * UploadResult r = Uploads.upload(target, multipartFile);
 *
 * // Injected bean (checksum driven by application.yml)
 * @RequiredArgsConstructor
 * class FilesController {
 *     private final RemoteUploadService uploads;
 *     UploadResult save(MultipartFile f, UploadTarget t) throws IOException {
 *         return uploads.upload(t, f);
 *     }
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

    /** Uploads a {@link MultipartFile}, carrying over content type and original filename. */
    public static UploadResult upload(UploadTarget target, MultipartFile file) throws IOException {
        UploadResult result = RemoteUpload.to(target)
                .body(file.getInputStream(), file.getSize())
                .contentType(file.getContentType())
                .filename(file.getOriginalFilename())
                .upload();
        log.debug("[Uploads] uploaded multipart {} ({} bytes)", file.getOriginalFilename(), result.getBytesTransferred());
        return result;
    }

    /** Uploads from a stream of known length. */
    public static UploadResult upload(UploadTarget target, InputStream body, long length, String contentType)
            throws IOException {
        return RemoteUpload.to(target).body(body, length).contentType(contentType).upload();
    }

    /** Uploads an in-memory byte array. */
    public static UploadResult upload(UploadTarget target, byte[] bytes, String contentType) throws IOException {
        return RemoteUpload.to(target).body(bytes).contentType(contentType).upload();
    }
}
