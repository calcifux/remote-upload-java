package com.github.calcifux.remoteupload.spring.core;

import com.github.calcifux.remoteupload.RemoteUpload;
import com.github.calcifux.remoteupload.UploadResult;
import com.github.calcifux.remoteupload.UploadTarget;
import com.github.calcifux.remoteupload.spring.config.RemoteUploadProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

/**
 * Spring MVC-aware service that adapts the universal {@link RemoteUpload} API for
 * controllers that receive bytes (a {@link MultipartFile}, a stream, an array)
 * and push them into an {@link UploadTarget}. Write-side twin of
 * {@code RemoteDownloadService}.
 *
 * <p>This is the bean callers inject when they prefer constructor injection over
 * the static {@code Uploads} facade. Both honour the configured default checksum
 * algorithm from {@link RemoteUploadProperties}.
 *
 * @since 1.0.0
 */
@Slf4j
@RequiredArgsConstructor
public class RemoteUploadService {

    private final RemoteUploadProperties properties;

    /**
     * Uploads a Spring {@link MultipartFile} (typical {@code @RequestParam} file)
     * to the target, carrying over its content type and original filename.
     *
     * @param target the destination
     * @param file   the uploaded multipart file
     * @return the upload result
     * @throws IOException if the file cannot be read or the upload fails
     */
    public UploadResult upload(UploadTarget target, MultipartFile file) throws IOException {
        UploadResult result = RemoteUpload.to(target)
                .body(file.getInputStream(), file.getSize())
                .contentType(file.getContentType())
                .filename(file.getOriginalFilename())
                .checksum(properties.getChecksumAlgorithm())
                .upload();
        log.debug("[RemoteUploadService] uploaded multipart {} ({} bytes) -> {}",
                file.getOriginalFilename(), result.getBytesTransferred(), result.getKey());
        return result;
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
        return RemoteUpload.to(target)
                .body(body, length)
                .contentType(contentType)
                .checksum(properties.getChecksumAlgorithm())
                .upload();
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
                .checksum(properties.getChecksumAlgorithm())
                .upload();
    }
}
