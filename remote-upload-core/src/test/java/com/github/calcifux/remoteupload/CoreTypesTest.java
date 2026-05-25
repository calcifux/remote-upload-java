package com.github.calcifux.remoteupload;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class CoreTypesTest {

    @Test
    void exceptions_carry_message_and_cause() {
        Throwable cause = new RuntimeException("boom");

        assertThat(new RemoteUploadException("m").getMessage()).isEqualTo("m");
        assertThat(new RemoteUploadException("m", cause).getCause()).isSameAs(cause);
        assertThat(new RemoteUploadException(cause).getCause()).isSameAs(cause);

        assertThat(new RetryableUploadException("r").getMessage()).isEqualTo("r");
        assertThat(new RetryableUploadException("r", cause).getCause()).isSameAs(cause);

        assertThat(new TerminalUploadException("t").getMessage()).isEqualTo("t");
        assertThat(new TerminalUploadException("t", cause).getCause()).isSameAs(cause);
    }

    @Test
    void retryable_and_terminal_are_remote_upload_exceptions() {
        assertThat(new RetryableUploadException("r")).isInstanceOf(RemoteUploadException.class);
        assertThat(new TerminalUploadException("t")).isInstanceOf(RemoteUploadException.class);
    }

    @Test
    void upload_result_helpers() {
        UploadResult r = UploadResult.builder()
                .key("k")
                .bytesTransferred(2000)
                .duration(Duration.ofSeconds(1))
                .etag("e")
                .build();

        assertThat(r.bytesPerSecond()).isEqualTo(2000L);
        assertThat(r.etag()).contains("e");
        assertThat(r.location()).isEmpty();
        assertThat(r.versionId()).isEmpty();
        assertThat(r.checksum()).isEmpty();

        UploadResult noDuration = UploadResult.builder().bytesTransferred(10).build();
        assertThat(noDuration.bytesPerSecond()).isZero();
    }

    @Test
    void upload_content_metadata_and_optionals_default_empty() {
        UploadContent c = UploadContent.builder().build();
        assertThat(c.metadata()).isEmpty();
        assertThat(c.contentType()).isEmpty();
        assertThat(c.contentLength()).isEmpty();
        assertThat(c.filename()).isEmpty();
    }
}
