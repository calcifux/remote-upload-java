package com.github.calcifux.remoteupload.apache;

import com.github.calcifux.remoteupload.RetryableUploadException;
import com.github.calcifux.remoteupload.TerminalUploadException;
import com.github.calcifux.remoteupload.UploadContent;
import com.github.calcifux.remoteupload.UploadResult;
import com.github.calcifux.remoteupload.UploadTarget;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.NTCredentials;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.DefaultHttpRequestRetryStrategy;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.io.entity.InputStreamEntity;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Enterprise-grade {@link UploadTarget} backed by Apache HttpClient 5. Write-side
 * twin of {@code ApacheHttpOrigin}.
 *
 * <p>Compared to the JDK-based {@code HttpTarget}, this adds automatic retries
 * with backoff, {@code Basic}/{@code Bearer}/{@code NTLM} auth (SharePoint and
 * Windows intranets), authenticated forward proxies, granular timeouts and
 * connection pooling.
 *
 * <p>Status mapping: 2xx → success; 4xx → {@link TerminalUploadException};
 * 5xx / I/O → {@link RetryableUploadException}.
 *
 * @since 1.0.0
 */
@Slf4j
public class ApacheHttpTarget implements UploadTarget {

    private final URI uri;
    private final String method;
    private final Map<String, String> headers;
    private final Timeout connectTimeout;
    private final Timeout responseTimeout;
    private final int retries;
    private final TimeValue retryInterval;
    private final HttpHost proxy;
    private final BasicCredentialsProvider credentialsProvider;

    private ApacheHttpTarget(Builder b) {
        this.uri = b.uri;
        this.method = b.method;
        this.headers = Map.copyOf(b.headers);
        this.connectTimeout = b.connectTimeout;
        this.responseTimeout = b.responseTimeout;
        this.retries = b.retries;
        this.retryInterval = b.retryInterval;
        this.proxy = b.proxy;
        this.credentialsProvider = b.credentialsProvider;
    }

    /**
     * @param url absolute HTTP/HTTPS URL (required)
     * @return a fluent builder for {@link ApacheHttpTarget}
     */
    public static Builder url(String url) {
        return new Builder(URI.create(url));
    }

    @Override
    public UploadResult upload(UploadContent content) throws IOException {
        log.debug("[ApacheHttpTarget] {} {}", method, uri);

        PoolingHttpClientConnectionManager connectionManager =
                PoolingHttpClientConnectionManagerBuilder.create()
                        .setDefaultConnectionConfig(ConnectionConfig.custom()
                                .setConnectTimeout(connectTimeout)
                                .build())
                        .build();

        RequestConfig requestConfig = RequestConfig.custom()
                .setResponseTimeout(responseTimeout)
                .build();

        HttpClientBuilder clientBuilder = HttpClientBuilder.create()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(requestConfig)
                .setRetryStrategy(new DefaultHttpRequestRetryStrategy(retries, retryInterval));
        if (proxy != null) {
            clientBuilder.setProxy(proxy);
        }
        if (credentialsProvider != null) {
            clientBuilder.setDefaultCredentialsProvider(credentialsProvider);
        }

        CloseableHttpClient client = clientBuilder.build();
        try {
            BasicClassicHttpRequest request = new BasicClassicHttpRequest(method, uri);
            headers.forEach(request::addHeader);

            long len = content.contentLength().orElse(-1L);
            request.setEntity(new InputStreamEntity(content.getBody(), len, resolveContentType(content)));

            try (ClassicHttpResponse response = client.executeOpen(null, request, null)) {
                int status = response.getCode();
                if (status >= 200 && status < 300) {
                    Header etag = response.getFirstHeader("ETag");
                    return UploadResult.builder()
                            .key(uri.toString())
                            .location(uri.toString())
                            .etag(etag != null ? etag.getValue() : null)
                            .contentType(content.getContentType())
                            .build();
                }
                if (status >= 400 && status < 500) {
                    throw new TerminalUploadException("HTTP " + status + " uploading to " + uri);
                }
                throw new RetryableUploadException("HTTP " + status + " uploading to " + uri);
            }
        } catch (IOException e) {
            throw new RetryableUploadException("I/O error uploading to " + uri, e);
        } finally {
            try {
                client.close();
            } catch (IOException e) {
                log.debug("[ApacheHttpTarget] ignored failure closing client", e);
            }
        }
    }

    private static ContentType resolveContentType(UploadContent content) {
        if (content.getContentType() == null) {
            return ContentType.APPLICATION_OCTET_STREAM;
        }
        try {
            return ContentType.parse(content.getContentType());
        } catch (RuntimeException e) {
            return ContentType.APPLICATION_OCTET_STREAM;
        }
    }

    /**
     * Fluent builder for {@link ApacheHttpTarget}. Not thread-safe; one per target.
     */
    public static class Builder {
        private final URI uri;
        private String method = "PUT";
        private final Map<String, String> headers = new LinkedHashMap<>();
        private Timeout connectTimeout = Timeout.ofSeconds(30);
        private Timeout responseTimeout = Timeout.ofMinutes(5);
        private int retries = 3;
        private TimeValue retryInterval = TimeValue.ofSeconds(2);
        private HttpHost proxy;
        private BasicCredentialsProvider credentialsProvider;

        private Builder(URI uri) {
            this.uri = uri;
        }

        /** HTTP method; defaults to {@code PUT}. */
        public Builder method(String method) {
            this.method = method;
            return this;
        }

        /** Adds or replaces a request header. */
        public Builder header(String name, String value) {
            headers.put(name, value);
            return this;
        }

        /** Adds an {@code Authorization: Bearer} header. */
        public Builder bearer(String token) {
            return header("Authorization", "Bearer " + token);
        }

        /** Adds an {@code Authorization: Basic} header (UTF-8 + base64). */
        public Builder basicAuth(String user, String pass) {
            String encoded = Base64.getEncoder()
                    .encodeToString((user + ":" + pass).getBytes(StandardCharsets.UTF_8));
            return header("Authorization", "Basic " + encoded);
        }

        /** Configures NTLM authentication (SharePoint / Windows intranets). */
        @SuppressWarnings("deprecation") // NTCredentials 5-arg ctor needs HC 5.4+; this ships 5.3.1.
        public Builder ntlm(String domain, String user, String pass) {
            ensureCredentials();
            credentialsProvider.setCredentials(
                    new AuthScope(uri.getHost(), uri.getPort()),
                    new NTCredentials(user, pass.toCharArray(), null, domain));
            return this;
        }

        /** Routes through a forward HTTP proxy. */
        public Builder proxy(String host, int port) {
            this.proxy = new HttpHost(host, port);
            return this;
        }

        /** Credentials for an authenticated proxy. Call after {@link #proxy(String, int)}. */
        public Builder proxyAuth(String user, String pass) {
            if (proxy == null) {
                throw new IllegalStateException("Call proxy(...) before proxyAuth(...)");
            }
            ensureCredentials();
            credentialsProvider.setCredentials(
                    new AuthScope(proxy),
                    new UsernamePasswordCredentials(user, pass.toCharArray()));
            return this;
        }

        /** Connect timeout. Defaults to 30 seconds. */
        public Builder connectTimeout(Duration d) {
            this.connectTimeout = Timeout.of(d);
            return this;
        }

        /** Response timeout. Defaults to 5 minutes. */
        public Builder responseTimeout(Duration d) {
            this.responseTimeout = Timeout.of(d);
            return this;
        }

        /** Maximum automatic retries. Defaults to 3. */
        public Builder retries(int retries) {
            this.retries = retries;
            return this;
        }

        /** Interval between retries. Defaults to 2 seconds. */
        public Builder retryInterval(Duration d) {
            this.retryInterval = TimeValue.of(d);
            return this;
        }

        /** @return an immutable {@link ApacheHttpTarget} */
        public ApacheHttpTarget build() {
            return new ApacheHttpTarget(this);
        }

        private void ensureCredentials() {
            if (credentialsProvider == null) {
                credentialsProvider = new BasicCredentialsProvider();
            }
        }
    }
}
