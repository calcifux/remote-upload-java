package com.github.calcifux.remoteupload.quarkus;

import com.github.calcifux.remoteupload.UploadResult;
import com.github.calcifux.remoteupload.quarkus.support.InMemoryTarget;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UploadsTest {

    @Test
    void upload_bytes() throws IOException {
        InMemoryTarget target = new InMemoryTarget();
        byte[] data = "datos".getBytes(StandardCharsets.UTF_8);

        UploadResult r = Uploads.upload(target, data, "text/plain");

        assertThat(target.received).isEqualTo(data);
        assertThat(target.seen.getContentType()).isEqualTo("text/plain");
        assertThat(r.getBytesTransferred()).isEqualTo(data.length);
        assertThat(r.getKey()).isEqualTo("mem://key");
    }

    @Test
    void upload_stream_with_length() throws IOException {
        InMemoryTarget target = new InMemoryTarget();

        UploadResult r = Uploads.upload(target,
                new ByteArrayInputStream(new byte[]{1, 2, 3, 4}), 4, "application/octet-stream");

        assertThat(r.getBytesTransferred()).isEqualTo(4);
        assertThat(target.seen.contentLength()).contains(4L);
    }

    @Test
    void upload_propagates_target_failure() {
        assertThatThrownBy(() -> Uploads.upload(InMemoryTarget.failing("boom"), new byte[]{1}, null))
                .isInstanceOf(IOException.class)
                .hasMessage("boom");
    }
}
