package com.github.calcifux.remoteupload.sftp;

import org.apache.sshd.sftp.client.SftpClient;

/**
 * A connected, authenticated SFTP session: exposes a ready {@link SftpClient}
 * and releases the whole chain (SFTP client + SSH session + SSH client) on
 * {@link #close()}. Internal seam to keep the (un-unit-testable) Apache Mina
 * wiring out of {@link SftpTarget}.
 */
interface SftpSession extends AutoCloseable {

    /** @return the ready SFTP client for this session. */
    SftpClient client();

    /** Releases the SFTP client, SSH session and SSH client (best-effort). */
    @Override
    void close();
}
