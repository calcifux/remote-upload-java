package com.github.calcifux.remoteupload.ftp;

import com.github.calcifux.remoteupload.RemoteUpload;
import com.github.calcifux.remoteupload.RetryableUploadException;
import com.github.calcifux.remoteupload.TerminalUploadException;
import com.github.calcifux.remoteupload.UploadResult;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FtpTargetTest {

    @Test
    void upload_success_stores_file_and_streams_bytes() throws Exception {
        FTPClient ftp = mock(FTPClient.class);
        when(ftp.login(anyString(), anyString())).thenReturn(true);
        when(ftp.storeFile(eq("/dir/f.txt"), any(InputStream.class))).thenAnswer(inv -> {
            inv.getArgument(1, InputStream.class).readAllBytes(); // drain → meter advances
            return true;
        });

        FtpTarget target = FtpTarget.builder().host("h").path("/dir/f.txt").client(ftp).build();
        UploadResult r = RemoteUpload.to(target)
                .body("hi".getBytes(StandardCharsets.UTF_8))
                .contentType("text/plain")
                .upload();

        assertThat(r.getKey()).isEqualTo("/dir/f.txt");
        assertThat(r.location()).contains("ftp://h:21/dir/f.txt");
        assertThat(r.getBytesTransferred()).isEqualTo(2);
        verify(ftp).connect("h", 21);
        verify(ftp).setFileType(FTP.BINARY_FILE_TYPE);
        verify(ftp).enterLocalPassiveMode();
    }

    @Test
    void login_failure_is_terminal() throws Exception {
        FTPClient ftp = mock(FTPClient.class);
        when(ftp.login(anyString(), anyString())).thenReturn(false);
        when(ftp.getReplyString()).thenReturn("530 not logged in");

        FtpTarget target = FtpTarget.builder().host("h").path("/f").client(ftp).build();

        assertThatThrownBy(() -> RemoteUpload.to(target).body(new byte[]{1}).upload())
                .isInstanceOf(TerminalUploadException.class);
    }

    @Test
    void store_failure_is_terminal() throws Exception {
        FTPClient ftp = mock(FTPClient.class);
        when(ftp.login(anyString(), anyString())).thenReturn(true);
        when(ftp.storeFile(anyString(), any(InputStream.class))).thenReturn(false);
        when(ftp.getReplyString()).thenReturn("550 permission denied");

        FtpTarget target = FtpTarget.builder().host("h").path("/f").client(ftp).build();

        assertThatThrownBy(() -> RemoteUpload.to(target).body(new byte[]{1}).upload())
                .isInstanceOf(TerminalUploadException.class);
    }

    @Test
    void io_error_is_retryable() throws Exception {
        FTPClient ftp = mock(FTPClient.class);
        when(ftp.login(anyString(), anyString())).thenThrow(new IOException("connection reset"));

        FtpTarget target = FtpTarget.builder().host("h").path("/f").client(ftp).build();

        assertThatThrownBy(() -> RemoteUpload.to(target).body(new byte[]{1}).upload())
                .isInstanceOf(RetryableUploadException.class);
    }

    @Test
    void real_client_against_dead_port_is_retryable() {
        // No injected client → exercises `new FTPClient()` + connect failure path.
        FtpTarget target = FtpTarget.builder()
                .host("127.0.0.1").port(1).path("/f")
                .connectTimeout(java.time.Duration.ofSeconds(2))
                .build();

        assertThatThrownBy(() -> RemoteUpload.to(target).body(new byte[]{1}).upload())
                .isInstanceOf(RetryableUploadException.class);
    }

    @Test
    void real_ftps_client_against_dead_port_is_retryable() {
        // secure=true → exercises `new FTPSClient()` branch.
        FtpTarget target = FtpTarget.builder()
                .host("127.0.0.1").port(1).path("/f").secure(true)
                .connectTimeout(java.time.Duration.ofSeconds(2))
                .build();

        assertThatThrownBy(() -> RemoteUpload.to(target).body(new byte[]{1}).upload())
                .isInstanceOf(RetryableUploadException.class);
    }

    @Test
    void builder_requires_host_and_path() {
        assertThatThrownBy(() -> FtpTarget.builder().path("/f").build())
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> FtpTarget.builder().host("h").build())
                .isInstanceOf(IllegalStateException.class);
    }
}
