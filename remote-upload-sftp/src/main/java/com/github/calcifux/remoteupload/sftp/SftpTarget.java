package com.github.calcifux.remoteupload.sftp;

import com.github.calcifux.remoteupload.RetryableUploadException;
import com.github.calcifux.remoteupload.TerminalUploadException;
import com.github.calcifux.remoteupload.UploadContent;
import com.github.calcifux.remoteupload.UploadResult;
import com.github.calcifux.remoteupload.UploadTarget;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.OutputStream;
import java.time.Duration;

/**
 * {@link UploadTarget} that writes files to a remote SFTP server using Apache
 * Mina SSHD. Write-side twin of {@code SftpOrigin}.
 *
 * <p>Password or public-key authentication. Authentication failures surface as
 * {@link TerminalUploadException}; connection / I/O failures as
 * {@link RetryableUploadException}.
 *
 * <p>The connect/auth wiring lives behind the {@link SftpConnector} seam
 * ({@code DefaultSftpConnector} in production), which keeps {@link #upload}
 * unit-testable without a live server.
 *
 * @since 1.0.0
 */
@Slf4j
public class SftpTarget implements UploadTarget {

    private final String host;
    private final int port;
    private final String user;
    private final String password;
    private final String privateKeyPath;
    private final String path;
    private final Duration connectTimeout;
    private final Duration authTimeout;
    private final SftpConnector connector;

    private SftpTarget(Builder b) {
        this.host = b.host;
        this.port = b.port;
        this.user = b.user;
        this.password = b.password;
        this.privateKeyPath = b.privateKeyPath;
        this.path = b.path;
        this.connectTimeout = b.connectTimeout;
        this.authTimeout = b.authTimeout;
        this.connector = b.connector;
    }

    /** @return a fluent builder for {@link SftpTarget} */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public UploadResult upload(UploadContent content) throws IOException {
        log.debug("[SftpTarget] write sftp://{}@{}:{}{}", user, host, port, path);

        if (password == null && privateKeyPath == null && connector == null) {
            throw new IllegalStateException("SftpTarget: either password or privateKey must be supplied");
        }

        SftpConnector activeConnector = (connector != null)
                ? connector
                : new DefaultSftpConnector(host, port, user, password, privateKeyPath, connectTimeout, authTimeout);

        try (SftpSession session = activeConnector.connect()) {
            try (OutputStream out = session.client().write(path)) {
                content.getBody().transferTo(out);
            }
            return UploadResult.builder()
                    .key(path)
                    .location("sftp://" + host + ":" + port + path)
                    .contentType(content.getContentType())
                    .build();
        } catch (TerminalUploadException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw new RetryableUploadException("SFTP error writing sftp://" + host + ":" + port + path, e);
        }
    }

    /**
     * Fluent builder for {@link SftpTarget}.
     */
    public static class Builder {
        private String host;
        private int port = 22;
        private String user;
        private String password;
        private String privateKeyPath;
        private String path;
        private Duration connectTimeout = Duration.ofSeconds(30);
        private Duration authTimeout = Duration.ofSeconds(30);
        private SftpConnector connector;

        /** Target SFTP host (required). */
        public Builder host(String host) {
            this.host = host;
            return this;
        }

        /** SFTP port. Defaults to 22. */
        public Builder port(int port) {
            this.port = port;
            return this;
        }

        /** SFTP user (required). */
        public Builder user(String user) {
            this.user = user;
            return this;
        }

        /** Password authentication. Mutually exclusive with key auth. */
        public Builder password(String password) {
            this.password = password;
            return this;
        }

        /** Public-key authentication using a PEM file on the local filesystem. */
        public Builder privateKey(String filesystemPath) {
            this.privateKeyPath = filesystemPath;
            return this;
        }

        /** Remote path to write the file at (required). */
        public Builder path(String path) {
            this.path = path;
            return this;
        }

        /** TCP connect timeout. Defaults to 30 seconds. */
        public Builder connectTimeout(Duration d) {
            this.connectTimeout = d;
            return this;
        }

        /** SSH authentication timeout. Defaults to 30 seconds. */
        public Builder authTimeout(Duration d) {
            this.authTimeout = d;
            return this;
        }

        /** Internal seam — inject a connector (tests). Package-private: not public API. */
        Builder connector(SftpConnector connector) {
            this.connector = connector;
            return this;
        }

        /**
         * @return an immutable {@link SftpTarget}
         * @throws IllegalStateException if {@code host}, {@code user} or {@code path} are missing
         */
        public SftpTarget build() {
            if (host == null || user == null || path == null) {
                throw new IllegalStateException("SftpTarget: host, user and path are required");
            }
            return new SftpTarget(this);
        }
    }
}
