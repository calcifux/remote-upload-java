package com.github.calcifux.remoteupload.sftp;

import java.io.IOException;

/**
 * Internal seam that connects + authenticates to an SFTP server and hands back a
 * ready {@link SftpSession}. The production implementation ({@code DefaultSftpConnector})
 * uses Apache Mina SSHD; tests inject a fake so {@link SftpTarget#upload}'s logic
 * is unit-testable without a live server.
 *
 * <p>Implementations should throw {@link com.github.calcifux.remoteupload.TerminalUploadException}
 * on authentication failure and {@link IOException} on connection failure.
 */
@FunctionalInterface
interface SftpConnector {

    /**
     * @return a connected, authenticated session
     * @throws IOException on connection failure
     */
    SftpSession connect() throws IOException;
}
