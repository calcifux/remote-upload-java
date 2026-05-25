package com.github.calcifux.remoteupload;

import com.github.calcifux.remoteupload.http.HttpTarget;

/**
 * Public, framework-agnostic entry point of the library. Write-side twin of
 * {@code RemoteDownload}.
 *
 * <p>Exposes the fluent {@link #to(String)} and {@link #to(UploadTarget)} factory
 * methods. The returned {@link RemoteUploadRequest} is the only object callers
 * need to push bytes into any destination from any framework (Spring MVC,
 * Quarkus / JAX-RS, plain Servlet, AWS Lambda, command-line tools).
 *
 * <pre>{@code
 * // 1. Plain HTTP PUT to a URL
 * RemoteUpload.to("https://api.example.com/files/report.pdf")
 *     .body(inputStream, length)
 *     .contentType("application/pdf")
 *     .upload();
 *
 * // 2. Cloud storage target (S3 / MinIO, Azure, GCS, ...)
 * UploadTarget target = S3Target.builder()
 *     .bucket("my-bucket").key("tenant/uploads/123/photo.jpg")
 *     .endpoint("http://localhost:9000").credentials(ak, sk).build();
 * UploadResult r = RemoteUpload.to(target).body(in, len).contentType("image/jpeg").upload();
 * }</pre>
 *
 * @since 1.0.0
 */
public final class RemoteUpload {

    private RemoteUpload() {
        // Utility class — no instances.
    }

    /**
     * Builds a request that PUTs the body to a public HTTP / HTTPS URL, delegating
     * to {@link HttpTarget} with default settings (PUT, no auth, default timeouts).
     *
     * @param url absolute HTTP or HTTPS URL
     * @return a fluent {@link RemoteUploadRequest} ready to receive the body
     */
    public static RemoteUploadRequest to(String url) {
        return to(HttpTarget.url(url).build());
    }

    /**
     * Builds a request from any {@link UploadTarget}. Use this overload for cloud
     * storage destinations (S3, Azure, GCS), authenticated HTTP, or your own
     * implementations of {@link UploadTarget}.
     *
     * @param target the destination to write to
     * @return a fluent {@link RemoteUploadRequest} ready to receive the body
     */
    public static RemoteUploadRequest to(UploadTarget target) {
        return new RemoteUploadRequest(target);
    }
}
