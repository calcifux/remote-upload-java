package com.github.calcifux.remoteupload.quarkus.core;

import com.github.calcifux.remoteupload.UploadResult;
import com.github.calcifux.remoteupload.quarkus.support.InMemoryTarget;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class RemoteUploadCdiServiceTest {

    /** The @ConfigProperty field is package-private, so the test sets it directly (no CDI container). */
    private RemoteUploadCdiService serviceWith(String checksumAlgorithm) {
        RemoteUploadCdiService service = new RemoteUploadCdiService();
        service.checksumAlgorithm = checksumAlgorithm;
        return service;
    }

    @Test
    void blank_checksum_disables_digest() throws IOException {
        InMemoryTarget target = new InMemoryTarget();

        UploadResult r = serviceWith("").upload(target, "x".getBytes(StandardCharsets.UTF_8), "text/plain");

        assertThat(r.getChecksumAlgorithm()).isNull();
        assertThat(r.checksum()).isEmpty();
        assertThat(r.getBytesTransferred()).isEqualTo(1);
    }

    @Test
    void configured_checksum_is_applied() throws IOException {
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
                .upload(target, new ByteArrayInputStream(new byte[]{7, 8}), 2, "application/octet-stream");

        assertThat(r.getBytesTransferred()).isEqualTo(2);
    }
}
