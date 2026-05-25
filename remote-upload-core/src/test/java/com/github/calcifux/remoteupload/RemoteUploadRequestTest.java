package com.github.calcifux.remoteupload;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RemoteUploadRequestTest {

    /** In-memory target that drains the body and records what it saw. */
    private static final class CapturingTarget implements UploadTarget {
        byte[] received;
        UploadContent seen;

        @Override
        public UploadResult upload(UploadContent content) throws IOException {
            this.seen = content;
            this.received = content.getBody().readAllBytes();
            return UploadResult.builder().key("the-key").etag("the-etag").build();
        }
    }

    @Test
    void upload_streams_bytes_and_enriches_result() throws IOException {
        CapturingTarget target = new CapturingTarget();
        byte[] data = "hola mundo".getBytes(StandardCharsets.UTF_8);

        UploadResult r = RemoteUpload.to(target)
                .body(data)
                .contentType("text/plain")
                .filename("nota.txt")
                .metadata("capturedBy", "user-1")
                .upload();

        assertThat(target.received).isEqualTo(data);
        assertThat(r.getKey()).isEqualTo("the-key");
        assertThat(r.etag()).contains("the-etag");
        assertThat(r.getBytesTransferred()).isEqualTo(data.length);
        assertThat(r.getDuration()).isNotNull();
        assertThat(target.seen.getContentType()).isEqualTo("text/plain");
        assertThat(target.seen.getFilename()).isEqualTo("nota.txt");
        assertThat(target.seen.metadata()).containsEntry("capturedBy", "user-1");
    }

    @Test
    void upload_computes_checksum_when_requested() throws IOException {
        CapturingTarget target = new CapturingTarget();

        UploadResult r = RemoteUpload.to(target)
                .body("abc".getBytes(StandardCharsets.UTF_8))
                .checksum("SHA-256")
                .upload();

        // SHA-256("abc")
        assertThat(r.getChecksumAlgorithm()).isEqualTo("SHA-256");
        assertThat(r.checksum())
                .contains("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void upload_fires_progress_with_running_total() throws IOException {
        CapturingTarget target = new CapturingTarget();
        List<Long> ticks = new ArrayList<>();

        RemoteUpload.to(target)
                .body(new ByteArrayInputStream(new byte[5000]), 5000)
                .onProgress((sent, total) -> ticks.add(sent))
                .upload();

        assertThat(ticks).isNotEmpty();
        assertThat(ticks.get(ticks.size() - 1)).isEqualTo(5000L);
    }

    @Test
    void upload_from_file_infers_filename_and_size() throws IOException {
        Path tmp = Files.createTempFile("ru", ".txt");
        Files.writeString(tmp, "contenido de prueba");
        try {
            CapturingTarget target = new CapturingTarget();
            UploadResult r = RemoteUpload.to(target).body(tmp).upload();

            assertThat(r.getBytesTransferred()).isEqualTo(Files.size(tmp));
            assertThat(target.seen.getFilename()).isEqualTo(tmp.getFileName().toString());
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Test
    void upload_without_body_throws() {
        assertThatThrownBy(() -> RemoteUpload.to(new CapturingTarget()).upload())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void metadata_map_overload_is_defensively_copied() throws IOException {
        CapturingTarget target = new CapturingTarget();
        Map<String, String> m = new HashMap<>();
        m.put("a", "1");

        RemoteUpload.to(target).body(new byte[]{1, 2, 3}).metadata(m).upload();
        m.put("b", "2"); // mutating after the call must not leak into the captured content

        assertThat(target.seen.metadata()).containsEntry("a", "1").doesNotContainKey("b");
    }

    @Test
    void unknown_length_stream_still_uploads() throws IOException {
        CapturingTarget target = new CapturingTarget();
        UploadResult r = RemoteUpload.to(target)
                .body(new ByteArrayInputStream(new byte[]{9, 8, 7}))
                .upload();
        assertThat(r.getBytesTransferred()).isEqualTo(3);
        assertThat(target.seen.contentLength()).isEmpty();
    }
}
