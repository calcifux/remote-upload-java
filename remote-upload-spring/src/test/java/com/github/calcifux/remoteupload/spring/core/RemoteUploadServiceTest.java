package com.github.calcifux.remoteupload.spring.core;

import com.github.calcifux.remoteupload.UploadResult;
import com.github.calcifux.remoteupload.spring.config.RemoteUploadProperties;
import com.github.calcifux.remoteupload.spring.support.InMemoryTarget;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class RemoteUploadServiceTest {

    private RemoteUploadService serviceWith(String checksumAlgorithm) {
        RemoteUploadProperties props = new RemoteUploadProperties();
        props.setChecksumAlgorithm(checksumAlgorithm);
        return new RemoteUploadService(props);
    }

    @Test
    void uploads_multipart_without_checksum_by_default() throws IOException {
        InMemoryTarget target = new InMemoryTarget();
        MockMultipartFile file = new MockMultipartFile("file", "n.txt", "text/plain",
                "hola".getBytes(StandardCharsets.UTF_8));

        UploadResult r = serviceWith(null).upload(target, file);

        assertThat(r.getBytesTransferred()).isEqualTo(4);
        assertThat(r.getChecksumAlgorithm()).isNull();
        assertThat(r.checksum()).isEmpty();
    }

    @Test
    void applies_configured_default_checksum() throws IOException {
        InMemoryTarget target = new InMemoryTarget();

        UploadResult r = serviceWith("SHA-256")
                .upload(target, "abc".getBytes(StandardCharsets.UTF_8), "text/plain");

        assertThat(r.getChecksumAlgorithm()).isEqualTo("SHA-256");
        assertThat(r.checksum())
                .contains("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void uploads_stream_overload() throws IOException {
        InMemoryTarget target = new InMemoryTarget();

        UploadResult r = serviceWith(null)
                .upload(target, new ByteArrayInputStream(new byte[]{1, 2}), 2, "application/octet-stream");

        assertThat(r.getBytesTransferred()).isEqualTo(2);
        assertThat(target.seen.getContentType()).isEqualTo("application/octet-stream");
    }
}
