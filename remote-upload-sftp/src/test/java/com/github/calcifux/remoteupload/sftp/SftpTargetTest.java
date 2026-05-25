package com.github.calcifux.remoteupload.sftp;

import com.github.calcifux.remoteupload.RemoteUpload;
import com.github.calcifux.remoteupload.RetryableUploadException;
import com.github.calcifux.remoteupload.TerminalUploadException;
import com.github.calcifux.remoteupload.UploadResult;
import org.apache.sshd.sftp.client.SftpClient;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SftpTargetTest {

    /** Builds a target with an injected connector that returns the given session. */
    private static SftpTarget targetWith(SftpConnector connector) {
        return SftpTarget.builder().host("h").user("u").path("/dir/f").connector(connector).build();
    }

    @Test
    void upload_success_writes_body_and_returns_result() throws Exception {
        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        SftpClient sftp = mock(SftpClient.class);
        when(sftp.write("/dir/f")).thenReturn(sink);

        SftpSession session = new SftpSession() {
            @Override public SftpClient client() { return sftp; }
            @Override public void close() { /* no-op */ }
        };

        UploadResult r = RemoteUpload.to(targetWith(() -> session))
                .body("hi".getBytes(StandardCharsets.UTF_8))
                .contentType("text/plain")
                .upload();

        assertThat(sink.toByteArray()).isEqualTo("hi".getBytes(StandardCharsets.UTF_8));
        assertThat(r.getKey()).isEqualTo("/dir/f");
        assertThat(r.location()).contains("sftp://h:22/dir/f");
        assertThat(r.getBytesTransferred()).isEqualTo(2);
    }

    @Test
    void auth_failure_is_terminal() {
        SftpConnector connector = () -> {
            throw new TerminalUploadException("auth failed");
        };
        assertThatThrownBy(() -> RemoteUpload.to(targetWith(connector)).body(new byte[]{1}).upload())
                .isInstanceOf(TerminalUploadException.class);
    }

    @Test
    void connection_failure_is_retryable() {
        SftpConnector connector = () -> {
            throw new IOException("connection refused");
        };
        assertThatThrownBy(() -> RemoteUpload.to(targetWith(connector)).body(new byte[]{1}).upload())
                .isInstanceOf(RetryableUploadException.class);
    }

    @Test
    void write_failure_is_retryable() throws Exception {
        SftpClient sftp = mock(SftpClient.class);
        when(sftp.write("/dir/f")).thenThrow(new IOException("disk full"));
        SftpSession session = new SftpSession() {
            @Override public SftpClient client() { return sftp; }
            @Override public void close() { }
        };

        assertThatThrownBy(() -> RemoteUpload.to(targetWith(() -> session)).body(new byte[]{1}).upload())
                .isInstanceOf(RetryableUploadException.class);
    }

    @Test
    void missing_auth_without_connector_is_illegal_state() {
        SftpTarget target = SftpTarget.builder().host("h").user("u").path("/f").build();
        assertThatThrownBy(() -> RemoteUpload.to(target).body(new byte[]{1}).upload())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void default_connector_against_dead_port_is_retryable() {
        // No injected connector + password set → exercises `new DefaultSftpConnector(...)`
        // in upload(); connecting to a refused port fails fast → Retryable.
        SftpTarget target = SftpTarget.builder()
                .host("localhost").port(1).user("u").password("p").path("/f")
                .connectTimeout(java.time.Duration.ofSeconds(2))
                .authTimeout(java.time.Duration.ofSeconds(2))
                .build();

        assertThatThrownBy(() -> RemoteUpload.to(target).body(new byte[]{1}).upload())
                .isInstanceOf(RetryableUploadException.class);
    }

    @Test
    void builder_requires_host_user_and_path() {
        assertThatThrownBy(() -> SftpTarget.builder().user("u").path("/f").build())
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> SftpTarget.builder().host("h").path("/f").build())
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> SftpTarget.builder().host("h").user("u").build())
                .isInstanceOf(IllegalStateException.class);
    }
}
