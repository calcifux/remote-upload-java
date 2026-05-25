package com.github.calcifux.remoteupload.http;

import com.github.calcifux.remoteupload.RetryableUploadException;
import com.github.calcifux.remoteupload.TerminalUploadException;
import com.github.calcifux.remoteupload.UploadContent;
import com.github.calcifux.remoteupload.UploadResult;
import com.github.calcifux.remoteupload.UploadTarget;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@link UploadTarget} that PUTs (or POSTs) the body to an HTTP/HTTPS URL using
 * the JDK 21 {@link HttpClient}. Lightweight, dependency-free (beyond the JDK).
 * Write-side twin of {@code HttpOrigin}.
 *
 * <p>The body is sent with chunked transfer encoding
 * ({@link HttpRequest.BodyPublishers#ofInputStream}); for length-prefixed or
 * enterprise scenarios (NTLM, proxy auth, retries) use {@code ApacheHttpTarget}.
 *
 * <p>Status mapping: 2xx → success; 4xx → {@link TerminalUploadException};
 * everything else (5xx, network I/O, interruption) → {@link RetryableUploadException}.
 *
 * @since 1.0.0
 */
@Slf4j
public class HttpTarget implements UploadTarget {

    private final URI uri;
    private final String method;
    private final Map<String, String> headers;
    private final String bearer;
    private final Duration connectTimeout;
    private final Duration requestTimeout;

    private HttpTarget(Builder b) {
        this.uri = URI.create(b.url);
        this.method = b.method;
        this.headers = b.headers;
        this.bearer = b.bearer;
        this.connectTimeout = b.connectTimeout;
        this.requestTimeout = b.requestTimeout;
    }

    /**
     * @param url absolute HTTP/HTTPS URL (required)
     * @return a fluent builder for {@link HttpTarget}
     */
    public static Builder url(String url) {
        return new Builder(url);
    }

    @Override
    public UploadResult upload(UploadContent content) throws IOException {
        log.debug("[HttpTarget] {} {}", method, uri);

        HttpClient client = HttpClient.newBuilder()
                // Force HTTP/1.1: a PUT with a streamed body over the JDK client's
                // default HTTP/2 fails (RST_STREAM / EOF) against many servers,
                // including WireMock. 1.1 is the broadly-compatible choice for uploads.
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(connectTimeout)
                .build();

        HttpRequest.Builder rb = HttpRequest.newBuilder(uri)
                .method(method, HttpRequest.BodyPublishers.ofInputStream(content::getBody))
                .timeout(requestTimeout);

        content.contentType().ifPresent(ct -> rb.header("Content-Type", ct));
        if (bearer != null) {
            rb.header("Authorization", "Bearer " + bearer);
        }
        headers.forEach(rb::header);

        try {
            HttpResponse<Void> response = client.send(rb.build(), HttpResponse.BodyHandlers.discarding());
            int status = response.statusCode();
            if (status >= 200 && status < 300) {
                return UploadResult.builder()
                        .key(uri.toString())
                        .location(uri.toString())
                        .etag(response.headers().firstValue("ETag").orElse(null))
                        .contentType(content.getContentType())
                        .build();
            }
            if (status >= 400 && status < 500) {
                throw new TerminalUploadException("HTTP " + status + " uploading to " + uri);
            }
            throw new RetryableUploadException("HTTP " + status + " uploading to " + uri);
        } catch (IOException e) {
            throw new RetryableUploadException("I/O error uploading to " + uri, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RetryableUploadException("Interrupted uploading to " + uri, e);
        }
    }

    /**
     * Fluent builder for {@link HttpTarget}.
     */
    public static class Builder {
        private final String url;
        private String method = "PUT";
        private final Map<String, String> headers = new LinkedHashMap<>();
        private String bearer;
        private Duration connectTimeout = Duration.ofSeconds(10);
        private Duration requestTimeout = Duration.ofSeconds(60);

        Builder(String url) {
            this.url = url;
        }

        /** HTTP method; defaults to {@code PUT}. Use {@code "POST"} for APIs that expect it. */
        public Builder method(String method) {
            this.method = method;
            return this;
        }

        /** Adds a request header. */
        public Builder header(String name, String value) {
            this.headers.put(name, value);
            return this;
        }

        /** Sets a Bearer token (adds the {@code Authorization} header). */
        public Builder bearer(String token) {
            this.bearer = token;
            return this;
        }

        /** Connection timeout; defaults to 10s. */
        public Builder connectTimeout(Duration timeout) {
            this.connectTimeout = timeout;
            return this;
        }

        /** Per-request timeout; defaults to 60s. */
        public Builder requestTimeout(Duration timeout) {
            this.requestTimeout = timeout;
            return this;
        }

        /**
         * @return an immutable {@link HttpTarget}
         * @throws IllegalStateException if {@code url} is missing/blank
         */
        public HttpTarget build() {
            if (url == null || url.isBlank()) {
                throw new IllegalStateException("HttpTarget: url is required");
            }
            return new HttpTarget(this);
        }
    }
}
