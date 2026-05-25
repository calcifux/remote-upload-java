package com.github.calcifux.remoteupload.sftp;

import com.github.calcifux.remoteupload.TerminalUploadException;
import lombok.extern.slf4j.Slf4j;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.keyprovider.FileKeyPairProvider;
import org.apache.sshd.sftp.client.SftpClient;
import org.apache.sshd.sftp.client.SftpClientFactory;

import java.io.IOException;
import java.nio.file.Paths;
import java.time.Duration;

/**
 * Production {@link SftpConnector} backed by Apache Mina SSHD: starts an
 * {@link SshClient}, connects, authenticates (password or public key) and opens
 * an {@link SftpClient}.
 *
 * <p>This class is the thin, un-unit-testable wiring layer (static factories +
 * fluent connect chain), so it is <strong>excluded from JaCoCo coverage</strong>
 * in the module pom. The testable upload logic lives in {@link SftpTarget}, which
 * talks to the {@link SftpConnector} seam.
 */
@Slf4j
final class DefaultSftpConnector implements SftpConnector {

    private final String host;
    private final int port;
    private final String user;
    private final String password;
    private final String privateKeyPath;
    private final Duration connectTimeout;
    private final Duration authTimeout;

    DefaultSftpConnector(String host, int port, String user, String password,
                         String privateKeyPath, Duration connectTimeout, Duration authTimeout) {
        this.host = host;
        this.port = port;
        this.user = user;
        this.password = password;
        this.privateKeyPath = privateKeyPath;
        this.connectTimeout = connectTimeout;
        this.authTimeout = authTimeout;
    }

    @Override
    public SftpSession connect() throws IOException {
        SshClient sshClient = SshClient.setUpDefaultClient();
        sshClient.start();
        ClientSession session = null;
        try {
            session = sshClient.connect(user, host, port)
                    .verify(connectTimeout.toMillis())
                    .getSession();
            if (password != null) {
                session.addPasswordIdentity(password);
            }
            if (privateKeyPath != null) {
                session.setKeyIdentityProvider(new FileKeyPairProvider(Paths.get(privateKeyPath)));
            }

            try {
                session.auth().verify(authTimeout.toMillis());
            } catch (IOException authEx) {
                throw new TerminalUploadException("SFTP authentication failed for " + user + "@" + host, authEx);
            }

            SftpClient sftp = SftpClientFactory.instance().createSftpClient(session);
            ClientSession openSession = session;
            return new SftpSession() {
                @Override
                public SftpClient client() {
                    return sftp;
                }

                @Override
                public void close() {
                    closeQuietly(sftp);
                    closeQuietly(openSession);
                    stopQuietly(sshClient);
                }
            };
        } catch (RuntimeException | IOException e) {
            closeQuietly(session);
            stopQuietly(sshClient);
            throw e;
        }
    }

    private static void closeQuietly(AutoCloseable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (Exception e) {
            log.debug("[DefaultSftpConnector] ignored close failure", e);
        }
    }

    private static void stopQuietly(SshClient client) {
        try {
            client.stop();
        } catch (Exception e) {
            log.debug("[DefaultSftpConnector] ignored failure stopping SSH client", e);
        }
    }
}
