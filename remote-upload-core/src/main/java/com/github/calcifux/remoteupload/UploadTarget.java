package com.github.calcifux.remoteupload;

import java.io.IOException;

/**
 * Universal abstraction for any remote destination that can receive a stream of
 * bytes. Write-side twin of {@code DownloadOrigin} from {@code remote-download}.
 *
 * <p>Each provider module ({@code remote-upload-apache}, {@code remote-upload-s3},
 * {@code remote-upload-azure}, {@code remote-upload-gcs}, ...) supplies its own
 * implementation. Consumers push bytes through the same API regardless of where
 * they finally land.
 *
 * <p>Provided implementations include:
 * <ul>
 *     <li>{@code HttpTarget} — JDK {@link java.net.http.HttpClient} based, lightweight PUT</li>
 *     <li>{@code ApacheHttpTarget} — Apache HttpClient 5, with retries, NTLM, proxy auth</li>
 *     <li>{@code S3Target} — Amazon S3 / MinIO via AWS SDK v2</li>
 *     <li>{@code AzureBlobTarget} — Azure Blob Storage</li>
 *     <li>{@code GcsTarget} — Google Cloud Storage</li>
 *     <li>{@code SftpTarget} / {@code FtpTarget} — file-transfer protocols</li>
 * </ul>
 *
 * <p>Custom destinations can be implemented by providing a single
 * {@link #upload(UploadContent)} method.
 *
 * @since 1.0.0
 */
public interface UploadTarget {

    /**
     * Streams the supplied {@link UploadContent} into the remote destination and
     * returns an {@link UploadResult} describing the outcome (key/location, and
     * any provider metadata such as ETag or version id).
     *
     * <p>Implementations consume {@link UploadContent#getBody()} but do
     * <strong>not</strong> own its lifecycle — the caller
     * ({@link RemoteUploadRequest}) opens and closes the body stream.
     *
     * <p>Implementations should translate provider failures into
     * {@link RetryableUploadException} (transient: network, 5xx, timeout) or
     * {@link TerminalUploadException} (permanent: auth, 4xx, quota, validation)
     * so callers can decide whether to retry.
     *
     * @param content the body plus its metadata (content type, length, filename, custom metadata)
     * @return the result of the upload
     * @throws IOException if the destination cannot be written (network failure, I/O error)
     */
    UploadResult upload(UploadContent content) throws IOException;
}
