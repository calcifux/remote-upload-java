package com.github.calcifux.remoteupload.ftp;

import com.github.calcifux.remoteupload.RetryableUploadException;
import com.github.calcifux.remoteupload.TerminalUploadException;
import com.github.calcifux.remoteupload.UploadContent;
import com.github.calcifux.remoteupload.UploadResult;
import com.github.calcifux.remoteupload.UploadTarget;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPSClient;

import java.io.IOException;
import java.time.Duration;

/**
 * {@link UploadTarget} that stores files on a remote FTP / FTPS server using
 * Apache Commons Net. Write-side twin of {@code FtpOrigin}.
 *
 * <p>Passive mode by default (NAT / firewall friendly). Binary transfer. For
 * tests / reuse, supply a pre-built {@link FTPClient} via {@link Builder#client(FTPClient)}.
 *
 * @since 1.0.0
 */
@Slf4j
public class FtpTarget implements UploadTarget {

    private final String host;
    private final int port;
    private final String user;
    private final String password;
    private final String path;
    private final boolean secure;
    private final boolean passive;
    private final Duration connectTimeout;
    private final Duration dataTimeout;
    private final FTPClient sharedClient;

    private FtpTarget(Builder b) {
        this.host = b.host;
        this.port = b.port;
        this.user = b.user;
        this.password = b.password;
        this.path = b.path;
        this.secure = b.secure;
        this.passive = b.passive;
        this.connectTimeout = b.connectTimeout;
        this.dataTimeout = b.dataTimeout;
        this.sharedClient = b.sharedClient;
    }

    /** @return a fluent builder for {@link FtpTarget} */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public UploadResult upload(UploadContent content) throws IOException {
        log.debug("[FtpTarget] STOR {}://{}@{}:{}{}", secure ? "ftps" : "ftp", user, host, port, path);

        FTPClient ftp = (sharedClient != null) ? sharedClient : (secure ? new FTPSClient() : new FTPClient());
        ftp.setConnectTimeout((int) connectTimeout.toMillis());
        try {
            ftp.connect(host, port);
            ftp.setDataTimeout(dataTimeout);

            if (!ftp.login(user, password)) {
                throw new TerminalUploadException("FTP login failed: " + ftp.getReplyString());
            }

            ftp.setFileType(FTP.BINARY_FILE_TYPE);
            if (passive) {
                ftp.enterLocalPassiveMode();
            }

            if (!ftp.storeFile(path, content.getBody())) {
                throw new TerminalUploadException("FTP cannot store " + path + ": " + ftp.getReplyString());
            }

            return UploadResult.builder()
                    .key(path)
                    .location((secure ? "ftps" : "ftp") + "://" + host + ":" + port + path)
                    .contentType(content.getContentType())
                    .build();
        } catch (IOException e) {
            throw new RetryableUploadException("FTP I/O error storing " + path, e);
        } finally {
            try {
                ftp.logout();
            } catch (IOException e) {
                log.debug("[FtpTarget] ignored failure logging out", e);
            }
            try {
                ftp.disconnect();
            } catch (IOException e) {
                log.debug("[FtpTarget] ignored failure disconnecting", e);
            }
        }
    }

    /**
     * Fluent builder for {@link FtpTarget}.
     */
    public static class Builder {
        private String host;
        private int port = 21;
        private String user = "anonymous";
        private String password = "anonymous@";
        private String path;
        private boolean secure = false;
        private boolean passive = true;
        private Duration connectTimeout = Duration.ofSeconds(30);
        private Duration dataTimeout = Duration.ofMinutes(5);
        private FTPClient sharedClient;

        /** Target FTP host (required). */
        public Builder host(String host) {
            this.host = host;
            return this;
        }

        /** FTP port. Defaults to 21. */
        public Builder port(int port) {
            this.port = port;
            return this;
        }

        /** FTP user. Defaults to {@code anonymous}. */
        public Builder user(String user) {
            this.user = user;
            return this;
        }

        /** FTP password. Defaults to {@code anonymous@}. */
        public Builder password(String password) {
            this.password = password;
            return this;
        }

        /** Remote path to store the file at (required). */
        public Builder path(String path) {
            this.path = path;
            return this;
        }

        /** Enables explicit FTPS (TLS). Defaults to {@code false}. */
        public Builder secure(boolean secure) {
            this.secure = secure;
            return this;
        }

        /** Toggles passive mode (PASV). Defaults to {@code true}. */
        public Builder passive(boolean passive) {
            this.passive = passive;
            return this;
        }

        /** TCP connect timeout. Defaults to 30 seconds. */
        public Builder connectTimeout(Duration d) {
            this.connectTimeout = d;
            return this;
        }

        /** Data-channel inactivity timeout. Defaults to 5 minutes. */
        public Builder dataTimeout(Duration d) {
            this.dataTimeout = d;
            return this;
        }

        /** Supplies a pre-built {@link FTPClient} (reuse / tests). When set, {@code secure} is ignored. */
        public Builder client(FTPClient client) {
            this.sharedClient = client;
            return this;
        }

        /**
         * @return an immutable {@link FtpTarget}
         * @throws IllegalStateException if {@code host} or {@code path} are missing
         */
        public FtpTarget build() {
            if (host == null || path == null) {
                throw new IllegalStateException("FtpTarget: host and path are required");
            }
            return new FtpTarget(this);
        }
    }
}
