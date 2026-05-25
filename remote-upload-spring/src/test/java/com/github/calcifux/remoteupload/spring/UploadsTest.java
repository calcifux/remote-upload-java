package com.github.calcifux.remoteupload.spring;

import com.github.calcifux.remoteupload.UploadResult;
import com.github.calcifux.remoteupload.spring.support.InMemoryTarget;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class UploadsTest {

    @Test
    void upload_multipart_carries_filename_and_content_type() throws IOException {
        InMemoryTarget target = new InMemoryTarget();
        byte[] data = "foto-bytes".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", data);

        UploadResult r = Uploads.upload(target, file);

        assertThat(target.received).isEqualTo(data);
        assertThat(target.seen.getFilename()).isEqualTo("photo.jpg");
        assertThat(target.seen.getContentType()).isEqualTo("image/jpeg");
        assertThat(r.getBytesTransferred()).isEqualTo(data.length);
        assertThat(r.getKey()).isEqualTo("mem://key");
    }

    @Test
    void upload_bytes_overload() throws IOException {
        InMemoryTarget target = new InMemoryTarget();
        UploadResult r = Uploads.upload(target, new byte[]{1, 2, 3, 4}, "application/octet-stream");

        assertThat(r.getBytesTransferred()).isEqualTo(4);
        assertThat(target.seen.getContentType()).isEqualTo("application/octet-stream");
    }

    @Test
    void upload_stream_overload() throws IOException {
        InMemoryTarget target = new InMemoryTarget();
        UploadResult r = Uploads.upload(target,
                new ByteArrayInputStream(new byte[]{5, 6, 7}), 3, "text/plain");

        assertThat(r.getBytesTransferred()).isEqualTo(3);
        assertThat(target.seen.contentLength()).contains(3L);
    }
}
